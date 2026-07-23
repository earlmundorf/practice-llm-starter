package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The public UCP discovery document served at
 * {@code GET /occ/v2/{baseSiteId}/.well-known/ucp}.
 *
 * Shape follows the task runbook's discovery-manifest contract (§2.1): a
 * {@code ucp} version block plus top-level {@code capabilities} /
 * {@code services} / {@code payment_handlers}. All three collections are
 * always serialized (empty until the corresponding phase lands) — the profile
 * only ever advertises what actually works.
 */
public class UcpProfile
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("capabilities")
	private List<UcpCapability> capabilities = new ArrayList<>();

	@JsonProperty("services")
	private Map<String, UcpServiceEntry> services = new LinkedHashMap<>();

	@JsonProperty("payment_handlers")
	private List<UcpPaymentHandler> paymentHandlers = new ArrayList<>();

	public UcpEnvelope getUcp()
	{
		return ucp;
	}

	public void setUcp(final UcpEnvelope ucp)
	{
		this.ucp = ucp;
	}

	public List<UcpCapability> getCapabilities()
	{
		return capabilities;
	}

	public void setCapabilities(final List<UcpCapability> capabilities)
	{
		this.capabilities = capabilities;
	}

	public Map<String, UcpServiceEntry> getServices()
	{
		return services;
	}

	public void setServices(final Map<String, UcpServiceEntry> services)
	{
		this.services = services;
	}

	public List<UcpPaymentHandler> getPaymentHandlers()
	{
		return paymentHandlers;
	}

	public void setPaymentHandlers(final List<UcpPaymentHandler> paymentHandlers)
	{
		this.paymentHandlers = paymentHandlers;
	}
}
