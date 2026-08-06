package com.ucpcommerce.services;

import com.ucpcommerce.dto.JsonRpcRequest;
import com.ucpcommerce.dto.JsonRpcResponse;

/**
 * Routes JSON-RPC requests on the UCP MCP binding. Stateless by design: the
 * UCP MCP binding carries full context on every call ({@code meta["ucp-agent"]},
 * {@code meta["idempotency-key"]}) — there is no session, no session header,
 * and no cart preload (contrast with coremcp's {@code McpDispatcherService}).
 */
public interface UcpMcpDispatcherService
{
	/**
	 * Dispatch one JSON-RPC request.
	 *
	 * @return the response, or null for notifications (caller answers 202)
	 */
	JsonRpcResponse dispatch(JsonRpcRequest request);
}
