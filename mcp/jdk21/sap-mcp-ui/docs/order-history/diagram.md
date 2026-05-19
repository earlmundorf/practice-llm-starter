# Order History — Diagrams

## Order List Flow

The user navigates to `/orders`. The page checks authentication, fetches the order list, merges any recently placed order, and renders clickable cards.

```mermaid
sequenceDiagram
    participant User
    participant Orders as Orders.tsx
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    User->>Orders: navigates to /orders
    Orders->>Orders: check auth.isLoggedIn()
    alt not logged in
        Orders->>User: show "Please log in" error
    else logged in
        Orders->>API: getUser()
        API->>OCC: GET /users/current?fields=FULL
        OCC-->>API: UserWsDTO
        API-->>Orders: User (for display name)

        Orders->>API: getUserOrders()
        API->>OCC: GET /users/current/orders?fields=FULL&pageSize=20
        OCC-->>API: OrderHistoryListWsDTO
        API-->>Orders: Order[]

        alt recent order in location.state
            Orders->>Orders: prepend recentOrder if not in list
        end
        alt recent order ID in sessionStorage
            Orders->>API: getOrder(recentOrderId)
            API->>OCC: GET /users/current/orders/{id}?fields=FULL
            OCC-->>API: OrderWsDTO
            API-->>Orders: Order (prepended to list)
        end

        Orders->>User: render order cards
    end
```

## Order Detail Expansion

Clicking an order card navigates to the OrderConfirmation page, which shows the full detail view. Each line item row can be expanded to show product image and description.

```mermaid
sequenceDiagram
    participant User
    participant Orders as Orders.tsx
    participant Detail as OrderConfirmation.tsx
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    User->>Orders: clicks order card
    Orders->>Detail: navigate("/order-confirmation?orderId=THINK-0001")

    alt order passed via location.state
        Detail->>Detail: use order from state (skip API)
    else no state
        Detail->>API: getOrder("THINK-0001")
        API->>OCC: GET /users/current/orders/THINK-0001?fields=FULL
        OCC-->>API: OrderWsDTO (full)
        API-->>Detail: Order with items, pricing
    end

    Detail->>User: render summary (status, date, total)
    Detail->>User: render items table

    User->>Detail: clicks item row to expand
    Detail->>API: getProduct(productCode)
    API->>OCC: GET /products/{code}?fields=...
    OCC-->>API: ProductWsDTO
    API-->>Detail: Product (image, description)
    Detail->>User: render expanded detail (image, description, product link)
```

## Cancel Order Flow

The cancel method is available in the API layer. It fetches the full order to build entry-level cancellation inputs, then submits the cancellation request.

```mermaid
sequenceDiagram
    participant Caller
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    Caller->>API: cancelOrder("THINK-0001")
    API->>API: getOrder("THINK-0001")
    API->>OCC: GET /users/current/orders/THINK-0001?fields=FULL
    OCC-->>API: OrderWsDTO (with entries)
    API->>API: build cancellationRequestEntryInputs (all entries, full qty)
    API->>OCC: POST /users/current/orders/THINK-0001/cancellation
    OCC-->>API: 200 OK
    API-->>Caller: void (success)
```

## Order Status Colors

Status badges are color-coded on both the list cards and the detail view.

```mermaid
graph LR
    CREATED["CREATED / pending<br/><small>yellow badge</small>"]
    COMPLETED["COMPLETED<br/><small>green badge</small>"]
    CANCELLED["CANCELLED<br/><small>red badge</small>"]
    OTHER["Other / unknown<br/><small>gray badge</small>"]
```

## Page Navigation

How users move between order-related pages.

```mermaid
graph TD
    Checkout["Checkout.tsx<br/>place order"] -->|"navigate with order state<br/>+ new=1 flag"| Confirm["OrderConfirmation.tsx<br/>new order confirmation"]
    Confirm -->|"View All Orders<br/>(passes recentOrder state)"| List["Orders.tsx<br/>order list"]
    List -->|"click order card"| Detail["OrderConfirmation.tsx<br/>order detail view"]
    Detail -->|"Back to Orders link"| List
    Confirm -->|"Continue Shopping"| Home["Home / Products"]
    Detail -->|"View product link"| PDP["Product Detail Page"]
```
