package com.coremcp.services.impl;

import de.hybris.platform.util.Config;

/**
 * Google Gemini via the OpenAI-compatible endpoint at
 * https://generativelanguage.googleapis.com/v1beta/openai/.
 *
 * Secret (env):     GEMINI_API_KEY  (required) — get from https://aistudio.google.com/app/apikey
 * Hybris properties (local.properties):
 *   coremcp.gemini.model     — chat model (default: gemini-2.5-flash)
 *   coremcp.gemini.baseurl   — override the canonical Google host (default: blank)
 */
public class GeminiLlmProvider extends AbstractOpenAiCompatibleLlmProvider
{
	@Override public String getProviderId()         { return "gemini"; }
	@Override protected String getApiKey()          { return System.getenv("GEMINI_API_KEY"); }
	@Override protected String getDefaultModel()    { return Config.getString("coremcp.gemini.model", "gemini-2.5-flash"); }
	@Override protected String getBaseUrl()         { return Config.getString("coremcp.gemini.baseurl", ""); }
	@Override protected String getCompletionsPath() { return "/v1beta/openai/chat/completions"; }
	@Override protected String getDefaultApiUrl()   { return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"; }
}
