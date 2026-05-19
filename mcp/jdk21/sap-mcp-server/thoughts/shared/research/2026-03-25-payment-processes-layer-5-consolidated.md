# Layer 5: Consolidated Research — Payment Processes
Date: 2026-03-25

## Executive Summary

The ThinkShop SAP Commerce 2211.50 system implements payment processing entirely through OOTB Commerce infrastructure with a mock payment provider. There are **zero custom payment types, services, or strategies** — the custom `coremcp` extension only adds an MCP tool handler that delegates to standard facades. Payment flows through a well-defined chain from MCP tool → facade → service → strategy → mock commands, with a separate fulfillment process handling capture after order placement.

---

## End-to-End Payment Flow

### 1. Setting Payment on Cart (Checkout)

```
User/Agent → checkout_set_payment (MCP tool)
  ↓
CheckoutSetPaymentToolHandler
  ├── Path A: Existing card → checkoutFacade.setPaymentDetails(paymentId)
  │     → CustomerAccountService.getCreditCardPaymentInfoForCode()
  │     → CommerceCheckoutService.setPaymentInfo()
  │     → DefaultCommercePaymentInfoStrategy.storePaymentInfoForCart()
  │     → cartModel.setPaymentInfo() + recalculate
  │
  └── Path B: New card details → checkoutFacade.createPaymentSubscription()
        → CustomerAccountService.createPaymentSubscription()
        → PaymentService.createSubscription()
        → CreateSubscriptionMockCommand (always succeeds)
        → CreditCardPaymentInfo created with subscriptionId
        → cart.setPaymentInfo() implicitly
```

**Default card** if none provided: Visa 4111111111111111, exp 12/2028.

### 2. Placing the Order (Authorization + Conversion)

```
User/Agent → order_place (MCP tool)
  ↓
CheckoutFacade.placeOrder()
  ↓
CommerceCheckoutService.authorizePayment()
  ↓
DefaultCommercePaymentAuthorizationStrategy.authorizePaymentAmount()
  ↓
PaymentService.authorize(merchantTxCode, amount, currency, address, subscriptionId, cvv, "Mockup")
  ↓
AuthorizationMockCommand / SubscriptionAuthorizationMockCommand → always ACCEPTED
  ↓
PaymentTransaction created (linked to cart)
  └── PaymentTransactionEntry (type=AUTHORIZATION, status=ACCEPTED)
  ↓
Cart converted to Order → Order inherits PaymentTransactions
```

### 3. Fulfillment — Payment Capture

```
Order Process (BPM):
  ... → sendOrderPlacedNotification → takePayment → splitOrder → ...
                                          ↓
TakePaymentAction.executeAction()
  ↓
For each order.paymentTransactions:
  PaymentService.capture(transaction)
    ↓
  CaptureMockCommand → always ACCEPTED
    ↓
  PaymentTransactionEntry (type=CAPTURE, status=ACCEPTED)
    ↓
  OrderStatus → PAYMENT_CAPTURED
    ↓
  Continue to splitOrder → warehouse → shipping → completion
```

**Failure path:** If capture returns non-ACCEPTED → OrderStatus=PAYMENT_NOT_CAPTURED → sendPaymentFailedNotification (but mock never fails).

---

## Type System

| Type | Source | Role |
|------|--------|------|
| `PaymentMode` (code=`advance`) | core platform | Available payment methods |
| `PaymentInfo` / `CreditCardPaymentInfo` | core + payment module | Card details stored on cart/order |
| `PaymentTransaction` | payment module | Transaction record linking payment to order |
| `PaymentTransactionEntry` | payment module | Individual auth/capture/refund entries |
| `SAPGenericPaymentInfo` | basecommerce | SAP-specific extension (not used in this system) |

**Key enums:** PaymentTransactionType (AUTHORIZATION, CAPTURE, REFUND_*, CANCEL), TransactionStatus (ACCEPTED, ERROR, REJECTED, REVIEW)

**Relations:**
- Order → PaymentTransaction (1:N)
- PaymentTransaction → PaymentTransactionEntry (1:N)
- Order/Cart → PaymentInfo (1:1)

---

## Data Configuration (ImpEx)

| What | Where | Details |
|------|-------|---------|
| PaymentMode `advance` | essentialdata-infrastructure.impex (sampledatamcp) | Zero surcharge, AdvancePaymentInfo type |
| BaseStore `electronics` | essentialdata-infrastructure.impex | paymentProvider = `Mockup` |
| Delivery modes | essentialdata-infrastructure.impex | standard ($5.99), express ($14.99), free (promo-only) |
| Sample orders | projectdata-sampledatamcp.impex | 3 orders with payment mode = advance |
| Test data | testdata-thinkshop.impex (coremcp) | Same config for JUnit tenant |

