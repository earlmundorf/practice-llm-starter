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
- `ucp-schema validate` runs best-effort when the CLI is installed;
  otherwise those checks report SKIP.

### External follow-up: official conformance suite / reference client

The official UCP [`conformance`](https://github.com/Universal-Commerce-Protocol/conformance)
suite and [`samples`](https://github.com/Universal-Commerce-Protocol/samples)
REST reference client have **not** been run against this server — the
implementation environment had no network access to clone/run them. When
running them, note the two documented local concessions:

- **Non-root profile path** (R6): pass the explicit profile URL
  `https://localhost:9002/occ/v2/electronics/.well-known/ucp` (or front the
  server with the edge rewrite above).
- **Bearer-token auth** (R8): inject the password-grant `Authorization`
  header (john.doe@thinkshop.com) — the tooling's auth-header/env injection
  point is required since real UCP clients do not present merchant OAuth
  tokens.

Record the results here once run. Until then, the verified substitute is the
both-transport e2e parity described above (see ADR 0002 — Consequences).
