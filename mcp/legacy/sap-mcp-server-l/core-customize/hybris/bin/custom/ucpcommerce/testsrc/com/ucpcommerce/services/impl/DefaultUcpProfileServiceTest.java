package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpCapability;
import com.ucpcommerce.dto.UcpProfile;
import com.ucpcommerce.dto.UcpServiceEntry;

import de.hybris.bootstrap.annotations.UnitTest;

import org.junit.Before;
import org.junit.Test;

import java.util.List;


/**
 * Profile shape pinned against the OFFICIAL discovery schema and the sample
 * server's live output (ADR 0003): everything inside a top-level {@code ucp}
 * object; {@code services}/{@code capabilities}/{@code payment_handlers} are
 * registries — maps keyed by reverse-domain name with LIST values.
 */
@UnitTest
public class DefaultUcpProfileServiceTest
{
	private static final String PINNED_VERSION = "2026-04-08";
	private static final String PUBLIC_BASE_URL = "https://localhost:9002";

	private DefaultUcpProfileService profileService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp()
	{
		// Override the config seams so the test doesn't depend on platform Config state.
		profileService = new DefaultUcpProfileService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}

			@Override
			protected String getPublicBaseUrl()
			{
				return PUBLIC_BASE_URL;
			}
		};
	}

	@Test
	public void testProfileCarriesPinnedUcpVersion()
	{
		final UcpProfile profile = profileService.buildProfile("electronics");

		assertNotNull(profile.getUcp());
		assertEquals(PINNED_VERSION, profile.getUcp().getVersion());
	}

	@Test
	public void testProfileAdvertisesCatalogCapability()
	{
		final UcpCapability catalog = capability("dev.ucp.shopping.catalog");
		assertNotNull("catalog capability must be advertised", catalog);
		assertEquals("capability version is the pinned dated calver string",
			PINNED_VERSION, catalog.getVersion());
		assertNotNull(catalog.getSpec());
		assertNotNull(catalog.getSchema());
	}

	@Test
	public void testProfileAdvertisesCheckoutCapability()
	{
		// Exactly the capabilities that work end-to-end: catalog, checkout,
		// fulfillment + discount (extensions), order, promotions, knowledge.
		assertEquals(7, profileService.buildProfile("electronics").getUcp().getCapabilities().size());
		final UcpCapability checkout = capability("dev.ucp.shopping.checkout");
		assertNotNull("checkout capability must be advertised once the lifecycle works", checkout);
		assertEquals(PINNED_VERSION, checkout.getVersion());
		assertNotNull(checkout.getSpec());
		assertNotNull(checkout.getSchema());
	}

	@Test
	public void testProfileAdvertisesFulfillmentAsCheckoutExtension()
	{
		// The negotiation flow (methods → destinations → groups/options) is an
		// extension capability that composes onto checkout — declared with the
		// official "extends" pointer like the sample server does.
		final UcpCapability fulfillment = capability("dev.ucp.shopping.fulfillment");
		assertNotNull("fulfillment capability must be advertised", fulfillment);
		assertEquals("dev.ucp.shopping.checkout", fulfillment.getExtendsCapability());
	}

	@Test
	public void testProfileAdvertisesDiscountAsCheckoutExtension()
	{
		// Declarative discounts.codes + the official applied[] echo are
		// implemented, so the discount extension is honestly advertised.
		final UcpCapability discount = capability("dev.ucp.shopping.discount");
		assertNotNull("discount capability must be advertised", discount);
		assertEquals("dev.ucp.shopping.checkout", discount.getExtendsCapability());
		assertNotNull(discount.getSchema());
	}

	@Test
	public void testProfileAdvertisesOrderCapability()
	{
		final UcpCapability order = capability("dev.ucp.shopping.order");
		assertNotNull("order capability must be advertised once get/history work", order);
		assertEquals(PINNED_VERSION, order.getVersion());
		assertNotNull(order.getSpec());
		assertNotNull(order.getSchema());
	}

	@Test
	public void testProfileAdvertisesCustomThinkshopCapabilities()
	{
		// Custom reverse-domain capabilities (design R7): promotions + knowledge,
		// versioned like the standard set but with no hosted spec/schema URLs.
		final UcpCapability promotions = capability("com.thinkshop.promotions");
		assertNotNull("com.thinkshop.promotions must be advertised", promotions);
		assertEquals(PINNED_VERSION, promotions.getVersion());
		assertNull(promotions.getSpec());
		assertNull(promotions.getSchema());

		final UcpCapability knowledge = capability("com.thinkshop.knowledge");
		assertNotNull("com.thinkshop.knowledge must be advertised", knowledge);
		assertEquals(PINNED_VERSION, knowledge.getVersion());
		assertNull(knowledge.getSpec());
		assertNull(knowledge.getSchema());
	}

	private UcpCapability capability(final String name)
	{
		final List<UcpCapability> entries =
			profileService.buildProfile("electronics").getUcp().getCapabilities().get(name);
		return entries == null || entries.isEmpty() ? null : entries.get(0);
	}

	private UcpServiceEntry transport(final UcpProfile profile, final String transport)
	{
		final List<UcpServiceEntry> entries = profile.getUcp().getServices().get("dev.ucp.shopping");
		assertNotNull("dev.ucp.shopping service must be registered", entries);
		return entries.stream().filter(e -> transport.equals(e.getTransport())).findFirst().orElse(null);
	}

	@Test
	public void testProfileAdvertisesMcpTransportForBaseSite()
	{
		final UcpProfile profile = profileService.buildProfile("electronics");

		final UcpServiceEntry mcp = transport(profile, "mcp");
		assertNotNull("mcp transport must be advertised", mcp);
		assertEquals(PUBLIC_BASE_URL + "/occ/v2/electronics/ucp/mcp", mcp.getEndpoint());
		assertEquals(PINNED_VERSION, mcp.getVersion());
	}

	@Test
	public void testProfileAdvertisesRestTransportBase()
	{
		// The rest entry is the BASE the client prefixes to resource paths
		// (/checkout-sessions, /catalog/search, /orders — ADR 0002).
		final UcpProfile profile = profileService.buildProfile("electronics");

		final UcpServiceEntry rest = transport(profile, "rest");
		assertNotNull("rest transport must be advertised once the REST routes work", rest);
		assertEquals(PUBLIC_BASE_URL + "/occ/v2/electronics/ucp", rest.getEndpoint());
	}

	@Test
	public void testProfileDeclaresTheMockPaymentHandlerAndItsAlias()
	{
		// The honest mock handler (design R9) plus the ecosystem's well-known
		// mock_payment_handler id as an alias of the same demo mock, both
		// registered under one reverse-domain namespace with a LIST of
		// entries — the registry shape the reference client flattens.
		final UcpProfile profile = profileService.buildProfile("electronics");

		assertEquals(1, profile.getUcp().getPaymentHandlers().size());
		final List<com.ucpcommerce.dto.UcpPaymentHandler> handlers =
			profile.getUcp().getPaymentHandlers().get("com.thinkshop.mock_card");
		assertNotNull("mock handler registered under com.thinkshop.mock_card", handlers);
		assertEquals(2, handlers.size());
		assertEquals("thinkshop_mock_card", handlers.get(0).getId());
		assertEquals("mock_payment_handler", handlers.get(1).getId());
		assertNotNull("handler declares a human-readable name", handlers.get(0).getName());
	}

	@Test
	public void testPublicBaseUrlTrailingSlashIsStripped()
	{
		final DefaultUcpProfileService slashService = new DefaultUcpProfileService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}

			@Override
			protected String getPublicBaseUrl()
			{
				return "https://store.example.com/";
			}
		};
		assertEquals("https://store.example.com/occ/v2/electronics/ucp/mcp",
			transport(slashService.buildProfile("electronics"), "mcp").getEndpoint());
	}

	@Test
	public void testSerializedShapeMatchesOfficialDiscoverySchema() throws Exception
	{
		// Pin the wire shape against the official profile.json / sample server:
		// {"ucp": {version, services: {ns: [...]}, capabilities: {ns: [...]},
		//  payment_handlers: {ns: [...]}}} — registries, NOT top-level arrays.
		final String json = objectMapper.writeValueAsString(profileService.buildProfile("electronics"));
		final JsonNode root = objectMapper.readTree(json);
		final JsonNode ucp = root.path("ucp");

		assertTrue("profile body must live inside the ucp object", ucp.isObject());
		assertEquals(PINNED_VERSION, ucp.path("version").asText());
		assertTrue("no top-level capabilities outside ucp", root.path("capabilities").isMissingNode());
		assertTrue("no top-level payment_handlers outside ucp", root.path("payment_handlers").isMissingNode());

		assertTrue("capabilities is a registry object", ucp.path("capabilities").isObject());
		assertTrue(ucp.path("capabilities").path("dev.ucp.shopping.checkout").isArray());
		assertTrue("capability entries carry no name field (the key is the name)",
			ucp.path("capabilities").path("dev.ucp.shopping.checkout").path(0).path("name").isMissingNode());
		assertEquals("dev.ucp.shopping.checkout",
			ucp.path("capabilities").path("dev.ucp.shopping.fulfillment").path(0).path("extends").asText());

		assertTrue("services is a registry object", ucp.path("services").isObject());
		final JsonNode shopping = ucp.path("services").path("dev.ucp.shopping");
		assertTrue("service entries are a list of transports", shopping.isArray());
		assertEquals("rest", shopping.path(0).path("transport").asText());
		assertEquals(PUBLIC_BASE_URL + "/occ/v2/electronics/ucp", shopping.path(0).path("endpoint").asText());
		assertEquals("mcp", shopping.path(1).path("transport").asText());
		assertEquals(PUBLIC_BASE_URL + "/occ/v2/electronics/ucp/mcp", shopping.path(1).path("endpoint").asText());

		assertTrue("payment_handlers is a registry object", ucp.path("payment_handlers").isObject());
		final JsonNode handlers = ucp.path("payment_handlers").path("com.thinkshop.mock_card");
		assertTrue(handlers.isArray());
		assertEquals("thinkshop_mock_card", handlers.path(0).path("id").asText());
		assertTrue("handler config serializes (empty object for the mock)",
			handlers.path(0).path("config").isObject());
	}

	@Test
	public void testProfileEnvelopeOmitsStatus() throws Exception
	{
		// status belongs to capability responses, not the discovery profile.
		final String json = objectMapper.writeValueAsString(profileService.buildProfile("electronics"));
		final JsonNode ucp = objectMapper.readTree(json).path("ucp");

		assertTrue("profile ucp block must not carry a status field", ucp.path("status").isMissingNode());
	}
}
