package com.coremcp.services.impl;

import com.coremcp.dto.llm.LlmChatResponse;
import com.coremcp.dto.llm.LlmToolCall;
import com.coremcp.services.AgentService;
import com.coremcp.services.AgentStateSnapshotBuilder;
import com.coremcp.services.AgentToolInvoker;
import com.coremcp.services.AgentTurnContext;
import com.coremcp.services.LlmClient;
import com.coremcp.tools.McpToolHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Orchestrates the agent turn: message assembly (persona prompt + state snapshot +
 * history), the bounded LLM/tool loop, and result shaping for the client. Tool
 * execution lives in {@link AgentToolInvoker}; the state snapshot in
 * {@link AgentStateSnapshotBuilder}.
 */
public class DefaultAgentService implements AgentService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentService.class);

	// Defaults for the tunables below live in project.properties (coremcp.agent.*) and are
	// injected via coremcp-spring.xml.
	private int maxToolIterations = 10;

	// Above this size, tool result payloads are replaced with a short snippet before being
	// returned to the client (and thus echoed back on the next turn). Keeps long conversations
	// from ballooning over time. The agent's own in-progress tool loop still sees full payloads.
	private int toolResultSummaryThreshold = 300;
	private int toolResultSummarySnippet = 200;

	private static final String SYSTEM_PROMPT =
		"You are a knowledgeable friend who happens to know the ThinkShop electronics catalog (powered by " +
		"SAP Commerce). Talk like a person who genuinely enjoys this stuff — share observations, opinions, " +
		"useful context, the kind of thing a friend would say. When something we sell is actually relevant " +
		"to what the user is talking about, mention it lightly and link it back; when it isn't, just chat. " +
		"Never reflexively pivot every reply to a product pitch.\n\n" +
		"You can also help with the practical shopping flow: browsing, managing the cart, applying coupons, " +
		"and completing purchases. When a customer wants to buy something, guide them through: " +
		"add to cart → set delivery address → set delivery mode → set payment → place order. " +
		"When showing products, include the product code, name, and price.\n\n" +
		"LINKS: Don't try to render URLs in your replies. The chat UI automatically attaches " +
		"clickable chips below your message for any products, orders, or order-history pages your " +
		"tool calls touched — the user can open them from there. Just describe the entity in " +
		"normal text (e.g., \"your most recent order is #THINK-0001\") and let the UI handle " +
		"navigation.\n\n" +
		"IMAGES: When the user attaches an image, look at it the way a friend would. Describe what you see " +
		"naturally — what it looks like, what it's probably for, a thought or observation about it. If a " +
		"product in the image (or something close to it) seems like something the user might want to find " +
		"in the catalog, call product_search lightly to see what we have and mention it as a suggestion, " +
		"not a sales pitch. If we don't sell anything similar, that's fine — keep chatting about the " +
		"image itself. If the image is a receipt, order confirmation, or any document with an order code, " +
		"read the order code and call order_get to help them with that order. If you cannot tell what the " +
		"image is for, ask a brief friendly clarifying question.\n\n" +
		"PROMOTIONS & COUPONS: Use the promotions_get tool to check active promotions and coupons. " +
		"The tool returns per-customer redemption data including 'currentUserRedemptions' which tells you " +
		"how many times THIS customer has used a coupon. Compare it against 'maxRedemptionsPerCustomer' to " +
		"determine eligibility. ALWAYS call promotions_get when the customer asks about coupon eligibility, " +
		"whether they've used a coupon, or if a deal applies to them — never guess from order history.\n\n" +
		"KNOWLEDGE BASE (policies, events, how-tos, brand): Use the info_search tool whenever the user " +
		"asks anything that isn't about a specific product, order, or cart action — returns/refunds, " +
		"shipping, warranty, privacy, payment methods, contact info, loyalty program, upcoming events, " +
		"current sales/promos, sustainability, about ThinkShop, or step-by-step help like 'how do I track " +
		"an order' or 'how do I start a return'. Pass the user's question (or key phrase) as the query — " +
		"don't invent uids. If info_search returns a relevant entry, answer from its summary/body and cite " +
		"the title naturally; only fall back to a generic answer if it returns nothing. NEVER tell the user " +
		"the policy 'isn't available' without first calling info_search.\n\n" +
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
	private AgentStateSnapshotBuilder stateSnapshotBuilder;
	private AgentToolInvoker toolInvoker;

	private List<Map<String, Object>> openAiToolDefinitions;

	@PostConstruct
	public void init()
	{
		openAiToolDefinitions = toolHandlers.stream()
			.map(this::buildToolDefinition)
			.collect(Collectors.toList());

		LOG.info("Agent initialized with {} tool definitions", openAiToolDefinitions.size());
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

	private static long elapsedMs(final long startNs)
	{
		return (System.nanoTime() - startNs) / 1_000_000L;
	}

	@Override
	public Map<String, Object> chat(final List<Map<String, Object>> messages)
	{
		return runChat(messages, null, null);
	}

	@Override
	public Map<String, Object> chatStream(final List<Map<String, Object>> messages,
		final Consumer<String> textDeltaConsumer,
		final Consumer<String> toolEventConsumer)
	{
		return runChat(messages, textDeltaConsumer, toolEventConsumer);
	}

	private Map<String, Object> runChat(final List<Map<String, Object>> messages,
		final Consumer<String> textDeltaConsumer,
		final Consumer<String> toolEventConsumer)
	{
		final boolean streaming = textDeltaConsumer != null;
		final long turnStartNs = System.nanoTime();
		// Send the full tool set every turn. We previously ran a Haiku classifier to filter
		// tools per intent (browse/cart/checkout) but it added 1.3–2.3 s of latency AND caused
		// the Anthropic prompt cache to thrash whenever the user shifted intents. Sonnet picks
		// tools fine from the full list; cache stays warm; net win on both latency and cost.
		final List<Map<String, Object>> tools = openAiToolDefinitions;

		// If the provider doesn't support vision, strip any image content blocks defensively so the
		// LLM doesn't choke on multimodal arrays. The UI is supposed to gate this via the capabilities
		// endpoint, but stale clients can still send images.
		final List<Map<String, Object>> incoming = llmClient.supportsVision()
			? messages
			: pruneImageContent(messages);

		// Build the full conversation: persona prompt, fresh state snapshot, then conversation history.
		// The persona prompt is stable (good for OpenAI's prefix cache); the state block changes per
		// turn but gives the model cart/customer context without burning round-trips on cart_get /
		// customer_get.
		final List<Map<String, Object>> fullMessages = new ArrayList<>();
		fullMessages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
		fullMessages.add(Map.of("role", "system", "content", stateSnapshotBuilder.buildStateSnapshotMessage()));
		fullMessages.addAll(incoming);

		final AgentTurnContext context = new AgentTurnContext();

		int iterations = 0;
		while (iterations < maxToolIterations)
		{
			iterations++;
			final long llmStartNs = System.nanoTime();
			final Map<String, Object> response = streaming
				? llmClient.chatCompletionStream(fullMessages, tools, textDeltaConsumer)
				: llmClient.chatCompletion(fullMessages, tools);
			LOG.info("[perf] llmRound iter={} streaming={} durationMs={}",
				iterations, streaming, elapsedMs(llmStartNs));

			final LlmChatResponse parsed = LlmChatResponse.parse(response);

			// Add assistant message to conversation
			fullMessages.add(parsed.getAssistantMessage());

			// If no tool calls, we're done
			if (!parsed.hasToolCalls())
			{
				final Map<String, Object> result = buildResult(parsed.getContent(), fullMessages, context);
				LOG.info("[perf] turn=complete iterations={} entityRefs={} durationMs={}",
					iterations, context.getEntityRefs().size(), elapsedMs(turnStartNs));
				return result;
			}

			// Process tool calls
			boolean allDuplicates = !parsed.getToolCalls().isEmpty();
			for (final LlmToolCall toolCall : parsed.getToolCalls())
			{
				final AgentToolInvoker.Outcome outcome = toolInvoker.invoke(toolCall, context, toolEventConsumer);
				if (!outcome.isDuplicate())
				{
					allDuplicates = false;
				}
				fullMessages.add(outcome.getToolResultMessage());
			}

			if (allDuplicates)
			{
				LOG.warn("Agent attempted only duplicate tool calls in iteration {}; breaking tool loop", iterations);
				break;
			}
		}

		// Max iterations reached, or all tool calls were duplicates
		final Map<String, Object> result = buildResult(
			"I apologize, but I'm having trouble completing your request. Could you try rephrasing it?",
			fullMessages, context);
		result.remove("action"); // the fallback reply never carries a ui action
		LOG.info("[perf] turn=maxIter iterations={} durationMs={}",
			maxToolIterations, elapsedMs(turnStartNs));
		return result;
	}

	/** Shape the client-facing result: reply + summarized history (minus the 2 system messages) + extras. */
	private Map<String, Object> buildResult(final String reply, final List<Map<String, Object>> fullMessages,
		final AgentTurnContext context)
	{
		final Map<String, Object> result = new LinkedHashMap<>();
		result.put("reply", reply);
		result.put("messages",
			summarizeHistoryForClient(pruneImageContent(fullMessages.subList(2, fullMessages.size()))));
		if (context.getUiAction() != null)
		{
			result.put("action", context.getUiAction());
		}
		if (!context.getEntityRefs().isEmpty())
		{
			result.put("entityRefs", context.getEntityRefs());
		}
		return result;
	}

	/**
	 * Drop image_url content blocks from any multimodal user messages. Used both defensively
	 * before sending to a non-vision provider and on the return path so images don't persist in
	 * the conversation history echoed back to the client. Per-turn image use only — no storage.
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> pruneImageContent(final List<Map<String, Object>> messages)
	{
		final List<Map<String, Object>> out = new ArrayList<>(messages.size());
		for (final Map<String, Object> message : messages)
		{
			final Object content = message.get("content");
			if (!(content instanceof List))
			{
				out.add(message);
				continue;
			}
			final List<Object> filtered = new ArrayList<>();
			for (final Object block : (List<Object>) content)
			{
				if (block instanceof Map && "image_url".equals(((Map<String, Object>) block).get("type")))
				{
					continue;
				}
				filtered.add(block);
			}
			final Map<String, Object> copy = new LinkedHashMap<>(message);
			if (filtered.isEmpty())
			{
				copy.put("content", "[image]");
			}
			else if (filtered.size() == 1 && filtered.get(0) instanceof Map
				&& "text".equals(((Map<String, Object>) filtered.get(0)).get("type")))
			{
				copy.put("content", ((Map<String, Object>) filtered.get(0)).get("text"));
			}
			else
			{
				copy.put("content", filtered);
			}
			out.add(copy);
		}
		return out;
	}

	private static final java.util.regex.Pattern URL_FIELD_PATTERN =
		java.util.regex.Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

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
			if (content.length() <= toolResultSummaryThreshold)
			{
				out.add(message);
				continue;
			}
			final Map<String, Object> summarized = new LinkedHashMap<>(message);
			summarized.put("content", "[previous tool result summarized; " + content.length()
				+ " chars omitted] " + content.substring(0, toolResultSummarySnippet) + "..."
				+ extractDeepLinks(content));
			out.add(summarized);
		}
		return out;
	}

	/**
	 * Pull every "url":"..." value out of the original tool-result JSON and append it to the
	 * summary, so deep links survive truncation and are still available to the agent on
	 * subsequent turns.
	 */
	private String extractDeepLinks(final String json)
	{
		final java.util.regex.Matcher m = URL_FIELD_PATTERN.matcher(json);
		final List<String> urls = new ArrayList<>();
		while (m.find())
		{
			final String url = m.group(1);
			if (!urls.contains(url))
			{
				urls.add(url);
			}
		}
		if (urls.isEmpty())
		{
			return "";
		}
		return " [preserved deep links: " + String.join(", ", urls) + "]";
	}

	public void setMaxToolIterations(final int maxToolIterations)
	{
		this.maxToolIterations = maxToolIterations;
	}

	public void setToolResultSummaryThreshold(final int toolResultSummaryThreshold)
	{
		this.toolResultSummaryThreshold = toolResultSummaryThreshold;
	}

	public void setToolResultSummarySnippet(final int toolResultSummarySnippet)
	{
		this.toolResultSummarySnippet = toolResultSummarySnippet;
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

	@Required
	public void setStateSnapshotBuilder(final AgentStateSnapshotBuilder stateSnapshotBuilder)
	{
		this.stateSnapshotBuilder = stateSnapshotBuilder;
	}

	@Required
	public void setToolInvoker(final AgentToolInvoker toolInvoker)
	{
		this.toolInvoker = toolInvoker;
	}
}
