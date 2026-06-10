# coremcp — MCP Server & AI Agent for SAP Commerce

An SAP Commerce (Hybris) OCC extension that provides:
- **MCP server** — JSON-RPC 2.0 protocol with 19 commerce tools and a DB-persisted, cluster-safe session store
- **AI agent** — multi-provider (OpenAI / Anthropic / OpenAI-compatible) shopping assistant with tool calling, streaming, and per-user rate limiting
- **Visual search** — vision-model image analysis with 3-tier catalog search
- **Knowledge base** — Solr-indexed `KnowledgeEntry` content (policies, events, how-tos) served via public endpoints and agent tools

All features share the same commerce facade layer, OAuth2 auth, and tool handler infrastructure.

## Feature Documentation

Each feature has a dedicated flow directory with context, components, and diagrams:

| Flow | What It Does |
|------|-------------|
| [mcp-protocol/](mcp-protocol/) | JSON-RPC 2.0 MCP server — session lifecycle (persistent store), tool registry, 19 tool handlers |
| [agent-chat/](agent-chat/) | AI shopping assistant — tool loop, streaming, entity refs, multi-provider LLM integration |
| [visual-search/](visual-search/) | Image-based product search — vision model, 3-tier Solr search (OCC endpoint, not MCP) |

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
| [llm-providers.md](llm-providers.md) | LLM provider configuration: models, base URLs, timeouts, retry |
| [llm/README.md](llm/README.md) | LLM client architecture and provider selection |

Operational tunables (session TTL/store, rate limits, retry, vision model, size caps)
are defined with commented defaults in [`project.properties`](../project.properties)
— that file is the configuration reference.

## Tools Quick Reference

19 tools on the MCP dispatcher; the agent service additionally has `ui_action` (20 total).

| Tool | Backed By |
|------|-----------|
| `product_search` | ProductSearchFacade (keyword, category filter, pagination) |
| `product_get` | ProductFacade |
| `cart_get` / `cart_add_product` / `cart_update_entry` / `cart_remove_entry` | CartFacade |
| `cart_apply_voucher` / `cart_remove_voucher` | VoucherFacade |
| `order_get` / `order_history` | OrderFacade |
| `order_place` | CheckoutFacade |
| `customer_get` / `customer_lookup` | CustomerFacade |
| `checkout_set_delivery_address` / `checkout_set_delivery_mode` / `checkout_set_payment` | CheckoutFacade |
| `promotions_get` | PromotionQueryService |
| `info_get` / `info_search` | KnowledgeSearchService (Solr knowledgeIndex) |
| `ui_action` (agent only) | UI navigation hint, never executed server-side |

## Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/{baseSiteId}/mcp` | POST | MCP JSON-RPC requests |
| `/{baseSiteId}/mcp` | DELETE | Terminate MCP session |
| `/{baseSiteId}/agent/chat` | POST | AI agent chat (rate-limited) |
| `/{baseSiteId}/agent/chat/stream` | POST | AI agent chat, SSE streaming (rate-limited) |
| `/{baseSiteId}/agent/capabilities` | GET | Provider capability flags (vision) |
| `/{baseSiteId}/agent/visual-search` | POST | Visual product search (rate-limited) |
| `/{baseSiteId}/info/{uid}` | GET | Knowledge entry by uid (public) |
| `/{baseSiteId}/info/search` | GET | Knowledge search (public) |

MCP and agent endpoints require an OAuth2 Bearer token (`ROLE_CUSTOMERGROUP` or
`ROLE_TRUSTED_CLIENT`); `/info/**` additionally allows anonymous access. Agent
endpoints enforce a per-user rate limit (`coremcp.agent.rateLimit.perMinute`,
default 20) — see [endpoints.md](endpoints.md) for the 429/400 contracts.

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

# Or run the full 21-check suite against the live server:
#   ../scripts/smoke-test.sh  (from core-customize/)
```
