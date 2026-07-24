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
 * UCP MCP checkout binding: {@code complete_checkout} — place the order
 * (design S3). Addressed by the top-level {@code id}; the {@code checkout}
 * payload carries {@code payment.instruments[{handler_id, credential}]}
 * referencing the declared mock handler (R9). Per the binding spec the call
 * MUST carry {@code meta["idempotency-key"]}: a duplicate key replays the
 * stored completion response — never a second order.
 */
public class CompleteCheckoutTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private UcpCheckoutService ucpCheckoutService;

	@Override
	public String getName()
	{
		return "complete_checkout";
	}

	@Override
	public String getDescription()
	{
		return "Complete a checkout session and place the order (mock payment). Requires the checkout " +
			"to be ready_for_complete (items + destination + delivery mode). The checkout payload must " +
			"contain payment.instruments with handler_id \"thinkshop_mock_card\" (any credential token is " +
			"accepted by this demo handler) and must NOT contain an id. The call MUST carry a unique " +
			"meta[\"idempotency-key\"] (UUID): replaying the same key returns the same completed checkout " +
			"and never places a second order. On success the response has status \"completed\" and an " +
			"embedded order block with order.id. All prices are integer minor units.";
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
				"description", "UCP checkout payload with payment.instruments[{handler_id: "
					+ "\"thinkshop_mock_card\", credential: {token}}]. Must not contain an id.")
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
			// Corrected rule (ADR 0003): an id MATCHING the top-level parameter
			// is accepted (SDK request shape); a mismatch is a protocol bug.
			if (!args.get("id").equals(checkoutArg.get("id")))
			{
				throw new IllegalArgumentException(
					"checkout payload id does not match the top-level id parameter");
			}
			checkoutArg.remove("id");
		}
		final String idempotencyKey = context != null ? context.getIdempotencyKey() : null;
		if (idempotencyKey == null || idempotencyKey.isBlank())
		{
			// Binding spec: complete MUST carry meta["idempotency-key"] — a
			// missing key is a client protocol bug (MCP isError), not a UCP
			// business error.
			throw new IllegalArgumentException("meta[\"idempotency-key\"] is required for complete_checkout");
		}
		final UcpCheckoutRequest payload = objectMapper.convertValue(checkoutArg, UcpCheckoutRequest.class);
		return objectMapper.writeValueAsString(
			ucpCheckoutService.complete((String) args.get("id"), payload, idempotencyKey));
	}

	@Required
	public void setUcpCheckoutService(final UcpCheckoutService ucpCheckoutService)
	{
		this.ucpCheckoutService = ucpCheckoutService;
	}
}
