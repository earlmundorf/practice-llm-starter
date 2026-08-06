package com.ucpcommerce.constants;

@SuppressWarnings({ "deprecation", "squid:CallToDeprecatedMethod" })
public class UcpcommerceConstants extends GeneratedUcpcommerceConstants
{
	public static final String EXTENSIONNAME = "ucpcommerce";

	/** Config key holding the pinned UCP spec version (dated calver string). */
	public static final String UCP_VERSION_PROPERTY = "ucpcommerce.ucp.version";

	/** Shipped default for the pinned UCP spec version — see project.properties. */
	public static final String UCP_VERSION_DEFAULT = "2026-04-08";

	/**
	 * Config key for the publicly reachable base URL advertised in the
	 * profile's {@code services.*.{mcp,rest}.endpoint} entries.
	 */
	public static final String PUBLIC_BASE_URL_PROPERTY = "ucpcommerce.public.base.url";

	/** Shipped default for the public base URL (local dev server). */
	public static final String PUBLIC_BASE_URL_DEFAULT = "https://localhost:9002";

	/**
	 * The single declared payment handler id (design R9): the profile's
	 * {@code payment_handlers} entry and the only {@code handler_id}
	 * {@code complete_checkout} accepts. Any credential token is accepted for
	 * this handler; the existing mock default-Visa flow runs behind it.
	 */
	public static final String PAYMENT_HANDLER_ID = "thinkshop_mock_card";

	/**
	 * Well-known mock handler id the official conformance suite (and the
	 * sample ecosystem) hard-codes — accepted as an ALIAS of the ThinkShop
	 * mock handler and declared alongside it in the profile registry.
	 */
	public static final String PAYMENT_HANDLER_ALIAS = "mock_payment_handler";

	private UcpcommerceConstants()
	{
		//empty
	}
}
