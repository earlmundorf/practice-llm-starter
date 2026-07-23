# ucpcommerce — documentation index

`ucpcommerce` exposes a **UCP (Universal Commerce Protocol)** surface over the
existing SAP Commerce facades, as a dependent sibling of `coremcp`. The
proprietary MCP dialect, agent endpoints, and all `coremcp` behavior remain
untouched — UCP is a strictly additive, standards-based third surface.

- Pinned UCP spec version: **`2026-04-08`** (`ucpcommerce.ucp.version` in
  `project.properties`; see ADR 0001 — dated calver strings are load-bearing).
- Transport strategy: **MCP binding first** at
  `POST /occ/v2/{baseSiteId}/ucp/mcp`, REST binding added alongside as thin
  adapters over the same binding-agnostic capability services (ADR 0001).

## Flows

| Flow | Status |
|------|--------|
| `ucp-profile` — anonymous discovery document at `/.well-known/ucp` | Phase 1 (profile served nearly empty; entries added as capabilities land) |
| `ucp-mcp-binding` — JSON-RPC tools endpoint | Phase 2+ |
| `ucp-checkout` — checkout-session lifecycle over `UcpCheckoutSessionEntry` | Phase 3+ |
| `ucp-rest-binding` — REST routes over the same services | Phase 7 |

(Per-flow `context.md`/`components.md`/`diagram.md` directories are created as
each flow lands — docs before code, per the repo's extending checklist.)

## ADRs

| ADR | Decision |
|-----|----------|
| [0001](adr/0001-ucp-as-dependent-extension-mcp-first.md) | UCP as a dependent extension; MCP binding first with REST alongside; spec version pinned |

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
