package com.coremcp.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.dto.llm.LlmToolCall;
import com.coremcp.services.AgentToolInvoker;
import com.coremcp.services.AgentTurnContext;
import com.coremcp.services.EntityRefCollector;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Default {@link AgentToolInvoker}. Behavior contract (pinned by DefaultAgentServiceTest):
 * duplicate calls (same tool + same args within one turn) are not re-executed; ui_action
 * is captured on the context rather than executed; handler failures become a "Tool error"
 * result message and never abort the turn.
 */
public class DefaultAgentToolInvoker implements AgentToolInvoker
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentToolInvoker.class);

	private List<McpToolHandler> toolHandlers;
	private EntityRefCollector entityRefCollector;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private Map<String, McpToolHandler> toolHandlerMap;

	@PostConstruct
	public void init()
	{
		toolHandlerMap = toolHandlers.stream()
			.collect(Collectors.toMap(McpToolHandler::getName, h -> h));
	}

	@Override
	public Outcome invoke(final LlmToolCall toolCall, final AgentTurnContext context,
		final Consumer<String> toolEventConsumer)
	{
		final String toolName = toolCall.getName();
		final String argsJson = toolCall.getArgumentsJson();

		final boolean isDuplicate = context.markInvocation(toolCall.invocationKey());

		String toolResultContent;
		if (isDuplicate)
		{
			LOG.info("Skipping duplicate tool invocation: {} {}", toolName, argsJson);
			toolResultContent = "This tool was already called with the same arguments in this conversation. "
				+ "Reuse the previous result instead of calling it again.";
		}
		else
		{
			LOG.info("Agent executing tool: {} with args: {}", toolName, argsJson);
			// Notify the streaming caller that a tool is starting so the UI can render
			// a transient "Looking up..." status while we wait for the round-trip. We
			// suppress ui_action since it isn't user-visible work.
			if (toolEventConsumer != null && !"ui_action".equals(toolName))
			{
				try
				{
					toolEventConsumer.accept(toolName);
				}
				catch (final Exception e)
				{
					LOG.debug("Tool event consumer failed for {}: {}", toolName, e.getMessage());
				}
			}
			try
			{
				@SuppressWarnings("unchecked")
				final Map<String, Object> toolArgs = objectMapper.readValue(argsJson, Map.class);

				// Capture UI actions
				if ("ui_action".equals(toolName))
				{
					context.setUiAction((String) toolArgs.get("action"));
				}

				final McpToolHandler handler = toolHandlerMap.get(toolName);

				if (handler == null)
				{
					toolResultContent = "Unknown tool: " + toolName;
				}
				else
				{
					final long toolStartNs = System.nanoTime();
					final McpToolResult toolResult = handler.execute(toolArgs);
					toolResultContent = toolResult.getContent();
					LOG.info("[perf] tool={} bytes={} durationMs={}",
						toolName, toolResultContent == null ? 0 : toolResultContent.length(),
						(System.nanoTime() - toolStartNs) / 1_000_000L);
					entityRefCollector.collect(toolName, toolArgs, toolResultContent, context);
				}
			}
			catch (final Exception e)
			{
				LOG.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
				toolResultContent = "Tool error: " + e.getMessage();
			}
		}

		final Map<String, Object> toolResultMessage = new LinkedHashMap<>();
		toolResultMessage.put("role", "tool");
		toolResultMessage.put("tool_call_id", toolCall.getId());
		toolResultMessage.put("content", toolResultContent);
		return new Outcome(toolResultMessage, isDuplicate);
	}

	@Required
	public void setToolHandlers(final List<McpToolHandler> toolHandlers)
	{
		this.toolHandlers = toolHandlers;
	}

	@Required
	public void setEntityRefCollector(final EntityRefCollector entityRefCollector)
	{
		this.entityRefCollector = entityRefCollector;
	}
}
