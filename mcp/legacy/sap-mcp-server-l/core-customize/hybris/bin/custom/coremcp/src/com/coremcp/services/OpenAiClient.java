package com.coremcp.services;

import java.util.List;
import java.util.Map;

/**
 * Interface for OpenAI Chat Completions API calls.
 * Abstracted to allow mocking in tests and swapping implementations.
 */
public interface OpenAiClient
{
	/**
	 * Call Chat Completions API using the default model from config.
	 *
	 * @param messages conversation messages
	 * @param tools    tool definitions (OpenAI function calling format), may be null
	 * @return parsed response body
	 */
	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

	/**
	 * Call Chat Completions API with an optional model override.
	 *
	 * @param messages      conversation messages
	 * @param tools         tool definitions, may be null
	 * @param modelOverride model to use instead of configured default, may be null
	 * @return parsed response body
	 */
	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
		String modelOverride);
}
