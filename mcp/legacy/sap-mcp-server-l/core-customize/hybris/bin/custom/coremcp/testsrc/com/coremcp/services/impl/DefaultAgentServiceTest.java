package com.coremcp.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coremcp.services.LlmClient;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.order.CartFacade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;


/**
 * Pins the agent-loop behavior end to end (orchestrator + real tool invoker,
 * entity-ref collector, and state-snapshot builder; mocked LLM and facades):
 * single-turn replies, tool execution, duplicate skipping, the iteration cap,
 * history summarization, entity refs, ui_action capture, and streaming events.
 */
@UnitTest
public class DefaultAgentServiceTest
{
	private DefaultAgentService agentService;
	private LlmClient llmClient;
	private McpToolHandler productGetHandler;
	private McpToolHandler uiActionHandler;

	@Before
	public void setUp()
	{
		llmClient = mock(LlmClient.class);
		when(llmClient.supportsVision()).thenReturn(true);

		productGetHandler = handler("product_get");
		uiActionHandler = handler("ui_action");

		final CartFacade cartFacade = mock(CartFacade.class);
		when(cartFacade.hasSessionCart()).thenReturn(false);
		final CustomerFacade customerFacade = mock(CustomerFacade.class);
		when(customerFacade.getCurrentCustomer()).thenReturn(null);

		final DefaultAgentStateSnapshotBuilder snapshotBuilder = new DefaultAgentStateSnapshotBuilder();
		snapshotBuilder.setCartFacade(cartFacade);
		snapshotBuilder.setCustomerFacade(customerFacade);

		final DefaultAgentToolInvoker toolInvoker = new DefaultAgentToolInvoker();
		toolInvoker.setToolHandlers(List.of(productGetHandler, uiActionHandler));
		toolInvoker.setEntityRefCollector(new DefaultEntityRefCollector());
		toolInvoker.init();

		agentService = new DefaultAgentService();
		agentService.setLlmClient(llmClient);
		agentService.setToolHandlers(List.of(productGetHandler, uiActionHandler));
		agentService.setStateSnapshotBuilder(snapshotBuilder);
		agentService.setToolInvoker(toolInvoker);
		agentService.init();
	}

	private McpToolHandler handler(final String name)
	{
		final McpToolHandler h = mock(McpToolHandler.class);
		when(h.getName()).thenReturn(name);
		when(h.getDescription()).thenReturn("test tool " + name);
		when(h.getInputSchema()).thenReturn(Map.of("type", "object"));
		when(h.execute(any())).thenReturn(McpToolResult.success("{\"ok\":true}"));
		return h;
	}

