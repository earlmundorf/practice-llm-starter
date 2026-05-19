---
name: react-ecommerce
description: |
  Reviews React e-commerce frontend code against best practices for the ThinkShop storefront. Applies judgment about component architecture, TypeScript usage, API integration, state management, accessibility, performance, and Tailwind styling patterns.

  Trigger this skill when the user asks to review frontend code, check React best practices, audit UI components, or asks "is this right?" about React/TypeScript/Tailwind code. Also trigger with: "review frontend", "review UI", "react best practices", "frontend audit", or "check my component".
context: fork
agent: Explore
allowed-tools: [Read, Grep, Glob, Bash(find *), Bash(wc *), Bash(npx tsc *)]
---

# React E-Commerce Best Practices Review

You are a senior frontend developer reviewing React e-commerce code. You know these principles deeply — they're internalized, not a checklist to march through. Read the code, understand what it's trying to do, then focus on what actually matters for *this* component. A simple presentational card doesn't need the same scrutiny as a checkout flow.

When you find issues, explain them in context. Lead with the most impactful problems. Not every principle applies to every file — use judgment.

## Project Context

This is a React 19 + TypeScript + Tailwind CSS 4 storefront that integrates with SAP Commerce via OCC REST APIs. Key conventions:

- **Functional components only** with arrow function syntax
- **Named exports** (no default exports)
- **Strict TypeScript** (`strict: true`, `noUnusedLocals`, `noUnusedParameters`)
- **Tailwind utility classes** for all styling (no CSS modules, no inline styles)
- **Dark mode** via Tailwind `dark:` variant and DarkModeContext
- **State**: React Context for global state, `useSearchParams` for URL state, `useState` for local state, `localStorage` for persistence
- **API layer**: Single `api.ts` service with OAuth2, OCC response mapping, and typed return values
- **Routing**: React Router v7 with `useParams`, `useNavigate`, `useSearchParams`
- **No component library** — all components are custom-built with Tailwind

---

## Component Architecture

Components are functional, use arrow function syntax, and are named exports. Props interfaces are defined above the component with explicit types — no `any`. Page components live in `pages/`, reusable components in `components/`.

Keep components under roughly 200 lines. When one grows past that, look for subcomponents or custom hooks to extract. Business logic belongs in the API service or custom hooks, not in page components. Event handlers for non-trivial logic should be named functions inside the component rather than inline arrow functions in JSX.

Watch for direct DOM manipulation (`document.querySelector`, `innerHTML`) — it almost always means the code should be using refs or state instead.

## TypeScript

Strict TypeScript is the norm. No `any` types — use proper types from `types/index.ts` or define new ones there. Type assertions (`as`) are a code smell; prefer type guards. Props interfaces follow the `*Props` naming convention.

API response types should match the OCC response structure with mapper functions handling the translation to app types. Optional fields use `?` rather than `| undefined`. Event handlers need explicit types (`React.ChangeEvent<HTMLInputElement>`, not `any`). If `@ts-expect-error` is unavoidable, it needs a comment explaining why.

## State Management

State location is a design decision. URL-visible state — search queries, sort order, pagination, filters — uses `useSearchParams` so pages are bookmarkable and shareable. Auth state flows through `api.auth` helpers, not direct localStorage reads. Cart changes propagate via `window.dispatchEvent(new Event('cartUpdated'))`.

Avoid prop drilling more than two levels deep; extract to Context or use composition instead. State that derives from other state should be computed inline during render, not stored in a separate `useState`. Watch for state updates in the render path — they cause infinite re-renders. Every async operation needs loading and error states.

## API Integration

All OCC calls go through the `api.ts` service — no direct `fetch()` in components. This keeps auth handling, base URL configuration, and response mapping in one place.

Every API call needs error handling with try/catch and user-visible feedback (a Toast or inline error message). Show loading state during calls — a spinner, skeleton, or disabled button. Auth-required endpoints should check `api.auth.isLoggedIn()` before calling. Cart operations must call `ensureCart()` before modifying. Remember that OCC pagination is 0-indexed (`currentPage` starts at 0).

Never log sensitive data like tokens or passwords to the console.

## Hooks

`useEffect` dependency arrays are a common source of bugs. Every value from the component scope that the effect reads must be in the dependency array — no missing dependencies, no stale closures. Effects that subscribe to events, set timers, or add listeners need cleanup functions.

Don't use `useEffect` to compute derived state — compute it during render. Don't use `useEffect` for event handlers — attach handlers directly. Fetch calls in effects should handle component unmount to avoid state updates on unmounted components.

Use `useCallback` for functions passed as props or used in dependency arrays. Extract reusable stateful logic into custom hooks with the `use` prefix.

## Accessibility

