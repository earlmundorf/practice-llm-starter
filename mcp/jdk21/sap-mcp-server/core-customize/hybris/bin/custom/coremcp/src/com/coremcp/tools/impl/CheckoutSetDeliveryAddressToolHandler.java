package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckoutSetDeliveryAddressToolHandler implements McpToolHandler
{
	private CheckoutFacade checkoutFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "checkout_set_delivery_address";
	}

	@Override
	public String getDescription()
	{
		return "Set the shipping address for the current order. This is step 1 of checkout. " +
			"Provide either an addressId from the customer's saved addresses (use customer_get to find them), " +
			"or provide full address fields (firstName, lastName, line1, town, postalCode, country) to use a new address. " +
			"Must be called before checkout_set_delivery_mode. Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"addressId", Map.of("type", "string", "description", "ID of an existing address from the customer's address book"),
			"firstName", Map.of("type", "string", "description", "First name (required if addressId not provided)"),
			"lastName", Map.of("type", "string", "description", "Last name (required if addressId not provided)"),
			"line1", Map.of("type", "string", "description", "Street address line 1 (required if addressId not provided)"),
			"line2", Map.of("type", "string", "description", "Street address line 2"),
			"town", Map.of("type", "string", "description", "City/town (required if addressId not provided)"),
			"postalCode", Map.of("type", "string", "description", "Postal/ZIP code (required if addressId not provided)"),
			"country", Map.of("type", "string", "description", "Country ISO code, e.g., 'US' (required if addressId not provided)")
		));
		schema.put("required", Collections.emptyList());
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final AddressData addressData = new AddressData();

			if (args.containsKey("addressId"))
			{
				addressData.setId((String) args.get("addressId"));
			}
			else
			{
				addressData.setFirstName((String) args.get("firstName"));
				addressData.setLastName((String) args.get("lastName"));
				addressData.setLine1((String) args.get("line1"));
				addressData.setLine2((String) args.get("line2"));
				addressData.setTown((String) args.get("town"));
				addressData.setPostalCode((String) args.get("postalCode"));

				if (args.containsKey("country"))
				{
					final CountryData countryData = new CountryData();
					countryData.setIsocode((String) args.get("country"));
					addressData.setCountry(countryData);
				}
			}

			final boolean success = checkoutFacade.setDeliveryAddress(addressData);

			final Map<String, Object> response = new LinkedHashMap<>();
			response.put("success", success);
			if (success)
			{
				response.put("deliveryAddress", Map.of(
					"firstName", addressData.getFirstName() != null ? addressData.getFirstName() : "",
					"lastName", addressData.getLastName() != null ? addressData.getLastName() : "",
					"line1", addressData.getLine1() != null ? addressData.getLine1() : "",
					"town", addressData.getTown() != null ? addressData.getTown() : "",
					"postalCode", addressData.getPostalCode() != null ? addressData.getPostalCode() : ""
				));
			}
			return McpToolResult.success(objectMapper.writeValueAsString(response));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to set delivery address: " + e.getMessage());
		}
	}

	public void setCheckoutFacade(final CheckoutFacade checkoutFacade)
	{
		this.checkoutFacade = checkoutFacade;
	}
}
