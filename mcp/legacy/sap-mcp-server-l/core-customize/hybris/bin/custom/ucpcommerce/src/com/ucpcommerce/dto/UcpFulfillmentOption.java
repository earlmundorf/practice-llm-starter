package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One option in {@code fulfillment.methods[].groups[].options[]} (python-sdk
 * {@code FulfillmentOption}): id, title and a totals breakdown (the sample
 * server emits {@code subtotal} + {@code total}, both the option's cost in
 * minor units). ThinkShop option ids are the hybris delivery-mode codes, so
 * {@code selected_option_id} maps 1:1 onto
 * {@code CheckoutFacade.setDeliveryMode}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpFulfillmentOption
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("title")
	private String title;

	@JsonProperty("description")
	private String description;

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

	public List<UcpTotal> getTotals()
	{
		return totals;
	}

	public void setTotals(final List<UcpTotal> totals)
	{
		this.totals = totals;
	}
}
