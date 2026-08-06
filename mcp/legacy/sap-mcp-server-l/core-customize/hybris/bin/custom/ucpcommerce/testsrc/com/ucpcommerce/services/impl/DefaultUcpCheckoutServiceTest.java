package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpCheckoutRequest;
import com.ucpcommerce.dto.UcpCheckoutSession;
import com.ucpcommerce.dto.UcpDestination;
import com.ucpcommerce.dto.UcpDiscounts;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpFulfillment;
import com.ucpcommerce.dto.UcpItemRef;
import com.ucpcommerce.dto.UcpLineItemRequest;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpPayment;
import com.ucpcommerce.dto.UcpPaymentInstrument;
import com.ucpcommerce.services.UcpCheckoutSessionService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CartModificationData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.voucher.VoucherFacade;
import de.hybris.platform.commercefacades.voucher.data.VoucherData;
import de.hybris.platform.commercefacades.voucher.exceptions.VoucherOperationException;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;
import de.hybris.platform.order.InvalidCartException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class DefaultUcpCheckoutServiceTest
{
	private static final String CHECKOUT_ID = "ucp_chk_test0001";
	private static final String CART_CODE = "00001234";

	private DefaultUcpCheckoutService checkoutService;

	@Mock
	private CartFacade cartFacade;
	@Mock
	private CheckoutFacade checkoutFacade;
	@Mock
	private UserFacade userFacade;
	@Mock
	private VoucherFacade voucherFacade;

	@Mock
	private com.ucpcommerce.services.UcpIdempotencyService idempotencyService;
	@Mock
	private CartLoaderStrategy cartLoaderStrategy;
	@Mock
	private UcpCheckoutSessionService sessionService;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		final UcpCheckoutMarshaller marshaller = new UcpCheckoutMarshaller()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return "2026-04-08";
			}
		};
		marshaller.setUcpMoneyConverter(new UcpMoneyConverter());
		marshaller.setUcpProfileService(new DefaultUcpProfileService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return "2026-04-08";
			}
		});

		final UcpOrderMarshaller orderMarshaller = new UcpOrderMarshaller();
		orderMarshaller.setUcpCheckoutMarshaller(marshaller);
		orderMarshaller.setUcpMoneyConverter(new UcpMoneyConverter());
		orderMarshaller.setDeepLinkBuilder(new com.coremcp.services.DeepLinkBuilder()
		{
			@Override
			public String orderUrl(final String code)
			{
				return "http://storefront.test/orders/" + code;
			}
		});

		checkoutService = new DefaultUcpCheckoutService()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return "2026-04-08";
			}
		};
		checkoutService.setCartFacade(cartFacade);
		checkoutService.setCheckoutFacade(checkoutFacade);
		checkoutService.setUserFacade(userFacade);
		checkoutService.setVoucherFacade(voucherFacade);
		checkoutService.setUcpIdempotencyService(idempotencyService);
		when(idempotencyService.consult(anyString(), anyString(), anyString())).thenReturn(
			new com.ucpcommerce.services.UcpIdempotencyService.Consultation(
				com.ucpcommerce.services.UcpIdempotencyService.Outcome.NEW, null));
		checkoutService.setCartLoaderStrategy(cartLoaderStrategy);
		checkoutService.setUcpCheckoutSessionService(sessionService);
		checkoutService.setUcpCheckoutMarshaller(marshaller);
		checkoutService.setUcpOrderMarshaller(orderMarshaller);
		checkoutService.setUcpMoneyConverter(new UcpMoneyConverter());
	}

	private CartData sessionCart()
	{
		final CartData cart = new CartData();
		cart.setCode(CART_CODE);
		final OrderEntryData entry = new OrderEntryData();
		entry.setEntryNumber(0);
		entry.setQuantity(1L);
		final ProductData product = new ProductData();
		product.setCode("WIRELESS_GAMING_MOUSE");
		product.setName("Wireless Gaming Mouse");
		entry.setProduct(product);
		final PriceData price = new PriceData();
		price.setValue(new BigDecimal("79.99"));
		price.setCurrencyIso("USD");
		entry.setBasePrice(price);
		entry.setTotalPrice(price);
		cart.setEntries(List.of(entry));
		cart.setSubTotal(price);
		cart.setTotalPrice(price);
		return cart;
	}

	private UcpCheckoutSession session(final String status, final String buyerJson)
	{
		final UcpCheckoutSession session = new UcpCheckoutSession();
		session.setCheckoutId(CHECKOUT_ID);
		session.setCartCode(CART_CODE);
		session.setStatus(status);
		session.setBuyerJson(buyerJson);
		return session;
	}

	private UcpCheckoutRequest request(final String... itemIds)
	{
		final UcpCheckoutRequest request = new UcpCheckoutRequest();
		request.setLineItems(java.util.Arrays.stream(itemIds).map(id -> {
			final UcpLineItemRequest lineItem = new UcpLineItemRequest();
			final UcpItemRef item = new UcpItemRef();
			item.setId(id);
			lineItem.setItem(item);
			lineItem.setQuantity(1L);
			return lineItem;
		}).collect(java.util.stream.Collectors.toList()));
		return request;
	}

	private CartModificationData modification(final long quantityAdded)
	{
		final CartModificationData modification = new CartModificationData();
		modification.setQuantityAdded(quantityAdded);
		return modification;
	}

	@Test
	public void createBuildsCartMintsIdAndReturnsIncompleteCheckout() throws Exception
	{
		when(cartFacade.addToCart(eq("WIRELESS_GAMING_MOUSE"), eq(1L))).thenReturn(modification(1L));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(sessionService.create(eq(CART_CODE), eq(UcpCheckout.STATUS_INCOMPLETE), any()))
			.thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));

		final UcpCheckout checkout = checkoutService.create(request("WIRELESS_GAMING_MOUSE"));

		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(CHECKOUT_ID, checkout.getId());
		assertEquals("incomplete", checkout.getStatus());
		assertEquals(1, checkout.getLineItems().size());
		assertEquals(Long.valueOf(7999L), checkout.getLineItems().get(0).getItem().getPrice());
		assertNull("no messages on a clean create", checkout.getMessages());
		// The opaque id never leaks the internal cart code.
		assertTrue(checkout.getId().startsWith("ucp_chk_"));
	}

	@Test
	public void createStoresBuyerJsonOnTheEntryAndEchoesBuyer() throws Exception
	{
		when(cartFacade.addToCart(anyString(), anyLong())).thenReturn(modification(1L));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(sessionService.create(anyString(), anyString(), anyString()))
			.thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));

		final UcpCheckoutRequest request = request("WIRELESS_GAMING_MOUSE");
		final UcpBuyer buyer = new UcpBuyer();
		buyer.setEmail("john.doe@thinkshop.com");
		request.setBuyer(buyer);

		final UcpCheckout checkout = checkoutService.create(request);

		final org.mockito.ArgumentCaptor<String> buyerJson = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(sessionService).create(eq(CART_CODE), eq(UcpCheckout.STATUS_INCOMPLETE), buyerJson.capture());
		assertTrue(buyerJson.getValue().contains("john.doe@thinkshop.com"));
		assertEquals("john.doe@thinkshop.com", checkout.getBuyer().getEmail());
	}

	@Test
	public void createWithUnknownSkuOnlyReturnsErrorAndCreatesNoSession() throws Exception
	{
		when(cartFacade.addToCart(eq("NO_SUCH_SKU"), anyLong()))
			.thenThrow(new CommerceCartModificationException("Product not found"));

		final UcpCheckout checkout = checkoutService.create(request("NO_SUCH_SKU"));

		assertEquals("error", checkout.getUcp().getStatus());
		assertNull("no checkout id when nothing could be added", checkout.getId());
		assertTrue(checkout.getMessages().stream().anyMatch(m -> "not_found".equals(m.getCode())));
		assertTrue("terminal for this create",
			checkout.getMessages().stream()
				.anyMatch(m -> UcpMessage.SEVERITY_UNRECOVERABLE.equals(m.getSeverity())));
		verify(sessionService, never()).create(any(), any(), any());
	}

	@Test
	public void createWithMixedSkusSucceedsPartiallyWithRecoverableMessage() throws Exception
	{
		when(cartFacade.addToCart(eq("WIRELESS_GAMING_MOUSE"), eq(1L))).thenReturn(modification(1L));
		when(cartFacade.addToCart(eq("NO_SUCH_SKU"), eq(1L)))
			.thenThrow(new CommerceCartModificationException("Product not found"));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(sessionService.create(any(), any(), any()))
			.thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));

		final UcpCheckout checkout = checkoutService.create(request("WIRELESS_GAMING_MOUSE", "NO_SUCH_SKU"));

		assertEquals("the checkout is still created", "success", checkout.getUcp().getStatus());
		assertEquals(CHECKOUT_ID, checkout.getId());
		assertEquals(1, checkout.getMessages().size());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
	}

	@Test
	public void createWithZeroQuantityAddedReportsOutOfStock() throws Exception
	{
		when(cartFacade.addToCart(eq("WIRELESS_GAMING_MOUSE"), eq(1L))).thenReturn(modification(0L));

		final UcpCheckout checkout = checkoutService.create(request("WIRELESS_GAMING_MOUSE"));

		assertEquals("error", checkout.getUcp().getStatus());
		assertTrue(checkout.getMessages().stream().anyMatch(m -> "out_of_stock".equals(m.getCode())));
		verify(sessionService, never()).create(any(), any(), any());
	}

	@Test
	public void createWithoutLineItemsIsInvalidRequest()
	{
		final UcpCheckout checkout = checkoutService.create(new UcpCheckoutRequest());

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("invalid_request", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
	}

	@Test
	public void getResolvesLoadsAndMarshalsWithStoredStatusAndBuyer()
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, "{\"email\":\"john.doe@thinkshop.com\"}"));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckout checkout = checkoutService.get(CHECKOUT_ID);

		verify(cartLoaderStrategy).loadCart(CART_CODE);
		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(CHECKOUT_ID, checkout.getId());
		assertEquals("incomplete", checkout.getStatus());
		assertEquals(1, checkout.getLineItems().size());
		assertNotNull(checkout.getBuyer());
		assertEquals("john.doe@thinkshop.com", checkout.getBuyer().getEmail());
	}

	@Test
	public void getUnknownIdReturnsUnrecoverableNotFoundPayload()
	{
		when(sessionService.get("ucp_chk_nope")).thenReturn(null);

		final UcpCheckout checkout = checkoutService.get("ucp_chk_nope");

		// Business error inside the payload — never an exception/500.
		assertEquals("error", checkout.getUcp().getStatus());
		assertNull(checkout.getId());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(cartLoaderStrategy, never()).loadCart(anyString());
	}

	@Test
	public void getWithUnloadableCartReturnsNotFoundPayload()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		doThrow(new IllegalStateException("cart gone")).when(cartLoaderStrategy).loadCart(CART_CODE);

		final UcpCheckout checkout = checkoutService.get(CHECKOUT_ID);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
	}

	// ── Phase 4: update_checkout ────────────────────────────────────────────

	private CartData sessionCartWithQuantity(final long quantity)
	{
		final CartData cart = sessionCart();
		cart.getEntries().get(0).setQuantity(quantity);
		return cart;
	}

	/** Marks a cart as having a destination: delivery address + mode set. */
	private CartData withDestination(final CartData cart)
	{
		final AddressData address = new AddressData();
		address.setFirstName("John");
		address.setLastName("Doe");
		address.setLine1("100 Main St");
		address.setTown("New York");
		address.setPostalCode("10001");
		final CountryData country = new CountryData();
		country.setIsocode("US");
		address.setCountry(country);
		cart.setDeliveryAddress(address);

		final DeliveryModeData mode = new DeliveryModeData();
		mode.setCode("thinkshop-standard");
		mode.setName("Standard Delivery");
		cart.setDeliveryMode(mode);
		return cart;
	}

	private UcpDestination destination()
	{
		final UcpDestination destination = new UcpDestination();
		destination.setFirstName("John");
		destination.setLastName("Doe");
		destination.setLine1("100 Main St");
		destination.setCity("New York");
		destination.setRegion("NY");
		destination.setPostalCode("10001");
		destination.setCountry("US");
		return destination;
	}

	private UcpCheckoutRequest fulfillmentRequest(final UcpDestination destination, final String deliveryMode)
	{
		final UcpCheckoutRequest request = new UcpCheckoutRequest();
		final UcpFulfillment fulfillment = new UcpFulfillment();
		fulfillment.setDestination(destination);
		fulfillment.setDeliveryMode(deliveryMode);
		request.setFulfillment(fulfillment);
		return request;
	}

	private UcpCheckoutRequest itemsRequest(final Object... idQuantityPairs)
	{
		final UcpCheckoutRequest request = new UcpCheckoutRequest();
		final java.util.List<UcpLineItemRequest> lineItems = new java.util.ArrayList<>();
		for (int i = 0; i < idQuantityPairs.length; i += 2)
		{
			final UcpLineItemRequest lineItem = new UcpLineItemRequest();
			final UcpItemRef item = new UcpItemRef();
			item.setId((String) idQuantityPairs[i]);
			lineItem.setItem(item);
			lineItem.setQuantity(((Number) idQuantityPairs[i + 1]).longValue());
			lineItems.add(lineItem);
		}
		request.setLineItems(lineItems);
		return request;
	}

	private UcpCheckoutRequest discountsRequest(final String... codes)
	{
		final UcpCheckoutRequest request = new UcpCheckoutRequest();
		request.setDiscounts(new UcpDiscounts(List.of(codes)));
		return request;
	}

	private VoucherData appliedVoucher(final String code)
	{
		final VoucherData voucher = new VoucherData();
		voucher.setVoucherCode(code);
		return voucher;
	}

	@Test
	public void updateAppliesNewDiscountCodesDeclaratively() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		// First read = diff base (nothing applied); second read = the echo.
		when(voucherFacade.getVouchersForCart())
			.thenReturn(List.of(), List.of(appliedVoucher("10OFF")));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, discountsRequest("10OFF"));

		verify(voucherFacade).applyVoucher("10OFF");
		verify(voucherFacade, never()).releaseVoucher(anyString());
		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(List.of("10OFF"), checkout.getDiscounts().getCodes());
		// Official discount.json echo: applied[] entries with required
		// title + amount (positive magnitude; 0 when the facade reports none).
		assertEquals(1, checkout.getDiscounts().getApplied().size());
		assertEquals("10OFF", checkout.getDiscounts().getApplied().get(0).getCode());
		assertEquals("10OFF", checkout.getDiscounts().getApplied().get(0).getTitle());
		assertEquals(Long.valueOf(0L), checkout.getDiscounts().getApplied().get(0).getAmount());
	}

	@Test
	public void discountCodesMatchCaseInsensitively() throws Exception
	{
		// DSC-005 (discount.md): a case variant of an applied code neither
		// releases nor re-applies it — and the echo keeps the canonical code.
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(voucherFacade.getVouchersForCart()).thenReturn(List.of(appliedVoucher("10OFF")));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, discountsRequest("10off"));

		verify(voucherFacade, never()).applyVoucher(anyString());
		verify(voucherFacade, never()).releaseVoucher(anyString());
		assertEquals(List.of("10OFF"), checkout.getDiscounts().getCodes());
	}

	@Test
	public void caseVariantOfAnUnappliedCodeRetriesTheCanonicalForm() throws Exception
	{
		// Hybris coupon codes are stored canonically (uppercase): "10off"
		// misses as-given, then the uppercase retry lands.
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(voucherFacade.getVouchersForCart())
			.thenReturn(List.of(), List.of(appliedVoucher("10OFF")));
		doThrow(new VoucherOperationException("no such voucher"))
			.when(voucherFacade).applyVoucher("10off");

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, discountsRequest("10off"));

		verify(voucherFacade).applyVoucher("10OFF");
		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals("no invalid-code message when a case variant lands",
			null, checkout.getMessages());
		assertEquals(List.of("10OFF"), checkout.getDiscounts().getCodes());
	}

	@Test
	public void updateReleasesAppliedCodesAbsentFromTheDeclarativeList() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(voucherFacade.getVouchersForCart())
			.thenReturn(List.of(appliedVoucher("10OFF"), appliedVoucher("SUMMER")));

		checkoutService.update(CHECKOUT_ID, discountsRequest("10OFF"));

		verify(voucherFacade).releaseVoucher("SUMMER");
		verify(voucherFacade, never()).releaseVoucher("10OFF");
		// Already applied — a second apply would double-redeem.
		verify(voucherFacade, never()).applyVoucher(anyString());
	}

	@Test
	public void invalidDiscountCodeIsARecoverableMessageAndTheUpdateStillLands() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(voucherFacade.getVouchersForCart()).thenReturn(List.of());
		// Every case variant misses (the service retries upper/lower forms).
		doThrow(new VoucherOperationException("no such voucher"))
			.when(voucherFacade).applyVoucher(anyString());

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, discountsRequest("BOGUS"));

		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals("invalid_request", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_INCOMPLETE);
	}

	@Test
	public void absentDiscountsBlockLeavesAppliedVouchersUntouched() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		checkoutService.update(CHECKOUT_ID, itemsRequest("WIRELESS_GAMING_MOUSE", 1));

		verify(voucherFacade, never()).applyVoucher(anyString());
		verify(voucherFacade, never()).releaseVoucher(anyString());
	}

	@Test
	public void updateUnknownIdReturnsUnrecoverableNotFound()
	{
		when(sessionService.get("ucp_chk_nope")).thenReturn(null);

		final UcpCheckout checkout = checkoutService.update("ucp_chk_nope", itemsRequest("X", 1));

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(cartLoaderStrategy, never()).loadCart(anyString());
	}

	@Test
	public void updateTerminalStatusIsRejectedWithoutTouchingTheCart()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_COMPLETED, null));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, itemsRequest("X", 1));

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("conflict", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(cartLoaderStrategy, never()).loadCart(anyString());
		verify(sessionService, never()).update(anyString(), anyString(), anyString());
	}

	@Test
	public void updateIncreasesQuantityViaEntryDiff() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart())
			.thenReturn(sessionCartWithQuantity(1L), sessionCartWithQuantity(2L));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID,
			itemsRequest("WIRELESS_GAMING_MOUSE", 2));

		verify(cartLoaderStrategy).loadCart(CART_CODE);
		verify(cartFacade).updateCartEntry(0L, 2L);
		verify(cartFacade, never()).addToCart(anyString(), anyLong());
		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(Long.valueOf(2L), checkout.getLineItems().get(0).getQuantity());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_INCOMPLETE);
	}

	@Test
	public void updateDecreasesQuantityViaEntryDiff() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart())
			.thenReturn(sessionCartWithQuantity(3L), sessionCartWithQuantity(1L));

		checkoutService.update(CHECKOUT_ID, itemsRequest("WIRELESS_GAMING_MOUSE", 1));

		verify(cartFacade).updateCartEntry(0L, 1L);
	}

	@Test
	public void updateAddsNewItemsAndRemovesAbsentOnes() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(cartFacade.addToCart(eq("LAPTOP_PRO_15"), eq(1L))).thenReturn(modification(1L));

		// Desired end state: only the laptop → mouse removed, laptop added.
		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, itemsRequest("LAPTOP_PRO_15", 1));

		verify(cartFacade).addToCart("LAPTOP_PRO_15", 1L);
		verify(cartFacade).updateCartEntry(0L, 0L);
		assertEquals("success", checkout.getUcp().getStatus());
	}

	@Test
	public void updateWithUnchangedQuantityTouchesNothing() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCartWithQuantity(1L));

		checkoutService.update(CHECKOUT_ID, itemsRequest("WIRELESS_GAMING_MOUSE", 1));

		verify(cartFacade, never()).updateCartEntry(anyLong(), anyLong());
		verify(cartFacade, never()).addToCart(anyString(), anyLong());
	}

	@Test
	public void updateEmptyLineItemsIsRecoverableAndLeavesItemsUnchanged() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, itemsRequest());

		assertEquals("the checkout survives", "success", checkout.getUcp().getStatus());
		assertEquals("invalid_request", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(cartFacade, never()).updateCartEntry(anyLong(), anyLong());
		verify(cartFacade, never()).addToCart(anyString(), anyLong());
	}

	@Test
	public void updateUnknownSkuAddIsRecoverableAndRestOfDiffStillApplies() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCartWithQuantity(1L));
		when(cartFacade.addToCart(eq("NO_SUCH_SKU"), anyLong()))
			.thenThrow(new CommerceCartModificationException("Product not found"));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID,
			itemsRequest("WIRELESS_GAMING_MOUSE", 2, "NO_SUCH_SKU", 1));

		verify(cartFacade).updateCartEntry(0L, 2L);
		assertEquals("success", checkout.getUcp().getStatus());
		assertTrue(checkout.getMessages().stream().anyMatch(m -> "not_found".equals(m.getCode())
			&& UcpMessage.SEVERITY_RECOVERABLE.equals(m.getSeverity())));
	}

	@Test
	public void updateSetsDestinationAndModeAndDerivesReadyForComplete()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
		when(checkoutFacade.setDeliveryMode("thinkshop-standard")).thenReturn(true);
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID,
			fulfillmentRequest(destination(), "thinkshop-standard"));

		// The OCC-conformant inline-address flow: persisted to the customer
		// first, then selected on the cart.
		verify(userFacade).addAddress(any(AddressData.class));
		final org.mockito.ArgumentCaptor<AddressData> address =
			org.mockito.ArgumentCaptor.forClass(AddressData.class);
		verify(checkoutFacade).setDeliveryAddress(address.capture());
		assertEquals("100 Main St", address.getValue().getLine1());
		assertEquals("New York", address.getValue().getTown());
		assertEquals("10001", address.getValue().getPostalCode());
		assertEquals("US", address.getValue().getCountry().getIsocode());
		assertEquals("bare region codes are prefixed with the country",
			"US-NY", address.getValue().getRegion().getIsocode());
		verify(checkoutFacade).setDeliveryMode("thinkshop-standard");

		assertEquals(UcpCheckout.STATUS_READY_FOR_COMPLETE, checkout.getStatus());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_READY_FOR_COMPLETE);
		// The fulfillment echo comes from the cart, not the request.
		assertNotNull(checkout.getFulfillment());
		assertEquals("New York", checkout.getFulfillment().getDestination().getCity());
		assertEquals("thinkshop-standard", checkout.getFulfillment().getDeliveryMode());
	}

	@Test
	public void updateAutoSelectsCheapestDeliveryModeWhenNoneRequested()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);

		// After the address is applied the cart has a destination but no mode yet.
		final CartData addressOnly = withDestination(sessionCart());
		addressOnly.setDeliveryMode(null);
		when(checkoutFacade.getCheckoutCart()).thenReturn(addressOnly, withDestination(sessionCart()));

		final DeliveryModeData express = new DeliveryModeData();
		express.setCode("thinkshop-express");
		final PriceData expressCost = new PriceData();
		expressCost.setValue(new BigDecimal("14.99"));
		express.setDeliveryCost(expressCost);
		final DeliveryModeData standard = new DeliveryModeData();
		standard.setCode("thinkshop-standard");
		final PriceData standardCost = new PriceData();
		standardCost.setValue(new BigDecimal("5.99"));
		standard.setDeliveryCost(standardCost);
		org.mockito.Mockito.doReturn(List.of(express, standard))
			.when(checkoutFacade).getSupportedDeliveryModes();

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID,
			fulfillmentRequest(destination(), null));

		verify(checkoutFacade).setDeliveryMode("thinkshop-standard");
		assertEquals(UcpCheckout.STATUS_READY_FOR_COMPLETE, checkout.getStatus());
	}

	@Test
	public void updateWithoutDestinationStaysIncomplete() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCartWithQuantity(1L), sessionCartWithQuantity(2L));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID,
			itemsRequest("WIRELESS_GAMING_MOUSE", 2));

		assertEquals(UcpCheckout.STATUS_INCOMPLETE, checkout.getStatus());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_INCOMPLETE);
	}

	@Test
	public void updateFallsBackToIncompleteWhenTheAddressIsGone()
	{
		// The entry says ready_for_complete, but the cart has lost its address —
		// derived status wins (S5: status is computed, never trusted/stale).
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, new UcpCheckoutRequest());

		assertEquals(UcpCheckout.STATUS_INCOMPLETE, checkout.getStatus());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_INCOMPLETE);
	}

	@Test
	public void updateRejectedDeliveryModeIsARecoverableMessage()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
		when(checkoutFacade.setDeliveryMode("no-such-mode")).thenReturn(false);
		final CartData addressOnly = withDestination(sessionCart());
		addressOnly.setDeliveryMode(null);
		when(checkoutFacade.getCheckoutCart()).thenReturn(addressOnly);

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID,
			fulfillmentRequest(destination(), "no-such-mode"));

		assertEquals("success", checkout.getUcp().getStatus());
		assertTrue(checkout.getMessages().stream().anyMatch(m -> "invalid_request".equals(m.getCode())
			&& UcpMessage.SEVERITY_RECOVERABLE.equals(m.getSeverity())));
		assertEquals("no mode applied → still incomplete",
			UcpCheckout.STATUS_INCOMPLETE, checkout.getStatus());
	}

	// ── Spec fulfillment negotiation (ADR 0003) ────────────────────────────

	private AddressData bookAddress(final String id, final String line1, final String town, final String postal)
	{
		final AddressData address = new AddressData();
		address.setId(id);
		address.setFirstName("John");
		address.setLastName("Doe");
		address.setLine1(line1);
		address.setTown(town);
		address.setPostalCode(postal);
		final CountryData country = new CountryData();
		country.setIsocode("US");
		address.setCountry(country);
		return address;
	}

	private UcpCheckoutRequest methodsRequest(final String selectedDestinationId, final String selectedOptionId)
	{
		final UcpCheckoutRequest request = new UcpCheckoutRequest();
		final UcpFulfillment fulfillment = new UcpFulfillment();
		final com.ucpcommerce.dto.UcpFulfillmentMethod method = new com.ucpcommerce.dto.UcpFulfillmentMethod();
		method.setId("method_1");
		method.setType("shipping");
		method.setLineItemIds(List.of("li_0"));
		method.setSelectedDestinationId(selectedDestinationId);
		if (selectedOptionId != null)
		{
			final com.ucpcommerce.dto.UcpFulfillmentGroup group = new com.ucpcommerce.dto.UcpFulfillmentGroup();
			group.setId("group_1");
			group.setLineItemIds(List.of("li_0"));
			group.setSelectedOptionId(selectedOptionId);
			method.setGroups(List.of(group));
		}
		fulfillment.setMethods(List.of(method));
		request.setFulfillment(fulfillment);
		return request;
	}

	@Test
	public void negotiationTriggerOffersSavedAddressesAsDestinations()
	{
		// OOTB reference-client step 4: PUT methods with NO destination →
		// the response offers the customer's saved addresses.
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());
		when(userFacade.getAddressBook()).thenReturn(
			List.of(bookAddress("addr1", "100 Main St", "New York", "10001"),
				bookAddress("addr2", "456 Oak Ave", "Metropolis", "10012")));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, methodsRequest(null, null));

		verify(checkoutFacade, never()).setDeliveryAddress(any());
		final com.ucpcommerce.dto.UcpFulfillmentMethod method = checkout.getFulfillment().getMethods().get(0);
		assertEquals("method_1", method.getId());
		assertEquals("shipping", method.getType());
		assertEquals(2, method.getDestinations().size());
		assertEquals("addr1", method.getDestinations().get(0).getId());
		assertEquals("PostalAddress field mapping", "100 Main St",
			method.getDestinations().get(0).getStreetAddress());
		assertEquals("New York", method.getDestinations().get(0).getAddressLocality());
		assertEquals("US", method.getDestinations().get(0).getAddressCountry());
		assertNull("nothing selected yet", method.getSelectedDestinationId());
		// The group block is always present (clients index groups[0]); its
		// options stay empty until a destination is applied.
		assertEquals(1, method.getGroups().size());
		assertTrue("no options before a destination is applied",
			method.getGroups().get(0).getOptions().isEmpty());
		assertEquals(UcpCheckout.STATUS_INCOMPLETE, checkout.getStatus());
	}

	@Test
	public void negotiationSelectedDestinationAppliesAddressAndOffersOptions()
	{
		// OOTB step 5: PUT selected_destination_id → address applied by id,
		// response gains groups[].options[] (delivery modes, cheapest first).
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
		final CartData applied = withDestination(sessionCart());
		when(checkoutFacade.getCheckoutCart()).thenReturn(applied);
		when(userFacade.getAddressBook()).thenReturn(
			List.of(bookAddress("addr1", "100 Main St", "New York", "10001")));

		final DeliveryModeData express = new DeliveryModeData();
		express.setCode("thinkshop-express");
		express.setName("Express Delivery");
		final PriceData expressCost = new PriceData();
		expressCost.setValue(new BigDecimal("14.99"));
		expressCost.setCurrencyIso("USD");
		express.setDeliveryCost(expressCost);
		final DeliveryModeData standard = new DeliveryModeData();
		standard.setCode("thinkshop-standard");
		standard.setName("Standard Delivery");
		final PriceData standardCost = new PriceData();
		standardCost.setValue(new BigDecimal("5.99"));
		standardCost.setCurrencyIso("USD");
		standard.setDeliveryCost(standardCost);
		org.mockito.Mockito.doReturn(List.of(express, standard))
			.when(checkoutFacade).getSupportedDeliveryModes();

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, methodsRequest("addr1", null));

		final org.mockito.ArgumentCaptor<AddressData> byId =
			org.mockito.ArgumentCaptor.forClass(AddressData.class);
		verify(checkoutFacade).setDeliveryAddress(byId.capture());
		assertEquals("selected by saved-address id", "addr1", byId.getValue().getId());

		final com.ucpcommerce.dto.UcpFulfillmentMethod method = checkout.getFulfillment().getMethods().get(0);
		assertEquals("the applied cart address matches the offered book entry",
			"addr1", method.getSelectedDestinationId());
		final com.ucpcommerce.dto.UcpFulfillmentGroup group = method.getGroups().get(0);
		assertEquals("group_1", group.getId());
		assertEquals("options are the supported modes, cheapest FIRST",
			"thinkshop-standard", group.getOptions().get(0).getId());
		assertEquals(Long.valueOf(599L), group.getOptions().get(0).getTotals().get(1).getAmount());
		assertEquals("thinkshop-express", group.getOptions().get(1).getId());
		assertEquals(Long.valueOf(1499L), group.getOptions().get(1).getTotals().get(1).getAmount());
		assertEquals("the cart's current mode is echoed as the selected option",
			"thinkshop-standard", group.getSelectedOptionId());
	}

	@Test
	public void negotiationSelectedOptionSetsTheDeliveryMode()
	{
		// OOTB step 6: PUT groups[].selected_option_id → option ids ARE
		// delivery-mode codes.
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.setDeliveryMode("thinkshop-express")).thenReturn(true);
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));

		checkoutService.update(CHECKOUT_ID, methodsRequest("addr1", "thinkshop-express"));

		verify(checkoutFacade).setDeliveryMode("thinkshop-express");
	}

	@Test
	public void paymentInstrumentsAreEchoedAndDefaultEmpty() throws Exception
	{
		// The reference client feeds response.payment into its next request —
		// every checkout response carries a payment block (ADR 0003).
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckout noInstruments = checkoutService.update(CHECKOUT_ID, new UcpCheckoutRequest());
		assertNotNull(noInstruments.getPayment());
		assertTrue(noInstruments.getPayment().getInstruments().isEmpty());

		final UcpCheckoutRequest withInstrument = new UcpCheckoutRequest();
		final UcpPayment payment = new UcpPayment();
		final UcpPaymentInstrument instrument = new UcpPaymentInstrument();
		instrument.setHandlerId("thinkshop_mock_card");
		payment.setInstruments(List.of(instrument));
		withInstrument.setPayment(payment);

		final UcpCheckout echoed = checkoutService.update(CHECKOUT_ID, withInstrument);
		assertEquals(1, echoed.getPayment().getInstruments().size());
		assertEquals("thinkshop_mock_card", echoed.getPayment().getInstruments().get(0).getHandlerId());
	}

	@Test
	public void updateReplacesTheStoredBuyer()
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, "{\"email\":\"old@thinkshop.com\"}"));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckoutRequest request = new UcpCheckoutRequest();
		final UcpBuyer buyer = new UcpBuyer();
		buyer.setEmail("john.doe@thinkshop.com");
		request.setBuyer(buyer);

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, request);

		final org.mockito.ArgumentCaptor<String> buyerJson = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(sessionService).updateBuyer(eq(CHECKOUT_ID), buyerJson.capture());
		assertTrue(buyerJson.getValue().contains("john.doe@thinkshop.com"));
		assertEquals("john.doe@thinkshop.com", checkout.getBuyer().getEmail());
	}

	@Test
	public void updateWithUnloadableCartReturnsNotFoundPayload()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		doThrow(new IllegalStateException("cart gone")).when(cartLoaderStrategy).loadCart(CART_CODE);

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, itemsRequest("X", 1));

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(sessionService, never()).update(anyString(), anyString(), anyString());
	}

	// ── Phase 5: complete_checkout / cancel_checkout ────────────────────────

	private static final String IDEMPOTENCY_KEY = "1f0f2a34-idem-key-0001";
	private static final String ORDER_CODE = "00001000";
	private static final String HANDLER_ID = "thinkshop_mock_card";

	private UcpCheckoutRequest completeRequest(final String handlerId)
	{
		return completeRequest(handlerId, "tok_any_token_accepted");
	}

	private UcpCheckoutRequest completeRequest(final String handlerId, final String token)
	{
		final UcpCheckoutRequest request = new UcpCheckoutRequest();
		final UcpPayment payment = new UcpPayment();
		final UcpPaymentInstrument instrument = new UcpPaymentInstrument();
		instrument.setHandlerId(handlerId);
		instrument.setType("card");
		instrument.setCredential(Map.of("token", token));
		payment.setInstruments(List.of(instrument));
		request.setPayment(payment);
		return request;
	}

	@Test
	public void createRejectsAnUnsupportedRequestedUcpVersion()
	{
		// Version negotiation: a request pinned to a version this server does
		// not implement is version_unsupported (HTTP 422 over REST).
		final UcpCheckoutRequest request = itemsRequest("WIRELESS_GAMING_MOUSE", 1);
		final UcpEnvelope requested = new UcpEnvelope("2099-01-01");
		request.setUcp(requested);

		final UcpCheckout checkout = checkoutService.create(request);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("version_unsupported", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
	}

	@Test
	public void completeDeclinesTheFailTokenProbeWithoutAcceptingTheCompletion() throws Exception
	{
		// The mock handler's decline probe: fail_token → payment_declined
		// (HTTP 402 over REST) BEFORE any state transition or order placement.
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID, "fail_token"), IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("payment_declined", checkout.getMessages().get(0).getCode());
		verify(sessionService, never()).beginCompletion(anyString(), anyString());
		verify(checkoutFacade, never()).placeOrder();
	}

	private OrderData orderData()
	{
		final OrderData order = new OrderData();
		order.setCode(ORDER_CODE);
		order.setCreated(new Date());
		final PriceData total = new PriceData();
		total.setValue(new BigDecimal("85.98"));
		total.setCurrencyIso("USD");
		order.setTotalPrice(total);
		order.setSubTotal(total);
		return order;
	}

	private void mockHappyPaymentPath() throws Exception
	{
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));
		when(checkoutFacade.createPaymentSubscription(any())).thenReturn(new CCPaymentInfoData());
		when(checkoutFacade.authorizePayment("123")).thenReturn(true);
		when(checkoutFacade.placeOrder()).thenReturn(orderData());
	}

	@Test
	public void completeRunsMockPaymentPathAndRecordsCompletionAtomically() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE,
				"{\"email\":\"john.doe@thinkshop.com\"}"));
		mockHappyPaymentPath();

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		// The S3 sequence: subscription (default mock Visa) → authorize("123") → placeOrder.
		verify(cartLoaderStrategy).loadCart(CART_CODE);
		final org.mockito.ArgumentCaptor<CCPaymentInfoData> paymentInfo =
			org.mockito.ArgumentCaptor.forClass(CCPaymentInfoData.class);
		verify(checkoutFacade).createPaymentSubscription(paymentInfo.capture());
		assertEquals("4111111111111111", paymentInfo.getValue().getCardNumber());
		assertEquals("billing address defaults to the delivery address",
			"100 Main St", paymentInfo.getValue().getBillingAddress().getLine1());
		verify(checkoutFacade).authorizePayment("123");
		verify(checkoutFacade).placeOrder();

		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(UcpCheckout.STATUS_COMPLETED, checkout.getStatus());
		assertEquals(ORDER_CODE, checkout.getOrder().getId());
		assertEquals("buyer survives onto the completed checkout",
			"john.doe@thinkshop.com", checkout.getBuyer().getEmail());

		// Protocol-state persistence: accept marks in-progress + key, success
		// records status/orderCode/replayable response in one save.
		verify(sessionService).beginCompletion(CHECKOUT_ID, IDEMPOTENCY_KEY);
		final org.mockito.ArgumentCaptor<String> stored = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(sessionService).recordCompletion(eq(CHECKOUT_ID), stored.capture(), eq(ORDER_CODE));
		assertTrue("stored response is the serialized completed checkout",
			stored.getValue().contains("\"completed\"") && stored.getValue().contains(ORDER_CODE));
		verify(sessionService, never()).failCompletion(anyString());
	}

	@Test
	public void completeReplaysStoredResponseWithoutAnyFacadeCalls() throws Exception
	{
		final UcpCheckoutSession completed = session(UcpCheckout.STATUS_COMPLETED, null);
		completed.setIdempotencyKey(IDEMPOTENCY_KEY);
		completed.setOrderCode(ORDER_CODE);
		completed.setCompletionResponseJson("{\"ucp\":{\"version\":\"2026-04-08\",\"status\":\"success\"},"
			+ "\"id\":\"" + CHECKOUT_ID + "\",\"status\":\"completed\","
			+ "\"order\":{\"id\":\"" + ORDER_CODE + "\"}}");
		when(sessionService.get(CHECKOUT_ID)).thenReturn(completed);

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		// The stored response verbatim — never a second placeOrder (S3).
		assertEquals(UcpCheckout.STATUS_COMPLETED, checkout.getStatus());
		assertEquals(ORDER_CODE, checkout.getOrder().getId());
		assertEquals("success", checkout.getUcp().getStatus());
		verify(cartLoaderStrategy, never()).loadCart(anyString());
		verify(checkoutFacade, never()).createPaymentSubscription(any());
		verify(checkoutFacade, never()).placeOrder();
		verify(sessionService, never()).beginCompletion(anyString(), anyString());
		verify(sessionService, never()).recordCompletion(anyString(), anyString(), anyString());
	}

	@Test
	public void completeOnCompletedCheckoutWithDifferentKeyIsUnrecoverable() throws Exception
	{
		final UcpCheckoutSession completed = session(UcpCheckout.STATUS_COMPLETED, null);
		completed.setIdempotencyKey("some-other-key");
		completed.setCompletionResponseJson("{\"status\":\"completed\"}");
		when(sessionService.get(CHECKOUT_ID)).thenReturn(completed);

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("conflict", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(checkoutFacade, never()).placeOrder();
	}

	@Test
	public void completeWithUnknownHandlerIsUnrecoverableAndPlacesNoOrder() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest("acme_real_card"), IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("invalid_request", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		assertTrue("message names the declared handler",
			checkout.getMessages().get(0).getContent().contains("thinkshop_mock_card"));
		verify(cartLoaderStrategy, never()).loadCart(anyString());
		verify(checkoutFacade, never()).placeOrder();
		verify(sessionService, never()).beginCompletion(anyString(), anyString());
	}

	@Test
	public void completeWithoutPaymentInstrumentsIsUnrecoverable() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			new UcpCheckoutRequest(), IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("invalid_request", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(checkoutFacade, never()).placeOrder();
	}

	@Test
	public void completeInvalidCartIsRecoverableAndRollsBackToReadyForComplete() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));
		when(checkoutFacade.createPaymentSubscription(any())).thenReturn(new CCPaymentInfoData());
		when(checkoutFacade.authorizePayment("123")).thenReturn(true);
		when(checkoutFacade.placeOrder()).thenThrow(new InvalidCartException("cart is not calculated"));

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals("the checkout survives", "success", checkout.getUcp().getStatus());
		assertEquals(UcpCheckout.STATUS_READY_FOR_COMPLETE, checkout.getStatus());
		assertEquals("invalid_request", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		assertNull("no order on failure", checkout.getOrder());
		// S5: complete_in_progress → ready_for_complete, key cleared for retry.
		verify(sessionService).beginCompletion(CHECKOUT_ID, IDEMPOTENCY_KEY);
		verify(sessionService).failCompletion(CHECKOUT_ID);
		verify(sessionService, never()).recordCompletion(anyString(), anyString(), anyString());
	}

	@Test
	public void completePaymentSubscriptionFailureIsRecoverablePaymentDeclined() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));
		when(checkoutFacade.createPaymentSubscription(any())).thenReturn(null);

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(UcpCheckout.STATUS_READY_FOR_COMPLETE, checkout.getStatus());
		assertEquals("payment_declined", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(checkoutFacade, never()).placeOrder();
		verify(sessionService).failCompletion(CHECKOUT_ID);
	}

	@Test
	public void completeIgnoresTheMockAuthorizationResultLikeOrderPlaceDoes() throws Exception
	{
		// Found live: the demo platform's mock payment reports a non-ACCEPTED
		// authorization even though placeOrder succeeds — the proprietary
		// order_place handler ignores the boolean, and so do we.
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));
		when(checkoutFacade.createPaymentSubscription(any())).thenReturn(new CCPaymentInfoData());
		when(checkoutFacade.authorizePayment("123")).thenReturn(false);
		when(checkoutFacade.placeOrder()).thenReturn(orderData());

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals(UcpCheckout.STATUS_COMPLETED, checkout.getStatus());
		assertEquals(ORDER_CODE, checkout.getOrder().getId());
		verify(checkoutFacade).placeOrder();
	}

	@Test
	public void completeOnCheckoutThatIsNotReadyIsRecoverable() throws Exception
	{
		// Entry claims ready, but the cart has no destination — derived state wins (S5).
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(UcpCheckout.STATUS_INCOMPLETE, checkout.getStatus());
		assertEquals("not_ready", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_INCOMPLETE);
		verify(sessionService, never()).beginCompletion(anyString(), anyString());
		verify(checkoutFacade, never()).createPaymentSubscription(any());
	}

	@Test(expected = IllegalArgumentException.class)
	public void completeWithoutIdempotencyKeyIsAClientProtocolBug()
	{
		checkoutService.complete(CHECKOUT_ID, completeRequest(HANDLER_ID), null);
	}

	@Test
	public void completeUnknownIdReturnsUnrecoverableNotFound()
	{
		when(sessionService.get("ucp_chk_nope")).thenReturn(null);

		final UcpCheckout checkout = checkoutService.complete("ucp_chk_nope",
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
	}

	@Test
	public void completeOnCanceledCheckoutIsUnrecoverable() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_CANCELED, null));

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("conflict", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(checkoutFacade, never()).placeOrder();
	}

	@Test
	public void completeWhileAnotherCompletionIsInProgressIsRecoverable() throws Exception
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_COMPLETE_IN_PROGRESS, null));

		final UcpCheckout checkout = checkoutService.complete(CHECKOUT_ID,
			completeRequest(HANDLER_ID), IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(checkoutFacade, never()).placeOrder();
		verify(sessionService, never()).beginCompletion(anyString(), anyString());
	}

	@Test
	public void cancelFromIncompleteBecomesCanceled()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_INCOMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckout checkout = checkoutService.cancel(CHECKOUT_ID, IDEMPOTENCY_KEY);

		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(UcpCheckout.STATUS_CANCELED, checkout.getStatus());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_CANCELED);
	}

	@Test
	public void cancelFromReadyForCompleteBecomesCanceled()
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));

		final UcpCheckout checkout = checkoutService.cancel(CHECKOUT_ID, IDEMPOTENCY_KEY);

		assertEquals(UcpCheckout.STATUS_CANCELED, checkout.getStatus());
		verify(sessionService).update(CHECKOUT_ID, CART_CODE, UcpCheckout.STATUS_CANCELED);
	}

	@Test
	public void cancelIsIdempotentOnAnAlreadyCanceledCheckout()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_CANCELED, null));
		when(checkoutFacade.getCheckoutCart()).thenReturn(sessionCart());

		final UcpCheckout checkout = checkoutService.cancel(CHECKOUT_ID, IDEMPOTENCY_KEY);

		// Terminal state re-returned; no second status write.
		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(UcpCheckout.STATUS_CANCELED, checkout.getStatus());
		verify(sessionService, never()).update(anyString(), anyString(), anyString());
	}

	@Test
	public void cancelWithUnloadableCartStillReturnsTheCanceledState()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_CANCELED, null));
		doThrow(new IllegalStateException("cart gone")).when(cartLoaderStrategy).loadCart(CART_CODE);

		final UcpCheckout checkout = checkoutService.cancel(CHECKOUT_ID, IDEMPOTENCY_KEY);

		assertEquals("success", checkout.getUcp().getStatus());
		assertEquals(UcpCheckout.STATUS_CANCELED, checkout.getStatus());
		assertTrue("no line items when the cart is gone", checkout.getLineItems().isEmpty());
	}

	@Test
	public void cancelOnCompletedCheckoutIsUnrecoverable()
	{
		when(sessionService.get(CHECKOUT_ID)).thenReturn(session(UcpCheckout.STATUS_COMPLETED, null));

		final UcpCheckout checkout = checkoutService.cancel(CHECKOUT_ID, IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("conflict", checkout.getMessages().get(0).getCode());
		assertEquals(UcpMessage.SEVERITY_UNRECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(sessionService, never()).update(anyString(), anyString(), anyString());
	}

	@Test
	public void cancelWhileCompletionInProgressIsRecoverable()
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_COMPLETE_IN_PROGRESS, null));

		final UcpCheckout checkout = checkoutService.cancel(CHECKOUT_ID, IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(sessionService, never()).update(anyString(), anyString(), anyString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void cancelWithoutIdempotencyKeyIsAClientProtocolBug()
	{
		checkoutService.cancel(CHECKOUT_ID, " ");
	}

	@Test
	public void cancelUnknownIdReturnsUnrecoverableNotFound()
	{
		when(sessionService.get("ucp_chk_nope")).thenReturn(null);

		final UcpCheckout checkout = checkoutService.cancel("ucp_chk_nope", IDEMPOTENCY_KEY);

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals("not_found", checkout.getMessages().get(0).getCode());
	}

	@Test
	public void updateWhileCompletionInProgressIsRecoverablyRejected()
	{
		when(sessionService.get(CHECKOUT_ID))
			.thenReturn(session(UcpCheckout.STATUS_COMPLETE_IN_PROGRESS, null));

		final UcpCheckout checkout = checkoutService.update(CHECKOUT_ID, itemsRequest("X", 1));

		assertEquals("error", checkout.getUcp().getStatus());
		assertEquals(UcpMessage.SEVERITY_RECOVERABLE, checkout.getMessages().get(0).getSeverity());
		verify(cartLoaderStrategy, never()).loadCart(anyString());
	}

	@Test
	public void getOnCompletedCheckoutReplaysTheStoredCompletionResponse()
	{
		final UcpCheckoutSession completed = session(UcpCheckout.STATUS_COMPLETED, null);
		completed.setIdempotencyKey(IDEMPOTENCY_KEY);
		completed.setOrderCode(ORDER_CODE);
		completed.setCompletionResponseJson("{\"ucp\":{\"version\":\"2026-04-08\",\"status\":\"success\"},"
			+ "\"id\":\"" + CHECKOUT_ID + "\",\"status\":\"completed\","
			+ "\"order\":{\"id\":\"" + ORDER_CODE + "\"}}");
		when(sessionService.get(CHECKOUT_ID)).thenReturn(completed);

		final UcpCheckout checkout = checkoutService.get(CHECKOUT_ID);

		// The consumed cart is never loaded — the stored terminal state IS the checkout.
		assertEquals(UcpCheckout.STATUS_COMPLETED, checkout.getStatus());
		assertEquals(ORDER_CODE, checkout.getOrder().getId());
		verify(cartLoaderStrategy, never()).loadCart(anyString());
	}

	@Test
	public void createWithDestinationDerivesReadyForComplete() throws Exception
	{
		when(cartFacade.addToCart(eq("WIRELESS_GAMING_MOUSE"), eq(1L))).thenReturn(modification(1L));
		when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
		when(checkoutFacade.setDeliveryMode("thinkshop-standard")).thenReturn(true);
		when(checkoutFacade.getCheckoutCart()).thenReturn(withDestination(sessionCart()));
		when(sessionService.create(eq(CART_CODE), eq(UcpCheckout.STATUS_READY_FOR_COMPLETE), any()))
			.thenReturn(session(UcpCheckout.STATUS_READY_FOR_COMPLETE, null));

		final UcpCheckoutRequest request = request("WIRELESS_GAMING_MOUSE");
		final UcpFulfillment fulfillment = new UcpFulfillment();
		fulfillment.setDestination(destination());
		fulfillment.setDeliveryMode("thinkshop-standard");
		request.setFulfillment(fulfillment);

		final UcpCheckout checkout = checkoutService.create(request);

		assertEquals(UcpCheckout.STATUS_READY_FOR_COMPLETE, checkout.getStatus());
		verify(sessionService).create(eq(CART_CODE), eq(UcpCheckout.STATUS_READY_FOR_COMPLETE), any());
	}
}
