# Checkout Flow — Diagrams

## Checkout Page Sequence

The checkout page loads data in parallel, then reacts to user selections. Address selection triggers delivery mode fetching. Order placement sets payment and places the order in sequence.

```mermaid
sequenceDiagram
    participant User
    participant Checkout as Checkout.tsx
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    Note over Checkout: Page loads
    Checkout->>API: getUser(), getAddresses(), getCart()
    API->>OCC: parallel: GET user, GET addresses, GET cart
    OCC-->>API: user, addresses, cart items
    API-->>Checkout: render form with data

    Checkout->>API: getCartPromotions()
    API->>OCC: GET /carts/{cartCode}?fields=FULL
    OCC-->>API: promotions, totals, discounts
    API-->>Checkout: display applied/potential promotions

    Note over User: Selects delivery address
    User->>Checkout: selects address (or creates new)
    Checkout->>API: setCartDeliveryAddress(address)
    API->>OCC: PUT /carts/{cartCode}/addresses/delivery?addressId={id}
    OCC-->>API: success

    Checkout->>API: getDeliveryModes()
    API->>OCC: GET /carts/{cartCode}/deliverymodes
    OCC-->>API: DeliveryModeListWsDTO
    API-->>Checkout: render mode options (auto-select first)

    Checkout->>API: setCartDeliveryMode(firstMode)
    API->>OCC: PUT /carts/{cartCode}/deliverymode
    OCC-->>API: success
    Checkout->>API: getCartPromotions()
    API-->>Checkout: updated totals with delivery cost

    Note over User: Optionally applies coupon
    User->>Checkout: enters coupon code
    Checkout->>API: applyVoucher(code)
    API->>OCC: POST /carts/{cartCode}/vouchers
    OCC-->>API: success
    Checkout->>API: getCartPromotions()
    API-->>Checkout: updated discounts and totals

    Note over User: Clicks "Place Order"
    User->>Checkout: submit
    Checkout->>Checkout: disable button (prevent double-submit)
    Checkout->>API: createOrder(address, payment)
    API->>OCC: POST /carts/{cartCode}/paymentdetails
    OCC-->>API: PaymentDetailsWsDTO
    API->>OCC: POST /users/current/orders?cartId={cartCode}
    OCC-->>API: OrderWsDTO
    API->>OCC: DELETE stale carts (cleanup)
    API-->>Checkout: order with ID

    alt Normal mode
        Checkout->>Checkout: navigate('/order-confirmation?orderId=...&new=1')
    else From-chat mode
        Checkout->>Checkout: write order summary to sessionStorage
        Checkout->>Checkout: navigate('/chat')
    else Embedded mode
        Checkout->>Checkout: onOrderPlaced(orderId)
    end
```

## Checkout Entry Points

The checkout can be reached three ways, each with a different return path after order placement.

```mermaid
graph TD
    A[Cart Page] -->|"navigate('/checkout')"| C[Checkout Page]
    B[Chat Agent] -->|"action: checkout"| D{Delivery Method}
    D -->|"Navigation mode"| C
    D -->|"Embedded mode"| E[Checkout in Chat]

    C -->|order placed| F[Order Confirmation]
    C -->|"fromChat + order placed"| G[Chat with order summary]
    E -->|"onOrderPlaced"| G

    F -->|"fromChat"| G
    F -->|normal| H[Continue Shopping / View Orders]

    style A fill:#3b82f6,color:#fff
    style B fill:#8b5cf6,color:#fff
    style F fill:#22c55e,color:#fff
    style G fill:#22c55e,color:#fff
```

## Session Storage Communication

When checkout is triggered from the chat via navigation (not embedded), sessionStorage bridges the two pages.

```mermaid
sequenceDiagram
    participant Chat as Chat.tsx
    participant Storage as sessionStorage
    participant Checkout as Checkout.tsx
    participant Confirm as OrderConfirmation.tsx

    Note over Chat: Agent returns action: "checkout"
    Chat->>Storage: set thinkshop_from_chat = "true"
    Chat->>Chat: navigate('/checkout')

    Note over Checkout: User completes checkout
    Checkout->>Storage: read thinkshop_from_chat → "true"

    alt Order placed
        Checkout->>Storage: remove thinkshop_from_chat
        Checkout->>Storage: set thinkshop_checkout_result = {type: "placed", orderId, items, totals}
        Checkout->>Chat: navigate('/chat')
    else Order cancelled
        Checkout->>Storage: remove thinkshop_from_chat
        Checkout->>Storage: set thinkshop_checkout_result = {type: "cancelled"}
        Checkout->>Chat: navigate('/chat')
    end

    Note over Chat: Chat mounts, reads result
    Chat->>Storage: read thinkshop_checkout_result
    Chat->>Storage: remove thinkshop_checkout_result
    Chat->>Chat: post order summary or cancellation message
    Chat->>Chat: clear old cart, create fresh cart
```

## Order Confirmation Page

The confirmation page serves both new orders and order history lookups, with expandable product details.

```mermaid
graph TD
    A["/order-confirmation?orderId=X&new=1"] --> B{order in location.state?}
    B -->|yes| C[Display order immediately]
    B -->|no| D[api.getOrder orderId]
    D --> C

    C --> E{isNew?}
    E -->|yes| F["Success banner: Order #X Confirmed!"]
    E -->|no| G["Header: Order #X with Back to Orders link"]

    F --> H[Order details table with expandable items]
    G --> H

    H -->|click item| I[api.getProduct for image and description]

    H --> J{fromChat?}
    J -->|yes| K["Back to Chat button → writes result to sessionStorage"]
    J -->|no| L[Continue Shopping / View All Orders]

    style F fill:#22c55e,color:#fff
    style K fill:#8b5cf6,color:#fff
```
