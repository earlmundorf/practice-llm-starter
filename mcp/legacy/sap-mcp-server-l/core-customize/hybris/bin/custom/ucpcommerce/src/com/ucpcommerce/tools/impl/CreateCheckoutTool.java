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
 * UCP MCP checkout binding: {@code create_checkout} — create a checkout
 * session from a {@code checkout} payload. Per the binding spec the payload
 * MUST NOT contain an {@code id}; the response returns the minted
 * {@code checkout.id}, which the agent echoes on all later calls.
 */
public class CreateCheckoutTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCheckoutService ucpCheckoutService;

	@Override
	public String getName()
	{
		return "create_checkout";
	}

	@Override
	public String getDescription()
	{
		return "Create a new checkout session from line items (and optional buyer). " +
			"The checkout payload must NOT contain an id — the response returns checkout.id, " +
			"which you must remember and pass to get_checkout and later checkout operations. " +
			"All prices in the response are integer minor units.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"checkout", Map.of(
				"type", "object",
				"description", "UCP checkout payload: line_items[{item:{id}, quantity}] and optional buyer "
					+ "{first_name, last_name, email, phone_number}. Must not contain an id.")
		));
		schema.put("required", List.of("checkout"));
		return schema;
	}

	@Override
	@SuppressWarnings("unchecked")
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		if (!(args.get("checkout") instanceof Map))
		{
			throw new IllegalArgumentException("checkout is required and must be an object");
		}
		final Map<String, Object> checkoutArg = (Map<String, Object>) args.get("checkout");
		if (checkoutArg.containsKey("id"))
		{
			// Binding spec: the checkout payload MUST NOT contain an id on create.
			throw new IllegalArgumentException("checkout payload must not contain an id on create");
		}
		final UcpCheckoutRequest payload = objectMapper.convertValue(checkoutArg, UcpCheckoutRequest.class);
		return objectMapper.writeValueAsString(ucpCheckoutService.create(payload));
	}

	@Required
	public void setUcpCheckoutService(final UcpCheckoutService ucpCheckoutService)
	{
		this.ucpCheckoutService = ucpCheckoutService;
	}
}
