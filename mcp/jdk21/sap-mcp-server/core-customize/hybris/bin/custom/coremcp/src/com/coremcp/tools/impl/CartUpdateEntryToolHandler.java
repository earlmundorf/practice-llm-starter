package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartModificationData;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartUpdateEntryToolHandler implements McpToolHandler
{
	private CartFacade cartFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "cart_update_entry";
	}

	@Override
	public String getDescription()
	{
		return "Change the quantity of an item already in the cart. Use this when the user says " +
			"'change quantity', 'I want 3 instead', or 'update my cart'. Requires the entry number " +
			"from cart_get results (not the product code). Set quantity to 0 to remove the item. " +
			"Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"entryNumber", Map.of("type", "integer", "description", "The cart entry number to update (from cart_get results)"),
			"quantity", Map.of("type", "integer", "description", "New quantity (0 to remove the entry)", "minimum", 0)
		));
		schema.put("required", List.of("entryNumber", "quantity"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final long entryNumber = ((Number) args.get("entryNumber")).longValue();
			final long quantity = ((Number) args.get("quantity")).longValue();

			final CartModificationData result = cartFacade.updateCartEntry(entryNumber, quantity);
			return McpToolResult.success(objectMapper.writeValueAsString(result));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to update cart entry: " + e.getMessage());
		}
	}

	public void setCartFacade(final CartFacade cartFacade)
	{
		this.cartFacade = cartFacade;
	}
}
