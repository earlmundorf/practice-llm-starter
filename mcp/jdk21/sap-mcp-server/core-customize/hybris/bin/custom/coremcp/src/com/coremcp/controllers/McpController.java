package com.coremcp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.dto.JsonRpcError;
import com.coremcp.dto.JsonRpcRequest;
import com.coremcp.dto.JsonRpcResponse;
import com.coremcp.dto.McpSession;
import com.coremcp.services.McpDispatcherService;
import com.coremcp.services.McpSessionService;

import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;
import de.hybris.platform.order.CartService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MCP (Model Context Protocol) JSON-RPC 2.0 endpoint.
 * Single endpoint supporting POST (requests), DELETE (session termination).
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class McpController
{
	private static final Logger LOG = LoggerFactory.getLogger(McpController.class);
	private static final String SESSION_HEADER = "MCP-Session-Id";
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource(name = "mcpDispatcherService")
	private McpDispatcherService mcpDispatcherService;

	@Resource(name = "mcpSessionService")
	private McpSessionService mcpSessionService;

	@Resource(name = "cartLoaderStrategy")
	private CartLoaderStrategy cartLoaderStrategy;

	@Resource(name = "cartService")
	private CartService cartService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/mcp", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public String handlePost(
		@RequestBody final String body,
		@RequestHeader(value = SESSION_HEADER, required = false) final String sessionId,
		final HttpServletResponse response)
	{
		try
		{
			final JsonRpcRequest request = objectMapper.readValue(body, JsonRpcRequest.class);

			// Validate JSON-RPC version
			if (!"2.0".equals(request.getJsonrpc()))
			{
				return writeResponse(JsonRpcResponse.error(request.getId(),
					JsonRpcError.INVALID_REQUEST, "jsonrpc must be \"2.0\""), response);
			}

			// Handle initialize (no session required)
			if ("initialize".equals(request.getMethod()))
			{
				final McpDispatcherService.InitializeResult result = mcpDispatcherService.handleInitialize(request);
				response.setHeader(SESSION_HEADER, result.getSessionId());
				return writeResponse(result.getResponse(), response);
			}

			// All other methods require a valid session
			final McpSession session = mcpSessionService.getSession(sessionId);
			if (session == null)
			{
				return writeResponse(JsonRpcResponse.error(request.getId(),
					JsonRpcError.INVALID_REQUEST, "Invalid or expired MCP-Session-Id"), response);
			}

			// Load the session's cart if we have one — don't fall back to "current" which picks up stale carts
			final String sessionCartCode = session.getCartCode();
			if (sessionCartCode != null)
			{
				try
				{
					cartLoaderStrategy.loadCart(sessionCartCode);
				}
				catch (final Exception e)
				{
					LOG.debug("Could not load cart {}, will create fresh: {}", sessionCartCode, e.getMessage());
					session.setCartCode(null);
				}
			}

			final JsonRpcResponse jsonRpcResponse = mcpDispatcherService.dispatch(request, session);

			// Save cart code back to session for next request (or clear it if cart was removed after order)
			try
			{
				if (cartService.hasSessionCart())
				{
					session.setCartCode(cartService.getSessionCart().getCode());
				}
				else
				{
					session.setCartCode(null);
				}
			}
			catch (final Exception e)
			{
				LOG.debug("Could not save cart code to session: {}", e.getMessage());
			}

			// Notifications return null — respond with 202 Accepted
			if (jsonRpcResponse == null)
			{
				response.setStatus(HttpServletResponse.SC_ACCEPTED);
				return "";
			}

			return writeResponse(jsonRpcResponse, response);
		}
		catch (final Exception e)
		{
			LOG.error("MCP POST error", e);
			try
			{
				return writeResponse(JsonRpcResponse.error(null,
					JsonRpcError.PARSE_ERROR, "Parse error: " + e.getMessage()), response);
			}
			catch (final Exception jsonError)
			{
				return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
			}
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/mcp", method = RequestMethod.DELETE)
	@ResponseBody
	public String handleDelete(
		@RequestHeader(value = SESSION_HEADER, required = false) final String sessionId,
		final HttpServletResponse response)
	{
		if (sessionId != null)
		{
			mcpSessionService.removeSession(sessionId);
		}
		response.setStatus(HttpServletResponse.SC_OK);
		return "";
	}

	private String writeResponse(final JsonRpcResponse jsonRpcResponse, final HttpServletResponse response)
		throws Exception
	{
		response.setContentType("application/json");
		return objectMapper.writeValueAsString(jsonRpcResponse);
	}
}
