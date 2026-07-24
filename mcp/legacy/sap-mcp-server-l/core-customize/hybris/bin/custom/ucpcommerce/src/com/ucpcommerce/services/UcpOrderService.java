package com.ucpcommerce.services;

import com.ucpcommerce.dto.UcpOrderResponse;
import com.ucpcommerce.dto.UcpOrdersResponse;

import java.util.List;

/**
 * Binding-agnostic UCP order capability ({@code dev.ucp.shopping.order},
 * design R12): order get + history over the standard {@code OrderFacade},
 * scoped to the authenticated customer by the facade contract. Business
 * failures (unknown order id, bad status filter) are reported inside the
 * returned payload ({@code ucp.status="error"} + {@code messages[]}), never
 * as exceptions/500s.
 */
public interface UcpOrderService
{
	/**
	 * Full order detail for one order id (the hybris order code returned by
	 * {@code complete_checkout} / {@code list_orders}).
	 */
	UcpOrderResponse getOrder(String id);

	/**
	 * Paged order history for the authenticated customer.
	 *
	 * @param page     0-based page number (negative clamps to 0)
	 * @param pageSize page size (clamped to 1–50)
	 * @param statuses optional hybris order-status filters (case-insensitive
	 *                 codes, e.g. {@code COMPLETED}); null/empty means all
	 */
	UcpOrdersResponse history(int page, int pageSize, List<String> statuses);
}
