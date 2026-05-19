# ThinkShop UI — Features

Features carried over from the reference app, plus adaptations for
SAP Commerce / MCP integration.

## Pages

### Home (`/`)

Carried over as-is from reference. Hero section with animated background
blobs, gradient title, two CTAs ("Browse Products", "AI Assistant"),
feature cards (AI assistant, fast checkout, premium electronics),
quick stats row.

No backend calls — purely presentational.

### Products (`/products`)

**Reference behavior**: Fetches all products via `api.getProducts()`,
displays in grid (2-4 columns) or list view. ProductCard shows name,
description, price, stock badge, quantity input, add-to-cart button.
Toast notifications on add/error.

**MCP adaptation**:
- Replace `api.getProducts()` with `mcpClient.callTool('product_search', { query: '*' })`
- Add search input at top (maps to `product_search` query parameter)
- Add sort dropdown (relevance, price-asc, price-desc, name-asc)
- Add pagination (SAP Commerce returns paginated results)
- ProductCard adapted for SAP Commerce product data shape:
  - `product.code` instead of `product.id`
  - `product.price.formattedValue` instead of `product.price.toFixed(2)`
  - `product.stock.stockLevelStatus` instead of `product.stockQuantity`

### Chat (`/chat`)

**Reference behavior**: Full-page chat with contextual suggestion
chips above the message area. Messages typed or selected from
suggestions. Sends to `/api/chat/message`, gets back AI response.
Loading states with contextual emoji messages. Cart update events
dispatched after responses.

**MCP adaptation**:
- Replace `api.sendChatMessage()` with direct Claude API call
- Pass MCP tool definitions to Claude as available tools
- When Claude returns `tool_use`, execute via `mcpClient.callTool()`
- Feed tool results back to Claude for natural language response
- Keep contextual suggestions but generate client-side (no backend
  `/api/chat/suggestions` endpoint needed — Claude handles this)
- Keep loading messages pattern (`getLoadingMessage()`)

### Checkout (`/checkout`)

**Reference behavior**: Two-column layout — order summary (left) and
confirm order (right). Shows cart items with prices. Single "Place Order"
button. Navigates to order confirmation on success.

**MCP adaptation**:
- Add address step: `checkout_set_delivery_address`
- Add shipping step: `checkout_set_delivery_mode`
- Add payment step: `checkout_set_payment` (mock defaults)
- Final step: `order_place`
- Could be multi-step wizard OR keep simple single-page with sections
- Cart loaded via `cart_get` instead of `cartUtils.getCart()`

### Order Confirmation (`/order-confirmation`)

**Reference behavior**: Success checkmark, order details grid (ID,
username, status, date), items table with prices, continue shopping
and view orders CTAs.

**MCP adaptation**:
- Order data comes from `order_place` response (already has everything)
- Or fetch via `order_get` using order code from URL params
- Adapt field names: `order.code` not `order.orderId`, etc.

### Orders (`/orders`)

**Reference behavior**: List of orders with expandable details. Each
order shows status badge (color-coded), date, item count, total.
Expanded view shows line items, cancel button for pending orders.

**MCP adaptation**:
- `order_history` returns paginated list
- `order_get` for expanded detail
- Status values from SAP Commerce (CREATED, CHECKED_VALID, etc.)
- No cancel endpoint in current MCP tools (defer or add later)

### Visual Search (integrated into Products page)

Visual search is built into the Products page search bar — no separate page. Users can:
- Click the camera icon in the search bar to pick/capture an image
- Paste an image from clipboard while the search bar is focused

When an image is provided, the backend sends it to GPT-4o Vision for identification, then searches the SAP Commerce Solr catalog using AI-suggested search terms.

**Backend endpoint**: `POST /{baseSiteId}/agent/visual-search`

**UI behavior**:
1. Image uploaded via camera button or paste triggers immediate search (no confirmation step)
2. Normal product grid is replaced with visual search results overlay
3. AI Analysis banner shows the AI's reasoning about the image
4. Search term chips show what terms were used to search the catalog
5. Product results use the same ProductCard component as normal search
6. Match type badges overlay each card: Best Match (green), Similar (blue), You Might Like (purple)
7. "Clear" button dismisses results and restores normal product grid
8. Grid/list view toggle applies to visual results too

