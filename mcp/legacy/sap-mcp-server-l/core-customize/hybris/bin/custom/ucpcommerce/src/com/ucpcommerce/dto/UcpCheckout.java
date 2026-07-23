package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The UCP {@code checkout} response object (runbook §2.2): {@code ucp}
 * envelope + opaque {@code id} + derived {@code status} + line items, totals
 * (all money integer minor units) and buyer. Business failures use the same
 * shape with {@code ucp.status="error"} and {@code messages[]} — never a 500
 * or a transport-level error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
// Tolerant deserialization: the stored completion response is re-parsed on
// idempotent replay (Phase 5), and must survive additive future fields.
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpCheckout
{
	/** UCP checkout status lifecycle codes (design diagram S5). */
	public static final String STATUS_INCOMPLETE = "incomplete";
	public static final String STATUS_READY_FOR_COMPLETE = "ready_for_complete";
	public static final String STATUS_COMPLETE_IN_PROGRESS = "complete_in_progress";
	public static final String STATUS_COMPLETED = "completed";
	public static final String STATUS_CANCELED = "canceled";

	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("id")
	private String id;

	@JsonProperty("status")
	private String status;

	@JsonProperty("currency")
	private String currency;

	@JsonProperty("line_items")
	private List<UcpLineItem> lineItems;

	@JsonProperty("totals")
	private List<UcpTotal> totals;

	@JsonProperty("buyer")
	private UcpBuyer buyer;

	@JsonProperty("fulfillment")
	private UcpFulfillment fulfillment;

	/** The placed order — present only once the checkout is {@code completed} (S3). */
	@JsonProperty("order")
	private UcpOrder order;

	@JsonProperty("messages")
	private List<UcpMessage> messages;

	public UcpEnvelope getUcp()
	{
		return ucp;
	}

	public void setUcp(final UcpEnvelope ucp)
	{
		this.ucp = ucp;
	}

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(final String status)
	{
		this.status = status;
	}

	public String getCurrency()
	{
		return currency;
	}

	public void setCurrency(final String currency)
	{
		this.currency = currency;
	}

	public List<UcpLineItem> getLineItems()
	{
		return lineItems;
	}

	public void setLineItems(final List<UcpLineItem> lineItems)
	{
		this.lineItems = lineItems;
	}

	public List<UcpTotal> getTotals()
	{
		return totals;
	}

	public void setTotals(final List<UcpTotal> totals)
	{
		this.totals = totals;
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

	public UcpOrder getOrder()
	{
		return order;
	}

	public void setOrder(final UcpOrder order)
	{
		this.order = order;
	}

	public List<UcpMessage> getMessages()
	{
		return messages;
	}

	public void setMessages(final List<UcpMessage> messages)
	{
		this.messages = messages;
	}
}
