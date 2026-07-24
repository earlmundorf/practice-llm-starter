package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The official {@code price_range.json} block: {@code min} / {@code max}
 * prices across a product's variants. ThinkShop products are variantless on
 * the storefront, so min == max == the product price.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpPriceRange
{
	@JsonProperty("min")
	private UcpPrice min;

	@JsonProperty("max")
	private UcpPrice max;

	public UcpPriceRange()
	{
		// for Jackson
	}

	public UcpPriceRange(final UcpPrice min, final UcpPrice max)
	{
		this.min = min;
		this.max = max;
	}

	public UcpPrice getMin()
	{
		return min;
	}

	public void setMin(final UcpPrice min)
	{
		this.min = min;
	}

	public UcpPrice getMax()
	{
		return max;
	}

	public void setMax(final UcpPrice max)
	{
		this.max = max;
	}
}
