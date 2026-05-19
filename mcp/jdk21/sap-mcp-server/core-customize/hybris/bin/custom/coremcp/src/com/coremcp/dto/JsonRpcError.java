package com.coremcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonRpcError
{
	@JsonProperty("code")
	private int code;

	@JsonProperty("message")
	private String message;

	public JsonRpcError()
	{
	}

	public JsonRpcError(final int code, final String message)
	{
		this.code = code;
		this.message = message;
	}

	// Standard JSON-RPC error codes
	public static final int PARSE_ERROR = -32700;
	public static final int INVALID_REQUEST = -32600;
	public static final int METHOD_NOT_FOUND = -32601;
	public static final int INVALID_PARAMS = -32602;
	public static final int INTERNAL_ERROR = -32603;

	public int getCode()
	{
		return code;
	}

	public void setCode(final int code)
	{
		this.code = code;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(final String message)
	{
		this.message = message;
	}
}
