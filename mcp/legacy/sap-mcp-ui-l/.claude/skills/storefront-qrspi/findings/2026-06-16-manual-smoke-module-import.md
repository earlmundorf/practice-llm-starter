---
date: 2026-06-16
ticket: KB-01
tier: simple
stage: 6-implement
applies_to:
  area: api-types
  ticket_type: data
kind: project-knowledge
status: promoted
promotion_target: repo CLAUDE.md → "Smoke-testing API methods in the dev console"
---

## What happened

A manual smoke that says "in the browser console, call `api.searchKnowledge(...)`" fails
with `Uncaught ReferenceError: api is not defined` — `api` is an ES module export in
`src/services/api.ts`, not a global, so the bare reference can't resolve. The working
recipe in Vite dev is `const { api } = await import('/src/services/api.ts')` first.

## Context

Stage 6 manual smoke for any api-layer ticket on this Vite/React storefront. The api
boundary (`src/services/api.ts`) exports `api`/`auth` as module consts; the dev server
serves source modules, so dynamic `import('/src/...')` resolves them in the console.

## The fix / the knowledge

When writing Manual success criteria (stage 3) or running the smoke (stage 6) for an
api-layer change, prefix console steps with:
`const { api } = await import('/src/services/api.ts');` then `await api.method(...)`.
404/non-OK paths log a red network line in the console — that is the browser logging the
response, not a thrown error; the call still resolves (here, to `null`).

## Why this generalizes

Every KB ticket (KB-02, KB-03) and any future api-layer ticket will hit this. The naive
"call api.x() in console" instruction is wrong for this repo's module setup and wastes a
smoke round.

## Promotion suggestion

Add a one-liner to the smoke guidance (repo CLAUDE.md "Visual Product Search"-style note,
or the skill's stage-3/6 manual-criteria phrasing): "To exercise api methods in the dev
console, first `const { api } = await import('/src/services/api.ts')`." Confirm on KB-02
before promoting.
