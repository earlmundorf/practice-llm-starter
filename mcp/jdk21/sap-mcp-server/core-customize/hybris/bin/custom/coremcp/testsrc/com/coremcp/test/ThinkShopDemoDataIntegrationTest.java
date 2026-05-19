package com.coremcp.test;

import static org.junit.Assert.*;

import de.hybris.bootstrap.annotations.IntegrationTest;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.commerceservices.stock.CommerceStockService;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.ServicelayerTransactionalTest;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.Resource;

import org.junit.Before;
import org.junit.Test;


/**
 * Integration tests verifying ThinkShop demo data loaded from projectdata-coremcp.impex.
 * Uses the same commerce facades and services that OCC controllers inject, confirming
 * the data is correct before testing against the live server.
 */
@IntegrationTest
public class ThinkShopDemoDataIntegrationTest extends ServicelayerTransactionalTest
{
	private static final String SITE_UID = "electronics";
	private static final String CATALOG_ID = "electronicsProductCatalog";
	private static final String CATALOG_VERSION_NAME = "Online";

	private static final String JOHN = "john.doe@thinkshop.com";
	private static final String JANE = "jane.smith@thinkshop.com";
	private static final String BOB = "bob.wilson@thinkshop.com";

	private static final String[] ALL_PRODUCT_CODES = {
		"LAPTOP_PRO_15", "SMARTPHONE_X", "WIRELESS_HEADPHONES", "TABLET_AIR",
		"SMART_WATCH_PRO", "MECHANICAL_KEYBOARD", "WIRELESS_GAMING_MOUSE",
		"MONITOR_4K_27", "HD_WEBCAM", "BLUETOOTH_SPEAKER"
	};

	private static final Map<String, String> EXPECTED_NAMES = new LinkedHashMap<>();
	static
	{
		EXPECTED_NAMES.put("LAPTOP_PRO_15", "Laptop Pro 15");
		EXPECTED_NAMES.put("SMARTPHONE_X", "Smartphone X");
		EXPECTED_NAMES.put("WIRELESS_HEADPHONES", "Wireless Headphones");
		EXPECTED_NAMES.put("TABLET_AIR", "Tablet Air");
		EXPECTED_NAMES.put("SMART_WATCH_PRO", "Smart Watch Pro");
		EXPECTED_NAMES.put("MECHANICAL_KEYBOARD", "Mechanical Keyboard");
		EXPECTED_NAMES.put("WIRELESS_GAMING_MOUSE", "Wireless Gaming Mouse");
		EXPECTED_NAMES.put("MONITOR_4K_27", "4K Monitor 27\"");
		EXPECTED_NAMES.put("HD_WEBCAM", "HD Webcam");
		EXPECTED_NAMES.put("BLUETOOTH_SPEAKER", "Bluetooth Speaker");
	}

	private static final Map<String, BigDecimal> EXPECTED_PRICES = new LinkedHashMap<>();
	static
	{
		EXPECTED_PRICES.put("LAPTOP_PRO_15", new BigDecimal("1299.99"));
		EXPECTED_PRICES.put("SMARTPHONE_X", new BigDecimal("799.99"));
		EXPECTED_PRICES.put("WIRELESS_HEADPHONES", new BigDecimal("199.99"));
		EXPECTED_PRICES.put("TABLET_AIR", new BigDecimal("649.99"));
		EXPECTED_PRICES.put("SMART_WATCH_PRO", new BigDecimal("349.99"));
		EXPECTED_PRICES.put("MECHANICAL_KEYBOARD", new BigDecimal("149.99"));
		EXPECTED_PRICES.put("WIRELESS_GAMING_MOUSE", new BigDecimal("79.99"));
		EXPECTED_PRICES.put("MONITOR_4K_27", new BigDecimal("499.99"));
		EXPECTED_PRICES.put("HD_WEBCAM", new BigDecimal("89.99"));
		EXPECTED_PRICES.put("BLUETOOTH_SPEAKER", new BigDecimal("129.99"));
	}

	private static final Map<String, Long> EXPECTED_STOCK = new LinkedHashMap<>();
	static
	{
		EXPECTED_STOCK.put("LAPTOP_PRO_15", 25L);
		EXPECTED_STOCK.put("SMARTPHONE_X", 50L);
		EXPECTED_STOCK.put("WIRELESS_HEADPHONES", 100L);
		EXPECTED_STOCK.put("TABLET_AIR", 40L);
		EXPECTED_STOCK.put("SMART_WATCH_PRO", 75L);
		EXPECTED_STOCK.put("MECHANICAL_KEYBOARD", 60L);
		EXPECTED_STOCK.put("WIRELESS_GAMING_MOUSE", 80L);
		EXPECTED_STOCK.put("MONITOR_4K_27", 30L);
		EXPECTED_STOCK.put("HD_WEBCAM", 45L);
		EXPECTED_STOCK.put("BLUETOOTH_SPEAKER", 90L);
	}

