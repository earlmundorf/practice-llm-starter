package com.ucpcommerce.services;

import com.ucpcommerce.dto.UcpPaymentHandler;
import com.ucpcommerce.dto.UcpProfile;

import java.util.List;
import java.util.Map;

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

	/**
	 * The payment-handler registry (reverse-domain namespace → version
	 * entries) — the same block the profile advertises. Checkout responses
	 * embed it in their {@code ucp} envelope because the official
	 * {@code response_checkout_schema} requires {@code payment_handlers}
	 * there (the agent picks a handler off the response, not the profile).
	 */
	Map<String, List<UcpPaymentHandler>> paymentHandlerRegistry();
}
