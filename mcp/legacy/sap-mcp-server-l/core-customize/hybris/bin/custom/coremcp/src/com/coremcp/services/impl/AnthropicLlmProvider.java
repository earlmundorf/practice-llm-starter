package com.coremcp.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.LlmProvider;

import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Direct Anthropic provider. Adapts Anthropic's content-block request/response format
 * to the OpenAI-style {@code choices[].message} shape that the rest of the agent expects.
 *
 * Secrets (env):
 *   ANTHROPIC_API_KEY   (required)
 *   ANTHROPIC_BASE_URL  (optional) — base URL to a gateway, e.g.
 *                        https://anthropic.generative.engine.capgemini.com.
 *                        /v1/messages is appended automatically when missing.
 *                        If unset, falls back to coremcp.anthropic.baseurl, then api.anthropic.com.
 * Hybris properties (local.properties):
 *   coremcp.anthropic.model    — main chat model (default: claude-3-5-sonnet-latest)
 *   coremcp.anthropic.version  — Anthropic API version header (default: 2023-06-01)
 *   coremcp.anthropic.baseurl  — full messages endpoint override (only used if ANTHROPIC_BASE_URL is unset)
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
			logUsage(raw);
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

	@Override
	public Map<String, Object> chatCompletionStream(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride,
		final Consumer<String> textDeltaConsumer)
	{
		try
		{
			final Map<String, Object> body = buildRequestBody(messages, tools, modelOverride);
			body.put("stream", true);
			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(resolveApiUrl()))
				.header("Content-Type", "application/json")
				.header("Accept", "text/event-stream")
				.header("x-api-key", requireApiKey())
				.header("anthropic-version", Config.getString("coremcp.anthropic.version", "2023-06-01"))
				.timeout(Duration.ofSeconds(Config.getInt("coremcp.llm.timeout.seconds", 60)))
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
				.build();

			final HttpResponse<java.io.InputStream> response =
				httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

			final String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase();
			if (response.statusCode() != 200 || !contentType.contains("text/event-stream"))
			{
				// Gateway/proxy didn't honor streaming. Drain whatever we got, log, and fall back
				// to non-streaming so the caller still gets a usable result.
				final String drained = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
				LOG.info("Anthropic streaming unavailable (status={}, contentType={}); falling back to non-streaming",
					response.statusCode(), contentType);
				if (response.statusCode() != 200)
				{
					LOG.warn("Anthropic streaming non-200 body: {}", drained);
				}
				return fallbackToNonStreaming(messages, tools, modelOverride, textDeltaConsumer);
			}

			return consumeStream(response.body(), textDeltaConsumer);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException("Anthropic streaming request interrupted", e);
		}
		catch (final RuntimeException e)
		{
			throw e;
		}
		catch (final Exception e)
		{
			LOG.warn("Anthropic streaming failed ({}); falling back to non-streaming", e.getMessage());
			return fallbackToNonStreaming(messages, tools, modelOverride, textDeltaConsumer);
		}
	}

	private Map<String, Object> fallbackToNonStreaming(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride,
		final Consumer<String> textDeltaConsumer)
	{
		final Map<String, Object> result = chatCompletion(messages, tools, modelOverride);
		try
		{
			@SuppressWarnings("unchecked")
			final List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
			if (choices != null && !choices.isEmpty())
			{
				@SuppressWarnings("unchecked")
				final Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
				final Object content = message == null ? null : message.get("content");
				if (content instanceof String && !((String) content).isEmpty())
				{
					textDeltaConsumer.accept((String) content);
				}
			}
		}
		catch (final Exception ignored)
		{
		}
		return result;
	}

	/**
	 * Consume an Anthropic SSE stream. Emits text deltas through the consumer as they
	 * arrive and accumulates the full response shape (text + tool_use blocks + usage)
	 * so we can return a non-streaming-equivalent result Map at the end.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> consumeStream(final java.io.InputStream body,
		final Consumer<String> textDeltaConsumer) throws Exception
	{
		final List<Map<String, Object>> contentBlocks = new ArrayList<>();
		final Map<Integer, StringBuilder> textBuffers = new LinkedHashMap<>();
		final Map<Integer, StringBuilder> toolInputBuffers = new LinkedHashMap<>();
		Map<String, Object> usage = null;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (!line.startsWith("data:")) continue;
				final String payload = line.substring(5).trim();
				if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
				final Map<String, Object> evt;
				try
				{
					evt = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
				}
				catch (final Exception parseEx)
				{
					LOG.debug("Skipping unparseable SSE event: {}", payload);
					continue;
				}
				final String type = asString(evt.get("type"));
				switch (type)
				{
					case "content_block_start":
					{
						final int idx = ((Number) evt.getOrDefault("index", 0)).intValue();
						final Map<String, Object> block = (Map<String, Object>) evt.get("content_block");
						if (block != null)
						{
							final String blockType = asString(block.get("type"));
							if ("text".equals(blockType))
							{
								textBuffers.put(idx, new StringBuilder());
								while (contentBlocks.size() <= idx) contentBlocks.add(null);
								contentBlocks.set(idx, new LinkedHashMap<>(Map.of("type", "text", "text", "")));
							}
							else if ("tool_use".equals(blockType))
							{
								toolInputBuffers.put(idx, new StringBuilder());
								while (contentBlocks.size() <= idx) contentBlocks.add(null);
								final Map<String, Object> tu = new LinkedHashMap<>();
								tu.put("type", "tool_use");
								tu.put("id", asString(block.get("id")));
								tu.put("name", asString(block.get("name")));
								tu.put("input", Map.of());
								contentBlocks.set(idx, tu);
							}
						}
						break;
					}
					case "content_block_delta":
					{
						final int idx = ((Number) evt.getOrDefault("index", 0)).intValue();
						final Map<String, Object> delta = (Map<String, Object>) evt.get("delta");
						if (delta == null) break;
						final String dtype = asString(delta.get("type"));
						if ("text_delta".equals(dtype))
						{
							final String chunk = asString(delta.get("text"));
							if (!chunk.isEmpty())
							{
								final StringBuilder buf = textBuffers.get(idx);
								if (buf != null) buf.append(chunk);
								textDeltaConsumer.accept(chunk);
							}
						}
						else if ("input_json_delta".equals(dtype))
						{
							final String chunk = asString(delta.get("partial_json"));
							final StringBuilder buf = toolInputBuffers.get(idx);
							if (buf != null) buf.append(chunk);
						}
						break;
					}
					case "content_block_stop":
					{
						final int idx = ((Number) evt.getOrDefault("index", 0)).intValue();
						if (textBuffers.containsKey(idx))
						{
							final Map<String, Object> tb = contentBlocks.get(idx);
							if (tb != null) tb.put("text", textBuffers.get(idx).toString());
						}
						else if (toolInputBuffers.containsKey(idx))
						{
							final Map<String, Object> tu = contentBlocks.get(idx);
							if (tu != null)
							{
								final String json = toolInputBuffers.get(idx).toString();
								Object parsed = Map.of();
								if (!json.isEmpty())
								{
									try
									{
										parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
									}
									catch (final Exception ignored)
									{
										LOG.debug("Failed to parse streamed tool input json: {}", json);
									}
								}
								tu.put("input", parsed);
							}
						}
						break;
					}
					case "message_delta":
					{
						final Map<String, Object> u = (Map<String, Object>) evt.get("usage");
						if (u != null) usage = u;
						break;
					}
					case "message_stop":
					default:
						break;
				}
			}
		}

		final Map<String, Object> raw = new LinkedHashMap<>();
		raw.put("content", contentBlocks);
		if (usage != null) raw.put("usage", usage);
		logUsage(raw);
		return normalizeResponse(raw);
	}

	private Map<String, Object> buildRequestBody(final List<Map<String, Object>> messages,
		final List<Map<String, Object>> tools,
		final String modelOverride)
	{
		final Map<String, Object> requestBody = new LinkedHashMap<>();
		requestBody.put("model", resolveModel(modelOverride));
		requestBody.put("max_tokens", DEFAULT_MAX_TOKENS);

		final List<String> systemTexts = new ArrayList<>();
		final List<Map<String, Object>> anthropicMessages = new ArrayList<>();
		for (final Map<String, Object> message : messages)
		{
			final String role = asString(message.get("role"));
			if ("system".equals(role))
			{
				final String text = asString(message.get("content"));
				if (!text.isBlank()) systemTexts.add(text);
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
			if ("assistant".equals(role))
			{
				anthropicMessages.add(Map.of(
					"role", "assistant",
					"content", buildAssistantContent(message)
				));
				continue;
			}
			anthropicMessages.add(Map.of(
				"role", normalizeRole(role),
				"content", normalizeContent(message.get("content"))
			));
		}

		// Send system as an array of content blocks. The FIRST block (caller convention:
		// the stable persona prompt) gets a cache_control breakpoint so Anthropic's
		// ephemeral prompt cache can reuse it across turns within ~5 minutes. Subsequent
		// system blocks (per-turn state snapshot) stay uncached.
		if (!systemTexts.isEmpty())
		{
			final List<Map<String, Object>> systemBlocks = new ArrayList<>();
			for (int i = 0; i < systemTexts.size(); i++)
			{
				final Map<String, Object> block = new LinkedHashMap<>();
				block.put("type", "text");
				block.put("text", systemTexts.get(i));
				if (i == 0)
				{
					block.put("cache_control", Map.of("type", "ephemeral"));
				}
				systemBlocks.add(block);
			}
			requestBody.put("system", systemBlocks);
		}
		requestBody.put("messages", anthropicMessages);

		if (tools != null && !tools.isEmpty())
		{
			// Tag the last tool with a cache breakpoint so the entire tools section is cacheable.
			final List<Map<String, Object>> normalizedTools = normalizeTools(tools);
			if (!normalizedTools.isEmpty())
			{
				final Map<String, Object> last = new LinkedHashMap<>(normalizedTools.get(normalizedTools.size() - 1));
				last.put("cache_control", Map.of("type", "ephemeral"));
				normalizedTools.set(normalizedTools.size() - 1, last);
			}
			requestBody.put("tools", normalizedTools);
		}

		return requestBody;
	}

	@SuppressWarnings("unchecked")
	private void logUsage(final Map<String, Object> raw)
	{
		final Object usageObj = raw == null ? null : raw.get("usage");
		if (!(usageObj instanceof Map)) return;
		final Map<String, Object> usage = (Map<String, Object>) usageObj;
		LOG.info("[perf] anthropic.usage input={} output={} cacheCreate={} cacheRead={}",
			usage.get("input_tokens"),
			usage.get("output_tokens"),
			usage.getOrDefault("cache_creation_input_tokens", 0),
			usage.getOrDefault("cache_read_input_tokens", 0));
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
	@SuppressWarnings("unchecked")
	List<Map<String, Object>> buildAssistantContent(final Map<String, Object> message)
	{
		final List<Map<String, Object>> blocks = new ArrayList<>();
		final String text = asString(message.get("content"));
		if (!text.isBlank())
		{
			blocks.add(Map.of("type", "text", "text", text));
		}
		final List<Map<String, Object>> toolCalls = castList(message.get("tool_calls"));
		if (toolCalls != null)
		{
			for (final Map<String, Object> toolCall : toolCalls)
			{
				final Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
				if (function == null)
				{
					continue;
				}
				final Map<String, Object> block = new LinkedHashMap<>();
				block.put("type", "tool_use");
				block.put("id", asString(toolCall.get("id")));
				block.put("name", asString(function.get("name")));
				block.put("input", parseToolInput(function.get("arguments")));
				blocks.add(block);
			}
		}
		// Anthropic rejects assistant messages with empty content arrays.
		if (blocks.isEmpty())
		{
			blocks.add(Map.of("type", "text", "text", ""));
		}
		return blocks;
	}

	private Object parseToolInput(final Object arguments)
	{
		if (arguments instanceof Map)
		{
			return arguments;
		}
		final String json = asString(arguments);
		if (json.isBlank())
		{
			return Map.of();
		}
		try
		{
			return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		}
		catch (final Exception e)
		{
			LOG.warn("Failed to parse tool_call.arguments as JSON, sending empty input: {}", json);
			return Map.of();
		}
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
		final String envBase = System.getenv("ANTHROPIC_BASE_URL");
		if (envBase != null && !envBase.isBlank())
		{
			final String trimmed = envBase.replaceAll("/+$", "");
			return trimmed.contains("/v1/messages") ? trimmed : trimmed + "/v1/messages";
		}
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