	@Resource
	private ProductService productService;

	@Resource
	private ProductFacade productFacade;

	@Resource
	private UserService userService;

	@Resource
	private BaseSiteService baseSiteService;

	@Resource
	private CatalogVersionService catalogVersionService;

	@Resource
	private CustomerAccountService customerAccountService;

	@Resource
	private CommerceStockService commerceStockService;

	@Resource
	private CommonI18NService commonI18NService;

	private BaseStoreModel store;
	private CatalogVersionModel onlineCatalogVersion;

	@Before
	public void setUp() throws Exception
	{
		importCsv("/coremcp/test/testdata-thinkshop.impex", "utf-8");

		baseSiteService.setCurrentBaseSite(SITE_UID, true);
		onlineCatalogVersion = catalogVersionService.getCatalogVersion(CATALOG_ID, CATALOG_VERSION_NAME);
		catalogVersionService.setSessionCatalogVersions(Collections.singletonList(onlineCatalogVersion));
		store = baseSiteService.getCurrentBaseSite().getStores().get(0);

		commonI18NService.setCurrentCurrency(commonI18NService.getCurrency("USD"));
		commonI18NService.setCurrentLanguage(commonI18NService.getLanguage("en"));
	}

	// =========================================================================
	// Site / Store
	// =========================================================================

	@Test
	public void testElectronicsBaseSiteExists()
	{
		assertNotNull("electronics base site should exist",
			baseSiteService.getBaseSiteForUID(SITE_UID));
	}

	@Test
	public void testStoreHasUSDCurrency()
	{
		assertTrue("Store should have USD currency",
			store.getCurrencies().stream()
				.anyMatch(c -> "USD".equals(c.getIsocode())));
	}

	@Test
	public void testStoreHasWarehouse()
	{
		assertTrue("Store should have electronics-warehouse",
			store.getWarehouses().stream()
				.anyMatch(w -> "electronics-warehouse".equals(w.getCode())));
	}

	// =========================================================================
	// Products — Service Layer
	// =========================================================================

	@Test
	public void testAllProductsExistViaService()
	{
		for (final String code : ALL_PRODUCT_CODES)
		{
			final ProductModel product = productService.getProductForCode(onlineCatalogVersion, code);
			assertNotNull("Product " + code + " should exist", product);
		}
	}

	@Test
	public void testProductNamesMatchDemoData()
	{
		for (final Map.Entry<String, String> entry : EXPECTED_NAMES.entrySet())
		{
			final ProductModel product = productService.getProductForCode(onlineCatalogVersion, entry.getKey());
			assertEquals("Name mismatch for " + entry.getKey(),
				entry.getValue(), product.getName());
		}
	}

	// =========================================================================
	// Products — Facade (same code path as OCC controllers)
	// =========================================================================

	@Test
	public void testAllProductsAccessibleViaFacade()
	{
		final Collection<ProductOption> options = Collections.singletonList(ProductOption.BASIC);
		for (final String code : ALL_PRODUCT_CODES)
		{
			final ProductData data = productFacade.getProductForCodeAndOptions(code, options);
			assertNotNull("ProductFacade should return data for " + code, data);
			assertEquals(code, data.getCode());
		}
	}

	@Test
	public void testProductPricesViaFacade()
	{
		final Collection<ProductOption> options = Arrays.asList(ProductOption.BASIC, ProductOption.PRICE);
		for (final Map.Entry<String, BigDecimal> entry : EXPECTED_PRICES.entrySet())
		{
			final ProductData data = productFacade.getProductForCodeAndOptions(entry.getKey(), options);
			assertNotNull("Price should be set for " + entry.getKey(), data.getPrice());
			assertEquals("Price mismatch for " + entry.getKey(),
				0, entry.getValue().compareTo(data.getPrice().getValue()));
		}
	}

	// =========================================================================
	// Stock
	// =========================================================================

	@Test
	public void testStockLevelsMatchDemoData()
	{
		for (final Map.Entry<String, Long> entry : EXPECTED_STOCK.entrySet())
		{
			final ProductModel product = productService.getProductForCode(onlineCatalogVersion, entry.getKey());
			final Long stockLevel = commerceStockService.getStockLevelForProductAndBaseStore(product, store);
			assertNotNull("Stock level should exist for " + entry.getKey(), stockLevel);
			assertEquals("Stock mismatch for " + entry.getKey(),
				entry.getValue(), stockLevel);
		}
	}

	// =========================================================================
	// Customers
	// =========================================================================

	@Test
	public void testCustomersExist()
	{
		assertNotNull(userService.getUserForUID(JOHN));
		assertNotNull(userService.getUserForUID(JANE));
		assertNotNull(userService.getUserForUID(BOB));
	}