**No test credit cards are pre-seeded** — the mock provider accepts anything at runtime.

---

## Spring Bean Architecture

```
checkoutSetPaymentToolHandler (coremcp)
  └── ref: checkoutFacade
        ↓
defaultCheckoutFacade (commercefacades)
  ├── ref: commerceCheckoutService
  └── ref: customerAccountService
        ↓
defaultCommerceCheckoutService (commerceservices)
  ├── ref: commercePaymentAuthorizationStrategy
  ├── ref: commercePaymentInfoStrategy
  └── ref: commercePlaceOrderStrategy
        ↓
defaultCommercePaymentAuthorizationStrategy
  └── ref: paymentService
        ↓
paymentService (payment module)
  └── ref: mockupCommandFactory
        ├── AuthorizationMockCommand
        ├── SubscriptionAuthorizationMockCommand
        ├── CaptureMockCommand
        └── CreateSubscriptionMockCommand
```

All beans use the alias pattern (`defaultXxx` aliased to `xxx`), making any layer swappable.

---

## Key Files

| File | Purpose |
|------|---------|
| `coremcp/src/.../tools/impl/CheckoutSetPaymentToolHandler.java` | MCP entry point for payment |
| `commercefacades/.../order/impl/DefaultCheckoutFacade.java` | Checkout facade with payment methods |
| `commerceservices/.../order/impl/DefaultCommerceCheckoutService.java` | Checkout service delegating to strategies |
| `commerceservices/.../order/impl/DefaultCommercePaymentAuthorizationStrategy.java` | Authorization logic |
| `commerceservices/.../order/impl/DefaultCommercePaymentInfoStrategy.java` | Payment info storage |
| `yacceleratorfulfilmentprocess/.../actions/order/TakePaymentAction.java` | Payment capture in fulfillment |
| `yacceleratorfulfilmentprocess/resources/.../process/order-process.xml` | BPM definition with takePayment step |
| `commerceservices/resources/commerceservices/mock-payment-spring.xml` | Mock command factory config |
| `sampledatamcp/resources/impex/essentialdata-infrastructure.impex` | Payment mode + BaseStore seed data |
| `coremcp/resources/coremcp-spring.xml` | Tool handler bean definition |

---

## Assumptions

1. **Mock payment is intentional** — this is a demo/MCP server, not a production storefront. The mock provider is fit for purpose.
2. **No fraud check** — the order process includes TakePayment but no explicit fraud screening step before it. The mock always passes.
3. **Single payment mode** — only `advance` is configured. No invoice, PayPal, or other alternative payment methods.
4. **No partial payments** — the system authorizes the full cart amount. No split-payment or installment support.
5. **No refund flow exposed** — while OOTB supports REFUND_FOLLOW_ON and REFUND_STANDALONE transaction types, no MCP tool or custom code exercises this path.
6. **Payment capture is automatic** — TakePaymentAction runs as part of the order process BPM without manual intervention.
7. **Card details are stored** — CreditCardPaymentInfo persists card data (masked in responses). In a real system, this would need PCI compliance review.

---

## Cross-Cutting Observations

1. **Extension point for real PSP:** Replace `mockupCommandFactory` with a real command factory (implementing AuthorizationCommand, CaptureCommand, etc.) and update BaseStore.paymentProvider. No other code changes needed — the strategy/command pattern is designed for this.

2. **Missing from MCP tools:** There is no `checkout_authorize_payment` MCP tool. Authorization happens implicitly during `order_place` inside `CommercePlaceOrderStrategy`. The MCP consumer doesn't control authorization timing.

3. **Delivery vs Payment independence:** Delivery modes and payment modes are independently configured. Promotions can change delivery mode but not payment mode.

4. **Order status lifecycle:** CREATED → CHECKED_VALID → PAYMENT_AUTHORIZED → PAYMENT_CAPTURED → (fulfillment) → COMPLETED. Payment-related statuses are PAYMENT_AUTHORIZED, PAYMENT_CAPTURED, and PAYMENT_NOT_CAPTURED.

---

## Summary

Payment in ThinkShop is a clean, minimal implementation using entirely OOTB SAP Commerce infrastructure. The custom layer adds only the MCP tool handler entry point. The architecture is well-structured for extension: swapping to a real PSP requires implementing payment commands and updating the BaseStore provider — no structural changes to the strategy chain, facades, or fulfillment process. The main gaps for production use would be: real PSP integration, fraud screening, refund tooling, PCI-compliant card handling, and multiple payment method support.
