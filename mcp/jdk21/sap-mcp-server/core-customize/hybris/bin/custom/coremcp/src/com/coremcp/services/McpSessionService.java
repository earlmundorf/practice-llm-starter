package com.coremcp.services;

import com.coremcp.dto.McpSession;

import java.util.Map;

public interface McpSessionService
{
	String createSession(Map<String, Object> clientInfo, String protocolVersion);

	McpSession getSession(String sessionId);

	void removeSession(String sessionId);
}
