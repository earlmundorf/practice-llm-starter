# Checkout Flow — Context

## What This Flow Does

Guides the user through a single-page checkout process: selecting a delivery address, choosing a shipping method, entering payment details, applying coupons, and placing the order. On success, navigates to an order confirmation page — or, if initiated from the chat assistant, returns to the chat with an order summary.

## When It's Used

- User clicks "Proceed to Checkout" from the cart or product browsing
- AI chat agent triggers a checkout action (navigates to `/checkout` or embeds inline)
- User views a previously placed order via `/order-confirmation?orderId=...`

## Key Decisions

### Single-page form (not a step wizard)

Unlike a traditional multi-step wizard, the checkout renders all sections — address, delivery mode, payment, coupons — on a single scrollable page in a two-column layout (form on the left, order summary on the right). Steps are still sequential at the API level: delivery modes only load after an address is set, and payment is submitted at order placement time. But the user sees everything at once, reducing clicks.

### Embedded mode for chat

The `Checkout` component accepts an `embedded` prop. When `true`, it renders inside the Chat page without its own page chrome (no `min-h-screen`, no max-width container). The Chat page toggles between the chat view and the embedded checkout via `showCheckout` state. On order completion, the `onOrderPlaced` callback returns the order ID to the chat, which posts a confirmation message and creates a fresh cart.

### Two paths back from checkout to chat

There are two ways checkout can be triggered from chat, each with its own return path:

1. **Embedded mode** (`embedded={true}`): Checkout renders inline within the Chat page. On order placement, `onOrderPlaced` fires and the chat handles cleanup directly.
2. **Navigation mode** (`fromChat` flag): The agent navigates to `/checkout` as a separate page, setting `thinkshop_from_chat` in sessionStorage. On order placement, checkout writes the order summary to `thinkshop_checkout_result` in sessionStorage and navigates back to `/chat`. The Chat page reads this on mount and posts the summary as an assistant message.

### Address handling

Users can select from saved addresses or create a new one inline via `AddressForm`. The component auto-selects the default address (or the first address) on load. When a new address is created, it is saved to the customer's address book via OCC, then set as the delivery address. The country field is locked to `US`.

### Delivery modes depend on address

Available delivery modes are fetched from OCC only after a delivery address is set on the cart. Changing the address triggers a re-fetch. The first available mode is auto-selected and set on the cart immediately. Promotion-discounted free shipping is detected by comparing the server-reported delivery cost against the mode's listed price.

### Mock payment with pre-filled test card

Payment uses a pre-filled test card (`4111111111111111`, Visa, 12/2028). The cardholder name is auto-populated from the logged-in user's full name. Users can edit all fields. The UI labels this as a mock payment provider for demo purposes.

### Coupon and promotion support

The checkout includes a coupon code input that calls `applyVoucher` on the cart. Applied vouchers display with a remove button. Both applied and potential promotions are shown in the order summary — applied promotions in green, potential promotions (thresholds not yet met) in blue. Totals, subtotals, and delivery costs are sourced from the server after promotion calculation.

### Double-submit prevention

The "Place Order" button is disabled after the first click and shows "Placing Order..." text. The button stays disabled until the API responds with success or error. On error, it re-enables so the user can retry.

### Cart cleanup after order placement

After a successful order, `createOrder` in `api.ts` removes the stored cart code and deletes all remaining carts via the OCC API to prevent `ensureCart` from adopting stale carts. The Chat page (both embedded and navigation modes) then creates a fresh empty cart so the header count resets to zero.

### Order confirmation serves dual purpose

`OrderConfirmation` handles both newly placed orders (`?new=1` shows a success banner) and order history lookups (shows a "Back to Orders" link). It accepts the order object via navigation state to avoid an extra API call, falling back to `getOrder` if state is missing. Items are expandable to show product images and descriptions fetched on demand.
