package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpOrderResponse;
import com.ucpcommerce.dto.UcpOrdersResponse;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderHistoryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.PaginationData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class DefaultUcpOrderServiceTest
{
	private static final String PINNED_VERSION = "2026-04-08";

	private DefaultUcpOrderService orderService;

	@Mock
	private OrderFacade orderFacade;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		final UcpCheckoutMarshaller checkoutMarshaller = new UcpCheckoutMarshaller()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		};
		final UcpMoneyConverter moneyConverter = new UcpMoneyConverter();
		checkoutMarshaller.setUcpMoneyConverter(moneyConverter);
		final UcpOrderMarshaller orderMarshaller = new UcpOrderMarshaller();
		orderMarshaller.setUcpCheckoutMarshaller(checkoutMarshaller);
		orderMarshaller.setUcpMoneyConverter(moneyConverter);
		orderMarshaller.setDeepLinkBuilder(new com.coremcp.services.DeepLinkBuilder()
		{
			@Override
			public String orderUrl(final String code)
			{
				return "http://storefront.test/orders/" + code;
			}
		});

		orderService = new DefaultUcpOrderService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		};
		orderService.setOrderFacade(orderFacade);
		orderService.setUcpOrderMarshaller(orderMarshaller);
	}

	private PriceData usd(final String major)
	{
		final PriceData price = new PriceData();
		price.setValue(new BigDecimal(major));
		price.setCurrencyIso("USD");
		return price;
	}

	private OrderHistoryData historyEntry(final String code, final String totalMajor)
	{
		final OrderHistoryData entry = new OrderHistoryData();
		entry.setCode(code);
		entry.setPlaced(new Date());
		entry.setStatus(OrderStatus.COMPLETED);
		entry.setTotal(usd(totalMajor));
		return entry;
	}

	@Test
	public void getOrderMarshalsTheFullOrder()
	{
		final OrderData orderData = new OrderData();
		orderData.setCode("00005004");
		orderData.setCreated(new Date());
		orderData.setStatus(OrderStatus.COMPLETED);
		orderData.setTotalPrice(usd("85.98"));
		when(orderFacade.getOrderDetailsForCode("00005004")).thenReturn(orderData);

		final UcpOrderResponse response = orderService.getOrder("00005004");

		assertEquals(PINNED_VERSION, response.getUcp().getVersion());
		assertEquals("success", response.getUcp().getStatus());
		assertNotNull(response.getOrder());
		assertEquals("00005004", response.getOrder().getId());
		assertEquals("completed", response.getOrder().getStatus());
		assertEquals("USD", response.getOrder().getCurrency());
		assertNull(response.getMessages());
	}

	@Test
	public void getOrderUnknownIdReturnsUnrecoverableNotFoundPayload()
	{
		when(orderFacade.getOrderDetailsForCode("NOPE"))
			.thenThrow(new UnknownIdentifierException("Order with code NOPE not found"));

		final UcpOrderResponse response = orderService.getOrder("NOPE");

		// Business error inside the payload — never an exception/500.
		assertEquals("error", response.getUcp().getStatus());
		assertNull(response.getOrder());
		assertEquals(1, response.getMessages().size());
		assertEquals("not_found", response.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, response.getMessages().get(0).getSeverity());
		assertTrue(response.getMessages().get(0).getContent().contains("NOPE"));
	}

	@Test
	public void historyMarshalsSummariesAndPagination()
	{
		final SearchPageData<OrderHistoryData> page = new SearchPageData<>();
		page.setResults(List.of(historyEntry("00005004", "85.98"), historyEntry("THINK-0001", "49.99")));
		final PaginationData pagination = new PaginationData();
		pagination.setCurrentPage(0);
		pagination.setPageSize(10);
		pagination.setTotalNumberOfResults(2L);
		pagination.setNumberOfPages(1);
		page.setPagination(pagination);
		when(orderFacade.getPagedOrderHistoryForStatuses(any(PageableData.class))).thenReturn(page);

		final UcpOrdersResponse response = orderService.history(0, 10, null);

		assertEquals("success", response.getUcp().getStatus());
		assertEquals(2, response.getOrders().size());
		assertEquals("00005004", response.getOrders().get(0).getId());
		assertEquals("total $85.98 must become 8598 minor units",
			Long.valueOf(8598L), response.getOrders().get(0).getTotals().get(0).getAmount());
		assertEquals("THINK-0001", response.getOrders().get(1).getId());
		assertNotNull(response.getPagination());
		assertEquals(Long.valueOf(2L), response.getPagination().getTotalResults());
	}

	@Test
	public void historyClampsPagingAndSortsByDate()
	{
		when(orderFacade.getPagedOrderHistoryForStatuses(any(PageableData.class)))
			.thenReturn(new SearchPageData<>());

		orderService.history(-4, 500, null);

		final ArgumentCaptor<PageableData> pageCaptor = ArgumentCaptor.forClass(PageableData.class);
		verify(orderFacade).getPagedOrderHistoryForStatuses(pageCaptor.capture());
		assertEquals("negative page clamps to 0", 0, pageCaptor.getValue().getCurrentPage());
		assertEquals("oversized page_size clamps to 50", 50, pageCaptor.getValue().getPageSize());
		assertEquals("byDate", pageCaptor.getValue().getSort());
	}

	@Test
	public void historyPassesStatusFiltersCaseInsensitively()
	{
		when(orderFacade.getPagedOrderHistoryForStatuses(any(PageableData.class),
			any(OrderStatus.class), any(OrderStatus.class))).thenReturn(new SearchPageData<>());

		final UcpOrdersResponse response = orderService.history(0, 10, List.of("completed", "CANCELLED"));

		assertEquals("success", response.getUcp().getStatus());
		verify(orderFacade).getPagedOrderHistoryForStatuses(any(PageableData.class),
			eq(OrderStatus.COMPLETED), eq(OrderStatus.CANCELLED));
	}

	@Test
	public void historyEmptyResultsMarshalSafely()
	{
		when(orderFacade.getPagedOrderHistoryForStatuses(any(PageableData.class)))
			.thenReturn(new SearchPageData<>());

		final UcpOrdersResponse response = orderService.history(0, 10, List.of());

		assertEquals("success", response.getUcp().getStatus());
		assertTrue(response.getOrders().isEmpty());
		assertNull(response.getPagination());
	}
}
