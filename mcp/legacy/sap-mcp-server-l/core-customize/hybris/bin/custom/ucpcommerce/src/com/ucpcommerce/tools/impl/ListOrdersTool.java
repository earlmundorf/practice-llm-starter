package com.ucpcommerce.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.services.UcpOrderService;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UCP MCP order binding: {@code list_orders} — the authenticated customer's
 * paged order history (newest first), as UCP order summaries.
 */
public class ListOrdersTool implements UcpTool
{
	private static final int DEFAULT_PAGE_SIZE = 10;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpOrderService ucpOrderService;

	@Override
	public String getName()
	{
		return "list_orders";
	}

	@Override
	public String getDescription()
	{
		return "List the customer's placed orders (newest first) with pagination. " +
			"Returns UCP order summaries (id, created_at, status, total in integer minor units); " +
			"use get_order with an id for full details.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"page", Map.of("type", "integer", "description", "0-based page number", "default", 0),
			"page_size", Map.of("type", "integer", "description", "Results per page (1-50)", "default", DEFAULT_PAGE_SIZE),
			"statuses", Map.of("type", "array",
				"items", Map.of("type", "string"),
				"description", "Optional order-status filters (e.g. COMPLETED, CANCELLED); all statuses when omitted")
		));
		schema.put("required", List.of());
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		final int page = args.get("page") instanceof Number ? ((Number) args.get("page")).intValue() : 0;
		final int pageSize = args.get("page_size") instanceof Number
			? ((Number) args.get("page_size")).intValue() : DEFAULT_PAGE_SIZE;
		final List<String> statuses = args.get("statuses") instanceof List
			? ((List<?>) args.get("statuses")).stream().map(String::valueOf).collect(Collectors.toList())
			: null;

		return objectMapper.writeValueAsString(ucpOrderService.history(page, pageSize, statuses));
	}

	@Required
	public void setUcpOrderService(final UcpOrderService ucpOrderService)
	{
		this.ucpOrderService = ucpOrderService;
	}
}
