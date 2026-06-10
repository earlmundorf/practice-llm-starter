# 0002 — MCP session state: in-memory → DB-persisted item type

**Status:** accepted (2026-06, supersedes the initial in-memory design)

## Context

MCP sessions (id → cart code, client info, last access) were initially held in a
`ConcurrentHashMap` with lazy TTL eviction — simple and fast, but single-node.
CCv2 API aspects run multiple nodes; ingress stickiness is **cookie-based**, and
MCP/LLM agent clients identify sessions via the `MCP-Session-Id` header, not
cookies, so consecutive requests of one conversation can land on different nodes.
Rolling deployments also wipe node memory mid-conversation. SAP's sanctioned
cluster-state mechanism is DB persistence (the platform's HTTP Session Failover
is Spring Session over the database; the region cache is invalidation-based and
not a shared store).

## Decision

Persist sessions as `McpSessionEntry` items (typecode 14002) via
`PersistedMcpSessionService`. A `DelegatingMcpSessionService` selects the backing
store at boot from `coremcp.session.store` (default `persistent`; `memory` retained
for unit tests and pre-`yupdatesystem` local bootstraps). Cart-code updates flow
through `McpSessionService.updateCartCode(...)` — never by mutating the returned
DTO, which only worked by object identity in the in-memory store. Expired rows
are removed lazily on access plus a 30-minute cleanup cronjob
(`mcpSessionCleanupCronJob`).

## Consequences

- Conversations survive node hops and rolling deploys; any node serves any request.
- One DB read/write per MCP request — acceptable at demo/pilot scale; add a
  read-through cache in front of the store if profiling ever shows it hot.
- The junit tenant must be re-initialized after the items.xml change
  (`ant yunitinit`) — recorded in CLAUDE.md.
