package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The official {@code price.json} money object: {@code amount} in ISO 4217
 * integer minor units + {@code currency}. Amounts cross the major→minor
 * boundary only via {@code UcpMoneyConverter} (the silent-100×-bug guard).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpPrice
{
	@JsonProperty("amount")
	private Long amount;

	@JsonProperty("currency")
	private String currency;

	public UcpPrice()
	{
		// for Jackson
	}

	public UcpPrice(final Long amount, final String currency)
	{
		this.amount = amount;
		this.currency = currency;
	}

	public Long getAmount()
	{
		return amount;
	}

	public void setAmount(final Long amount)
	{
		this.amount = amount;
	}

	public String getCurrency()
	{
		return currency;
	}

	public void setCurrency(final String currency)
	{
		this.currency = currency;
	}
}
