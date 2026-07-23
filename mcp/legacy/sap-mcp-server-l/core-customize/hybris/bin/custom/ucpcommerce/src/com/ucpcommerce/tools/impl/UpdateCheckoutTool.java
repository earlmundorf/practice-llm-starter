package com.ucpcommerce.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpCheckoutRequest;
import com.ucpcommerce.services.UcpCheckoutService;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UCP MCP checkout binding: {@code update_checkout} — mutate an existing
 * checkout addressed by its top-level {@code id} (the binding's stateless
 * addressing; per the spec the {@code checkout} payload MUST NOT contain an
 * {@code id}). Line items are declarative (desired end state), fulfillment
 * sets the destination/delivery mode, and the response's {@code status} is
 * derived server-side — a client-sent status is ignored.
 */
public class UpdateCheckoutTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCheckoutService ucpCheckoutService;

	@Override
	public String getName()
	{
		return "update_checkout";
	}

	@Override
	public String getDescription()
	{
		return "Update an existing checkout session by id. The checkout payload may contain: " +
			"line_items — the DESIRED end state of the cart (items absent from the list are removed, " +
			"quantities adjusted, new items added); buyer — replaces the stored buyer; " +
			"fulfillment {destination {first_name, last_name, line1, line2, city, region, postal_code, " +
			"country, phone_number}, delivery_mode} — sets the shipping destination (delivery_mode is " +
			"optional; the cheapest supported mode is auto-selected). The payload must NOT contain an id. " +
			"Status is derived server-side: it becomes ready_for_complete once the checkout has items, a " +
			"destination and a delivery mode. Promotions are recalculated — discounts appear in totals. " +
			"All prices in the response are integer minor units.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"id", Map.of("type", "string", "description", "Checkout id from create_checkout (ucp_chk_...)"),
			"checkout", Map.of(
				"type", "object",
				"description", "UCP checkout payload: line_items[{item:{id}, quantity}] (desired end state), "
					+ "optional buyer, optional fulfillment {destination, delivery_mode}. Must not contain an id.")
		));
		schema.put("required", List.of("id", "checkout"));
		return schema;
	}

	@Override
	@SuppressWarnings("unchecked")
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		if (!(args.get("id") instanceof String) || ((String) args.get("id")).isBlank())
		{
			throw new IllegalArgumentException("id is required");
		}
		if (!(args.get("checkout") instanceof Map))
		{
			throw new IllegalArgumentException("checkout is required and must be an object");
		}
		final Map<String, Object> checkoutArg = (Map<String, Object>) args.get("checkout");
		if (checkoutArg.containsKey("id"))
		{
			// Binding spec: the checkout payload MUST NOT contain an id — the
			// top-level id parameter addresses the resource.
			throw new IllegalArgumentException(
				"checkout payload must not contain an id; pass the id as the top-level parameter");
		}
		final UcpCheckoutRequest payload = objectMapper.convertValue(checkoutArg, UcpCheckoutRequest.class);
		return objectMapper.writeValueAsString(ucpCheckoutService.update((String) args.get("id"), payload));
	}

	@Required
	public void setUcpCheckoutService(final UcpCheckoutService ucpCheckoutService)
	{
		this.ucpCheckoutService = ucpCheckoutService;
	}
}
