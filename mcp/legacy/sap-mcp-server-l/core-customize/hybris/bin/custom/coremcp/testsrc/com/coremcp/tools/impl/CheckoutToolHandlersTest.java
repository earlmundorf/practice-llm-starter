package com.coremcp.tools.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import com.coremcp.tools.McpToolResult;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.order.InvalidCartException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class CheckoutToolHandlersTest
{
	private CheckoutSetDeliveryModeToolHandler deliveryModeHandler;
	private OrderPlaceToolHandler orderPlaceHandler;

	@Mock
	private CheckoutFacade checkoutFacade;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		deliveryModeHandler = new CheckoutSetDeliveryModeToolHandler();
		deliveryModeHandler.setCheckoutFacade(checkoutFacade);

		orderPlaceHandler = new OrderPlaceToolHandler();
		orderPlaceHandler.setCheckoutFacade(checkoutFacade);
	}

	@Test
	public void testDeliveryModeListsModes()
	{
		final DeliveryModeData mode = new DeliveryModeData();
		mode.setCode("standard");
		mode.setName("Standard");
		final PriceData cost = new PriceData();
		cost.setValue(BigDecimal.valueOf(5.99));
		cost.setFormattedValue("$5.99");
		mode.setDeliveryCost(cost);

		org.mockito.Mockito.doReturn(List.of(mode)).when(checkoutFacade).getSupportedDeliveryModes();

		final McpToolResult result = deliveryModeHandler.execute(Map.of());

		assertFalse(result.isError());
		assertTrue(result.getContent().contains("standard"));
		assertTrue(result.getContent().contains("5.99"));
	}

	@Test
	public void testDeliveryModeSetsMode()
	{
		when(checkoutFacade.setDeliveryMode(eq("express"))).thenReturn(true);

		final McpToolResult result = deliveryModeHandler.execute(Map.of("deliveryModeCode", "express"));

		assertFalse(result.isError());
		assertTrue(result.getContent().contains("express"));
	}

	@Test
	public void testOrderPlaceSucceeds() throws Exception
	{
		final OrderData orderData = new OrderData();
		orderData.setCode("00014001");

		when(checkoutFacade.authorizePayment(eq("123"))).thenReturn(true);
		when(checkoutFacade.placeOrder()).thenReturn(orderData);

		final McpToolResult result = orderPlaceHandler.execute(Map.of());

		assertFalse(result.isError());
		assertTrue(result.getContent().contains("00014001"));
	}

	@Test
	public void testOrderPlaceWithCustomCvv() throws Exception
	{
		final OrderData orderData = new OrderData();
		orderData.setCode("00014002");

		when(checkoutFacade.authorizePayment(eq("456"))).thenReturn(true);
		when(checkoutFacade.placeOrder()).thenReturn(orderData);

		final McpToolResult result = orderPlaceHandler.execute(Map.of("securityCode", "456"));

		assertFalse(result.isError());
	}

	@Test
	public void testOrderPlaceReturnsErrorOnInvalidCart() throws Exception
	{
		when(checkoutFacade.authorizePayment(eq("123"))).thenReturn(true);
		when(checkoutFacade.placeOrder()).thenThrow(new InvalidCartException("Cart is empty"));

		final McpToolResult result = orderPlaceHandler.execute(Map.of());

		assertTrue(result.isError());
		assertTrue(result.getContent().contains("invalid"));
	}
}
