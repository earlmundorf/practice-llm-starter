package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One fulfillment method in the spec's negotiation flow
 * ({@code fulfillment.methods[]} — python-sdk {@code FulfillmentMethod}).
 * The agent PUTs a bare method ({@code id}, {@code type},
 * {@code line_item_ids}); the server replies with {@code destinations[]}
 * (saved addresses); the agent selects one via
 * {@code selected_destination_id}; the server replies with
 * {@code groups[].options[]} (delivery modes); the agent selects one via
 * {@code groups[].selected_option_id}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpFulfillmentMethod
{
	public static final String TYPE_SHIPPING = "shipping";

	@JsonProperty("id")
	private String id;

	@JsonProperty("type")
	private String type;

	@JsonProperty("line_item_ids")
	private List<String> lineItemIds;

	@JsonProperty("destinations")
	private List<UcpShippingDestination> destinations;

	@JsonProperty("selected_destination_id")
	private String selectedDestinationId;

	@JsonProperty("groups")
	private List<UcpFulfillmentGroup> groups;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getType()
	{
		return type;
	}

	public void setType(final String type)
	{
		this.type = type;
	}

	public List<String> getLineItemIds()
	{
		return lineItemIds;
	}

	public void setLineItemIds(final List<String> lineItemIds)
	{
		this.lineItemIds = lineItemIds;
	}

	public List<UcpShippingDestination> getDestinations()
	{
		return destinations;
	}

	public void setDestinations(final List<UcpShippingDestination> destinations)
	{
		this.destinations = destinations;
	}

	public String getSelectedDestinationId()
	{
		return selectedDestinationId;
	}

	public void setSelectedDestinationId(final String selectedDestinationId)
	{
		this.selectedDestinationId = selectedDestinationId;
	}

	public List<UcpFulfillmentGroup> getGroups()
	{
		return groups;
	}

	public void setGroups(final List<UcpFulfillmentGroup> groups)
	{
		this.groups = groups;
	}
}
