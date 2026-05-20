package com.coremcp.services;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for provider-specific LLM execution.
 */
public interface LlmProvider
{
	String getProviderId();

	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
		String modelOverride);
}
