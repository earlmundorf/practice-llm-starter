package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.coremcp.services.DeepLinkBuilder;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;

import org.springframework.beans.factory.annotation.Required;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProductGetToolHandler implements McpToolHandler
{
	private ProductFacade productFacade;
	private DeepLinkBuilder deepLinkBuilder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "product_get";
	}

	@Override
	public String getDescription()
	{
		return "Get detailed information for a specific ThinkShop product by its code (e.g., 'LAPTOP_PRO_15'). " +
			"Returns full product data including description, price, stock level, images, categories, and reviews. " +
			"Use this when the user asks about a specific product you already know the code for " +
			"(e.g., from a previous product_search result). Use product_search instead for browsing or discovery.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"code", Map.of("type", "string", "description", "Product code (e.g., 'LAPTOP_PRO_15')"),
			"options", Map.of("type", "array",
				"items", Map.of("type", "string", "enum", List.of("BASIC", "PRICE", "STOCK", "DESCRIPTION", "GALLERY", "CATEGORIES", "REVIEW", "CLASSIFICATION", "REFERENCES", "PROMOTIONS")),
				"description", "Data options to include. Defaults to all if omitted.",
				"default", List.of("BASIC", "PRICE", "STOCK", "DESCRIPTION", "CATEGORIES"))
		));
		schema.put("required", List.of("code"));
		return schema;
	}

	@Override
	@SuppressWarnings("unchecked")
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String code = (String) args.get("code");

			Collection<ProductOption> options;
			if (args.containsKey("options") && args.get("options") instanceof List)
			{
				options = ((List<String>) args.get("options")).stream()
					.map(ProductOption::valueOf)
					.collect(Collectors.toList());
			}
			else
			{
				options = Arrays.asList(ProductOption.BASIC, ProductOption.PRICE, ProductOption.STOCK,
					ProductOption.DESCRIPTION, ProductOption.CATEGORIES);
			}

			final Object result = productFacade.getProductForCodeAndOptions(code, options);
			final ObjectNode tree = objectMapper.valueToTree(result);
			final String url = deepLinkBuilder.productUrl(code);
			if (url != null) tree.put("url", url);
			return McpToolResult.success(objectMapper.writeValueAsString(tree));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Product not found: " + e.getMessage());
		}
	}

	@Required
	public void setProductFacade(final ProductFacade productFacade)
	{
		this.productFacade = productFacade;
	}

	@Required
	public void setDeepLinkBuilder(final DeepLinkBuilder deepLinkBuilder)
	{
		this.deepLinkBuilder = deepLinkBuilder;
	}
}
