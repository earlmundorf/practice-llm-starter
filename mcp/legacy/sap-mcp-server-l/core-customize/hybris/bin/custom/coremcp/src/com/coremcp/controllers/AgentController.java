package com.coremcp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.AgentService;

import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;
import de.hybris.platform.order.CartService;

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
 * Accepts chat messages and returns AI-powered responses using OpenAI with tool calling.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class AgentController
{
	private static final Logger LOG = LoggerFactory.getLogger(AgentController.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource(name = "agentService")
	private AgentService agentService;

	@Resource(name = "cartLoaderStrategy")
	private CartLoaderStrategy cartLoaderStrategy;

	@Resource(name = "cartService")
	private CartService cartService;

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

			// Load the user's cart into the session. Prefer the explicit cartCode from the UI;
			// fall back to "current" (most recently modified cart) so agent tool calls that
			// modify the cart within a multi-turn conversation work correctly.
			final String cartCode = (String) request.get("cartCode");
			try
			{
				cartLoaderStrategy.loadCart(
					cartCode != null && !cartCode.isEmpty() ? cartCode : "current");
			}
			catch (final Exception e)
			{
				LOG.debug("No existing cart to load: {}", e.getMessage());
			}

			final Map<String, Object> result = agentService.chat(messages);

			// Return the session cart code so the UI stays in sync
			if (cartService.hasSessionCart())
			{
				result.put("cartCode", cartService.getSessionCart().getCode());
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
