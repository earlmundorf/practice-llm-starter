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
import com.ucpcommerce.dto.UcpFulfillment;
import com.ucpcommerce.dto.UcpItemRef;
import com.ucpcommerce.dto.UcpLineItemRequest;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.services.UcpCheckoutSessionService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CartModificationData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;

import java.math.BigDecimal;
import java.util.List;

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

		checkoutService = new DefaultUcpCheckoutService();
		checkoutService.setCartFacade(cartFacade);
		checkoutService.setCheckoutFacade(checkoutFacade);
		checkoutService.setUserFacade(userFacade);
		checkoutService.setCartLoaderStrategy(cartLoaderStrategy);
		checkoutService.setUcpCheckoutSessionService(sessionService);
		checkoutService.setUcpCheckoutMarshaller(marshaller);
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
		assertEquals("invalid_request", checkout.getMessages().get(0).getCode());
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
