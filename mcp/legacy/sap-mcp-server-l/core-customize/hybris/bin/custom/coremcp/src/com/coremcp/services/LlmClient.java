package com.coremcp.services;

import java.util.List;
import java.util.Map;

/**
 * Provider-neutral interface for chat completion style LLM calls.
 */
public interface LlmClient
{
	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
		String modelOverride);
}
