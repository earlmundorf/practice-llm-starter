package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.coremcp.services.DeepLinkBuilder;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.search.ProductSearchFacade;
import de.hybris.platform.commercefacades.search.data.SearchStateData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductSearchToolHandler implements McpToolHandler
{
	private ProductSearchFacade<ProductData> productSearchFacade;
	private DeepLinkBuilder deepLinkBuilder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "product_search";
	}

	@Override
	public String getDescription()
	{
		return "Search the ThinkShop electronics catalog by keyword, with optional category filter and pagination. " +
			"Returns matching products with prices, stock status, and pagination metadata. " +
			"Use this when the user wants to browse, shop, find products, or asks questions like " +
			"'what do you have', 'show me laptops', or 'find something under $200'. " +
			"Pass an empty string for query to browse all products.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"query", Map.of("type", "string", "description", "Search keyword or phrase"),
			"categoryCode", Map.of("type", "string", "description", "Optional category code to filter results"),
			"currentPage", Map.of("type", "integer", "description", "Page number (0-based)", "default", 0),
			"pageSize", Map.of("type", "integer", "description", "Number of results per page (max 100)", "default", 20),
			"sort", Map.of("type", "string", "description", "Sort code (e.g., 'relevance', 'name-asc', 'price-asc')")
		));
		schema.put("required", List.of("query"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String query = (String) args.get("query");
			final int currentPage = args.containsKey("currentPage") ? ((Number) args.get("currentPage")).intValue() : 0;
			final int pageSize = args.containsKey("pageSize") ? ((Number) args.get("pageSize")).intValue() : 20;
			final String sort = (String) args.getOrDefault("sort", "relevance");

			final String searchQuery = query + ":" + sort;

			final SearchStateData searchState = new SearchStateData();
			final de.hybris.platform.commercefacades.search.data.SearchQueryData queryData = new de.hybris.platform.commercefacades.search.data.SearchQueryData();
			queryData.setValue(searchQuery);
			searchState.setQuery(queryData);

			final PageableData pageableData = new PageableData();
			pageableData.setCurrentPage(currentPage);
			pageableData.setPageSize(pageSize);

			final Object result = productSearchFacade.textSearch(searchState, pageableData);
			final ObjectNode tree = objectMapper.valueToTree(result);
			if (tree.get("results") instanceof ArrayNode results)
			{
				for (int i = 0; i < results.size(); i++)
				{
					if (results.get(i) instanceof ObjectNode product && product.has("code"))
					{
						final String url = deepLinkBuilder.productUrl(product.get("code").asText());
						if (url != null) product.put("url", url);
					}
				}
			}
			return McpToolResult.success(objectMapper.writeValueAsString(tree));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Product search failed: " + e.getMessage());
		}
	}

	@Required
	public void setProductSearchFacade(final ProductSearchFacade<ProductData> productSearchFacade)
	{
		this.productSearchFacade = productSearchFacade;
	}

	@Required
	public void setDeepLinkBuilder(final DeepLinkBuilder deepLinkBuilder)
	{
		this.deepLinkBuilder = deepLinkBuilder;
	}
}
