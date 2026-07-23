package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpProfile;
import com.ucpcommerce.services.UcpProfileService;

import de.hybris.platform.util.Config;

/**
 * Default profile builder. Phase 1 ships a nearly-empty profile: the pinned
 * UCP version plus empty capabilities/services/payment_handlers blocks —
 * Phase 2 adds dev.ucp.shopping.catalog and the mcp transport, Phase 5 the
 * checkout capability and the mock payment handler, Phase 6 order/promotions/
 * knowledge, Phase 7 the rest transport.
 */
public class DefaultUcpProfileService implements UcpProfileService
{
	@Override
	public UcpProfile buildProfile(final String baseSiteId)
	{
		final UcpProfile profile = new UcpProfile();
		profile.setUcp(new UcpEnvelope(getPinnedUcpVersion()));
		// capabilities / services / payment_handlers deliberately stay empty here:
		// each is populated by the phase that makes it actually work.
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
}
