package com.ucpcommerce.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpCatalogResponse;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpProductResponse;
import com.ucpcommerce.services.UcpCatalogService;

import de.hybris.bootstrap.annotations.UnitTest;

import java.lang.reflect.Field;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;


/**
 * REST-binding adapter test (Phase 7): request → service parameter mapping and
 * envelope/status wrapping only — the capability service behavior itself is
 * covered by {@code DefaultUcpCatalogServiceTest}.
 */
@UnitTest
public class UcpCatalogRestControllerTest
{
	private UcpCatalogRestController controller;
	private UcpCatalogService ucpCatalogService;
	private HttpServletResponse response;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp() throws Exception
	{
		controller = new UcpCatalogRestController();
		ucpCatalogService = mock(UcpCatalogService.class);
		response = mock(HttpServletResponse.class);

		final Field field = UcpCatalogRestController.class.getDeclaredField("ucpCatalogService");
		field.setAccessible(true);
		field.set(controller, ucpCatalogService);
	}

	private UcpCatalogResponse catalogPayload()
	{
		final UcpCatalogResponse payload = new UcpCatalogResponse();
		final UcpEnvelope envelope = new UcpEnvelope("2026-04-08");
		envelope.setStatus("success");
		payload.setUcp(envelope);
		return payload;
	}

	@Test
	public void searchAppliesTheMcpToolDefaults() throws Exception
	{
		when(ucpCatalogService.search("", 0, 10)).thenReturn(catalogPayload());

		final String body = controller.search("", null, null, response);

		verify(ucpCatalogService).search("", 0, 10);
		final JsonNode root = objectMapper.readTree(body);
		assertEquals("2026-04-08", root.path("ucp").path("version").asText());
		assertEquals("success", root.path("ucp").path("status").asText());
		verify(response, never()).setStatus(anyInt());
		verify(response).setContentType("application/json");
	}

	@Test
	public void searchMapsExplicitQueryParameters() throws Exception
	{
		when(ucpCatalogService.search("laptop", 2, 5)).thenReturn(catalogPayload());

		controller.search("laptop", "2", "5", response);

		verify(ucpCatalogService).search("laptop", 2, 5);
	}

	@Test
	public void searchRejectsANonIntegerPageAs400() throws Exception
	{
		final String body = controller.search("laptop", "two", null, response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		verify(ucpCatalogService, never()).search(anyString(), anyInt(), anyInt());
		final JsonNode root = objectMapper.readTree(body);
		assertEquals("error", root.path("ucp").path("status").asText());
		assertEquals("invalid_request", root.path("messages").path(0).path("code").asText());
		assertEquals("unrecoverable", root.path("messages").path(0).path("severity").asText());
	}

	@Test
	public void lookupSplitsTheCommaSeparatedIdsParameter() throws Exception
	{
		when(ucpCatalogService.lookup(List.of("LAPTOP_PRO_15", "WIRELESS_GAMING_MOUSE")))
			.thenReturn(catalogPayload());

		controller.lookup("LAPTOP_PRO_15, WIRELESS_GAMING_MOUSE", response);

		verify(ucpCatalogService).lookup(List.of("LAPTOP_PRO_15", "WIRELESS_GAMING_MOUSE"));
	}

	@Test
	public void lookupWithoutIdsIsAClientProtocolBug400() throws Exception
	{
		final String body = controller.lookup(null, response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		verify(ucpCatalogService, never()).lookup(anyList());
		assertTrue(objectMapper.readTree(body).path("messages").path(0).path("content").asText()
			.contains("ids"));
	}

	@Test
	public void getProductPassesThePathIdThrough() throws Exception
	{
		final UcpProductResponse payload = new UcpProductResponse();
		final UcpEnvelope envelope = new UcpEnvelope("2026-04-08");
		envelope.setStatus("success");
		payload.setUcp(envelope);
		when(ucpCatalogService.getProduct("LAPTOP_PRO_15")).thenReturn(payload);

		final String body = controller.getProduct("LAPTOP_PRO_15", response);

		verify(ucpCatalogService).getProduct("LAPTOP_PRO_15");
		assertEquals("success", objectMapper.readTree(body).path("ucp").path("status").asText());
	}

	@Test
	public void businessErrorPayloadsStayHttp200() throws Exception
	{
		// Unknown product id is a business error: the service returns an
		// error-envelope payload and the controller must NOT map it to 4xx.
		final UcpProductResponse payload = new UcpProductResponse();
		final UcpEnvelope envelope = new UcpEnvelope("2026-04-08");
		envelope.setStatus("error");
		payload.setUcp(envelope);
		when(ucpCatalogService.getProduct("NO_SUCH_SKU")).thenReturn(payload);

		final String body = controller.getProduct("NO_SUCH_SKU", response);

		verify(response, never()).setStatus(anyInt());
		assertEquals("error", objectMapper.readTree(body).path("ucp").path("status").asText());
	}
}
