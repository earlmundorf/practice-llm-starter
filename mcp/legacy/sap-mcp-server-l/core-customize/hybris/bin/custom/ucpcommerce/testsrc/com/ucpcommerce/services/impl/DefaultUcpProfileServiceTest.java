package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpProfile;

import de.hybris.bootstrap.annotations.UnitTest;

import org.junit.Before;
import org.junit.Test;


@UnitTest
public class DefaultUcpProfileServiceTest
{
	private static final String PINNED_VERSION = "2026-04-08";

	private DefaultUcpProfileService profileService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp()
	{
		// Override the config seam so the test doesn't depend on platform Config state.
		profileService = new DefaultUcpProfileService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
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
	public void testPhase1ProfileAdvertisesNothingYet()
	{
		// The profile only advertises what works — nothing works yet in Phase 1.
		final UcpProfile profile = profileService.buildProfile("electronics");

		assertTrue("capabilities must start empty", profile.getCapabilities().isEmpty());
		assertTrue("services must start empty", profile.getServices().isEmpty());
		assertTrue("payment_handlers must start empty", profile.getPaymentHandlers().isEmpty());
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
		assertTrue("services must serialize as an object", root.path("services").isObject());
		assertTrue("payment_handlers must serialize as an array", root.path("payment_handlers").isArray());
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
