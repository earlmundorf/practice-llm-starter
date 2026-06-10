# Agent Chat — Components

## Core Files

| File | Location | Purpose |
|------|----------|---------|
| `AgentController.java` | `src/com/coremcp/controllers/` | REST endpoints `POST /{baseSiteId}/agent/chat` (JSON) and `POST /{baseSiteId}/agent/chat/stream` (SSE). Parses request body, loads cart into Hybris session, delegates to `AgentService`, attaches the session cart code, writes either a JSON response or SSE events depending on the route + `coremcp.agent.streaming.enabled`. Secured to `ROLE_CUSTOMERGROUP` and `ROLE_TRUSTED_CLIENT`. |
| `AgentService.java` | `src/com/coremcp/services/` | Interface with two methods: `chat(messages)` (non-streaming) and `chatStream(messages, textDeltaConsumer, toolEventConsumer)`. |
| `DefaultAgentService.java` | `src/com/coremcp/services/impl/` | Orchestration only. On `@PostConstruct`, builds the tool definition list. On each turn: prepends persona prompt + state snapshot (from `AgentStateSnapshotBuilder`), runs the LLM tool loop (up to `coremcp.agent.maxToolIterations`, default 10), delegates each tool call to `AgentToolInvoker`, returns `{reply, messages, action?, entityRefs?}`. Both `chat()` and `chatStream()` share `runChat()`; the streaming path threads the consumers through to `LlmClient.chatCompletionStream`. LLM responses are parsed once into typed `LlmChatResponse`/`LlmToolCall` (`dto/llm/`). |
| `DefaultAgentToolInvoker.java` | `src/com/coremcp/services/impl/` | Executes one tool call: duplicate detection (per-turn `AgentTurnContext`), `ui_action` capture, handler dispatch, entity-ref collection, error containment ("Tool error: …" result, never a failed turn). |
| `DefaultAgentStateSnapshotBuilder.java` | `src/com/coremcp/services/impl/` | Builds the per-turn "CURRENT STATE" system message (customer + cart snapshot) from `CustomerFacade`/`CartFacade`; best-effort, degrades to a smaller snapshot on facade errors. |
| `DefaultEntityRefCollector.java` | `src/com/coremcp/services/impl/` | Extracts product/order entity refs from tool args + results onto the turn context (capped at 5 per call). |
| `LlmClient.java` | `src/com/coremcp/services/` | Provider-neutral interface: `chatCompletion()` (with optional model override) and `chatCompletionStream()` (with text delta consumer). |
| `DefaultLlmClient.java` | `src/com/coremcp/services/impl/` | Routes to the configured provider based on `coremcp.llm.provider` (default `openai`; production default `anthropic`). See `coremcp/docs/llm/README.md`. |
| `LlmProvider.java` | `src/com/coremcp/services/` | Strategy interface — one implementation per vendor (`OpenAiLlmProvider`, `AnthropicLlmProvider`, `OpenAiCompatibleLlmProvider`). The default `chatCompletionStream` calls `chatCompletion` and emits the full reply as one chunk, so vendors that can't stream still satisfy the contract. |
| `AnthropicLlmProvider.java` | `src/com/coremcp/services/impl/` | Anthropic-specific provider. Sends system as an array of content blocks with `cache_control: ephemeral` on the persona block; tags the last tool definition with `cache_control: ephemeral`. Implements true SSE consumption for `chatCompletionStream`, with auto-fallback to non-streaming when the gateway returns non-`text/event-stream` content. Logs `cache_creation_input_tokens` / `cache_read_input_tokens` for observability. |
| `AbstractHttpLlmProvider.java` | `src/com/coremcp/services/impl/` | Base for all HTTP providers: shared HttpClient, request timeouts (`coremcp.llm.timeout.seconds`, `coremcp.llm.stream.timeout.seconds`), and bounded retry with exponential backoff for 429/500/502/503 and connection errors (`coremcp.llm.retry.maxAttempts`, `coremcp.llm.retry.baseDelayMillis`). Streaming responses are never retried after the first delta. |
| `AbstractOpenAiCompatibleLlmProvider.java` | `src/com/coremcp/services/impl/` | Shared JSON plumbing for OpenAI-flavored providers, on top of `AbstractHttpLlmProvider`. |

