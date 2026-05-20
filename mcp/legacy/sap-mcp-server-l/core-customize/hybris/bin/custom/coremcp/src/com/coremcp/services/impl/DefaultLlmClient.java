package com.coremcp.services.impl;

import com.coremcp.services.LlmClient;
import com.coremcp.services.LlmProvider;

import de.hybris.platform.util.Config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes chat completion requests to the provider selected by {@code coremcp.llm.provider}
 * in local.properties (default: {@code openai}). The id must match one of the registered
 * providers' {@link LlmProvider#getProviderId()} values.
 */
public class DefaultLlmClient implements LlmClient
{
	private Map<String, LlmProvider> providers = Map.of();

	@Override
	public Map<String, Object> chatCompletion(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools)
	{
		return chatCompletion(messages, tools, null);
	}

	@Override
	public Map<String, Object> chatCompletion(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride)
	{
		return getProvider().chatCompletion(messages, tools, modelOverride);
	}

	public void setProviders(final List<LlmProvider> providerList)
	{
		final Map<String, LlmProvider> mapped = new LinkedHashMap<>();
		for (final LlmProvider provider : providerList)
		{
			mapped.put(provider.getProviderId(), provider);
		}
		this.providers = mapped;
	}

	protected LlmProvider getProvider()
	{
		final String providerId = Config.getString("coremcp.llm.provider", "openai").trim().toLowerCase();
		final LlmProvider provider = providers.get(providerId);
		if (provider == null)
		{
			throw new IllegalStateException("Unsupported LLM provider: " + providerId
				+ ". Available providers: " + providers.keySet());
		}
		return provider;
	}

	@Override
	public boolean supportsVision()
	{
		final String providerId = Config.getString("coremcp.llm.provider", "openai").trim().toLowerCase();
		// openai-compatible defaults to false because the configured model is often a self-hosted
		// text-only build. openai/anthropic default to true since their flagship models support vision.
		final boolean defaultEnabled = !"openai-compatible".equals(providerId);
		return Config.getBoolean("coremcp." + providerId + ".vision.enabled", defaultEnabled);
	}
}
