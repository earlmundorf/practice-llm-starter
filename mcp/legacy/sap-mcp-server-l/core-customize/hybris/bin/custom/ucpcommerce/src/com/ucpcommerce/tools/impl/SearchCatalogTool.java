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
 * UCP MCP catalog binding: {@code search_catalog} — free-text catalog search.
 */
public class SearchCatalogTool implements UcpTool
{
	private static final int DEFAULT_PAGE_SIZE = 10;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCatalogService ucpCatalogService;

	@Override
	public String getName()
	{
		return "search_catalog";
	}

	@Override
	public String getDescription()
	{
		return "Search the store's product catalog with a free-text query. " +
			"Returns UCP product objects (integer minor-unit prices) with pagination. " +
			"Pass an empty query to browse the whole catalog.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"query", Map.of("type", "string", "description", "Free-text search query; empty string browses all products"),
			"page", Map.of("type", "integer", "description", "0-based page number", "default", 0),
			"page_size", Map.of("type", "integer", "description", "Results per page (1-50)", "default", DEFAULT_PAGE_SIZE)
		));
		schema.put("required", List.of("query"));
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		final String query = args.get("query") instanceof String ? (String) args.get("query") : "";
		final int page = args.get("page") instanceof Number ? ((Number) args.get("page")).intValue() : 0;
		final int pageSize = args.get("page_size") instanceof Number
			? ((Number) args.get("page_size")).intValue() : DEFAULT_PAGE_SIZE;

		return objectMapper.writeValueAsString(ucpCatalogService.search(query, page, pageSize));
	}

	@Required
	public void setUcpCatalogService(final UcpCatalogService ucpCatalogService)
	{
		this.ucpCatalogService = ucpCatalogService;
	}
}
