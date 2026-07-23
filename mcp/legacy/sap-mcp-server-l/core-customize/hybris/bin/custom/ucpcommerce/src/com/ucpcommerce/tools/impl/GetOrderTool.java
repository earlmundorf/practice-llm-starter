package com.ucpcommerce.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.services.UcpOrderService;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UCP MCP order binding: {@code get_order} — full detail for one placed order
 * by its id (the order code returned by {@code complete_checkout} /
 * {@code list_orders}). Unknown id → payload with {@code ucp.status="error"}
 * and an {@code unrecoverable} {@code not_found} message (never a transport
 * error). Scoped to the authenticated customer.
 */
public class GetOrderTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpOrderService ucpOrderService;

	@Override
	public String getName()
	{
		return "get_order";
	}

	@Override
	public String getDescription()
	{
		return "Get full details for one of the customer's placed orders by its id " +
			"(the order.id returned by complete_checkout or list_orders). " +
			"Returns a UCP order object with status, line items, totals (integer minor units) and fulfillment.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"id", Map.of("type", "string", "description", "Order id, e.g. from complete_checkout's order.id or a list_orders entry")
		));
		schema.put("required", List.of("id"));
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		if (!(args.get("id") instanceof String) || ((String) args.get("id")).isBlank())
		{
			throw new IllegalArgumentException("id is required");
		}
		return objectMapper.writeValueAsString(ucpOrderService.getOrder((String) args.get("id")));
	}

	@Required
	public void setUcpOrderService(final UcpOrderService ucpOrderService)
	{
		this.ucpOrderService = ucpOrderService;
	}
}
