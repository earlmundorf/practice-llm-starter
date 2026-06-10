# MCP Protocol — Components

## Controller

| File | Purpose |
|------|---------|
| `src/com/coremcp/controllers/McpController.java` | Single `@Controller` for `POST` and `DELETE` on `/{baseSiteId}/mcp`. Parses JSON-RPC envelope with Jackson `ObjectMapper`, validates `jsonrpc: "2.0"`, routes `initialize` directly, validates `MCP-Session-Id` for all other methods, loads/saves session cart via `cartLoaderStrategy`/`cartService`, delegates to `McpDispatcherService`, returns 202 for notifications or serialized `JsonRpcResponse` for everything else. Secured with `ROLE_CUSTOMERGROUP` and `ROLE_TRUSTED_CLIENT`. |

## Services

| File | Purpose |
|------|---------|
| `src/com/coremcp/services/McpDispatcherService.java` | Interface: `dispatch(request, session)` and `handleInitialize(request)`. Defines the inner class `InitializeResult` that bundles the JSON-RPC response with the new session ID. |
| `src/com/coremcp/services/impl/DefaultMcpDispatcherService.java` | Routes by JSON-RPC method using a `switch` statement: `tools/list` iterates all handlers' `getDefinition()`, `tools/call` looks up a handler by name in `toolHandlerMap` and calls `execute()`, `notifications/*` returns `null`, anything else returns -32601. Builds `toolHandlerMap` (a `Map<String, McpToolHandler>`) from the injected `toolHandlers` list in `@PostConstruct`. The `handleInitialize` method creates a session via `McpSessionService`, returns protocol version, capabilities (`tools.listChanged: false`, `logging: {}`), server info, and instructions. |
| `src/com/coremcp/services/McpSessionService.java` | Interface: `createSession(clientInfo, protocolVersion)`, `getSession(sessionId)`, `removeSession(sessionId)`, `updateCartCode(sessionId, cartCode)` (cart changes must flow through this — store implementations may return detached DTOs). |
| `src/com/coremcp/services/impl/DelegatingMcpSessionService.java` | Selects the backing store at boot via `coremcp.session.store`: `persistent` (default) or `memory`. Wired as the `mcpSessionService` alias target. |
| `src/com/coremcp/services/impl/PersistedMcpSessionService.java` | **Default store.** Persists sessions as `McpSessionEntry` items (typecode 14002, defined in `coremcp-items.xml`) via ModelService/FlexibleSearch — cluster-safe, survives restarts. Lazy TTL eviction on access; `jobs/McpSessionCleanupJob` sweeps abandoned sessions every 30 min (`essentialdata-mcp-session-cleanup.impex`). |
| `src/com/coremcp/services/impl/DefaultMcpSessionService.java` | In-memory store (`ConcurrentHashMap<String, McpSession>`) — single-node; used for unit tests and `coremcp.session.store=memory`. `createSession` generates `sess_` + 12-char truncated UUID. |
| `src/com/coremcp/services/McpCartSessionService.java` | Interface for cart loading/persistence shared between MCP and Agent controllers: `loadCart(cartCode)`, `loadCartOrCurrent(cartCode)`, `getSessionCartCode()`. |
| `src/com/coremcp/services/impl/DefaultMcpCartSessionService.java` | Implementation that wraps `CartLoaderStrategy` and `CartService`. Not directly used by `McpController` (which inlines the cart logic), but available for the Agent controller. |

## DTOs

| File | Purpose |
|------|---------|
| `src/com/coremcp/dto/JsonRpcRequest.java` | Parsed from the POST body. Fields: `jsonrpc` (String), `id` (Object — integer or string), `method` (String), `params` (Map). Has `isNotification()` helper (true when `id` is null). Uses `@JsonIgnoreProperties(ignoreUnknown = true)`. |
| `src/com/coremcp/dto/JsonRpcResponse.java` | Serialized back to the client. Fields: `jsonrpc` (always "2.0"), `id`, `result`, `error`. Factory methods: `success(id, result)`, `error(id, code, message)`, `toolResult(id, content, isError)`. The `toolResult` method wraps content in the MCP content array format `[{type:"text", text:"..."}]` and sets `isError` when true. Uses `@JsonInclude(NON_NULL)` so `result` and `error` are mutually exclusive in output. |
| `src/com/coremcp/dto/JsonRpcError.java` | Error structure with `code` (int) and `message` (String). Defines constants: `PARSE_ERROR` (-32700), `INVALID_REQUEST` (-32600), `METHOD_NOT_FOUND` (-32601), `INVALID_PARAMS` (-32602), `INTERNAL_ERROR` (-32603). |
| `src/com/coremcp/dto/McpSession.java` | Session state POJO. Fields: `id`, `clientInfo` (Map), `protocolVersion`, `createdAt` (Instant), `lastAccessedAt` (Instant), `cartCode` (String). The `touch()` method updates `lastAccessedAt` to `Instant.now()`. The `cartCode` field bridges MCP sessions with Commerce's thread-local cart model. |

