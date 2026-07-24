package com.ucpcommerce.tools.impl;

import com.coremcp.services.KnowledgeSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpKnowledgeSearchResponse;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import de.hybris.platform.solrfacetsearch.search.Document;
import de.hybris.platform.util.Config;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Custom capability {@code com.thinkshop.knowledge} (design R7):
 * {@code search_knowledge} — free-text search over the ThinkShop knowledge
 * base (Solr {@code knowledgeIndex}) via coremcp's
 * {@code KnowledgeSearchService}.
 */
public class SearchKnowledgeTool implements UcpTool
{
	private static final int DEFAULT_PAGE_SIZE = 5;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private KnowledgeSearchService knowledgeSearchService;

	@Override
	public String getName()
	{
		return "search_knowledge";
	}

	@Override
	public String getDescription()
	{
		return "Search the store's knowledge base (custom capability com.thinkshop.knowledge): policies " +
			"(returns, shipping, warranty), brand/contact info, events, promotions content, how-tos and " +
			"buying guides. Returns entries with uid, category, title, summary and body; use get_knowledge " +
			"with a uid for a single entry.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"query", Map.of("type", "string", "description", "Free-text question or keywords"),
			"category", Map.of("type", "string",
				"description", "Optional category filter",
				"enum", List.of("policy", "event", "promo", "guide", "brand", "howto", "contact")),
			"page_size", Map.of("type", "integer", "description", "Max results (1-50)", "default", DEFAULT_PAGE_SIZE)
		));
		schema.put("required", List.of("query"));
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		if (!(args.get("query") instanceof String))
		{
			throw new IllegalArgumentException("query is required");
		}
		final String query = (String) args.get("query");
		final String category = args.get("category") instanceof String ? (String) args.get("category") : null;
		final int pageSize = args.get("page_size") instanceof Number
			? ((Number) args.get("page_size")).intValue() : DEFAULT_PAGE_SIZE;

		final List<Document> documents = knowledgeSearchService.search(query, category, pageSize);
		final List<Map<String, Object>> results = documents.stream()
			.map(knowledgeSearchService::toJson)
			.collect(Collectors.toList());

		final UcpKnowledgeSearchResponse response = new UcpKnowledgeSearchResponse();
		response.setUcp(successEnvelope());
		response.setResults(results);
		response.setCount(results.size());
		return objectMapper.writeValueAsString(response);
	}

	protected UcpEnvelope successEnvelope()
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus("success");
		return envelope;
	}

	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY, UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	@Required
	public void setKnowledgeSearchService(final KnowledgeSearchService knowledgeSearchService)
	{
		this.knowledgeSearchService = knowledgeSearchService;
	}
}
