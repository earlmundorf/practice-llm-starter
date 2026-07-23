package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpCapability;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpProfile;
import com.ucpcommerce.dto.UcpServiceEntry;
import com.ucpcommerce.dto.UcpTransportEndpoint;
import com.ucpcommerce.services.UcpProfileService;

import de.hybris.platform.util.Config;

/**
 * Default profile builder. The profile only ever advertises what actually
 * works: Phase 2 added {@code dev.ucp.shopping.catalog} and the {@code mcp}
 * transport; Phase 5 adds the checkout capability and the mock payment
 * handler, Phase 6 order/promotions/knowledge, Phase 7 the {@code rest}
 * transport.
 */
public class DefaultUcpProfileService implements UcpProfileService
{
	private static final String SHOPPING_SERVICE = "dev.ucp.shopping";
	private static final String CATALOG_CAPABILITY = "dev.ucp.shopping.catalog";
	private static final String CATALOG_SPEC_URL = "https://ucp.dev/specification/catalog";
	private static final String CATALOG_SCHEMA_URL = "https://ucp.dev/schemas/catalog.json";

	@Override
	public UcpProfile buildProfile(final String baseSiteId)
	{
		final String version = getPinnedUcpVersion();

		final UcpProfile profile = new UcpProfile();
		profile.setUcp(new UcpEnvelope(version));

		// Catalog capability (Phase 2) — first capability that works end-to-end.
		final UcpCapability catalog = new UcpCapability(CATALOG_CAPABILITY, version);
		catalog.setSpec(CATALOG_SPEC_URL);
		catalog.setSchema(CATALOG_SCHEMA_URL);
		profile.getCapabilities().add(catalog);

		// MCP transport (Phase 2). The advertised endpoint must be the
		// publicly reachable base (runbook §2.1) — configurable so an edge
		// rewrite / tunnel deployment can override the local default.
		final UcpServiceEntry shopping = new UcpServiceEntry();
		shopping.setMcp(new UcpTransportEndpoint(
			stripTrailingSlash(getPublicBaseUrl()) + "/occ/v2/" + baseSiteId + "/ucp/mcp"));
		profile.getServices().put(SHOPPING_SERVICE, shopping);

		// payment_handlers stays empty until complete_checkout works (Phase 5).
		return profile;
	}

	/**
	 * The pinned UCP spec version (dated calver string). Read from config so a
	 * deliberate bump is a one-line properties change; the shipped default is
	 * the version this surface was built and schema-validated against.
	 */
	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY, UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	/** Publicly reachable base URL for advertised transport endpoints. */
	protected String getPublicBaseUrl()
	{
		return Config.getString(UcpcommerceConstants.PUBLIC_BASE_URL_PROPERTY,
			UcpcommerceConstants.PUBLIC_BASE_URL_DEFAULT);
	}

	private static String stripTrailingSlash(final String baseUrl)
	{
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}
}
