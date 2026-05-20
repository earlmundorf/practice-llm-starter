package com.coremcp.services.impl;

import com.coremcp.services.LlmClient;
import com.coremcp.services.LlmProvider;
import com.coremcp.services.OpenAiClient;

import de.hybris.platform.util.Config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes chat completion requests to the configured provider.
 */
public class DefaultLlmClient implements LlmClient, OpenAiClient
{
	private static final String DEFAULT_PROVIDER = "openai";
	private static final String PROVIDER_PROPERTY = "coremcp.llm.provider";

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
		final Map<String, LlmProvider> mappedProviders = new LinkedHashMap<>();
		for (final LlmProvider provider : providerList)
		{
			mappedProviders.put(provider.getProviderId(), provider);
		}
		this.providers = mappedProviders;
	}

	protected LlmProvider getProvider()
	{
		final String configuredProvider = normalizeProvider(resolveConfigOrEnvValue(Config.getParameter(PROVIDER_PROPERTY)));
		final String providerId = configuredProvider != null ? configuredProvider : defaultProviderFromEnv();
		final LlmProvider provider = providers.get(providerId);
		if (provider == null)
		{
			throw new IllegalStateException("Unsupported LLM provider: " + providerId + ". Available providers: "
				+ providers.keySet());
		}
		return provider;
	}

	private String defaultProviderFromEnv()
	{
		final String envProvider = normalizeProvider(System.getenv("COREMCP_LLM_PROVIDER"));
		return envProvider != null ? envProvider : DEFAULT_PROVIDER;
	}

	private String normalizeProvider(final String provider)
	{
		if (provider == null)
		{
			return null;
		}
		final String normalized = provider.trim().toLowerCase();
		return normalized.isEmpty() ? null : normalized;
	}

	private String resolveConfigOrEnvValue(final String configuredValue)
	{
		return AbstractOpenAiCompatibleLlmProvider.resolveConfigOrEnvValue(configuredValue, null);
	}
}
