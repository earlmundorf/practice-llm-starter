package com.coremcp.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.dto.McpSession;
import com.coremcp.model.McpSessionEntryModel;
import com.coremcp.services.McpSessionService;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * DB-backed MCP session store ({@link McpSessionEntryModel} items). Cluster-safe:
 * any CCv2 node can serve any request in a conversation, and sessions survive
 * rolling deployments. MCP/LLM agent clients identify their session via the
 * MCP-Session-Id header (not cookies), so CCv2 ingress stickiness cannot be
 * relied on — this store is the default in multi-node deployments
 * (coremcp.session.store=persistent).
 *
 * Expired sessions are removed lazily on access; bulk cleanup of abandoned
 * sessions is handled by mcpSessionCleanupCronJob (see McpSessionCleanupJob).
 */
public class PersistedMcpSessionService implements McpSessionService
{
	private static final Logger LOG = LoggerFactory.getLogger(PersistedMcpSessionService.class);

	private static final String QUERY_BY_SESSION_ID =
		"SELECT {pk} FROM {McpSessionEntry} WHERE {sessionId} = ?sessionId";

	private final ObjectMapper objectMapper = new ObjectMapper();

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private int ttlMinutes = 30;

	@Override
	public String createSession(final Map<String, Object> clientInfo, final String protocolVersion)
	{
		final String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		final McpSessionEntryModel entry = modelService.create(McpSessionEntryModel.class);
		entry.setSessionId(sessionId);
		entry.setProtocolVersion(protocolVersion);
		entry.setClientInfoJson(toJson(clientInfo));
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
		return sessionId;
	}

	@Override
	public McpSession getSession(final String sessionId)
	{
		final McpSessionEntryModel entry = findEntry(sessionId);
		if (entry == null)
		{
			return null;
		}
		if (isExpired(entry))
		{
			modelService.remove(entry);
			return null;
		}
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
		return toDto(entry);
	}

	@Override
	public void removeSession(final String sessionId)
	{
		final McpSessionEntryModel entry = findEntry(sessionId);
		if (entry != null)
		{
			modelService.remove(entry);
		}
	}

	@Override
	public void updateCartCode(final String sessionId, final String cartCode)
	{
		final McpSessionEntryModel entry = findEntry(sessionId);
		if (entry == null || isExpired(entry))
		{
			return;
		}
		entry.setCartCode(cartCode);
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
	}

	private McpSessionEntryModel findEntry(final String sessionId)
	{
		if (sessionId == null)
		{
			return null;
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(QUERY_BY_SESSION_ID);
		query.addQueryParameter("sessionId", sessionId);
		final SearchResult<McpSessionEntryModel> result = flexibleSearchService.search(query);
		return result.getResult().isEmpty() ? null : result.getResult().get(0);
	}

	private boolean isExpired(final McpSessionEntryModel entry)
	{
		final Date lastAccessed = entry.getLastAccessedAt();
		if (lastAccessed == null)
		{
			return true;
		}
		return lastAccessed.toInstant().plus(Duration.ofMinutes(ttlMinutes)).isBefore(Instant.now());
	}

	private McpSession toDto(final McpSessionEntryModel entry)
	{
		final McpSession session = new McpSession();
		session.setId(entry.getSessionId());
		session.setProtocolVersion(entry.getProtocolVersion());
		session.setClientInfo(fromJson(entry.getClientInfoJson()));
		session.setCartCode(entry.getCartCode());
		if (entry.getCreationtime() != null)
		{
			session.setCreatedAt(entry.getCreationtime().toInstant());
		}
		if (entry.getLastAccessedAt() != null)
		{
			session.setLastAccessedAt(entry.getLastAccessedAt().toInstant());
		}
		return session;
	}

	private String toJson(final Map<String, Object> clientInfo)
	{
		try
		{
			return objectMapper.writeValueAsString(clientInfo == null ? Map.of() : clientInfo);
		}
		catch (final Exception e)
		{
			LOG.warn("Could not serialize MCP clientInfo, storing empty object: {}", e.getMessage());
			return "{}";
		}
	}

	private Map<String, Object> fromJson(final String json)
	{
		if (json == null || json.isBlank())
		{
			return Map.of();
		}
		try
		{
			return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		}
		catch (final Exception e)
		{
			LOG.warn("Could not parse stored MCP clientInfo, returning empty object: {}", e.getMessage());
			return Map.of();
		}
	}

	public void setTtlMinutes(final int ttlMinutes)
	{
		this.ttlMinutes = ttlMinutes;
	}

	@Required
	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	@Required
	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
