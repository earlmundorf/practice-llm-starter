package com.coremcp.controllers;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coremcp.services.AgentRateLimiter;
import com.coremcp.services.AgentService;
import com.coremcp.services.McpCartSessionService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.user.UserService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;


/**
 * Request validation and result shaping for the agent chat endpoints. Config reads
 * are overridden via the controller's protected getters (no platform needed).
 */
@UnitTest
public class AgentControllerTest
{
	/** Test subclass: pins the config-backed getters to test-controlled values. */
	private static class TestAgentController extends AgentController
	{
		private boolean streamingEnabled = true;
		private int maxMessages = 50;

		@Override
		protected boolean isStreamingEnabled()
		{
			return streamingEnabled;
		}

		@Override
		protected int getMaxMessagesPerRequest()
		{
			return maxMessages;
		}
	}

	private TestAgentController controller;
	private AgentService agentService;
	private McpCartSessionService mcpCartSessionService;
	private AgentRateLimiter agentRateLimiter;
	private HttpServletResponse response;

	@Before
	public void setUp() throws Exception
	{
		controller = new TestAgentController();
		agentService = mock(AgentService.class);
		mcpCartSessionService = mock(McpCartSessionService.class);
		agentRateLimiter = mock(AgentRateLimiter.class);
		final UserService userService = mock(UserService.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
		when(userService.getCurrentUser().getUid()).thenReturn("user1");
		when(agentRateLimiter.tryAcquire("user1")).thenReturn(true);

		setField("agentService", agentService);
		setField("mcpCartSessionService", mcpCartSessionService);
		setField("agentRateLimiter", agentRateLimiter);
		setField("userService", userService);

		response = mock(HttpServletResponse.class);
	}

	private void setField(final String name, final Object value) throws Exception
	{
		final Field field = AgentController.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(controller, value);
	}

	@Test
	public void chatRejectsMissingMessages() throws Exception
	{
		final String result = controller.handleChat("{}", response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		assertTrue(result.contains("messages array is required"));
		verify(agentService, never()).chat(anyList());
	}

	@Test
	public void chatRejectsEmptyMessages() throws Exception
	{
		final String result = controller.handleChat("{\"messages\":[]}", response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		assertTrue(result.contains("messages array is required"));
	}

	@Test
	public void chatRejectsTooManyMessages() throws Exception
	{
		controller.maxMessages = 2;
		final String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"a\"},"
			+ "{\"role\":\"assistant\",\"content\":\"b\"},{\"role\":\"user\",\"content\":\"c\"}]}";

		final String result = controller.handleChat(body, response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		assertTrue(result.contains("exceeds maximum of 2"));
		verify(agentService, never()).chat(anyList());
	}

	@Test
	public void chatRejectsWhenRateLimited() throws Exception
	{
		when(agentRateLimiter.tryAcquire("user1")).thenReturn(false);

		final String result = controller.handleChat("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", response);

		verify(response).setStatus(429);
		assertTrue(result.contains("Too many requests"));
		verify(agentService, never()).chat(anyList());
	}

	@Test
	public void chatReturnsAgentResultWithSessionCartCode() throws Exception
	{
		final Map<String, Object> agentResult = new LinkedHashMap<>();
		agentResult.put("reply", "Hello!");
		when(agentService.chat(anyList())).thenReturn(agentResult);
		when(mcpCartSessionService.getSessionCartCode()).thenReturn("CART-42");

		final String result = controller.handleChat("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
			+ "\"cartCode\":\"CART-42\"}", response);

		assertTrue(result.contains("\"reply\":\"Hello!\""));
		assertTrue(result.contains("\"cartCode\":\"CART-42\""));
		verify(mcpCartSessionService).loadCartOrCurrent("CART-42");
	}

	@Test
	public void chatReturnsErrorJsonWhenAgentFails() throws Exception
	{
		when(agentService.chat(anyList())).thenThrow(new RuntimeException("LLM unavailable"));

		final String result = controller.handleChat("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", response);

		verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		assertTrue(result.contains("LLM unavailable"));
	}

	@Test
	public void chatStreamFallsBackToPlainJsonWhenStreamingDisabled() throws Exception
	{
		controller.streamingEnabled = false;
		final Map<String, Object> agentResult = new LinkedHashMap<>();
		agentResult.put("reply", "no stream");
		when(agentService.chat(anyList())).thenReturn(agentResult);

		final StringWriter out = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(out));

		controller.handleChatStream("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", response);

		verify(response).setContentType("application/json");
		assertTrue(out.toString().contains("\"reply\":\"no stream\""));
	}

	@Test
	public void chatStreamRejectsMissingMessagesWithJsonError() throws Exception
	{
		final StringWriter out = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(out));

		controller.handleChatStream("{}", response);

		verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
		assertTrue(out.toString().contains("messages array is required"));
	}

	@Test
	public void chatStreamRejectsWhenRateLimited() throws Exception
	{
		when(agentRateLimiter.tryAcquire("user1")).thenReturn(false);
		final StringWriter out = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(out));

		controller.handleChatStream("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", response);

		verify(response).setStatus(429);
		assertTrue(out.toString().contains("Too many requests"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void chatStreamWritesSseEventsAndDoneEvent() throws Exception
	{
		final Map<String, Object> agentResult = new LinkedHashMap<>();
		agentResult.put("reply", "streamed");
		when(agentService.chatStream(anyList(), org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()))
			.thenAnswer(invocation -> {
				final java.util.function.Consumer<String> deltas = invocation.getArgument(1);
				deltas.accept("hel");
				deltas.accept("lo");
				return agentResult;
			});

		final StringWriter out = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(out));

		controller.handleChatStream("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", response);

		final String sse = out.toString();
		verify(response).setContentType("text/event-stream;charset=UTF-8");
		assertTrue(sse.contains("event: text\ndata: \"hel\""));
		assertTrue(sse.contains("event: text\ndata: \"lo\""));
		assertTrue(sse.contains("event: done\n"));
		assertTrue(sse.contains("\"reply\":\"streamed\""));
	}
}
