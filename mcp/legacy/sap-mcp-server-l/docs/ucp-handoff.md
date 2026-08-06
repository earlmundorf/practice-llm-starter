# UCP on SAP Commerce — Complete Handoff for Claude Code

**Purpose of this document:** everything a fresh Claude Code session needs to
continue the UCP (Universal Commerce Protocol) work on this repository —
what was built, why, where it lives, how to build/verify it, what is proven
working, and exactly what remains. Written 2026-07-24, current as of commit
`f5fc3ad` on branch `expose-sap-server-tools-to-ucp-via-llm-extension`
(repo `earlmundorf/practice-llm-starter`).

---

## 1. What this project is

We exposed a SAP Commerce (hybris) demo store ("ThinkShop", base site
`electronics`) to **any UCP agent** — Google's Universal Commerce Protocol
for agentic shopping. A UCP agent can discover the store, search the
catalog, run a full checkout (items → fulfillment negotiation → coupons →
mock payment), and read orders — over **two transports** (MCP JSON-RPC and
REST) that are thin adapters over one identical service layer.

This is proven working three independent ways (all re-verified 2026-07-24):

1. **Google's out-of-the-box reference client** completes a full purchase
   unmodified (3 documented constant substitutions only).
2. **The official UCP conformance suite** passes everything that reflects
   the actual spec (44 pass; the 20 remaining failures are all
   sample-server/test-data couplings — classified, see §8).
3. **A live claude.ai custom connector** drove search → create → update →
   complete through the MCP bridge.

Every e2e payload is validated against the **official pinned JSON schemas**
on every harness run (254/254 MCP, 223/223 REST, zero schema failures).

---

## 2. Repository geography

The git repo root is `practice-llm-starter/`. The UCP work lives in the SAP
Commerce project at:

```
mcp/legacy/sap-mcp-server-l/                  ← the SAP Commerce (CCv2) project
├── CLAUDE.md                                 ← READ THIS: build/test/run commands + critical rules
├── core-customize/                           ← CCv2 root; all ./gradlew commands run from here
│   ├── hybris/bin/custom/
│   │   ├── coremcp/                          ← pre-existing MCP server extension (UNTOUCHED by UCP work)
│   │   ├── sampledatamcp/                    ← sample data (products, customers, promotions)
│   │   └── ucpcommerce/                      ← ★ THE UCP EXTENSION (all our work)
│   ├── scripts/                              ← verification + local-infra scripts (see §6)
│   └── dev-config/local.properties           ← tracked config source (NOT hybris/config)
└── working-docs/ucp-client/                  ← gitignored clones of the official UCP repos:
    ├── ucp/                                  ←   spec + schemas (pin f9bf815)
    ├── python-sdk/                           ←   pydantic models (pin c1ffd1b)
    ├── samples/                              ←   reference server + happy-path client (pin f59d963)
    ├── conformance/                          ←   official conformance suite (cloned 2026-07-24)
    └── schemas-2026-04-08/                   ←   pinned schema mirror crawled from ucp.dev
```

("mcp/legacy" is a misleading path name — this is the ACTIVE server project.)

Authoritative in-repo documentation (keep in sync — repo rule):

- `ucpcommerce/docs/README.md` — index + **Verification** section (all
  proof points with reproduction commands + conformance N/A table)
- `ucpcommerce/docs/reference/tools.md` — all 13 tools, REST routes, error
  taxonomy, idempotency rules, HTTP status semantics
- `ucpcommerce/docs/adr/` — ADR 0001 (dependent extension, MCP-first),
  0002 (REST binding, `/checkout-sessions` paths, in-band errors),
  0003 (wire-shape corrections from official schemas + reference-client
  acceptance), **0004 (external validation round — the full record of the
  2026-07-24 fixes)**
- Four flow dirs: `ucp-profile/`, `ucp-mcp-binding/`, `ucp-checkout/`,
  `ucp-rest-binding/` (context/components/diagram each)

Related GitHub issues: **#5 CLOSED** (external validation — done),
**#4 OPEN** (production hardening — partially done, see §9),
**#6 OPEN** (React storefront — untouched, independent).

