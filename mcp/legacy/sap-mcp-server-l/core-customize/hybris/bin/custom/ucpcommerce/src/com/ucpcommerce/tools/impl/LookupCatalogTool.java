package com.ucpcommerce.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.services.UcpCatalogService;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UCP MCP catalog binding: {@code lookup_catalog} — batch lookup by product id.
 * Unknown ids do not fail the call; each becomes a {@code not_found} entry in
 * the payload's {@code messages[]}.
 */
public class LookupCatalogTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCatalogService ucpCatalogService;

	@Override
	public String getName()
	{
		return "lookup_catalog";
	}

	@Override
	public String getDescription()
	{
		return "Look up catalog products by their ids (SKU codes). Returns UCP product objects " +
			"for the ids that exist; unknown ids are reported in messages[] without failing the call.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"ids", Map.of(
				"type", "array",
				"items", Map.of("type", "string"),
				"description", "Product ids (SKU codes) to look up")
		));
		schema.put("required", List.of("ids"));
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		if (!(args.get("ids") instanceof List))
		{
			throw new IllegalArgumentException("ids is required and must be an array of product ids");
		}
		final List<String> ids = ((List<?>) args.get("ids")).stream()
			.map(String::valueOf)
			.collect(Collectors.toList());

		return objectMapper.writeValueAsString(ucpCatalogService.lookup(ids));
	}

	@Required
	public void setUcpCatalogService(final UcpCatalogService ucpCatalogService)
	{
		this.ucpCatalogService = ucpCatalogService;
	}
}
