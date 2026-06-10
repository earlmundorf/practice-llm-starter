---
name: react-ecommerce
description: |
  Reviews React e-commerce frontend code against best practices for an SAP Commerce storefront. Applies judgment about component architecture, TypeScript usage, API integration, state management, accessibility, performance, and Tailwind styling patterns.

  Trigger this skill when the user asks to review frontend code, check React best practices, audit UI components, or asks "is this right?" about React/TypeScript/Tailwind code. Also trigger with: "review frontend", "review UI", "react best practices", "frontend audit", or "check my component". This is the REVIEW skill — for development how-to use react-typescript, for backend/OCC integration contracts use commerce-storefront, for structured ticket workflows use storefront-qrspi.
context: fork
agent: Explore
allowed-tools: [Read, Grep, Glob, Bash(find *), Bash(wc *), Bash(npx tsc *)]
---

# React E-Commerce Best Practices Review

You are a senior frontend developer reviewing React e-commerce code. You know these principles deeply — they're internalized, not a checklist to march through. Read the code, understand what it's trying to do, then focus on what actually matters for *this* component. A simple presentational card doesn't need the same scrutiny as a checkout flow.

When you find issues, explain them in context. Lead with the most impactful problems. Not every principle applies to every file — use judgment.

## Project Conventions

Read `CLAUDE.md` at the project root for authoritative rules on component style, TypeScript, Tailwind, file naming, and don'ts. This skill focuses on *how to review* against those conventions and the deeper patterns below — it does not restate the rules from CLAUDE.md.

---

## Component Architecture

Components should be small, focused, and testable. Page components orchestrate — they fetch data, manage state, and compose child components. Reusable components in `components/` are presentational — they receive data and callbacks via props.

When a component grows past the line limit in CLAUDE.md, look for subcomponents or custom hooks to extract. Business logic belongs in the API service or custom hooks, not inline in page components. Event handlers for non-trivial logic should be named functions, not inline arrow functions in JSX.

Watch for direct DOM manipulation (`document.querySelector`, `innerHTML`) — it almost always means the code should be using refs or state instead.

### Inline Example — Clean Component Structure

```tsx
interface ProductCardProps {
  product: Product;
  onAddToCart: (productCode: string, quantity: number) => void;
}

export const ProductCard = ({ product, onAddToCart }: ProductCardProps) => {
  const [quantity, setQuantity] = useState(1);
  const inStock = product.stock?.stockLevelStatus === 'inStock';

  const handleAdd = () => {
    if (inStock) {
      onAddToCart(product.code, quantity);
    }
  };

  return (
    <div className="flex flex-col gap-3 p-4 bg-white dark:bg-gray-800 rounded-lg shadow-md">
      <h3 className="text-lg font-semibold text-gray-900 dark:text-white">{product.name}</h3>
      <p className="text-xl font-bold text-blue-600 dark:text-blue-400">
        {product.price?.formattedValue}
      </p>
      {inStock ? (
        <span className="text-sm text-green-600 dark:text-green-400">In Stock</span>
      ) : (
        <span className="text-sm text-red-600 dark:text-red-400">Out of Stock</span>
      )}
      <button
        onClick={handleAdd}
        disabled={!inStock}
        className="mt-auto px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700
                   disabled:bg-gray-300 dark:disabled:bg-gray-600 disabled:cursor-not-allowed
                   transition-colors"
      >
        Add to Cart
      </button>
    </div>
  );
};
```

Props interface above the component. Named export. Named event handler. Stock-aware conditional rendering. `dark:` variants on every color class including disabled states.

## TypeScript

Strict TypeScript is the norm. Type assertions (`as`) are a code smell; prefer type guards. Props interfaces follow the `*Props` naming convention.

API response types should match the OCC response structure with mapper functions handling the translation to app types. Optional fields use `?` rather than `| undefined`. Event handlers need explicit types (`React.ChangeEvent<HTMLInputElement>`, not `any`). If `@ts-expect-error` is unavoidable, it needs a comment explaining why.

## State Management

