package com.coremcp.services.impl;

import com.coremcp.dto.McpSession;
import com.coremcp.services.McpSessionService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultMcpSessionService implements McpSessionService
{
	private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

	@Override
	public String createSession(final Map<String, Object> clientInfo, final String protocolVersion)
	{
		final String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		final McpSession session = new McpSession(sessionId, clientInfo, protocolVersion);
		sessions.put(sessionId, session);
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
		if (session != null)
		{
			session.touch();
		}
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
}
