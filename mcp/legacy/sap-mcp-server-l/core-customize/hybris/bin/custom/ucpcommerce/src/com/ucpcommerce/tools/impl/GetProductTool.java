package com.ucpcommerce.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.services.UcpCatalogService;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UCP MCP catalog binding: {@code get_product} — single-product detail.
 * Unknown id → payload with {@code ucp.status="error"} and an
 * {@code unrecoverable} {@code not_found} message (never a transport error).
 */
public class GetProductTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCatalogService ucpCatalogService;

	@Override
	public String getName()
	{
		return "get_product";
	}

	@Override
	public String getDescription()
	{
		return "Get detailed information for one catalog product by its id (SKU code). " +
			"Returns a UCP product object with integer minor-unit price, currency, and availability.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"id", Map.of("type", "string", "description", "Product id (SKU code), e.g. from a search_catalog result")
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
		return objectMapper.writeValueAsString(ucpCatalogService.getProduct((String) args.get("id")));
	}

	@Required
	public void setUcpCatalogService(final UcpCatalogService ucpCatalogService)
	{
		this.ucpCatalogService = ucpCatalogService;
	}
}
