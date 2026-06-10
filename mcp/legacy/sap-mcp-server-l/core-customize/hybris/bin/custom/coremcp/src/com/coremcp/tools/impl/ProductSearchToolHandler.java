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
		return "Search the ThinkShop catalog by keyword, with optional category filter and pagination. " +
			"Returns matching products with prices, stock status, and pagination metadata. " +
			"Use this when the user wants to browse, shop, find products, or asks questions like " +
			"'what do you have', 'show me laptops', or 'find something under $200'. " +
			"Pass an empty string for query to browse all products. " +
			"\n\nKnown category codes:\n" +
			"  Electronics:\n" +
			"  - 'computing' — laptops, monitors\n" +
			"  - 'mobile' — smartphones, tablets, smartwatches\n" +
			"  - 'audio' — headphones, speakers\n" +
			"  - 'accessories' — keyboards, mice, webcams\n" +
			"  Merch:\n" +
			"  - 'swag' — all ThinkShop branded merch\n" +
			"  - 'swag-apparel' — tees, hoodies, caps (use this when the user asks for swag CLOTHES/clothing/apparel)\n" +
			"  - 'swag-drinkware' — mugs and bottles (use this for drink-related swag requests)\n" +
			"  - 'swag-accessories' — stickers, totes, notebooks\n" +
			"When the user asks to browse a product TYPE, pick the most specific category code and pass an " +
			"empty query; use a keyword query for specific products or features. The word 'swag' is not in " +
			"product names, so a query-only search returns nothing for merch — always use the category. " +
			"Some products may be out of stock (stockLevelStatus 'outOfStock') — say so honestly and " +
			"suggest an in-stock alternative.";
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
			"pageSize", Map.of("type", "integer", "description", "Number of results per page (max 100). Default is small to keep the conversation light; ask for more only when the user wants a wider view.", "default", 5),
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
			final int pageSize = args.containsKey("pageSize") ? ((Number) args.get("pageSize")).intValue() : 5;
			final String sort = (String) args.getOrDefault("sort", "relevance");
			final String categoryCode = (String) args.get("categoryCode");

			// Hybris search-state encodes facet filters as :facet:value pairs
			// appended to "<query>:<sort>". Without this the categoryCode is silently
			// dropped and the search returns the whole catalog.
			final StringBuilder qb = new StringBuilder().append(query).append(":").append(sort);
			if (categoryCode != null && !categoryCode.isBlank())
			{
				qb.append(":category:").append(categoryCode);
			}
			final String searchQuery = qb.toString();

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
