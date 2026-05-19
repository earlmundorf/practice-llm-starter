# Product Browse — Components

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/pages/Products.tsx` | Page component — search bar with visual search, sort dropdown, facet sidebar, active filter tags, product grid/list, pagination, visual search result overlay |
| `src/pages/ProductDetail.tsx` | Product detail page — full product info, quantity selector, add to cart |
| `src/components/ProductCard.tsx` | Product card — name, description, price, stock badge, quantity input, add to cart button |
| `src/components/FacetSidebar.tsx` | Facet filter panel — collapsible groups for price ranges and availability, responsive mobile drawer |
| `src/components/ActiveFacetTags.tsx` | Tags showing active filters as removable pill buttons, with clear-all option |
| `src/components/Toast.tsx` | Transient notification for add-to-cart success/error feedback |
| `src/services/api.ts` | `api.searchProducts()`, `api.getProduct()`, `api.visualSearch()`, `cartUtils.addToCart()` |
| `src/types/index.ts` | `Product`, `SearchResult`, `Facet`, `FacetValue`, `VisualSearchResult`, `MappedVisualSearchMatch` types |

## How They Connect

```
Products.tsx
├── reads q/sort/page/facets from useSearchParams
├── calls api.searchProducts() on param change (useEffect)
├── debounces search input (300ms) before writing to URL
├── renders ActiveFacetTags (removable filter pills)
├── renders FacetSidebar (checkbox filters, mobile drawer)
├── renders ProductCard[] in grid or list mode (viewMode toggle)
├── renders Pagination (ellipsis algorithm, prev/next)
├── handles visual search:
│   ├── camera icon → file input → handleVisualFile()
│   ├── paste event → clipboard image detection → handleVisualFile()
│   ├── calls api.visualSearch(base64, mimeType)
│   └── renders visual result overlay with match badges + ProductCard[]
└── handles add-to-cart via cartUtils (stock validation, toast feedback)

ProductDetail.tsx
├── reads :productId from useParams
├── calls api.getProduct(productId) on mount
├── renders product image, name, price, stock status, description
├── quantity selector with stock-bounded input
└── add-to-cart with stock validation and toast feedback
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/products/search?query={q}:{sort}:{facets}&currentPage={p}&pageSize=12&fields=FULL` | Paginated search with Solr facets |
| GET | `/products/{code}?fields=code,name,description,summary,price(FULL),stock(FULL),images(FULL)` | Product detail |
| POST | `/agent/visual-search` | AI-powered image search (authenticated, base64 body) |
| POST | `/users/current/carts/{cartCode}/entries` | Add to cart (from ProductCard or ProductDetail) |

## Facet Serialization

Facets are stored in the URL as a single `facets` query param with pipe-delimited entries:

```
?facets=priceValue:$500-$999.99|inStockFlag:true
```

`parseFacetParams` converts this to `Record<string, string[]>` on read; `serializeFacetParams` converts back on write. Changing any facet resets the page to 0.
