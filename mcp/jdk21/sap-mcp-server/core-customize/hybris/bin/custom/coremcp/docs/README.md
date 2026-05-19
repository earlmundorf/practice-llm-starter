# coremcp — MCP Server & AI Agent for SAP Commerce

An SAP Commerce (Hybris) OCC extension that provides:
- **MCP server** — JSON-RPC 2.0 protocol with 15 commerce tools
- **AI agent** — OpenAI-powered shopping assistant with intent classification and tool calling
- **Visual search** — GPT-4o Vision image analysis with 3-tier catalog search

All three features share the same commerce facade layer, OAuth2 auth, and tool handler infrastructure.

## Feature Documentation

Each feature has a dedicated flow directory with context, components, and diagrams:

| Flow | What It Does |
|------|-------------|
| [mcp-protocol/](mcp-protocol/) | JSON-RPC 2.0 MCP server — session lifecycle, tool registry, 15 tool handlers |
| [agent-chat/](agent-chat/) | AI shopping assistant — intent classification, tool loop, OpenAI integration |
| [visual-search/](visual-search/) | Image-based product search — GPT-4o Vision, 3-tier Solr search (OCC endpoint, not MCP) |

Each directory contains:
- `context.md` — what it does, when it's used, key decisions
- `components.md` — files that implement it, Spring wiring
- `diagram.md` — Mermaid diagrams of request flows and architecture

## Reference Documentation

| File | Contents |
|------|----------|
| [solr.md](solr.md) | Solr index configuration, facets, sort definitions |
| [tools.md](tools.md) | Complete MCP tool definitions with input schemas |
| [endpoints.md](endpoints.md) | HTTP endpoint specs and response examples |

## Tools Quick Reference

| Tool | Facade |
|------|--------|
| `product_search` | ProductSearchFacade |
| `product_get` | ProductFacade |
| `cart_get` | CartFacade |
| `cart_add_product` | CartFacade |
| `cart_update_entry` | CartFacade |
| `cart_remove_entry` | CartFacade |
| `order_get` | OrderFacade |
| `order_history` | OrderFacade |
| `order_place` | CheckoutFacade |
| `customer_get` | CustomerFacade |
| `customer_lookup` | CustomerFacade |
| `checkout_set_delivery_address` | CheckoutFacade |
| `checkout_set_delivery_mode` | CheckoutFacade |
| `checkout_set_payment` | CheckoutFacade |
| `promotions_get` | PromotionQueryService |

## Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/{baseSiteId}/mcp` | POST | MCP JSON-RPC requests |
| `/{baseSiteId}/mcp` | DELETE | Terminate MCP session |
| `/{baseSiteId}/agent/chat` | POST | AI agent chat |
| `/{baseSiteId}/agent/visual-search` | POST | Visual product search |

All endpoints require OAuth2 Bearer token and `@Secured` roles (`ROLE_CUSTOMERGROUP` or `ROLE_TRUSTED_CLIENT`).

## Quick Start

```bash
# 1. Get an OAuth2 token
curl -sk -X POST https://localhost:9002/authorizationserver/oauth/token \
  -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234'

# 2. Initialize MCP session
curl -sk -X POST https://localhost:9002/occ/v2/electronics/mcp \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'

# 3. Search products via MCP
curl -sk -X POST https://localhost:9002/occ/v2/electronics/mcp \
  -H "Authorization: Bearer <token>" \
  -H "MCP-Session-Id: <session-id>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"product_search","arguments":{"query":"laptop","pageSize":5}}}'
```
