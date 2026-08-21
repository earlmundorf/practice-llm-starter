# THINK-UI-004 — Footer credits the Capgemini SAP CX team

- **Source:** generated (developer request, 2026-08-21)
- **Suggested tier:** trivial
- **Area:** components | styling
- **Depends on:** none

## Problem

The storefront footer credits the platform but not the team that built the app. It should
name the Capgemini SAP CX team as the producer. Today the credit is also invisible to
mobile users, so on phones the app carries no attribution at all.

## Evidence / context

- The footer is an **inline element, not a component**: `src/App.tsx:66-68`

  ```tsx
  <footer className="hidden md:block text-center py-3 text-xs text-gray-400 dark:text-gray-600">
    Powered by SAP Commerce
  </footer>
  ```

  There is no `Footer` component — `src/components/` has no `Footer.tsx`, and no ticket
  plans one, so this inline element is where the change lands.
- `hidden md:block` hides it below the `md` breakpoint — mobile never renders it.
- The mobile bottom nav is `md:hidden fixed bottom-0 left-0 right-0 … z-40`
  (`src/components/Header.tsx:332`). The footer is a **sibling of** the scroll container
  that carries `pb-16 md:pb-0` (`src/App.tsx:50`), not a child of it — so the footer
  inherits no clearance and a mobile-visible footer will sit under the fixed nav unless it
  gets its own bottom padding (nav is ~64px → `pb-20` or similar, dropped at `md`).
- Tailwind class order per CLAUDE.md: layout → spacing → sizing → colors → effects, with
  `dark:` variants alongside their light counterparts.

## Decisions (agreed with the developer, 2026-08-21)

1. **Text** — one combined line replacing the current text, keeping both facts:
   `Produced by the Capgemini SAP CX team · Powered by SAP Commerce`
   ("Capgemini" is one word — do not render "Cap Gemini".)
2. **Visibility** — show on all viewports; drop `hidden md:block`.

## Acceptance criteria

- [ ] The footer reads `Produced by the Capgemini SAP CX team · Powered by SAP Commerce`
      on every route.
- [ ] The footer is visible at mobile widths as well as desktop, and does **not** sit
      under or overlap the fixed mobile bottom nav — the credit is fully readable at
      375px wide.
- [ ] Existing footer styling intent is preserved: centered, small, muted, and legible in
      both light and dark mode.
- [ ] Verification: TYPECHECK + LINT + BUILD, and `npx playwright test` stays green
      (E2E_TEST — `tests/` has live specs; no new spec required for a text change, though
      a footer-text assertion is welcome). Manual: `npm run dev`, then check one route at
      375px and at desktop width, in light and dark mode, confirming no nav overlap.

## Out of scope

- Extracting the footer into a `Footer` component. It stays inline in `App.tsx`.
- Footer policy/About links, logos, legal text, or a copyright line.
- Restyling the Header or the mobile bottom nav.
