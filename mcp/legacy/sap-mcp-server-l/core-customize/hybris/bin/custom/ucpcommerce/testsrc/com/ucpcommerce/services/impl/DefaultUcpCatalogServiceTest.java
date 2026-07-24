package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ucpcommerce.dto.UcpCatalogResponse;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpProduct;
import com.ucpcommerce.dto.UcpProductResponse;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.basecommerce.enums.StockLevelStatus;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.product.data.StockData;
import de.hybris.platform.commercefacades.search.ProductSearchFacade;
import de.hybris.platform.commercefacades.search.data.SearchStateData;
import de.hybris.platform.commerceservices.search.facetdata.ProductSearchPageData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.PaginationData;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class DefaultUcpCatalogServiceTest
{
	private static final String PINNED_VERSION = "2026-04-08";

	private DefaultUcpCatalogService catalogService;

	@Mock
	private ProductSearchFacade<ProductData> productSearchFacade;
	@Mock
	private ProductFacade productFacade;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		catalogService = new DefaultUcpCatalogService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		};
		catalogService.setProductSearchFacade(productSearchFacade);
		catalogService.setProductFacade(productFacade);
		catalogService.setUcpMoneyConverter(new UcpMoneyConverter());
	}

	private ProductData productData(final String code, final String name, final String priceMajor,
		final StockLevelStatus stockStatus)
	{
		final ProductData data = new ProductData();
		data.setCode(code);
		data.setName(name);
		data.setSummary("Summary of " + name);
		if (priceMajor != null)
		{
			final PriceData price = new PriceData();
			price.setValue(new BigDecimal(priceMajor));
			price.setCurrencyIso("USD");
			data.setPrice(price);
		}
		if (stockStatus != null)
		{
			final StockData stock = new StockData();
			stock.setStockLevelStatus(stockStatus);
			data.setStock(stock);
		}
		return data;
	}

	@Test
	public void testSearchMarshalsProductsWithMinorUnitPrices()
	{
		final ProductSearchPageData<SearchStateData, ProductData> page = new ProductSearchPageData<>();
		page.setResults(List.of(productData("LAPTOP_PRO_15", "Laptop Pro 15", "1299.99", StockLevelStatus.INSTOCK)));
		final PaginationData pagination = new PaginationData();
		pagination.setCurrentPage(0);
		pagination.setPageSize(10);
		pagination.setTotalNumberOfResults(1L);
		pagination.setNumberOfPages(1);
		page.setPagination(pagination);
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class))).thenReturn(page);

		final UcpCatalogResponse response = catalogService.search("laptop", 0, 10);

		assertEquals(PINNED_VERSION, response.getUcp().getVersion());
		assertEquals("success", response.getUcp().getStatus());
		assertEquals(1, response.getProducts().size());

		final UcpProduct product = response.getProducts().get(0);
		assertEquals("LAPTOP_PRO_15", product.getId());
		assertEquals("Laptop Pro 15", product.getTitle());
		assertEquals("integer minor units — $1299.99 must become 129999",
			Long.valueOf(129999L), product.getPrice());
		assertEquals("USD", product.getCurrency());
		assertEquals("in_stock", product.getAvailability());

		assertNotNull(response.getPagination());
		assertEquals(Long.valueOf(1L), response.getPagination().getTotalResults());
		assertEquals(Integer.valueOf(1), response.getPagination().getTotalPages());
	}

	@Test
	public void testSearchBuildsQuerySortStateAndClampsPaging()
	{
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class)))
			.thenReturn(new ProductSearchPageData<>());

		catalogService.search("laptop", -3, 500);

		final ArgumentCaptor<SearchStateData> stateCaptor = ArgumentCaptor.forClass(SearchStateData.class);
		final ArgumentCaptor<PageableData> pageCaptor = ArgumentCaptor.forClass(PageableData.class);
		verify(productSearchFacade).textSearch(stateCaptor.capture(), pageCaptor.capture());

		assertEquals("laptop:relevance", stateCaptor.getValue().getQuery().getValue());
		assertEquals("negative page clamps to 0", 0, pageCaptor.getValue().getCurrentPage());
		assertEquals("oversized page_size clamps to 50", 50, pageCaptor.getValue().getPageSize());
	}

	@Test
	public void testSearchWithNullQueryBrowsesAll()
	{
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class)))
			.thenReturn(new ProductSearchPageData<>());

		final UcpCatalogResponse response = catalogService.search(null, 0, 10);

		final ArgumentCaptor<SearchStateData> stateCaptor = ArgumentCaptor.forClass(SearchStateData.class);
		verify(productSearchFacade).textSearch(stateCaptor.capture(), any(PageableData.class));
		assertEquals(":relevance", stateCaptor.getValue().getQuery().getValue());
		assertTrue("null results marshal to empty products", response.getProducts().isEmpty());
		assertNull(response.getPagination());
	}

	@Test
	public void testProductWithoutPriceOrStockMarshalsNullSafely()
	{
		final ProductSearchPageData<SearchStateData, ProductData> page = new ProductSearchPageData<>();
		page.setResults(List.of(productData("NO_PRICE", "No Price", null, null)));
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class))).thenReturn(page);

		final UcpProduct product = catalogService.search("x", 0, 10).getProducts().get(0);

		assertNull(product.getPrice());
		assertNull(product.getCurrency());
		assertNull(product.getAvailability());
	}

	@Test
	public void testOutOfStockAvailabilityMapping()
	{
		final ProductSearchPageData<SearchStateData, ProductData> page = new ProductSearchPageData<>();
		page.setResults(List.of(productData("OOS", "Gone", "9.99", StockLevelStatus.OUTOFSTOCK)));
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class))).thenReturn(page);

		assertEquals("out_of_stock", catalogService.search("x", 0, 10).getProducts().get(0).getAvailability());
	}

	@Test
	public void testGetProductReturnsMarshalledProduct()
	{
		when(productFacade.getProductForCodeAndOptions(eq("LAPTOP_PRO_15"), anyList()))
			.thenReturn(productData("LAPTOP_PRO_15", "Laptop Pro 15", "1299.99", StockLevelStatus.INSTOCK));

		final UcpProductResponse response = catalogService.getProduct("LAPTOP_PRO_15");

		assertEquals("success", response.getUcp().getStatus());
		assertNotNull(response.getProduct());
		assertEquals(Long.valueOf(129999L), response.getProduct().getPrice());
		assertNull(response.getMessages());
	}

	@Test
	public void testGetProductUnknownIdReturnsUnrecoverableErrorPayload()
	{
		when(productFacade.getProductForCodeAndOptions(eq("NOPE"), anyList()))
			.thenThrow(new UnknownIdentifierException("Product with code 'NOPE' not found!"));

		final UcpProductResponse response = catalogService.getProduct("NOPE");

		// Business error inside the payload — never an exception/500.
		assertEquals("error", response.getUcp().getStatus());
		assertNull(response.getProduct());
		assertEquals(1, response.getMessages().size());
		assertEquals("not_found", response.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, response.getMessages().get(0).getSeverity());
	}

	@Test
	public void testLookupResolvesKnownIdsAndReportsMissesAsMessages()
	{
		when(productFacade.getProductForCodeAndOptions(eq("LAPTOP_PRO_15"), anyList()))
			.thenReturn(productData("LAPTOP_PRO_15", "Laptop Pro 15", "1299.99", StockLevelStatus.INSTOCK));
		when(productFacade.getProductForCodeAndOptions(eq("MISSING"), anyList()))
			.thenThrow(new UnknownIdentifierException("nope"));

		final UcpCatalogResponse response = catalogService.lookup(List.of("LAPTOP_PRO_15", "MISSING"));

		assertEquals("success", response.getUcp().getStatus());
		assertEquals(1, response.getProducts().size());
		assertEquals("LAPTOP_PRO_15", response.getProducts().get(0).getId());
		assertEquals(1, response.getMessages().size());
		assertEquals("not_found", response.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, response.getMessages().get(0).getSeverity());
		assertTrue(response.getMessages().get(0).getContent().contains("MISSING"));
	}
}
