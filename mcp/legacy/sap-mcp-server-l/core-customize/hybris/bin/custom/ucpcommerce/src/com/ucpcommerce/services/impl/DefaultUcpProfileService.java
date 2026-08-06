package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpCapability;
import com.ucpcommerce.dto.UcpPaymentHandler;
import com.ucpcommerce.dto.UcpProfile;
import com.ucpcommerce.dto.UcpServiceEntry;
import com.ucpcommerce.services.UcpProfileService;

import de.hybris.platform.util.Config;

import java.util.List;
import java.util.Map;

/**
 * Default profile builder. Shape corrected against the official discovery
 * schema and the reference sample server (ADR 0003): everything lives inside
 * a top-level {@code ucp} object, and {@code services} / {@code capabilities}
 * / {@code payment_handlers} are registries — maps keyed by reverse-domain
 * name whose values are LISTS of version entries. The profile only ever
 * advertises what actually works.
 */
public class DefaultUcpProfileService implements UcpProfileService
{
	private static final String SHOPPING_SERVICE = "dev.ucp.shopping";
	private static final String CATALOG_CAPABILITY = "dev.ucp.shopping.catalog";
	private static final String CHECKOUT_CAPABILITY = "dev.ucp.shopping.checkout";
	private static final String FULFILLMENT_CAPABILITY = "dev.ucp.shopping.fulfillment";
	private static final String DISCOUNT_CAPABILITY = "dev.ucp.shopping.discount";
	private static final String ORDER_CAPABILITY = "dev.ucp.shopping.order";
	/** Custom reverse-domain capabilities (design R7) — no hosted spec/schema URLs. */
	private static final String PROMOTIONS_CAPABILITY = "com.thinkshop.promotions";
	private static final String KNOWLEDGE_CAPABILITY = "com.thinkshop.knowledge";
	/** Reverse-domain namespace the mock handler is registered under. */
	private static final String MOCK_HANDLER_NAMESPACE = "com.thinkshop.mock_card";

	@Override
	public UcpProfile buildProfile(final String baseSiteId)
	{
		final String version = getPinnedUcpVersion();
		final String specBase = "https://ucp.dev/" + version;

		final UcpProfile profile = new UcpProfile();
		final UcpProfile.Body body = profile.getUcp();
		body.setVersion(version);

		// Capability registry — keyed by capability name, values are version
		// entries (name lives in the key, not the entry). Spec/schema URLs use
		// the versioned ucp.dev layout the sample server advertises.
		body.getCapabilities().put(CATALOG_CAPABILITY,
			List.of(capability(version, specBase + "/specification/catalog",
				specBase + "/schemas/shopping/catalog.json", null)));
		body.getCapabilities().put(CHECKOUT_CAPABILITY,
			List.of(capability(version, specBase + "/specification/checkout",
				specBase + "/schemas/shopping/checkout.json", null)));
		// Fulfillment negotiation (methods → destinations → groups/options) is
		// implemented by the checkout service — advertised as the extension
		// capability it is in the official profile.
		body.getCapabilities().put(FULFILLMENT_CAPABILITY,
			List.of(capability(version, specBase + "/specification/fulfillment",
				specBase + "/schemas/shopping/fulfillment.json", CHECKOUT_CAPABILITY)));
		// Discount extension: declarative discounts.codes (case-insensitive)
		// with the official applied[] echo — implemented by the checkout
		// service's voucher/coupon integration.
		body.getCapabilities().put(DISCOUNT_CAPABILITY,
			List.of(capability(version, specBase + "/specification/discount",
				specBase + "/schemas/shopping/discount.json", CHECKOUT_CAPABILITY)));
		body.getCapabilities().put(ORDER_CAPABILITY,
			List.of(capability(version, specBase + "/specification/order",
				specBase + "/schemas/shopping/order.json", null)));
		// Custom reverse-domain capabilities (Phase 6, design R7): version-only
		// entries — nothing is hosted for these local capabilities.
		body.getCapabilities().put(PROMOTIONS_CAPABILITY, List.of(new UcpCapability(version)));
		body.getCapabilities().put(KNOWLEDGE_CAPABILITY, List.of(new UcpCapability(version)));

		// Service registry: one transport entry per binding. The advertised
		// endpoint must be the publicly reachable base (runbook §2.1) —
		// configurable so an edge rewrite / tunnel deployment can override the
		// local default. REST endpoint = the BASE the client prefixes to the
		// resource paths (/checkout-sessions, … — ADR 0002).
		final String publicBase = stripTrailingSlash(getPublicBaseUrl());
		final UcpServiceEntry rest = new UcpServiceEntry(version, "rest",
			publicBase + "/occ/v2/" + baseSiteId + "/ucp");
		rest.setSpec(specBase + "/specification/overview");
		final UcpServiceEntry mcp = new UcpServiceEntry(version, "mcp",
			publicBase + "/occ/v2/" + baseSiteId + "/ucp/mcp");
		mcp.setSpec(specBase + "/specification/overview");
		body.getServices().put(SHOPPING_SERVICE, List.of(rest, mcp));

		// Payment-handler registry (design R9): the single mock handler,
		// registered under its reverse-domain namespace. complete_checkout
		// accepts any credential token for this handler id and runs the
		// existing mock default-Visa flow — honestly declared as a mock.
		body.getPaymentHandlers().putAll(paymentHandlerRegistry());

		return profile;
	}

	@Override
	public Map<String, List<UcpPaymentHandler>> paymentHandlerRegistry()
	{
		final UcpPaymentHandler mockCard = new UcpPaymentHandler(UcpcommerceConstants.PAYMENT_HANDLER_ID,
			"ThinkShop mock card (demo handler — any credential token is accepted, no real payment)");
		mockCard.setVersion(getPinnedUcpVersion());
		// The ecosystem's well-known mock id, honestly declared as an alias of
		// the same demo handler (the conformance suite hard-codes it).
		final UcpPaymentHandler alias = new UcpPaymentHandler(UcpcommerceConstants.PAYMENT_HANDLER_ALIAS,
			"Alias of thinkshop_mock_card (same demo mock — no real payment)");
		alias.setVersion(getPinnedUcpVersion());
		return Map.of(MOCK_HANDLER_NAMESPACE, List.of(mockCard, alias));
	}

	private static UcpCapability capability(final String version, final String spec, final String schema,
		final String extendsCapability)
	{
		final UcpCapability capability = new UcpCapability(version);
		capability.setSpec(spec);
		capability.setSchema(schema);
		capability.setExtendsCapability(extendsCapability);
		return capability;
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