---

## 3. What the surface exposes

Pinned UCP version: `2026-04-08` (`ucpcommerce.ucp.version`). All money is
**integer minor units** ($1,299.99 → `129999`) — converted exactly once at
the marshalling boundary by `UcpMoneyConverter`, never ad hoc.

| Capability | What works |
|---|---|
| **Discovery** | Anonymous `GET /occ/v2/electronics/.well-known/ucp` — official registry shape: everything inside a top-level `ucp` object; `services`/`capabilities`/`payment_handlers` are reverse-DNS-keyed maps of version-entry LISTS. Advertised base URL is `ucpcommerce.public.base.url` (default `https://localhost:9002`; override for tunnels/edge) |
| **Catalog** | `search_catalog` / `lookup_catalog` / `get_product` — official `product.json` shape (description OBJECT, `price_range`, single `variants[]` entry mirroring the variantless product, lookup `inputs[]` correlation) |
| **Checkout** | create (client id tolerated, 201) → declarative line-item diffs → **fulfillment negotiation** (saved-address destinations AND client-supplied inline destinations, groups/options = delivery modes) → **discounts** (case-insensitive codes, official `applied[]` echo, demo coupon `10OFF`) → complete with mock payment (idempotent) → cancel. Status lifecycle derived server-side: `incomplete → ready_for_complete → complete_in_progress → completed / canceled` |
| **Orders** | `get_order` (RAW official `order.json` object — no wrapper — with `checkout_id` provenance, order-shaped line items) + `list_orders` (extension surface, summaries + pagination), customer-scoped |
| **Custom** | `com.thinkshop.promotions`, `com.thinkshop.knowledge` (MCP-only, design R7) |
| **Payment** | Declared mock handler `thinkshop_mock_card` + declared ecosystem alias `mock_payment_handler` (same mock). Any credential token accepted EXCEPT `fail_token` (declines, 402). Credentials **stripped** from every echo and from persisted completion responses |

Transports (13 identical tools / routes):

- **MCP**: `POST /occ/v2/{site}/ucp/mcp` — stateless JSON-RPC.
  `meta["idempotency-key"]` travels in `params.meta` (or `params._meta`),
  OUTSIDE the `params.arguments` an LLM controls.
- **REST**: base `/occ/v2/{site}/ucp` — `/catalog/search`, `/catalog/lookup`,
  `/products/{id}`, `/checkout-sessions[...]`, `/orders[...]`.
  `Idempotency-Key` and `UCP-Agent` headers are the REST spellings.

Error model (ADR 0002 + 0004): business errors are in-band payloads
(`ucp.status="error"` + `messages[]`); client protocol bugs are MCP
`isError` / REST 400. The official binding pins dedicated REST statuses,
mapped from in-band message codes: **create success → 201**, `conflict` →
**409** (terminal-state mutations, idempotency conflicts),
`payment_declined` → **402**, `not_ready` → **400** (complete before
fulfillment selected), `version_unsupported` → **422** (UCP-Agent header
`version="…"` negotiation).

**Idempotency (important):** `complete`/`cancel` REQUIRE the key;
`create`/`update` honor it when supplied. Identical same-key retry replays
the first response verbatim; same key + DIFFERENT payload → 409. Backed by
the `UcpIdempotencyRecord` item type (typecode 14004); completion replay
itself comes from the session entry (`completionResponseJson`).

---

## 4. Architecture of the `ucpcommerce` extension

Design rules (from the original design phase, still binding):

- **R5** — persisted session store: `UcpCheckoutSessionEntry` (typecode
  14003) maps opaque `ucp_chk_…` ids to hybris cart codes + protocol state
  (status, buyer JSON, idempotency key, completion response, order code).
  Lazy TTL eviction + `ucpCheckoutSessionCleanupCronJob` sweep.
- **R6** — discovery must be public at the host root in production (edge
  rewrite); locally the proxy stands in.
- **R8** — merchant auth (OAuth password grant) is an agent-gateway
  responsibility, not the agent's; locally the proxy/bridge inject it.
