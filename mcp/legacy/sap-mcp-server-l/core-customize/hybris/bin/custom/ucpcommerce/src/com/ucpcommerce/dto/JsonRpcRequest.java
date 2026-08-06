package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * JSON-RPC 2.0 request envelope for the UCP MCP binding.
 *
 * Deliberately a copy of coremcp's equivalent rather than an import — the two
 * MCP dialects (proprietary vs UCP) stay decoupled (design R2), so neither can
 * accidentally change the other's wire shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonRpcRequest
{
	@JsonProperty("jsonrpc")
	private String jsonrpc;

	@JsonProperty("id")
	private Object id;

	@JsonProperty("method")
	private String method;

	@JsonProperty("params")
	private Map<String, Object> params;

	public String getJsonrpc()
	{
		return jsonrpc;
	}

	public void setJsonrpc(final String jsonrpc)
	{
		this.jsonrpc = jsonrpc;
	}

	public Object getId()
	{
		return id;
	}

	public void setId(final Object id)
	{
		this.id = id;
	}

	public String getMethod()
	{
		return method;
	}

	public void setMethod(final String method)
	{
		this.method = method;
	}

	public Map<String, Object> getParams()
	{
		return params;
	}

	public void setParams(final Map<String, Object> params)
	{
		this.params = params;
	}

	public boolean isNotification()
	{
		return id == null;
	}
}
