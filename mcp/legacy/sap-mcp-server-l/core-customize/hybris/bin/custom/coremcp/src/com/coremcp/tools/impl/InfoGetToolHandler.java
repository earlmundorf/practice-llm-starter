package com.coremcp.tools.impl;

import com.coremcp.services.KnowledgeSearchService;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InfoGetToolHandler implements McpToolHandler
{
	private KnowledgeSearchService search;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName() { return "info_get"; }

	@Override
	public String getDescription()
	{
		return "Fetch the full content of a single ThinkShop knowledge base entry by its unique id (uid). " +
			"Use this after info_search has identified a relevant uid, or when the user asks about a specific " +
			"topic and you already know the uid (e.g., 'returns-policy', 'shipping-info'). Returns title, " +
			"summary, full body, category, image, and tags.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"uid", Map.of("type", "string", "description", "The unique id of the knowledge entry, e.g. 'returns-policy'.")
		));
		schema.put("required", List.of("uid"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String uid = (String) args.get("uid");
			final var doc = search.getByUid(uid);
			if (doc.isEmpty()) return McpToolResult.error("No knowledge entry found for uid: " + uid);
			return McpToolResult.success(objectMapper.writeValueAsString(search.toJson(doc.get())));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("info_get failed: " + e.getMessage());
		}
	}

	@Required public void setKnowledgeSearchService(final KnowledgeSearchService s) { this.search = s; }
}