State location is a design decision. URL-visible state — search queries, sort order, pagination, filters — uses `useSearchParams` so pages are bookmarkable and shareable. Auth state flows through `api.auth` helpers, not direct localStorage reads. Cart changes propagate via `window.dispatchEvent(new Event('cartUpdated'))`.

Avoid prop drilling more than two levels deep; extract to Context or use composition instead. State that derives from other state should be computed inline during render, not stored in a separate `useState`. Watch for state updates in the render path — they cause infinite re-renders. Every async operation needs loading and error states.

### Inline Example — URL-Driven State

```tsx
export const Products = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get('q') || '';
  const sort = searchParams.get('sort') || 'relevance';
  const page = parseInt(searchParams.get('page') || '0', 10);

  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchProducts = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await api.searchProducts({ query, sort, page });
        setProducts(result.products);
      } catch (err) {
        setError('Failed to load products');
      } finally {
        setLoading(false);
      }
    };
    fetchProducts();
  }, [query, sort, page]);

  const handleSearch = (newQuery: string) => {
    setSearchParams({ q: newQuery, sort, page: '0' });
  };

  // ...
};
```

Search, sort, and page in URL params — bookmarkable and shareable. Loading and error states for every async operation. Effect dependencies match all values read from params.

## API Integration

All OCC calls go through the `api.ts` service — no direct `fetch()` in components. This keeps auth handling, base URL configuration, and response mapping in one place.

Every API call needs error handling with try/catch and user-visible feedback (a Toast or inline error message). Show loading state during calls — a spinner, skeleton, or disabled button. Auth-required endpoints should check `api.auth.isLoggedIn()` before calling. Cart operations must call `ensureCart()` before modifying. Remember that OCC pagination is 0-indexed (`currentPage` starts at 0).

Never log sensitive data like tokens or passwords to the console.

### Inline Example — API Service Layer

```tsx
const BASE_URL = import.meta.env.VITE_API_URL || '/occ/v2/electronics';

const apiFetch = async <T>(path: string, options: RequestInit = {}): Promise<T> => {
  const token = localStorage.getItem('access_token');
  const defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { ...defaultHeaders, ...(options.headers as Record<string, string>) },
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`API error ${response.status}: ${errorBody}`);
  }

  return response.json();
};

let cartCode: string | null = null;

const ensureCart = async (): Promise<string> => {
  if (!cartCode) {
    const cart = await apiFetch<{ code: string }>('/users/current/carts', { method: 'POST' });
    cartCode = cart.code;
  }
  return cartCode;
};

export const api = {
  searchProducts: (params: { query: string; sort: string; page: number }) =>
    apiFetch<ProductSearchResult>(
      `/products/search?query=${encodeURIComponent(params.query)}:${params.sort}` +
      `&currentPage=${params.page}&pageSize=20&fields=FULL`
    ),

  addToCart: async (productCode: string, quantity: number) => {
    const code = await ensureCart();
    return apiFetch<CartModification>(`/users/current/carts/${code}/entries?fields=FULL`, {
      method: 'POST',
      body: JSON.stringify({ product: { code: productCode }, quantity }),
    });
  },
};
```

Single API layer with typed return values. Caller headers merge with defaults (not overwritten). `ensureCart()` creates a cart on first use — cart operations call it before modifying. OCC-specific patterns: `fields=FULL`, URL encoding, 0-based pagination.

## Hooks

`useEffect` dependency arrays are a common source of bugs. Every value from the component scope that the effect reads must be in the dependency array — no missing dependencies, no stale closures. Effects that subscribe to events, set timers, or add listeners need cleanup functions.

Don't use `useEffect` to compute derived state — compute it during render. Don't use `useEffect` for event handlers — attach handlers directly. Fetch calls in effects should handle component unmount to avoid state updates on unmounted components.

Use `useCallback` for functions passed as props or used in dependency arrays. Extract reusable stateful logic into custom hooks with the `use` prefix.

## Forms and Validation

Checkout and login are form-heavy flows. Use controlled inputs with `useState`. Validate on submit — show inline error messages per field, not a single alert. Clear field errors when the user edits the field.

