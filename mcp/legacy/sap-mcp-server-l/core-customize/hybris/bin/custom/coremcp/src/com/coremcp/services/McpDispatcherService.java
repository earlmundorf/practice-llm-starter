package com.coremcp.services;

import com.coremcp.dto.JsonRpcRequest;
import com.coremcp.dto.JsonRpcResponse;
import com.coremcp.dto.McpSession;

public interface McpDispatcherService
{
	/**
	 * Dispatch a JSON-RPC request to the appropriate handler.
	 *
	 * @param request The parsed JSON-RPC request
	 * @param session The MCP session (may be null for initialize)
	 * @return The JSON-RPC response, or null for notifications
	 */
	JsonRpcResponse dispatch(JsonRpcRequest request, McpSession session);

	/**
	 * Handle the initialize request (creates a new session).
	 *
	 * @param request The initialize request
	 * @return The initialize response with session ID set as a side effect
	 */
	InitializeResult handleInitialize(JsonRpcRequest request);

	class InitializeResult
	{
		private final JsonRpcResponse response;
		private final String sessionId;

		public InitializeResult(final JsonRpcResponse response, final String sessionId)
		{
			this.response = response;
			this.sessionId = sessionId;
		}

		public JsonRpcResponse getResponse()
		{
			return response;
		}

		public String getSessionId()
		{
			return sessionId;
		}
	}
}