**Key features**:
- Mobile-ready: `capture="environment"` for native camera
- Client-side validation: file type whitelist (JPEG, PNG, WebP, GIF), 10MB size limit
- Reuses existing ProductCard with add-to-cart support
- Full OCC product data (price, stock, images) via mapOccProduct mapping

## Components

### Header

Keep both desktop (top nav) and mobile (bottom nav) patterns from
reference. Changes:

- Remove UserPicker trigger — replace with OAuth2 login/logout
- Avatar shows logged-in SAP Commerce user
- Cart badge count from `cart_get` tool
- Dark mode toggle stays as-is
- Navigation links: Home, Products, Orders, Chat, Cart

### CartModal (Slide-out Drawer)

Keep slide-out drawer pattern. Changes:

- Load cart via `mcpClient.callTool('cart_get')` instead of `cartUtils.getCart()`
- Quantity updates via MCP (may need `cart_update_product` tool — not yet defined,
  could use `cart_add_product` with delta quantity)
- Remove item (may need `cart_remove_product` tool — not yet defined)
- Checkout button navigates to `/checkout`

### ProductCard

Keep card layout. Adapt for SAP Commerce product shape:

```
  Reference:               SAP Commerce:
  product.id               product.code
  product.name             product.name
  product.description      product.description (from DESCRIPTION option)
  product.price            product.price.value
  product.stockQuantity    product.stock.stockLevel
  —                        product.images[0].url
  —                        product.averageRating
  —                        product.categories
```

Add product image display (reference uses emoji placeholder).

### Toast

Keep as-is. Success/error/info notifications with slide-in animation.

### DarkModeContext

Keep as-is. `dark` class on root element, Tailwind dark: variant.

## New Components Needed

| Component | Purpose |
|-----------|---------|
| `LoginForm` | OAuth2 username/password login (replaces UserPicker) |
| `SearchBar` | Product search input for Products page |
| `Pagination` | Page controls for search results and order history |
| `StarRating` | Display product ratings (SAP Commerce has ratings) |
| `ProductImage` | Image display with fallback (SAP Commerce has media URLs) |
| `CheckoutSteps` | Multi-step checkout wizard (address, shipping, payment, confirm) |

## Service Layer Changes

### Reference: `src/services/api.ts`

Single file with REST calls and `cartUtils` helper. All methods are
`async` functions using `fetch`.

### New: `src/services/mcp-client.ts`

MCP JSON-RPC client that replaces the REST API:

```ts
// Key methods:
initialize()           // Start MCP session, get MCP-Session-Id
callTool(name, args)   // Execute a tool, return parsed result
listTools()            // Get available tool definitions
terminate()            // End session (DELETE)
```

### New: `src/services/auth.ts`

OAuth2 token management for SAP Commerce:

```ts
// Key methods:
login(username, password)   // Password grant -> access token
loginClient()               // Client credentials grant
getToken()                  // Current token (auto-refresh)
logout()                    // Clear token
isAuthenticated()           // Check token validity
```

### New: `src/services/llm.ts`

Claude API integration for the Chat page:

```ts
// Key methods:
sendMessage(messages, tools, mcpClient)  // Chat with tool use
// Handles: user msg -> Claude -> tool_use -> callTool -> tool_result -> Claude -> text
```

## Events

Keep the existing `window.dispatchEvent` pattern for cross-component
communication:

| Event | Trigger | Listeners |
|-------|---------|-----------|
| `cartUpdated` | After any cart modification | Header (badge), CartModal, Checkout |
| `cartItemAdded` | After add-to-cart | Header (pulse animation) |

## Theming

Keep reference app's color scheme:

```
  Primary:     blue-600 / dark:blue-500     (buttons, links, accents)
  Background:  gray-50 / dark:gray-900      (page background)
  Surface:     white / dark:gray-800         (cards, modals)
  Text:        gray-900 / dark:white         (headings)
  Muted:       gray-600 / dark:gray-400      (secondary text)
  Success:     green-600 / dark:green-400    (in-stock, confirmations)
  Error:       red-600 / dark:red-400        (out-of-stock, errors)
  Warning:     yellow-800 / dark:yellow-300  (pending status)
```

## Animations (from reference `index.css`)

- `animate-bounce-three` — Hero icon bounce (3 bounces then stop)
- `animate-slide-in` — Toast slide-in from right
- `animate-cart-pulse` — Cart icon pulse on add
- `animate-blob` — Background blob movement (with delays)
