package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.ucpcommerce.dto.UcpOrder;
import com.ucpcommerce.dto.UcpTotal;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.order.data.OrderHistoryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.core.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;


@UnitTest
public class UcpOrderMarshallerTest
{
	private static final String PINNED_VERSION = "2026-04-08";

	private UcpOrderMarshaller marshaller;

	@Before
	public void setUp()
	{
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
		checkoutMarshaller.setUcpProfileService(new DefaultUcpProfileService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		});

		marshaller = new UcpOrderMarshaller();
		marshaller.setUcpCheckoutMarshaller(checkoutMarshaller);
		marshaller.setUcpMoneyConverter(moneyConverter);
		final com.coremcp.services.DeepLinkBuilder deepLinkBuilder = new com.coremcp.services.DeepLinkBuilder()
		{
			@Override
			public String orderUrl(final String code)
			{
				return "http://storefront.test/orders/" + code;
			}
		};
		marshaller.setDeepLinkBuilder(deepLinkBuilder);
	}

	private PriceData usd(final String major)
	{
		final PriceData price = new PriceData();
		price.setValue(new BigDecimal(major));
		price.setCurrencyIso("USD");
		return price;
	}

	private OrderData order()
	{
		final OrderData order = new OrderData();
		order.setCode("00005004");
		order.setCreated(new Date(1785000000000L));
		order.setStatus(OrderStatus.COMPLETED);

		final OrderEntryData entry = new OrderEntryData();
		entry.setEntryNumber(0);
		entry.setQuantity(2L);
		final ProductData product = new ProductData();
		product.setCode("WIRELESS_GAMING_MOUSE");
		product.setName("Wireless Gaming Mouse");
		entry.setProduct(product);
		entry.setBasePrice(usd("79.99"));
		entry.setTotalPrice(usd("159.98"));
		order.setEntries(List.of(entry));

		order.setSubTotal(usd("159.98"));
		order.setTotalDiscounts(usd("79.99"));
		order.setDeliveryCost(usd("5.99"));
		order.setTotalPrice(usd("85.98"));

		final AddressData address = new AddressData();
		address.setFirstName("John");
		address.setTown("New York");
		final CountryData country = new CountryData();
		country.setIsocode("US");
		address.setCountry(country);
		order.setDeliveryAddress(address);
		final DeliveryModeData mode = new DeliveryModeData();
		mode.setCode("thinkshop-standard");
		order.setDeliveryMode(mode);

		return order;
	}

	private Map<String, Long> totalsByType(final UcpOrder order)
	{
		return order.getTotals().stream().collect(Collectors.toMap(UcpTotal::getType, UcpTotal::getAmount));
	}

	@Test
	public void embeddedMarshalIsTheSpecOrderConfirmation()
	{
		// The completed-checkout embedded block is the spec's
		// OrderConfirmation: id + permalink_url (required — the reference
		// client reads it) + created_at; nothing heavier. Responses stored
		// before permalink_url existed replay as recorded (ADR 0003).
		final UcpOrder embedded = marshaller.marshal(order());

		assertEquals("00005004", embedded.getId());
		assertNotNull(embedded.getCreatedAt());
		assertEquals("http://storefront.test/orders/00005004", embedded.getPermalinkUrl());
		assertNull(embedded.getStatus());
		assertNull(embedded.getCurrency());
		assertNull(embedded.getLineItems());
		assertNull(embedded.getTotals());
		assertNull(embedded.getFulfillment());
	}

	@Test
	public void fullMarshalCarriesTheWholeOrderSchema()
	{
		final UcpOrder full = marshaller.marshalFull(order(), "ucp_chk_prov");

		assertEquals("00005004", full.getId());
		assertEquals("order.json requires the originating checkout id",
			"ucp_chk_prov", full.getCheckoutId());
		assertNotNull(full.getCreatedAt());
		assertEquals("hybris status codes are lowercased on the wire", "completed", full.getStatus());
		assertEquals("USD", full.getCurrency());

		assertEquals(1, full.getLineItems().size());
		assertEquals("WIRELESS_GAMING_MOUSE", full.getLineItems().get(0).getItem().getId());
		// ORDER line-item shape (order_line_item.json): quantity is an object
		// and a derived status is required — always processing on this demo
		// platform (no fulfillment process runs).
		assertEquals(Long.valueOf(2L), full.getLineItems().get(0).getQuantity().getTotal());
		assertEquals(Long.valueOf(2L), full.getLineItems().get(0).getQuantity().getOriginal());
		assertEquals(Long.valueOf(0L), full.getLineItems().get(0).getQuantity().getFulfilled());
		assertEquals("processing", full.getLineItems().get(0).getStatus());
		assertEquals("unit price $79.99 must become 7999 minor units",
			Long.valueOf(7999L), full.getLineItems().get(0).getItem().getPrice());

		final Map<String, Long> totals = totalsByType(full);
		assertEquals(Long.valueOf(15998L), totals.get(UcpTotal.TYPE_SUBTOTAL));
		assertEquals("discounts are negative on the wire (ADR 0003)",
			Long.valueOf(-7999L), totals.get(UcpTotal.TYPE_DISCOUNT));
		assertEquals(Long.valueOf(599L), totals.get(UcpTotal.TYPE_FULFILLMENT));
		assertEquals(Long.valueOf(8598L), totals.get(UcpTotal.TYPE_TOTAL));
		assertEquals("http://storefront.test/orders/00005004", full.getPermalinkUrl());

		assertNotNull(full.getFulfillment());
		assertEquals("New York", full.getFulfillment().getDestination().getCity());
		assertEquals("thinkshop-standard", full.getFulfillment().getDeliveryMode());
	}

	@Test
	public void summaryMarshalsOneHistoryEntry()
	{
		final OrderHistoryData history = new OrderHistoryData();
		history.setCode("THINK-0001");
		history.setPlaced(new Date(1780000000000L));
		history.setStatus(OrderStatus.COMPLETED);
		history.setTotal(usd("85.98"));

		final UcpOrder summary = marshaller.marshalSummary(history);

		assertEquals("THINK-0001", summary.getId());
		assertNotNull(summary.getCreatedAt());
		assertEquals("completed", summary.getStatus());
		assertEquals("USD", summary.getCurrency());
		assertEquals(1, summary.getTotals().size());
		assertEquals(UcpTotal.TYPE_TOTAL, summary.getTotals().get(0).getType());
		assertEquals("total $85.98 must become 8598 minor units",
			Long.valueOf(8598L), summary.getTotals().get(0).getAmount());
		assertNull("history summaries carry no line items", summary.getLineItems());
	}

	@Test
	public void summaryToleratesSparseHistoryEntries()
	{
		final OrderHistoryData history = new OrderHistoryData();
		history.setCode("THINK-0002");

		final UcpOrder summary = marshaller.marshalSummary(history);

		assertEquals("THINK-0002", summary.getId());
		assertNull(summary.getCreatedAt());
		assertEquals("a status-less hybris order (mock placeOrder path) is 'created' on the wire",
			"created", summary.getStatus());
		assertNull(summary.getCurrency());
		assertNull(summary.getTotals());
	}

	@Test
	public void nullInputsMarshalToNull()
	{
		assertNull(marshaller.marshal(null));
		assertNull(marshaller.marshalFull(null, null));
		assertNull(marshaller.marshalSummary(null));
	}

	@Test
	public void fullMarshalAlwaysEmitsFulfillmentAndTolerateUnknownCheckout()
	{
		// order.json requires fulfillment — an address-less order still gets
		// an (empty) object; an unknown provenance omits checkout_id.
		final OrderData bare = order();
		bare.setDeliveryAddress(null);
		bare.setDeliveryMode(null);

		final UcpOrder full = marshaller.marshalFull(bare, null);

		assertNull(full.getCheckoutId());
		assertNotNull("fulfillment must be present even when empty", full.getFulfillment());
		assertNull(full.getFulfillment().getDestination());
	}
}
