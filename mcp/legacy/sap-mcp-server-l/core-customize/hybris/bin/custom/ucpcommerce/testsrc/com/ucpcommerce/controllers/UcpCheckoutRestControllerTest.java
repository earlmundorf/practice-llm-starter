package com.ucpcommerce.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpCheckoutRequest;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.services.UcpCheckoutService;

import de.hybris.bootstrap.annotations.UnitTest;

import java.lang.reflect.Field;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;


/**
 * REST-binding adapter test (Phase 7): body/header → service parameter mapping
 * and the protocol-bug → HTTP 400 taxonomy. The checkout lifecycle itself is
 * covered by {@code DefaultUcpCheckoutServiceTest} — nothing here re-tests it.
 */
@UnitTest
public class UcpCheckoutRestControllerTest
{
	private static final String CHECKOUT_ID = "ucp_chk_test123";

	private UcpCheckoutRestController controller;
	private UcpCheckoutService ucpCheckoutService;
	private HttpServletResponse response;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp() throws Exception
	{
		controller = new UcpCheckoutRestController();
		ucpCheckoutService = mock(UcpCheckoutService.class);
		response = mock(HttpServletResponse.class);

		final Field field = UcpCheckoutRestController.class.getDeclaredField("ucpCheckoutService");
		field.setAccessible(true);
		field.set(controller, ucpCheckoutService);
	}

	private static UcpCheckout checkoutPayload(final String status)
	{
		final UcpCheckout checkout = new UcpCheckout();
		final UcpEnvelope envelope = new UcpEnvelope("2026-04-08");
		envelope.setStatus("error".equals(status) ? "error" : "success");
		checkout.setUcp(envelope);
		checkout.setId(CHECKOUT_ID);
		checkout.setStatus("error".equals(status) ? null : status);
		return checkout;
	}

	// ── create ──────────────────────────────────────────────────────────────

	@Test
	public void createMapsTheBodyOntoTheSharedRequestDto() throws Exception
	{
		when(ucpCheckoutService.create(any())).thenReturn(checkoutPayload("incomplete"));

		final String body = controller.create(
			"{\"line_items\":[{\"item\":{\"id\":\"WIRELESS_GAMING_MOUSE\"},\"quantity\":2}],"
				+ "\"buyer\":{\"email\":\"john.doe@thinkshop.com\"}}", response);

		final ArgumentCaptor<UcpCheckoutRequest> captor = ArgumentCaptor.forClass(UcpCheckoutRequest.class);
		verify(ucpCheckoutService).create(captor.capture());
		final UcpCheckoutRequest mapped = captor.getValue();
		assertEquals(1, mapped.getLineItems().size());
		assertEquals("WIRELESS_GAMING_MOUSE", mapped.getLineItems().get(0).getItem().getId());
		assertEquals(Long.valueOf(2), mapped.getLineItems().get(0).getQuantity());
		assertEquals("john.doe@thinkshop.com", mapped.getBuyer().getEmail());

		final JsonNode root = objectMapper.readTree(body);
		assertEquals(CHECKOUT_ID, root.path("id").asText());
		assertEquals("success", root.path("ucp").path("status").asText());
		verify(response, never()).setStatus(anyInt());
		verify(response).setContentType("application/json");
	}

