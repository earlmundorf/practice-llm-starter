package com.ucpcommerce.controllers;

import com.ucpcommerce.services.UcpCatalogService;

import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * UCP REST catalog binding (Phase 7): GET routes over the identical
 * binding-agnostic {@link UcpCatalogService} the MCP tools use (design R12).
 *
 * <pre>
 *   GET /occ/v2/{baseSiteId}/ucp/catalog/search?query=&amp;page=&amp;page_size=
 *   GET /occ/v2/{baseSiteId}/ucp/catalog/lookup?ids=A,B
 *   GET /occ/v2/{baseSiteId}/ucp/products/{id}
 * </pre>
 *
 * Same defaults/clamps as the MCP tools ({@code page}=0,
 * {@code page_size}=10). Business errors (unknown product id, per-id lookup
 * misses) are HTTP-200 payloads with {@code ucp.status}/{@code messages[]};
 * malformed parameters are HTTP 400.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class UcpCatalogRestController extends AbstractUcpRestController
{
	private static final int DEFAULT_PAGE_SIZE = 10;

	@Resource(name = "ucpCatalogService")
	private UcpCatalogService ucpCatalogService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/catalog/search", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String search(
		@RequestParam(value = "query", required = false, defaultValue = "") final String query,
		@RequestParam(value = "page", required = false) final String page,
		@RequestParam(value = "page_size", required = false) final String pageSize,
		final HttpServletResponse response) throws IOException
	{
		try
		{
			return json(ucpCatalogService.search(query,
				intParam(page, 0, "page"),
				intParam(pageSize, DEFAULT_PAGE_SIZE, "page_size")), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/catalog/lookup", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String lookup(
		@RequestParam(value = "ids", required = false) final String ids,
		final HttpServletResponse response) throws IOException
	{
		try
		{
			final List<String> idList = csvParam(ids);
			if (idList == null)
			{
				// Same rule as the MCP tool: ids is required (client protocol bug).
				throw new IllegalArgumentException("ids is required (comma-separated product ids)");
			}
			return json(ucpCatalogService.lookup(idList), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/products/{productId}", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String getProduct(@PathVariable final String productId, final HttpServletResponse response)
		throws IOException
	{
		try
		{
			return json(ucpCatalogService.getProduct(productId), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}
}
