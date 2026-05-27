package com.coremcp.services;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Strategy interface for provider-specific LLM execution.
 */
public interface LlmProvider
{
	String getProviderId();

	Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
		String modelOverride);

	/**
	 * Streaming variant. Implementations should emit text-delta chunks via
	 * {@code textDeltaConsumer} as they arrive from the provider, and return the same
	 * normalized response shape as {@link #chatCompletion} when finished. The default
	 * implementation falls back to non-streaming and emits the full text as a single
	 * chunk at the end — providers that can't stream (or whose gateway strips streaming)
	 * inherit this behavior automatically.
	 */
	@SuppressWarnings("unchecked")
	default Map<String, Object> chatCompletionStream(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride,
		final Consumer<String> textDeltaConsumer)
	{
		final Map<String, Object> result = chatCompletion(messages, tools, modelOverride);
		try
		{
			final List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
			if (choices != null && !choices.isEmpty())
			{
				final Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
				final Object content = message == null ? null : message.get("content");
				if (content instanceof String && !((String) content).isEmpty())
				{
					textDeltaConsumer.accept((String) content);
				}
			}
		}
		catch (final Exception ignored)
		{
			// If anything goes sideways extracting text, the caller still gets the full result.
		}
		return result;
	}
}
