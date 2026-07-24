package com.ucpcommerce.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpOrderResponse;
import com.ucpcommerce.dto.UcpOrdersResponse;
import com.ucpcommerce.services.UcpOrderService;

import de.hybris.bootstrap.annotations.UnitTest;

import java.lang.reflect.Field;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;


/**
 * REST-binding adapter test (Phase 7): request → service parameter mapping
 * only — the order capability itself is covered by
 * {@code DefaultUcpOrderServiceTest}.
 */
@UnitTest
public class UcpOrderRestControllerTest
{
	private UcpOrderRestController controller;
	private UcpOrderService ucpOrderService;
	private HttpServletResponse response;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp() throws Exception
	{
		controller = new UcpOrderRestController();
		ucpOrderService = mock(UcpOrderService.class);
		response = mock(HttpServletResponse.class);

		final Field field = UcpOrderRestController.class.getDeclaredField("ucpOrderService");
		field.setAccessible(true);
		field.set(controller, ucpOrderService);
	}

	private static UcpEnvelope success()
	{
		final UcpEnvelope envelope = new UcpEnvelope("2026-04-08");
		envelope.setStatus("success");
		return envelope;
	}

	@Test
	public void getOrderPassesThePathIdThrough() throws Exception
	{
		final UcpOrderResponse payload = new UcpOrderResponse();
		payload.setUcp(success());
		when(ucpOrderService.getOrder("00005004")).thenReturn(payload);

		final String body = controller.getOrder("00005004", response);

		verify(ucpOrderService).getOrder("00005004");
		assertEquals("success", objectMapper.readTree(body).path("ucp").path("status").asText());
		verify(response, never()).setStatus(anyInt());
	}

	@Test
	public void listOrdersAppliesTheMcpToolDefaults() throws Exception
	{
		final UcpOrdersResponse payload = new UcpOrdersResponse();
		payload.setUcp(success());
		when(ucpOrderService.history(0, 10, null)).thenReturn(payload);

		controller.listOrders(null, null, null, response);

		verify(ucpOrderService).history(0, 10, null);
	}

	@Test
	public void listOrdersMapsPagingAndTheStatusesCsv() throws Exception
	{
		final UcpOrdersResponse payload = new UcpOrdersResponse();
		payload.setUcp(success());
		when(ucpOrderService.history(1, 5, List.of("COMPLETED", "CANCELLED"))).thenReturn(payload);

		controller.listOrders("1", "5", "COMPLETED,CANCELLED", response);

		verify(ucpOrderService).history(1, 5, List.of("COMPLETED", "CANCELLED"));
	}

	@Test
	public void listOrdersRejectsANonIntegerPageSizeAs400() throws Exception
	{
		final String body = controller.listOrders("0", "lots", null, response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		verify(ucpOrderService, never()).history(anyInt(), anyInt(), any());
		assertEquals("invalid_request",
			objectMapper.readTree(body).path("messages").path(0).path("code").asText());
	}
}
