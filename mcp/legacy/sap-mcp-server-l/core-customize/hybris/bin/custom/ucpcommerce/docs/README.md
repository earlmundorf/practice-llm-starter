# ucpcommerce — documentation index

`ucpcommerce` exposes a **UCP (Universal Commerce Protocol)** surface over the
existing SAP Commerce facades, as a dependent sibling of `coremcp`. The
proprietary MCP dialect, agent endpoints, and all `coremcp` behavior remain
untouched — UCP is a strictly additive, standards-based third surface.

- Pinned UCP spec version: **`2026-04-08`** (`ucpcommerce.ucp.version` in
  `project.properties`; see ADR 0001 — dated calver strings are load-bearing).
- Transport strategy: **both bindings live** (ADR 0001: MCP first, REST
  alongside as thin adapters over the same binding-agnostic capability
  services) — MCP at `POST /occ/v2/{baseSiteId}/ucp/mcp`, REST under the base
  `/occ/v2/{baseSiteId}/ucp` (`/checkout-sessions`, `/catalog/*`,
  `/products/{id}`, `/orders` — ADR 0002).

## Flows

| Flow | Status |
|------|--------|
| [`ucp-profile`](ucp-profile/context.md) — anonymous discovery document at `/.well-known/ucp` | live — full capability set (catalog, checkout, order, `com.thinkshop.*`) + mcp transport + mock payment handler |
| [`ucp-mcp-binding`](ucp-mcp-binding/context.md) — stateless JSON-RPC tools endpoint at `/{baseSiteId}/ucp/mcp` | live — 13 tools across all five capabilities |
| [`ucp-checkout`](ucp-checkout/context.md) — checkout-session lifecycle over `UcpCheckoutSessionEntry` | live — create/get/update/complete (mock payment, idempotent)/cancel |
| [`ucp-rest-binding`](ucp-rest-binding/context.md) — REST routes over the same services | live — catalog/checkout/order routes; profile advertises the `rest` transport |

## Reference

| Doc | Contents |
|-----|----------|
| [`reference/tools.md`](reference/tools.md) | Every exposed tool and capability: inputs, payload shapes, error taxonomy |

## ADRs

| ADR | Decision |
|-----|----------|
| [0001](adr/0001-ucp-as-dependent-extension-mcp-first.md) | UCP as a dependent extension; MCP binding first with REST alongside; spec version pinned |
| [0002](adr/0002-rest-binding-checkout-sessions-path.md) | REST binding: `/checkout-sessions` naming, route map, 400-vs-messages[] error taxonomy, `Idempotency-Key`/`UCP-Agent` header mappings |

## Profile path concession (local testing)

UCP requires the discovery profile at the **domain root**
(`https://host/.well-known/ucp`). SAP Commerce apps live under context paths,
so locally the profile is served **inside OCC** at

```
GET /occ/v2/{baseSiteId}/.well-known/ucp        (ROLE_ANONYMOUS)
```

All local tooling (`scripts/ucp-e2e.py`, the official reference client where
applicable) takes an explicit profile URL, so the non-root path is a documented
local-testing concession.

**Production note:** front the platform with an edge rewrite so the root path
maps onto this route, e.g. (Apache httpd on CCv2, or nginx/ingress):

```apache
ProxyPass  /.well-known/ucp  https://backend:9002/occ/v2/electronics/.well-known/ucp
```

The profile's advertised `services.*.{mcp,rest}.endpoint` values must then use
the publicly reachable base, not the internal context path.

## Auth concession (local testing)

Real UCP guest checkout does not present merchant OAuth tokens. Locally, all
UCP commerce endpoints are `@Secured({"ROLE_CUSTOMERGROUP","ROLE_TRUSTED_CLIENT"})`
and the harness authenticates with the demo customer's password-grant token
(`john.doe@thinkshop.com`) — the only checkout path proven end-to-end on this
platform (anonymous sessions are blocked at `createPaymentSubscription`).
Only the profile endpoint allows `ROLE_ANONYMOUS`.

## Verification

What is verified automatically against a running local server:

- `core-customize/scripts/ucp-e2e.py --transport mcp` — full flow over the
  MCP binding (profile → catalog → checkout lifecycle incl. idempotent
  replay → orders → promotions → knowledge).
- `core-customize/scripts/ucp-e2e.py --transport rest` — the **same payload
  assertions** over the REST binding (catalog/checkout/order; the
  `com.thinkshop.*` capabilities are MCP-only and skipped). Running both
  transports green against the same server is the working proof of
  binding-agnosticism (R12): the REST binding added zero service-layer
  changes.
- `core-customize/scripts/smoke-test.sh` — UCP section (profile,
  exactly-13-tools, catalog search, `com.thinkshop.*` calls).
- `ucp-schema validate` — every capability payload the e2e harness
  captures is validated against the OFFICIAL pinned schema set
  (`cargo install ucp-schema`; schemas mirrored under
  `working-docs/ucp-client/schemas-2026-04-08/`, remote fallback to
  ucp.dev). The only SKIPs left are honest ones: `list_orders` (extension
  surface — the official spec has no list binding) and the
  `com.thinkshop.*` custom capabilities (no official schema).
- `core-customize/scripts/run-ucp-conformance.sh` — the official
  conformance suite through the local proxy (see below).

### Official reference client: PASSING (2026-07-23)

