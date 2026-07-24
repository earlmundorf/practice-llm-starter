package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpDestination;
import com.ucpcommerce.dto.UcpLineItem;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpTotal;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;

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
		// subtotal + total per line, as the sample server emits (ADR 0003).
		assertEquals(2, mouse.getTotals().size());
		assertEquals(UcpTotal.TYPE_SUBTOTAL, mouse.getTotals().get(0).getType());
		assertEquals("line total $159.98 must become 15998 minor units",
			Long.valueOf(15998L), mouse.getTotals().get(0).getAmount());
		assertEquals(UcpTotal.TYPE_TOTAL, mouse.getTotals().get(1).getType());
		assertEquals(Long.valueOf(15998L), mouse.getTotals().get(1).getAmount());
	}

	@Test
	public void everyCheckoutCarriesLinksAndAPaymentEcho()
	{
		// Base-schema conformance (ADR 0003): links is REQUIRED (empty like the
		// sample server) and payment defaults to an empty instruments block —
		// the reference client feeds response.payment into its next request.
		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE,
			cart(), null, null);

		assertNotNull(checkout.getLinks());
		assertTrue(checkout.getLinks().isEmpty());
		assertNotNull(checkout.getPayment());
		assertNotNull(checkout.getPayment().getInstruments());
		assertTrue(checkout.getPayment().getInstruments().isEmpty());
	}

	@Test
	public void totalIsAlwaysTheLastTotalsEntry()
	{
		// Clients read totals[-1] as the running total (the reference client
		// logs it after every step).
		final CartData cart = cart();
		cart.setTotalDiscounts(usd("79.99"));
		cart.setDeliveryCost(usd("5.99"));
		cart.setTotalPrice(usd("1385.97"));

		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE,
			cart, null, null);
		final List<UcpTotal> totals = checkout.getTotals();
		assertEquals(UcpTotal.TYPE_TOTAL, totals.get(totals.size() - 1).getType());
	}

	@Test
	public void marshalsCartTotalsIncludingSubtotalAndTotal()
	{
		final Map<String, Long> totals = totalsByType(
			marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE, cart(), null, null));

		assertEquals(Long.valueOf(145997L), totals.get(UcpTotal.TYPE_SUBTOTAL));
		assertEquals(Long.valueOf(145997L), totals.get(UcpTotal.TYPE_TOTAL));
		assertNull("no discount entry when nothing is discounted", totals.get(UcpTotal.TYPE_DISCOUNT));
		assertNull("no fulfillment entry before a delivery mode is set",
			totals.get(UcpTotal.TYPE_FULFILLMENT));
	}

	@Test
	public void discountAppearsInTotalsAsANegativeAmount()
	{
		// Official total.json (ADR 0003): discount entries carry a NEGATIVE
		// amount (exclusiveMaximum: 0); hybris reports the magnitude positive.
		final CartData cart = cart();
		cart.setTotalDiscounts(usd("79.99"));
		cart.setTotalPrice(usd("1379.98"));

		final Map<String, Long> totals = totalsByType(
			marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE, cart, null, null));

		assertEquals("discounts are reported as a NEGATIVE minor-unit amount",
			Long.valueOf(-7999L), totals.get(UcpTotal.TYPE_DISCOUNT));
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
	public void fulfillmentIsMarshalledFromTheCartsAddressAndMode()
	{
		final CartData cart = cart();

		final AddressData address = new AddressData();
		address.setFirstName("John");
		address.setLastName("Doe");
		address.setLine1("100 Main St");
		address.setTown("New York");
		address.setPostalCode("10001");
		final RegionData region = new RegionData();
		region.setIsocode("US-NY");
		region.setIsocodeShort("NY");
		address.setRegion(region);
		final CountryData country = new CountryData();
		country.setIsocode("US");
		address.setCountry(country);
		cart.setDeliveryAddress(address);

		final DeliveryModeData mode = new DeliveryModeData();
		mode.setCode("thinkshop-standard");
		mode.setName("Standard Delivery");
		cart.setDeliveryMode(mode);
		cart.setDeliveryCost(usd("5.99"));
		cart.setTotalPrice(usd("1465.96"));

		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc",
			UcpCheckout.STATUS_READY_FOR_COMPLETE, cart, null, null);

		assertNotNull(checkout.getFulfillment());
		final UcpDestination destination = checkout.getFulfillment().getDestination();
		assertEquals("John", destination.getFirstName());
		assertEquals("100 Main St", destination.getLine1());
		assertEquals("New York", destination.getCity());
		assertEquals("the short region code is echoed", "NY", destination.getRegion());
		assertEquals("10001", destination.getPostalCode());
		assertEquals("US", destination.getCountry());
		assertEquals("thinkshop-standard", checkout.getFulfillment().getDeliveryMode());
		assertEquals("Standard Delivery", checkout.getFulfillment().getDeliveryModeName());

		final Map<String, Long> totals = totalsByType(checkout);
		assertEquals("delivery cost $5.99 must become 599 minor units under type fulfillment (ADR 0003)",
			Long.valueOf(599L), totals.get(UcpTotal.TYPE_FULFILLMENT));
		assertEquals(Long.valueOf(146596L), totals.get(UcpTotal.TYPE_TOTAL));
	}

	@Test
	public void noFulfillmentBlockBeforeADestinationIsSet()
	{
		final UcpCheckout checkout = marshaller.marshal("ucp_chk_abc", UcpCheckout.STATUS_INCOMPLETE,
			cart(), null, null);

		assertNull(checkout.getFulfillment());
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
