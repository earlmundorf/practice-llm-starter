package com.ucpcommerce.controllers;

import com.ucpcommerce.services.UcpOrderService;

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

/**
 * UCP REST order binding (Phase 7): GET routes over the identical
 * binding-agnostic {@link UcpOrderService} the MCP tools use (design R12),
 * scoped to the authenticated customer by the {@code OrderFacade} contract.
 *
 * <pre>
 *   GET /occ/v2/{baseSiteId}/ucp/orders/{id}
 *   GET /occ/v2/{baseSiteId}/ucp/orders?page=&amp;page_size=&amp;statuses=A,B
 * </pre>
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class UcpOrderRestController extends AbstractUcpRestController
{
	private static final int DEFAULT_PAGE_SIZE = 10;

	@Resource(name = "ucpOrderService")
	private UcpOrderService ucpOrderService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/orders/{orderId}", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String getOrder(@PathVariable final String orderId, final HttpServletResponse response) throws IOException
	{
		try
		{
			return json(ucpOrderService.getOrder(orderId), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/orders", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String listOrders(
		@RequestParam(value = "page", required = false) final String page,
		@RequestParam(value = "page_size", required = false) final String pageSize,
		@RequestParam(value = "statuses", required = false) final String statuses,
		final HttpServletResponse response) throws IOException
	{
		try
		{
			return json(ucpOrderService.history(
				intParam(page, 0, "page"),
				intParam(pageSize, DEFAULT_PAGE_SIZE, "page_size"),
				csvParam(statuses)), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}
}
