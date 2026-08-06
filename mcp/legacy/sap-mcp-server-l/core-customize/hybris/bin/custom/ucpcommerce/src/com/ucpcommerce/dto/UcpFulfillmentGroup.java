package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One group in {@code fulfillment.methods[].groups[]} (python-sdk
 * {@code FulfillmentGroup}): a package of line items with fulfillment
 * {@code options[]} the agent chooses from via {@code selected_option_id}.
 * ThinkShop always ships as one group; the options are the cart's supported
 * hybris delivery modes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpFulfillmentGroup
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("line_item_ids")
	private List<String> lineItemIds;

	@JsonProperty("options")
	private List<UcpFulfillmentOption> options;

	@JsonProperty("selected_option_id")
	private String selectedOptionId;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public List<String> getLineItemIds()
	{
		return lineItemIds;
	}

	public void setLineItemIds(final List<String> lineItemIds)
	{
		this.lineItemIds = lineItemIds;
	}

	public List<UcpFulfillmentOption> getOptions()
	{
		return options;
	}

	public void setOptions(final List<UcpFulfillmentOption> options)
	{
		this.options = options;
	}

	public String getSelectedOptionId()
	{
		return selectedOptionId;
	}

	public void setSelectedOptionId(final String selectedOptionId)
	{
		this.selectedOptionId = selectedOptionId;
	}
}
