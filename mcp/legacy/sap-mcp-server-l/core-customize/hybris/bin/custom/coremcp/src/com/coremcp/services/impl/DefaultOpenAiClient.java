package com.coremcp.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.OpenAiClient;
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
 * Default implementation of {@link OpenAiClient}.
 * Uses java.net.http.HttpClient (Java 17 built-in).
 */
public class DefaultOpenAiClient implements OpenAiClient
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultOpenAiClient.class);
	private static final String API_URL = "https://api.openai.com/v1/chat/completions";

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public DefaultOpenAiClient()
	{
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		this.objectMapper = new ObjectMapper();
	}

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
		try
		{
			String apiKey = Config.getParameter("coremcp.openai.apikey");
			if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${"))
			{
				apiKey = System.getenv("OPENAI_API_KEY");
			}
			if (apiKey == null || apiKey.isBlank())
			{
				throw new IllegalStateException(
					"OpenAI API key not found. Set OPENAI_API_KEY env var or coremcp.openai.apikey in local.properties");
			}

			final String model;
			if (modelOverride != null && !modelOverride.isBlank())
			{
				model = modelOverride;
			}
			else
			{
				final String configModel = Config.getParameter("coremcp.openai.model");
				model = configModel != null ? configModel : "gpt-4o";
			}

			final Map<String, Object> requestBody = new LinkedHashMap<>();
			requestBody.put("model", model);
			requestBody.put("messages", messages);
			if (tools != null && !tools.isEmpty())
			{
				requestBody.put("tools", tools);
			}

			final String jsonBody = objectMapper.writeValueAsString(requestBody);

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(API_URL))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + apiKey)
				.timeout(Duration.ofSeconds(60))
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.build();

			final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200)
			{
				LOG.error("OpenAI API error ({}): {}", response.statusCode(), response.body());
				throw new RuntimeException("OpenAI API returned status " + response.statusCode() + ": " + response.body());
			}

			return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException("OpenAI request interrupted", e);
		}
		catch (final RuntimeException e)
		{
			throw e;
		}
		catch (final Exception e)
		{
			throw new RuntimeException("OpenAI request failed: " + e.getMessage(), e);
		}
	}
}
