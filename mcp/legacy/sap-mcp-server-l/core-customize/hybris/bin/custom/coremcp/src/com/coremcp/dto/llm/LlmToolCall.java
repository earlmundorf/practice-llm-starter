package com.coremcp.dto.llm;

/**
 * One tool call requested by the LLM, extracted from the normalized
 * (OpenAI-shaped) chat completion response by {@link LlmChatResponse#parse}.
 */
public final class LlmToolCall
{
	private final String id;
	private final String name;
	private final String argumentsJson;

	public LlmToolCall(final String id, final String name, final String argumentsJson)
	{
		this.id = id;
		this.name = name;
		this.argumentsJson = argumentsJson;
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public String getArgumentsJson()
	{
		return argumentsJson;
	}

	/** Key used for duplicate-invocation detection within one agent turn. */
	public String invocationKey()
	{
		return name + "|" + argumentsJson;
	}
}
