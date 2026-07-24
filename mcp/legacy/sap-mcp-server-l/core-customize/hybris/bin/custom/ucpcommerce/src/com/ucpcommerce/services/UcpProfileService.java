package com.ucpcommerce.services;

import com.ucpcommerce.dto.UcpProfile;

/**
 * Builds the public UCP discovery profile served at
 * {@code GET /occ/v2/{baseSiteId}/.well-known/ucp}.
 *
 * The profile advertises the pinned UCP spec version plus exactly the
 * capabilities, transports, and payment handlers that are implemented and
 * working — later phases add entries as they land.
 */
public interface UcpProfileService
{
	/**
	 * @param baseSiteId the OCC base site the profile is requested for
	 * @return the profile document to serialize as the response body
	 */
	UcpProfile buildProfile(String baseSiteId);
}
