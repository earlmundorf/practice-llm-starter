# CLAUDE.md

This is the root configuration file for [Claude Code](https://claude.ai/code) in the ThinkShop UI project.

## What This Project Is

A standalone React storefront for SAP Commerce (Hybris) via OCC REST APIs. Designed for static hosting on CloudFront/S3, with a configurable backend URL for any SAP Commerce instance.

- **Backend:** SAP Commerce 22.11 OCC API (configurable via `VITE_API_URL`)
- **Reference app:** `sample/thinkshop-frontend/` (read-only, do not modify)
- **Planning docs:** `docs/` — architecture, feature specs, endpoint mapping, setup guide

## Tech Stack

- **Framework:** React 19, TypeScript
- **Styling:** Tailwind CSS 4
- **Build Tool:** Vite 7
- **Router:** React Router 7
- **Code Quality:** ESLint

## Commands

```bash
npm install      # Install dependencies
npm run dev      # Start dev server (proxies to SAP Commerce via VITE_API_URL)
npm run build    # Production build → dist/ (deployable to S3/CloudFront)
npm run lint     # Run ESLint
```

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_URL` | `https://localhost:9002` | SAP Commerce backend URL (dev proxy target and production API base) |

Copy `.env.example` to `.env` and configure for your environment.

## Deployment

### Local Development
Vite proxies `/occ` and `/authorizationserver` requests to the backend URL, stripping the Origin header to avoid SAP Commerce CORS rejections.

### Production (CloudFront/S3)
Run `npm run build` and deploy the `dist/` directory to S3. Set `VITE_API_URL` to the production Commerce API URL at build time. Configure CloudFront to serve `index.html` for all routes (SPA fallback).

## Project Structure

```
ThinkShop-UI/
├── docs/              # Architecture & planning docs
├── sample/            # Read-only reference app (do not modify)
├── src/
│   ├── components/    # Reusable UI components
│   ├── pages/         # Page-level components
│   ├── services/      # API calls (OCC endpoints)
│   ├── contexts/      # React Context providers
│   ├── types/         # TypeScript type definitions
│   └── assets/        # Static assets
├── public/            # Vite public assets
├── vite.config.ts     # Vite config (proxy, configurable backend URL)
├── .env.example       # Required env vars reference
└── .claude/skills/    # Claude Code skills for SAP Commerce
```

## Skills & Workflow

| Skill (`.claude/skills/`) | Use it for |
|---|---|
| `react-ecommerce` | Code review against this repo's conventions |
| `qrspi` | Structured ticket workflow — `/cq:go <TICKET> [tier]` (trivial/simple/full/comprehensive). Stack-neutral; this project's specifics live in `working-docs/config.json` |

QRSPI verification verbs + research layers resolve from `working-docs/config.json`
(committed, along with `working-docs/findings/`; the rest of `working-docs/` is gitignored
per-ticket scratch). This project has **real Playwright e2e** (`tests/*.spec.ts`), so
`E2E_TEST` (`npx playwright test`) is a live checkpoint verb — there's no unit runner, so
the gate is TYPECHECK + LINT + BUILD + e2e. The skill learns across tickets via
`working-docs/findings/` (stage 1 loads matching findings; stage 7 captures + proposes
promotion).

`.claude/skills/qrspi/` and `.claude/commands/cq/` are **installed** by the QRSPI kit
(`qrspi-kit/install.sh react-storefront`, or `install.ps1` on Windows) and replaced wholesale
on update — never hand-edit a stage there; edit `qrspi-kit/skills/qrspi/commands/` and
re-install. `.installed-from` records the profile and kit version behind this copy.

## Documentation Convention

Each feature flow has a dedicated directory in `docs/` with three files:

| File | Purpose |
|------|---------|
| `context.md` | What the flow does, when it's used, key decisions |
| `components.md` | The files that implement it and what each one does |
| `diagram.md` | Mermaid diagrams with descriptive context |

**Before working on a feature**, read its flow directory. **When adding a new feature**, create the flow directory first — docs before code.

Current flows: `authentication/`, `product-browse/`, `cart-management/`, `checkout-flow/`, `order-history/`

## Code Style Guidelines

### Components

- Use **functional components only** (no class components)
- Use **arrow functions** for component definitions
- Keep components **under 150 lines** — split if larger
- Always handle **loading** and **error** states

```tsx
// Correct
export const Button = ({ label, onClick }: ButtonProps) => {
  return <button onClick={onClick}>{label}</button>;
};
```

### TypeScript

- Prefer **interfaces** over types for object shapes
- Use **named exports** over default exports
- Define props interfaces above the component

### Tailwind CSS

Organize classes in this order: **layout -> spacing -> sizing -> colors -> effects**

```tsx
// Organized
<div className="flex items-center gap-4 p-4 w-full bg-white rounded-lg shadow-md">
```

### API Calls

- All API logic goes in `src/services/`
- Use async/await with proper error handling
- Return typed responses
- API calls target SAP Commerce OCC endpoints (see `docs/endpoint-mapping.md`)

### Smoke-testing API methods in the dev console

`api` is an ES module export, not a global — a bare `api.x()` in the browser console
throws `ReferenceError: api is not defined`. With `npm run dev` running, pull the module
in first:

```js
const { api } = await import('/src/services/api.ts');
await api.searchProducts('shoe');
```

A red `404 (Not Found)` line in the console on a method that returns `null`/`[]` for
missing data is the browser logging the network response — not a thrown error; the call
still resolves.

## File Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Components | PascalCase | `ProductCard.tsx` |
| Hooks | camelCase with `use` prefix | `useAuth.ts` |
| Utilities | camelCase | `formatDate.ts` |
| Types | PascalCase | `Product.ts` |
| Services | camelCase | `api.ts` |

## Visual Product Search

- **Location:** Integrated into the Products page search bar (`src/pages/Products.tsx`)
- **Trigger:** Camera icon button in search bar, or paste an image (Cmd+V) while search bar is focused
- **API:** `api.visualSearch(base64, mimeType)` in `src/services/api.ts`
- **Backend:** `POST /{baseSiteId}/agent/visual-search` (same auth as other OCC endpoints)
- **Response:** Full OCC-shaped products mapped via `mapOccProduct`, with `aiDetail` (reasoning, searchTerms) and match badges
- **Match types:** `bestMatch` (Best Match), `similar` (Similar), `explore` (You Might Like)
- **Types:** `VisualSearchResult`, `VisualSearchMatch`, `MappedVisualSearchMatch`, `VisualSearchAiDetail` in `src/types/index.ts`
- **Display:** Uses same `ProductCard` component as normal search, with match type badge overlay
- **Mobile:** Uses `capture="environment"` for native camera, 44px+ touch targets

## Help Center

- **Routes:** `/help` (`HelpCenter`, `src/pages/HelpCenter.tsx`) and `/help/:uid` (`HelpDetail`, `src/pages/HelpDetail.tsx`). Public — no auth required.
- **API:** `api.searchKnowledge({ q?, category?, pageSize? })` returns `{ results, count }`; `api.getKnowledgeEntry(uid)` returns `KnowledgeEntry | null`. Both in `src/services/api.ts`. Public fetch (no `authFetch`), safe defaults on failure (empty result / null), never throw.
- **Backend:** `GET ${OCC_BASE}/info/search` and `GET ${OCC_BASE}/info/{uid}` (coremcp). `pageSize` is server-clamped (default max 50); no offset/cursor.
- **Types:** `KnowledgeEntry`, `KnowledgeSearchResult`, `KnowledgeCategory`, `KNOWLEDGE_CATEGORIES` const in `src/types/Knowledge.ts` (re-exported from `src/types/index.ts`). Categories: `policy, event, promo, guide, brand, howto, contact, loyalty`.
- **URL state:** `/help` mirrors `Products.tsx` — `?q=` (300 ms debounce) + `?category=`, `setSearchParams(..., { replace: true })`, merge-then-mutate. Empty `q` is omitted so `/help` is canonical.
- **Markdown:** Entry `body` rendered via shared `<Markdown>` (`src/components/Markdown.tsx`) — same component used by `Chat.tsx`. Plugins: `remarkGfm`, `rehypeExternalLinks` (links open in new tab). No sanitization (content is server-controlled).
- **Not-found:** `/help/:uid` renders an inline not-found block per the `ProductDetail.tsx` pattern. No catch-all `<NotFound>` route.
- **Spec:** `tests/help-center.spec.ts` covers index render, search-debounce → URL, category-chip toggle, click-through, and direct not-found navigation. Selectors: `getByRole` + `aria-label` (no `data-testid`).

## Don'ts

- Don't modify files in `sample/` — it's a read-only reference
- Don't use class components
- Don't use default exports
- Don't create inline styles (use Tailwind)
- Don't ignore TypeScript errors with `@ts-ignore`
- Don't leave console.logs in production code
