---
date: 2026-06-19
ticket: KB-02
tier: full
stage: 7-validate
applies_to:
  area: build | cross-cutting
  ticket_type: feature
kind: workflow
status: unpromoted
promotion_target: commands/4_structure.md (add an "e2e-gate runnability" research item) and config.json `_notes`
---

## What happened

The KB-02 e2e gate (`npx playwright test`) was unrunnable out of the box because
`playwright.config.ts:7` hardcodes `baseURL=http://localhost:5175` while
`vite.config.ts` doesn't pin `server.port`, so `npm run dev` defaults to `:5173`.
The mismatch only surfaced at TG.1 — after Slices 1–4 were already implemented
and TYPECHECK + LINT + BUILD were green. Worked around with a gitignored
`working-docs/KB-02/playwright.local.config.ts` that overrides `baseURL` to
`:5173`.

## Context

- Stages 1–6 never exercise the dev server, so a port misalignment is invisible
  until stage 7.
- The repo has a real e2e gate (Playwright, `tests/*.spec.ts`) and `config.json`
  treats it as a checkpoint verb, but nothing verifies the gate's _plumbing_
  (port alignment, fixture presence) before the gate fires.
- A second instance of the same class of issue surfaced simultaneously: the
  pre-existing `tests/visual-search-*.spec.ts` specs require `/tmp/test-product.jpg`
  with no setup hook to create it, so the full suite has been red on any fresh
  machine since commit `15409ac` (initial). Not a KB-02 regression, but the same
  finding category (e2e plumbing not validated up front).

## The fix / the knowledge

For workflows on this repo (and any repo where the e2e config is decoupled from
the dev-server config): structure stage should include a one-shot "can the gate
actually run?" check that verifies (a) the playwright `baseURL` host:port
resolves to the same host:port `npm run dev` listens on, and (b) any
`setInputFiles(<absolute path>)` / fixture file referenced by an existing spec
is either present or has a `beforeAll`/`globalSetup` hook to materialize it.

Concrete repo fix (a separate, non-KB ticket): in `vite.config.ts`, set
`server.port = 5175` so it matches `playwright.config.ts:7`. That removes the
need for the per-ticket shim config.

## Why this generalizes

Any storefront ticket that touches a flow with a spec will hit this. The cost
is paid once — at validation time — by every ticket; promoting the check into
stage 4 (structure) makes every future ticket find it instantly. The fixture-
presence half of the rule generalizes to any test that hard-codes paths under
`/tmp` or any other ephemeral location.

## Promotion suggestion

1. **`commands/4_structure.md`** — add a step to the structure stage:
   > Before slicing, confirm the e2e gate is _runnable_ on this machine: (a)
   > `playwright.config.ts` `baseURL` host:port matches `vite.config.ts`
   > `server.port` (or its default), and (b) every fixture file referenced by
   > an existing spec resolves on disk or is materialized by `globalSetup` /
   > `beforeAll`. If either fails, surface as a finding before slicing.
2. **`config.json` `_notes`** — append:
   > "If `playwright.config.ts` `baseURL` ≠ Vite dev port, e2e is unrunnable
   > out of the box. Stage 4 must verify alignment."
3. Consider opening a tiny repo-fix ticket to set `vite.config.ts`
   `server.port = 5175` and add a `globalSetup` that creates
   `/tmp/test-product.jpg` from a seed image checked into `tests/fixtures/`.
