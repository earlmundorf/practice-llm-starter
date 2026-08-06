package com.ucpcommerce.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.JsonRpcError;
import com.ucpcommerce.dto.JsonRpcRequest;
import com.ucpcommerce.dto.JsonRpcResponse;
import com.ucpcommerce.services.UcpMcpDispatcherService;

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

/**
 * UCP MCP binding endpoint: {@code POST /occ/v2/{baseSiteId}/ucp/mcp}.
 *
 * Deliberately distinct from the proprietary {@code /{baseSiteId}/mcp}
 * dialect, and — unlike {@code McpController} — <strong>stateless</strong>:
 * no {@code MCP-Session-Id} header, no session lookup, no cart preload.
 * Checkout continuity (later phases) is addressed by an explicit top-level
 * {@code id} the calling agent echoes on every call.
 *
 * Auth is the platform OAuth2 chain + {@code @Secured} roles (design R8);
 * the password-grant demo customer is the proven checkout path.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class UcpMcpController
{
	private static final Logger LOG = LoggerFactory.getLogger(UcpMcpController.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource(name = "ucpMcpDispatcherService")
	private UcpMcpDispatcherService ucpMcpDispatcherService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/mcp", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public String handlePost(@RequestBody final String body, final HttpServletResponse response)
	{
		try
		{
			final JsonRpcRequest request = objectMapper.readValue(body, JsonRpcRequest.class);

			if (!"2.0".equals(request.getJsonrpc()))
			{
				return writeResponse(JsonRpcResponse.error(request.getId(),
					JsonRpcError.INVALID_REQUEST, "jsonrpc must be \"2.0\""), response);
			}

			final JsonRpcResponse jsonRpcResponse = ucpMcpDispatcherService.dispatch(request);

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
			LOG.error("UCP MCP POST error", e);
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

	private String writeResponse(final JsonRpcResponse jsonRpcResponse, final HttpServletResponse response)
		throws Exception
	{
		response.setContentType("application/json");
		return objectMapper.writeValueAsString(jsonRpcResponse);
	}
}
