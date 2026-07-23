package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The UCP checkout {@code fulfillment} block. Symmetric on request and
 * response so an agent can echo back what it received:
 *
 * <pre>
 * "fulfillment": {
 *   "destination":   { ...address fields... },
 *   "delivery_mode": "thinkshop-standard",      // mode code; auto-selected when omitted
 *   "delivery_mode_name": "Standard Delivery"   // response-only convenience
 * }
 * </pre>
 *
 * On update, applying a destination (and a delivery mode, explicit or
 * auto-selected) is what moves the derived checkout status from
 * {@code incomplete} to {@code ready_for_complete} (design diagram S5).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpFulfillment
{
	@JsonProperty("destination")
	private UcpDestination destination;

	@JsonProperty("delivery_mode")
	private String deliveryMode;

	@JsonProperty("delivery_mode_name")
	private String deliveryModeName;

	public UcpDestination getDestination()
	{
		return destination;
	}

	public void setDestination(final UcpDestination destination)
	{
		this.destination = destination;
	}

	public String getDeliveryMode()
	{
		return deliveryMode;
	}

	public void setDeliveryMode(final String deliveryMode)
	{
		this.deliveryMode = deliveryMode;
	}

	public String getDeliveryModeName()
	{
		return deliveryModeName;
	}

	public void setDeliveryModeName(final String deliveryModeName)
	{
		this.deliveryModeName = deliveryModeName;
	}
}
