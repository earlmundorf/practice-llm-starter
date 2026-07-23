package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One transport entry inside a {@code services} block — the base
 * {@code endpoint} the client prefixes to resource paths (REST) or POSTs
 * JSON-RPC to (MCP), plus an optional {@code schema} URL (e.g. the hosted
 * OpenRPC document for the MCP binding).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpTransportEndpoint
{
	@JsonProperty("endpoint")
	private String endpoint;

	@JsonProperty("schema")
	private String schema;

	public UcpTransportEndpoint()
	{
		// for Jackson
	}

	public UcpTransportEndpoint(final String endpoint)
	{
		this.endpoint = endpoint;
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
