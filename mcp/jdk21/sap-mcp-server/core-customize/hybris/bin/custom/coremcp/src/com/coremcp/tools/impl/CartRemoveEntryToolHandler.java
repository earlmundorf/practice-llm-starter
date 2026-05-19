package com.coremcp.tools.impl;

import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CartFacade;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartRemoveEntryToolHandler implements McpToolHandler
{
	private CartFacade cartFacade;

	@Override
	public String getName()
	{
		return "cart_remove_entry";
	}

	@Override
	public String getDescription()
	{
		return "Remove an item from the cart entirely. Use this when the user says 'remove', 'take it out', " +
			"or 'I don't want that anymore'. Requires the entry number from cart_get results (not the product code). " +
			"Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"entryNumber", Map.of("type", "integer", "description", "The cart entry number to remove (from cart_get results)")
		));
		schema.put("required", List.of("entryNumber"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final long entryNumber = ((Number) args.get("entryNumber")).longValue();
			cartFacade.updateCartEntry(entryNumber, 0);
			return McpToolResult.success("{\"status\":\"removed\",\"entryNumber\":" + entryNumber + "}");
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to remove cart entry: " + e.getMessage());
		}
	}

	public void setCartFacade(final CartFacade cartFacade)
	{
		this.cartFacade = cartFacade;
	}
}
