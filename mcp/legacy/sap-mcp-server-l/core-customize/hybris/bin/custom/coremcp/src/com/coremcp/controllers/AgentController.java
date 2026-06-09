package com.coremcp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.AgentService;
import com.coremcp.services.LlmClient;
import com.coremcp.services.McpCartSessionService;

import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the AI shopping agent.
 * Accepts chat messages and returns AI-powered responses using the configured
 * LLM provider with tool calling.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class AgentController
{
	private static final Logger LOG = LoggerFactory.getLogger(AgentController.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource(name = "agentService")
	private AgentService agentService;

	@Resource(name = "mcpCartSessionService")
	private McpCartSessionService mcpCartSessionService;

	@Resource(name = "llmClient")
	private LlmClient llmClient;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/agent/capabilities", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String handleCapabilities() throws Exception
	{
		return objectMapper.writeValueAsString(Map.of("vision", llmClient.supportsVision()));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/agent/chat/stream", method = RequestMethod.POST)
	@SuppressWarnings("unchecked")
	public void handleChatStream(@RequestBody final String body, final HttpServletResponse response)
	{
		// Feature flag — when off, return plain JSON in the same shape as /agent/chat so the
		// frontend's transparent fallback path picks it up without special-casing.
		final boolean streamingEnabled = Config.getBoolean("coremcp.agent.streaming.enabled", true);

		try
		{
			final Map<String, Object> request = objectMapper.readValue(body, Map.class);
			final List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");

			if (messages == null || messages.isEmpty())
			{
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.setContentType("application/json");
				response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", "messages array is required")));
				return;
			}

			mcpCartSessionService.loadCartOrCurrent((String) request.get("cartCode"));

			if (!streamingEnabled)
			{
				final Map<String, Object> result = agentService.chat(messages);
				attachCartCode(result);
				response.setContentType("application/json");
				response.getWriter().write(objectMapper.writeValueAsString(result));
				return;
			}

			response.setContentType("text/event-stream;charset=UTF-8");
			response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
			response.setHeader("X-Accel-Buffering", "no"); // disable proxy buffering when applicable
			response.flushBuffer();

			final PrintWriter writer = response.getWriter();
			final Map<String, Object> result = agentService.chatStream(
				messages,
				chunk -> writeSseEvent(writer, "text", chunk),
				toolName -> {
					try
					{
						writeSseEvent(writer, "tool",
							objectMapper.writeValueAsString(Map.of("name", toolName)));
					}
					catch (final Exception ignored) { }
				});
			attachCartCode(result);
			writeSseEvent(writer, "done", objectMapper.writeValueAsString(result));
			writer.flush();
		}
		catch (final Exception e)
		{
			LOG.error("Agent chat stream error", e);
			try
			{
				if (!response.isCommitted())
				{
					response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					response.setContentType("application/json");
					response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", e.getMessage())));
				}
				else
				{
					// Already streaming — send a final error event so the client can react.
					writeSseEvent(response.getWriter(), "error",
						objectMapper.writeValueAsString(Map.of("error", String.valueOf(e.getMessage()))));
				}
			}
			catch (final IOException ignored)
			{
			}
		}
	}

	private void attachCartCode(final Map<String, Object> result)
	{
		final String sessionCartCode = mcpCartSessionService.getSessionCartCode();
		LOG.info("[perf] attachCartCode sessionCartCode={}", sessionCartCode);
		if (sessionCartCode != null)
		{
			result.put("cartCode", sessionCartCode);
		}
	}

	private void writeSseEvent(final PrintWriter writer, final String event, final String data)
	{
		// Multi-line data needs each line prefixed with `data:`. We send all chunks on a single
		// data: line by escaping newlines — recipient unescapes if needed. For text deltas we
		// just JSON-encode the payload to dodge the line-break issue entirely.
		final String payload = "text".equals(event)
			? safeJson(data)
			: data; // already JSON for done/error events
		writer.write("event: " + event + "\n");
		writer.write("data: " + payload + "\n\n");
		writer.flush();
	}

	private String safeJson(final String text)
	{
		try
		{
			return objectMapper.writeValueAsString(text);
		}
		catch (final Exception e)
		{
			return "\"\"";
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/agent/chat", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	@SuppressWarnings("unchecked")
	public String handleChat(@RequestBody final String body, final HttpServletResponse response)
	{
		try
		{
			final Map<String, Object> request = objectMapper.readValue(body, Map.class);
			final List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");

			if (messages == null || messages.isEmpty())
			{
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return objectMapper.writeValueAsString(Map.of("error", "messages array is required"));
			}

			// Prefer the explicit cartCode from the UI; fall back to "current" so multi-turn
			// tool calls within a conversation operate on the same cart.
			mcpCartSessionService.loadCartOrCurrent((String) request.get("cartCode"));

			final Map<String, Object> result = agentService.chat(messages);

			// Return the session cart code so the UI stays in sync.
			final String sessionCartCode = mcpCartSessionService.getSessionCartCode();
			if (sessionCartCode != null)
			{
				result.put("cartCode", sessionCartCode);
			}

			return objectMapper.writeValueAsString(result);
		}
		catch (final Exception e)
		{
			LOG.error("Agent chat error", e);
			try
			{
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return objectMapper.writeValueAsString(Map.of("error", e.getMessage()));
			}
			catch (final Exception jsonError)
			{
				return "{\"error\":\"Internal server error\"}";
			}
		}
	}
}
