# Cart Management — Diagrams

## Add to Cart Flow

When the user adds a product, the UI ensures a cart exists, adds the entry, and updates the cart badge.

```mermaid
sequenceDiagram
    participant User
    participant Card as ProductCard
    participant API as api.ts
    participant OCC as SAP Commerce OCC
    participant Header as Header.tsx

    User->>Card: clicks "Add to Cart"
    Card->>API: ensureCart()
    alt no cart exists
        API->>OCC: POST /users/current/carts
        OCC-->>API: { code: "00001003" }
        API->>API: store cartCode
    end
    Card->>API: addToCart(cartCode, productCode, quantity)
    API->>OCC: POST /carts/{cartCode}/entries
    OCC-->>API: CartModificationWsDTO
    API-->>Card: success
    Card->>Card: show Toast "Added to cart"
    Card->>Header: dispatchEvent('cartUpdated')
    Header->>API: getCart(cartCode)
    API->>OCC: GET /carts/{cartCode}
    OCC-->>API: CartWsDTO
    Header->>Header: update badge count
```

## Cart Drawer Interaction

```mermaid
graph TD
    Header[Header - Cart Icon + Badge]
    Header -->|click| Modal[CartModal Drawer]
    Modal --> Items[Cart Entry List]
    Items --> Qty[Quantity +/- Controls]
    Items --> Remove[Remove Button]
    Qty -->|PATCH| OCC[OCC API]
    Remove -->|DELETE| OCC
    OCC --> Event[dispatchEvent cartUpdated]
    Event --> Header
    Modal --> Checkout[Proceed to Checkout]
    Checkout -->|navigate| CheckoutPage[/checkout]
```
