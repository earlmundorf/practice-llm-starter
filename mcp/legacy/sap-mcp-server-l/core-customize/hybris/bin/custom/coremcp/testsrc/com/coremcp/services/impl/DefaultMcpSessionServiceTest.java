package com.coremcp.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.coremcp.dto.McpSession;

import de.hybris.bootstrap.annotations.UnitTest;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;


@UnitTest
public class DefaultMcpSessionServiceTest
{
	private DefaultMcpSessionService sessionService;

	@Before
	public void setUp()
	{
		sessionService = new DefaultMcpSessionService();
	}

	@Test
	public void testCreateSessionReturnsSessionId()
	{
		final String sessionId = sessionService.createSession(Map.of("name", "test-client"), "2025-11-25");

		assertNotNull(sessionId);
		assertTrue(sessionId.startsWith("sess_"));
	}

	@Test
	public void testGetSessionReturnsCreatedSession()
	{
		final String sessionId = sessionService.createSession(Map.of(), "2025-11-25");

		final McpSession session = sessionService.getSession(sessionId);

		assertNotNull(session);
		assertEquals(sessionId, session.getId());
		assertEquals("2025-11-25", session.getProtocolVersion());
	}

	@Test
	public void testGetSessionReturnsNullForUnknownId()
	{
		assertNull(sessionService.getSession("sess_unknown"));
	}

	@Test
	public void testGetSessionReturnsNullForNullId()
	{
		assertNull(sessionService.getSession(null));
	}

	@Test
	public void testRemoveSessionDeletesSession()
	{
		final String sessionId = sessionService.createSession(Map.of(), "2025-11-25");

		sessionService.removeSession(sessionId);

		assertNull(sessionService.getSession(sessionId));
	}

	@Test
	public void testRemoveSessionWithNullDoesNotThrow()
	{
		sessionService.removeSession(null);
	}

	@Test
	public void testUpdateCartCodePersistsOnSession()
	{
		final String sessionId = sessionService.createSession(Map.of(), "2025-11-25");

		sessionService.updateCartCode(sessionId, "CART-001");

		assertEquals("CART-001", sessionService.getSession(sessionId).getCartCode());
	}

	@Test
	public void testUpdateCartCodeForUnknownSessionIsNoOp()
	{
		sessionService.updateCartCode("sess_unknown", "CART-001");
	}
}
