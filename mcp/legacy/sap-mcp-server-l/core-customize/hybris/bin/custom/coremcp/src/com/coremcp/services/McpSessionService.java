package com.coremcp.services;

import com.coremcp.dto.McpSession;

import java.util.Map;

public interface McpSessionService
{
	String createSession(Map<String, Object> clientInfo, String protocolVersion);

	McpSession getSession(String sessionId);

	void removeSession(String sessionId);

	/**
	 * Persist the cart code on an existing session. Callers must use this (rather than
	 * mutating the {@link McpSession} DTO returned by {@link #getSession}) so the change
	 * survives in store implementations that return detached copies (e.g. the DB-backed
	 * store). Unknown/expired session ids are a no-op.
	 */
	void updateCartCode(String sessionId, String cartCode);
}
