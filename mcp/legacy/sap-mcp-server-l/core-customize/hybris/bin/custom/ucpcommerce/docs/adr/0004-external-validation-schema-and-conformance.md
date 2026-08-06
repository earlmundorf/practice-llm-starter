# ADR 0004: External validation — official schema validation + conformance suite

- **Status:** accepted (2026-07-24)
- **Context refs:** ADR 0003 (schema-oracle corrections, left two external
  checks open), issues #4/#5 follow-ups from the PR #3 review

## Context

ADR 0003 aligned the wire shapes against the cloned official repos but left
two external checks outstanding: real `ucp-schema` validation in the e2e
harness (every run reported SKIP) and the official
`Universal-Commerce-Protocol/conformance` suite. Both have now been run
against the local server, and everything they surfaced that reflects the
actual spec has been fixed.

## What was set up

- **`ucp-schema` CLI** (`cargo install ucp-schema`, v1.4.0) with the pinned
  2026-04-08 schema set mirrored to
  `working-docs/ucp-client/schemas-2026-04-08/` (crawled once from ucp.dev;
  the harness falls back to remote fetch when the mirror is absent).
  `ucp-e2e.py` now maps every captured payload to its official schema
  (`checkout.json` per op, `order.json`, `catalog_search.json#search_response`,
  `catalog_lookup.json#lookup_response`/`get_product_response`,
  `ucp.json#base` for the profile) — 0 skipped validations except
  `list_orders` (extension surface, no official binding) and the
  `com.thinkshop.*` custom capabilities.
- **Conformance suite** cloned under `working-docs/ucp-client/conformance/`,
  run per-file via `scripts/run-ucp-conformance.sh` with ThinkShop
  expectations in `scripts/conformance/` and a $35.00 fixture product
  (`UCP_CONFORMANCE_ITEM`, `projectdata-70-ucp-conformance.impex`) chosen to
  be untouched by any automatic promotion (the suite's fulfillment flow
  hard-codes a 3500-minor-unit base). The local proxy now rewrites the
  advertised transport endpoints to itself — the discovery-driven suite then
  routes through the proxy's bearer injection, exactly like a production
  edge advertising its public base.

## Corrections the validation drove

Schema validation:

1. **Checkout envelopes carry `payment_handlers`**
   (`response_checkout_schema` requires it) — the registry is shared with
   the profile via `UcpProfileService.paymentHandlerRegistry()`.
2. **`get_order` returns the RAW order** (no `{"order": …}` wrapper — the
   suite does `Order(**response)`), with its own `ucp` envelope, the
   REQUIRED `checkout_id` (recovered from the session store by order code),
   ORDER-shaped line items (`quantity` object + derived `status`,
   `order_line_item.json`) and an always-present `fulfillment` object.
3. **Catalog products follow `product.json`**: `description` is a formats
   OBJECT, `price_range` and a single `variants[]` entry (mirroring the
   variantless ThinkShop product) are required; lookup variants carry the
   required `inputs[]` correlation. Flat `price`/`currency`/`availability`
   ride along as tolerated extras.
4. **Pagination is the official response block**: `has_next_page` required,
   `cursor` (the next page number) when true, `total_count`.

Conformance suite:

5. **Create tolerates a client-generated `id`** (the official
   `CheckoutCreateRequest` has an optional id; the server still mints the
   canonical one) and answers **HTTP 201**.
6. **REST status semantics** for well-known outcomes, mapped from dedicated
   in-band message codes: `conflict` → 409 (terminal-state mutations,
   idempotency conflicts), `payment_declined` → 402, `version_unsupported`
   → 422 (UCP-Agent header `version="…"` negotiation), `not_ready` → 400
   (complete before fulfillment is selected). All other business errors stay
   in-band per ADR 0002.
7. **Per-operation idempotency** (`UcpIdempotencyRecord`, typecode 14004):
   any mutating call may carry an `Idempotency-Key`; an identical retry
   replays the stored response verbatim (create/update) or the stored
   completion (complete), and a same-key retry with a DIFFERENT payload is
   a 409. `UcpCheckoutRequest` retains unmapped fields (`@JsonAnySetter`)
   so the request hash reflects the payload as sent. Same-key completion
   replay now also covers the stored-response-missing case
   (`completedFallback`), fixing the bogus "different idempotency key"
   error the review flagged.
8. **Discounts**: codes match case-insensitively (release by server-side
   canonical code; case-variant applies retry canonical forms), and the
   response carries the official `applied[]` entries (title + positive
   minor-unit amount).
9. **Fulfillment**: inline `methods[].destinations[]` supplied by the
   client are applied when selected in the same call (official
   `shipping_destination` field names), offered destinations are the UNION
   of the address book and client-supplied entries under the client's ids,
   and the `groups[]` block is always attached (options fill in once a
   destination applies). The saved-address selection path resolves ids
   against the address book explicitly — `CheckoutFacade.setDeliveryAddress`
   silently resolves unknown ids to null (clearing the address) and still
   returns true.
10. **Payment**: instruments echo their client `id` and `display` block and
    are **stripped of `credential`** before echo/persist (review finding —
    credentials are never echoed, logged or stored). The mock handler
    declines the well-known `fail_token` probe (402) and the ecosystem's
    hard-coded `mock_payment_handler` id is declared as an alias of
    `thinkshop_mock_card`.
11. **Buyer consent** (`buyer.consent`) is persisted with the buyer block
    and echoed verbatim.
12. **Stock clamping** reports the canonical `quantity_adjusted` warning.
13. **Cleanup job** no longer age-sweeps COMPLETED/COMPLETE_IN_PROGRESS
    entries (review finding): completed checkouts and idempotency records
    follow `ucpcommerce.checkout.completed.retention.minutes` (default 7
    days); stuck in-progress completions are never deleted here.

## Results (2026-07-24)

- e2e: **254/254 (mcp)**, **223/223 (rest)** — schema validation ACTIVE.
- Conformance: **44 pass / 5 skip / 20 fail — all 20 are sample-server or
  test-data couplings** (simulation endpoints, webhooks, order-modification
  API, flower-shop shipping fixtures, per-buyer-email address books with
  CSV ids); classification table in `docs/README.md`.
- Reference client: PASSING (order `00020101`); smoke test 25/25.

## Consequences

- The wire is now pinned by the official schemas themselves on every e2e
  run, not just hand-written assertions.
- If the pinned version advances: re-crawl the schema mirror, re-run
  `run-ucp-conformance.sh`, and re-classify.
- The N/A conformance categories are the honest boundary of a demo
  commerce backend; implementing webhooks or an order-modification surface
  would be new capabilities, not corrections.
