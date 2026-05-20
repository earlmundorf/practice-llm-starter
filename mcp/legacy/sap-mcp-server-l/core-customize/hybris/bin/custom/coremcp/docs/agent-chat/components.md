# Agent Chat — Components

## Core Files

| File | Location | Purpose |
|------|----------|---------|
| `AgentController.java` | `src/com/coremcp/controllers/` | REST endpoint `POST /{baseSiteId}/agent/chat`. Parses the request body, loads the user's cart into the Hybris session (explicit `cartCode` or `"current"` fallback), delegates to `AgentService.chat()`, attaches the session cart code to the response. Secured to `ROLE_CUSTOMERGROUP` and `ROLE_TRUSTED_CLIENT`. |
| `AgentService.java` | `src/com/coremcp/services/` | Interface with single method: `Map<String, Object> chat(List<Map<String, Object>> messages)` |
| `DefaultAgentService.java` | `src/com/coremcp/services/impl/` | Core orchestration. On `@PostConstruct`, builds a tool handler lookup map and pre-filters tool definition lists per intent (browse, cart, checkout). On each `chat()` call: classifies intent via lightweight OpenAI call, selects filtered tools, prepends system prompt, runs the OpenAI tool loop (up to 10 iterations), captures `ui_action` calls, returns reply + conversation history + optional action. |
| `LlmClient.java` | `src/com/coremcp/services/` | Provider-neutral interface with two overloads of `chatCompletion()`: default model and explicit model override |
| `DefaultLlmClient.java` | `src/com/coremcp/services/impl/` | Routes to the configured provider based on `COREMCP_LLM_PROVIDER` (default `openai`). See `coremcp/docs/llm/README.md` for the provider matrix and env var reference. |
| `LlmProvider.java` | `src/com/coremcp/services/` | Strategy interface — one implementation per vendor (`OpenAiLlmProvider`, `AnthropicLlmProvider`, `OpenAiCompatibleLlmProvider`) |
| `AbstractOpenAiCompatibleLlmProvider.java` | `src/com/coremcp/services/impl/` | Shared HTTP + JSON plumbing for any provider that speaks the OpenAI `/v1/chat/completions` protocol. Reads `COREMCP_LLM_TIMEOUT_SECONDS`. |

## Tool Handlers Injected into AgentService

The `agentService` bean receives all 16 tool handlers via Spring XML. These are the tools the agent can call during the tool loop:

| # | Bean Name | Tool Name | Purpose |
|---|-----------|-----------|---------|
| 1 | `productSearchToolHandler` | `product_search` | Solr free-text product search |
| 2 | `productGetToolHandler` | `product_get` | Get product details by code |
| 3 | `cartGetToolHandler` | `cart_get` | View current cart contents |
| 4 | `cartAddProductToolHandler` | `cart_add_product` | Add a product to cart |
| 5 | `cartUpdateEntryToolHandler` | `cart_update_entry` | Update cart entry quantity |
| 6 | `cartRemoveEntryToolHandler` | `cart_remove_entry` | Remove entry from cart |
| 7 | `orderGetToolHandler` | `order_get` | Get order details by code |
| 8 | `orderHistoryToolHandler` | `order_history` | List past orders |
| 9 | `customerGetToolHandler` | `customer_get` | Get current customer profile |
| 10 | `customerLookupToolHandler` | `customer_lookup` | Look up customer by criteria |
| 11 | `checkoutSetDeliveryAddressToolHandler` | `checkout_set_delivery_address` | Set delivery address on cart |
| 12 | `checkoutSetDeliveryModeToolHandler` | `checkout_set_delivery_mode` | Set delivery mode on cart |
| 13 | `checkoutSetPaymentToolHandler` | `checkout_set_payment` | Set payment info on cart |
| 14 | `orderPlaceToolHandler` | `order_place` | Place the order |
| 15 | `promotionsGetToolHandler` | `promotions_get` | Query active promotions and coupon redemption data |
| 16 | `uiActionToolHandler` | `ui_action` | Trigger UI-side navigation (e.g., go to checkout page) |

## Intent-Based Tool Filtering

Not all 16 tools are sent to OpenAI on every request. `DefaultAgentService` pre-builds filtered lists at startup:

| Intent | Tools Available | Count |
|--------|----------------|-------|
| **browse** | `product_search`, `product_get`, `cart_add_product`, `customer_get`, `customer_lookup`, `order_history`, `order_get`, `promotions_get` | 8 |
| **cart** | `product_search`, `product_get`, `cart_add_product`, `customer_get`, `customer_lookup`, `order_history`, `order_get`, `cart_get`, `cart_update_entry`, `cart_remove_entry`, `promotions_get` | 11 |
| **checkout** | All 16 tools (full set including checkout + ui_action) | 16 |

## Spring Bean Wiring (coremcp-spring.xml)

```xml
<!-- LLM providers + router -->
<bean id="openAiLlmProvider"          class="com.coremcp.services.impl.OpenAiLlmProvider"/>
<bean id="anthropicLlmProvider"       class="com.coremcp.services.impl.AnthropicLlmProvider"/>
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
    <property name="toolHandlers">
        <list>
            <ref bean="productSearchToolHandler"/>
            <ref bean="productGetToolHandler"/>
            <ref bean="cartGetToolHandler"/>
            <ref bean="cartAddProductToolHandler"/>
            <ref bean="cartUpdateEntryToolHandler"/>
            <ref bean="cartRemoveEntryToolHandler"/>
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

`AgentController` is discovered via component-scan in `coremcp-web-spring.xml` and injects `agentService`, `cartLoaderStrategy`, and `cartService` via `@Resource`.

## Existing Files Used (no changes)

| File | Role |
|------|------|
| `coremcp-web-spring.xml` | Component-scans `com.coremcp.controllers`, picks up `AgentController` automatically |
| `McpToolHandler.java` | Interface implemented by all tool handlers — `getName()`, `getDescription()`, `getInputSchema()`, `execute()` |
| `McpToolResult.java` | Return type from `handler.execute()` — wraps content string |
| All 16 tool handler implementations | Each called by the agent during the tool loop via the handler map |
| `CartLoaderStrategy` (platform) | Loads a cart into the Hybris session by code |
| `CartService` (platform) | Checks for and retrieves the session cart after agent processing |

## Dependency Chain

```
AgentController
  ├─ @Resource agentService
  │    ├─ llmClient (DefaultLlmClient → routes to selected LlmProvider)
  │    │    └─ Chat Completions API (OpenAI / Anthropic / OpenAI-compatible host)
  │    └─ toolHandlers (16 McpToolHandler instances)
  │         ├─ productSearchFacade (Solr)
  │         ├─ productFacade
  │         ├─ cartFacade
  │         ├─ orderFacade
  │         ├─ checkoutFacade
  │         ├─ customerFacade
  │         └─ promotionQueryService
  ├─ @Resource cartLoaderStrategy (platform)
  └─ @Resource cartService (platform)
```
