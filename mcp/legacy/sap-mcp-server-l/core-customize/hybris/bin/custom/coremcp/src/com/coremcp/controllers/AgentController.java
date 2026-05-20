package com.coremcp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.AgentService;
import com.coremcp.services.LlmClient;
import com.coremcp.services.McpCartSessionService;

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
