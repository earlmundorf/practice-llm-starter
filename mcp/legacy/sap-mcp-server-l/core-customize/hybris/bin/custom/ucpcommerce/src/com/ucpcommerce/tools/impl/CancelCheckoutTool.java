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
 * UCP MCP checkout binding: {@code cancel_checkout} — cancel a checkout
 * session by its top-level {@code id}. Idempotent and terminal (design S5):
 * canceling an already-canceled checkout re-returns the canceled state; a
 * completed checkout can no longer be canceled. Per the binding spec the call
 * MUST carry {@code meta["idempotency-key"]}.
 */
public class CancelCheckoutTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCheckoutService ucpCheckoutService;

	@Override
	public String getName()
	{
		return "cancel_checkout";
	}

	@Override
	public String getDescription()
	{
		return "Cancel a checkout session by id. Terminal and idempotent: repeating the cancel returns " +
			"the same canceled checkout. A completed checkout cannot be canceled. The call MUST carry a " +
			"meta[\"idempotency-key\"] (UUID).";
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
		final String idempotencyKey = context != null ? context.getIdempotencyKey() : null;
		if (idempotencyKey == null || idempotencyKey.isBlank())
		{
			// Binding spec: cancel MUST carry meta["idempotency-key"] (client
			// protocol bug when absent → MCP isError, not a UCP message).
			throw new IllegalArgumentException("meta[\"idempotency-key\"] is required for cancel_checkout");
		}
		return objectMapper.writeValueAsString(
			ucpCheckoutService.cancel((String) args.get("id"), idempotencyKey));
	}

	@Required
	public void setUcpCheckoutService(final UcpCheckoutService ucpCheckoutService)
	{
		this.ucpCheckoutService = ucpCheckoutService;
	}
}
