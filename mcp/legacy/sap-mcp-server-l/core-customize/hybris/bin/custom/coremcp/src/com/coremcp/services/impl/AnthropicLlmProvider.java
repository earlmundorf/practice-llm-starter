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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct Anthropic provider. Adapts Anthropic's content-block request/response format
 * to the OpenAI-style {@code choices[].message} shape that the rest of the agent expects.
 *
 * Secret (env):     ANTHROPIC_API_KEY  (required)
 * Hybris properties (local.properties):
 *   coremcp.anthropic.model    — main chat model (default: claude-3-5-sonnet-latest)
 *   coremcp.anthropic.version  — Anthropic API version header (default: 2023-06-01)
 *   coremcp.anthropic.baseurl  — override the canonical Anthropic host (default: blank)
 */
public class AnthropicLlmProvider implements LlmProvider
{
	private static final Logger LOG = LoggerFactory.getLogger(AnthropicLlmProvider.class);
	private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
	private static final int DEFAULT_MAX_TOKENS = 1024;

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getProviderId()
	{
		return "anthropic";
	}

	@Override
	public Map<String, Object> chatCompletion(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride)
	{
		try
		{
			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(resolveApiUrl()))
				.header("Content-Type", "application/json")
				.header("x-api-key", requireApiKey())
				.header("anthropic-version", Config.getString("coremcp.anthropic.version", "2023-06-01"))
				.timeout(Duration.ofSeconds(Config.getInt("coremcp.llm.timeout.seconds", 60)))
				.POST(HttpRequest.BodyPublishers.ofString(
					objectMapper.writeValueAsString(buildRequestBody(messages, tools, modelOverride))))
				.build();

			final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				LOG.error("Anthropic API error ({}): {}", response.statusCode(), response.body());
				throw new RuntimeException("Anthropic API returned status " + response.statusCode() + ": "
					+ response.body());
			}

