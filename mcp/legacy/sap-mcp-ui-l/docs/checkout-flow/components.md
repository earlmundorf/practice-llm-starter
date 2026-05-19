# Checkout Flow — Components

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/pages/Checkout.tsx` | Single-page checkout form — address, delivery mode, payment, coupons, order summary. Supports standalone and embedded modes |
| `src/pages/OrderConfirmation.tsx` | Displays order details after placement or from order history. Expandable line items with lazy-loaded product details |
| `src/pages/Chat.tsx` | Hosts embedded checkout via `showCheckout` state. Handles `thinkshop_checkout_result` from sessionStorage on return from navigation-mode checkout |
| `src/components/AddressSelector.tsx` | Renders saved addresses as selectable cards with a "Add New Address" button |
| `src/components/AddressForm.tsx` | Address input form (first/last name, line1/line2, city, postal code, country). Country locked to US |
| `src/services/api.ts` | Checkout API methods: `setCartDeliveryAddress`, `getDeliveryModes`, `setCartDeliveryMode`, `createOrder`, `applyVoucher`, `removeVoucher`, `getCartPromotions`, `getAddresses`, `createAddress`, `getOrder` |
| `src/types/index.ts` | `Address`, `DeliveryMode`, `PaymentFormData`, `Order`, `OrderItem`, `AppliedVoucher`, `AppliedPromotion` types |

## How They Connect

```
Checkout.tsx
├── loads user, addresses, cart, promotions on mount
├── AddressSelector — select from saved addresses
│   └── on select: calls api.setCartDeliveryAddress()
├── AddressForm — create new address inline
│   └── calls api.createAddress() → api.getAddresses() → api.setCartDeliveryAddress()
├── Delivery Mode section
│   ├── calls api.getDeliveryModes() after address is set
│   └── calls api.setCartDeliveryMode() on selection (auto-selects first)
├── Payment section — pre-filled test card, editable
├── Coupon section
│   ├── calls api.applyVoucher() on apply
│   └── calls api.removeVoucher() on remove
├── Order Summary — shows cart items, promotions, discounts, totals (server-sourced)
├── on submit: calls api.createOrder(address, payment)
│   ├── embedded mode: onOrderPlaced(orderId) → Chat handles cleanup
│   ├── fromChat mode: writes to sessionStorage → navigate('/chat')
│   └── normal mode: navigate('/order-confirmation?orderId=...&new=1')
└── api.createOrder() internally:
    ├── POST paymentdetails on the cart
    ├── POST place order
    └── DELETE all remaining carts (cleanup)

Chat.tsx (embedded checkout)
├── showCheckout=true → renders <Checkout embedded onBack onOrderPlaced />
├── handleOrderPlaced(orderId):
│   ├── clears stored cart code
│   ├── creates fresh empty cart via OCC
│   ├── dispatches cartUpdated event
│   └── appends confirmation message to chat
└── on mount: reads thinkshop_checkout_result from sessionStorage
    ├── type=placed: clears cart, creates fresh cart, shows order summary
    └── type=cancelled: shows "cart still saved" message

OrderConfirmation.tsx
├── reads orderId from ?orderId= query param
├── reads order from location.state (if available) or calls api.getOrder()
├── expandable items: calls api.getProduct() on demand
└── fromChat: shows "Back to Chat" button that writes result to sessionStorage
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/users/current/addresses?fields=FULL` | Fetch saved addresses |
| POST | `/users/current/addresses` | Create new address |
| PUT | `/users/current/carts/{cartCode}/addresses/delivery?addressId={id}` | Set delivery address by ID |
| GET | `/users/current/carts/{cartCode}/deliverymodes` | Get available shipping methods |
| PUT | `/users/current/carts/{cartCode}/deliverymode?deliveryModeId={code}` | Set shipping method |
| POST | `/users/current/carts/{cartCode}/vouchers?voucherId={code}` | Apply coupon code |
| DELETE | `/users/current/carts/{cartCode}/vouchers/{code}` | Remove coupon |
| GET | `/users/current/carts/{cartCode}?fields=FULL` | Fetch cart with promotions, totals |
| POST | `/users/current/carts/{cartCode}/paymentdetails` | Set payment details (with billing address) |
| POST | `/users/current/orders?cartId={cartCode}&fields=FULL` | Place order |
| DELETE | `/users/current/carts/{cartCode}` | Clean up stale carts after order |
| POST | `/users/current/carts` | Create fresh empty cart after order |
| GET | `/users/current/orders/{orderId}?fields=FULL` | Get order details (confirmation page) |
