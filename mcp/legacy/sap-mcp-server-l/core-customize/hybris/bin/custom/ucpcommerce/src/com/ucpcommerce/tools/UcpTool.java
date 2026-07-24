package com.ucpcommerce.tools;

import java.util.Map;

/**
 * One UCP tool on the MCP binding (names per the UCP MCP capability bindings,
 * e.g. {@code search_catalog}, {@code create_checkout}).
 *
 * Thin adapters over the binding-agnostic capability services (design R12):
 * a tool unwraps {@code tools/call} arguments, calls exactly one service
 * operation, and serializes the returned UCP payload DTO. UCP business errors
 * live inside that payload ({@code ucp.status="error"} + {@code messages[]});
 * thrown exceptions are converted by the dispatcher into MCP
 * {@code isError} tool results (unexpected failures only).
 */
public interface UcpTool
{
	String getName();

	String getDescription();

	Map<String, Object> getInputSchema();

	/**
	 * Execute the tool and return the serialized UCP JSON payload.
	 *
	 * @param args    the {@code tools/call} arguments (never null)
	 * @param context per-call UCP metadata (agent profile, idempotency key)
	 */
	String execute(Map<String, Object> args, UcpToolContext context) throws Exception;

	default Map<String, Object> getDefinition()
	{
		return Map.of(
			"name", getName(),
			"description", getDescription(),
			"inputSchema", getInputSchema()
		);
	}
}
