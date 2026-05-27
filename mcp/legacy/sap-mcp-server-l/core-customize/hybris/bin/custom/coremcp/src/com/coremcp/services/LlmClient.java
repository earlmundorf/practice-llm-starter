package com.coremcp.services;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provider-neutral interface for chat completion style LLM calls.
 */
public interface LlmClient
{
	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
		String modelOverride);

	/**
	 * Streaming variant — emits text deltas as they arrive and returns the full normalized
	 * response when done. Providers that can't stream fall back to non-streaming and emit a
	 * single chunk; callers don't need to special-case that.
	 */
	Map<String, Object> chatCompletionStream(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
		Consumer<String> textDeltaConsumer);

	/**
	 * Whether the currently configured provider/model can accept image content in user messages.
	 * Controlled per provider by the {@code coremcp.<provider>.vision.enabled} property.
	 */
	boolean supportsVision();
}
