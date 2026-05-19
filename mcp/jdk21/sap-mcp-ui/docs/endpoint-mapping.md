# ThinkShop UI — Endpoint Mapping

Maps every `api.ts` method from the reference ThinkShop app to the
equivalent SAP Commerce OCC REST endpoint. The UI hits OCC directly
for all page operations. MCP (coremcp) is only used by the Chat page
where Claude needs tool discovery/invocation.

## Base URL

```
Reference:    /api                              (custom backend)
SAP Commerce: /occ/v2/electronics               (OCC REST)
```

The `electronics` base site ID comes from `VITE_BASE_SITE_ID` env var.
User ID in OCC paths is `current` for the authenticated user.

## Auth

```
Reference:    No auth — userId picked from list, passed in URL
SAP Commerce: OAuth2 Bearer token in Authorization header

POST /authorizationserver/oauth/token
  grant_type=password
  &client_id=mobile_android
  &client_secret=secret
  &username=user@example.com
  &password=1234

Response: { access_token: "xyz", expires_in: 43200 }

All subsequent requests:
  Authorization: Bearer xyz
```

---

## Products

### `api.getProducts()` — Get all products

```
BEFORE:  GET /api/products
AFTER:   GET /occ/v2/electronics/products/search?query=:relevance&fields=FULL&pageSize=50
```

OCC returns paginated `ProductSearchPageWsDTO`:
```json
{
  "products": [
    {
      "code": "1934793",
      "name": "PowerShot A480",
      "price": { "value": 99.85, "currencyIso": "USD", "formattedValue": "$99.85" },
      "stock": { "stockLevelStatus": "inStock", "stockLevel": 583 },
      "images": [{ "format": "thumbnail", "url": "/medias/..." }]
    }
  ],
  "pagination": { "currentPage": 0, "pageSize": 50, "totalResults": 100 },
  "sorts": [{ "code": "relevance", "selected": true }]
}
```

**Type mapping:**
```
Reference Product       OCC ProductWsDTO
-------------------     ----------------------------
product.id              product.code
product.name            product.name
product.description     product.description
product.price           product.price.value
product.stockQuantity   product.stock.stockLevel
product.imageUrl        product.images[0].url
—                       product.averageRating
—                       product.price.formattedValue
```

---

## Cart

OCC carts require a cart ID. On first access, create a cart, then
reuse the ID for the session.

### Create cart (new — no reference equivalent)

```
POST /occ/v2/electronics/users/current/carts
  fields=FULL

Response: { "code": "00001003", "guid": "abc123", ... }
```

Store `cartCode` in state. Use it for all subsequent cart operations.

### `api.getCart(userId)` — Get cart

```
BEFORE:  GET /api/cart/{userId}
AFTER:   GET /occ/v2/electronics/users/current/carts/{cartCode}?fields=FULL
```

OCC returns `CartWsDTO`:
```json
{
  "code": "00001003",
  "entries": [
    {
      "entryNumber": 0,
      "product": { "code": "1934793", "name": "PowerShot A480" },
      "quantity": 2,
      "totalPrice": { "value": 199.70, "formattedValue": "$199.70" },
      "basePrice": { "value": 99.85, "formattedValue": "$99.85" }
    }
  ],
  "totalItems": 1,
  "totalUnitCount": 2,
  "totalPrice": { "value": 199.70, "formattedValue": "$199.70" }
}
```

**Type mapping:**
```
Reference Cart          OCC CartWsDTO
-------------------     ----------------------------
cart.userId             (implicit from auth token)
cart.items[]            cart.entries[]
cart.total              cart.totalPrice.value
item.productId          entry.product.code
item.productName        entry.product.name
item.quantity           entry.quantity
item.price              entry.basePrice.value
```

### `api.addToCart(userId, productId, quantity)` — Add to cart

```
BEFORE:  POST /api/cart/{userId}/items
         { productId, quantity }

AFTER:   POST /occ/v2/electronics/users/current/carts/{cartCode}/entries
         { "product": { "code": "1934793" }, "quantity": 2 }
         fields=FULL
```

Returns `CartModificationWsDTO`:
```json
{
  "statusCode": "success",
  "quantityAdded": 2,
  "quantity": 2,
  "entry": {
    "entryNumber": 0,
    "product": { "code": "1934793" },
    "quantity": 2,
    "totalPrice": { "value": 199.70 }
  }
}
```

### `api.updateCartItem(userId, productId, quantity)` — Update quantity

```
BEFORE:  PUT /api/cart/{userId}/items/{productId}
         { quantity }

AFTER:   PATCH /occ/v2/electronics/users/current/carts/{cartCode}/entries/{entryNumber}
         { "quantity": 3 }
         fields=FULL
```

Note: OCC uses `entryNumber` (integer, from cart entries), not product
code. The UI needs to look up the entryNumber from the cart entry that
matches the product code.

### `api.removeFromCart(userId, productId)` — Remove item

```
BEFORE:  DELETE /api/cart/{userId}/items/{productId}

AFTER:   DELETE /occ/v2/electronics/users/current/carts/{cartCode}/entries/{entryNumber}
```