	private Map<String, Object> textResponse(final String content)
	{
		final Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "assistant");
		message.put("content", content);
		final Map<String, Object> choice = new LinkedHashMap<>();
		choice.put("message", message);
		choice.put("finish_reason", "stop");
		return Map.of("choices", List.of(choice));
	}

	private Map<String, Object> toolCallResponse(final String id, final String name, final String argsJson)
	{
		final Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "assistant");
		message.put("content", "");
		message.put("tool_calls", new ArrayList<>(List.of(Map.of(
			"id", id,
			"type", "function",
			"function", Map.of("name", name, "arguments", argsJson)))));
		final Map<String, Object> choice = new LinkedHashMap<>();
		choice.put("message", message);
		choice.put("finish_reason", "tool_calls");
		return Map.of("choices", List.of(choice));
	}

	private List<Map<String, Object>> userMessage(final String text)
	{
		return List.of(Map.of("role", "user", "content", text));
	}

	@Test
	public void singleTurnReturnsReplyAndHistory()
	{
		final Map<String, Object> reply = textResponse("Hello there!");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(reply);

		final Map<String, Object> result = agentService.chat(userMessage("hi"));

		assertEquals("Hello there!", result.get("reply"));
		// History excludes the two system messages: user message + assistant reply.
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		assertEquals(2, messages.size());
		assertNull(result.get("entityRefs"));
		assertNull(result.get("action"));
	}

	@Test
	public void toolCallExecutesHandlerAndCollectsEntityRefs()
	{
		final Map<String, Object> toolTurn = toolCallResponse("call_1", "product_get", "{\"code\":\"LAPTOP_PRO_15\"}");
		final Map<String, Object> finalTurn = textResponse("That laptop is great.");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(toolTurn, finalTurn);

		final Map<String, Object> result = agentService.chat(userMessage("tell me about the laptop"));

		assertEquals("That laptop is great.", result.get("reply"));
		verify(productGetHandler, times(1)).execute(any());
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> refs = (List<Map<String, String>>) result.get("entityRefs");
		assertEquals(1, refs.size());
		assertEquals("product", refs.get(0).get("type"));
		assertEquals("LAPTOP_PRO_15", refs.get(0).get("code"));
	}

	@Test
	public void duplicateToolCallIsNotReExecutedAndLoopBreaks()
	{
		final Map<String, Object> sameCall1 = toolCallResponse("call_1", "product_get", "{\"code\":\"X\"}");
		final Map<String, Object> sameCall2 = toolCallResponse("call_2", "product_get", "{\"code\":\"X\"}");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(sameCall1, sameCall2);

		final Map<String, Object> result = agentService.chat(userMessage("loop please"));

		// Handler ran only for the first call; the second identical call broke the loop.
		verify(productGetHandler, times(1)).execute(any());
		assertTrue(((String) result.get("reply")).contains("trouble completing your request"));
	}

	@Test
	public void iterationCapProducesFallbackReply()
	{
		agentService.setMaxToolIterations(3);
		final Map<String, Object> call1 = toolCallResponse("c1", "product_get", "{\"code\":\"A\"}");
		final Map<String, Object> call2 = toolCallResponse("c2", "product_get", "{\"code\":\"B\"}");
		final Map<String, Object> call3 = toolCallResponse("c3", "product_get", "{\"code\":\"C\"}");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(call1, call2, call3);

		final Map<String, Object> result = agentService.chat(userMessage("keep going"));

		verify(productGetHandler, times(3)).execute(any());
		assertTrue(((String) result.get("reply")).contains("trouble completing your request"));
		// Entity refs collected along the way are still returned.
		@SuppressWarnings("unchecked")
		final List<Map<String, String>> refs = (List<Map<String, String>>) result.get("entityRefs");
		assertEquals(3, refs.size());
	}

	@Test
	public void longToolResultsAreSummarizedWithPreservedDeepLinks()
	{
		final StringBuilder big = new StringBuilder("{\"url\":\"https://shop/p/123\",\"data\":\"");
		big.append("x".repeat(400)).append("\"}");
		when(productGetHandler.execute(any())).thenReturn(McpToolResult.success(big.toString()));

		final Map<String, Object> toolTurn = toolCallResponse("call_1", "product_get", "{\"code\":\"P1\"}");
		final Map<String, Object> finalTurn = textResponse("done");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(toolTurn, finalTurn);

		final Map<String, Object> result = agentService.chat(userMessage("show me"));

		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		final Map<String, Object> toolMessage = messages.stream()
			.filter(m -> "tool".equals(m.get("role"))).findFirst().orElseThrow();
		final String content = (String) toolMessage.get("content");
		assertTrue(content.startsWith("[previous tool result summarized;"));
		assertTrue(content.contains("preserved deep links: https://shop/p/123"));
	}

	@Test
	public void shortToolResultsAreReturnedVerbatim()
	{
		final Map<String, Object> toolTurn = toolCallResponse("call_1", "product_get", "{\"code\":\"P1\"}");
		final Map<String, Object> finalTurn = textResponse("done");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(toolTurn, finalTurn);

		final Map<String, Object> result = agentService.chat(userMessage("show me"));

		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		final Map<String, Object> toolMessage = messages.stream()
			.filter(m -> "tool".equals(m.get("role"))).findFirst().orElseThrow();
		assertEquals("{\"ok\":true}", toolMessage.get("content"));
	}

	@Test
	public void unknownToolProducesToolErrorMessageNotException()
	{
		final Map<String, Object> toolTurn = toolCallResponse("call_1", "nonexistent_tool", "{}");
		final Map<String, Object> finalTurn = textResponse("recovered");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(toolTurn, finalTurn);

		final Map<String, Object> result = agentService.chat(userMessage("do something odd"));

		assertEquals("recovered", result.get("reply"));
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		final Map<String, Object> toolMessage = messages.stream()
			.filter(m -> "tool".equals(m.get("role"))).findFirst().orElseThrow();
		assertEquals("Unknown tool: nonexistent_tool", toolMessage.get("content"));
	}

	@Test
	public void failingHandlerProducesToolErrorAndTurnContinues()
	{
		when(productGetHandler.execute(any())).thenThrow(new RuntimeException("backend down"));
		final Map<String, Object> toolTurn = toolCallResponse("call_1", "product_get", "{\"code\":\"P1\"}");
		final Map<String, Object> finalTurn = textResponse("sorry about that");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(toolTurn, finalTurn);

		final Map<String, Object> result = agentService.chat(userMessage("show me"));

		assertEquals("sorry about that", result.get("reply"));
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
		final Map<String, Object> toolMessage = messages.stream()
			.filter(m -> "tool".equals(m.get("role"))).findFirst().orElseThrow();
		assertTrue(((String) toolMessage.get("content")).startsWith("Tool error:"));
	}

	@Test
	public void uiActionIsCapturedOnResult()
	{
		final Map<String, Object> toolTurn = toolCallResponse("call_1", "ui_action", "{\"action\":\"checkout\"}");
		final Map<String, Object> finalTurn = textResponse("Taking you to checkout.");
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(toolTurn, finalTurn);

		final Map<String, Object> result = agentService.chat(userMessage("buy it"));

		assertEquals("checkout", result.get("action"));
	}

	@Test
	public void streamingPathUsesStreamClientAndEmitsToolEvents()
	{
		final Map<String, Object> toolTurn = toolCallResponse("call_1", "product_get", "{\"code\":\"P1\"}");
		final Map<String, Object> finalTurn = textResponse("streamed reply");
		when(llmClient.chatCompletionStream(anyList(), anyList(), any())).thenReturn(toolTurn, finalTurn);

		final List<String> deltas = new ArrayList<>();
		final List<String> toolEvents = new ArrayList<>();
		final Map<String, Object> result = agentService.chatStream(userMessage("hi"), deltas::add, toolEvents::add);

		assertEquals("streamed reply", result.get("reply"));
		assertEquals(List.of("product_get"), toolEvents);
		verify(llmClient, times(2)).chatCompletionStream(anyList(), anyList(), any());
	}

	@Test
	public void uiActionDoesNotEmitToolEvent()
	{
		final Map<String, Object> toolTurn = toolCallResponse("call_1", "ui_action", "{\"action\":\"checkout\"}");
		final Map<String, Object> finalTurn = textResponse("ok");
		when(llmClient.chatCompletionStream(anyList(), anyList(), any())).thenReturn(toolTurn, finalTurn);

		final List<String> toolEvents = new ArrayList<>();
		agentService.chatStream(userMessage("buy"), s -> { }, toolEvents::add);

		assertTrue(toolEvents.isEmpty());
	}

	@Test
	public void malformedLlmResponseThrows()
	{
		when(llmClient.chatCompletion(anyList(), anyList())).thenReturn(Map.of("choices", List.of()));

		try
		{
			agentService.chat(userMessage("hi"));
			assertFalse("expected exception for empty choices", true);
		}
		catch (final IllegalStateException expected)
		{
			assertTrue(expected.getMessage().contains("No choices"));
		}
	}
}
