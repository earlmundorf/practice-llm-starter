package com.coremcp.services.impl;

import de.hybris.platform.util.Config;

/**
 * Direct OpenAI provider.
 *
 * Secret (env):     OPENAI_API_KEY  (required)
 * Hybris properties (local.properties):
 *   coremcp.openai.model     — main chat model (default: gpt-4o)
 *   coremcp.openai.baseurl   — override the canonical OpenAI host (default: blank)
 */
public class OpenAiLlmProvider extends AbstractOpenAiCompatibleLlmProvider
{
	@Override public String getProviderId()         { return "openai"; }
	@Override protected String getApiKey()          { return System.getenv("OPENAI_API_KEY"); }
	@Override protected String getDefaultModel()    { return Config.getString("coremcp.openai.model", "gpt-4o"); }
	@Override protected String getBaseUrl()         { return Config.getString("coremcp.openai.baseurl", ""); }
	@Override protected String getCompletionsPath() { return "/v1/chat/completions"; }
	@Override protected String getDefaultApiUrl()   { return "https://api.openai.com/v1/chat/completions"; }
}