Returns 200 OK with no body.

### `api.clearCart(userId)` — Clear cart

No direct OCC equivalent for clearing all entries. Two options:

**Option A** — Delete each entry individually:
```
DELETE /occ/v2/electronics/users/current/carts/{cartCode}/entries/0
DELETE /occ/v2/electronics/users/current/carts/{cartCode}/entries/1
...
```

**Option B** — Delete the cart and create a new one:
```
DELETE /occ/v2/electronics/users/current/carts/{cartCode}
POST   /occ/v2/electronics/users/current/carts
```

Option B is simpler. Just replace the stored cartCode.

---

## Checkout

The reference app has a single "Place Order" button. OCC requires
setting delivery address, delivery mode, and payment before placing.

### Set delivery address (new)

```
POST /occ/v2/electronics/users/current/carts/{cartCode}/addresses/delivery
  { "firstName": "John", "lastName": "Doe", "line1": "123 Main St",
    "town": "New York", "postalCode": "10001",
    "country": { "isocode": "US" } }
  fields=FULL
```

### Get delivery modes (new)

```
GET /occ/v2/electronics/users/current/carts/{cartCode}/deliverymodes
  fields=FULL
```

Returns list of available shipping methods.

### Set delivery mode (new)

```
PUT /occ/v2/electronics/users/current/carts/{cartCode}/deliverymode?deliveryModeId=standard-gross
```

### Set payment details (new)

```
POST /occ/v2/electronics/users/current/carts/{cartCode}/paymentdetails
  { "accountHolderName": "John Doe",
    "cardNumber": "4111111111111111",
    "cardType": { "code": "visa" },
    "expiryMonth": "12", "expiryYear": "2028",
    "billingAddress": { ... } }
  fields=FULL
```

### `api.createOrder(orderData)` — Place order

```
BEFORE:  POST /api/orders
         { userId, items: [...] }

AFTER:   POST /occ/v2/electronics/users/current/orders?cartId={cartCode}&fields=FULL
```

OCC returns `OrderWsDTO` with the full order details.

**Type mapping:**
```
Reference Order         OCC OrderWsDTO
-------------------     ----------------------------
order.orderId           order.code
order.userId            (implicit from auth)
order.status            order.statusDisplay
order.orderDate         order.created
order.totalAmount       order.totalPrice.value
order.items[]           order.entries[]
item.productName        entry.product.name
item.priceAtPurchase    entry.basePrice.value
item.lineTotal          entry.totalPrice.value
```

---

## Orders

### `api.getUserOrders(userId)` — Order history

```
BEFORE:  GET /api/orders/user/{userId}

AFTER:   GET /occ/v2/electronics/users/current/orders?fields=FULL&pageSize=20
```

Returns `OrderHistoryListWsDTO`:
```json
{
  "orders": [
    {
      "code": "00001001",
      "status": "COMPLETED",
      "statusDisplay": "completed",
      "placed": "2025-12-15T10:30:00Z",
      "total": { "value": 199.70, "formattedValue": "$199.70" }
    }
  ],
  "pagination": { "currentPage": 0, "totalResults": 5 }
}
```

### `api.getOrder(orderId)` — Get order details

```
BEFORE:  GET /api/orders/{orderId}

AFTER:   GET /occ/v2/electronics/users/current/orders/{orderCode}?fields=FULL
```

### `api.cancelOrder(orderId)` — Cancel order

```
BEFORE:  DELETE /api/orders/{orderId}

AFTER:   POST /occ/v2/electronics/users/current/orders/{orderCode}/cancellation
         { "cancellationRequestEntryInputs": [
             { "orderEntryNumber": 0, "quantity": 2 },
             { "orderEntryNumber": 1, "quantity": 1 }
           ] }
```

OCC cancel requires specifying which entries and quantities to cancel.
To cancel the full order, list all entries with their full quantities.

---

## Users

### `api.getUser(userId)` — Get user details

```
BEFORE:  GET /api/users/{userId}

AFTER:   GET /occ/v2/electronics/users/current?fields=FULL
```

Returns `UserWsDTO`:
```json
{
  "uid": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "name": "John Doe",
  "currency": { "isocode": "USD" },
  "language": { "isocode": "en" }
}
```

**Type mapping:**
```
Reference User          OCC UserWsDTO
-------------------     ----------------------------
user.id                 (no numeric ID — uid is email)
user.username           user.uid
user.email              user.uid
user.fullName           user.name
```

### `api.getAllUsers()` — List all users

No OCC equivalent. Not needed — OAuth2 login replaces user selection.

### `api.createUser(userData)` — Create user

```
BEFORE:  POST /api/users
         { username, email, fullName }

AFTER:   POST /occ/v2/electronics/users
         { "uid": "john@example.com", "firstName": "John",
           "lastName": "Doe", "password": "Test1234!",
           "titleCode": "mr" }
```

Only needed if we add a registration page.

---

## Chat

Chat does NOT use OCC REST directly. Two approaches:

### Approach A — MCP via coremcp (planned)

Chat page calls Claude API with MCP tool definitions. Claude returns
`tool_use` blocks. React executes them via coremcp JSON-RPC endpoint.

