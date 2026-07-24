package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A UCP catalog product (the {@code item} shape from the runbook's checkout
 * contract, extended with catalog fields).
 *
 * <p><strong>{@code price} is integer minor units</strong> ($12.99 → 1299),
 * converted exactly once at the marshalling boundary by
 * {@code UcpMoneyConverter} — never ad hoc (the silent-100×-bug guard).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpProduct
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("title")
	private String title;

	@JsonProperty("description")
	private String description;

	/** Integer minor units (e.g. cents for USD). */
	@JsonProperty("price")
	private Long price;

	@JsonProperty("currency")
	private String currency;

	/** {@code in_stock} / {@code low_stock} / {@code out_of_stock}. */
	@JsonProperty("availability")
	private String availability;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(final String title)
	{
		this.title = title;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(final String description)
	{
		this.description = description;
	}

	public Long getPrice()
	{
		return price;
	}

	public void setPrice(final Long price)
	{
		this.price = price;
	}

	public String getCurrency()
	{
		return currency;
	}

	public void setCurrency(final String currency)
	{
		this.currency = currency;
	}

	public String getAvailability()
	{
		return availability;
	}

	public void setAvailability(final String availability)
	{
		this.availability = availability;
	}
}
