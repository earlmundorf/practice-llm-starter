# Checkout Flow — Context

## What This Flow Does

Guides the user through a multi-step checkout process: setting a delivery address, choosing a shipping method, entering payment details, and placing the order. On success, navigates to an order confirmation page.

## When It's Used

- User clicks "Proceed to Checkout" from the cart
- Each step must be completed in order before the next becomes available

## Key Decisions

### Multi-step wizard
OCC requires checkout steps in a specific order: address → delivery mode → payment → place order. The UI enforces this with a step indicator and sequential progression. Users can go back to previous steps but cannot skip ahead.

### Address handling
Two approaches: use an existing address from the customer's address book, or enter a new address inline. The UI fetches the customer's saved addresses and shows them as selectable options, with a "New Address" form as an alternative.

### Delivery modes from OCC
Available delivery modes depend on the delivery address (country/zone). The UI fetches modes from OCC after the address is set, not before. If the address changes, modes must be re-fetched.

### Mock payment
For development/testing, payment uses a mock card (`4111111111111111`). The UI pre-fills test payment data but allows editing. Production would integrate a real payment gateway.

### Double-submit prevention
The "Place Order" button is disabled after the first click and shows a loading spinner. This prevents duplicate orders from impatient clicks. The button stays disabled until the API responds (success or error).

### Order confirmation
On successful order placement, the UI navigates to `/order-confirmation/{orderCode}` with `replace: true` so the back button doesn't return to the checkout form with stale state.
