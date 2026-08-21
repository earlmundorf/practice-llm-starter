# THINK-UI-005 — Make the Help center discoverable

- **Source:** generated (fallout from dropping KB-03, 2026-08-21)
- **Suggested tier:** simple
- **Area:** components | routing
- **Depends on:** none. THINK-UI-004 (shipped) made the footer render at every width, so
  the footer is now a viable home for these links.

## Problem

`/help` and `/help/:uid` are built and working, but **nothing in the UI links to them** —
a user can only reach the Help center by typing the URL or by being handed a link in chat.
KB-02 shipped the pages; KB-03 was going to add the footer/nav entry points and was
dropped unbuilt, so the entry points never landed. The knowledge base — returns, shipping,
warranty, privacy — is effectively invisible.

## Evidence / context

- Routes exist: `src/App.tsx:62-63` (`/help` → `HelpCenter`, `/help/:uid` → `HelpDetail`).
  Both are public, no auth required.
- **Desktop nav** (`src/components/Header.tsx:129`, `hidden md:flex`) links to `/`,
  `/products`, `/orders`, `/chat`, `/architecture` (lines 144-176). No `/help`. Each item
  follows one pattern — emoji + `<span className="hidden sm:inline">Label</span>` inside a
  `<Link>` with shared hover/rounded classes (see `Header.tsx:143-148` as the model).
- **Mobile bottom nav** (`Header.tsx:332`, `md:hidden fixed bottom-0`) already carries
  seven targets — Home, Shop, Chat, Cart, Orders, Theme, Login. It is visually full at
  375px, so it is likely the wrong place to add an eighth.
- **Footer** (`src/App.tsx:66-70`) now renders at every width after THINK-UI-004, is
  centered/muted, and holds only credits today.
- KB entry uids for policy content are **unconfirmed** — KB-03 named `returns-policy`,
  `shipping-info`, `warranty`, `privacy-policy`, `about-thinkshop`, but that list was never
  verified against a running backend. Confirm via `api.searchKnowledge(...)` or
  `GET /info/search?q=returns` before hard-coding any deep link.
- Help categories that do exist in code: `policy, event, promo, guide, brand, howto,
  contact, loyalty` (`KNOWLEDGE_CATEGORIES`, `src/types/Knowledge.ts`).

## Open decisions (resolve at design, before implementing)

1. **Placement** — footer link only, desktop-nav item only, or both? (Mobile is covered by
   the footer either way, given the bottom nav is full.)
2. **Depth** — a single "Help" link to `/help`, or the full KB-03-style set of deep policy
   links (Returns · Shipping · Warranty · Privacy)? Deep links require confirming uids
   first and bump this toward **full** tier.
3. **`/about`** — still unowned since KB-03 was dropped. In or out of this ticket?

## Acceptance criteria

- [ ] The Help center is reachable from persistent chrome on **both** mobile and desktop
      without typing a URL — no route added or changed, only entry points.
- [ ] Navigation uses `<Link>`, not `<a href>`, so it does not full-page reload.
- [ ] Any deep link to `/help/{uid}` resolves to real content — uid confirmed against the
      backend, not assumed from KB-03's list.
- [ ] The mobile bottom nav is not made cramped: either unchanged, or verified legible with
      44px+ touch targets at 375px.
- [ ] Footer additions preserve what THINK-UI-004 established: link text meets **WCAG AA
      4.5:1** at `text-xs` in both themes (including hover/visited states), and footer
      content still clears the fixed mobile nav.
- [ ] Verification: TYPECHECK + LINT + BUILD, plus a Playwright assertion that the entry
      point navigates to `/help` and renders the index. Manual: 375px and desktop, light
      and dark.

## Out of scope

- Changing `/help` or `/help/:uid` themselves, or the knowledge API client (KB-01/KB-02).
- Authoring or editing KB content (backend).
- Restyling the header or the bottom nav beyond adding an entry point.

## Notes for whoever picks this up

The e2e suite currently fails 10/10 with SAP Commerce down — the specs call the backend
unmocked. Expect to establish a stashed baseline for no-regression evidence rather than a
green suite, and read
`working-docs/findings/2026-08-21-visual-checks-need-transition-settle.md` before doing any
visual or contrast verification: this app's `transition-colors duration-300` plus its
post-mount `dark` class will hand you pre-transition colors, and `UserPicker`'s overlay
occludes chrome while `isVisible()` still returns true.
