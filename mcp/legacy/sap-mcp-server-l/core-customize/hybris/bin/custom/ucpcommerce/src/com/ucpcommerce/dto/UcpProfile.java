package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The public UCP discovery document served at
 * {@code GET /occ/v2/{baseSiteId}/.well-known/ucp}.
 *
 * Shape verified against the official discovery schema
 * ({@code ucp/source/schemas/profile.json}: {@code $defs.base.required =
 * ["ucp"]}) and the reference sample server's live output: the whole profile
 * body lives INSIDE a top-level {@code ucp} object, and {@code services},
 * {@code capabilities} and {@code payment_handlers} are all registries —
 * maps keyed by reverse-domain name whose values are LISTS of version
 * entries. (The earlier top-level-arrays shape was a provisional judgment
 * call — see ADR 0003.)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpProfile
{
	@JsonProperty("ucp")
	private Body ucp = new Body();

	public Body getUcp()
	{
		return ucp;
	}

	public void setUcp(final Body ucp)
	{
		this.ucp = ucp;
	}

	/** The {@code ucp} object — the profile body (business schema). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class Body
	{
		@JsonProperty("version")
		private String version;

		/** Service registry keyed by reverse-domain name → transport entries. */
		@JsonProperty("services")
		private Map<String, List<UcpServiceEntry>> services = new LinkedHashMap<>();

		/** Capability registry keyed by reverse-domain name → version entries. */
		@JsonProperty("capabilities")
		private Map<String, List<UcpCapability>> capabilities = new LinkedHashMap<>();

		/** Payment-handler registry keyed by reverse-domain name → handler entries. */
		@JsonProperty("payment_handlers")
		private Map<String, List<UcpPaymentHandler>> paymentHandlers = new LinkedHashMap<>();

		public String getVersion()
		{
			return version;
		}

		public void setVersion(final String version)
		{
			this.version = version;
		}

		public Map<String, List<UcpServiceEntry>> getServices()
		{
			return services;
		}

		public void setServices(final Map<String, List<UcpServiceEntry>> services)
		{
			this.services = services;
		}

		public Map<String, List<UcpCapability>> getCapabilities()
		{
			return capabilities;
		}

		public void setCapabilities(final Map<String, List<UcpCapability>> capabilities)
		{
			this.capabilities = capabilities;
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
}
