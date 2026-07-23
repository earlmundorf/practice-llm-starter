# Reference — exposed UCP tools and capabilities

Pinned UCP spec version: `2026-04-08` (`ucpcommerce.ucp.version`). All money
is **integer minor units** (e.g. `129999` = $1,299.99). Every payload leads
with a `ucp` envelope (`{version, status}`); business errors are HTTP-200 /
non-`isError` payloads with `ucp.status="error"` + `messages[]`
(`code` ∈ `not_found` / `invalid_request` / `out_of_stock` /
`payment_declined`, `severity` ∈ `recoverable` / `unrecoverable`). Tool
`getDescription()`/`getInputSchema()` are authoritative; this table is the map.

Endpoint: `POST /occ/v2/{baseSiteId}/ucp/mcp` (stateless JSON-RPC;
`@Secured({ROLE_CUSTOMERGROUP, ROLE_TRUSTED_CLIENT})`).

## Capabilities → tools (13 tools)

### dev.ucp.shopping.catalog

| Tool | Input | Returns |
|------|-------|---------|
| `search_catalog` | `query`* (empty browses all), `page`=0, `page_size`=10 (1–50) | `{ucp, products[], pagination}` |
| `lookup_catalog` | `ids[]`* | `{ucp, products[], messages?}` — per-id misses are `recoverable not_found` |
| `get_product` | `id`* (SKU) | `{ucp, product}` or `unrecoverable not_found` |

### dev.ucp.shopping.checkout

| Tool | Input | Returns |
|------|-------|---------|
| `create_checkout` | `checkout` {line_items*, buyer?, fulfillment?} — MUST NOT contain an `id` | `{ucp, id: ucp_chk_…, status, currency, line_items[], totals[], buyer?, fulfillment?}` |
| `get_checkout` | `id`* | current checkout (completed checkouts replay the stored terminal payload incl. `order`) |
| `update_checkout` | `id`*, `checkout` (declarative `line_items`, `buyer`, `fulfillment.destination` + `delivery_mode`) | recalculated checkout; status derived (`ready_for_complete` = items + address + mode) |
| `complete_checkout` | `id`*, `checkout.payment.instruments[{handler_id, type, credential}]`, `meta["idempotency-key"]`* | `{…, status: completed, order: {id, created_at}}`; same-key replay returns the SAME order |
| `cancel_checkout` | `id`*, `meta["idempotency-key"]`* | `{…, status: canceled}` — idempotent, terminal |

Only `handler_id` `thinkshop_mock_card` is accepted (the profile's single
declared mock handler); any credential token passes and is never read/stored.

### dev.ucp.shopping.order

| Tool | Input | Returns |
|------|-------|---------|
| `get_order` | `id`* (order code from `complete_checkout`/`list_orders`) | `{ucp, order: {id, created_at, status, currency, line_items[], totals[], fulfillment}}` or `unrecoverable not_found` |
| `list_orders` | `page`=0, `page_size`=10 (1–50), `statuses[]?` (hybris codes, case-insensitive) | `{ucp, orders[] (summaries: id, created_at, status, total), pagination}` |

Both are scoped to the authenticated customer (standard `OrderFacade`
contract). Order `status` values are lowercased hybris codes.

### com.thinkshop.promotions (custom, design R7)

| Tool | Input | Returns |
|------|-------|---------|
| `get_promotions` | `active_only`=true, `include_coupons`=true | `{ucp, promotions[] (code, name, status, dates), coupons[]?}` — metadata via coremcp `PromotionQueryService`; actual discounts appear in checkout totals when Drools fires |

### com.thinkshop.knowledge (custom, design R7)

| Tool | Input | Returns |
|------|-------|---------|
| `search_knowledge` | `query`*, `category?` (policy/event/promo/guide/brand/howto/contact), `page_size`=5 (1–50) | `{ucp, results[] (uid, category, title, summary, body, tags), count}` |
| `get_knowledge` | `uid`* | `{ucp, entry}` or `unrecoverable not_found` |

Backed by coremcp's Solr-only `KnowledgeSearchService` (`knowledgeIndex`).

## Verification

- `core-customize/scripts/ucp-e2e.py --transport mcp` — full-flow harness
  (profile → catalog → checkout lifecycle → orders → promotions → knowledge),
  with best-effort `ucp-schema validate` on captured payloads.
- `core-customize/scripts/smoke-test.sh` — UCP section: profile, tools/list
  (exactly 13), one catalog search, `com.thinkshop.*` calls.
