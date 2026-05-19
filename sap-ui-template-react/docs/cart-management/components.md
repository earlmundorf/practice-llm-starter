# Cart Management — Components

> **Note:** `CartModal.tsx` is planned — create it following the patterns in CLAUDE.md. The `api.ts` service, `CartContext`, and `types/index.ts` already exist with the methods and types referenced here.

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/components/CartModal.tsx` | Slide-out cart drawer — item list, quantity controls, totals, checkout CTA |
| `src/components/Header.tsx` | Cart icon with badge count, opens CartModal |
| `src/services/api.ts` | Cart API methods: `createCart`, `getCart`, `addToCart`, `updateEntry`, `removeEntry` |
| `src/types/index.ts` | `Cart`, `CartEntry`, `CartModification` types |

## How They Connect

```
Header.tsx
├── listens for 'cartUpdated' event
├── shows cart badge count
└── toggles CartModal visibility

CartModal.tsx
├── calls api.getCart(cartCode) on open
├── renders cart entries with quantity controls
├── calls api.updateEntry() on quantity change
├── calls api.removeEntry() on remove click
├── dispatches 'cartUpdated' after mutations
└── links to /checkout on "Proceed to Checkout"
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/users/current/carts` | Create cart (returns cartCode) |
| GET | `/users/current/carts/{cartCode}?fields=FULL` | Get cart contents |
| POST | `/users/current/carts/{cartCode}/entries?fields=FULL` | Add product to cart |
| PATCH | `/users/current/carts/{cartCode}/entries/{entryNumber}?fields=FULL` | Update quantity |
| DELETE | `/users/current/carts/{cartCode}/entries/{entryNumber}` | Remove entry |
