# 0001 — Implement the MCP server as a native OCC v2 extension

**Status:** accepted (2025; recorded 2026-06)

## Context

We wanted SAP Commerce to be consumable by AI agents speaking the Model Context
Protocol. The alternatives were: (a) a standalone middleware service (Node/Java)
calling OCC REST from outside, (b) a BTP-side integration, or (c) a custom
extension inside the Commerce platform exposing MCP through the existing OCC v2
servlet.

## Decision

Build it as a custom extension (`coremcp`) whose controllers register into the
OCC v2 web context via the platform's `additional-web-spring-context.xml` hook,
under the standard `/{baseSiteId}/...` namespace, secured by the existing OAuth2 +
`@Secured` chain.

## Consequences

- Tool handlers call commerce **facades in-process** — no second network hop, no
  duplicated auth model, carts/sessions/promotions behave exactly as in any OCC call.
- Security, CORS, rate limiting, and deployment ride the platform's existing
  mechanisms; CCv2 deploys it like any other extension.
- The cost: the MCP server scales with (and only with) the Commerce API aspect,
  and protocol-level concerns (JSON-RPC session header) had to be implemented in
  a servlet not designed for them.
- When SAP's announced Storefront MCP server GAs, the overlap analysis is
  per-tool, not architectural — this extension already speaks the same protocol
  on the same platform (see docs/review/06).
