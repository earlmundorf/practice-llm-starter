package com.coremcp.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;

import java.util.List;
import java.util.Map;

import org.junit.Test;

@UnitTest
public class AnthropicLlmProviderTest
{
	private final AnthropicLlmProvider provider = new AnthropicLlmProvider();

	@Test
	public void testNormalizeResponseMapsTextAndToolUseToOpenAiShape()
	{
		final Map<String, Object> raw = Map.of(
			"content", List.of(
				Map.of("type", "text", "text", "Need to check cart. "),
				Map.of("type", "tool_use", "id", "toolu_1", "name", "cart_get", "input", Map.of("cartCode", "123")),
				Map.of("type", "text", "text", "Done")
			)
		);

		final Map<String, Object> normalized = provider.normalizeResponse(raw);
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> choices = (List<Map<String, Object>>) normalized.get("choices");
		@SuppressWarnings("unchecked")
		final Map<String, Object> choice = choices.get(0);
		@SuppressWarnings("unchecked")
		final Map<String, Object> message = (Map<String, Object>) choice.get("message");
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
		@SuppressWarnings("unchecked")
		final Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");

		assertEquals("tool_calls", choice.get("finish_reason"));
		assertEquals("Need to check cart. Done", message.get("content"));
		assertEquals("cart_get", function.get("name"));
		assertTrue(String.valueOf(function.get("arguments")).contains("cartCode"));
	}

	@Test
	public void testNormalizeContentMapsTextAndImageUrlBlocks()
	{
		final Object normalized = provider.normalizeContent(List.of(
			Map.of("type", "text", "text", "Describe this"),
			Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,abc123"))
		));

		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> content = (List<Map<String, Object>>) normalized;
		assertEquals(2, content.size());
		assertEquals("text", content.get(0).get("type"));
		assertEquals("image", content.get(1).get("type"));
		@SuppressWarnings("unchecked")
		final Map<String, Object> source = (Map<String, Object>) content.get(1).get("source");
		assertEquals("image/png", source.get("media_type"));
		assertEquals("abc123", source.get("data"));
	}
}
