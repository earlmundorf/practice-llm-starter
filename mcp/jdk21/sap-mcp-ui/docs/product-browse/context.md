# Product Browse — Context

## What This Flow Does

Allows users to search, filter, sort, and paginate through the product catalog. Users can view product cards in a grid or list layout, click through to product detail pages, add items to their cart, and use visual search to find products by uploading or pasting an image.

## When It's Used

- Landing on the Products page (default: all products, sorted by relevance)
- Typing a search query in the search bar
- Pasting an image into the search bar or clicking the camera icon to upload one
- Selecting a facet filter (price range, availability)
- Changing sort order (relevance, name, price)
- Navigating between pages of results
- Toggling between grid and list view
- Clicking a product card to view details

## Key Decisions

### URL-driven state
Search query, sort order, current page, and active facet filters all live in `useSearchParams`. This makes product listings bookmarkable and shareable — a user can send a link to "laptops sorted by price ascending, page 2, filtered to in-stock" and the recipient sees the same view. Facets are serialized into a single `facets` query param using pipe-delimited `code:value` pairs (e.g., `facets=priceValue:$500-$999.99|inStockFlag:true`).

### Debounced search
Search input is debounced (300ms) to avoid hammering the OCC API on every keystroke. The debounce timer resets on each keystroke, so the API call only fires after the user pauses typing.

### OCC pagination
OCC uses 0-based page indexing (`currentPage=0` is the first page). The UI displays 1-based page numbers but converts to 0-based for API calls. Page size is fixed at 12. The pagination control uses an ellipsis algorithm — when total pages exceed 7, it shows the first page, last page, and a window of 3 pages around the current page with ellipsis gaps.

### Solr-powered search
Product search hits SAP Commerce's Solr index via OCC. This means:
- Full-text search across product name, summary, and description
- Faceted filtering (price ranges, stock availability)
- Sort options defined in Solr config (relevance, name-asc, name-desc, price-asc, price-desc)
- The product list may be empty if Solr hasn't been indexed
- Facet query syntax follows the OCC colon-delimited pattern: `searchTerm:sort:facetCode:value:facetCode:value`

### Visual search in the search bar
Visual search is integrated directly into the search bar via two entry points: a camera icon button (file picker) and paste detection (clipboard images). When an image is provided, it is base64-encoded and sent to `/agent/visual-search`, which uses AI vision to analyze the image and return matching catalog products. Results appear as an overlay above the normal product grid, with match-type badges (Best Match, Similar, You Might Like) and confidence percentages. The normal Solr-powered results are hidden while visual search results are displayed. Visual search requires authentication.

### Grid/list view toggle
Users can switch between a grid layout (3 columns on desktop, responsive) and a list layout (single-column rows with horizontal product info). The toggle is local component state — it is not persisted in the URL or localStorage.

### Product detail
Product detail pages use the product `code` as the route parameter (`/products/:productId`). The detail page fetches full product data including description, images, stock level, and price via a separate OCC call with specific field selectors. The detail page supports quantity selection and add-to-cart with stock validation.

### Add-to-cart from anywhere
Both the product card (in grid view) and the product detail page support add-to-cart with quantity selection. The cart is managed server-side via OCC cart entries. Stock validation happens client-side (checking existing cart quantity + requested quantity against stock level) before the API call. A `cartItemAdded` custom event is dispatched so the header cart badge can update.
