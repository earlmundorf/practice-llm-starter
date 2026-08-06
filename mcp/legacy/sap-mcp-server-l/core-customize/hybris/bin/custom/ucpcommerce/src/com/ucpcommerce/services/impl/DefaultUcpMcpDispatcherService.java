package com.ucpcommerce.services.impl;

import com.ucpcommerce.dto.JsonRpcError;
import com.ucpcommerce.dto.JsonRpcRequest;
import com.ucpcommerce.dto.JsonRpcResponse;
import com.ucpcommerce.services.UcpMcpDispatcherService;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stateless dispatcher for the UCP MCP binding.
 *
 * The UCP MCP binding skips the standard MCP lifecycle, but a generic MCP
 * client may still send {@code initialize} / {@code notifications/initialized}
 * — both are answered harmlessly (no session is created; there is no session
 * header). Per-call UCP metadata ({@code meta["ucp-agent"]},
 * {@code meta["idempotency-key"]}) is parsed into a {@link UcpToolContext}
 * and handed to every tool.
 */
public class DefaultUcpMcpDispatcherService implements UcpMcpDispatcherService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultUcpMcpDispatcherService.class);

	private static final String PROTOCOL_VERSION = "2025-11-25";
	private static final String SERVER_NAME = "ucpcommerce";
	private static final String SERVER_VERSION = "1.0.0";
	private static final String SERVER_INSTRUCTIONS =
		"UCP (Universal Commerce Protocol) MCP binding for the ThinkShop store. " +
		"This endpoint is stateless: no session is required, and each tools/call may carry " +
		"UCP metadata in params.meta (\"ucp-agent\" with the calling agent's profile; " +
		"\"idempotency-key\" on state-changing checkout operations). " +
		"Tool payloads are UCP objects with integer minor-unit prices.";

	private List<UcpTool> tools;
	private Map<String, UcpTool> toolMap;

	@PostConstruct
	public void init()
	{
		toolMap = tools.stream().collect(Collectors.toMap(UcpTool::getName, t -> t));
		LOG.info("UCP MCP dispatcher initialized with {} tools: {}", tools.size(),
			tools.stream().map(UcpTool::getName).collect(Collectors.joining(", ")));
	}

	@Override
	public JsonRpcResponse dispatch(final JsonRpcRequest request)
	{
		final String method = request.getMethod();

		switch (method == null ? "" : method)
		{
			case "initialize":
				return handleInitialize(request);

			case "tools/list":
				return handleToolsList(request);

			case "tools/call":
				return handleToolsCall(request);

			case "notifications/initialized":
				return null;

			default:
				if (method != null && method.startsWith("notifications/"))
				{
					return null;
				}
				return JsonRpcResponse.error(request.getId(), JsonRpcError.METHOD_NOT_FOUND,
					"Method not found: " + method);
		}
	}

	/**
	 * Tolerate a generic MCP client's {@code initialize} handshake. Unlike the
	 * proprietary dialect, no session is created and no session header is
	 * issued — the binding is stateless.
	 */
	protected JsonRpcResponse handleInitialize(final JsonRpcRequest request)
	{
		final Map<String, Object> result = new LinkedHashMap<>();
		result.put("protocolVersion", PROTOCOL_VERSION);
		result.put("capabilities", Map.of(
			"tools", Map.of("listChanged", false)
		));
		result.put("serverInfo", Map.of(
			"name", SERVER_NAME,
			"version", SERVER_VERSION
		));
		result.put("instructions", SERVER_INSTRUCTIONS);
		return JsonRpcResponse.success(request.getId(), result);
	}

	private JsonRpcResponse handleToolsList(final JsonRpcRequest request)
	{
		final List<Map<String, Object>> definitions = tools.stream()
			.map(UcpTool::getDefinition)
			.collect(Collectors.toList());
		return JsonRpcResponse.success(request.getId(), Map.of("tools", definitions));
	}

	@SuppressWarnings("unchecked")
	private JsonRpcResponse handleToolsCall(final JsonRpcRequest request)
	{
		final Map<String, Object> params = request.getParams();
		if (params == null)
		{
			return JsonRpcResponse.error(request.getId(), JsonRpcError.INVALID_PARAMS, "Missing params");
		}

		final String toolName = params.get("name") instanceof String ? (String) params.get("name") : null;
		if (toolName == null)
		{
			return JsonRpcResponse.error(request.getId(), JsonRpcError.INVALID_PARAMS, "Missing tool name");
		}

		final UcpTool tool = toolMap.get(toolName);
		if (tool == null)
		{
			return JsonRpcResponse.error(request.getId(), JsonRpcError.INVALID_PARAMS,
				"Unknown tool: " + toolName);
		}

		final Map<String, Object> arguments = params.get("arguments") instanceof Map
			? (Map<String, Object>) params.get("arguments")
			: Map.of();
		final UcpToolContext context = UcpToolContext.fromParams(params);

		LOG.info("UCP tools/call: {} with args: {}", toolName, arguments);

		try
		{
			final String payload = tool.execute(arguments, context);
			return JsonRpcResponse.toolResult(request.getId(), payload, false);
		}
		catch (final Exception e)
		{
			// Unexpected failure only — UCP business errors travel inside the
			// payload as messages[], not as isError tool results.
			LOG.error("UCP tool execution failed for {}: {}", toolName, e.getMessage(), e);
			return JsonRpcResponse.toolResult(request.getId(), "Internal error: " + e.getMessage(), true);
		}
	}

	@Required
	public void setTools(final List<UcpTool> tools)
	{
		this.tools = tools;
	}
}
