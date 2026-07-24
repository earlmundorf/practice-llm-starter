package com.ucpcommerce.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.services.UcpCheckoutService;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UCP MCP checkout binding: {@code get_checkout} — retrieve an existing
 * checkout by its opaque top-level {@code id} (the binding's stateless
 * addressing: the agent echoes {@code checkout.id} per call). Unknown or
 * expired ids yield a payload with {@code ucp.status="error"} and an
 * {@code unrecoverable} {@code not_found} message — never a transport error.
 */
public class GetCheckoutTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCheckoutService ucpCheckoutService;

	@Override
	public String getName()
	{
		return "get_checkout";
	}

	@Override
	public String getDescription()
	{
		return "Get the current state of a checkout session by its id (as returned by create_checkout). " +
			"Returns the full UCP checkout object with line items and integer minor-unit totals.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"id", Map.of("type", "string", "description", "Checkout id from create_checkout (ucp_chk_...)")
		));
		schema.put("required", List.of("id"));
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		if (!(args.get("id") instanceof String) || ((String) args.get("id")).isBlank())
		{
			throw new IllegalArgumentException("id is required");
		}
		return objectMapper.writeValueAsString(ucpCheckoutService.get((String) args.get("id")));
	}

	@Required
	public void setUcpCheckoutService(final UcpCheckoutService ucpCheckoutService)
	{
		this.ucpCheckoutService = ucpCheckoutService;
	}
}
