package com.ucpcommerce.services;

import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpCheckoutRequest;

/**
 * Binding-agnostic UCP checkout capability (design R12): the same operations
 * serve the MCP tools now and the REST routes in Phase 7. Every mutation
 * follows the resolve → load cart → facade calls → persist-back bracket
 * (design diagram S2); business failures are returned as checkout payloads
 * with {@code ucp.status="error"} + {@code messages[]}, never thrown.
 *
 * Phases 3–4 ship create/get/update; Phase 5 completes the lifecycle with
 * complete/cancel (mock payment handler + idempotency, designs R9/S3).
 */
public interface UcpCheckoutService
{
	/**
	 * Create a checkout: build a cart from the payload's {@code line_items}
	 * (created implicitly by the first successful add), apply any
	 * {@code fulfillment} destination, persist a new
	 * {@code UcpCheckoutSessionEntry}, and return the full checkout object
	 * with its derived status (normally {@code incomplete}).
	 */
	UcpCheckout create(UcpCheckoutRequest payload);

	/**
	 * Create with OPTIONAL idempotency (official binding: any mutating call
	 * may carry an Idempotency-Key): an identical retry replays the first
	 * response, a same-key/different-payload retry is a conflict.
	 */
	UcpCheckout create(UcpCheckoutRequest payload, String idempotencyKey);

	/**
	 * Resolve a checkout id and re-marshal the backing cart. Unknown/expired
	 * ids yield an {@code unrecoverable} {@code not_found} message payload.
	 */
	UcpCheckout get(String checkoutId);

	/**
	 * Update an existing checkout: apply line-item diffs against the current
	 * cart ({@code line_items} is the desired end state — absent items are
	 * removed, quantities adjusted, new items added), replace the buyer block
	 * when supplied, and apply the {@code fulfillment} destination/delivery
	 * mode. The status is then derived from the recalculated cart (design
	 * S5: destination + deliverable cart → {@code ready_for_complete}; the
	 * client-side status is never trusted) and persisted back onto the entry.
	 * Drools promotions fire during recalculation, so discounted totals
	 * become visible here.
	 */
	UcpCheckout update(String checkoutId, UcpCheckoutRequest payload);

	/** Update with OPTIONAL idempotency — semantics as on create. */
	UcpCheckout update(String checkoutId, UcpCheckoutRequest payload, String idempotencyKey);

	/**
	 * Complete the purchase (design S3): validate the payment instrument's
	 * {@code handler_id} against the single declared mock handler (R9), then
	 * run the existing mock payment path — {@code createPaymentSubscription}
	 * (default Visa) → {@code authorizePayment("123")} → {@code placeOrder} —
	 * and return the checkout with {@code status=completed} + the embedded
	 * {@code order} block.
	 *
	 * Idempotency (checked against the entry FIRST): a duplicate
	 * {@code idempotencyKey} replays the stored completion response verbatim —
	 * never a second {@code placeOrder}. An {@code InvalidCartException} is a
	 * {@code recoverable} message with the status rolled back to
	 * {@code ready_for_complete}; an unknown {@code handler_id} is
	 * {@code unrecoverable}.
	 *
	 * @param idempotencyKey required ({@code meta["idempotency-key"]} on the
	 *                       MCP binding); null/blank →
	 *                       {@link IllegalArgumentException} (client protocol
	 *                       bug, not a business error)
	 */
	UcpCheckout complete(String checkoutId, UcpCheckoutRequest payload, String idempotencyKey);

	/**
	 * Cancel the checkout — idempotent and terminal (S5): allowed from
	 * {@code incomplete} and {@code ready_for_complete}; repeating it on an
	 * already-{@code canceled} checkout re-returns the canceled state; a
	 * {@code completed} checkout can no longer be canceled
	 * ({@code unrecoverable}).
	 *
	 * @param idempotencyKey required, as on {@link #complete}
	 */
	UcpCheckout cancel(String checkoutId, String idempotencyKey);
}
