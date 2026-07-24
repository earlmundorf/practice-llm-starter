package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One transport entry in the profile's {@code services} registry (the values
 * under a reverse-domain service name are a LIST of these, per the official
 * discovery schema / sample server — ADR 0003):
 *
 * <pre>
 * "services": {
 *   "dev.ucp.shopping": [
 *     { "version": "2026-04-08", "transport": "rest", "endpoint": "…/ucp" },
 *     { "version": "2026-04-08", "transport": "mcp",  "endpoint": "…/ucp/mcp" }
 *   ]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpServiceEntry
{
	@JsonProperty("version")
	private String version;

	@JsonProperty("spec")
	private String spec;

	@JsonProperty("transport")
	private String transport;

	@JsonProperty("endpoint")
	private String endpoint;

	@JsonProperty("schema")
	private String schema;

	public UcpServiceEntry()
	{
		// for Jackson
	}

	public UcpServiceEntry(final String version, final String transport, final String endpoint)
	{
		this.version = version;
		this.transport = transport;
		this.endpoint = endpoint;
	}

	public String getVersion()
	{
		return version;
	}

	public void setVersion(final String version)
	{
		this.version = version;
	}

	public String getSpec()
	{
		return spec;
	}

	public void setSpec(final String spec)
	{
		this.spec = spec;
	}

	public String getTransport()
	{
		return transport;
	}

	public void setTransport(final String transport)
	{
		this.transport = transport;
	}

	public String getEndpoint()
	{
		return endpoint;
	}

	public void setEndpoint(final String endpoint)
	{
		this.endpoint = endpoint;
	}

	public String getSchema()
	{
		return schema;
	}

	public void setSchema(final String schema)
	{
		this.schema = schema;
	}
}
