# KB-03 — Footer policy links + /about brand page

- **Source:** generated
- **Suggested tier:** simple
- **Area:** components | routing
- **Depends on:** KB-01 (and KB-02 for the `/help/:uid` detail route the footer links into)

## Problem

A storefront is expected to expose returns, shipping, warranty, and privacy from a
persistent footer, and to have an "About" page. This app has a `Header` but **no footer
at all**, and the brand story (now "part of Capgemini Fast Commerce") lives only in the
`about-thinkshop` KB entry, reachable only via chat. Add the standard chrome.

## Evidence / context

- `src/components/` has `Header.tsx` but no `Footer` (`ls src/components | grep -i footer`
  → none). The footer is new.
- KB entries exist for these links — uids: `returns-policy`, `shipping-info`, `warranty`,
  `privacy-policy`, `about-thinkshop` (confirm exact uids via `api.searchKnowledge` or
  `/info/search?q=returns`). Footer links target `/help/{uid}` (KB-02).
- `/about` can be a thin page that loads `about-thinkshop` via `api.getKnowledgeEntry`
  and renders it as the brand story — or simply redirect `/about` → `/help/about-thinkshop`
  if a dedicated layout isn't warranted (design decision for stage 3).

## Acceptance criteria

- [ ] A `Footer` component, rendered on every page (mount alongside `Header` in the
      layout/`App.tsx`), with links: Returns · Shipping · Warranty · Privacy · About,
      each resolving to the right `/help/{uid}` (or `/about`).
- [ ] `/about` route renders the brand story from the `about-thinkshop` entry (showing the
      Capgemini Fast Commerce content), with loading/not-found states; or the agreed redirect.
- [ ] Links use `<Link>` (not `<a href>`); dark mode + responsive; footer doesn't overlap
      content on short pages.
- [ ] Verification: TYPECHECK + LINT + BUILD; Playwright check that footer links navigate
      to the correct help entries (E2E_TEST). Manual: every footer link lands on the right
      content; `/about` shows the brand story.

## Out of scope

The Help center pages themselves (KB-02). Restyling the Header. Any new KB content.
