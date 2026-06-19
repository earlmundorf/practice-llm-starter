# KB-02 — Help center: searchable list + entry detail pages

- **Source:** generated
- **Suggested tier:** full
- **Area:** routing | components | api-types
- **Depends on:** KB-01

## Problem

Shoppers can only get policy/how-to/event answers by opening the chat and asking. There's
no browsable, linkable, SEO-friendly Help surface — no way to land on "Returns Policy"
from a search engine or a footer link, no way to skim what help content exists. Give
humans the same knowledge the agent already uses, as real pages.

## Evidence / context

- Routes live in `src/App.tsx:50-59` (plain `<Route>` list under `BrowserRouter`); adding
  `/help` and `/help/:uid` is a two-line change there plus two page components.
- KB-01 provides `api.searchKnowledge(...)` and `api.getKnowledgeEntry(uid)`.
- `body` is the long-form field; entries may carry `imageUrl` and a `category`. The agent
  reaches this via `info_search`/`info_get` — this is the human equivalent.
- Follow the repo's page conventions (loading + error + **empty** states; URL-driven
  state via `useSearchParams` for the query/category filter so a filtered Help view is
  shareable). Match existing pages like `Products.tsx` for structure.

## Acceptance criteria

- [ ] `/help` — searchable list: query box + category filter, results show title +
      summary + category, click-through to detail. Query/category in the URL
      (`useSearchParams`), so `/help?q=returns` is shareable. Empty-result and load-error
      states handled.
- [ ] `/help/:uid` — entry detail: title, body (rendered; `body` may contain prose —
      reuse the repo's existing markdown rendering if entries are markdown, else plain),
      optional image, "back to Help". A missing uid (KB-01 returns null) renders a clean
      not-found state, not a crash.
- [ ] Both routes registered in `App.tsx`; reachable without login (KB is public).
- [ ] Dark mode + responsive per repo conventions.
- [ ] Verification: TYPECHECK + LINT + BUILD; add/extend a Playwright spec covering
      `/help` search → click → detail, and the not-found path (E2E_TEST). Manual: load
      `/help`, search "return", open the Returns entry; load `/help/about-thinkshop`.

## Out of scope

Footer links and the dedicated `/about` page (KB-03). Editing KB content (backend).
