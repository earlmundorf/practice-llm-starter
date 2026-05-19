# Cart Management — Context

## What This Flow Does

Manages the shopping cart: adding products, updating quantities, removing items, and displaying cart contents in a slide-out drawer. The cart persists across page navigations within a session.

## When It's Used

- Adding a product to cart from the Products page or Product Detail page
- Opening the cart drawer from the header cart icon
- Changing item quantities in the cart
- Removing items from the cart
- Proceeding to checkout

## Key Decisions

### OCC cart requires creation
Unlike simple REST APIs, OCC requires explicitly creating a cart before adding items. The UI must call `POST /users/current/carts` first, then use the returned `cartCode` for all subsequent operations. The `ensureCart()` helper handles this transparently.

### Entry numbers, not product codes
OCC identifies cart entries by `entryNumber` (integer), not product code. When updating or removing an item, the UI must look up the entry number from the cart response that matches the product code. This is a key difference from typical REST cart APIs.

### Cart event propagation
Cart changes propagate via `window.dispatchEvent(new Event('cartUpdated'))`. The Header component listens for this event to update the cart badge count. This avoids prop drilling cart state through the entire component tree.

### Cart code storage
The cart code is stored in React state (context or top-level component). It's not persisted to localStorage because carts are tied to the OAuth session — a new login creates a new cart context.

### Slide-out drawer pattern
The cart uses a slide-out drawer (CartModal) rather than a dedicated page. This lets users review their cart without losing their place in the product catalog. The drawer closes on backdrop click, Escape key, or "Continue Shopping" action.
