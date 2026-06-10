package com.coremcp.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.coremcp.dto.McpSession;

import de.hybris.bootstrap.annotations.IntegrationTest;
import de.hybris.platform.servicelayer.ServicelayerTransactionalTest;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.Map;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;


/**
 * Verifies the DB-backed MCP session store is node-independent: a session created
 * through one service instance is fully visible through another (simulating a second
 * CCv2 node), and cart-code updates + TTL expiry are persisted.
 */
@IntegrationTest
public class PersistedMcpSessionServiceIntegrationTest extends ServicelayerTransactionalTest
{
	@Resource
	private ModelService modelService;

	@Resource
	private FlexibleSearchService flexibleSearchService;

	private PersistedMcpSessionService service;

	@Before
	public void setUp()
	{
		service = newService(30);
	}

	private PersistedMcpSessionService newService(final int ttlMinutes)
	{
		final PersistedMcpSessionService s = new PersistedMcpSessionService();
		s.setModelService(modelService);
		s.setFlexibleSearchService(flexibleSearchService);
		s.setTtlMinutes(ttlMinutes);
		return s;
	}

	@Test
	public void sessionSurvivesAcrossServiceInstances()
	{
		final String sessionId = service.createSession(Map.of("name", "test-client"), "2025-11-25");

		// A fresh instance simulates the request landing on a different node.
		final McpSession session = newService(30).getSession(sessionId);

		assertNotNull(session);
		assertEquals(sessionId, session.getId());
		assertEquals("2025-11-25", session.getProtocolVersion());
		assertEquals("test-client", session.getClientInfo().get("name"));
	}

	@Test
	public void cartCodeUpdateIsPersisted()
	{
		final String sessionId = service.createSession(Map.of(), "2025-11-25");

		service.updateCartCode(sessionId, "CART-001");

		final McpSession session = newService(30).getSession(sessionId);
		assertNotNull(session);
		assertEquals("CART-001", session.getCartCode());
	}

	@Test
	public void expiredSessionIsRemovedOnAccess() throws Exception
	{
		final String sessionId = service.createSession(Map.of(), "2025-11-25");

		final PersistedMcpSessionService zeroTtl = newService(0);
		Thread.sleep(5); // ensure lastAccessedAt is strictly in the past

		assertNull(zeroTtl.getSession(sessionId));
		// Lazy eviction removed the row — gone for non-expired readers too.
		assertNull(service.getSession(sessionId));
	}

	@Test
	public void removeSessionDeletesRow()
	{
		final String sessionId = service.createSession(Map.of(), "2025-11-25");

		service.removeSession(sessionId);

		assertNull(service.getSession(sessionId));
	}

	@Test
	public void getSessionReturnsNullForUnknownOrNullId()
	{
		assertNull(service.getSession("sess_doesnotexist"));
		assertNull(service.getSession(null));
	}

	@Test
	public void updateCartCodeForUnknownSessionIsNoOp()
	{
		service.updateCartCode("sess_doesnotexist", "CART-001");
	}
}
