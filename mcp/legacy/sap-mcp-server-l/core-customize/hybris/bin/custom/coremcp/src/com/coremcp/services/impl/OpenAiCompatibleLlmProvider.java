package com.coremcp.services.impl;

import de.hybris.platform.util.Config;

/**
 * Generic provider for any OpenAI-compatible chat completions endpoint.
 *
 * Use this for Azure OpenAI, OpenRouter, Kong-routed OpenAI, LocalAI, vLLM,
 * llama.cpp server, Together AI, Anyscale — anything that speaks the OpenAI
 * /v1/chat/completions protocol on a non-OpenAI host.
 *
 * Secret (env):     OPENAI_COMPATIBLE_API_KEY  (required by most hosts)
 * Hybris properties (local.properties):
 *   coremcp.openai-compatible.baseurl           — REQUIRED (no default; provider is unusable without it)
 *   coremcp.openai-compatible.model             — main chat model (default: gpt-4o)
 *   coremcp.openai-compatible.completions.path  — override for non-standard paths (default: /v1/chat/completions)
 */
public class OpenAiCompatibleLlmProvider extends AbstractOpenAiCompatibleLlmProvider
{
	@Override public String getProviderId()                   { return "openai-compatible"; }
	@Override protected String getApiKey()                    { return System.getenv("OPENAI_COMPATIBLE_API_KEY"); }
	@Override protected String getDefaultModel()              { return Config.getString("coremcp.openai-compatible.model", "gpt-4o"); }
	@Override protected String getBaseUrl()                   { return Config.getString("coremcp.openai-compatible.baseurl", ""); }
	@Override protected String getCompletionsPath()           { return "/v1/chat/completions"; }
	@Override protected String getConfiguredCompletionsPath() { return Config.getString("coremcp.openai-compatible.completions.path", ""); }

	@Override
	protected String getDefaultApiUrl()
	{
		throw new IllegalStateException("coremcp.openai-compatible.baseurl is required — set it in local.properties.");
	}
}