	@Test
	public void testCustomerNames()
	{
		assertEquals("John Doe", userService.getUserForUID(JOHN).getName());
		assertEquals("Jane Smith", userService.getUserForUID(JANE).getName());
		assertEquals("Bob Wilson", userService.getUserForUID(BOB).getName());
	}

	@Test
	public void testCustomerAddressesSet()
	{
		for (final String uid : new String[] { JOHN, JANE, BOB })
		{
			final CustomerModel customer = (CustomerModel) userService.getUserForUID(uid);
			final AddressModel shipAddr = customer.getDefaultShipmentAddress();
			assertNotNull(uid + " should have a default shipment address", shipAddr);
			final AddressModel payAddr = customer.getDefaultPaymentAddress();
			assertNotNull(uid + " should have a default payment address", payAddr);
		}
	}

	// =========================================================================
	// Orders — via CustomerAccountService (same path as OrderFacade)
	// =========================================================================

	@Test
	public void testOrderTHINK0001Exists()
	{
		final CustomerModel john = (CustomerModel) userService.getUserForUID(JOHN);
		final OrderModel order = customerAccountService.getOrderForCode(john, "THINK-0001", store);
		assertNotNull("THINK-0001 should exist", order);
	}

	@Test
	public void testOrderTHINK0002Exists()
	{
		final CustomerModel jane = (CustomerModel) userService.getUserForUID(JANE);
		final OrderModel order = customerAccountService.getOrderForCode(jane, "THINK-0002", store);
		assertNotNull("THINK-0002 should exist", order);
	}

	@Test
	public void testOrderTHINK0003Exists()
	{
		final CustomerModel john = (CustomerModel) userService.getUserForUID(JOHN);
		final OrderModel order = customerAccountService.getOrderForCode(john, "THINK-0003", store);
		assertNotNull("THINK-0003 should exist", order);
	}

	@Test
	public void testOrderStatuses()
	{
		final CustomerModel john = (CustomerModel) userService.getUserForUID(JOHN);
		final CustomerModel jane = (CustomerModel) userService.getUserForUID(JANE);

		assertEquals(OrderStatus.COMPLETED,
			customerAccountService.getOrderForCode(john, "THINK-0001", store).getStatus());
		assertEquals(OrderStatus.CREATED,
			customerAccountService.getOrderForCode(jane, "THINK-0002", store).getStatus());
		assertEquals(OrderStatus.COMPLETED,
			customerAccountService.getOrderForCode(john, "THINK-0003", store).getStatus());
	}

	@Test
	public void testOrderEntryCount_THINK0001()
	{
		final CustomerModel john = (CustomerModel) userService.getUserForUID(JOHN);
		final OrderModel order = customerAccountService.getOrderForCode(john, "THINK-0001", store);
		assertEquals("THINK-0001 should have 2 entries", 2, order.getEntries().size());
	}

	@Test
	public void testOrderEntryCount_THINK0002()
	{
		final CustomerModel jane = (CustomerModel) userService.getUserForUID(JANE);
		final OrderModel order = customerAccountService.getOrderForCode(jane, "THINK-0002", store);
		assertEquals("THINK-0002 should have 3 entries", 3, order.getEntries().size());
	}

	@Test
	public void testOrderEntryCount_THINK0003()
	{
		final CustomerModel john = (CustomerModel) userService.getUserForUID(JOHN);
		final OrderModel order = customerAccountService.getOrderForCode(john, "THINK-0003", store);
		assertEquals("THINK-0003 should have 3 entries", 3, order.getEntries().size());
	}

	@Test
	public void testOrderHistoryForJohn()
	{
		final CustomerModel john = (CustomerModel) userService.getUserForUID(JOHN);
		final PageableData pageableData = createPageableData();
		final SearchPageData<OrderModel> results =
			customerAccountService.getOrderList(john, store, null, pageableData);
		assertEquals("John should have 2 orders", 2, results.getResults().size());
	}

	@Test
	public void testOrderHistoryForJane()
	{
		final CustomerModel jane = (CustomerModel) userService.getUserForUID(JANE);
		final PageableData pageableData = createPageableData();
		final SearchPageData<OrderModel> results =
			customerAccountService.getOrderList(jane, store, null, pageableData);
		assertEquals("Jane should have 1 order", 1, results.getResults().size());
	}

	@Test
	public void testOrderHistoryForBob()
	{
		final CustomerModel bob = (CustomerModel) userService.getUserForUID(BOB);
		final PageableData pageableData = createPageableData();
		final SearchPageData<OrderModel> results =
			customerAccountService.getOrderList(bob, store, null, pageableData);
		assertEquals("Bob should have 0 orders", 0, results.getResults().size());
	}

	private PageableData createPageableData()
	{
		final PageableData pageableData = new PageableData();
		pageableData.setCurrentPage(0);
		pageableData.setPageSize(10);
		return pageableData;
	}
}
