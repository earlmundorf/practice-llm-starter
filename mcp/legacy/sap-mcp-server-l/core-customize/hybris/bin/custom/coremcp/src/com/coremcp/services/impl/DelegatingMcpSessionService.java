package com.coremcp.services.impl;

import com.coremcp.dto.McpSession;
import com.coremcp.services.McpSessionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;

import java.util.Map;

/**
 * Selects the MCP session backing store at boot from the coremcp.session.store
 * property: "persistent" (default — DB-backed, cluster-safe) or "memory"
 * (in-process, single-node; useful for local dev before the first yupdatesystem
 * and for tests).
 */
public class DelegatingMcpSessionService implements McpSessionService
{
	private static final Logger LOG = LoggerFactory.getLogger(DelegatingMcpSessionService.class);

	private String store = "persistent";
	private McpSessionService memoryStore;
	private McpSessionService persistentStore;

	private McpSessionService delegate;

	@PostConstruct
	public void init()
	{
		delegate = "memory".equalsIgnoreCase(store) ? memoryStore : persistentStore;
		LOG.info("MCP session store: {}", "memory".equalsIgnoreCase(store) ? "memory (single-node)" : "persistent (cluster-safe)");
	}

	@Override
	public String createSession(final Map<String, Object> clientInfo, final String protocolVersion)
	{
		return delegate.createSession(clientInfo, protocolVersion);
	}

	@Override
	public McpSession getSession(final String sessionId)
	{
		return delegate.getSession(sessionId);
	}

	@Override
	public void removeSession(final String sessionId)
	{
		delegate.removeSession(sessionId);
	}

	@Override
	public void updateCartCode(final String sessionId, final String cartCode)
	{
		delegate.updateCartCode(sessionId, cartCode);
	}

	public void setStore(final String store)
	{
		this.store = store;
	}

	@Required
	public void setMemoryStore(final McpSessionService memoryStore)
	{
		this.memoryStore = memoryStore;
	}

	@Required
	public void setPersistentStore(final McpSessionService persistentStore)
	{
		this.persistentStore = persistentStore;
	}
}
