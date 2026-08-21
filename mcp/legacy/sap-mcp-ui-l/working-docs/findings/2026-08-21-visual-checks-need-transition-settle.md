---
date: 2026-08-21
ticket: THINK-UI-004
tier: trivial
stage: 6-implement
applies_to:
  area: styling | cross-cutting
  ticket_type: feature
kind: project-knowledge
status: unpromoted
promotion_target: repo CLAUDE.md (a "verifying visuals in this app" note) and working-docs/config.json `manualVerificationSurfaces`
---

## What happened

A dark-mode screenshot of the footer showed a **white** bottom strip, which reads as
`dark:bg-gray-900` being broken. It isn't. The screenshot caught a mid-transition frame:
the layout root carries `transition-colors duration-300` (`src/App.tsx:48`) and
`DarkModeProvider` adds the `dark` class in a post-mount effect
(`src/contexts/DarkModeContext.tsx:19-23`), so for ~300 ms after load the page is still
animating from light to dark. Measuring immediately after `page.goto()` reported the
container's background as `oklab(0.999994 …)` — effectively white. The same read after a
1 s settle returned `oklch(0.21 0.034 264.665)` = `gray-900`, the correct value.

## Context

- Hit during stage 6 on a footer change, using an ad-hoc Playwright script rather than the
  `tests/` suite (the suite was unrunnable — see below).
- Any automated visual check on this app is exposed: Playwright screenshots,
  `getComputedStyle` reads, and contrast measurement all land inside the transition window
  if taken right after navigation.
- A second occlusion trap in the same session: `<UserPicker>` renders whenever
  `auth.isLoggedIn()` is false (`src/App.tsx:22-24`), and its `.fixed.inset-0` overlay
  covers page chrome. Playwright's `isVisible()` still returns **true** for the occluded
  footer, so an `isVisible()` assertion passes while a human sees only the modal. Seeding
  `localStorage['occ_access_token']` suppresses the modal for layout-only checks.

## The fix / the knowledge

When verifying anything color- or layout-related in this app:

1. Wait out the transition before reading or screenshotting —
   `await page.waitForTimeout(1000)` after `goto()` (300 ms transition + effect + paint).
2. Suppress `UserPicker` by seeding a token, or the overlay hides what you're checking:
   `localStorage.setItem('occ_access_token', '<anything>')`.
3. Set the theme before boot via `addInitScript`, key `darkMode`, string `'true'`/`'false'`
   (`DarkModeContext.tsx:11,19`).
4. Prefer a geometric or computed-value assertion over eyeballing a screenshot —
   `isVisible()` alone does not mean a human can see it.

Convert authored colors through a 1×1 canvas before doing contrast math: Tailwind 4 emits
`oklch()`, which no hand-kept hex table will match.

## Why this generalizes

Every ticket that touches styling, layout, or dark mode on this repo will verify it
visually, and all three traps are properties of the app shell rather than of any one
ticket — the transition on the root div, the auth-gated modal, and the oklch palette.
Each one produces a *confident wrong answer* rather than an error, which is the expensive
kind: the first reading here nearly became a bug report against working dark-mode CSS.

Related: the e2e gate is still unrunnable end-to-end
(`2026-06-19-e2e-gate-runnability.md`). Beyond the `baseURL :5175` vs Vite `:5173`
mismatch recorded there, the specs call the real backend unmocked, so with SAP Commerce
down all 10 fail. Confirmed no-regression by stashing the change and re-running:
10 failed before, 10 failed after. Ad-hoc scripts were the only way to actually verify
this change.

## Promotion suggestion

1. **repo CLAUDE.md** — add under a "Verifying visuals" heading: the 1 s settle wait, the
   `occ_access_token` modal suppression, the `darkMode` key, and the canvas/oklch note.
2. **`working-docs/config.json` `manualVerificationSurfaces`** — extend the responsive/dark
   entry to read: "wait ~1 s after load before judging colors (`transition-colors
   duration-300` on the layout root, `dark` class applied post-mount)".
3. Consider a committed `tests/` helper exposing `seedSession({theme})` so specs and
   ad-hoc checks share one correct setup instead of re-deriving it.
