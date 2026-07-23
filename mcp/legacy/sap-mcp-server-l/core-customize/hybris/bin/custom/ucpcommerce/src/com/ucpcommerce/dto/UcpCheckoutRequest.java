package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The inbound {@code checkout} payload for {@code create_checkout} (and, from
 * Phase 4, {@code update_checkout}). Per the MCP checkout binding it MUST NOT
 * contain an {@code id} on create — the tool enforces that before this DTO is
 * built. Unknown fields (context, payment, ...) are tolerated; the parts a
 * given phase does not yet act on are simply ignored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpCheckoutRequest
{
	@JsonProperty("line_items")
	private List<UcpLineItemRequest> lineItems;

	@JsonProperty("buyer")
	private UcpBuyer buyer;

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
}
