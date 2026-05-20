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
 * Single-node only — multi-node CCv2 deployments need either sticky sessions
 * at the load balancer or a persistent session store (TODO in coremcp-items.xml).
 */
public class DefaultMcpSessionService implements McpSessionService
{
	private static final Duration SESSION_TTL = Duration.ofMinutes(30);
	private static final int SWEEP_THRESHOLD = 10_000;

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

	private boolean isExpired(final McpSession session)
	{
		return session.getLastAccessedAt().plus(SESSION_TTL).isBefore(Instant.now());
	}

	private void sweepExpired()
	{
		final Instant cutoff = Instant.now().minus(SESSION_TTL);
		sessions.values().removeIf(s -> s.getLastAccessedAt().isBefore(cutoff));
	}
}
