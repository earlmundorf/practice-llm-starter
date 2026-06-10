package com.coremcp.services;

import com.coremcp.dto.llm.LlmToolCall;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Executes a single LLM-requested tool call: duplicate detection, ui_action
 * capture, handler dispatch, entity-ref collection, and error containment.
 */
public interface AgentToolInvoker
{
	/**
	 * @param toolCall the LLM-requested call
	 * @param context per-turn state (duplicates, entity refs, ui action)
	 * @param toolEventConsumer optional UI notification of tool starts (may be null)
	 */
	Outcome invoke(LlmToolCall toolCall, AgentTurnContext context, Consumer<String> toolEventConsumer);

	/** Result of one tool invocation: the tool message to append, and whether it was a duplicate. */
	final class Outcome
	{
		private final Map<String, Object> toolResultMessage;
		private final boolean duplicate;

		public Outcome(final Map<String, Object> toolResultMessage, final boolean duplicate)
		{
			this.toolResultMessage = toolResultMessage;
			this.duplicate = duplicate;
		}

		public Map<String, Object> getToolResultMessage()
		{
			return toolResultMessage;
		}

		public boolean isDuplicate()
		{
			return duplicate;
		}
	}
}
