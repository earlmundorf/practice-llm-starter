package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One requested line item on create/update:
 * {@code {"item": {"id": "SKU"}, "quantity": 1}} (runbook §2.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpLineItemRequest
{
	@JsonProperty("item")
	private UcpItemRef item;

	@JsonProperty("quantity")
	private Long quantity;

	public UcpItemRef getItem()
	{
		return item;
	}

	public void setItem(final UcpItemRef item)
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
}
