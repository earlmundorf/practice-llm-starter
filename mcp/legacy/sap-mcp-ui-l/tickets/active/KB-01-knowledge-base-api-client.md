# KB-01 — Knowledge base API client + types

- **Source:** generated
- **Suggested tier:** simple
- **Area:** api-types
- **Depends on:** none (foundation for KB-02, KB-03)

## Problem

The storefront has no way to read the knowledge base except through the chat agent. The
backend exposes the same content over public REST (`/info/search`, `/info/{uid}`), but
`src/services/api.ts` has no methods for it, so no page or component can use it. This
ticket adds the typed client layer the KB UI tickets build on.

## Evidence / context

- `src/services/api.ts` has zero `/info` methods (`grep "/info" src/services/api.ts` → none).
- The endpoints are **public** (no token) — model them on the plain `fetch` used for
  `products/search` at `api.ts:313`, not `authFetch`.
- Backend contract (authoritative: coremcp `docs/reference/endpoints.md`):
  - `GET ${OCC_BASE}/info/search?q=<text>&category=<cat>&pageSize=<n>`
    → `{ results: [{ uid, category, title, summary, body, tags, priority, imageUrl }], count }`.
    `pageSize` is clamped server-side (default max 50). `q` empty = browse.
  - `GET ${OCC_BASE}/info/{uid}` → one entry (same shape) or 404 `{ error, uid }`.
  - Categories: `policy, event, promo, guide, brand, howto, contact` (+ loyalty content).

## Acceptance criteria

- [ ] `KnowledgeEntry` interface in `src/types/` matches the contract fields above.
- [ ] `api.searchKnowledge({ q?, category?, pageSize? })` → typed `{ results, count }`,
      public fetch, URL-encoded params, returns `[]`/`count:0` on a non-OK response
      (KB is non-critical chrome — don't throw and break a page).
- [ ] `api.getKnowledgeEntry(uid)` → typed entry, returns `null` on 404 (not a throw).
- [ ] No auth header sent (endpoints are anonymous); no `cartUpdated`/auth side effects.
- [ ] Verification: TYPECHECK + LINT + BUILD. (No UI yet — consumed by KB-02/03.)

## Out of scope

Any page or component (KB-02), footer or nav links (KB-03). This is the data layer only.