	@Test
	public void createRejectsAPayloadCarryingAnIdAs400() throws Exception
	{
		final String body = controller.create("{\"id\":\"" + CHECKOUT_ID + "\",\"line_items\":[]}", response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		verify(ucpCheckoutService, never()).create(any());
		final JsonNode root = objectMapper.readTree(body);
		assertEquals("error", root.path("ucp").path("status").asText());
		assertEquals("invalid_request", root.path("messages").path(0).path("code").asText());
		assertEquals("unrecoverable", root.path("messages").path(0).path("severity").asText());
	}

	@Test
	public void createRejectsMalformedJsonAs400() throws Exception
	{
		controller.create("this is not json", response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		verify(ucpCheckoutService, never()).create(any());
	}

	// ── get / update ────────────────────────────────────────────────────────

	@Test
	public void getPassesTheCheckoutIdThrough() throws Exception
	{
		when(ucpCheckoutService.get(CHECKOUT_ID)).thenReturn(checkoutPayload("incomplete"));

		final String body = controller.get(CHECKOUT_ID, response);

		verify(ucpCheckoutService).get(CHECKOUT_ID);
		assertEquals(CHECKOUT_ID, objectMapper.readTree(body).path("id").asText());
	}

	@Test
	public void updateMapsThePathIdAndTheBody() throws Exception
	{
		when(ucpCheckoutService.update(eq(CHECKOUT_ID), any())).thenReturn(checkoutPayload("incomplete"));

		controller.update(CHECKOUT_ID,
			"{\"fulfillment\":{\"destination\":{\"city\":\"New York\"},\"delivery_mode\":\"thinkshop-standard\"}}",
			response);

		final ArgumentCaptor<UcpCheckoutRequest> captor = ArgumentCaptor.forClass(UcpCheckoutRequest.class);
		verify(ucpCheckoutService).update(eq(CHECKOUT_ID), captor.capture());
		assertEquals("New York", captor.getValue().getFulfillment().getDestination().getCity());
		assertEquals("thinkshop-standard", captor.getValue().getFulfillment().getDeliveryMode());
	}

	@Test
	public void updateAcceptsAPayloadIdMatchingThePath() throws Exception
	{
		// Corrected rule (ADR 0003): the SDK's CheckoutUpdateRequest carries an
		// id and the official reference client sends it — accepted when it
		// matches the path, stripped before the DTO conversion.
		when(ucpCheckoutService.update(eq(CHECKOUT_ID), any())).thenReturn(checkoutPayload("incomplete"));

		controller.update(CHECKOUT_ID,
			"{\"id\":\"" + CHECKOUT_ID + "\",\"line_items\":[{\"item\":{\"id\":\"LAPTOP_PRO_15\"},\"quantity\":1}]}",
			response);

		final ArgumentCaptor<UcpCheckoutRequest> captor = ArgumentCaptor.forClass(UcpCheckoutRequest.class);
		verify(ucpCheckoutService).update(eq(CHECKOUT_ID), captor.capture());
		assertEquals("LAPTOP_PRO_15", captor.getValue().getLineItems().get(0).getItem().getId());
		verify(response, never()).setStatus(anyInt());
	}

	@Test
	public void updateRejectsAMismatchedPayloadIdAs400() throws Exception
	{
		controller.update(CHECKOUT_ID, "{\"id\":\"ucp_chk_other\"}", response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		verify(ucpCheckoutService, never()).update(anyString(), any());
	}

	// ── complete / cancel — Idempotency-Key header mapping ──────────────────

	@Test
	public void completePassesTheIdempotencyKeyHeaderThrough() throws Exception
	{
		when(ucpCheckoutService.complete(eq(CHECKOUT_ID), any(), eq("key-123")))
			.thenReturn(checkoutPayload("completed"));

		final String body = controller.complete(CHECKOUT_ID,
			"{\"payment\":{\"instruments\":[{\"handler_id\":\"thinkshop_mock_card\",\"type\":\"card\","
				+ "\"credential\":{\"token\":\"tok\"}}]}}",
			"key-123", response);

		final ArgumentCaptor<UcpCheckoutRequest> captor = ArgumentCaptor.forClass(UcpCheckoutRequest.class);
		verify(ucpCheckoutService).complete(eq(CHECKOUT_ID), captor.capture(), eq("key-123"));
		assertEquals("thinkshop_mock_card",
			captor.getValue().getPayment().getInstruments().get(0).getHandlerId());
		assertEquals("completed", objectMapper.readTree(body).path("status").asText());
	}

	@Test
	public void completeWithoutTheHeaderSurfacesTheServiceRejectionAs400() throws Exception
	{
		// The service enforces the idempotency-key rule (Phase 5) — the
		// controller passes the absent header through as null and maps the
		// IllegalArgumentException to HTTP 400.
		when(ucpCheckoutService.complete(eq(CHECKOUT_ID), any(), isNull()))
			.thenThrow(new IllegalArgumentException("idempotency key is required"));

		final String body = controller.complete(CHECKOUT_ID, "{\"payment\":{\"instruments\":[]}}", null, response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		assertTrue(objectMapper.readTree(body).path("messages").path(0).path("content").asText()
			.contains("idempotency"));
	}

	@Test
	public void cancelPassesTheIdempotencyKeyHeaderThrough() throws Exception
	{
		when(ucpCheckoutService.cancel(CHECKOUT_ID, "key-456")).thenReturn(checkoutPayload("canceled"));

		final String body = controller.cancel(CHECKOUT_ID, "key-456", response);

		verify(ucpCheckoutService).cancel(CHECKOUT_ID, "key-456");
		assertEquals("canceled", objectMapper.readTree(body).path("status").asText());
	}

	// ── business errors stay HTTP 200 ───────────────────────────────────────

	@Test
	public void businessErrorPayloadsStayHttp200() throws Exception
	{
		// Unknown checkout id is a business error (unrecoverable not_found in
		// messages[]) — the service returns the payload, the controller must
		// NOT translate it to a 4xx (UCP error-envelope rule, runbook §2.2).
		when(ucpCheckoutService.get("ucp_chk_missing")).thenReturn(checkoutPayload("error"));

		final String body = controller.get("ucp_chk_missing", response);

		verify(response, never()).setStatus(anyInt());
		assertEquals("error", objectMapper.readTree(body).path("ucp").path("status").asText());
	}
}
