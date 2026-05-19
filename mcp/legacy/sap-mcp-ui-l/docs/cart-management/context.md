# Cart Management — Context

## What This Flow Does

Manages the shopping cart: adding products, updating quantities, removing items, clearing the cart, and displaying cart contents in a slide-out drawer. The cart is backed by SAP Commerce OCC and persists server-side per user across sessions.

## When It's Used

- Adding a product to cart from the Products page or Product Detail page
- Opening the cart drawer from the header cart icon (desktop or mobile)
- Opening the cart drawer programmatically via `window.dispatchEvent(new Event('openCartModal'))`
- Changing item quantities in the cart
- Removing items or clearing the entire cart
- Viewing applied promotions, potential promotions, and coupon codes
- Proceeding to checkout

## Key Decisions

### Cart code in localStorage

The `cartCode` (OCC cart identifier) is stored in `localStorage` under the key `occ_cart_code`. This allows the UI to resume the same cart across page reloads without creating a new one each time. On login, the stored cart code is cleared so `ensureCart()` creates a fresh cart for the new user. On logout, the cart code is also removed.

### ensureCart: verify-then-create

`ensureCart()` first checks localStorage for an existing cart code. If found, it verifies the cart still exists on the server with a GET request. If the cart is gone (e.g., after order placement or session expiry), it falls through and creates a new cart via `POST /users/current/carts`. This means callers never need to worry about cart lifecycle — every `api.addToCart()`, `api.getCart()`, and similar method calls `ensureCart()` internally.

### Entry numbers, not product codes

OCC identifies cart entries by `entryNumber` (integer), not product code. The `cartUtils` adapter layer handles this translation: when `CartModal` calls `cartUtils.updateCartItem(productId, quantity)` or `cartUtils.removeFromCart(productId)`, the utility fetches the current cart, finds the entry whose `productId` matches, extracts its `entryNumber`, and passes that to the OCC API. Components never deal with entry numbers directly.

### Window events for cross-component communication

Cart state changes propagate through three custom window events rather than React context or prop drilling:

| Event | Dispatched By | Listened By | Purpose |
|-------|--------------|-------------|---------|
| `cartUpdated` | `cartUtils` methods, `CartModal`, `Header` | `Header` (badge count), `CartModal` (reload items) | Signal that cart contents changed |
| `cartItemAdded` | Product pages after add-to-cart | `Header` | Trigger the pulse animation on the cart badge |
| `openCartModal` | Any component (e.g., Chat, product pages) | `Header` | Programmatically open the cart drawer |

### Promotions and vouchers loaded separately

After loading cart items, `CartModal` makes a second call to `api.getCartPromotions()` to fetch applied vouchers, applied promotions (order + product level), potential promotions, total discounts, and the server-computed total price. This data is only fetched for logged-in users with items in the cart. The server total is used on the checkout button so the displayed price reflects discounts accurately.

### Slide-out drawer pattern

The cart uses a slide-out drawer (`CartModal`) anchored to the right edge of the viewport rather than a dedicated page. This lets users review their cart without losing their place in the product catalog. The drawer closes on backdrop click or the "Continue Shopping" button. On mobile, the drawer spans the full viewport width; on desktop it is fixed at 440px.
