package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One line item in a checkout response (runbook §2.2): a stable per-cart-entry
 * id ({@code li_<entryNumber>}), the resolved {@code item} (with unit price in
 * integer minor units), the quantity, and per-line {@code totals[]}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpLineItem
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("item")
	private UcpProduct item;

	@JsonProperty("quantity")
	private Long quantity;

	@JsonProperty("totals")
	private List<UcpTotal> totals;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public UcpProduct getItem()
	{
		return item;
	}

	public void setItem(final UcpProduct item)
	{
		this.item = item;
	}

	public Long getQuantity()
	{
		return quantity;
	}

	public void setQuantity(final Long quantity)
	{
		this.quantity = quantity;
	}

	public List<UcpTotal> getTotals()
	{
		return totals;
	}

	public void setTotals(final List<UcpTotal> totals)
	{
		this.totals = totals;
	}
}
