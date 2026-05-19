# Checkout Flow — Components

> **Note:** Page and component files listed below are planned — create them following the patterns in CLAUDE.md. The `api.ts` service and `types/index.ts` already exist with the checkout methods and types referenced here.

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/pages/Checkout.tsx` | Multi-step checkout page — orchestrates steps, manages state |
| `src/pages/OrderConfirmation.tsx` | Success page after order placement |
| `src/components/AddressForm.tsx` | Address input form with validation |
| `src/components/AddressSelector.tsx` | Select from saved addresses or enter new |
| `src/services/api.ts` | Checkout API methods: `setDeliveryAddress`, `getDeliveryModes`, `setDeliveryMode`, `setPaymentDetails`, `placeOrder` |
| `src/types/index.ts` | `Address`, `DeliveryMode`, `PaymentDetails`, `Order` types |

## How They Connect

```
Checkout.tsx
├── Step 1: AddressSelector / AddressForm
│   └── calls api.setDeliveryAddress()
├── Step 2: Delivery Mode Selection
│   ├── calls api.getDeliveryModes() after address set
│   └── calls api.setDeliveryMode() on selection
├── Step 3: Payment Details
│   └── calls api.setPaymentDetails()
├── Step 4: Review & Place Order
│   └── calls api.placeOrder()
└── on success: navigate('/order-confirmation/{code}', { replace: true })

OrderConfirmation.tsx
├── reads :orderCode from useParams
├── calls api.getOrder(orderCode)
└── renders order summary
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/users/current/carts/{cartCode}/addresses/delivery` | Set delivery address |
| GET | `/users/current/carts/{cartCode}/deliverymodes` | Get available shipping methods |
| PUT | `/users/current/carts/{cartCode}/deliverymode?deliveryModeId={code}` | Set shipping method |
| POST | `/users/current/carts/{cartCode}/paymentdetails` | Set payment details |
| POST | `/users/current/orders?cartId={cartCode}&fields=FULL` | Place order |
| GET | `/users/current/orders/{orderCode}?fields=FULL` | Get order details |
