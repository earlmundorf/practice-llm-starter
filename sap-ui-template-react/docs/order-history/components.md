# Order History — Components

> **Note:** The `Orders.tsx` page is planned — create it following the patterns in CLAUDE.md. The `api.ts` service and `types/index.ts` already exist with `getOrders()`, `getOrder()`, and related types.

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/pages/Orders.tsx` | Order history page — paginated list with expandable details |
| `src/services/api.ts` | `api.getOrders()`, `api.getOrder()`, `api.cancelOrder()` methods |
| `src/types/index.ts` | `Order`, `OrderEntry`, `OrderHistoryResult` types |

## How They Connect

```
Orders.tsx
├── checks auth (redirect to login if needed)
├── calls api.getOrders({ page }) on mount and page change
├── renders order summary rows (code, date, status, total)
├── on expand: calls api.getOrder(orderCode) for full detail
├── renders order entries, delivery info, payment info
└── cancel button: calls api.cancelOrder(orderCode, entries)
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/users/current/orders?fields=FULL&pageSize=20&currentPage={p}` | Paginated history |
| GET | `/users/current/orders/{orderCode}?fields=FULL` | Full order detail |
| POST | `/users/current/orders/{orderCode}/cancellation` | Entry-level cancel |
