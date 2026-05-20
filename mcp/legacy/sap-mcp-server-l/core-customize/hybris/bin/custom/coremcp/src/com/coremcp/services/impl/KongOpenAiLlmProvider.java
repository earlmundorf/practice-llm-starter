package com.coremcp.services.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kong-routed provider using an OpenAI-compatible chat completions endpoint.
 */
public class KongOpenAiLlmProvider extends AbstractOpenAiCompatibleLlmProvider
{
	private static final Logger LOG = LoggerFactory.getLogger(KongOpenAiLlmProvider.class);

	@Override
	public String getProviderId()
	{
		return "kong-openai";
	}

	@Override
	protected String getApiKeyProperty()
	{
		return "coremcp.kong.apikey";
	}

	@Override
	protected String getApiKeyEnvVar()
	{
		return "KONG_LLM_API_KEY";
	}

	@Override
	protected String getDefaultModelProperty()
	{
		return "coremcp.kong.model";
	}

	@Override
	protected String getDefaultModel()
	{
		return "gpt-4o";
	}

	@Override
	protected String getBaseUrlProperty()
	{
		return "coremcp.kong.baseurl";
	}

	@Override
	protected String getCompletionsPath()
	{
		return "/v1/chat/completions";
	}

	@Override
	protected String getConfiguredCompletionsPath()
	{
		final String completionsPath = resolveConfigOrEnvValue(Config.getParameter("coremcp.kong.chat.completions.path"));
		LOG.info("Kong OpenAI completions path: {}", resolveConfigOrEnvValue(completionsPath, null));
		return completionsPath;
	}

	@Override
	protected String getDefaultApiUrl()
	{
		final String baseUrl = resolveConfigOrEnvValue(Config.getParameter("coremcp.kong.baseurl"), null);
		if (baseUrl == null || baseUrl.isBlank())
		{
			throw new IllegalStateException("Kong base URL not found. Set coremcp.kong.baseurl in local.properties");
		}

		LOG.warn("Kong OpenAI base URL: {}", baseUrl);
		return trimTrailingSlash(baseUrl) + resolveCompletionsPath();
	}
}