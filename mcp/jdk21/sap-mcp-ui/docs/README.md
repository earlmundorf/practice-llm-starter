# ThinkShop UI — Plan

An AI-powered shopping assistant frontend built with React and Tailwind CSS.
Based on the existing [thinkshop-frontend](https://github.com/earlmundorf/thinkshop-frontend)
reference app, adapted to connect to SAP Commerce via OCC REST endpoints
(for direct UI operations) and the `coremcp` MCP server (for AI chat).

Live reference: [ThinkShop](https://thinkshopstaticdev.z22.web.core.windows.net/)

## Reference App Summary

The existing ThinkShop app is a page-based React SPA with:

- **6 pages**: Home, Products, Chat, Checkout, OrderConfirmation, Orders
- **Slide-out cart drawer** (CartModal) accessible from header
- **AI chat page** with contextual suggestions and loading states
- **UserPicker** modal for selecting/switching users
- **Dark mode** via React Context
- **REST API service layer** (`/api/*`) for products, cart, orders, chat
- **Desktop top nav + mobile bottom nav** responsive pattern

```
  Pages:
  /              Home         Hero, features, stats
  /products      Products     Grid/list view, add-to-cart
  /chat          Chat         AI assistant, suggestions
  /checkout      Checkout     Order summary, confirm
  /order-confirmation         Success state
  /orders        Orders       History with expandable details

  Components:
  Header         Desktop top nav + mobile bottom nav, cart badge, avatar
  CartModal      Slide-out drawer, quantity controls, checkout CTA
  ProductCard    Price, stock badge, quantity input, add button
  Toast          Success/error/info notifications
  UserPicker     User selection modal
```

## What Changes for SAP Commerce

The page structure and UI components stay the same. The **service layer**
changes from REST calls to `/api/*` to SAP Commerce OCC REST endpoints.
MCP is only used by the Chat page for AI tool invocation.

```
  BEFORE (thinkshop-frontend)          AFTER (this project)
  ============================          ==========================

  api.getProducts()                     GET  /occ/v2/.../products/search
  api.addToCart(userId, prodId, qty)    POST /occ/v2/.../users/current/carts/{id}/entries
  api.getCart(userId)                   GET  /occ/v2/.../users/current/carts/{id}
  api.createOrder(orderData)            POST /occ/v2/.../users/current/orders
  api.getUserOrders(userId)             GET  /occ/v2/.../users/current/orders
  api.sendChatMessage(msg, sid, uid)    Claude API + MCP tools (or OCC directly)

  /api/*  (custom REST backend)         /occ/v2/{baseSiteId}/*  (SAP Commerce)
```

Key insight: **OCC REST endpoints already exist** for every operation
the UI needs. The pages (Products, Cart, Checkout, Orders) call OCC
directly — no MCP indirection needed. MCP is for the Chat page where
Claude needs structured tool discovery and invocation.

See [endpoint-mapping.md](endpoint-mapping.md) for the complete mapping
of every `api.ts` method to its OCC equivalent, including request/response
shapes and type field differences.

## Architecture

```
+------------------+         +------------------+
|                  |  OCC    |                  |
|  ThinkShop UI    +-------->| SAP Commerce     |
|  (React App)     | REST    | Platform         |
|                  |<--------+                  |
+--------+---------+         +-------+----------+
         |                           ^
         | Claude API                | Facade calls
         v                           |
+------------------+         +-------+----------+
|  Claude / LLM    |  MCP    |    coremcp      |
|  (decides which  +-------->|    Extension     |
|   tools to call) | JSON-RPC|  (Chat only)     |
+------------------+         +------------------+
```

Two paths to SAP Commerce:

- **Pages (Products, Cart, Checkout, Orders)** — OCC REST directly
  - `GET /occ/v2/electronics/products/search`
  - `POST /occ/v2/electronics/users/current/carts/{id}/entries`
  - etc.

- **Chat page** — Claude API decides tools, executed via MCP or OCC
  - User message -> Claude API -> tool_use -> execute against OCC or MCP -> result back to Claude

Both paths use the same OAuth2 token for authentication.

## Auth Difference

ThinkShop reference uses a simple UserPicker (select from list, userId
in localStorage). SAP Commerce uses OAuth2:

```
  ThinkShop reference:    localStorage userId -> /api/cart/{userId}
  SAP Commerce version:   OAuth2 Bearer token -> /occ/v2/electronics/users/current/*
```

We'll replace the UserPicker with an OAuth2 login form (username/password
grant) and store the token in memory. The token authenticates all OCC
calls and identifies the user (`current` in URL paths resolves from token).

## Phases

### Phase 1 — Docs + Reference (current)
- Documentation: plan, features, setup
- Reference app available at `ui/sample/thinkshop-frontend/`
- No new implementation yet

### Phase 2 — Clone + Adapt Service Layer
- Copy reference app structure into `ui/src/`
- Match package versions (React 19, Vite 7, Tailwind 4)
- Replace `api.ts` with OCC REST service (see [endpoint-mapping.md](endpoint-mapping.md))
- Replace UserPicker with OAuth2 login
- Add Vite proxy for `/occ` and `/authorizationserver`

### Phase 3 — OCC Integration
- Products page: `GET /products/search` instead of `api.getProducts()`
- Cart: create cart, add entries, update quantities via OCC
- Checkout: set address, delivery mode, payment, place order
- Orders: order history and detail via OCC
- Adapt types for OCC response shapes (price objects, `code` vs `id`)

### Phase 4 — Chat + LLM Integration
- Claude API connection with tool definitions
- Tool calls execute as OCC REST requests (or via coremcp MCP)
- Chat page calls Claude, Claude calls tools, results rendered
- Contextual suggestions from conversation history
- Loading states with contextual messages (keep existing pattern)

### Phase 5 — Polish
- Dark mode (keep existing DarkModeContext)
- Animations (keep existing CSS animations)
- Mobile bottom nav (keep existing pattern)
- Error handling, toast notifications

## Tech Stack

| Layer | Choice | Version | Notes |
|-------|--------|---------|-------|
| Framework | React | 19 | Matches reference app |
| Build | Vite | 7 | Matches reference app |
| Styling | Tailwind CSS | 4 | `@tailwindcss/postcss` plugin |
| Routing | React Router | 7 | Matches reference app |
| State | React Context | built-in | DarkMode context, keep simple |
| HTTP | fetch (native) | — | For both MCP and OAuth2 |
| LLM | Anthropic SDK | latest | `@anthropic-ai/sdk` |
| Linting | ESLint | 9 | Matches reference app |
| Language | TypeScript | 5.9 | Strict mode |
