package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The inbound {@code checkout} payload for {@code create_checkout} and
 * {@code update_checkout}. Per the MCP checkout binding it MUST NOT contain
 * an {@code id} — the tools enforce that before this DTO is built. Unknown
 * fields (context, payment, echoed status/totals, ...) are tolerated; the
 * parts a given phase does not yet act on are simply ignored — in particular
 * a client-supplied {@code status} is never trusted (status is derived
 * server-side, design S5).
 *
 * On update, {@code line_items} is declarative: it is the desired end state
 * of the cart (absent fields — a null {@code line_items}, {@code buyer} or
 * {@code fulfillment} — leave that aspect unchanged).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpCheckoutRequest
{
	/**
	 * The client's {@code ucp} metadata block. Only {@code version} is read —
	 * a request pinned to a version this server does not implement is
	 * rejected ({@code version_unsupported}, HTTP 422 over REST).
	 */
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("line_items")
	private List<UcpLineItemRequest> lineItems;

	@JsonProperty("buyer")
	private UcpBuyer buyer;

	@JsonProperty("fulfillment")
	private UcpFulfillment fulfillment;

	/** Payment instruments — only acted on by {@code complete_checkout} (Phase 5). */
	@JsonProperty("payment")
	private UcpPayment payment;

	/** Discount (voucher) codes — declarative like {@code line_items}. */
	@JsonProperty("discounts")
	private UcpDiscounts discounts;

	/**
	 * Unmapped request fields (currency, status echo, context, …) — retained
	 * so the idempotency request hash reflects the payload AS SENT: a same-key
	 * retry that differs only in a field this phase does not act on must
	 * still be a 409 conflict, not a replay.
	 */
	@JsonAnySetter
	private final Map<String, Object> extras = new LinkedHashMap<>();

	@JsonAnyGetter
	public Map<String, Object> getExtras()
	{
		return extras;
	}

	public UcpEnvelope getUcp()
	{
		return ucp;
	}

	public void setUcp(final UcpEnvelope ucp)
	{
		this.ucp = ucp;
	}

	public List<UcpLineItemRequest> getLineItems()
	{
		return lineItems;
	}

	public void setLineItems(final List<UcpLineItemRequest> lineItems)
	{
		this.lineItems = lineItems;
	}

	public UcpBuyer getBuyer()
	{
		return buyer;
	}

	public void setBuyer(final UcpBuyer buyer)
	{
		this.buyer = buyer;
	}

	public UcpFulfillment getFulfillment()
	{
		return fulfillment;
	}

	public void setFulfillment(final UcpFulfillment fulfillment)
	{
		this.fulfillment = fulfillment;
	}

	public UcpPayment getPayment()
	{
		return payment;
	}

	public void setPayment(final UcpPayment payment)
	{
		this.payment = payment;
	}

	public UcpDiscounts getDiscounts()
	{
		return discounts;
	}

	public void setDiscounts(final UcpDiscounts discounts)
	{
		this.discounts = discounts;
	}
}