```
User message -> Claude API -> tool_use: product_search
  -> POST /occ/v2/electronics/mcp (JSON-RPC tools/call)
  -> tool result back to Claude -> natural language response
```

### Approach B — Claude API with OCC calls directly

Chat page calls Claude API with tool definitions that map to OCC REST
endpoints. React executes the tool calls as OCC REST requests directly
(no coremcp needed for chat).

```
User message -> Claude API -> tool_use: product_search
  -> GET /occ/v2/electronics/products/search?query=camera
  -> tool result back to Claude -> natural language response
```

Approach B is simpler for the UI (no MCP session management). The MCP
server (coremcp) would then be for non-browser MCP clients like
Claude Code.

Either way, the chat backend endpoints are removed:
```
REMOVED:  POST /api/chat/message       -> Claude API (client-side)
REMOVED:  POST /api/chat/suggestions   -> Client-side logic or Claude
```

---

## Visual Search

### `api.visualSearch(base64Image, mimeType)` — Image-based product search

```
POST /occ/v2/electronics/agent/visual-search
  Authorization: Bearer {token}
  Content-Type: application/json
  { "image": "<base64>", "mimeType": "image/jpeg" }
```

Returns `VisualSearchResult`:
```json
{
  "visionAnalysis": "A pair of black wireless over-ear headphones",
  "aiDetail": {
    "searchTerms": ["wireless headphones", "over-ear headphones black"],
    "reasoning": "The image shows black wireless over-ear headphones with a padded headband..."
  },
  "products": [
    {
      "product": { "name": "...", "code": "...", "price": { "formattedValue": "$349.99" }, "thumbnailUrl": "...", "description": "..." },
      "matchType": "bestMatch",
      "confidence": 0.95
    }
  ]
}
```

Match types: `bestMatch` (best match, 0.95 or 0.9), `similar` (similar products, 0.7), `explore` (you might like, 0.4).

Note: The frontend adds `mappedProducts` to the response — products mapped through `mapOccProduct` for full OCC product data (price, stock, images).

Error statuses: 400 (bad input), 413 (>10MB), 429 (rate limit), 503 (OpenAI down).

---

## Summary Table

| Reference Method | HTTP | OCC Endpoint | Notes |
|---|---|---|---|
| `api.getProducts()` | GET | `/products/search?query=:relevance` | Paginated |
| `api.addToCart()` | POST | `/users/current/carts/{cartCode}/entries` | Product code in body |
| `api.getCart()` | GET | `/users/current/carts/{cartCode}` | Need cart code |
| `api.updateCartItem()` | PATCH | `/users/current/carts/{cartCode}/entries/{entryNumber}` | Entry number not product ID |
| `api.removeFromCart()` | DELETE | `/users/current/carts/{cartCode}/entries/{entryNumber}` | Entry number |
| `api.clearCart()` | DELETE | `/users/current/carts/{cartCode}` | Then create new cart |
| `api.createOrder()` | POST | `/users/current/orders?cartId={cartCode}` | Needs address+shipping+payment first |
| `api.getOrder()` | GET | `/users/current/orders/{orderCode}` | |
| `api.getUserOrders()` | GET | `/users/current/orders` | Paginated |
| `api.cancelOrder()` | POST | `/users/current/orders/{code}/cancellation` | Entry-level cancellation |
| `api.getUser()` | GET | `/users/current` | Auth token identifies user |
| `api.createUser()` | POST | `/users` | Registration |
| `api.visualSearch()` | POST | `/agent/visual-search` | GPT-4o Vision + Solr |
| `api.sendChatMessage()` | — | Claude API + MCP or OCC | Client-side |
| `api.getChatSuggestions()` | — | Client-side | No backend needed |

All OCC paths are prefixed with `/occ/v2/electronics`.
All require `Authorization: Bearer {token}` header.

---

## Key Differences to Handle in Code

1. **No userId in URLs** — OCC uses `current` (from auth token), not explicit IDs
2. **Cart needs creation** — Must POST to create cart before adding items
3. **Cart entries use entryNumber** — Not product code. Look up from cart response.
4. **Checkout is multi-step** — Address, delivery mode, payment, THEN place order
5. **Prices are objects** — `{ value: 99.85, formattedValue: "$99.85" }` not plain numbers
6. **Product ID is `code`** — String like `"1934793"`, not numeric ID
7. **Order cancel is entry-level** — Must specify which entries and quantities
8. **Search is always paginated** — No "get all" endpoint, use large pageSize

## `cartUtils` Replacement

The reference `cartUtils` wraps api calls and manages userId from
localStorage. For OCC:

- **No localStorage userId** — Auth token in memory identifies the user
- **Store cartCode** in React state or context instead
- **cartUtils.getCart()** → fetch cart by code from OCC
- **cartUtils.addToCart()** → POST entry to OCC, dispatch cartUpdated event
- **cartUtils.removeFromCart()** → need entryNumber lookup first
- **cartUtils.getCartTotal()** → use `cart.totalPrice.value` from OCC response
- **cartUtils.getCartCount()** → use `cart.totalUnitCount` from OCC response
