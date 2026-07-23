package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * JSON-RPC 2.0 response envelope for the UCP MCP binding (own copy — see
 * {@link JsonRpcRequest}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse
{
	@JsonProperty("jsonrpc")
	private final String jsonrpc = "2.0";

	@JsonProperty("id")
	private Object id;

	@JsonProperty("result")
	private Object result;

	@JsonProperty("error")
	private JsonRpcError error;

	private JsonRpcResponse()
	{
	}

	public static JsonRpcResponse success(final Object id, final Object result)
	{
		final JsonRpcResponse response = new JsonRpcResponse();
		response.id = id;
		response.result = result;
		return response;
	}

	public static JsonRpcResponse error(final Object id, final int code, final String message)
	{
		final JsonRpcResponse response = new JsonRpcResponse();
		response.id = id;
		response.error = new JsonRpcError(code, message);
		return response;
	}

	/**
	 * MCP tool result: the (already-serialized) UCP payload as text content.
	 * UCP business errors travel <em>inside</em> the payload
	 * ({@code ucp.status="error"} + {@code messages[]}); {@code isError} is
	 * reserved for unexpected server-side failures.
	 */
	public static JsonRpcResponse toolResult(final Object id, final String content, final boolean isError)
	{
		final JsonRpcResponse response = new JsonRpcResponse();
		response.id = id;
		if (isError)
		{
			response.result = Map.of(
				"content", List.of(Map.of("type", "text", "text", content)),
				"isError", true
			);
		}
		else
		{
			response.result = Map.of(
				"content", List.of(Map.of("type", "text", "text", content))
			);
		}
		return response;
	}

	public String getJsonrpc()
	{
		return jsonrpc;
	}

	public Object getId()
	{
		return id;
	}

	public Object getResult()
	{
		return result;
	}

	public JsonRpcError getError()
	{
		return error;
	}
}
