package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.order.InvalidCartException;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OrderPlaceToolHandler implements McpToolHandler
{
	private CheckoutFacade checkoutFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "order_place";
	}

	@Override
	public String getDescription()
	{
		return "Place the order and complete the purchase. This is the final step of checkout (step 4). " +
			"The cart must already have a delivery address, delivery mode, and payment details set " +
			"(steps 1-3). On success, the cart is converted into an order and the order details are returned. " +
			"Always confirm with the user before placing an order. Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"securityCode", Map.of("type", "string", "description", "Card security code (CVV). Use '123' for mock/test payments.", "default", "123")
		));
		schema.put("required", Collections.emptyList());
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String securityCode = (String) args.getOrDefault("securityCode", "123");

			// Authorize payment first
			checkoutFacade.authorizePayment(securityCode);

			final OrderData order = checkoutFacade.placeOrder();
			// Note: checkoutFacade.placeOrder() already calls removeSessionCart() internally via afterPlaceOrder()

			return McpToolResult.success(objectMapper.writeValueAsString(order));
		}
		catch (final InvalidCartException e)
		{
			return McpToolResult.error("Cannot place order — cart is invalid: " + e.getMessage());
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to place order: " + e.getMessage());
		}
	}

	public void setCheckoutFacade(final CheckoutFacade checkoutFacade)
	{
		this.checkoutFacade = checkoutFacade;
	}

}
