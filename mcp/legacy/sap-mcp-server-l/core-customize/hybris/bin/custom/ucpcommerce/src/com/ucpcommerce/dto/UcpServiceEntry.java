package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One value in the profile's {@code services} map (keyed by service name,
 * e.g. {@code dev.ucp.shopping}) — declares the transport endpoints a client
 * may use. The {@code mcp} entry is added in Phase 2, {@code rest} in Phase 7;
 * each transport is advertised only once it actually works.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpServiceEntry
{
	@JsonProperty("mcp")
	private UcpTransportEndpoint mcp;

	@JsonProperty("rest")
	private UcpTransportEndpoint rest;

	public UcpTransportEndpoint getMcp()
	{
		return mcp;
	}

	public void setMcp(final UcpTransportEndpoint mcp)
	{
		this.mcp = mcp;
	}

	public UcpTransportEndpoint getRest()
	{
		return rest;
	}

	public void setRest(final UcpTransportEndpoint rest)
	{
		this.rest = rest;
	}
}
