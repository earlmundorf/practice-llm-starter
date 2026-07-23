package com.ucpcommerce.services;

import com.ucpcommerce.dto.UcpCheckoutSession;

/**
 * Store for the opaque UCP checkout id → hybris cart code mapping plus
 * protocol state (design R5). Backed by persisted
 * {@code UcpCheckoutSessionEntry} items (CCv2 multi-node safe), with lazy TTL
 * eviction on access; abandoned rows are swept by
 * {@code ucpCheckoutSessionCleanupCronJob}.
 *
 * Statuses at this boundary are UCP wire code strings
 * (e.g. {@code "incomplete"}) — binding-agnostic per design R12.
 */
public interface UcpCheckoutSessionService
{
	/**
	 * Mint a new opaque checkout id ({@code ucp_chk_…}) and persist the entry.
	 *
	 * @param cartCode  backing hybris cart code
	 * @param status    initial UCP status code (normally {@code incomplete})
	 * @param buyerJson serialized UCP buyer block, or null
	 * @return the created session view (never null)
	 */
	UcpCheckoutSession create(String cartCode, String status, String buyerJson);

	/**
	 * Resolve a checkout id, touching {@code lastAccessedAt}. Expired entries
	 * are evicted lazily.
	 *
	 * @return the session view, or null when unknown/expired
	 */
	UcpCheckoutSession get(String checkoutId);

	/**
	 * Persist the cart code and derived status back onto the entry (the tail
	 * of every mutation's resolve → load → facade → persist-back bracket).
	 * No-op for unknown/expired ids.
	 */
	void update(String checkoutId, String cartCode, String status);

	/**
	 * Replace the stored serialized buyer block (Phase 4: {@code
	 * update_checkout} may re-supply {@code buyer}). No-op for unknown/expired
	 * ids.
	 */
	void updateBuyer(String checkoutId, String buyerJson);
}