- **R9** — one honest mock payment handler; credentials never inspected.
- **R12** — binding-agnostic services: MCP tools and REST controllers are
  thin adapters over identical `Ucp*Service` beans. Adding REST required
  zero service changes; keep it that way.

Key classes (all under `ucpcommerce/src/com/ucpcommerce/`):

- `services/impl/DefaultUcpCheckoutService` — the heart: create/get/update/
  complete/cancel, declarative diffs, fulfillment negotiation
  (`applyFulfillment` + `attachFulfillmentNegotiation`), discounts
  (`applyDiscounts` + `attachAppliedDiscounts`), idempotency bracket,
  version gate, decline probe, derived status (`deriveStatus`).
- `services/impl/UcpCheckoutMarshaller` — CartData/OrderData → UCP checkout
  (line items, totals that PROVABLY SUM, fulfillment echo, envelope with
  `payment_handlers`). All money crosses major→minor here only.
- `services/impl/UcpOrderMarshaller` — embedded order block (minimal:
  id/created_at/permalink_url), full `order.json` shape, history summaries.
- `services/impl/DefaultUcpProfileService` — discovery profile +
  `paymentHandlerRegistry()` (shared with checkout envelopes).
- `services/impl/PersistedUcpCheckoutSessionService` — session store +
  `findCheckoutIdForOrder` (order → checkout_id provenance).
- `services/impl/DefaultUcpIdempotencyService` — per-(user, operation, key)
  records, SHA-256 request hash comparison.
- `services/impl/DefaultUcpCatalogService` — product.json marshalling over
  the standard search/product facades.
- `controllers/` — `UcpMcpController` (JSON-RPC dispatcher),
  `Ucp*RestController` + `AbstractUcpRestController` (status mapping,
  UCP-Agent version gate), `UcpProfileController`.
- `tools/impl/*Tool` — the 13 MCP tools; `UcpToolContext` carries
  `meta["ucp-agent"]` + `meta["idempotency-key"]`.
- `dto/` — hand-written Jackson classes (deliberately NOT generated WsDTOs —
  coremcp ADR 0005 idiom). Request-side DTOs carry
  `@JsonIgnoreProperties(ignoreUnknown = true)`; `UcpCheckoutRequest`
  additionally retains unknown fields via `@JsonAnySetter` so idempotency
  hashes reflect the payload AS SENT.

Spring wiring in `resources/ucpcommerce-spring.xml` (alias pattern:
`defaultX` → `x`). Item types in `resources/ucpcommerce-items.xml`.

---

## 5. Build / test / run (the workflow that actually works)

Everything from `mcp/legacy/sap-mcp-server-l/core-customize/`, with
`JAVA_HOME=~/.sdkman/candidates/java/17.0.19-sapmchn`. Full rules in the
project `CLAUDE.md` — the critical ones:

```bash
# After ANY Java change (yclean is MANDATORY — incremental compile silently skips files):
./gradlew yclean ybuild stopServer startServer

# After *-items.xml changes (run yupdatesystem with the server STOPPED):
./gradlew yclean ybuild stopServer yupdatesystem startServer

# Unit tests — ALWAYS stop the server first (the junit tenant kills the live Solr):
./gradlew stopServer
cd hybris/bin/platform && . ./setantenv.sh && ant unittests -Dtestclasses.extensions=ucpcommerce
ant integrationtests -Dtestclasses.extensions=ucpcommerce   # session-store round-trip
cd ../../.. && ./gradlew startServer
```

Server readiness check:
`curl -k https://localhost:9002/occ/v2/electronics/.well-known/ucp` → 200
(comes up ~30s after startServer).

**Verification suite — the green baseline any change must preserve:**

```bash
python3 scripts/ucp-e2e.py --transport mcp    # 254/254, 2 honest skips
python3 scripts/ucp-e2e.py --transport rest   # 223/223, 3 honest skips
./scripts/smoke-test.sh                       # 25/25 (exits nonzero on failure)
./scripts/run-ucp-reference-client.sh         # Google's OOTB client, full purchase
./scripts/run-ucp-conformance.sh              # official suite: 44 pass / 5 skip / 20 N-A
```

