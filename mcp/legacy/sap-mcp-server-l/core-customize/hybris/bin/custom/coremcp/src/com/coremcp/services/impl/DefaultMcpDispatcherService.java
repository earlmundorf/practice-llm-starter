package com.coremcp.services.impl;

import com.coremcp.dto.JsonRpcError;
import com.coremcp.dto.JsonRpcRequest;
import com.coremcp.dto.JsonRpcResponse;
import com.coremcp.dto.McpSession;
import com.coremcp.services.McpDispatcherService;
import com.coremcp.services.McpSessionService;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultMcpDispatcherService implements McpDispatcherService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMcpDispatcherService.class);
	private static final String PROTOCOL_VERSION = "2025-11-25";
	private static final String SERVER_NAME = "coremcp";
	private static final String SERVER_VERSION = "1.0.0";
	private static final String SERVER_INSTRUCTIONS =
		"ThinkShop is an electronics e-commerce store powered by SAP Commerce. " +
		"Use these tools whenever the user mentions ThinkShop, wants to shop, browse or search for products, " +
		"buy something, manage their shopping cart, check out, or look up past orders.\n\n" +
		"CATALOG: laptops, smartphones, tablets, monitors, headphones, keyboards, mice, webcams, smartwatches, and speakers.\n\n" +
		"CAPABILITIES:\n" +
		"- Search and browse the product catalog (product_search, product_get)\n" +
		"- Manage a shopping cart: view, add items, update quantities, remove items (cart_get, cart_add_product, cart_update_entry, cart_remove_entry)\n" +
		"- Complete checkout and place orders (checkout_set_delivery_address, checkout_set_delivery_mode, checkout_set_payment, order_place)\n" +
		"- View customer profile and order history (customer_get, order_history, order_get)\n\n" +
		"CHECKOUT FLOW (must follow this order):\n" +
		"1. cart must have items (cart_get to verify)\n" +
		"2. set delivery address (checkout_set_delivery_address)\n" +
		"3. set delivery mode — call with no args first to list options (checkout_set_delivery_mode)\n" +
		"4. set payment details (checkout_set_payment)\n" +
		"5. place the order (order_place)\n\n" +
		"All cart, checkout, and order tools require an authenticated customer session.";

	private McpSessionService mcpSessionService;
	private List<McpToolHandler> toolHandlers;

	private Map<String, McpToolHandler> toolHandlerMap;

	@PostConstruct
	public void init()
	{
		toolHandlerMap = toolHandlers.stream()
			.collect(Collectors.toMap(McpToolHandler::getName, h -> h));
		LOG.info("MCP Dispatcher initialized with {} tools: {}", toolHandlers.size(),
			toolHandlers.stream().map(McpToolHandler::getName).collect(Collectors.joining(", ")));
	}

	@Override
	public JsonRpcResponse dispatch(final JsonRpcRequest request, final McpSession session)
	{
		final String method = request.getMethod();

		switch (method)
		{
			case "tools/list":
				return handleToolsList(request);

			case "tools/call":
				return handleToolsCall(request);

			case "notifications/initialized":
				// Notification — no response needed
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

	@Override
	@SuppressWarnings("unchecked")
	public InitializeResult handleInitialize(final JsonRpcRequest request)
	{
		final Map<String, Object> params = request.getParams() != null ? request.getParams() : Map.of();
		final Map<String, Object> clientInfo = (Map<String, Object>) params.getOrDefault("clientInfo", Map.of());
		final String protocolVersion = (String) params.getOrDefault("protocolVersion", PROTOCOL_VERSION);

		final String sessionId = mcpSessionService.createSession(clientInfo, protocolVersion);

		final Map<String, Object> result = new LinkedHashMap<>();
		result.put("protocolVersion", PROTOCOL_VERSION);
		result.put("capabilities", Map.of(
			"tools", Map.of("listChanged", false),
			"logging", Map.of()
		));
		result.put("serverInfo", Map.of(
			"name", SERVER_NAME,
			"version", SERVER_VERSION
		));
		result.put("instructions", SERVER_INSTRUCTIONS);

		final JsonRpcResponse response = JsonRpcResponse.success(request.getId(), result);
		return new InitializeResult(response, sessionId);
	}

	private JsonRpcResponse handleToolsList(final JsonRpcRequest request)
	{
		final List<Map<String, Object>> tools = toolHandlers.stream()
			.map(McpToolHandler::getDefinition)
			.collect(Collectors.toList());

		return JsonRpcResponse.success(request.getId(), Map.of("tools", tools));
	}

	@SuppressWarnings("unchecked")
	private JsonRpcResponse handleToolsCall(final JsonRpcRequest request)
	{
		final Map<String, Object> params = request.getParams();
		if (params == null)
		{
			return JsonRpcResponse.error(request.getId(), JsonRpcError.INVALID_PARAMS, "Missing params");
		}

		final String toolName = (String) params.get("name");
		if (toolName == null)
		{
			return JsonRpcResponse.error(request.getId(), JsonRpcError.INVALID_PARAMS, "Missing tool name");
		}

		final McpToolHandler handler = toolHandlerMap.get(toolName);
		if (handler == null)
		{
			return JsonRpcResponse.error(request.getId(), JsonRpcError.INVALID_PARAMS,
				"Unknown tool: " + toolName);
		}

		final Map<String, Object> arguments = params.containsKey("arguments")
			? (Map<String, Object>) params.get("arguments")
			: Map.of();

		LOG.info("MCP tools/call: {} with args: {}", toolName, arguments);

		try
		{
			final McpToolResult result = handler.execute(arguments);
			return JsonRpcResponse.toolResult(request.getId(), result.getContent(), result.isError());
		}
		catch (final Exception e)
		{
			LOG.error("Tool execution failed for {}: {}", toolName, e.getMessage(), e);
			return JsonRpcResponse.toolResult(request.getId(), "Internal error: " + e.getMessage(), true);
		}
	}

	@Required
	public void setMcpSessionService(final McpSessionService mcpSessionService)
	{
		this.mcpSessionService = mcpSessionService;
	}

	@Required
	public void setToolHandlers(final List<McpToolHandler> toolHandlers)
	{
		this.toolHandlers = toolHandlers;
	}
}