## Tool Handlers Injected into AgentService

The `agentService` bean receives all 20 tool handlers via Spring XML. The full set is sent to the LLM on every turn (no per-intent filtering).

| # | Bean Name | Tool Name | Purpose |
|---|-----------|-----------|---------|
| 1 | `productSearchToolHandler` | `product_search` | Solr free-text product search. Default `pageSize=5`. |
| 2 | `productGetToolHandler` | `product_get` | Get product details by code. Default options: `BASIC + PRICE + STOCK`. |
| 3 | `cartGetToolHandler` | `cart_get` | View current cart contents |
| 4 | `cartAddProductToolHandler` | `cart_add_product` | Add a product to cart |
| 5 | `cartUpdateEntryToolHandler` | `cart_update_entry` | Update cart entry quantity |
| 6 | `cartRemoveEntryToolHandler` | `cart_remove_entry` | Remove entry from cart |
| 7 | `cartApplyVoucherToolHandler` | `cart_apply_voucher` | Apply a coupon code |
| 8 | `cartRemoveVoucherToolHandler` | `cart_remove_voucher` | Remove a previously applied coupon |
| 9 | `orderGetToolHandler` | `order_get` | Get order details by code |
| 10 | `orderHistoryToolHandler` | `order_history` | List past orders. Default `pageSize=5`. |
| 11 | `customerGetToolHandler` | `customer_get` | Get current customer profile |
| 12 | `customerLookupToolHandler` | `customer_lookup` | Look up customer by criteria |
| 13 | `checkoutSetDeliveryAddressToolHandler` | `checkout_set_delivery_address` | Set delivery address on cart |
| 14 | `checkoutSetDeliveryModeToolHandler` | `checkout_set_delivery_mode` | Set delivery mode on cart |
| 15 | `checkoutSetPaymentToolHandler` | `checkout_set_payment` | Set payment info on cart |
| 16 | `orderPlaceToolHandler` | `order_place` | Place the order |
| 17 | `promotionsGetToolHandler` | `promotions_get` | Query active promotions and coupon redemption data |
| 18 | `infoGetToolHandler` | `info_get` | Get a knowledge entry by uid (policies, events, how-tos) |
| 19 | `infoSearchToolHandler` | `info_search` | Free-text knowledge base search via Solr knowledgeIndex |
| 20 | `uiActionToolHandler` | `ui_action` | Trigger UI-side navigation (e.g., go to checkout page) |

## Entity Reference Collection

`DefaultEntityRefCollector` (invoked by `DefaultAgentToolInvoker` after each tool execution) pulls user-visible identifiers out of the tool result so the chat UI can render clickable chips:

| Tool | Refs emitted |
|------|--------------|
| `product_get` | `{type: "product", code: <args.code>}` |
| `product_search` | `{type: "product", code}` for the first 5 results |
| `order_get` | `{type: "order", code: <args.code>}` |
| `order_history` | `{type: "orderHistory"}` plus `{type: "order", code}` for the first 5 results |

Refs are deduplicated and capped per turn to keep the chip row sane. Other tools produce no refs.

## Spring Bean Wiring (coremcp-spring.xml)