Disable the submit button and show a spinner during API calls. This is critical for the "Place Order" button — double-submission creates duplicate orders. After a successful async action (login, order placement), use `navigate()` with `replace: true` so the back button doesn't return to the stale form.

### Inline Example — Form with Validation

```tsx
export const LoginForm = ({ onSuccess }: { onSuccess: () => void }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Email and password are required');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await api.auth.login(email, password);
      onSuccess();
    } catch (err) {
      setError('Invalid email or password');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="email"
        value={email}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
          setEmail(e.target.value);
          setError(null);
        }}
        aria-label="Email"
      />
      <input
        type="password"
        value={password}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
          setPassword(e.target.value);
          setError(null);
        }}
        aria-label="Password"
      />
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      <button type="submit" disabled={submitting}>
        {submitting ? 'Logging in...' : 'Log In'}
      </button>
    </form>
  );
};
```

Controlled inputs. Error cleared on edit. Submit disabled during API call. Explicit event types. `aria-label` on inputs.

## Accessibility

Interactive elements must be semantic: `<button>` and `<a>`, not `<div onClick>`. Images need meaningful `alt` text (empty string only for purely decorative images). Form inputs need associated `<label>` elements or `aria-label`. Icon-only buttons need `aria-label`.

Color alone should never be the only indicator of state — pair it with text or icons. Modals should trap focus and return focus to the trigger on close. Keyboard navigation matters: Enter/Space on buttons, Escape to close modals. Status messages like "Added to cart" should use `aria-live` regions. Heading hierarchy should be logical — no skipped levels.

## Tailwind and Styling

Review Tailwind usage against the ordering and dark mode rules in CLAUDE.md. Key things to watch for:

- Missing `dark:` variants on color classes (backgrounds, text, borders, disabled states)
- Arbitrary pixel values (`p-[17px]`) instead of the spacing scale (`p-4`)
- Interactive elements without hover, focus, and active states
- Missing transitions on state changes
- Conflicting Tailwind classes on the same element
- Inconsistent spacing across similar components
- Not mobile-first (using `max-width` media queries instead of `sm:`, `md:`, `lg:`)

## Routing and Navigation

Internal links use `<Link to="...">` from React Router, not `<a href="...">`. Programmatic navigation uses `useNavigate()`, not `window.location`. Route parameters should be validated before use — handle a missing or invalid `productId` gracefully. Navigation after async actions (order placed, login) should use `navigate()` with `replace: true` where appropriate, so the user doesn't land back on a stale form via the back button.

## E-Commerce Patterns

Prices need currency symbols and consistent formatting — use `formattedValue` from OCC responses. Stock status should be clearly visible before the add-to-cart button, which should be disabled when out of stock. Cart quantities need validation (minimum 1, reasonable maximum).

Order summaries should show subtotal, delivery cost, discounts, and final total. Handle empty states everywhere: empty cart, no search results, no order history. Product images need a fallback for missing or broken URLs. Search input should be debounced (300ms or more) to avoid hammering the API. Facet and filter state should live in the URL so filtered views are shareable.

## Performance

Use pagination for large lists rather than unbounded infinite scroll. Give images explicit `width`/`height` or `aspect-ratio` to prevent layout shift. Watch for unnecessary re-renders caused by parent state changes — `useMemo` and `useCallback` can help when used with correct dependencies.

Event listeners added in `useEffect` must be cleaned up on unmount. Avoid synchronous localStorage reads in the render path — use lazy `useState` initialization. Remove or guard `console.log` calls before production.

## Security

Avoid `dangerouslySetInnerHTML` unless absolutely necessary with sanitized input. Encode user input before interpolating it into URLs. External links with `target="_blank"` need `rel="noopener noreferrer"`. Never hardcode credentials or API keys in component files.

**Token storage caveat:** The project stores OAuth tokens in `localStorage` for development convenience. This is acceptable for demo/dev but not production — a production app should use a backend-for-frontend (BFF) pattern or httpOnly cookies. When reviewing, don't flag localStorage token storage as a bug, but do flag any code that exposes tokens in logs, URLs, or error messages.

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
