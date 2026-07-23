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
 * Phases 3–4 ship create/get/update; complete/cancel (Phase 5) are added as
 * the lifecycle grows.
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
}
