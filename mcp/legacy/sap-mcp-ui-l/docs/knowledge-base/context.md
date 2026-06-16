# Knowledge Base — Context

## What this flow does

Provides the storefront's typed client for reading **knowledge-base (KB) content** —
policies, how-tos, events, promos, brand/about copy — that the backend serves over public
REST at `/info/*`. Before this flow, KB content was reachable only indirectly through the
chat agent (`info_search`/`info_get` tools); this gives pages and components a direct,
typed way to read the same content.

This flow is the **data layer only** (ticket KB-01). The human-facing surfaces that consume
it — the Help center pages (KB-02) and the footer policy links + `/about` page (KB-03) —
build on top of it.

## When it's used

- Any page/component that needs to list or display KB entries (Help center, footer links,
  About page).
- Reads are **anonymous** — the `/info/*` endpoints require no auth token, unlike most OCC
  endpoints which go through `authFetch`.

## Key decisions

- **Public fetch, not `authFetch`.** `/info/*` is public; the methods use the plain
  `fetch(..., { cache: 'no-store' })` pattern (same as `products/search`), send no
  Authorization header, and have no cart/auth side effects.
- **Quiet degradation.** KB is non-critical chrome, so failures must not break a host page:
  `searchKnowledge` returns `{ results: [], count: 0 }` on any non-OK response, and
  `getKnowledgeEntry` returns `null` on 404 (and any non-OK) — neither throws. This follows
  the existing precedent of `getDeliveryModes` (`→ []`) and `getAgentCapabilities`
  (`→ { vision: false }`).
- **`category` is `string`, not a union.** Tolerant of new backend categories (the contract
  notes "+ loyalty content" beyond the documented set).
- **Mapper boundary.** Raw JSON is passed through `mapKnowledgeEntry` like every other
  response (`mapOccProduct`, etc.), so any future backend drift is contained to one mapper.

## Accepted risks

The `/info` response shape and the server-side `pageSize` clamp are documented only in the
backend's coremcp endpoint reference, not corroborated inside this repo. Mitigation: the
client does not reshape (beyond field mapping) or clamp, and the mapper tolerates missing
fields — so if the live contract differs, the blast radius is `mapKnowledgeEntry` + the
`KnowledgeEntry` interface only.
