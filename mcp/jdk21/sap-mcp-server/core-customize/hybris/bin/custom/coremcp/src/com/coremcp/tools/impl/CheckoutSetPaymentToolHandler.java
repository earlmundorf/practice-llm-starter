package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CardTypeData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CheckoutSetPaymentToolHandler implements McpToolHandler
{
	private CheckoutFacade checkoutFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "checkout_set_payment";
	}

	@Override
	public String getDescription()
	{
		return "Set payment details for the current order. This is step 3 of checkout. " +
			"For testing, call with no arguments to use default mock payment (Visa ending 1111). " +
			"All parameters have sensible defaults — you do not need to ask the user for card details " +
			"in a test/demo environment. Must be called after checkout_set_delivery_mode. " +
			"Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");

		final Map<String, Object> props = new LinkedHashMap<>();
		props.put("paymentId", Map.of("type", "string", "description", "ID of existing saved payment details. If provided, other fields are ignored."));
		props.put("cardNumber", Map.of("type", "string", "description", "Card number. Use '4111111111111111' for mock/test payments.", "default", "4111111111111111"));
		props.put("cardType", Map.of("type", "string", "description", "Card type code", "enum", java.util.List.of("visa", "master", "amex"), "default", "visa"));
		props.put("expiryMonth", Map.of("type", "string", "description", "Expiry month (01-12)", "default", "12"));
		props.put("expiryYear", Map.of("type", "string", "description", "Expiry year (YYYY)", "default", "2028"));
		props.put("nameOnCard", Map.of("type", "string", "description", "Cardholder name. Defaults to customer's name if omitted."));

		schema.put("properties", props);
		schema.put("required", Collections.emptyList());
		return schema;
	}

	@Override
	@SuppressWarnings("unchecked")
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			if (args.containsKey("paymentId") && args.get("paymentId") != null)
			{
				final String paymentId = (String) args.get("paymentId");
				final boolean success = checkoutFacade.setPaymentDetails(paymentId);

				final Map<String, Object> response = new LinkedHashMap<>();
				response.put("success", success);
				response.put("paymentId", paymentId);
				return McpToolResult.success(objectMapper.writeValueAsString(response));
			}

			final CCPaymentInfoData paymentInfo = new CCPaymentInfoData();
			paymentInfo.setCardNumber((String) args.getOrDefault("cardNumber", "4111111111111111"));
			paymentInfo.setExpiryMonth((String) args.getOrDefault("expiryMonth", "12"));
			paymentInfo.setExpiryYear((String) args.getOrDefault("expiryYear", "2028"));

			final String cardTypeCode = (String) args.getOrDefault("cardType", "visa");
			final CardTypeData cardType = new CardTypeData();
			cardType.setCode(cardTypeCode);
			paymentInfo.setCardType(cardType.getCode());

			if (args.containsKey("nameOnCard"))
			{
				paymentInfo.setAccountHolderName((String) args.get("nameOnCard"));
			}

			// Use delivery address as billing address by default
			if (args.containsKey("billingAddress") && args.get("billingAddress") instanceof Map)
			{
				final AddressData billingAddress = new AddressData();
				final Map<String, Object> ba = (Map<String, Object>) args.get("billingAddress");
				billingAddress.setFirstName((String) ba.get("firstName"));
				billingAddress.setLastName((String) ba.get("lastName"));
				billingAddress.setLine1((String) ba.get("line1"));
				billingAddress.setTown((String) ba.get("town"));
				billingAddress.setPostalCode((String) ba.get("postalCode"));
				if (ba.containsKey("country"))
				{
					final CountryData country = new CountryData();
					country.setIsocode((String) ba.get("country"));
					billingAddress.setCountry(country);
				}
				paymentInfo.setBillingAddress(billingAddress);
			}
			else
			{
				// Fall back to the cart's delivery address
				final AddressData deliveryAddr = checkoutFacade.getCheckoutCart().getDeliveryAddress();
				if (deliveryAddr != null)
				{
					paymentInfo.setBillingAddress(deliveryAddr);
				}
			}

			final CCPaymentInfoData created = checkoutFacade.createPaymentSubscription(paymentInfo);

			final Map<String, Object> response = new LinkedHashMap<>();
			response.put("success", created != null);
			if (created != null)
			{
				response.put("paymentDetails", Map.of(
					"id", created.getId() != null ? created.getId() : "",
					"cardType", cardTypeCode,
					"cardNumber", maskCardNumber(paymentInfo.getCardNumber()),
					"expiryMonth", paymentInfo.getExpiryMonth(),
					"expiryYear", paymentInfo.getExpiryYear()
				));
			}
			return McpToolResult.success(objectMapper.writeValueAsString(response));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to set payment: " + e.getMessage());
		}
	}

	private String maskCardNumber(final String cardNumber)
	{
		if (cardNumber == null || cardNumber.length() < 4)
		{
			return "****";
		}
		return "************" + cardNumber.substring(cardNumber.length() - 4);
	}

	public void setCheckoutFacade(final CheckoutFacade checkoutFacade)
	{
		this.checkoutFacade = checkoutFacade;
	}
}
