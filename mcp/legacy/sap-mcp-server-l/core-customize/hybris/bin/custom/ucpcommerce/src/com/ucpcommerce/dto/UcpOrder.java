package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code order} block embedded in a completed checkout (design S3:
 * {@code complete_checkout} returns {@code status=completed} + the placed
 * order's id). Deliberately minimal in Phase 5 — the full UCP order schema
 * (line items, totals, fulfillment, order get/history) lands with the order
 * capability in Phase 6.
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
}
