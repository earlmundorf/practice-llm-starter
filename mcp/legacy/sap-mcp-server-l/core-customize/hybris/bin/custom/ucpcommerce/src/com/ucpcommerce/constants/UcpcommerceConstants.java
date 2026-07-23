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

	private UcpcommerceConstants()
	{
		//empty
	}
}
