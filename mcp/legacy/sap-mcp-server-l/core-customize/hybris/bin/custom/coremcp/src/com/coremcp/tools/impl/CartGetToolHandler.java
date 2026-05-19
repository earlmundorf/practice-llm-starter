package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CartFacade;

import org.springframework.beans.factory.annotation.Required;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartGetToolHandler implements McpToolHandler
{
	private CartFacade cartFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "cart_get";
	}

	@Override
	public String getDescription()
	{
		return "View the current shopping cart contents, including line items, quantities, prices, and totals. " +
			"Use this when the user asks 'what's in my cart', 'show my cart', 'how much is my order', " +
			"or before checkout to verify cart contents. Also use this to get entry numbers needed by " +
			"cart_update_entry and cart_remove_entry. Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Collections.emptyMap());
		schema.put("required", List.of());
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final Object result = cartFacade.getSessionCart();
			return McpToolResult.success(objectMapper.writeValueAsString(result));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to get cart: " + e.getMessage());
		}
	}

	@Required
	public void setCartFacade(final CartFacade cartFacade)
	{
		this.cartFacade = cartFacade;
	}
}
