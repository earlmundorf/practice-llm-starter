package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpCheckoutSession;
import com.ucpcommerce.enums.UcpCheckoutStatus;
import com.ucpcommerce.jobs.UcpCheckoutSessionCleanupJob;
import com.ucpcommerce.model.UcpCheckoutSessionEntryModel;

import de.hybris.bootstrap.annotations.IntegrationTest;
import de.hybris.platform.servicelayer.ServicelayerTransactionalTest;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;


/**
 * Verifies the DB-backed UCP checkout-session store is node-independent (an
 * entry created through one service instance is fully visible through another,
 * simulating a second CCv2 node), that protocol state round-trips, that TTL
 * expiry evicts lazily, and that the cleanup job sweeps abandoned rows —
 * mirror of coremcp's PersistedMcpSessionServiceIntegrationTest.
 */
@IntegrationTest
public class PersistedUcpCheckoutSessionServiceIntegrationTest extends ServicelayerTransactionalTest
{
	@Resource
	private ModelService modelService;

	@Resource
	private FlexibleSearchService flexibleSearchService;

	private PersistedUcpCheckoutSessionService service;

	@Before
	public void setUp()
	{
		service = newService(30);
	}

	private PersistedUcpCheckoutSessionService newService(final int ttlMinutes)
	{
		final PersistedUcpCheckoutSessionService s = new PersistedUcpCheckoutSessionService();
		s.setModelService(modelService);
		s.setFlexibleSearchService(flexibleSearchService);
		s.setTtlMinutes(ttlMinutes);
		return s;
	}

	@Test
	public void createdSessionIsVisibleAcrossServiceInstances()
	{
		final UcpCheckoutSession created = service.create("CART-001", UcpCheckout.STATUS_INCOMPLETE,
			"{\"email\":\"john.doe@thinkshop.com\"}");

		assertNotNull(created);
		assertTrue("opaque id must carry the ucp_chk_ prefix", created.getCheckoutId().startsWith("ucp_chk_"));

		// A fresh instance simulates the request landing on a different node.
		final UcpCheckoutSession session = newService(30).get(created.getCheckoutId());

		assertNotNull(session);
		assertEquals(created.getCheckoutId(), session.getCheckoutId());
		assertEquals("CART-001", session.getCartCode());
		assertEquals("incomplete", session.getStatus());
		assertTrue(session.getBuyerJson().contains("john.doe@thinkshop.com"));
	}

	@Test
	public void updatePersistsCartCodeAndStatus()
	{
		final String checkoutId = service.create("CART-001", UcpCheckout.STATUS_INCOMPLETE, null).getCheckoutId();

		service.update(checkoutId, "CART-002", UcpCheckout.STATUS_READY_FOR_COMPLETE);

		final UcpCheckoutSession session = newService(30).get(checkoutId);
		assertNotNull(session);
		assertEquals("CART-002", session.getCartCode());
		assertEquals("ready_for_complete", session.getStatus());
	}

	@Test
	public void expiredSessionIsRemovedOnAccess() throws Exception
	{
		final String checkoutId = service.create("CART-001", UcpCheckout.STATUS_INCOMPLETE, null).getCheckoutId();

		final PersistedUcpCheckoutSessionService zeroTtl = newService(0);
		Thread.sleep(5); // ensure lastAccessedAt is strictly in the past

		assertNull(zeroTtl.get(checkoutId));
		// Lazy eviction removed the row — gone for non-expired readers too.
		assertNull(service.get(checkoutId));
	}

	@Test
	public void getReturnsNullForUnknownOrNullId()
	{
		assertNull(service.get("ucp_chk_doesnotexist"));
		assertNull(service.get(null));
	}

	@Test
	public void findCheckoutIdForOrderRecoversProvenanceAfterCompletion()
	{
		// order.json's checkout_id: a completed session is findable by the
		// order code it recorded, from any node, without touching its TTL.
		final String checkoutId = service.create("CART-001", UcpCheckout.STATUS_INCOMPLETE, null).getCheckoutId();
		service.beginCompletion(checkoutId, "idem-key-1");
		service.recordCompletion(checkoutId, "{\"id\":\"" + checkoutId + "\"}", "ORDER-42");

		final PersistedUcpCheckoutSessionService secondNode = newService(30);
		assertEquals(checkoutId, secondNode.findCheckoutIdForOrder("ORDER-42"));
		assertNull(secondNode.findCheckoutIdForOrder("ORDER-UNKNOWN"));
		assertNull(secondNode.findCheckoutIdForOrder(null));
	}

	@Test
	public void updateForUnknownSessionIsNoOp()
	{
		service.update("ucp_chk_doesnotexist", "CART-001", UcpCheckout.STATUS_INCOMPLETE);
	}

	@Test
	public void cleanupJobSweepsAbandonedExpiredEntriesOnly()
	{
		// A stale entry the lazy path never touched again (created directly so
		// no service call refreshes lastAccessedAt) — 60 min > the 30 min TTL.
		final UcpCheckoutSessionEntryModel stale = modelService.create(UcpCheckoutSessionEntryModel.class);
		stale.setCheckoutId("ucp_chk_stale00000001");
		stale.setCartCode("CART-OLD");
		stale.setStatus(UcpCheckoutStatus.INCOMPLETE);
		stale.setLastAccessedAt(Date.from(Instant.now().minus(Duration.ofMinutes(60))));
		modelService.save(stale);

		final String freshId = service.create("CART-001", UcpCheckout.STATUS_INCOMPLETE, null).getCheckoutId();

		final UcpCheckoutSessionCleanupJob job = new UcpCheckoutSessionCleanupJob();
		job.setModelService(modelService);
		job.setFlexibleSearchService(flexibleSearchService);
		job.perform(null);

		final FlexibleSearchQuery query = new FlexibleSearchQuery(
			"SELECT {pk} FROM {UcpCheckoutSessionEntry} WHERE {checkoutId} = ?checkoutId");
		query.addQueryParameter("checkoutId", "ucp_chk_stale00000001");
		assertTrue("stale entry swept by the cleanup job",
			flexibleSearchService.<UcpCheckoutSessionEntryModel> search(query).getResult().isEmpty());

		assertNotNull("fresh entry survives the sweep", service.get(freshId));
	}
}