## Tool Interface and Result

| File | Purpose |
|------|---------|
| `src/com/coremcp/tools/McpToolHandler.java` | Strategy interface. Methods: `getName()`, `getDescription()`, `getInputSchema()`, `execute(args)`. Default method `getDefinition()` returns a map of `{name, description, inputSchema}` for `tools/list`. |
| `src/com/coremcp/tools/McpToolResult.java` | Immutable result wrapper. Fields: `content` (String — JSON-serialized facade data or error message), `isError` (boolean). Factory methods: `success(content)`, `error(message)`. |

## Tool Handlers

All tool handlers live in `src/com/coremcp/tools/impl/`. Each implements `McpToolHandler`, uses `ObjectMapper` to serialize facade results to JSON, and has a single facade dependency injected via `@Required` setter.

### Product Tools

| Handler | Tool Name | Facade |
|---------|-----------|--------|
| `ProductSearchToolHandler` | `product_search` | `ProductSearchFacade` |
| `ProductGetToolHandler` | `product_get` | `ProductFacade` |

### Cart Tools

| Handler | Tool Name | Facade |
|---------|-----------|--------|
| `CartGetToolHandler` | `cart_get` | `CartFacade` |
| `CartAddProductToolHandler` | `cart_add_product` | `CartFacade` |
| `CartUpdateEntryToolHandler` | `cart_update_entry` | `CartFacade` |
| `CartRemoveEntryToolHandler` | `cart_remove_entry` | `CartFacade` |

### Checkout Tools

| Handler | Tool Name | Facade |
|---------|-----------|--------|
| `CheckoutSetDeliveryAddressToolHandler` | `checkout_set_delivery_address` | `CheckoutFacade` |
| `CheckoutSetDeliveryModeToolHandler` | `checkout_set_delivery_mode` | `CheckoutFacade` |
| `CheckoutSetPaymentToolHandler` | `checkout_set_payment` | `CheckoutFacade` |

### Order Tools

| Handler | Tool Name | Facade |
|---------|-----------|--------|
| `OrderGetToolHandler` | `order_get` | `OrderFacade` |
| `OrderHistoryToolHandler` | `order_history` | `OrderFacade` |
| `OrderPlaceToolHandler` | `order_place` | `CheckoutFacade` |

### Customer Tools

| Handler | Tool Name | Facade |
|---------|-----------|--------|
| `CustomerGetToolHandler` | `customer_get` | `CustomerFacade` |
| `CustomerLookupToolHandler` | `customer_lookup` | `CustomerFacade` |

### Promotions

| Handler | Tool Name | Service |
|---------|-----------|---------|
| `PromotionsGetToolHandler` | `promotions_get` | `PromotionQueryService` (custom service using `FlexibleSearchService`) |

### UI Actions

| Handler | Tool Name | Facade |
|---------|-----------|--------|
| `UIActionToolHandler` | `ui_action` | None — returns a static JSON action payload. Not registered on the MCP dispatcher; only on the AgentService. |

## Spring Bean Wiring

Defined in `resources/coremcp-spring.xml`. Every bean follows the SAP Commerce alias pattern: `defaultXxx` bean ID with an `alias` pointing `xxx` to it, allowing downstream extensions to override.

### Pattern

```xml
<alias name="defaultProductSearchToolHandler" alias="productSearchToolHandler"/>
<bean id="defaultProductSearchToolHandler"
      class="com.coremcp.tools.impl.ProductSearchToolHandler">
    <property name="productSearchFacade" ref="productSearchFacade"/>
</bean>
```

### Dispatcher Wiring

The dispatcher receives all tool handlers as an ordered list:

```xml
<bean id="defaultMcpDispatcherService"
      class="com.coremcp.services.impl.DefaultMcpDispatcherService">
    <property name="mcpSessionService" ref="mcpSessionService"/>
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
        </list>
    </property>
</bean>
```

### Controller Discovery

`McpController` is discovered by Spring component-scan in `coremcp-web-spring.xml` (scans `com.coremcp.controllers`). It injects `mcpDispatcherService`, `mcpSessionService`, `cartLoaderStrategy`, and `cartService` via `@Resource`.
