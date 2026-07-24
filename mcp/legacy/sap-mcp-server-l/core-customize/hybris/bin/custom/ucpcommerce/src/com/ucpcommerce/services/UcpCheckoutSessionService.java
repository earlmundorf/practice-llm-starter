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

	/**
	 * Mark an accepted {@code complete_checkout}: status →
	 * {@code complete_in_progress} and the accepted {@code idempotency-key}
	 * stored on the entry, in one save (design S5: ready_for_complete →
	 * complete_in_progress). No-op for unknown/expired ids.
	 */
	void beginCompletion(String checkoutId, String idempotencyKey);

	/**
	 * Roll back a recoverable completion failure: status →
	 * {@code ready_for_complete} and the stored idempotency key cleared so a
	 * retry (same or new key) re-executes (design S5: complete_in_progress →
	 * ready_for_complete on recoverable failure). No-op for unknown/expired
	 * ids.
	 */
	void failCompletion(String checkoutId);

	/**
	 * Record a successful completion in ONE atomic entry save (runbook §5.2:
	 * the idempotency write must be atomic with order placement so a crash
	 * between them cannot double-charge): status → {@code completed}, the
	 * serialized completion response (replayed verbatim on duplicate keys) and
	 * the placed order's code. The idempotency key itself was stored by
	 * {@link #beginCompletion}. No-op for unknown/expired ids.
	 */
	void recordCompletion(String checkoutId, String completionResponseJson, String orderCode);
}
