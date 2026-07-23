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
 *   <li>full order schema (Phase 6 order capability): status, currency,
 *       line items, totals and fulfillment marshalled from the placed
 *       hybris order — or a summary (id/created_at/status/total) per
 *       order-history entry.</li>
 * </ul>
 * All optional fields are omitted when null ({@code NON_NULL}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpOrder
{
	/** The hybris order code — the id the client uses for order get/history. */
	@JsonProperty("id")
	private String id;

	@JsonProperty("created_at")
	private String createdAt;

	/** Lowercased hybris order-status code (e.g. {@code created}, {@code completed}). */
	@JsonProperty("status")
	private String status;

	@JsonProperty("currency")
	private String currency;

	@JsonProperty("line_items")
	private List<UcpLineItem> lineItems;

	@JsonProperty("totals")
	private List<UcpTotal> totals;

	@JsonProperty("fulfillment")
	private UcpFulfillment fulfillment;

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

	public UcpFulfillment getFulfillment()
	{
		return fulfillment;
	}

	public void setFulfillment(final UcpFulfillment fulfillment)
	{
		this.fulfillment = fulfillment;
	}
}
