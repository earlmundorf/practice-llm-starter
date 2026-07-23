package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpLineItem;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpTotal;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;


@UnitTest
public class UcpCheckoutMarshallerTest
{
	private static final String PINNED_VERSION = "2026-04-08";

	private UcpCheckoutMarshaller marshaller;

	@Before
	public void setUp()
	{
		marshaller = new UcpCheckoutMarshaller()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		};
		marshaller.setUcpMoneyConverter(new UcpMoneyConverter());
	}

	private PriceData usd(final String major)
	{
		final PriceData price = new PriceData();
		price.setValue(new BigDecimal(major));
		price.setCurrencyIso("USD");
		return price;
	}

	private OrderEntryData entry(final int entryNumber, final String code, final String name,
		final long quantity, final String basePriceMajor, final String totalPriceMajor)
	{
		final OrderEntryData entry = new OrderEntryData();
		entry.setEntryNumber(entryNumber);
		entry.setQuantity(quantity);
		final ProductData product = new ProductData();
		product.setCode(code);
		product.setName(name);
		entry.setProduct(product);
		entry.setBasePrice(usd(basePriceMajor));
		entry.setTotalPrice(usd(totalPriceMajor));
		return entry;
	}

	private CartData cart()
	{
		final CartData cart = new CartData();
		cart.setCode("00001234");
		final List<OrderEntryData> entries = new ArrayList<>();
		entries.add(entry(0, "WIRELESS_GAMING_MOUSE", "Wireless Gaming Mouse", 2, "79.99", "159.98"));
		entries.add(entry(1, "LAPTOP_PRO_15", "Laptop Pro 15", 1, "1299.99", "1299.99"));
		cart.setEntries(entries);
		cart.setSubTotal(usd("1459.97"));
		cart.setTotalPrice(usd("1459.97"));
		return cart;
	}

	private Map<String, Long> totalsByType(final UcpCheckout checkout)
	{
		return checkout.getTotals().stream().collect(Collectors.toMap(UcpTotal::getType, UcpTotal::getAmount));
	}

	@Test
	public void marshalsLineItemsWithMinorUnitPrices()
	{
		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE,
			cart(), null, null);

		assertEquals(PINNED_VERSION, checkout.getUcp().getVersion());
		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals("ucp_chk_abc", checkout.getId());
		assertEquals("incomplete", checkout.getStatus());
		assertEquals("USD", checkout.getCurrency());
		assertEquals(2, checkout.getLineItems().size());

		final UcpLineItem mouse = checkout.getLineItems().get(0);
		assertEquals("li_0", mouse.getId());
		assertEquals(Long.valueOf(2L), mouse.getQuantity());
		assertEquals("WIRELESS_GAMING_MOUSE", mouse.getItem().getId());
		assertEquals("Wireless Gaming Mouse", mouse.getItem().getTitle());
		assertEquals("unit price $79.99 must become 7999 minor units",
			Long.valueOf(7999L), mouse.getItem().getPrice());
		assertEquals(1, mouse.getTotals().size());
		assertEquals(UcpTotal.TYPE_SUBTOTAL, mouse.getTotals().get(0).getType());
		assertEquals("line total $159.98 must become 15998 minor units",
			Long.valueOf(15998L), mouse.getTotals().get(0).getAmount());
	}

	@Test
	public void marshalsCartTotalsIncludingSubtotalAndTotal()
	{
		final Map<String, Long> totals = totalsByType(
			marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE, cart(), null, null));

		assertEquals(Long.valueOf(145997L), totals.get(UcpTotal.TYPE_SUBTOTAL));
		assertEquals(Long.valueOf(145997L), totals.get(UcpTotal.TYPE_TOTAL));
		assertNull("no discount entry when nothing is discounted", totals.get(UcpTotal.TYPE_DISCOUNT));
		assertNull("no shipping entry before a delivery mode is set", totals.get(UcpTotal.TYPE_SHIPPING));
	}

	@Test
	public void discountAppearsInTotalsWhenTheCartHasOne()
	{
		final CartData cart = cart();
		cart.setTotalDiscounts(usd("79.99"));
		cart.setTotalPrice(usd("1379.98"));

		final Map<String, Long> totals = totalsByType(
			marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE, cart, null, null));

		assertEquals("discounts are reported as a positive minor-unit amount",
			Long.valueOf(7999L), totals.get(UcpTotal.TYPE_DISCOUNT));
		assertEquals(Long.valueOf(137998L), totals.get(UcpTotal.TYPE_TOTAL));
	}

	@Test
	public void buyerAndMessagesArePassedThrough()
	{
		final UcpBuyer buyer = new UcpBuyer();
		buyer.setEmail("john.doe@thinkshop.com");
		final List<UcpMessage> messages = List.of(
			new UcpMessage("warning", "out_of_stock", UcpMessage.SEVERITY_RECOVERABLE, "partial"));

		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE,
			cart(), buyer, messages);

		assertEquals("john.doe@thinkshop.com", checkout.getBuyer().getEmail());
		assertEquals(1, checkout.getMessages().size());
	}

	@Test
	public void emptyMessagesAreOmittedNotEmptyArray()
	{
		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE,
			cart(), null, List.of());

		assertNull(checkout.getMessages());
	}

	@Test
	public void errorPayloadHasErrorEnvelopeAndNoCheckoutBody()
	{
		final UcpCheckout checkout = marshaller.error(List.of(
			new UcpMessage("error", "not_found", UcpMessage.SEVERITY_UNRECOVERABLE, "Unknown checkout id")));

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals(PINNED_VERSION, checkout.getUcp().getVersion());
		assertNull(checkout.getId());
		assertNull(checkout.getStatus());
		assertNull(checkout.getLineItems());
		assertNull(checkout.getTotals());
		assertNotNull(checkout.getMessages());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
	}

	@Test
	public void cartWithoutEntriesMarshalsEmptyLineItems()
	{
		final CartData cart = new CartData();
		cart.setSubTotal(usd("0.00"));
		cart.setTotalPrice(usd("0.00"));

		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE,
			cart, null, null);

		assertTrue(checkout.getLineItems().isEmpty());
		final Map<String, Long> totals = totalsByType(checkout);
		assertEquals("zero subtotal is still reported", Long.valueOf(0L), totals.get(UcpTotal.TYPE_SUBTOTAL));
		assertEquals(Long.valueOf(0L), totals.get(UcpTotal.TYPE_TOTAL));
	}
}
