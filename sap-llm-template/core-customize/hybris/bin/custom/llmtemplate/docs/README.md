> **Note:** These docs are sample reference material from the SAP-MCP-Server project. They demonstrate how an MCP server extension was documented and can serve as a pattern for your own extension's documentation.

# llmtemplate — MCP Server for SAP Commerce

An SAP Commerce (Hybris) OCC extension that exposes commerce operations as
**MCP (Model Context Protocol)** tools over HTTP. MCP clients — Claude Code,
a React frontend, or any standards-compliant client — connect to a single
endpoint and discover/invoke tools for product search, order lookup, cart
management, and customer data.

## How It Fits

```
  +------------------+     +-----------------+     +------------------+
  |  coretoolsocc   |     |    llmtemplate      |     | commercewebsvc   |
  |  (REST / OCC)    |     |  (MCP / JSON-RPC)|     |  (standard OCC)  |
  +--------+---------+     +--------+---------+     +--------+---------+
           |                        |                        |
           +----------+-------------+------------------------+
                      |
              +-------v--------+
              | Commerce Facade|
              | Layer (shared) |
              +----------------+

  coretoolsocc  — Custom REST endpoints (e.g., LastOrder)
  llmtemplate       — MCP protocol server, delegates to commerce facades
  commercewebsvc — SAP-provided standard OCC endpoints
```

All three extensions share the same commerce facade layer and OAuth2
authentication. llmtemplate adds no new domain logic — it wraps existing
facade calls in the MCP protocol.

## Tools Quick Reference

| Tool               | Description                        |
|--------------------|------------------------------------|
| `product_search`   | Search products by keyword/category |
| `product_get`      | Get product details by code        |
| `order_get`        | Get order by code                  |
| `order_history`    | Paginated order history            |
| `order_place`      | Place order from current cart      |
| `cart_get`         | Get current session cart           |
| `cart_add_product` | Add product to cart                |
| `checkout_set_delivery_address` | Set shipping address  |
| `checkout_set_delivery_mode`    | Choose shipping method |
| `checkout_set_payment`          | Set payment (mock)    |
| `customer_get`     | Get current customer profile       |
| `customer_lookup`  | Look up customer by UID            |

See [tools.md](tools.md) for full MCP tool definitions with input schemas.

## Documentation

| File | Contents |
|------|----------|
| [mcp-protocol.md](mcp-protocol.md) | MCP spec overview, JSON-RPC format, session lifecycle |
| [endpoints.md](endpoints.md) | Endpoint specification, request/response examples |
| [tools.md](tools.md) | Complete tool definitions with inputSchema |
| [architecture.md](architecture.md) | Component design, request flow, Spring wiring plan |

## Phase Scope

### Phase 1 (current) — Scaffold + Documentation

- Extension scaffold (builds with `ant build`, no Java source yet)
- Registered in `localextensions.xml`
- Complete documentation covering protocol, endpoints, tools, architecture

### Phase 2 — Implementation

- JSON-RPC 2.0 controller (single POST/DELETE endpoint)
- Dispatcher service routing JSON-RPC methods to tool handlers
- In-memory session management
- 12 tool handler implementations delegating to existing commerce facades
- Unit and integration tests

See [architecture.md](architecture.md) for the planned component design and Spring wiring patterns.
