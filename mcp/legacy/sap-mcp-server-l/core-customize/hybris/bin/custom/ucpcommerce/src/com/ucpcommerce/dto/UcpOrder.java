package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The UCP {@code order} object. Two uses:
 * <ul>
 *   <li>embedded (minimal: {@code id} + {@code created_at}) in a completed
 *       checkout (design S3) — replayable from the stored completion
 *       response, so it stays deliberately small;</li>
 *   <li>full order schema (Phase 6 order capability): the RAW top-level
 *       response for {@code get_order} — the official binding returns the
 *       order object itself (with its {@code ucp} envelope), no wrapper —
 *       or a summary (id/created_at/status/total) per order-history
 *       entry.</li>
 * </ul>
 * All optional fields are omitted when null ({@code NON_NULL}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpOrder
{
	/**
	 * Envelope for the top-level {@code get_order} response (official
	 * {@code order.json} requires it on the order itself). Null — and thus
	 * omitted — for the embedded completion block and history summaries.
	 */
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	/** The hybris order code — the id the client uses for order get/history. */
	@JsonProperty("id")
	private String id;

	/**
	 * The UCP checkout session this order was placed from ({@code order.json}
	 * requires it). Recovered from the session store by order code; omitted
	 * when unknown (legacy orders, or the session has been swept).
	 */
	@JsonProperty("checkout_id")
	private String checkoutId;

	@JsonProperty("created_at")
	private String createdAt;

	/**
	 * Permalink to the order on the merchant site — REQUIRED by the SDK's
	 * {@code OrderConfirmation} (the reference client reads
	 * {@code order.permalink_url} after a successful complete). Points at the
	 * ThinkShop storefront order page via coremcp's {@code DeepLinkBuilder}.
	 * Completion responses stored before this field existed replay without it
	 * (ADR 0003).
	 */
	@JsonProperty("permalink_url")
	private String permalinkUrl;

	/** Lowercased hybris order-status code (e.g. {@code created}, {@code completed}). */
	@JsonProperty("status")
	private String status;

	@JsonProperty("currency")
	private String currency;

	/** Order-shaped line items (quantity object + status), full order only. */
	@JsonProperty("line_items")
	private List<UcpOrderLineItem> lineItems;

	@JsonProperty("totals")
	private List<UcpTotal> totals;

	@JsonProperty("fulfillment")
	private UcpFulfillment fulfillment;

	/** Business-error messages for a top-level error response ({@code get_order} miss). */
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

	public String getCheckoutId()
	{
		return checkoutId;
	}

	public void setCheckoutId(final String checkoutId)
	{
		this.checkoutId = checkoutId;
	}

	public List<UcpMessage> getMessages()
	{
		return messages;
	}

	public void setMessages(final List<UcpMessage> messages)
	{
		this.messages = messages;
	}

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getCreatedAt()
	{
		return createdAt;
	}

	public void setCreatedAt(final String createdAt)
	{
		this.createdAt = createdAt;
	}

	public String getPermalinkUrl()
	{
		return permalinkUrl;
	}

	public void setPermalinkUrl(final String permalinkUrl)
	{
		this.permalinkUrl = permalinkUrl;
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

	public List<UcpOrderLineItem> getLineItems()
	{
		return lineItems;
	}

	public void setLineItems(final List<UcpOrderLineItem> lineItems)
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

	public UcpFulfillment getFulfillment()
	{
		return fulfillment;
	}

	public void setFulfillment(final UcpFulfillment fulfillment)
	{
		this.fulfillment = fulfillment;
	}
}
