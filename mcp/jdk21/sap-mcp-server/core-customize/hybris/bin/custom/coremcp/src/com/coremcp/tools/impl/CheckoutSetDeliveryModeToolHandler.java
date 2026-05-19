package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CheckoutSetDeliveryModeToolHandler implements McpToolHandler
{
	private CheckoutFacade checkoutFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "checkout_set_delivery_mode";
	}

	@Override
	public String getDescription()
	{
		return "Set the shipping method for the current order. This is step 2 of checkout. " +
			"Call with no arguments first to list available shipping options and their costs, " +
			"then call again with the chosen deliveryModeCode. A delivery address must be set first " +
			"(checkout_set_delivery_address). Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"deliveryModeCode", Map.of("type", "string", "description", "Delivery mode code (e.g., 'thinkshop-standard'). Omit to list available modes.")
		));
		schema.put("required", Collections.emptyList());
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			if (!args.containsKey("deliveryModeCode") || args.get("deliveryModeCode") == null)
			{
				// List available delivery modes
				final List<? extends DeliveryModeData> modes = checkoutFacade.getSupportedDeliveryModes();
				final List<Map<String, Object>> modeList = modes.stream()
					.map(m -> {
						final Map<String, Object> modeMap = new LinkedHashMap<>();
						modeMap.put("code", m.getCode());
						modeMap.put("name", m.getName());
						if (m.getDeliveryCost() != null)
						{
							modeMap.put("deliveryCost", Map.of(
								"value", m.getDeliveryCost().getValue(),
								"formattedValue", m.getDeliveryCost().getFormattedValue() != null ? m.getDeliveryCost().getFormattedValue() : ""
							));
						}
						return modeMap;
					})
					.collect(Collectors.toList());

				return McpToolResult.success(objectMapper.writeValueAsString(Map.of("deliveryModes", modeList)));
			}
			else
			{
				final String deliveryModeCode = (String) args.get("deliveryModeCode");
				final boolean success = checkoutFacade.setDeliveryMode(deliveryModeCode);

				final Map<String, Object> response = new LinkedHashMap<>();
				response.put("success", success);
				if (success)
				{
					response.put("deliveryMode", Map.of("code", deliveryModeCode));
				}
				return McpToolResult.success(objectMapper.writeValueAsString(response));
			}
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to set delivery mode: " + e.getMessage());
		}
	}

	public void setCheckoutFacade(final CheckoutFacade checkoutFacade)
	{
		this.checkoutFacade = checkoutFacade;
	}
}
