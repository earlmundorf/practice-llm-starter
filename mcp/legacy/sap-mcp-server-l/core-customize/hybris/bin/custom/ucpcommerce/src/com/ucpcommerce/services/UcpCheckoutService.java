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
 * Phase 3 ships create/get; update (Phase 4) and complete/cancel (Phase 5)
 * are added as the lifecycle grows.
 */
public interface UcpCheckoutService
{
	/**
	 * Create a checkout: build a cart from the payload's {@code line_items}
	 * (created implicitly by the first successful add), persist a new
	 * {@code UcpCheckoutSessionEntry}, and return the full checkout object
	 * with status {@code incomplete}.
	 */
	UcpCheckout create(UcpCheckoutRequest payload);

	/**
	 * Resolve a checkout id and re-marshal the backing cart. Unknown/expired
	 * ids yield an {@code unrecoverable} {@code not_found} message payload.
	 */
	UcpCheckout get(String checkoutId);
}
