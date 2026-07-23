package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpOrder;
import com.ucpcommerce.dto.UcpOrderResponse;
import com.ucpcommerce.dto.UcpOrdersResponse;
import com.ucpcommerce.dto.UcpPagination;
import com.ucpcommerce.services.UcpOrderService;

import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.OrderHistoryData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.PaginationData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Order capability over the standard {@code OrderFacade} — the same calls the
 * proprietary {@code order_get}/{@code order_history} tools make, marshalled
 * to UCP order payloads (integer minor-unit money via the centralized
 * converter inside {@link UcpOrderMarshaller}). Both operations are scoped to
 * the authenticated customer by the facade contract; no customer parameter
 * exists on this surface.
 */
public class DefaultUcpOrderService implements UcpOrderService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultUcpOrderService.class);

	private static final int MIN_PAGE_SIZE = 1;
	private static final int MAX_PAGE_SIZE = 50;
	private static final String SORT_BY_DATE = "byDate";

	private OrderFacade orderFacade;
	private UcpOrderMarshaller ucpOrderMarshaller;

	@Override
	public UcpOrderResponse getOrder(final String id)
	{
		final UcpOrderResponse response = new UcpOrderResponse();
		try
		{
			final UcpOrder order = ucpOrderMarshaller.marshalFull(orderFacade.getOrderDetailsForCode(id));
			response.setUcp(envelope("success"));
			response.setOrder(order);
		}
		catch (final Exception e)
		{
			// Unknown id OR another customer's order — equally "not found" from
			// this caller's perspective; business error, never a 500.
			LOG.debug("get_order miss for id {}: {}", id, e.getMessage());
			response.setUcp(envelope("error"));
			response.setMessages(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Order not found: " + id)));
		}
		return response;
	}

	@Override
	public UcpOrdersResponse history(final int page, final int pageSize, final List<String> statuses)
	{
		final UcpOrdersResponse response = new UcpOrdersResponse();

		final OrderStatus[] statusFilter;
		try
		{
			statusFilter = toStatusFilter(statuses);
		}
		catch (final IllegalArgumentException e)
		{
			// A bad status string is fixable by the client — recoverable.
			response.setUcp(envelope("error"));
			response.setMessages(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_RECOVERABLE, e.getMessage())));
			return response;
		}

		final PageableData pageableData = new PageableData();
		pageableData.setCurrentPage(Math.max(0, page));
		pageableData.setPageSize(Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, pageSize)));
		pageableData.setSort(SORT_BY_DATE);

		// Zero varargs when unfiltered (the DAO treats an empty array as "all").
		final SearchPageData<OrderHistoryData> historyPage = statusFilter == null
			? orderFacade.getPagedOrderHistoryForStatuses(pageableData)
			: orderFacade.getPagedOrderHistoryForStatuses(pageableData, statusFilter);

		response.setUcp(envelope("success"));
		final List<UcpOrder> orders = new ArrayList<>();
		if (historyPage != null && historyPage.getResults() != null)
		{
			for (final OrderHistoryData entry : historyPage.getResults())
			{
				orders.add(ucpOrderMarshaller.marshalSummary(entry));
			}
		}
		response.setOrders(orders);
		if (historyPage != null && historyPage.getPagination() != null)
		{
			response.setPagination(toUcpPagination(historyPage.getPagination()));
		}
		return response;
	}

	/**
	 * Maps case-insensitive status codes to {@code OrderStatus}; null when no
	 * filter is requested (the coremcp {@code order_history} convention).
	 *
	 * @throws IllegalArgumentException for an unknown status code
	 */
	protected OrderStatus[] toStatusFilter(final List<String> statuses)
	{
		if (statuses == null || statuses.isEmpty())
		{
			return null;
		}
		final List<OrderStatus> filter = new ArrayList<>();
		for (final String status : statuses)
		{
			try
			{
				filter.add(OrderStatus.valueOf(status.toUpperCase(Locale.ROOT)));
			}
			catch (final Exception e)
			{
				throw new IllegalArgumentException("Unknown order status: " + status);
			}
		}
		return filter.toArray(new OrderStatus[0]);
	}

	protected UcpPagination toUcpPagination(final PaginationData paginationData)
	{
		final UcpPagination pagination = new UcpPagination();
		pagination.setCurrentPage(paginationData.getCurrentPage());
		pagination.setPageSize(paginationData.getPageSize());
		pagination.setTotalResults(paginationData.getTotalNumberOfResults());
		pagination.setTotalPages(paginationData.getNumberOfPages());
		return pagination;
	}

	protected UcpEnvelope envelope(final String status)
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus(status);
		return envelope;
	}

	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY, UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	@Required
	public void setOrderFacade(final OrderFacade orderFacade)
	{
		this.orderFacade = orderFacade;
	}

	@Required
	public void setUcpOrderMarshaller(final UcpOrderMarshaller ucpOrderMarshaller)
	{
		this.ucpOrderMarshaller = ucpOrderMarshaller;
	}
}
