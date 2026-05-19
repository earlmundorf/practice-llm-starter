# SAP UI Template — Documentation

This directory contains feature flow documentation for the SAP Commerce React storefront. Each feature flow has its own subdirectory with three files following the docs-as-code convention.

## Feature Flows

| Flow | Directory | Description |
|------|-----------|-------------|
| Product Browse | `product-browse/` | Search, filter, sort, paginate, and view product details |
| Cart Management | `cart-management/` | Add to cart, update quantities, remove items, cart drawer |
| Checkout Flow | `checkout-flow/` | Multi-step checkout: address, delivery, payment, place order |
| Order History | `order-history/` | View past orders, order details, order status |
| Authentication | `authentication/` | OAuth2 login, token management, protected routes |

## Three Files Per Flow

Each flow directory contains:

| File | Purpose |
|------|---------|
| `context.md` | What the flow does, when it's used, key decisions |
| `components.md` | The files that implement it and what each one does |
| `diagram.md` | Mermaid diagrams with descriptive context |

## Other Documentation

| File | Purpose |
|------|---------|
| `getting-started.md` | Local development setup guide |
| `endpoint-mapping.md` | Complete OCC REST API mapping for every UI operation |

## How to Use

**Before working on a feature**, read its flow directory to understand the context, components, and data flow.

**When adding a new feature**, create the flow directory first — docs before code.
