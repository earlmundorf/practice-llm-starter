package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in a checkout's {@code totals[]} array (runbook §2.2):
 * {@code {"type": "subtotal", "amount": 1299}}.
 *
 * <p><strong>{@code amount} is integer minor units</strong>, converted only by
 * {@code UcpMoneyConverter}, and SIGNED per the official schema
 * ({@code shopping/types/total.json}): {@code discount} entries MUST be
 * negative ({@code exclusiveMaximum: 0}); {@code subtotal}/{@code
 * fulfillment}/{@code tax}/{@code fee} MUST be ≥ 0. Delivery cost uses the
 * well-known type {@code fulfillment} (the earlier {@code shipping} spelling
 * and positive-discount convention were provisional — ADR 0003). The
 * {@code total} entry is emitted LAST (clients read {@code totals[-1]} as
 * the running total).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpTotal
{
	public static final String TYPE_SUBTOTAL = "subtotal";
	public static final String TYPE_DISCOUNT = "discount";
	public static final String TYPE_TAX = "tax";
	public static final String TYPE_FULFILLMENT = "fulfillment";
	public static final String TYPE_TOTAL = "total";

	@JsonProperty("type")
	private String type;

	/** Integer minor units. */
	@JsonProperty("amount")
	private Long amount;

	public UcpTotal()
	{
		// for Jackson
	}

	public UcpTotal(final String type, final Long amount)
	{
		this.type = type;
		this.amount = amount;
	}

	public String getType()
	{
		return type;
	}

	public void setType(final String type)
	{
		this.type = type;
	}

	public Long getAmount()
	{
		return amount;
	}

	public void setAmount(final Long amount)
	{
		this.amount = amount;
	}
}
