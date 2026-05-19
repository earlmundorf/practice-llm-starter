# Cart Management — Diagrams

## Add to Cart Flow

When the user adds a product, `cartUtils.addToCart()` ensures a cart exists via `ensureCart()`, posts the entry to OCC, and dispatches a `cartUpdated` event so the Header badge refreshes. The calling component dispatches `cartItemAdded` separately to trigger the pulse animation.

```mermaid
sequenceDiagram
    participant User
    participant Page as ProductCard / Detail
    participant Utils as cartUtils
    participant API as api.ts
    participant OCC as SAP Commerce OCC
    participant Header as Header.tsx

    User->>Page: clicks "Add to Cart"
    Page->>Utils: addToCart(product, quantity)
    Utils->>API: addToCart(productId, quantity)
    API->>API: ensureCart()
    alt no cart code in localStorage
        API->>OCC: POST /users/current/carts
        OCC-->>API: { code: "00001003" }
        API->>API: store cartCode in localStorage
    else cart code exists
        API->>OCC: GET /carts/{cartCode}?fields=DEFAULT
        alt cart still valid
            OCC-->>API: 200 OK
        else cart gone
            API->>OCC: POST /users/current/carts
            OCC-->>API: { code: "00001004" }
            API->>API: store new cartCode
        end
    end
    API->>OCC: POST /carts/{cartCode}/entries
    OCC-->>API: CartModificationWsDTO
    API-->>Utils: success
    Utils->>Utils: dispatchEvent('cartUpdated')
    Utils-->>Page: updated CartItem[]
    Page->>Page: show toast "Added to cart"
    Page->>Header: dispatchEvent('cartItemAdded')
    Header->>Header: pulse animation (400ms)
    Header->>Utils: getCart()
    Utils->>API: getCart()
    API->>OCC: GET /carts/{cartCode}?fields=FULL
    OCC-->>API: CartWsDTO
    API-->>Utils: { items, total, cartCode }
    Utils-->>Header: CartItem[]
    Header->>Header: update badge count
```

## Cart State Sync via Window Events

Three custom events keep cart-related components in sync without shared React state. Any component can trigger the cart drawer to open by dispatching `openCartModal`.

```mermaid
graph LR
    subgraph Producers
        CU[cartUtils methods]
        CM[CartModal]
        PP[Product Pages]
        Chat[Chat / AI Agent]
    end

    subgraph Events
        E1[cartUpdated]
        E2[cartItemAdded]
        E3[openCartModal]
    end

    subgraph Consumers
        HB[Header - Badge Count]
        HP[Header - Pulse Animation]
        HO[Header - Open Drawer]
        CMR[CartModal - Reload]
    end

    CU -->|dispatch| E1
    CM -->|dispatch| E1
    PP -->|dispatch| E2
    Chat -->|dispatch| E3
    PP -->|dispatch| E3

    E1 --> HB
    E1 --> CMR
    E2 --> HP
    E3 --> HO
```

## Cart Modal Interaction

The slide-out drawer renders cart entries with quantity controls, promotions, vouchers, and totals. Each mutation reloads the cart from the server and dispatches `cartUpdated` to keep the Header badge in sync.

```mermaid
graph TD
    Header[Header - Cart Icon + Badge]
    Header -->|click / openCartModal event| Modal[CartModal Drawer]
    Modal --> Load[loadCart]
    Load --> Items[Cart Entry List]
    Load --> Promos[Promotions & Vouchers]

    Items --> Qty[Quantity +/- Controls]
    Items --> Remove[Remove Button]
    Modal --> Clear[Clear Cart Button]

    Qty -->|cartUtils.updateCartItem| OCC[OCC API]
    Remove -->|cartUtils.removeFromCart| OCC
    Clear -->|cartUtils.clearCart| OCC

    OCC --> Reload[Reload Cart]
    Reload --> Event[dispatchEvent cartUpdated]
    Event --> Header

    Modal --> Checkout[Proceed to Checkout]
    Checkout -->|navigate| CheckoutPage[/checkout]
    Modal --> Continue[Continue Shopping]
    Continue -->|onClose| Header
```

## Cart Code Lifecycle

The `cartCode` stored in localStorage follows a clear lifecycle tied to authentication and order placement.

```mermaid
stateDiagram-v2
    [*] --> NoCart: app loads, no cartCode in localStorage

    NoCart --> CartCreated: ensureCart() called\nPOST /carts
    CartCreated --> CartActive: cartCode stored in localStorage

    CartActive --> CartActive: add / update / remove entries
    CartActive --> CartVerified: ensureCart() verifies\nGET /carts/{code}
    CartVerified --> CartActive: cart still valid
    CartVerified --> CartCreated: cart gone, create new

    CartActive --> NoCart: auth.login()\nclears cartCode
    CartActive --> NoCart: auth.logout()\nclears cartCode
    CartActive --> NoCart: api.clearCart()\nDELETE + clear localStorage
    CartActive --> NoCart: order placed\ncartCode removed
```
