package com.coremcp.services.impl;

/**
 * Direct OpenAI provider.
 */
public class OpenAiLlmProvider extends AbstractOpenAiCompatibleLlmProvider
{
	private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";

	@Override
	public String getProviderId()
	{
		return "openai";
	}

	@Override
	protected String getApiKeyProperty()
	{
		return "coremcp.openai.apikey";
	}

	@Override
	protected String getApiKeyEnvVar()
	{
		return "OPENAI_API_KEY";
	}

	@Override
	protected String getDefaultModelProperty()
	{
		return "coremcp.openai.model";
	}

	@Override
	protected String getDefaultModel()
	{
		return "gpt-4o";
	}

	@Override
	protected String getBaseUrlProperty()
	{
		return "coremcp.openai.baseurl";
	}

	@Override
	protected String getCompletionsPath()
	{
		return "/v1/chat/completions";
	}

	@Override
	protected String getDefaultApiUrl()
	{
		return DEFAULT_API_URL;
	}
}
