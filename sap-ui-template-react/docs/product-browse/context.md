# Product Browse — Context

## What This Flow Does

Allows users to search, filter, sort, and paginate through the product catalog. Users can view product cards in a grid, click through to product detail pages, and add items to their cart.

## When It's Used

- Landing on the Products page (default: all products, sorted by relevance)
- Typing a search query in the search bar
- Selecting a facet filter (price range, stock status)
- Changing sort order (relevance, name, price)
- Navigating between pages of results
- Clicking a product card to view details

## Key Decisions

### URL-driven state
Search query, sort order, current page, and active facet filters all live in `useSearchParams`. This makes product listings bookmarkable and shareable — a user can send a link to "laptops sorted by price ascending, page 2" and the recipient sees the same view.

### Debounced search
Search input is debounced (300ms) to avoid hammering the OCC API on every keystroke. The debounce timer resets on each keystroke, so the API call only fires after the user pauses typing.

### OCC pagination
OCC uses 0-based page indexing (`currentPage=0` is the first page). The UI displays 1-based page numbers but converts to 0-based for API calls. Page size is fixed at 20.

### Solr-powered search
Product search hits SAP Commerce's Solr index via OCC. This means:
- Full-text search across product name, summary, and description
- Faceted filtering (price ranges, stock status)
- Sort options defined in Solr config (relevance, name-asc, name-desc, price-asc, price-desc)
- The product list may be empty if Solr hasn't been indexed

### Product detail
Product detail pages use the product `code` as the route parameter (`/products/:code`). The detail page fetches full product data including description, images, categories, and reviews via a separate OCC call with `fields=FULL`.