Google's out-of-the-box happy-path client from
[`samples`](https://github.com/Universal-Commerce-Protocol/samples)
(`rest/python/client/flower_shop/simple_happy_path_client.py`; pins:
samples `f59d963`, python-sdk `c1ffd1b`, ucp spec `f9bf815`) completes a
**full purchase** against this server: discovery → create → item update →
discount attempt (warns, ThinkShop has no `10OFF` code) → **fulfillment
negotiation** (address-book destinations offered, delivery-mode options,
selection) → `complete` with `thinkshop_mock_card` → order placed with a
dereferenceable `permalink_url` (verified run: order `00010016`, mouse +
2× laptop, 267997 minor units).

One command reproduces it (server running, Solr indexed, `uv` installed,
official repos cloned under `working-docs/ucp-client/` as siblings):

```bash
./scripts/run-ucp-reference-client.sh
```

The script keeps the client **byte-identical except three documented
constant substitutions** (the two flower-shop demo SKUs → ThinkShop SKUs,
`mock_payment_handler` → `thinkshop_mock_card`) and runs it against
`scripts/ucp-local-proxy.py` — a stdlib-only localhost proxy that is the
documented dev stand-in for the two production concessions:

- **Root-path discovery** (R6): the proxy serves `/.well-known/ucp` and
  `/checkout-sessions/…` from one base, mapping onto the OCC paths —
  locally what an edge rewrite does in production.
- **Bearer-token auth** (R8): the proxy injects the password-grant
  `Authorization` header (john.doe@thinkshop.com) and skips TLS verification
  for the self-signed local cert — locally what an agent gateway does in
  production.

The wire-shape corrections this exercise produced (profile registries
inside `ucp`, negative discounts, `fulfillment` totals type, the
negotiation flow, `payment`/`links` echo, `order.permalink_url`,
matching-payload-id tolerance) are recorded in **ADR 0003**. A captured
request/response log of a passing run (copy-pasteable curls) lives in the
task artifacts (`reference-client-happy-path.md`); regenerate one anytime
with `UCP_CLIENT_EXPORT=<path> ./scripts/run-ucp-reference-client.sh`.

### Generic MCP chat clients (e.g. a claude.ai custom connector): PASSING (2026-07-23)

A generic MCP chat client can drive the full journey against the MCP
binding, but needs a gateway in front of the store for the two things such
clients cannot provide (both R8 agent-platform responsibilities): the OAuth2
bearer, and `meta["idempotency-key"]` — which travels in the JSON-RPC
`params.meta`, *outside* the `params.arguments` an LLM controls (see
`docs/reference/tools.md`). `scripts/ucp-mcp-bridge.py` is that gateway's
dev stand-in: it accepts unauthenticated MCP POSTs, injects the bearer, and
injects a **deterministic** idempotency key (hash of tool + arguments) so a
retried identical `complete_checkout` replays the stored completion instead
of placing a duplicate order.

```bash
python3 scripts/ucp-mcp-bridge.py            # :8183 → /occ/v2/electronics/ucp/mcp
cloudflared tunnel --url http://localhost:8183   # then use https://<host>/mcp
```

Verified live with a claude.ai custom connector completing search →
create → update → complete (the totals double-count fix in
`UcpCheckoutMarshaller` came out of that session). The bridge exposes the
demo store unauthenticated — take the tunnel down when finished.

### Official conformance suite: RUN (2026-07-24) — 44 passing, remainder N/A

The official
[`conformance`](https://github.com/Universal-Commerce-Protocol/conformance)
suite (cloned under `working-docs/ucp-client/conformance/`) runs against
this server through `scripts/ucp-local-proxy.py` (which now also rewrites
the advertised transport endpoints to itself, exactly as a production edge
would), with ThinkShop-specific expectations in
`scripts/conformance/` (`thinkshop-conformance-input.json`,
`thinkshop-test-fixtures.json`, `test_data/`):

```bash
./scripts/run-ucp-conformance.sh              # whole suite
./scripts/run-ucp-conformance.sh discount_test.py   # one file
```

Result on 2026-07-24: **44 tests pass, 5 skip, 20 fail — every failure is
a sample-server/test-data coupling, not a spec gap.** Ten of fifteen files
pass outright (`checkout_lifecycle`, `idempotency`, `discount`,
`business_logic`, `totals`, `validation`, `protocol`, `ap2`, `binding`,
`card_credential`). The suite drove a substantial round of fixes recorded
in **ADR 0004**, including: client-generated create ids tolerated,
HTTP 201/402/409/422 status semantics on the REST binding, per-operation
idempotency (`UcpIdempotencyRecord`), case-insensitive discount codes with
the official `applied[]` echo, inline fulfillment destinations, the
`payment_handlers` checkout envelope, the official `product.json` catalog
shape, raw `order.json` responses with `checkout_id`, cursor-style
pagination, buyer consent echo, the `fail_token` decline probe, and
UCP-Agent version negotiation.

The 20 remaining failures, classified (all reproduced in
`/tmp/ucp-conformance-run.log` after a run):

| Category | Tests | Why not applicable |
|---|---|---|
| Simulation endpoints | 3 | `/simulation/*` + `SIMULATION_SECRET` are the sample server's test doubles; this is a real commerce backend |
| Webhooks | 3 | no `order_webhook` capability is advertised or implemented (no eventing on the demo platform) |
| Order modification | 5 | `PUT /orders/{id}` + adjustments + fulfillment expectations model merchant-ops simulation; no fulfillment process runs on this demo platform |
| Flower-shop shipping fixtures | 3 | expect `exp-ship-us`/`exp-ship-intl` option ids and CA delivery; ThinkShop is US-only with its own delivery modes |
| Per-buyer address books | 6 | expect buyer-email-scoped customers and CSV address ids (`addr_1`/`addr_2`); this surface binds to the authenticated gateway customer and hybris address ids are PKs |