Demo access: OAuth `trusted_client`/`secret` (client-credentials) and
`mobile_android`/`secret` + `john.doe@thinkshop.com`/`1234` (password
grant). All scripts take env overrides (`UCP_CLIENT_ID`, `UCP_CLIENT_SECRET`,
`UCP_USERNAME`, `UCP_PASSWORD`, `SMOKE_*`). HAC/Backoffice:
`http://localhost:9001` admin/nimda. Groovy/flexquery/impex via
`./gradlew groovy -Pfile=… [-Pcommit=true]` etc.

---

## 6. Local infrastructure scripts (`core-customize/scripts/`)

| Script | Role |
|---|---|
| `ucp-e2e.py` | Transport-flagged e2e harness; every payload schema-validated via the `ucp-schema` CLI (`cargo install ucp-schema`; mirror at `working-docs/ucp-client/schemas-2026-04-08/`, remote ucp.dev fallback) |
| `ucp-local-proxy.py` | Dev stand-in for the production edge (R6) + agent gateway (R8): serves `/.well-known/ucp` and `/{path}` from one base, injects the password-grant bearer, **rewrites advertised endpoints to itself** (discovery-driven clients then route through it), rejects `..` traversal |
| `ucp-mcp-bridge.py` | Auth-injecting MCP bridge for generic MCP chat clients (claude.ai connector): injects bearer + a DETERMINISTIC `meta["idempotency-key"]` (hash of tool+args) on complete/cancel ONLY. Prints a loud exposure warning; optional `--auth-token` / `UCP_BRIDGE_AUTH_TOKEN` shared secret. Expose via `cloudflared tunnel --url http://localhost:8183`, point the connector at `https://<host>/mcp` |
| `run-ucp-reference-client.sh` | Derives Google's happy-path client (3 sed substitutions: 2 SKUs + handler id), runs it through the proxy, asserts full purchase |
| `run-ucp-conformance.sh` | Runs the official suite per-file (absltest form — the suite's conftest doesn't forward pytest flags) through the proxy with ThinkShop configs |
| `conformance/` | `thinkshop-conformance-input.json`, `thinkshop-test-fixtures.json`, `test_data/` (payment instruments with our handler ids, addresses) |
| `smoke-test.sh` | 25-check demo-readiness suite incl. UCP section |
| `setup-promotions.sh` + `publish-promotions.groovy` | Drools promotion setup/publish (6 rules — see gotcha in §10) |

Fixture data that matters: SKUs `LAPTOP_PRO_15` ($1299.99),
`WIRELESS_GAMING_MOUSE` ($79.99, BOGO promo), `WIRELESS_HEADPHONES`
($199.99, **automatic 10% promo — do not use as a neutral fixture**),
`UCP_CONFORMANCE_ITEM` ($35.00, promotion-free, added for the conformance
suite via `sampledatamcp/resources/impex/projectdata-70-ucp-conformance.impex`
— on an existing DB import it with `./gradlew impex -Pfile=…`),
`KEYBOARD_LTD_ALUMINUM` (force out-of-stock). Demo coupon `10OFF` (10% off
order) via `ucpcommerce/resources/ucpcommerce/demo/setup-ucp-demo-coupon.groovy`.
Delivery modes `thinkshop-standard` ($5.99) / `thinkshop-express` ($14.99) /
`thinkshop-free-delivery` (carts ≥ $1,000); US-only store.

---

## 7. History — how we got here (10+1 commits, PR #3 + follow-up)

Phases 1–7 built the surface MCP-first (profile → catalog → checkout
create/get → update/negotiation → complete/cancel with idempotency →
orders + custom capabilities → REST binding). Then three hardening commits:
`97385a5` (official-schema alignment, ADR 0003 — reference client passes),
`d346b6d` (claude.ai enablement: totals double-count fix + the MCP bridge),
`c56604c` (discounts.codes coupon support).

**`f5fc3ad` (2026-07-24, the external-validation round — ADR 0004)** closed
issue #5 and part of #4:

