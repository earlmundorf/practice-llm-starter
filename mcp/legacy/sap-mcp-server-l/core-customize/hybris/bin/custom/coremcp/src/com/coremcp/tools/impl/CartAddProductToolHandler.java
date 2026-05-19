package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartModificationData;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartAddProductToolHandler implements McpToolHandler
{
	private CartFacade cartFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "cart_add_product";
	}

	@Override
	public String getDescription()
	{
		return "Add a product to the shopping cart by product code. Creates a new entry or increases quantity " +
			"if the product is already in the cart. Use this when the user says 'add to cart', 'I want to buy', " +
			"'get me one of those', or similar purchase intent. You need the product code from a product_search " +
			"or product_get result. Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"productCode", Map.of("type", "string", "description", "Product code to add to cart"),
			"quantity", Map.of("type", "integer", "description", "Number of units to add", "default", 1, "minimum", 1)
		));
		schema.put("required", List.of("productCode"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String productCode = (String) args.get("productCode");
			final long quantity = args.containsKey("quantity") ? ((Number) args.get("quantity")).longValue() : 1L;

			final CartModificationData result = cartFacade.addToCart(productCode, quantity);
			return McpToolResult.success(objectMapper.writeValueAsString(result));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to add to cart: " + e.getMessage());
		}
	}

	@Required
	public void setCartFacade(final CartFacade cartFacade)
	{
		this.cartFacade = cartFacade;
	}
}