```xml
<!-- LLM providers + router -->
<bean id="openAiLlmProvider"           class="com.coremcp.services.impl.OpenAiLlmProvider"/>
<bean id="anthropicLlmProvider"        class="com.coremcp.services.impl.AnthropicLlmProvider"/>
<bean id="openAiCompatibleLlmProvider" class="com.coremcp.services.impl.OpenAiCompatibleLlmProvider"/>

<alias name="defaultLlmClient" alias="llmClient"/>
<bean id="defaultLlmClient" class="com.coremcp.services.impl.DefaultLlmClient">
    <property name="providers">
        <list>
            <ref bean="openAiLlmProvider"/>
            <ref bean="anthropicLlmProvider"/>
            <ref bean="openAiCompatibleLlmProvider"/>
        </list>
    </property>
</bean>

<!-- Agent Service -->
<alias name="defaultAgentService" alias="agentService"/>
<bean id="defaultAgentService"
      class="com.coremcp.services.impl.DefaultAgentService">
    <property name="llmClient" ref="llmClient"/>
    <property name="cartFacade" ref="cartFacade"/>
    <property name="customerFacade" ref="customerFacade"/>
    <property name="toolHandlers">
        <list>
            <ref bean="productSearchToolHandler"/>
            <ref bean="productGetToolHandler"/>
            <ref bean="cartGetToolHandler"/>
            <ref bean="cartAddProductToolHandler"/>
            <ref bean="cartUpdateEntryToolHandler"/>
            <ref bean="cartRemoveEntryToolHandler"/>
            <ref bean="cartApplyVoucherToolHandler"/>
            <ref bean="cartRemoveVoucherToolHandler"/>
            <ref bean="orderGetToolHandler"/>
            <ref bean="orderHistoryToolHandler"/>
            <ref bean="customerGetToolHandler"/>
            <ref bean="customerLookupToolHandler"/>
            <ref bean="checkoutSetDeliveryAddressToolHandler"/>
            <ref bean="checkoutSetDeliveryModeToolHandler"/>
            <ref bean="checkoutSetPaymentToolHandler"/>
            <ref bean="orderPlaceToolHandler"/>
            <ref bean="promotionsGetToolHandler"/>
            <ref bean="uiActionToolHandler"/>
        </list>
    </property>
</bean>
```

`AgentController` is discovered via component-scan in `coremcp-web-spring.xml` and injects `agentService`, `mcpCartSessionService`, and `llmClient` via `@Resource`.

## Configuration Properties

| Property | Default | Effect |
|----------|---------|--------|
| `coremcp.llm.provider` | `openai` | Selects which `LlmProvider` to use. Production sets this to `anthropic`. |
| `coremcp.llm.timeout.seconds` | `60` | Read timeout on the upstream LLM HTTP request. |
| `coremcp.agent.streaming.enabled` | `true` | Master kill-switch for the SSE endpoint. When `false`, `/agent/chat/stream` returns plain JSON in the same shape as `/agent/chat`. |
| `coremcp.anthropic.model` | `claude-3-5-sonnet-latest` | Main Anthropic chat model (production override: `claude-sonnet-4-6`). |
| `coremcp.anthropic.version` | `2023-06-01` | `anthropic-version` header value. |

## Existing Files Used (no changes)

| File | Role |
|------|------|
| `coremcp-web-spring.xml` | Component-scans `com.coremcp.controllers`, picks up `AgentController` automatically |
| `McpToolHandler.java` | Interface implemented by all tool handlers — `getName()`, `getDescription()`, `getInputSchema()`, `execute()` |
| `McpToolResult.java` | Return type from `handler.execute()` — wraps content string |
| All 20 tool handler implementations | Each called by the agent during the tool loop via the handler map |
| `McpCartSessionService` | Loads a cart into the Hybris session by code or `"current"` |

## Dependency Chain

```
AgentController
  ├─ @Resource agentService
  │    ├─ llmClient (DefaultLlmClient → routes to selected LlmProvider)
  │    │    └─ Anthropic / OpenAI / OpenAI-compatible HTTP API
  │    │       (streaming variant for SSE; default fallback for non-streaming)
  │    ├─ cartFacade  (state snapshot)
  │    ├─ customerFacade  (state snapshot)
  │    └─ toolHandlers (18 McpToolHandler instances)
  │         ├─ productSearchFacade (Solr)
  │         ├─ productFacade
  │         ├─ cartFacade
  │         ├─ orderFacade
  │         ├─ checkoutFacade
  │         ├─ customerFacade
  │         ├─ voucherFacade
  │         └─ promotionQueryService
  ├─ @Resource mcpCartSessionService
  └─ @Resource llmClient (capabilities endpoint)
```
