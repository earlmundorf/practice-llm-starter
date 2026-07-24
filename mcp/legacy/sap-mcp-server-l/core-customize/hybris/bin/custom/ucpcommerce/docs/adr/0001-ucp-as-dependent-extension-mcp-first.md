# 0001 — UCP as a dependent extension, MCP binding first (REST alongside)

**Status:** accepted (2026-07-23)

## Context

The `coremcp` extension already exposes 19 commerce tools over a proprietary
MCP dialect plus an internal LLM agent, all as thin adapters over standard
platform facades. The Universal Commerce Protocol (UCP, ucp.dev) is the open
capability-layer standard powering agentic shopping on Google AI surfaces; a
merchant server publishes a `/.well-known/ucp` profile and implements
capability operations over one or more transport bindings (REST, MCP, A2A,
Embedded). Nothing in the repo spoke UCP.

Alternatives considered: (a) grow UCP inside `coremcp`, (b) a standalone
`ucpwebservices`-style web extension with its own servlet, (c) a new dependent
sibling extension joining the existing OCC v2 context. For transports:
REST-first (official tooling works day one) vs MCP-first vs both at once.

## Decision

1. **New dependent extension `ucpcommerce`** (design R3), sibling of `coremcp`,
   with `<requires-extension name="coremcp"/>` (service reuse:
   `PromotionQueryService`, `KnowledgeSearchService`) plus the same four
   platform extensions `coremcp` declares. Controllers join the OCC v2 servlet
   via the verbatim `additional-web-spring-context.xml` mechanism.
2. **MCP binding ships first** (design R12) at
   `POST /occ/v2/{baseSiteId}/ucp/mcp` — deliberately distinct from the
   proprietary `/{baseSiteId}/mcp` dialect — with the **REST binding added
   alongside** in later phases as thin adapters over the same binding-agnostic
   capability services (`UcpCatalogService`/`UcpCheckoutService`/
   `UcpOrderService`). The profile advertises each transport only once it
   works.
3. **The UCP spec version is pinned**: `ucpcommerce.ucp.version=2026-04-08`
   (dated calver, per the `ucp-schema` releases current at decision time).
   DTOs, marshalling, tool names, and schema validation all target exactly
   this version; bumping it is a deliberate, reviewed change.

## Consequences

- `coremcp` stays untouched (design R2): its docs, ADRs, tests — including
  smoke-test's exactly-19-tools assertion — remain accurate; UCP is
  independently versionable as the calver spec moves.
- Two MCP dialects coexist on one server (proprietary header-session dialect
  vs stateless UCP binding with `meta["ucp-agent"]` per call) — paths and docs
  must stay clearly separate, and the UCP endpoint must tolerate a generic
  client's `initialize` harmlessly.
- The profile is served at a **non-root path**
  (`/occ/v2/{baseSiteId}/.well-known/ucp`, `ROLE_ANONYMOUS`) as a local-testing
  concession (design R6); production requires an edge rewrite from the domain
  root (see docs/README.md).
- ~~The Phase 1 profile shape follows the task runbook's discovery-manifest
  contract (`ucp.version` + top-level `capabilities`/`services`/
  `payment_handlers`); it is provisional until verified against the cloned
  pinned `ucp-schema` repo — any correction is confined to the profile DTOs
  and their tests.~~ **Resolved (2026-07-23, post-Phase-7):** the official
  repos were cloned and the provisional shapes were verified — several did
  NOT match. The corrections (profile registries inside the `ucp` object,
  negative discounts, `fulfillment` totals type, the fulfillment negotiation
  flow, `order.permalink_url`, payload-id tolerance) are recorded in
  **ADR 0003**; the prediction held that corrections stayed confined to
  DTOs/marshalling + tests + harness assertions.
- Official REST-first tooling (reference client, conformance suite) applies at
  the REST-binding phase rather than day one; until then verification is the
  transport-flagged `scripts/ucp-e2e.py` harness plus `ucp-schema validate`.
