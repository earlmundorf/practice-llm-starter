package com.coremcp.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared HTTP/JSON plumbing for providers that speak the OpenAI chat-completions protocol.
 * Subclasses supply config via the abstract getters below — typically by reading hybris
 * properties via {@link de.hybris.platform.util.Config} (plain text values) and the API
 * key via {@link System#getenv}. Timeout and retry behavior comes from
 * {@link AbstractHttpLlmProvider}.
 */
public abstract class AbstractOpenAiCompatibleLlmProvider extends AbstractHttpLlmProvider
{
	@Override
	public Map<String, Object> chatCompletion(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride)
	{
		try
		{
			final Map<String, Object> requestBody = new LinkedHashMap<>();
			requestBody.put("model", resolveModel(modelOverride));
			requestBody.put("messages", messages);
			if (tools != null && !tools.isEmpty())
			{
				requestBody.put("tools", tools);
			}

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(resolveApiUrl()))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + requireApiKey())
				.timeout(requestTimeout())
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
				.build();

			final HttpResponse<String> response = sendWithRetry(request);
			requireOk(response);

			return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException(getProviderId() + " request interrupted", e);
		}
		catch (final RuntimeException e)
		{
			throw e;
		}
		catch (final Exception e)
		{
			throw new RuntimeException(getProviderId() + " request failed: " + e.getMessage(), e);
		}
	}

	protected String resolveModel(final String modelOverride)
	{
		return (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : getDefaultModel();
	}

	protected String requireApiKey()
	{
		final String apiKey = getApiKey();
		if (apiKey == null || apiKey.isBlank())
		{
			throw new IllegalStateException(getProviderId() + " API key missing. Set the relevant env var.");
		}
		return apiKey;
	}

	protected String resolveApiUrl()
	{
		final String baseUrl = getBaseUrl();
		if (baseUrl != null && !baseUrl.isBlank())
		{
			return trimTrailingSlash(baseUrl) + resolveCompletionsPath();
		}
		return getDefaultApiUrl();
	}

	protected String resolveCompletionsPath()
	{
		final String configured = getConfiguredCompletionsPath();
		return (configured != null && !configured.isBlank()) ? configured : getCompletionsPath();
	}

	/** Override to supply a per-instance completions path (e.g. for proxies that rewrite the path). */
	protected String getConfiguredCompletionsPath()
	{
		return null;
	}

	protected String trimTrailingSlash(final String url)
	{
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/** Returned from {@link System#getenv(String)} for the provider's secret. May be null. */
	protected abstract String getApiKey();

	protected abstract String getBaseUrl();

	protected abstract String getDefaultModel();

	protected abstract String getCompletionsPath();

	/** Returned only when {@link #getBaseUrl()} is blank — usually the vendor's canonical URL. */
	protected abstract String getDefaultApiUrl();
}
