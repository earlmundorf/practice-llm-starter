# Order History — Components

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/pages/Orders.tsx` | Order list page — card-based list with status badges, navigates to detail on click |
| `src/pages/OrderConfirmation.tsx` | Order detail page — also serves as post-checkout confirmation; shows items table with expandable product info, pricing breakdown, status badge |
| `src/services/api.ts` | `api.getUserOrders()`, `api.getOrder()`, `api.cancelOrder()`, `api.getProduct()` methods and OCC response mappers |
| `src/types/index.ts` | `Order`, `OrderItem`, `OrderStatus` types |
| `src/App.tsx` | Route definitions for `/orders` and `/order-confirmation` |

## How They Connect

```
Orders.tsx (list)
├── checks auth via auth.isLoggedIn()
├── calls api.getUserOrders() on mount (and on location.key change)
├── merges recent order from location.state or sessionStorage
├── renders order cards (id, date, status badge, total)
└── on click: navigate("/order-confirmation?orderId=...")

OrderConfirmation.tsx (detail)
├── reads orderId from URL search params
├── reads order from location.state (skip API if present)
├── otherwise calls api.getOrder(orderId)
├── renders status badge, date, total in summary header
├── renders items table with expandable rows
│   └── on expand: calls api.getProduct(productId) for image/description
├── renders pricing breakdown (subtotal, delivery, tax, total)
└── navigation links: back to orders, continue shopping, back to chat

api.ts (service layer)
├── getUserOrders() → GET /users/current/orders?fields=FULL&pageSize=20
├── getOrder(id) → GET /users/current/orders/{id}?fields=FULL
├── cancelOrder(id) → fetches order, then POST /users/current/orders/{id}/cancellation
├── mapOccOrder() → maps OCC OrderWsDTO to Order type
├── mapOccOrderEntry() → maps OCC OrderEntryWsDTO to OrderItem type
└── mapOccStatusDisplay() → normalizes statusDisplay string to OrderStatus union
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/users/current/orders?fields=FULL&pageSize=20` | Paginated order list, newest first |
| GET | `/users/current/orders/{orderCode}?fields=FULL` | Full order detail with entries |
| POST | `/users/current/orders/{orderCode}/cancellation` | Entry-level cancel (all entries, full quantities) |
| GET | `/products/{code}?fields=...` | Product detail for expanded line items |

## Type Definitions

```typescript
type OrderStatus = 'CREATED' | 'COMPLETED' | 'CANCELLED';

interface OrderItem {
  productId: string;
  productName: string;
  description?: string;
  imageUrl?: string;
  quantity: number;
  price: number;
}

interface Order {
  id: string;
  userId: string;
  items: OrderItem[];
  totalAmount: number;
  subTotal?: number;
  deliveryCost?: number;
  totalTax?: number;
  status: OrderStatus;
  createdAt: string;
}
```