			final Map<String, Object> raw = objectMapper.readValue(response.body(),
				new TypeReference<Map<String, Object>>() {});
			return normalizeResponse(raw);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException("Anthropic request interrupted", e);
		}
		catch (final RuntimeException e)
		{
			throw e;
		}
		catch (final Exception e)
		{
			throw new RuntimeException("Anthropic request failed: " + e.getMessage(), e);
		}
	}

	private Map<String, Object> buildRequestBody(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride)
	{
		final Map<String, Object> requestBody = new LinkedHashMap<>();
		requestBody.put("model", resolveModel(modelOverride));
		requestBody.put("max_tokens", DEFAULT_MAX_TOKENS);

		String system = null;
		final List<Map<String, Object>> anthropicMessages = new ArrayList<>();
		for (final Map<String, Object> message : messages)
		{
			final String role = asString(message.get("role"));
			if ("system".equals(role))
			{
				system = asString(message.get("content"));
				continue;
			}
			if ("tool".equals(role))
			{
				anthropicMessages.add(Map.of(
					"role", "user",
					"content", List.of(Map.of(
						"type", "tool_result",
						"tool_use_id", asString(message.get("tool_call_id")),
						"content", asString(message.get("content"))
					))
				));
				continue;
			}
			anthropicMessages.add(Map.of(
				"role", normalizeRole(role),
				"content", normalizeContent(message.get("content"))
			));
		}

		if (system != null && !system.isBlank())
		{
			requestBody.put("system", system);
		}
		requestBody.put("messages", anthropicMessages);

		if (tools != null && !tools.isEmpty())
		{
			requestBody.put("tools", normalizeTools(tools));
		}

		return requestBody;
	}

	// Package-private — exercised directly by AnthropicLlmProviderTest.
	Map<String, Object> normalizeResponse(final Map<String, Object> raw)
	{
		final List<Map<String, Object>> content = castList(raw.get("content"));
		final StringBuilder text = new StringBuilder();
		final List<Map<String, Object>> toolCalls = new ArrayList<>();
		if (content != null)
		{
			for (final Map<String, Object> item : content)
			{
				final String type = asString(item.get("type"));
				if ("text".equals(type))
				{
					text.append(asString(item.get("text")));
				}
				else if ("tool_use".equals(type))
				{
					final Map<String, Object> function = new LinkedHashMap<>();
					function.put("name", asString(item.get("name")));
					function.put("arguments", toJson(item.get("input")));

					final Map<String, Object> toolCall = new LinkedHashMap<>();
					toolCall.put("id", asString(item.get("id")));
					toolCall.put("type", "function");
					toolCall.put("function", function);
					toolCalls.add(toolCall);
				}
			}
		}

		final Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "assistant");
		message.put("content", text.toString());
		if (!toolCalls.isEmpty())
		{
			message.put("tool_calls", toolCalls);
		}

		final Map<String, Object> choice = new LinkedHashMap<>();
		choice.put("index", 0);
		choice.put("message", message);
		choice.put("finish_reason", toolCalls.isEmpty() ? "stop" : "tool_calls");

		return Map.of("choices", List.of(choice));
	}

	// Package-private — exercised directly by AnthropicLlmProviderTest.
	Object normalizeContent(final Object content)
	{
		if (content instanceof String)
		{
			return List.of(Map.of("type", "text", "text", content));
		}
		if (content instanceof List)
		{
			final List<?> items = (List<?>) content;
			final List<Map<String, Object>> normalized = new ArrayList<>();
			for (final Object item : items)
			{
				if (!(item instanceof Map))
				{
					continue;
				}
				@SuppressWarnings("unchecked")
				final Map<String, Object> map = (Map<String, Object>) item;
				final String type = asString(map.get("type"));
				if ("text".equals(type))
				{
					normalized.add(Map.of("type", "text", "text", asString(map.get("text"))));
				}
				else if ("image_url".equals(type))
				{
					@SuppressWarnings("unchecked")
					final Map<String, Object> imageUrl = (Map<String, Object>) map.get("image_url");
					if (imageUrl != null)
					{
						final Map<String, Object> imageBlock = new LinkedHashMap<>();
						imageBlock.put("type", "image");
						imageBlock.put("source", buildImageSource(asString(imageUrl.get("url"))));
						normalized.add(imageBlock);
					}
				}
			}
			return normalized;
		}
		return List.of(Map.of("type", "text", "text", asString(content)));
	}

	private List<Map<String, Object>> normalizeTools(final List<Map<String, Object>> tools)
	{
		final List<Map<String, Object>> normalized = new ArrayList<>();
		for (final Map<String, Object> tool : tools)
		{
			@SuppressWarnings("unchecked")
			final Map<String, Object> function = (Map<String, Object>) tool.get("function");
			final Map<String, Object> normalizedTool = new LinkedHashMap<>();
			normalizedTool.put("name", asString(function.get("name")));
			normalizedTool.put("description", asString(function.get("description")));
			normalizedTool.put("input_schema", function.get("parameters"));
			normalized.add(normalizedTool);
		}
		return normalized;
	}

	private Map<String, Object> buildImageSource(final String dataUrl)
	{
		final int comma = dataUrl.indexOf(',');
		final String metadata = comma >= 0 ? dataUrl.substring(0, comma) : "data:image/jpeg;base64";
		final String data = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
		final String mediaType = metadata.replace("data:", "").replace(";base64", "");
		final Map<String, Object> source = new LinkedHashMap<>();
		source.put("type", "base64");
		source.put("media_type", mediaType);
		source.put("data", data);
		return source;
	}

	private String resolveApiUrl()
	{
		final String baseUrl = Config.getString("coremcp.anthropic.baseurl", "");
		return baseUrl.isBlank() ? DEFAULT_API_URL : baseUrl;
	}

	private String resolveModel(final String modelOverride)
	{
		return (modelOverride != null && !modelOverride.isBlank())
			? modelOverride
			: Config.getString("coremcp.anthropic.model", "claude-3-5-sonnet-latest");
	}

	private String requireApiKey()
	{
		final String apiKey = System.getenv("ANTHROPIC_API_KEY");
		if (apiKey == null || apiKey.isBlank())
		{
			throw new IllegalStateException("Anthropic API key missing. Set ANTHROPIC_API_KEY env var.");
		}
		return apiKey;
	}

	private String normalizeRole(final String role)
	{
		return "assistant".equals(role) ? "assistant" : "user";
	}

	private String toJson(final Object value)
	{
		try
		{
			return objectMapper.writeValueAsString(value != null ? value : Map.of());
		}
		catch (final Exception e)
		{
			throw new RuntimeException("Failed to serialize Anthropic tool input", e);
		}
	}

	private String asString(final Object value)
	{
		return value == null ? "" : String.valueOf(value);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> castList(final Object value)
	{
		return value instanceof List ? (List<Map<String, Object>>) value : null;
	}
}
