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
import com.ucpcommerce.dto.UcpItemRef;
import com.ucpcommerce.dto.UcpLineItemRequest;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.services.UcpCheckoutSessionService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CartModificationData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
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
		when(cartFacade.getSessionCart()).thenReturn(sessionCart());
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
		when(cartFacade.getSessionCart()).thenReturn(sessionCart());
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
		when(cartFacade.getSessionCart()).thenReturn(sessionCart());
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
		when(cartFacade.getSessionCart()).thenReturn(sessionCart());

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
}