- `ucp-schema` CLI wired into the harness (real validation, was SKIP).
- Official conformance suite run; every spec-real finding fixed:
  - checkout envelopes embed `payment_handlers`
  - raw `order.json` responses (`checkout_id`, order line items
    `{quantity:{original,total,fulfilled}, status}`, always-present
    `fulfillment`)
  - official catalog `product.json` + `pagination` (`has_next_page`/`cursor`)
  - create: client id tolerated, 201; REST 409/402/422/400 mapping
  - per-operation idempotency store (`UcpIdempotencyRecord`)
  - discounts: case-insensitive matching, canonical release, `applied[]`
  - fulfillment: inline client destinations + union echo under client ids;
    saved ids resolved against the address book EXPLICITLY (see gotcha §10)
  - payment: instrument `id`/`display` echo, credential stripping,
    `fail_token` decline, `mock_payment_handler` alias
  - buyer `consent` passthrough; `quantity_adjusted` clamp warnings
  - cleanup job spares COMPLETED/IN_PROGRESS from the age sweep; new
    retention property `ucpcommerce.checkout.completed.retention.minutes`
- Script hardening: proxy traversal rejection + endpoint rewrite, bridge
  warning/auth-token/scoped injection, smoke-test exit code, env-var creds.

---

## 8. Conformance suite — current standing and how to read it

**44 pass / 5 skip / 20 fail.** Files fully passing: checkout_lifecycle,
idempotency, discount, business_logic, totals, validation, protocol, ap2,
binding, card_credential. The 20 failures are N/A — classified in
`ucpcommerce/docs/README.md → Verification` (keep that table current):

| Category | Why N/A |
|---|---|
| Simulation endpoints (3) | `/simulation/*` + `SIMULATION_SECRET` are the sample server's test doubles |
| Webhooks (3) | no `order_webhook` capability advertised or implemented (no eventing on demo platform) |
| Order modification (5) | `PUT /orders/{id}` + adjustments + fulfillment expectations model merchant-ops simulation; no fulfillment process runs here |
| Flower-shop shipping fixtures (3) | expect `exp-ship-us`/`exp-ship-intl` option ids and CA delivery; ThinkShop is US-only with its own modes |
| Per-buyer address books (6) | expect buyer-email-scoped customers + CSV address ids (`addr_1`); this surface binds to the authenticated gateway customer, hybris address ids are PKs |

If you change wire behavior, re-run the suite and re-classify honestly —
do NOT chase the N/A categories by faking sample-server surfaces.

---

## 9. Outstanding work (the backlog, in priority order)

**Issue #4 — production hardening (OPEN, partially done).** Remaining:

1. **Owner binding on checkout sessions** (before any multi-user
   environment): add an owning-user attribute to `UcpCheckoutSessionEntry`,
   verify ownership on EVERY resolve — especially `cancel()` (never loads
   the cart) and `get()` on a COMPLETED checkout (returns the stored payload
   before `loadCart()` runs) — plus a cross-customer isolation test.
2. **Atomic completion claim**: make `beginCompletion` a conditional
   transition (`UPDATE … WHERE status='ready_for_complete'` semantics)
   returning whether the claim won; staleness bound so a crashed
   `complete_in_progress` can be failed/re-driven (today every retry
   refreshes `lastAccessedAt` and wedges); `recordCompletion` must not
   silently no-op on an expired entry; integration-test the
   begin/record/fail round-trip.
3. Remaining wire hygiene: confirm net-vs-gross tax handling + `totalTax`
   marshaller test; per-line rounding drift can emit a POSITIVE "discount"
   (unit price rounded to minor before ×qty) and the `cart.getSubTotal()`
   fallback silently reintroduces the BOGO double-count — test both paths;
   `completionResponseJson` is `HYBRIS.LONG_STRING` (~4k on some DBs) —
   the NEW `UcpIdempotencyRecord.responseJson` already uses the
   MySQL-`MEDIUMTEXT` columntype override as the pattern to copy; stop
   reflecting raw exception messages to the wire (MCP dispatcher
   `"Internal error: " + e.getMessage()`, REST parse errors); escape/strip
   `:` in catalog queries before composing the OCC search-state string
   (breaks "16:9 monitor"; allows sort/facet injection).

