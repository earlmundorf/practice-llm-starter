# UCP MCP binding — context

## What this flow does

Exposes every UCP capability as MCP tools over one stateless JSON-RPC
endpoint:

```
POST /occ/v2/{baseSiteId}/ucp/mcp        @Secured(ROLE_CUSTOMERGROUP, ROLE_TRUSTED_CLIENT)
```

This is the UCP **MCP transport binding** (design R12: MCP first, REST
alongside) — deliberately distinct from coremcp's proprietary dialect at
`/{baseSiteId}/mcp`. Two MCP dialects coexist on this server:

| | Proprietary (`/mcp`, coremcp) | UCP (`/ucp/mcp`, ucpcommerce) |
|---|---|---|
| Sessions | `MCP-Session-Id` header → persisted session + cart preload | **stateless** — no session header ever |
| Tool names | `product_search`, `cart_add_product`, … | `search_catalog`, `create_checkout`, … |
| Per-call metadata | — | `params.meta` (`ucp-agent` profile, `idempotency-key`); `_meta` also accepted |
| Payloads | facade JSON pass-through | UCP objects: `ucp` envelope, integer minor-unit money, `messages[]` |

## When it's used

Every capability operation from a UCP client arrives here as a
`tools/call`. A generic MCP client's `initialize` /
`notifications/initialized` are answered harmlessly (no session is created).

## Key decisions

- **Stateless per the UCP MCP binding spec**: checkout continuity is the
  *client's* job — it remembers `checkout.id` and echoes it as a top-level
  `id` param on later calls (see the ucp-checkout flow).
- **Error taxonomy** (pinned across all phases): UCP *business* errors travel
  inside a normal tool result (`ucp.status="error"` + `messages[]`, never a
  500); *client protocol bugs* (missing required arg, `checkout.id` in a
  payload, missing idempotency key) are `IllegalArgumentException` → MCP
  `isError` tool results; JSON-RPC envelope problems get JSON-RPC error codes.
- **Tools are thin adapters** over binding-agnostic capability services
  (`UcpCatalogService`/`UcpCheckoutService`/`UcpOrderService`) so the Phase 7
  REST binding reuses the identical service layer. The custom
  `com.thinkshop.*` tools wrap coremcp's `PromotionQueryService`/
  `KnowledgeSearchService` directly — that is what the
  `<requires-extension name="coremcp"/>` dependency exists for.
- **13 tools after Phase 6**: 3 catalog + 5 checkout + 2 order + 3 custom
  (see `docs/reference/tools.md`).
