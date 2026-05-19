# Order History — Context

## What This Flow Does

Displays the authenticated user's past orders in a paginated list. Users can expand individual orders to see line items, delivery details, and totals. Optionally supports order cancellation.

## When It's Used

- User navigates to the Orders page
- After placing an order (linked from order confirmation)
- Checking status of a previous order

## Key Decisions

### Paginated order list
Order history uses OCC pagination (0-based). The UI displays orders newest-first with page navigation. Each order shows code, date, status, and total at a glance.

### Expandable detail
Order details load on-demand when the user expands an order row. This avoids fetching full details for every order in the list. The detail call uses `fields=FULL` to get line items, addresses, and payment info.

### Order status display
OCC order statuses (`CREATED`, `CHECKED_VALID`, `PAYMENT_AUTHORIZED`, `PAYMENT_CAPTURED`, `READY`, `COMPLETED`, `CANCELLED`) are mapped to user-friendly display labels and color-coded badges.

### Entry-level cancellation
OCC cancellation is entry-level, not order-level. To cancel an entire order, all entries must be listed with their full quantities. The UI abstracts this with a "Cancel Order" button that builds the full cancellation request automatically.

### Auth-required
The Orders page requires authentication. If the user is not logged in, they're redirected to the login flow with a return URL so they land back on Orders after authenticating.
