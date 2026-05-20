package com.coremcp.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.AgentService;
import com.coremcp.services.LlmClient;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;

import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultAgentService implements AgentService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentService.class);
	private static final int MAX_TOOL_ITERATIONS = 10;

	// Above this size, tool result payloads are replaced with a short snippet before being
	// returned to the client (and thus echoed back on the next turn). Keeps long conversations
	// from ballooning over time. The agent's own in-progress tool loop still sees full payloads.
	private static final int TOOL_RESULT_SUMMARY_THRESHOLD = 300;
	private static final int TOOL_RESULT_SUMMARY_SNIPPET = 200;

	private static final Set<String> BROWSE_TOOLS = Set.of(
		"product_search", "product_get", "cart_add_product",
		"customer_get", "customer_lookup", "order_history", "order_get", "promotions_get");

	private static final Set<String> CART_TOOLS = Set.of(
		"product_search", "product_get", "cart_add_product",
		"customer_get", "customer_lookup", "order_history", "order_get",
		"cart_get", "cart_update_entry", "cart_remove_entry",
		"cart_apply_voucher", "promotions_get");

	private static final String INTENT_PROMPT =
		"Classify the user's shopping intent as exactly one word: browse, cart, or checkout.\n" +
		"- browse: searching, viewing products, asking questions, greeting, general chat\n" +
		"- cart: viewing cart, changing quantities, removing items, applying coupons or vouchers\n" +
		"- checkout: buying, purchasing, placing order, ready to pay, completing a purchase\n" +
		"Reply with ONLY that single word.";

	private static final String SYSTEM_PROMPT =
		"You are a shopping assistant for ThinkShop, an electronics store powered by SAP Commerce. " +
		"You help customers browse products, manage their cart, and complete purchases. " +
		"Use the available tools to look up products, check stock, manage the cart, and process orders. " +
		"Be concise and helpful. When showing products, include the product code, name, and price. " +
		"When a customer wants to buy something, guide them through: add to cart → set delivery address → " +
		"set delivery mode → set payment → place order.\n\n" +
		"PROMOTIONS & COUPONS: Use the promotions_get tool to check active promotions and coupons. " +
		"The tool returns per-customer redemption data including 'currentUserRedemptions' which tells you " +
		"how many times THIS customer has used a coupon. Compare it against 'maxRedemptionsPerCustomer' to " +
		"determine eligibility. ALWAYS call promotions_get when the customer asks about coupon eligibility, " +
		"whether they've used a coupon, or if a deal applies to them — never guess from order history.\n\n" +
		"IMPORTANT: After EVERY response, include exactly one line at the very end with 2-4 suggested " +
		"follow-up actions the user might want to take next. Format this line as:\n" +
		"SUGGESTIONS:[\"suggestion 1\",\"suggestion 2\",\"suggestion 3\"]\n" +
		"Make suggestions contextual — for example after adding to cart suggest viewing cart or checkout, " +
		"after browsing suggest adding items, after viewing cart suggest checkout or continuing shopping. " +
		"Keep each suggestion under 30 characters. Do NOT mention the suggestions in your response text.\n\n" +
		"You have a ui_action tool. ALWAYS call it when the customer wants to:\n" +
		"- checkout: proceed to checkout or place an order\n" +
		"Call ui_action BEFORE writing your text response when the user's intent clearly matches checkout. " +
		"For everything else (browsing products, viewing orders, viewing cart) — use the appropriate tools and show results inline.";

	private List<McpToolHandler> toolHandlers;
	private LlmClient llmClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private Map<String, McpToolHandler> toolHandlerMap;
	private List<Map<String, Object>> openAiToolDefinitions;
	private List<Map<String, Object>> browseToolDefinitions;
	private List<Map<String, Object>> cartToolDefinitions;

	@PostConstruct
	public void init()
	{
		// Build lookup map
		toolHandlerMap = toolHandlers.stream()
			.collect(Collectors.toMap(McpToolHandler::getName, h -> h));

		// Build OpenAI function definitions
		openAiToolDefinitions = toolHandlers.stream()
			.map(this::buildToolDefinition)
			.collect(Collectors.toList());

		// Pre-build filtered tool lists per intent
		browseToolDefinitions = toolHandlers.stream()
			.filter(h -> BROWSE_TOOLS.contains(h.getName()))
			.map(this::buildToolDefinition)
			.collect(Collectors.toList());

		cartToolDefinitions = toolHandlers.stream()
			.filter(h -> CART_TOOLS.contains(h.getName()))
			.map(this::buildToolDefinition)
			.collect(Collectors.toList());

		LOG.info("Intent tool groups — browse: {} tools, cart: {} tools, checkout: {} tools",
			browseToolDefinitions.size(), cartToolDefinitions.size(), openAiToolDefinitions.size());
	}

	private Map<String, Object> buildToolDefinition(final McpToolHandler handler)
	{
		final Map<String, Object> tool = new LinkedHashMap<>();
		tool.put("type", "function");
		tool.put("function", Map.of(
			"name", handler.getName(),
			"description", handler.getDescription(),
			"parameters", handler.getInputSchema()
		));
		return tool;
	}

	@SuppressWarnings("unchecked")
	private String classifyIntent(final List<Map<String, Object>> messages)
	{
		try
		{
			// Find the last user message
			String lastUserMessage = "";
			for (int i = messages.size() - 1; i >= 0; i--)
			{
				if ("user".equals(messages.get(i).get("role")))
				{
					lastUserMessage = (String) messages.get(i).get("content");
					break;
				}
			}

			if (lastUserMessage.isBlank())
			{
				return "browse";
			}

			final String intentModel = resolveIntentModel();

			final List<Map<String, Object>> intentMessages = List.of(
				Map.of("role", "system", "content", INTENT_PROMPT),
				Map.of("role", "user", "content", lastUserMessage)
			);

			final Map<String, Object> response = llmClient.chatCompletion(intentMessages, null, intentModel);
			final List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
			if (choices != null && !choices.isEmpty())
			{
				final Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
				final String content = ((String) message.getOrDefault("content", "")).trim().toLowerCase();
				if (Set.of("browse", "cart", "checkout").contains(content))
				{
					return content;
				}
				LOG.warn("Intent classification returned unexpected value: '{}', defaulting to browse", content);
			}
		}
		catch (final Exception e)
		{
			LOG.warn("Intent classification failed, defaulting to browse: {}", e.getMessage());
		}
		return "browse";
	}

	private List<Map<String, Object>> toolsForIntent(final String intent)
	{
		switch (intent)
		{
			case "cart":
				return cartToolDefinitions;
			case "checkout":
				return openAiToolDefinitions;
			default:
				return browseToolDefinitions;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> chat(final List<Map<String, Object>> messages)
	{
		// Classify intent and select filtered tools
		final String intent = classifyIntent(messages);
		final List<Map<String, Object>> tools = toolsForIntent(intent);
		LOG.info("Classified intent: {} — sending {} tool definitions", intent, tools.size());

		// Build the full conversation with system prompt
		final List<Map<String, Object>> fullMessages = new ArrayList<>();
		fullMessages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
		fullMessages.addAll(messages);

		String uiAction = null;
		final Set<String> seenInvocations = new HashSet<>();

		int iterations = 0;
		while (iterations < MAX_TOOL_ITERATIONS)
		{
			iterations++;
			final Map<String, Object> response = llmClient.chatCompletion(fullMessages, tools);

			final List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
			if (choices == null || choices.isEmpty())
			{
				throw new RuntimeException("No choices in OpenAI response");
			}

			final Map<String, Object> choice = choices.get(0);
			final Map<String, Object> assistantMessage = (Map<String, Object>) choice.get("message");
			final String finishReason = (String) choice.get("finish_reason");

			// Add assistant message to conversation
			fullMessages.add(assistantMessage);

			// If no tool calls, we're done
			if (!"tool_calls".equals(finishReason) || !assistantMessage.containsKey("tool_calls"))
			{
				final String reply = (String) assistantMessage.getOrDefault("content", "");

				// Return the result: the reply plus the full message history (minus system prompt)
				final Map<String, Object> result = new LinkedHashMap<>();
				result.put("reply", reply);
				result.put("messages", summarizeHistoryForClient(fullMessages.subList(1, fullMessages.size())));
				if (uiAction != null)
				{
					result.put("action", uiAction);
				}
				return result;
			}

			// Process tool calls
			final List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistantMessage.get("tool_calls");
			boolean allDuplicates = !toolCalls.isEmpty();
			for (final Map<String, Object> toolCall : toolCalls)
			{
				final String toolCallId = (String) toolCall.get("id");
				final Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
				final String toolName = (String) function.get("name");
				final String argsJson = (String) function.get("arguments");

				final String invocationKey = toolName + "|" + argsJson;
				final boolean isDuplicate = !seenInvocations.add(invocationKey);

				String toolResultContent;
				if (isDuplicate)
				{
					LOG.info("Skipping duplicate tool invocation: {} {}", toolName, argsJson);
					toolResultContent = "This tool was already called with the same arguments in this conversation. "
						+ "Reuse the previous result instead of calling it again.";
				}
				else
				{
					allDuplicates = false;
					LOG.info("Agent executing tool: {} with args: {}", toolName, argsJson);
					try
					{
						final Map<String, Object> toolArgs = objectMapper.readValue(argsJson, Map.class);

						// Capture UI actions
						if ("ui_action".equals(toolName))
						{
							uiAction = (String) toolArgs.get("action");
						}

						final McpToolHandler handler = toolHandlerMap.get(toolName);

						if (handler == null)
						{
							toolResultContent = "Unknown tool: " + toolName;
						}
						else
						{
							final McpToolResult toolResult = handler.execute(toolArgs);
							toolResultContent = toolResult.getContent();
						}
					}
					catch (final Exception e)
					{
						LOG.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
						toolResultContent = "Tool error: " + e.getMessage();
					}
				}

				// Add tool result message
				final Map<String, Object> toolResultMessage = new LinkedHashMap<>();
				toolResultMessage.put("role", "tool");
				toolResultMessage.put("tool_call_id", toolCallId);
				toolResultMessage.put("content", toolResultContent);
				fullMessages.add(toolResultMessage);
			}

			if (allDuplicates)
			{
				LOG.warn("Agent attempted only duplicate tool calls in iteration {}; breaking tool loop", iterations);
				break;
			}
		}

		// Max iterations reached, or all tool calls were duplicates
		final Map<String, Object> result = new LinkedHashMap<>();
		result.put("reply", "I apologize, but I'm having trouble completing your request. Could you try rephrasing it?");
		result.put("messages", summarizeHistoryForClient(fullMessages.subList(1, fullMessages.size())));
		return result;
	}

	private List<Map<String, Object>> summarizeHistoryForClient(final List<Map<String, Object>> history)
	{
		final List<Map<String, Object>> out = new ArrayList<>(history.size());
		for (final Map<String, Object> message : history)
		{
			if (!"tool".equals(message.get("role")))
			{
				out.add(message);
				continue;
			}
			final String content = (String) message.getOrDefault("content", "");
			if (content.length() <= TOOL_RESULT_SUMMARY_THRESHOLD)
			{
				out.add(message);
				continue;
			}
			final Map<String, Object> summarized = new LinkedHashMap<>(message);
			summarized.put("content", "[previous tool result summarized; " + content.length()
				+ " chars omitted] " + content.substring(0, TOOL_RESULT_SUMMARY_SNIPPET) + "...");
			out.add(summarized);
		}
		return out;
	}

	private String resolveIntentModel()
	{
		switch (Config.getString("coremcp.llm.provider", "openai").trim().toLowerCase())
		{
			case "anthropic":
				return Config.getString("coremcp.anthropic.intent.model", "claude-3-5-haiku-latest");
			case "openai-compatible":
				return Config.getString("coremcp.openai-compatible.intent.model", "gpt-4o-mini");
			default:
				return Config.getString("coremcp.openai.intent.model", "gpt-4o-mini");
		}
	}

	@Required
	public void setToolHandlers(final List<McpToolHandler> toolHandlers)
	{
		this.toolHandlers = toolHandlers;
	}

	@Required
	public void setLlmClient(final LlmClient llmClient)
	{
		this.llmClient = llmClient;
	}
}
