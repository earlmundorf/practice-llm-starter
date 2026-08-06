package com.ucpcommerce.tools;

import java.util.Map;

/**
 * Per-call UCP metadata for the stateless MCP binding. The UCP MCP binding
 * carries no transport session: the calling agent advertises its own profile
 * via {@code meta["ucp-agent"]} on every call, and state-changing operations
 * (complete/cancel, later phases) carry {@code meta["idempotency-key"]}.
 *
 * Both the UCP-style {@code params.meta} and the MCP-SDK-style
 * {@code params._meta} spellings are accepted.
 */
public final class UcpToolContext
{
	public static final UcpToolContext EMPTY = new UcpToolContext(null, null);

	private static final String META_KEY = "meta";
	private static final String SDK_META_KEY = "_meta";
	private static final String UCP_AGENT_KEY = "ucp-agent";
	private static final String IDEMPOTENCY_KEY = "idempotency-key";

	private final Map<String, Object> ucpAgent;
	private final String idempotencyKey;

	public UcpToolContext(final Map<String, Object> ucpAgent, final String idempotencyKey)
	{
		this.ucpAgent = ucpAgent;
		this.idempotencyKey = idempotencyKey;
	}

	@SuppressWarnings("unchecked")
	public static UcpToolContext fromParams(final Map<String, Object> params)
	{
		if (params == null)
		{
			return EMPTY;
		}
		Object meta = params.get(META_KEY);
		if (!(meta instanceof Map))
		{
			meta = params.get(SDK_META_KEY);
		}
		if (!(meta instanceof Map))
		{
			return EMPTY;
		}
		final Map<String, Object> metaMap = (Map<String, Object>) meta;
		final Object agent = metaMap.get(UCP_AGENT_KEY);
		final Object idempotencyKey = metaMap.get(IDEMPOTENCY_KEY);
		return new UcpToolContext(
			agent instanceof Map ? (Map<String, Object>) agent : null,
			idempotencyKey instanceof String ? (String) idempotencyKey : null);
	}

	/** The raw {@code meta["ucp-agent"]} object, or null when absent. */
	public Map<String, Object> getUcpAgent()
	{
		return ucpAgent;
	}

	/** The agent's advertised profile ({@code meta["ucp-agent"].profile}), or null. */
	public Object getAgentProfile()
	{
		return ucpAgent != null ? ucpAgent.get("profile") : null;
	}

	/** The {@code meta["idempotency-key"]} value, or null when absent. */
	public String getIdempotencyKey()
	{
		return idempotencyKey;
	}
}
