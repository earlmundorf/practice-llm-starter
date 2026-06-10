package com.coremcp.dto.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Typed view over the normalized (OpenAI-shaped) chat completion response that
 * every LlmProvider returns. {@link #parse} does all the shape validation and
 * casting in one place — callers get typed accessors instead of cast chains.
 *
 * The raw assistant message map is retained ({@link #getAssistantMessage()})
 * because it is echoed back into the conversation history sent to the LLM on
 * the next round, and the wire shape must be preserved exactly.
 */
public final class LlmChatResponse
{
	private final Map<String, Object> assistantMessage;
	private final String finishReason;
	private final String content;
	private final List<LlmToolCall> toolCalls;

	private LlmChatResponse(final Map<String, Object> assistantMessage, final String finishReason,
		final String content, final List<LlmToolCall> toolCalls)
	{
		this.assistantMessage = assistantMessage;
		this.finishReason = finishReason;
		this.content = content;
		this.toolCalls = toolCalls;
	}

	/**
	 * @throws IllegalStateException when the response does not have the expected shape
	 */
	public static LlmChatResponse parse(final Map<String, Object> response)
	{
		final Object choicesObj = response == null ? null : response.get("choices");
		if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty())
		{
			throw new IllegalStateException("No choices in LLM response");
		}
		final Object choiceObj = ((List<?>) choicesObj).get(0);
		if (!(choiceObj instanceof Map))
		{
			throw new IllegalStateException("LLM response choice is not an object");
		}
		final Map<String, Object> choice = castMap(choiceObj);

		final Object messageObj = choice.get("message");
		if (!(messageObj instanceof Map))
		{
			throw new IllegalStateException("LLM response choice has no message object");
		}
		final Map<String, Object> message = castMap(messageObj);

		final String content = message.get("content") instanceof String ? (String) message.get("content") : "";

		final List<LlmToolCall> toolCalls = new ArrayList<>();
		if (message.get("tool_calls") instanceof List)
		{
			for (final Object toolCallObj : (List<?>) message.get("tool_calls"))
			{
				if (!(toolCallObj instanceof Map))
				{
					continue;
				}
				final Map<String, Object> toolCall = castMap(toolCallObj);
				final Object functionObj = toolCall.get("function");
				if (!(functionObj instanceof Map))
				{
					continue;
				}
				final Map<String, Object> function = castMap(functionObj);
				toolCalls.add(new LlmToolCall(
					asString(toolCall.get("id")),
					asString(function.get("name")),
					asString(function.get("arguments"))));
			}
		}

		return new LlmChatResponse(message, asString(choice.get("finish_reason")), content,
			Collections.unmodifiableList(toolCalls));
	}

	/** The raw assistant message map — append this to the conversation history verbatim. */
	public Map<String, Object> getAssistantMessage()
	{
		return assistantMessage;
	}

	public String getFinishReason()
	{
		return finishReason;
	}

	/** Text content of the assistant message ("" when absent or non-textual). */
	public String getContent()
	{
		return content;
	}

	public List<LlmToolCall> getToolCalls()
	{
		return toolCalls;
	}

	/** True when the model finished by requesting tool calls (and the message carries them). */
	public boolean hasToolCalls()
	{
		return "tool_calls".equals(finishReason) && assistantMessage.containsKey("tool_calls");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(final Object value)
	{
		return (Map<String, Object>) value;
	}

	private static String asString(final Object value)
	{
		return value == null ? "" : String.valueOf(value);
	}
}
