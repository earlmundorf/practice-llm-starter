# Order History — Context

## What This Flow Does

Displays the authenticated user's past orders as a card list. Users can click any order to navigate to a detail view showing line items with expandable product information, a pricing breakdown (subtotal, delivery, tax, total), and status badges. The API layer also supports order cancellation at the entry level, though the cancel action is not yet wired into the UI.

## When It's Used

- User navigates to `/orders` from the main navigation
- After placing an order — the order confirmation page links to `/orders` with the recent order passed via router state
- After completing an embedded (chat-initiated) checkout, the recent order ID is stored in `sessionStorage` and merged into the list on load

## Key Decisions

### Card-based order list (not expandable rows)

The Orders page renders each order as a clickable card showing code, date, status badge, and total. Clicking a card navigates to `/order-confirmation?orderId=...` (the `OrderConfirmation` page), which serves double duty as both the post-purchase confirmation and the order detail view.

### Recent order merge

OCC may not immediately return a just-placed order in the list endpoint. The page handles this two ways:
1. **Router state:** The order confirmation page passes the order object via `location.state.recentOrder`, which gets prepended if not already present.
2. **Session storage:** For chat-initiated checkouts, the order ID is stored in `sessionStorage` under `thinkshop_recent_order`. On load, the page fetches that order by ID and prepends it.

### Order detail on a separate page

Rather than expanding details inline on the list, the implementation navigates to `OrderConfirmation` with the order ID as a query param. That page fetches full order details (if not passed via state) and renders items in a table with expandable rows for product images and descriptions.

### Expandable line items in the detail view

Each order item row in the confirmation/detail page is clickable. Expanding it lazily fetches the product via `api.getProduct()` to show the image, description, and a link to the product page. Products that no longer exist are handled gracefully with a "no longer available" message.

### Pricing breakdown

The detail view renders subtotal, delivery cost, and tax as separate line items in the table footer when available. These fields are optional on the `Order` type and only display when non-null and non-zero.

### Status color coding

Order statuses are mapped to color-coded badges using Tailwind classes with dark mode support:

| Status | Color | Icon |
|--------|-------|------|
| `CREATED` / `pending` | Yellow | Hourglass |
| `COMPLETED` | Green | Check |
| `CANCELLED` | Red | Cross |
| Other / unknown | Gray | Package |

### Entry-level cancellation (API only)

The `api.cancelOrder()` method implements OCC's entry-level cancellation pattern: it fetches the full order, then POSTs a cancellation request listing every entry with its full quantity. This effectively cancels the entire order. The cancel method is available in `api.ts` but is not currently exposed in the Orders or OrderConfirmation UI.

### Auth-required

The Orders page checks `auth.isLoggedIn()` on load. If the user is not authenticated, an error message is displayed prompting them to log in. The page does not redirect to a login flow automatically.
