package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ucpcommerce.dto.JsonRpcError;
import com.ucpcommerce.dto.JsonRpcRequest;
import com.ucpcommerce.dto.JsonRpcResponse;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import de.hybris.bootstrap.annotations.UnitTest;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class DefaultUcpMcpDispatcherServiceTest
{
	private DefaultUcpMcpDispatcherService dispatcher;

	@Mock
	private UcpTool mockTool;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		when(mockTool.getName()).thenReturn("search_catalog");
		when(mockTool.getDefinition()).thenReturn(Map.of(
			"name", "search_catalog",
			"description", "A test tool",
			"inputSchema", Map.of("type", "object")
		));

		dispatcher = new DefaultUcpMcpDispatcherService();
		dispatcher.setTools(List.of(mockTool));
		dispatcher.init();
	}

	private JsonRpcRequest request(final Object id, final String method, final Map<String, Object> params)
	{
		final JsonRpcRequest req = new JsonRpcRequest();
		req.setJsonrpc("2.0");
		req.setId(id);
		req.setMethod(method);
		req.setParams(params);
		return req;
	}

	@Test
	public void testToolsListReturnsToolDefinitions()
	{
		final JsonRpcResponse response = dispatcher.dispatch(request(1, "tools/list", null));

		assertNotNull(response);
		assertEquals(1, response.getId());
		assertNull(response.getError());

		@SuppressWarnings("unchecked")
		final Map<String, Object> result = (Map<String, Object>) response.getResult();
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
		assertEquals(1, tools.size());
		assertEquals("search_catalog", tools.get(0).get("name"));
	}

	@Test
	public void testToolsCallExecutesToolAndReturnsPayloadAsContent() throws Exception
	{
		when(mockTool.execute(eq(Map.of("query", "laptop")), any(UcpToolContext.class)))
			.thenReturn("{\"ucp\":{\"status\":\"success\"}}");

		final JsonRpcResponse response = dispatcher.dispatch(request(2, "tools/call",
			Map.of("name", "search_catalog", "arguments", Map.of("query", "laptop"))));

		assertNotNull(response);
		assertNull(response.getError());
		assertTrue(response.getResult().toString().contains("{\"ucp\":{\"status\":\"success\"}}"));
		assertTrue("non-error tool results must not carry isError",
			!response.getResult().toString().contains("isError"));
	}

	@Test
	public void testToolsCallParsesUcpMetaIntoContext() throws Exception
	{
		when(mockTool.execute(any(), any(UcpToolContext.class))).thenReturn("{}");

		dispatcher.dispatch(request(3, "tools/call", Map.of(
			"name", "search_catalog",
			"arguments", Map.of("query", "x"),
			"meta", Map.of(
				"ucp-agent", Map.of("profile", "https://agent.example/.well-known/ucp"),
				"idempotency-key", "key-123"))));

		final ArgumentCaptor<UcpToolContext> captor = ArgumentCaptor.forClass(UcpToolContext.class);
		verify(mockTool).execute(any(), captor.capture());
		assertEquals("https://agent.example/.well-known/ucp", captor.getValue().getAgentProfile());
		assertEquals("key-123", captor.getValue().getIdempotencyKey());
	}

	@Test
	public void testToolsCallParsesSdkStyleUnderscoreMeta() throws Exception
	{
		when(mockTool.execute(any(), any(UcpToolContext.class))).thenReturn("{}");

		dispatcher.dispatch(request(4, "tools/call", Map.of(
			"name", "search_catalog",
			"_meta", Map.of("idempotency-key", "key-456"))));

		final ArgumentCaptor<UcpToolContext> captor = ArgumentCaptor.forClass(UcpToolContext.class);
		verify(mockTool).execute(any(), captor.capture());
		assertEquals("key-456", captor.getValue().getIdempotencyKey());
		assertNull(captor.getValue().getAgentProfile());
	}

	@Test
	public void testToolsCallWithoutMetaYieldsEmptyContext() throws Exception
	{
		when(mockTool.execute(any(), any(UcpToolContext.class))).thenReturn("{}");

		dispatcher.dispatch(request(5, "tools/call", Map.of("name", "search_catalog")));

		final ArgumentCaptor<UcpToolContext> captor = ArgumentCaptor.forClass(UcpToolContext.class);
		verify(mockTool).execute(any(), captor.capture());
		assertNull(captor.getValue().getIdempotencyKey());
		assertNull(captor.getValue().getUcpAgent());
	}

	@Test
	public void testToolsCallUnknownToolReturnsInvalidParams()
	{
		final JsonRpcResponse response = dispatcher.dispatch(request(6, "tools/call",
			Map.of("name", "nonexistent_tool")));

		assertNotNull(response.getError());
		assertEquals(JsonRpcError.INVALID_PARAMS, response.getError().getCode());
	}

	@Test
	public void testToolsCallMissingParamsReturnsInvalidParams()
	{
		final JsonRpcResponse response = dispatcher.dispatch(request(7, "tools/call", null));

		assertNotNull(response.getError());
		assertEquals(JsonRpcError.INVALID_PARAMS, response.getError().getCode());
	}

	@Test
	public void testToolsCallMissingToolNameReturnsInvalidParams()
	{
		final JsonRpcResponse response = dispatcher.dispatch(request(8, "tools/call",
			Map.of("arguments", Map.of())));

		assertNotNull(response.getError());
		assertEquals(JsonRpcError.INVALID_PARAMS, response.getError().getCode());
	}

	@Test
	public void testToolExceptionBecomesIsErrorToolResult() throws Exception
	{
		when(mockTool.execute(any(), any(UcpToolContext.class))).thenThrow(new RuntimeException("boom"));

		final JsonRpcResponse response = dispatcher.dispatch(request(9, "tools/call",
			Map.of("name", "search_catalog")));

		assertNull("unexpected tool failures are tool results, not JSON-RPC errors", response.getError());
		assertTrue(response.getResult().toString().contains("boom"));
		assertTrue(response.getResult().toString().contains("isError"));
	}

	@Test
	public void testInitializeIsToleratedStatelessly()
	{
		final JsonRpcResponse response = dispatcher.dispatch(request(10, "initialize",
			Map.of("protocolVersion", "2025-11-25", "clientInfo", Map.of("name", "generic-client"))));

		assertNotNull(response);
		assertNull(response.getError());
		@SuppressWarnings("unchecked")
		final Map<String, Object> result = (Map<String, Object>) response.getResult();
		assertEquals("2025-11-25", result.get("protocolVersion"));
		assertNotNull(result.get("serverInfo"));
	}

	@Test
	public void testNotificationsReturnNull()
	{
		assertNull(dispatcher.dispatch(request(null, "notifications/initialized", null)));
		assertNull(dispatcher.dispatch(request(null, "notifications/custom", null)));
	}

	@Test
	public void testUnknownMethodReturnsMethodNotFound()
	{
		final JsonRpcResponse response = dispatcher.dispatch(request(11, "unknown/method", null));

		assertNotNull(response.getError());
		assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.getError().getCode());
	}
}
