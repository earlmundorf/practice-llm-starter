package com.ucpcommerce.services;

import com.ucpcommerce.dto.UcpCatalogResponse;
import com.ucpcommerce.dto.UcpProductResponse;

import java.util.List;

/**
 * Binding-agnostic UCP catalog capability ({@code dev.ucp.shopping.catalog},
 * design R12): consumed unchanged by the MCP tools (Phase 2) and the REST
 * controllers (Phase 7). Business failures are reported inside the returned
 * payload ({@code ucp.status="error"} + {@code messages[]}), never thrown.
 */
public interface UcpCatalogService
{
	/**
	 * Free-text catalog search over Solr ({@code ProductSearchFacade.textSearch}).
	 *
	 * @param query    free-text query; empty/null browses the whole catalog
	 * @param page     0-based page number (negative treated as 0)
	 * @param pageSize results per page (clamped to a sane range)
	 */
	UcpCatalogResponse search(String query, int page, int pageSize);

	/**
	 * Batch lookup by product ids (SKU codes). Unknown ids do not fail the
	 * call — each one becomes a {@code not_found} entry in {@code messages[]}.
	 */
	UcpCatalogResponse lookup(List<String> ids);

	/**
	 * Single-product detail. Unknown id → {@code ucp.status="error"} with an
	 * {@code unrecoverable} {@code not_found} message.
	 */
	UcpProductResponse getProduct(String id);
}
