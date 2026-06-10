package com.coremcp.services.impl;

import com.coremcp.dto.McpSession;
import com.coremcp.services.McpSessionService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory MCP session store with lazy TTL eviction.
 *
 * Sessions older than {@link #SESSION_TTL} (measured from last access) are
 * removed on the next {@link #getSession} call, and a full sweep runs when
 * the map size crosses {@link #SWEEP_THRESHOLD} on a new session creation.
 * No background threads, no external dependencies.
 *
 * Single-node only — multi-node CCv2 deployments use {@link PersistedMcpSessionService}
 * instead (selected via the coremcp.session.store property).
 */
public class DefaultMcpSessionService implements McpSessionService
{
	private static final int SWEEP_THRESHOLD = 10_000;

	private int ttlMinutes = 30;

	private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

	@Override
	public String createSession(final Map<String, Object> clientInfo, final String protocolVersion)
	{
		if (sessions.size() >= SWEEP_THRESHOLD)
		{
			sweepExpired();
		}
		final String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		sessions.put(sessionId, new McpSession(sessionId, clientInfo, protocolVersion));
		return sessionId;
	}

	@Override
	public McpSession getSession(final String sessionId)
	{
		if (sessionId == null)
		{
			return null;
		}
		final McpSession session = sessions.get(sessionId);
		if (session == null)
		{
			return null;
		}
		if (isExpired(session))
		{
			sessions.remove(sessionId);
			return null;
		}
		session.touch();
		return session;
	}

	@Override
	public void removeSession(final String sessionId)
	{
		if (sessionId != null)
		{
			sessions.remove(sessionId);
		}
	}

	@Override
	public void updateCartCode(final String sessionId, final String cartCode)
	{
		final McpSession session = getSession(sessionId);
		if (session != null)
		{
			session.setCartCode(cartCode);
		}
	}

	private boolean isExpired(final McpSession session)
	{
		return session.getLastAccessedAt().plus(sessionTtl()).isBefore(Instant.now());
	}

	private void sweepExpired()
	{
		final Instant cutoff = Instant.now().minus(sessionTtl());
		sessions.values().removeIf(s -> s.getLastAccessedAt().isBefore(cutoff));
	}

	private Duration sessionTtl()
	{
		return Duration.ofMinutes(ttlMinutes);
	}

	public void setTtlMinutes(final int ttlMinutes)
	{
		this.ttlMinutes = ttlMinutes;
	}
}