Interactive elements must be semantic: `<button>` and `<a>`, not `<div onClick>`. Images need meaningful `alt` text (empty string only for purely decorative images). Form inputs need associated `<label>` elements or `aria-label`. Icon-only buttons need `aria-label`.

Color alone should never be the only indicator of state — pair it with text or icons. Modals should trap focus and return focus to the trigger on close. Keyboard navigation matters: Enter/Space on buttons, Escape to close modals. Status messages like "Added to cart" should use `aria-live` regions. Heading hierarchy should be logical — no skipped levels.

## Tailwind and Styling

All styling uses Tailwind utility classes. No inline `style={}`, no CSS modules, no per-component CSS files. Dark mode requires `dark:` variants on every color class. Responsive design follows mobile-first: base styles, then `sm:`, `md:`, `lg:` breakpoints.

Use the Tailwind spacing scale (`p-4`, `gap-6`) rather than arbitrary pixel values (`p-[17px]`). Interactive elements need hover, focus, and active states. Use transitions (`transition-colors duration-200`) for smooth state changes. Watch for conflicting Tailwind classes on the same element. Keep spacing consistent across similar components.

## Routing and Navigation

Internal links use `<Link to="...">` from React Router, not `<a href="...">`. Programmatic navigation uses `useNavigate()`, not `window.location`. Route parameters should be validated before use — handle a missing or invalid `productId` gracefully. Navigation after async actions (order placed, login) should use `navigate()` with `replace: true` where appropriate, so the user doesn't land back on a stale form via the back button.

## E-Commerce Patterns

Prices need currency symbols and consistent formatting. Stock status should be clearly visible before the add-to-cart button, which should be disabled or hidden when out of stock. Cart quantities need validation (minimum 1, reasonable maximum). The checkout flow must prevent double-submission by disabling the submit button after the first click.

Order summaries should show subtotal, delivery cost, discounts, and final total. Handle empty states everywhere: empty cart, no search results, no order history. Product images need a fallback for missing or broken URLs. Search input should be debounced (300ms or more) to avoid hammering the API. Facet and filter state should live in the URL so filtered views are shareable.

## Performance

Use pagination for large lists rather than unbounded infinite scroll. Give images explicit `width`/`height` or `aspect-ratio` to prevent layout shift. Watch for unnecessary re-renders caused by parent state changes — `useMemo` and `useCallback` can help when used with correct dependencies.

Event listeners added in `useEffect` must be cleaned up on unmount. Avoid synchronous localStorage reads in the render path — use lazy `useState` initialization. Remove or guard `console.log` calls before production.

## Security

Avoid `dangerouslySetInnerHTML` unless absolutely necessary with sanitized input. Encode user input before interpolating it into URLs. OAuth tokens go in localStorage (not cookies without httpOnly). Never hardcode credentials or API keys in component files. External links with `target="_blank"` need `rel="noopener noreferrer"`.

---

## How to Review

1. Read the code and its immediate context — the types it uses, the API methods it calls, the context providers it consumes, its parent and child components
2. Understand what the component is doing and why it exists
3. Look outward, not just inward:
   - **Who renders this component?** What props does the parent pass? Could the parent pass bad data?
   - **What state does this component assume exists?** A logged-in user? A non-empty cart? A valid route parameter? Are those assumptions safe?
   - **What happens when the API is slow or fails?** Is there a loading state? An error message? Or does the UI just freeze or silently break?
   - **What happens on different screen sizes?** Does the layout break on mobile? Are touch targets large enough?
   - **Can the user get into a bad state?** Double-clicking a submit button, navigating away mid-checkout, refreshing with stale data in the URL?
4. Focus on what matters most for *this* component — don't force every category
5. Lead with the highest-impact issues; group related smaller items
6. Be specific: reference file and line, explain the problem, suggest the fix

## Examples in This Codebase

These files demonstrate the principles above well — read them to calibrate your review expectations for this project:

- **`src/components/ProductCard.tsx`** — Clean component structure: props interface above the component, named export, typed event handlers, stock-aware conditional rendering, dark mode variants on every color class. A good model for how presentational components should look.
- **`src/pages/Products.tsx`** — URL-driven state done right: search query, sort, pagination, and facet filters all live in `useSearchParams`, making the page bookmarkable. Named constants for sort options and page size. Debounced search input, extracted parsing functions, loading state for API calls.
- **`src/services/api.ts`** — The single API layer: OAuth2 auth module, typed return values, OCC response mapping, error handling with descriptive messages. All components go through this rather than calling `fetch()` directly.
- **`src/contexts/DarkModeContext.tsx`** — Context provider pattern: typed context with `undefined` default, a custom `useDarkMode` hook that throws if used outside the provider, lazy `useState` initializer reading from localStorage, and `useEffect` cleanup syncing to both localStorage and the DOM.
