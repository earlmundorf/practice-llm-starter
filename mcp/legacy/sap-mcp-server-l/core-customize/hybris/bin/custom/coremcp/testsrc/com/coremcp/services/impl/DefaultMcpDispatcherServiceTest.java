package com.coremcp.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.coremcp.dto.JsonRpcError;
import com.coremcp.dto.JsonRpcRequest;
import com.coremcp.dto.JsonRpcResponse;
import com.coremcp.dto.McpSession;
import com.coremcp.services.McpDispatcherService.InitializeResult;
import com.coremcp.services.McpSessionService;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;

import de.hybris.bootstrap.annotations.UnitTest;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class DefaultMcpDispatcherServiceTest
{
	private DefaultMcpDispatcherService dispatcher;

	@Mock
	private McpSessionService mcpSessionService;
	@Mock
	private McpToolHandler mockToolHandler;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		when(mockToolHandler.getName()).thenReturn("test_tool");
		when(mockToolHandler.getDefinition()).thenReturn(Map.of(
			"name", "test_tool",
			"description", "A test tool",
			"inputSchema", Map.of("type", "object")
		));

		dispatcher = new DefaultMcpDispatcherService();
		dispatcher.setMcpSessionService(mcpSessionService);
		dispatcher.setToolHandlers(List.of(mockToolHandler));
		dispatcher.init();
	}

	@Test
	public void testDispatchToolsListReturnsTools()
	{
		final JsonRpcRequest request = new JsonRpcRequest();
		request.setId(1);
		request.setMethod("tools/list");

		final JsonRpcResponse response = dispatcher.dispatch(request, null);

		assertNotNull(response);
		assertEquals(1, response.getId());
		assertNull(response.getError());

		@SuppressWarnings("unchecked")
		final Map<String, Object> result = (Map<String, Object>) response.getResult();
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
		assertEquals(1, tools.size());
		assertEquals("test_tool", tools.get(0).get("name"));
	}

	@Test
	public void testDispatchToolsCallExecutesTool()
	{
		when(mockToolHandler.execute(Map.of("key", "val")))
			.thenReturn(McpToolResult.success("{\"ok\":true}"));

		final JsonRpcRequest request = new JsonRpcRequest();
		request.setId(2);
		request.setMethod("tools/call");
		request.setParams(Map.of("name", "test_tool", "arguments", Map.of("key", "val")));

		final JsonRpcResponse response = dispatcher.dispatch(request, null);

		assertNotNull(response);
		assertEquals(2, response.getId());
		assertNull(response.getError());
	}

	@Test
	public void testDispatchToolsCallUnknownToolReturnsError()
	{
		final JsonRpcRequest request = new JsonRpcRequest();
		request.setId(3);
		request.setMethod("tools/call");
		request.setParams(Map.of("name", "nonexistent_tool"));

		final JsonRpcResponse response = dispatcher.dispatch(request, null);

		assertNotNull(response);
		assertNotNull(response.getError());
		assertEquals(JsonRpcError.INVALID_PARAMS, response.getError().getCode());
	}

	@Test
	public void testDispatchToolsCallMissingParamsReturnsError()
	{
		final JsonRpcRequest request = new JsonRpcRequest();
		request.setId(4);
		request.setMethod("tools/call");
		request.setParams(null);

		final JsonRpcResponse response = dispatcher.dispatch(request, null);

		assertNotNull(response);
		assertNotNull(response.getError());
		assertEquals(JsonRpcError.INVALID_PARAMS, response.getError().getCode());
	}

	@Test
	public void testDispatchToolsCallHandlesException()
	{
		when(mockToolHandler.execute(Map.of()))
			.thenThrow(new RuntimeException("boom"));

		final JsonRpcRequest request = new JsonRpcRequest();
		request.setId(5);
		request.setMethod("tools/call");
		request.setParams(Map.of("name", "test_tool"));

		final JsonRpcResponse response = dispatcher.dispatch(request, null);

		assertNotNull(response);
		assertNull(response.getError());
		// Tool errors are returned as tool results with isError flag, not JSON-RPC errors
		assertTrue(response.getResult().toString().contains("boom"));
	}

	@Test
	public void testDispatchNotificationReturnsNull()
	{
		final JsonRpcRequest request = new JsonRpcRequest();
		request.setMethod("notifications/initialized");

		assertNull(dispatcher.dispatch(request, null));
	}

	@Test
	public void testDispatchUnknownNotificationReturnsNull()
	{
		final JsonRpcRequest request = new JsonRpcRequest();
		request.setMethod("notifications/custom");

		assertNull(dispatcher.dispatch(request, null));
	}

	@Test
	public void testDispatchUnknownMethodReturnsError()
	{
		final JsonRpcRequest request = new JsonRpcRequest();
		request.setId(6);
		request.setMethod("unknown/method");

		final JsonRpcResponse response = dispatcher.dispatch(request, null);

		assertNotNull(response);
		assertNotNull(response.getError());
		assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.getError().getCode());
	}

	@Test
	public void testHandleInitializeCreatesSession()
	{
		when(mcpSessionService.createSession(Map.of(), "2025-11-25")).thenReturn("sess_abc123");

		final JsonRpcRequest request = new JsonRpcRequest();
		request.setId(7);
		request.setMethod("initialize");
		request.setParams(Map.of("protocolVersion", "2025-11-25"));

		final InitializeResult result = dispatcher.handleInitialize(request);

		assertEquals("sess_abc123", result.getSessionId());
		assertNotNull(result.getResponse());
		assertNull(result.getResponse().getError());
	}
}
