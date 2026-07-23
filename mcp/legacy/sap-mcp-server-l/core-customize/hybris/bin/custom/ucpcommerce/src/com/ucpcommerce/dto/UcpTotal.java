package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in a checkout's {@code totals[]} array (runbook §2.2):
 * {@code {"type": "subtotal", "amount": 1299}}.
 *
 * <p><strong>{@code amount} is integer minor units</strong>, converted only by
 * {@code UcpMoneyConverter}. Discounts are reported as a positive amount under
 * {@code type="discount"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpTotal
{
	public static final String TYPE_SUBTOTAL = "subtotal";
	public static final String TYPE_DISCOUNT = "discount";
	public static final String TYPE_TAX = "tax";
	public static final String TYPE_SHIPPING = "shipping";
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
