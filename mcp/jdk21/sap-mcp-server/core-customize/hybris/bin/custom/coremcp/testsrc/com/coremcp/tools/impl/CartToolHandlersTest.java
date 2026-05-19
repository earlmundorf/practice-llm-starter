package com.coremcp.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.coremcp.tools.McpToolResult;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class CartToolHandlersTest
{
	private CartGetToolHandler cartGetHandler;
	private CartAddProductToolHandler cartAddHandler;

	@Mock
	private CartFacade cartFacade;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		cartGetHandler = new CartGetToolHandler();
		cartGetHandler.setCartFacade(cartFacade);

		cartAddHandler = new CartAddProductToolHandler();
		cartAddHandler.setCartFacade(cartFacade);
	}

	@Test
	public void testCartGetName()
	{
		assertEquals("cart_get", cartGetHandler.getName());
	}

	@Test
	public void testCartAddName()
	{
		assertEquals("cart_add_product", cartAddHandler.getName());
	}

	@Test
	public void testCartAddSchemaRequiresProductCode()
	{
		final Map<String, Object> schema = cartAddHandler.getInputSchema();

		@SuppressWarnings("unchecked")
		final List<String> required = (List<String>) schema.get("required");
		assertTrue(required.contains("productCode"));
	}

	@Test
	public void testCartGetReturnsErrorOnException()
	{
		when(cartFacade.getSessionCart()).thenThrow(new RuntimeException("No session"));

		final McpToolResult result = cartGetHandler.execute(Map.of());

		assertTrue(result.isError());
		assertTrue(result.getContent().contains("No session"));
	}

	@Test
	public void testCartAddReturnsErrorOnException() throws Exception
	{
		when(cartFacade.addToCart(anyString(), anyLong()))
			.thenThrow(new RuntimeException("Product not found"));

		final McpToolResult result = cartAddHandler.execute(Map.of("productCode", "BAD_CODE"));

		assertTrue(result.isError());
		assertTrue(result.getContent().contains("Product not found"));
	}

	@Test
	public void testCartGetSchemaIsObject()
	{
		final Map<String, Object> schema = cartGetHandler.getInputSchema();
		assertEquals("object", schema.get("type"));
		assertNotNull(schema.get("properties"));
	}
}
