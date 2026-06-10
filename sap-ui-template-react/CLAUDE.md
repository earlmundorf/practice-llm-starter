# CLAUDE.md

This is the root configuration file for [Claude Code](https://claude.ai/code) in this SAP Commerce UI project.

## What This Project Is

A standalone React storefront for SAP Commerce (Hybris) via OCC REST APIs. Designed for static hosting on CloudFront/S3, with a configurable backend URL for any SAP Commerce instance.

- **Backend:** SAP Commerce 22.11 OCC API (configurable via `VITE_API_URL`)
- **Planning docs:** `docs/` — feature flows, endpoint mapping, architecture

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
SAP-UI-Template/
├── docs/                  # Feature flow documentation (three files per flow)
├── src/
│   ├── components/        # Reusable UI components (Header, Layout, Toast, CartModal, etc.)
│   ├── pages/             # Page-level components (Products, Checkout, Orders, etc.)
│   ├── services/api.ts    # OCC API client — all backend calls go through here
│   ├── contexts/          # React Context providers (AuthContext, CartContext, DarkModeContext)
│   ├── hooks/             # Custom React hooks
│   ├── types/index.ts     # TypeScript type definitions for OCC responses
│   ├── utils/format.ts    # Formatting helpers (prices, dates, order status)
│   └── assets/            # Static assets
├── public/                # Vite public assets
├── vite.config.ts         # Vite config (proxy, path aliases, configurable backend URL)
├── .env.example           # Required env vars reference
└── .claude/skills/        # Claude Code skills
```

### Key Files

| File | Purpose |
|------|---------|
| `src/types/index.ts` | All OCC response interfaces — Product, Cart, Order, etc. |
| `src/services/api.ts` | API client with auth, cart management, and all OCC calls |
| `src/utils/format.ts` | `formatPrice()`, `formatDate()`, `formatOrderStatus()` |
| `src/contexts/AuthContext.tsx` | Auth state + `useAuth()` hook |
| `src/contexts/CartContext.tsx` | Cart state + `useCart()` hook (listens for `cartUpdated` events) |
| `src/contexts/DarkModeContext.tsx` | Dark mode toggle + `useDarkMode()` hook |
| `src/components/Layout.tsx` | Wraps all routes with Header + Toast container |
| `src/components/Toast.tsx` | Toast notifications via `showToast('success', 'Added to cart')` |

## Skills & Workflow

| Skill (`.claude/skills/`) | Use it for |
|---|---|
| `react-typescript` | Development how-to: stack, npm toolchain, add-a-page/context/API-method recipes |
| `commerce-storefront` | Backend integration: OCC contracts, auth/cart lifecycle, the coremcp agent/knowledge/visual-search endpoints |
| `react-ecommerce` | Code review against this repo's conventions |
| `storefront-qrspi` | Structured ticket workflow — `/cq:go <TICKET> [tier]` (trivial/simple/full/comprehensive) |
| `spartacus-storefront` | **Explicitly chosen Angular path** — SAP Composable Storefront (Spartacus 6.x) knowledge with 9 topic references. Never applies to this repo's React code; moves to the Composable Storefront repo when it exists |

QRSPI verification verbs resolve from `working-docs/config.json` (committed; the rest
of `working-docs/` is gitignored per-ticket scratch). Note: `UNIT_TEST` is `MANUAL:`
until a test runner is added — verify with `npx tsc --noEmit && npm run lint` plus a
dev-server check.

## Documentation Convention

Each feature flow has a dedicated directory under `docs/` with three files:

| File | Purpose |
|------|---------|
| `context.md` | What the flow does, when it's used, key decisions |
| `components.md` | The files that implement it and what each one does |
| `diagram.md` | Mermaid diagrams with descriptive context |

**Before working on a feature**, read its flow directory. **When adding a new feature**, create the flow directory first — docs before code.

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

- Organize classes in this order: **layout -> spacing -> sizing -> colors -> effects**
- **Dark mode:** add `dark:` variants on every color class (backgrounds, text, borders)
- **Responsive:** mobile-first — base styles, then `sm:`, `md:`, `lg:` breakpoints

```tsx
// Organized, with dark mode
<div className="flex items-center gap-4 p-4 w-full bg-white dark:bg-gray-800 rounded-lg shadow-md">
```

### API Calls

- All API logic goes in `src/services/api.ts` — no direct `fetch()` in components
- Use async/await with proper error handling
- Return typed responses from `src/types/index.ts`
- Show user feedback on success (`showToast()`) and error (inline message or toast)
- API calls target SAP Commerce OCC endpoints (see `docs/endpoint-mapping.md`)

### Toasts

Use the `showToast()` helper from `src/components/Toast.tsx`:

```tsx
import { showToast } from '../components/Toast';
showToast('success', 'Added to cart');
showToast('error', 'Failed to place order');
```

### Formatting

Use helpers from `src/utils/format.ts` — never format prices or dates inline:

```tsx
import { formatPrice, formatDate, formatOrderStatus } from '../utils/format';
```

### Forms

- Use controlled inputs with `useState`
- Validate on submit, show inline errors per field
- Disable submit button during API calls (prevent double-submit)
- Clear errors when the user edits the field

## File Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Components | PascalCase | `ProductCard.tsx` |
| Hooks | camelCase with `use` prefix | `useAuth.ts` |
| Utilities | `utils/` + camelCase | `format.ts` |
| Types | `types/index.ts` (single file) | `Product`, `Cart`, `Order` interfaces |
| Contexts | PascalCase + `Context` suffix | `AuthContext.tsx` |
| Services | camelCase | `api.ts` |

## Testing

No test framework is configured yet. When added, tests will use Vitest + React Testing Library. Until then, use `npm run lint` and `npx tsc --noEmit` to catch errors.

## Don'ts

- Don't use class components
- Don't use default exports
- Don't create inline styles (use Tailwind)
- Don't ignore TypeScript errors with `@ts-ignore`
- Don't leave console.logs in production code

## Access Points

| URL | Purpose |
|-----|---------|
| http://localhost:5173 | Vite dev server (UI) |
| https://localhost:9002/occ/v2/ | SAP Commerce OCC API (proxied in dev) |

## Test Users

| Email | Password |
|-------|----------|
| john.doe@thinkshop.com | 1234 |
| jane.smith@thinkshop.com | 1234 |
| bob.wilson@thinkshop.com | 1234 |

OAuth client: `trusted_client` / `secret`
