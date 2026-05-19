package com.coremcp.dto;

import java.time.Instant;
import java.util.Map;

public class McpSession
{
	private String id;
	private Map<String, Object> clientInfo;
	private String protocolVersion;
	private Instant createdAt;
	private Instant lastAccessedAt;
	private String cartCode;

	public McpSession()
	{
	}

	public McpSession(final String id, final Map<String, Object> clientInfo, final String protocolVersion)
	{
		this.id = id;
		this.clientInfo = clientInfo;
		this.protocolVersion = protocolVersion;
		this.createdAt = Instant.now();
		this.lastAccessedAt = Instant.now();
	}

	public void touch()
	{
		this.lastAccessedAt = Instant.now();
	}

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public Map<String, Object> getClientInfo()
	{
		return clientInfo;
	}

	public void setClientInfo(final Map<String, Object> clientInfo)
	{
		this.clientInfo = clientInfo;
	}

	public String getProtocolVersion()
	{
		return protocolVersion;
	}

	public void setProtocolVersion(final String protocolVersion)
	{
		this.protocolVersion = protocolVersion;
	}

	public Instant getCreatedAt()
	{
		return createdAt;
	}

	public void setCreatedAt(final Instant createdAt)
	{
		this.createdAt = createdAt;
	}

	public Instant getLastAccessedAt()
	{
		return lastAccessedAt;
	}

	public void setLastAccessedAt(final Instant lastAccessedAt)
	{
		this.lastAccessedAt = lastAccessedAt;
	}

	public String getCartCode()
	{
		return cartCode;
	}

	public void setCartCode(final String cartCode)
	{
		this.cartCode = cartCode;
	}
}
