package com.coremcp.tools.impl;

import com.coremcp.services.KnowledgeSearchService;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.hybris.platform.solrfacetsearch.search.Document;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InfoSearchToolHandler implements McpToolHandler
{
	private KnowledgeSearchService search;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName() { return "info_search"; }

	@Override
	public String getDescription()
	{
		return "Search the ThinkShop knowledge base for shopper-facing content: policies (returns, shipping, " +
			"warranty, privacy, payment), brand/about/contact, marketing events and promotions, how-tos, and " +
			"buying guides. Use this whenever the user asks a question that is NOT about a specific product " +
			"or order, e.g. 'what is your return policy', 'any upcoming events', 'how do I track an order', " +
			"'is shipping free', 'what's your loyalty program'. Pass the user's natural-language question as " +
			"the query.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		final Map<String, Object> props = new LinkedHashMap<>();
		props.put("query", Map.of("type", "string", "description", "The user's question or keywords (natural language)."));
		props.put("category", Map.of(
			"type", "string",
			"description", "Optional category filter: policy, event, promo, guide, brand, howto, contact.",
			"enum", List.of("policy", "event", "promo", "guide", "brand", "howto", "contact")));
		props.put("pageSize", Map.of(
			"type", "integer",
			"description", "Max results to return (1-20).",
			"default", 5));
		schema.put("properties", props);
		schema.put("required", List.of("query"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String query = (String) args.getOrDefault("query", "");
			final String category = (String) args.get("category");
			final int pageSize = args.containsKey("pageSize") ? ((Number) args.get("pageSize")).intValue() : 5;

			final List<Document> results = search.search(query, category, pageSize);
			final List<Map<String, Object>> json = results.stream().map(search::toJson).toList();
			return McpToolResult.success(objectMapper.writeValueAsString(Map.of(
				"results", json,
				"count", json.size()
			)));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("info_search failed: " + e.getMessage());
		}
	}

	@Required public void setKnowledgeSearchService(final KnowledgeSearchService s) { this.search = s; }
}
