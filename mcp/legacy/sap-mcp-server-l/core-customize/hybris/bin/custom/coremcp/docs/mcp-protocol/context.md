# MCP Protocol — Context

## What It Does

The coremcp extension implements a JSON-RPC 2.0 server that speaks the Model Context Protocol (MCP, spec version 2025-11-25) over Streamable HTTP transport. AI agents (Claude Code, React apps, curl) connect to a single OCC endpoint, open a session, discover available tools, and invoke them to interact with SAP Commerce — searching products, managing carts, completing checkout, viewing orders, and looking up customer data.

The server exposes 19 tools covering product catalog, shopping cart, vouchers, checkout, orders, customer profile, promotions, and the knowledge base (the agent service additionally has `ui_action` — 20 handlers total). All tool handlers delegate to existing Commerce facades, so no new business logic is introduced.

## When It's Used

- An AI agent needs to browse or purchase products on behalf of a customer
- A frontend chat widget calls the MCP endpoint to power conversational commerce
- Automated test harnesses exercise the full browse-to-buy flow via JSON-RPC

## Endpoint

```
POST   /occ/v2/{baseSiteId}/mcp   — All JSON-RPC requests
DELETE /occ/v2/{baseSiteId}/mcp   — Session termination
```

Both methods require `ROLE_CUSTOMERGROUP` or `ROLE_TRUSTED_CLIENT` (enforced by `@Secured` on `McpController`). OAuth2 token validation is handled by Spring Security's filter chain before the controller is reached.

## JSON-RPC Method Routing

The controller parses the JSON-RPC envelope, then routes by `method`:

| Method | Handler | Returns |
|--------|---------|---------|
| `initialize` | `McpDispatcherService.handleInitialize()` | 200 + `MCP-Session-Id` header + capabilities/serverInfo/instructions |
| `notifications/initialized` | Dispatcher returns `null` | 202 Accepted (no body) |
| `notifications/*` (any) | Dispatcher returns `null` | 202 Accepted (no body) |
| `tools/list` | Dispatcher iterates `toolHandlers` list, calls `getDefinition()` on each | 200 + `{ tools: [...] }` |
| `tools/call` | Dispatcher looks up handler by `params.name` in `toolHandlerMap`, calls `execute(arguments)` | 200 + `{ content: [{type:"text", text:"..."}] }` |
| Anything else | | JSON-RPC error -32601 (Method not found) |

## Session Lifecycle

1. **Create** — Client sends `initialize` with optional `clientInfo` and `protocolVersion`. The session service generates a `sess_` + 12-char UUID ID, stores the session, and returns the ID in the `MCP-Session-Id` response header. The backing store is selected at boot by `coremcp.session.store`: **`persistent`** (default — `McpSessionEntry` items in the DB via `PersistedMcpSessionService`; cluster-safe, survives restarts) or `memory` (in-process `ConcurrentHashMap`, single-node; for tests and pre-`yupdatesystem` bootstraps). `DelegatingMcpSessionService` does the selection.

2. **Active** — Every subsequent request must include the `MCP-Session-Id` header. Reads refresh `lastAccessedAt` (TTL: `coremcp.session.ttl.minutes`, default 30). The controller loads the session's cart (if any) via `cartLoaderStrategy` before dispatching, and persists the cart code back through `McpSessionService.updateCartCode()` after dispatch — never by mutating the returned DTO, which would be lost by the DB-backed store.

3. **Terminated** — Client sends `DELETE` with the session header; `removeSession()` deletes it. Expired sessions are evicted lazily on access, and the `mcpSessionCleanupCronJob` sweeps abandoned ones every 30 minutes. Any further requests with a removed/expired ID get a -32600 error.

## Tool Registry

`DefaultMcpDispatcherService` receives a `List<McpToolHandler>` via Spring injection. On `@PostConstruct`, it builds a `Map<String, McpToolHandler>` keyed by `getName()`. The tool list is static (`listChanged: false` in capabilities).

### Registered Tools (20)

**Product** (2): `product_search`, `product_get`
**Cart** (6): `cart_get`, `cart_add_product`, `cart_update_entry`, `cart_remove_entry`, `cart_apply_voucher`, `cart_remove_voucher`
**Checkout** (3): `checkout_set_delivery_address`, `checkout_set_delivery_mode`, `checkout_set_payment`
**Order** (3): `order_get`, `order_history`, `order_place`
**Customer** (2): `customer_get`, `customer_lookup`
**Promotions** (1): `promotions_get`
**Knowledge** (2): `info_get`, `info_search`
**UI Actions** (1): `ui_action`

Note: `ui_action` is registered on the AgentService's tool list but NOT on the MCP dispatcher's tool list. The MCP dispatcher has 19 tools; the agent service has all 20.

## Key Decisions

1. **Persistent sessions by default** — MCP clients identify sessions via the `MCP-Session-Id` header (not cookies), so CCv2's cookie-based sticky routing cannot keep a conversation on one node. Sessions are therefore persisted as `McpSessionEntry` items (DB-backed, cluster-safe, survive rolling deploys); the original in-memory store remains available via `coremcp.session.store=memory` for tests and local bootstraps. See [ADR 0002](../../../../../../docs/adr/0002-persisted-mcp-session-store.md).

2. **Strategy pattern for tools** — Each tool is a separate `McpToolHandler` implementation. Tools are independently testable, new tools are added by implementing the interface and registering a bean, and the dispatcher has no knowledge of individual tool logic. Tools can be overridden via the SAP Commerce alias pattern.

3. **Cart loading per request** — The controller reads `session.getCartCode()`, loads it via `cartLoaderStrategy`, and writes it back after dispatch. This bridges MCP's stateless HTTP transport with Commerce's thread-local cart model. If the stored cart cannot be loaded (e.g., after order placement removes it), the session's cart code is cleared.

4. **No SSE yet** — The spec supports GET for server-sent events, but all Commerce facade calls are synchronous, so every response is a single JSON payload (`Content-Type: application/json`). SSE can be added later for long-running operations.

5. **Delegates to existing facades** — No new DAO or service-layer business logic. Every tool handler calls a platform Commerce facade (`ProductSearchFacade`, `CartFacade`, `CheckoutFacade`, `OrderFacade`, `CustomerFacade`, or the custom `PromotionQueryService`).

6. **Jackson for protocol DTOs** — `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError`, and `McpSession` are hand-written with Jackson annotations. They are internal to coremcp and not generated from `*-beans.xml`, because JSON-RPC parsing needs `@JsonProperty`, `@JsonIgnoreProperties`, and `@JsonInclude` control that the beans generator does not provide.

7. **Server instructions** — The `initialize` response includes an `instructions` field that describes ThinkShop's catalog, tool capabilities, and the required checkout flow order. This guides LLM agents on when and how to use the tools.

8. **Tool errors vs. protocol errors** — Per the MCP spec, tool execution failures return `result` with `isError: true` (not a JSON-RPC `error`). The `error` field is reserved for protocol-level problems (bad method, invalid session, parse failure).

## Error Codes

| Code | Constant | Used When |
|------|----------|-----------|
| -32700 | `PARSE_ERROR` | Request body is not valid JSON |
| -32600 | `INVALID_REQUEST` | `jsonrpc` is not `"2.0"`, or session is missing/expired |
| -32601 | `METHOD_NOT_FOUND` | Unknown JSON-RPC method |
| -32602 | `INVALID_PARAMS` | Missing `params`, missing tool `name`, or unknown tool name |
| -32603 | `INTERNAL_ERROR` | (Defined but not currently used by dispatcher; handler exceptions return `isError` tool results instead) |
