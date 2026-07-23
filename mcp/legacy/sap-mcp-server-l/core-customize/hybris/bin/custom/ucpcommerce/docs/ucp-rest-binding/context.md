# UCP REST binding — context

## What this flow does

Exposes the catalog, checkout, and order capabilities as plain REST routes —
the UCP **REST transport binding** (design R12), added alongside MCP in
Phase 7 as thin adapters over the **identical** binding-agnostic capability
services. The profile advertises the base under
`services.dev.ucp.shopping.rest.endpoint`:

```
https://localhost:9002/occ/v2/{baseSiteId}/ucp        (local default)
```

Routes (all `@Secured(ROLE_CUSTOMERGROUP, ROLE_TRUSTED_CLIENT)`):

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

The `com.thinkshop.*` custom capabilities are **MCP-only** (Phase 6 decision:
their tools wrap coremcp services directly; Phase 7 REST scope is
catalog/checkout/order).

## When it's used

A REST-speaking UCP client (the official reference client shape, or
`scripts/ucp-e2e.py --transport rest`) discovers the `rest` endpoint from the
profile and drives the same discover → search → create → update → complete →
orders flow the MCP binding serves.

## Key decisions

- **`/checkout-sessions`, not `/checkouts`** — ADR 0002 (the runbook §9.1
  ambiguity, resolved to the researched Google Native-checkout shape).
- **Zero service-layer changes**: the controllers only map HTTP onto the
  existing service signatures. Proof: the MCP-transport e2e suite runs
  unchanged after Phase 7 (binding-agnosticism, R12).
- **Error taxonomy mirrors MCP exactly**: business errors = HTTP 200 +
  `ucp.status="error"`/`messages[]` (identical payload bytes to the MCP
  binding); client protocol bugs (payload `id`, malformed JSON/params,
  missing `Idempotency-Key`) = HTTP 400 with a UCP error envelope — the REST
  spelling of an MCP `isError` tool result.
- **Header mappings**: `Idempotency-Key` → the same service parameter as
  `meta["idempotency-key"]` (requiredness enforced in the service, so both
  bindings behave identically); `UCP-Agent` is the REST spelling of
  `meta["ucp-agent"]` (accepted, not currently acted on).
- The official `conformance` suite / `samples` reference client are an
  **external follow-up** (see docs/README.md — Verification): this
  environment has no network access to run them; both-transport e2e parity
  was verified instead.
