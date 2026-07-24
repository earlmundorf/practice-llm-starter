package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * The {@code ucp} metadata block that leads every UCP document — the profile
 * carries {@code version} only; capability responses (later phases) add
 * {@code status} ("success" / "error"). Checkout responses additionally carry
 * the {@code payment_handlers} registry (required by the official
 * {@code ucp.json#/$defs/response_checkout_schema}).
 *
 * Hand-written Jackson class, never a generated WsDTO (coremcp ADR 0005 idiom).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpEnvelope
{
	@JsonProperty("version")
	private String version;

	@JsonProperty("status")
	private String status;

	/** Registry keyed by reverse-domain namespace → version entries. */
	@JsonProperty("payment_handlers")
	private Map<String, List<UcpPaymentHandler>> paymentHandlers;

	public UcpEnvelope()
	{
		// for Jackson
	}

	public UcpEnvelope(final String version)
	{
		this.version = version;
	}

	public String getVersion()
	{
		return version;
	}

	public void setVersion(final String version)
	{
		this.version = version;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(final String status)
	{
		this.status = status;
	}

	public Map<String, List<UcpPaymentHandler>> getPaymentHandlers()
	{
		return paymentHandlers;
	}

	public void setPaymentHandlers(final Map<String, List<UcpPaymentHandler>> paymentHandlers)
	{
		this.paymentHandlers = paymentHandlers;
	}
}