Already done from #4 (don't redo): credential stripping, cleanup-sweep
exclusions + retention, `completedFallback` on same-key/no-stored-response,
case-insensitive coupons, all of item 5 (script hardening).

**Issue #6 — React storefront (OPEN, untouched).** `sap-ui-template-react/`
boots and its OCC API client works against the live backend, but all 7
routes in `src/App.tsx` are placeholder divs. The issue text has the full
task list (validate → reconcile docs → complete pages → env-driven creds →
verify the human journey against the same store the agents use).

**Operational note:** two cloudflared tunnels + the OLD (pre-hardening)
bridge may still be running from the claude.ai demo session — an
unauthenticated public exposure that can place orders as john.doe. Restart
the bridge to pick up `--auth-token`, or kill the tunnels
(`ps aux | grep -E "cloudflared|ucp-mcp-bridge"`). Also: if the profile
must advertise a tunnel URL, set `ucpcommerce.public.base.url` (runtime
property changes are lost on server restart).

---

## 10. Gotchas and hard-won lessons (read before touching the code)

- **`yclean` before `ybuild`, always** — incremental compilation silently
  ships stale `.class` files; the symptom is "my change has no effect".
- **`CheckoutFacade.setDeliveryAddress(addressData)` with an unknown id
  resolves to null, CLEARS the cart's address, and returns `true`.** Never
  trust its return for id validation — resolve against
  `userFacade.getAddressBook()` first (that's what
  `findSavedAddress` in `DefaultUcpCheckoutService` is for).
- **Six Drools rules are published**, not three: `free_shipping_1000`,
  `bogo_mouse`, `ucp_10off_coupon`, plus `laptop_10pct_coupon`,
  `headphones_10pct` (AUTOMATIC 10% on WIRELESS_HEADPHONES),
  `speaker_5pct_coupon`. Any fixture math must use a promotion-free SKU —
  that's exactly why `UCP_CONFORMANCE_ITEM` exists.
- **The conformance suite hard-codes things**: a 3500-minor-unit base price
  in `test_fulfillment_flow` (hence the $35.00 fixture), the
  `mock_payment_handler` id (hence the declared alias), and per-file flags
  only work in the absltest form (hence the per-file runner).
- **The suite is discovery-driven**: it reads the REST endpoint from
  `/.well-known/ucp` — that's why the proxy rewrites advertised endpoints
  to itself. The reference client, by contrast, uses fixed paths on
  `--server_url`.
- **Unit tests use config seams**, not the platform Config: services expose
  `protected getPinnedUcpVersion()` etc., overridden in anonymous
  subclasses in tests. Keep new config reads behind protected methods.
- **Replay tolerance**: completion responses stored before a field existed
  replay WITHOUT it (e.g. pre-`permalink_url`, pre-envelope-handlers rows).
  Never make replay paths strict about newer fields.
- **The e2e harness pages through order history** for its fixture checks —
  the demo DB has hundreds of orders from test runs; don't assert fixtures
  on page 0.
- Test scope rules: only `-Dtestclasses.extensions=ucpcommerce` (never the
  gradle passthrough tasks with -D — they run the whole platform suite);
  after items.xml changes run `ant yunitinit` once before integration tests.
- OCC/OAuth must use HTTPS 9002 (`curl -k`, self-signed).

---

## 11. Quick-start checklist for a new session

1. Read `mcp/legacy/sap-mcp-server-l/CLAUDE.md`, then
   `ucpcommerce/docs/README.md` and ADR 0004.
2. Check the server: `curl -k https://localhost:9002/occ/v2/electronics/.well-known/ucp`
   (start with `./gradlew startServer` if down; Solr may need
   `./scripts/index-solr.sh` after a full init).
3. Establish the green baseline (both e2e transports + smoke test) BEFORE
   changing anything, and re-establish it after — plus the reference client
   and conformance suite if you touched wire shapes.
4. Pick up work from §9. For issue #4 items, follow the existing patterns:
   items.xml change → `yupdatesystem` cycle; new queries via
   FlexibleSearch; tests mirror the existing suites.
