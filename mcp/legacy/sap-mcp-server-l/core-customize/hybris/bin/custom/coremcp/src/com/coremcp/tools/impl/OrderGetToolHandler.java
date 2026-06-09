package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.coremcp.services.DeepLinkBuilder;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.OrderFacade;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderGetToolHandler implements McpToolHandler
{
	private OrderFacade orderFacade;
	private DeepLinkBuilder deepLinkBuilder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "order_get";
	}

	@Override
	public String getDescription()
	{
		return "Get details for a specific order by its order code (e.g., 'THINK-0001'). " +
			"Returns order status, line items, totals, delivery address, and payment information. " +
			"Use this when the user asks 'where is my order', 'track order', or wants details about " +
			"a specific order. Use order_history to list all orders first if the code is unknown. " +
			"Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"code", Map.of("type", "string", "description", "Order code (e.g., 'THINK-0001')")
		));
		schema.put("required", List.of("code"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String code = (String) args.get("code");
			final Object result = orderFacade.getOrderDetailsForCode(code);
			final ObjectNode tree = objectMapper.valueToTree(result);
			tree.put("url", deepLinkBuilder.orderUrl(code));
			return McpToolResult.success(objectMapper.writeValueAsString(tree));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Order not found: " + e.getMessage());
		}
	}

	@Required
	public void setOrderFacade(final OrderFacade orderFacade)
	{
		this.orderFacade = orderFacade;
	}

	@Required
	public void setDeepLinkBuilder(final DeepLinkBuilder deepLinkBuilder)
	{
		this.deepLinkBuilder = deepLinkBuilder;
	}
}
