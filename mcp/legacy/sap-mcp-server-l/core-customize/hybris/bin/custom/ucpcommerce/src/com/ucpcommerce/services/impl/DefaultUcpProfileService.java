package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpCapability;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpPaymentHandler;
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
	private static final String CHECKOUT_CAPABILITY = "dev.ucp.shopping.checkout";
	private static final String CHECKOUT_SPEC_URL = "https://ucp.dev/specification/checkout";
	private static final String CHECKOUT_SCHEMA_URL = "https://ucp.dev/schemas/checkout.json";
	private static final String ORDER_CAPABILITY = "dev.ucp.shopping.order";
	private static final String ORDER_SPEC_URL = "https://ucp.dev/specification/order";
	private static final String ORDER_SCHEMA_URL = "https://ucp.dev/schemas/order.json";
	/** Custom reverse-domain capabilities (design R7) — no hosted spec/schema URLs. */
	private static final String PROMOTIONS_CAPABILITY = "com.thinkshop.promotions";
	private static final String KNOWLEDGE_CAPABILITY = "com.thinkshop.knowledge";

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

		// Checkout capability (Phase 5) — advertised only now that the full
		// create → update → complete/cancel lifecycle works end-to-end.
		final UcpCapability checkout = new UcpCapability(CHECKOUT_CAPABILITY, version);
		checkout.setSpec(CHECKOUT_SPEC_URL);
		checkout.setSchema(CHECKOUT_SCHEMA_URL);
		profile.getCapabilities().add(checkout);

		// Order capability (Phase 6) — order get/history over OrderFacade,
		// scoped to the authenticated customer.
		final UcpCapability order = new UcpCapability(ORDER_CAPABILITY, version);
		order.setSpec(ORDER_SPEC_URL);
		order.setSchema(ORDER_SCHEMA_URL);
		profile.getCapabilities().add(order);

		// Custom reverse-domain capabilities (Phase 6, design R7): promotions
		// metadata and knowledge-base content, reusing coremcp's services. No
		// spec/schema URLs — nothing is hosted for these local capabilities.
		profile.getCapabilities().add(new UcpCapability(PROMOTIONS_CAPABILITY, version));
		profile.getCapabilities().add(new UcpCapability(KNOWLEDGE_CAPABILITY, version));

		// MCP transport (Phase 2). The advertised endpoint must be the
		// publicly reachable base (runbook §2.1) — configurable so an edge
		// rewrite / tunnel deployment can override the local default.
		final UcpServiceEntry shopping = new UcpServiceEntry();
		final String publicBase = stripTrailingSlash(getPublicBaseUrl());
		shopping.setMcp(new UcpTransportEndpoint(publicBase + "/occ/v2/" + baseSiteId + "/ucp/mcp"));

		// REST transport (Phase 7) — advertised only now that the REST routes
		// work end-to-end. The endpoint is the BASE the client prefixes to the
		// resource paths (/checkout-sessions, /catalog/..., /orders — ADR 0002).
		shopping.setRest(new UcpTransportEndpoint(publicBase + "/occ/v2/" + baseSiteId + "/ucp"));
		profile.getServices().put(SHOPPING_SERVICE, shopping);

		// The single mock payment handler (design R9): complete_checkout
		// accepts any credential token for this handler id and runs the
		// existing mock default-Visa flow — honestly declared as a mock.
		final UcpPaymentHandler mockCard = new UcpPaymentHandler(UcpcommerceConstants.PAYMENT_HANDLER_ID,
			"ThinkShop mock card (demo handler — any credential token is accepted, no real payment)");
		mockCard.setVersion(version);
		profile.getPaymentHandlers().add(mockCard);

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
