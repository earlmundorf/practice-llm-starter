package com.coremcp.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.LlmProvider;

import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared base class for providers that speak an OpenAI-compatible chat completions API.
 */
public abstract class AbstractOpenAiCompatibleLlmProvider implements LlmProvider
{
	private static final Logger LOG = LoggerFactory.getLogger(AbstractOpenAiCompatibleLlmProvider.class);

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	protected AbstractOpenAiCompatibleLlmProvider()
	{
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public Map<String, Object> chatCompletion(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride)
	{
		try
		{
			final String apiKey = requireApiKey();
			final String model = resolveModel(modelOverride);
			final Map<String, Object> requestBody = new LinkedHashMap<>();
			requestBody.put("model", model);
			requestBody.put("messages", messages);
			if (tools != null && !tools.isEmpty())
			{
				requestBody.put("tools", tools);
			}

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(resolveApiUrl()))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + apiKey)
				.timeout(Duration.ofSeconds(resolveTimeoutSeconds()))
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
				.build();

			final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				LOG.error("{} API error ({}): {}", getProviderId(), response.statusCode(), response.body());
				throw new RuntimeException(getProviderId() + " API returned status " + response.statusCode() + ": "
					+ response.body());
			}

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

	protected int resolveTimeoutSeconds()
	{
		final String configured = resolveConfigOrEnvValue(Config.getParameter("coremcp.llm.timeout.seconds"), null);
		return configured != null && !configured.isBlank() ? Integer.parseInt(configured) : 60;
	}

	protected String resolveModel(final String modelOverride)
	{
		if (modelOverride != null && !modelOverride.isBlank())
		{
			return modelOverride;
		}

		final String configModel = resolveConfigOrEnvValue(Config.getParameter(getDefaultModelProperty()), null);
		if (configModel != null && !configModel.isBlank())
		{
			return configModel;
		}

		return getDefaultModel();
	}

	protected String requireApiKey()
	{
		final String configuredApiKey = Config.getParameter(getApiKeyProperty());
		String apiKey = resolveConfigOrEnvValue(configuredApiKey, getApiKeyEnvVar());
		if (apiKey == null || apiKey.isBlank())
		{
			throw new IllegalStateException(getProviderId() + " API key not found. Set " + getApiKeyEnvVar()
				+ " env var or " + getApiKeyProperty() + " in local.properties");
		}
		return apiKey;
	}

	protected String resolveApiUrl()
	{
		final String configured = resolveConfigOrEnvValue(Config.getParameter(getBaseUrlProperty()), null);
		if (configured != null && !configured.isBlank())
		{
			return trimTrailingSlash(configured) + resolveCompletionsPath();
		}
		return getDefaultApiUrl();
	}

	protected String resolveCompletionsPath()
	{
		final String configured = resolveConfigOrEnvValue(getConfiguredCompletionsPath(), null);
		return configured != null && !configured.isBlank() ? configured : getCompletionsPath();
	}

	protected String getConfiguredCompletionsPath()
	{
		return null;
	}

	protected static String resolveConfigOrEnvValue(final String configuredValue, final String fallbackEnvVar)
	{
		if (configuredValue == null || configuredValue.isBlank())
		{
			return fallbackEnvVar != null ? System.getenv(fallbackEnvVar) : configuredValue;
		}

		final String trimmedValue = configuredValue.trim();
		if (trimmedValue.startsWith("${") && trimmedValue.endsWith("}"))
		{
			final String envVarName = trimmedValue.substring(2, trimmedValue.length() - 1).trim();
			if (!envVarName.isEmpty())
			{
				final String envValue = System.getenv(envVarName);
				if (envValue != null && !envValue.isBlank())
				{
					return envValue;
				}
			}
		}

		return trimmedValue;
	}

	protected String trimTrailingSlash(final String url)
	{
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	protected abstract String getApiKeyProperty();

	protected abstract String getApiKeyEnvVar();

	protected abstract String getDefaultModelProperty();

	protected abstract String getDefaultModel();

	protected abstract String getBaseUrlProperty();

	protected abstract String getCompletionsPath();

	protected abstract String getDefaultApiUrl();
}
