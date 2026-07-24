package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One successfully applied discount ({@code discount.json#applied_discount}):
 * {@code title} and {@code amount} are REQUIRED; {@code amount} is the
 * POSITIVE magnitude in integer minor units (the signed effect appears as the
 * negative {@code discount} entry in {@code totals[]}); {@code code} echoes
 * the server-side canonical code and is omitted for automatic discounts.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpAppliedDiscount
{
	@JsonProperty("code")
	private String code;

	@JsonProperty("title")
	private String title;

	/** Positive magnitude in integer minor units. */
	@JsonProperty("amount")
	private Long amount;

	@JsonProperty("automatic")
	private Boolean automatic;

	public UcpAppliedDiscount()
	{
		// for Jackson
	}

	public UcpAppliedDiscount(final String code, final String title, final Long amount)
	{
		this.code = code;
		this.title = title;
		this.amount = amount;
	}

	public String getCode()
	{
		return code;
	}

	public void setCode(final String code)
	{
		this.code = code;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(final String title)
	{
		this.title = title;
	}

	public Long getAmount()
	{
		return amount;
	}

	public void setAmount(final Long amount)
	{
		this.amount = amount;
	}

	public Boolean getAutomatic()
	{
		return automatic;
	}

	public void setAutomatic(final Boolean automatic)
	{
		this.automatic = automatic;
	}
}
