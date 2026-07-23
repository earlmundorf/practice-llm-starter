package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpCapability;
import com.ucpcommerce.dto.UcpProfile;

import de.hybris.bootstrap.annotations.UnitTest;

import org.junit.Before;
import org.junit.Test;


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
	public void testPhase2ProfileAdvertisesCatalogCapability()
	{
		final UcpProfile profile = profileService.buildProfile("electronics");

		final UcpCapability catalog = capability(profile, "dev.ucp.shopping.catalog");
		assertNotNull("catalog capability must be advertised", catalog);
		assertEquals("capability version is the pinned dated calver string",
			PINNED_VERSION, catalog.getVersion());
		assertNotNull(catalog.getSpec());
		assertNotNull(catalog.getSchema());
	}

	@Test
	public void testPhase5ProfileAdvertisesCheckoutCapability()
	{
		final UcpProfile profile = profileService.buildProfile("electronics");

		// Exactly the capabilities that work end-to-end so far (Phases 2 + 5).
		assertEquals(2, profile.getCapabilities().size());
		final UcpCapability checkout = capability(profile, "dev.ucp.shopping.checkout");
		assertNotNull("checkout capability must be advertised once the lifecycle works", checkout);
		assertEquals(PINNED_VERSION, checkout.getVersion());
		assertNotNull(checkout.getSpec());
		assertNotNull(checkout.getSchema());
	}

	private UcpCapability capability(final UcpProfile profile, final String name)
	{
		return profile.getCapabilities().stream()
			.filter(c -> name.equals(c.getName()))
			.findFirst().orElse(null);
	}

	@Test
	public void testPhase2ProfileAdvertisesMcpTransportForBaseSite()
	{
		final UcpProfile profile = profileService.buildProfile("electronics");

		assertTrue(profile.getServices().containsKey("dev.ucp.shopping"));
		assertNotNull("mcp transport must be advertised", profile.getServices().get("dev.ucp.shopping").getMcp());
		assertEquals(PUBLIC_BASE_URL + "/occ/v2/electronics/ucp/mcp",
			profile.getServices().get("dev.ucp.shopping").getMcp().getEndpoint());
		// REST is not advertised until it works (Phase 7).
		assertEquals(null, profile.getServices().get("dev.ucp.shopping").getRest());
	}

	@Test
	public void testPhase5ProfileDeclaresTheSingleMockPaymentHandler()
	{
		// One honest mock handler (design R9): thinkshop_mock_card, nothing else.
		final UcpProfile profile = profileService.buildProfile("electronics");

		assertEquals(1, profile.getPaymentHandlers().size());
		assertEquals("thinkshop_mock_card", profile.getPaymentHandlers().get(0).getId());
		assertNotNull("handler declares a human-readable name",
			profile.getPaymentHandlers().get(0).getName());
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
			slashService.buildProfile("electronics").getServices().get("dev.ucp.shopping").getMcp().getEndpoint());
	}

	@Test
	public void testSerializedShapeMatchesDiscoveryContract() throws Exception
	{
		// Pin the wire shape: ucp.version + top-level snake_case blocks always present.
		final String json = objectMapper.writeValueAsString(profileService.buildProfile("electronics"));
		final JsonNode root = objectMapper.readTree(json);

		assertEquals(PINNED_VERSION, root.path("ucp").path("version").asText());
		assertTrue("ucp block must be an object", root.path("ucp").isObject());
		assertTrue("capabilities must serialize as an array", root.path("capabilities").isArray());
		assertEquals("dev.ucp.shopping.catalog", root.path("capabilities").path(0).path("name").asText());
		assertEquals("dev.ucp.shopping.checkout", root.path("capabilities").path(1).path("name").asText());
		assertTrue("services must serialize as an object", root.path("services").isObject());
		assertEquals(PUBLIC_BASE_URL + "/occ/v2/electronics/ucp/mcp",
			root.path("services").path("dev.ucp.shopping").path("mcp").path("endpoint").asText());
		assertTrue("payment_handlers must serialize as an array", root.path("payment_handlers").isArray());
		assertEquals("thinkshop_mock_card", root.path("payment_handlers").path(0).path("id").asText());
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
