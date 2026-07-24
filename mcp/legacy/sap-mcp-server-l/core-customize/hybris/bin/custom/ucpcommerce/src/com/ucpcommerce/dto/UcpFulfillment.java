package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The UCP checkout {@code fulfillment} block. Two layers coexist here:
 *
 * <ul>
 *   <li><strong>{@code methods[]}</strong> — the spec's fulfillment
 *       negotiation flow (python-sdk {@code Fulfillment}; ADR 0003): the
 *       agent triggers a method, the server offers {@code destinations[]},
 *       the agent selects one, the server offers {@code groups[].options[]},
 *       the agent selects an option. This is what the official reference
 *       client drives.</li>
 *   <li><strong>{@code destination}/{@code delivery_mode}</strong> — the
 *       pre-correction ThinkShop shorthand (an inline address + optional
 *       mode code), kept accepted on requests and echoed on responses for
 *       backward compatibility. The spec tolerates the extra fields
 *       ({@code extra=allow}).</li>
 * </ul>
 *
 * Either way, an applied destination plus a delivery mode (explicit or
 * auto-selected) is what moves the derived checkout status from
 * {@code incomplete} to {@code ready_for_complete} (design diagram S5).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpFulfillment
{
	/** Spec negotiation flow (ADR 0003). */
	@JsonProperty("methods")
	private java.util.List<UcpFulfillmentMethod> methods;

	@JsonProperty("destination")
	private UcpDestination destination;

	@JsonProperty("delivery_mode")
	private String deliveryMode;

	@JsonProperty("delivery_mode_name")
	private String deliveryModeName;

	public java.util.List<UcpFulfillmentMethod> getMethods()
	{
		return methods;
	}

	public void setMethods(final java.util.List<UcpFulfillmentMethod> methods)
	{
		this.methods = methods;
	}

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
