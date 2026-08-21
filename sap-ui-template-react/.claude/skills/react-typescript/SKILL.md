---
name: react-typescript
description: |
  React + TypeScript + npm development assistant for this Vite storefront. Provides
  working knowledge of the stack (React 19, TypeScript, Vite 7, Tailwind CSS 4, React
  Router 7, ESLint), the npm toolchain, and this repo's structure — including the
  recipes for adding a page/route, a context, an API method, or a component the
  project's way. Use this skill for "how do I do X in this frontend?" questions and
  hands-on coding tasks. For code REVIEW use react-ecommerce; for backend/OCC
  integration specifics use commerce-storefront; for structured ticket workflows use
  qrspi.

  Trigger this skill when the user asks how to build or modify something in this React
  app, or mentions: component, page, route, hook, context, props, useState, useEffect,
  Vite, tsconfig, eslint, npm script, Tailwind, dark mode, React Router. Do NOT trigger
  for pure review requests (react-ecommerce) or ticket workflows (qrspi).
allowed-tools: [Read, Write, Edit, Grep, Glob, Bash(npm *), Bash(npx tsc *), Bash(find *)]
---

# React + TypeScript Development Skill

You are an expert React/TypeScript developer working in this Vite storefront.
**The repo's CLAUDE.md is authoritative** for conventions — functional components with
named exports, components under the line limit, props interfaces above the component,
all backend calls through `src/services/api.ts`, loading + error states everywhere,
Tailwind class ordering with `dark:` variants, `showToast()` feedback, format helpers.
This skill adds the how-to knowledge on top; it never contradicts CLAUDE.md.

## Stack and toolchain

| Tool | Version | Notes |
|---|---|---|
| React | 19 | Functional components only |
| TypeScript | strict | `npx tsc --noEmit` is the type gate (no test runner yet) |
| Vite | 7 | Dev server on :5173; proxies `/occ` + `/authorizationserver` to `VITE_API_URL` |
| Tailwind CSS | 4 | Via PostCSS; dark mode variants required on every color class |
| React Router | 7 | `react-router-dom`; routes declared in `App.tsx` |
| ESLint | flat config | `eslint.config.js` with react-hooks + react-refresh plugins |

```bash
npm install          # deps (npm + package-lock.json — don't introduce yarn/pnpm)
npm run dev          # dev server with backend proxy
npx tsc --noEmit     # typecheck — run after every change set
npm run lint         # eslint
npm run build        # production build → dist/
npm run build:dev | build:staging | build:prod   # mode-specific builds (env files)
npm run preview      # serve the built dist/ locally
```

Verification habit: `npx tsc --noEmit && npm run lint` after every change set, plus a
dev-server check for anything visual. There is no test runner yet (Vitest + RTL
planned) — don't invent test commands.

## Project structure and where things go

```
src/
├── App.tsx            # Route table — every new page registers here
├── pages/             # Page components: orchestrate (fetch, state, compose)
├── components/        # Reusable presentational components (props in, callbacks out)
├── contexts/          # Cross-cutting state: AuthContext, CartContext, DarkModeContext
├── hooks/             # Custom hooks (use* prefix)
├── services/api.ts    # THE backend boundary — every fetch lives here
├── types/index.ts     # All shared interfaces (OCC response shapes, app types)
└── utils/format.ts    # formatPrice / formatDate / formatOrderStatus
```

`docs/` holds feature-flow documentation (context/components/diagram per flow) — read
the flow before working on a feature; create the flow directory before building a new
one (docs before code).

## Recipes — the project's way

**Add a page + route:** create `src/pages/Thing.tsx` (named export, loading + error +
empty states), add `<Route path="/thing" element={<Thing />} />` in `App.tsx` inside the
`Layout` wrapper, link with `<Link to="/thing">`. URL-visible state (search, sort, page,
filters) goes in `useSearchParams`, not `useState`.

**Add an API method:** define/extend response interfaces in `src/types/index.ts`, add a
typed method on the `api` object in `src/services/api.ts` using the `apiFetch<T>` wrapper
(it handles auth header, 401 → `authExpired`, empty bodies). Components call `api.x()`,
never `fetch`. Mutating cart calls dispatch `window.dispatchEvent(new Event('cartUpdated'))`.

**Add a context:** `src/contexts/ThingContext.tsx` exporting the provider and a
`useThing()` hook that throws if used outside the provider; mount it in `main.tsx`'s
provider stack. Reach for context only when prop drilling exceeds ~2 levels — otherwise
compose.

**Add a component:** props interface above an arrow-function component, named export,
named event handlers (no non-trivial inline arrows in JSX), Tailwind classes in
layout → spacing → sizing → colors → effects order, every color with a `dark:` variant,
hover/focus/disabled states on interactive elements.

**Cross-component signals:** the app uses window events (`cartUpdated`, `authChanged`,
`authExpired`) where context would be overkill — subscribe in `useEffect` with cleanup.

## TypeScript and hooks discipline

Strict mode is on: no `any` leaks, no `@ts-ignore` (use `@ts-expect-error` with a reason
if truly unavoidable), prefer interfaces for object shapes, type guards over `as`.
Effects: every value read from scope goes in the dependency array; cleanup for
listeners/timers; never compute derived state in an effect (derive in render); never use
an effect as an event handler. `useCallback`/`useMemo` only with correct dependencies
and a real consumer.

## npm hygiene

Commit `package-lock.json`; never edit `node_modules/`. Adding a dependency is a design
decision — this template is deliberately lean (3 runtime deps); prefer the platform and
existing utilities before reaching for a library. Env config goes through
`import.meta.env.VITE_*` (define in `.env`, document in `.env.example`, never commit
`.env`). Mode-specific behavior uses Vite modes (`--mode staging`), not runtime checks.

## Related skills

- **react-ecommerce** — code review against these conventions (invoke for "review this")
- **commerce-storefront** — OCC/agent backend integration contracts and patterns
- **qrspi** — `/cq:go` structured ticket workflow
