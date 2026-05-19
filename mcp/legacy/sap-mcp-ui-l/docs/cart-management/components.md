# Cart Management — Components

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/components/CartModal.tsx` | Slide-out cart drawer — item list, quantity controls, promotions/vouchers display, totals, checkout CTA |
| `src/components/Header.tsx` | Cart icon with badge count, pulse animation on add, opens CartModal, listens for `openCartModal` event |
| `src/services/api.ts` | Cart API methods (`getCart`, `addToCart`, `updateCartItem`, `removeFromCart`, `clearCart`, `ensureCart`) and `cartUtils` adapter |
| `src/types/index.ts` | `CartItem`, `AppliedVoucher`, `AppliedPromotion`, `CartModalProps` types |

## How They Connect

```
Header.tsx
├── listens for 'cartUpdated' → calls cartUtils.getCart() → updates badge count
├── listens for 'cartItemAdded' → triggers pulse animation (400ms)
├── listens for 'openCartModal' → sets showCartModal = true
├── cart icon click → sets showCartModal = true
└── renders <CartModal isOpen={showCartModal} onClose={...} />

CartModal.tsx
├── on open → calls cartUtils.getCart() → renders cart entries
├── on open (logged in, has items) → calls api.getCartPromotions() → renders promotions/vouchers
├── listens for 'cartUpdated' (while open) → reloads cart
├── quantity +/- → calls cartUtils.updateCartItem(productId, qty) → reloads → dispatches 'cartUpdated'
├── remove → calls cartUtils.removeFromCart(productId) → reloads → dispatches 'cartUpdated'
├── clear cart → calls cartUtils.clearCart() → reloads → dispatches 'cartUpdated'
├── "Checkout" button → navigates to /checkout
└── backdrop click / "Continue Shopping" → closes drawer

api.ts — cartUtils (adapter layer)
├── getCart() → api.getCart() → returns CartItem[]
├── addToCart(product, qty) → api.addToCart() → dispatches 'cartUpdated'
├── updateCartItem(productId, qty) → looks up entryNumber → api.updateCartItem(entryNumber, qty)
├── removeFromCart(productId) → looks up entryNumber → api.removeFromCart(entryNumber)
├── clearCart() → api.clearCart() → removes cart code from localStorage
├── getCartTotal(cart) → client-side sum of price * quantity
└── getCartCount(cart) → client-side sum of quantities

api.ts — ensureCart() (internal)
├── checks localStorage for existing cart code
├── verifies cart exists on server (GET)
├── if missing → creates new cart (POST) → stores code in localStorage
└── returns cart code
```

## OCC Endpoints Used

| Method | Endpoint | Used By | Notes |
|--------|----------|---------|-------|
| POST | `/users/current/carts` | `ensureCart()` | Create cart, returns `code` |
| GET | `/users/current/carts/{cartCode}?fields=DEFAULT` | `ensureCart()` | Verify cart exists |
| GET | `/users/current/carts/{cartCode}?fields=FULL` | `api.getCart()`, `api.getCartPromotions()` | Get entries, totals, promotions, vouchers |
| POST | `/users/current/carts/{cartCode}/entries` | `api.addToCart()` | Add product by code + quantity |
| PATCH | `/users/current/carts/{cartCode}/entries/{entryNumber}` | `api.updateCartItem()` | Update quantity by entry number |
| DELETE | `/users/current/carts/{cartCode}/entries/{entryNumber}` | `api.removeFromCart()` | Remove entry by entry number |
| DELETE | `/users/current/carts/{cartCode}` | `api.clearCart()` | Delete entire cart |

## Key Types

| Type | Fields | Defined In |
|------|--------|-----------|
| `CartItem` | `productId`, `productName`, `quantity`, `price`, `entryNumber?` | `src/types/index.ts` |
| `AppliedVoucher` | `code`, `name?`, `appliedValue?`, `freeShipping?` | `src/types/index.ts` |
| `AppliedPromotion` | `description`, `promotionCode?` | `src/types/index.ts` |
