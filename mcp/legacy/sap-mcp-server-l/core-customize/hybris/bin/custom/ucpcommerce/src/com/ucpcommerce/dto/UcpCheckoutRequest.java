package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
	@JsonProperty("line_items")
	private List<UcpLineItemRequest> lineItems;

	@JsonProperty("buyer")
	private UcpBuyer buyer;

	@JsonProperty("fulfillment")
	private UcpFulfillment fulfillment;

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
}
