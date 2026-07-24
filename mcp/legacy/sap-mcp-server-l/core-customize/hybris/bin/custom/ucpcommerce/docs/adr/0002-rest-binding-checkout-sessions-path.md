# 0002 — REST binding: `/checkout-sessions` naming, route map, error taxonomy

**Status:** accepted (2026-07-23)

## Context

Phase 7 adds the REST binding alongside MCP as thin adapters over the same
binding-agnostic capability services (ADR 0001, design R12). Two things had to
be decided against a pinned spec that is **not available locally** (no clone of
the pinned `ucp-schema` repo, no network access to the official `samples`
reference client or `conformance` suite):

1. **`/checkouts` vs `/checkout-sessions`** — the task runbook (§2.2, §9.1)
   flags this as a known ambiguity between spec revisions and says "use what
   the captured Phase 0 client actually calls". No Phase 0 client capture
   exists in this environment.
2. How MCP-side conventions (isError tool results for client protocol bugs,
   `meta["idempotency-key"]`, `meta["ucp-agent"]`) map onto HTTP.

## Decision

1. **`/checkout-sessions`.** The task research document's concrete REST route
   listing — taken from Google's Native-checkout guide, i.e. the production
   client shape — uses `/checkout-sessions` (`POST /checkout-sessions`,
   `GET/PUT /checkout-sessions/{id}`, `POST /{id}/complete`, `POST
   /{id}/cancel`). That is the closest thing to a "captured client" available
   here, so it wins over the runbook's alternate `/checkouts` spelling. If the
   pinned spec is later consulted and disagrees, the change is confined to the
   controller mappings, the harness route table, and these docs.
2. **Route map** (base = the profile's `services.dev.ucp.shopping.rest.endpoint`,
   locally `https://localhost:9002/occ/v2/{baseSiteId}/ucp`):

   | Operation | Route |
   |---|---|
   | search_catalog | `GET /catalog/search?query=&page=&page_size=` |
   | lookup_catalog | `GET /catalog/lookup?ids=A,B` |
   | get_product | `GET /products/{id}` |
   | create_checkout | `POST /checkout-sessions` |
   | get_checkout | `GET /checkout-sessions/{id}` |
   | update_checkout | `PUT /checkout-sessions/{id}` |
   | complete_checkout | `POST /checkout-sessions/{id}/complete` |
   | cancel_checkout | `POST /checkout-sessions/{id}/cancel` |
   | get_order | `GET /orders/{id}` |
   | list_orders | `GET /orders?page=&page_size=&statuses=A,B` |

   The catalog/lookup GET paths are a local judgment call (no researched REST
   shape existed for the catalog capability) chosen to map 1:1 onto the
   service operations. The `com.thinkshop.*` custom capabilities remain
   **MCP-only** — Phase 7's scope is catalog/checkout/order routes, and the
   custom tools deliberately wrap coremcp services without a binding-agnostic
   layer (Phase 6 decision).
3. **Error taxonomy on the wire**:
   - UCP **business errors** stay HTTP **200** with `ucp.status="error"` +
     `messages[]` (runbook §2.2) — produced by the services, identical bytes
     to the MCP payloads.
   - **Client protocol bugs** (malformed JSON, a checkout payload carrying an
     `id`, missing `Idempotency-Key`, non-integer paging params) are HTTP
     **400** with a UCP error envelope (`invalid_request`/`unrecoverable`) —
     the REST spelling of an MCP `isError` tool result.
4. **Header mappings**: `Idempotency-Key` → the same service parameter the MCP
   tools take from `meta["idempotency-key"]` (the requiredness rule lives in
   the service, Phase 5 decision, so both bindings enforce it identically).
   The `UCP-Agent` header is accepted (the REST spelling of
   `meta["ucp-agent"]`) and, like its MCP counterpart, not currently acted on.
5. The checkout payload **must not contain an `id` on any REST route** — the
   URL path addresses the resource, mirroring the MCP binding's rule; enforced
   in the controller (the same layer the MCP tools enforce it).

## Consequences

- Zero service-layer changes were needed for the REST binding — verified by
  running the MCP-transport e2e suite unchanged after Phase 7 (206 checks,
  same result as Phase 6). Binding-agnosticism (R12) held.
- The official `conformance` suite / `samples` reference client remain an
  **external follow-up** (no network access in this environment): the
  non-root profile path and bearer-token auth are documented local
  concessions (R6/R8) that will need the client's profile-URL flag and
  auth-header injection. What was verified instead: full both-transport e2e
  parity via `scripts/ucp-e2e.py --transport rest` and `--transport mcp`.
