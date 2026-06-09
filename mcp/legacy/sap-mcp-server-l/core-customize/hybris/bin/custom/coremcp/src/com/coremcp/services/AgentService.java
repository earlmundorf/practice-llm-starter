package com.coremcp.services;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service interface for the AI shopping agent. Takes a conversation history
 * and returns an updated conversation with the assistant's response.
 */
public interface AgentService
{
	/**
	 * Process a chat conversation through the AI agent. The agent may invoke
	 * tool calls (via commerce facades) in a loop before returning.
	 *
	 * @param messages List of message maps with "role" and "content" keys
	 * @return Updated message list including the assistant's response
	 */
	Map<String, Object> chat(List<Map<String, Object>> messages);

	/**
	 * Streaming variant. Emits text deltas through {@code textDeltaConsumer} as they arrive
	 * from the LLM, and notifies {@code toolEventConsumer} with the tool name each time the
	 * agent starts executing a tool call (so the UI can render a "Looking up..." status
	 * while waiting). Returns the same shape as {@link #chat} when finished.
	 */
	Map<String, Object> chatStream(List<Map<String, Object>> messages,
		Consumer<String> textDeltaConsumer,
		Consumer<String> toolEventConsumer);
}
