# Product Browse — Components

> **Note:** Page and component files listed below are planned — create them following the patterns in CLAUDE.md. The `api.ts` service and `types/index.ts` already exist with the methods and types referenced here.

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/pages/Products.tsx` | Page component — search bar, facet sidebar, product grid, pagination |
| `src/pages/ProductDetail.tsx` | Product detail page — full product info, add to cart |
| `src/components/ProductCard.tsx` | Product card — image, name, price, stock status, add to cart button |
| `src/components/FacetSidebar.tsx` | Facet filter panel — price ranges, stock status checkboxes |
| `src/components/ActiveFacetTags.tsx` | Tags showing active filters with remove buttons |
| `src/services/api.ts` | `api.searchProducts()` and `api.getProduct()` methods |
| `src/types/index.ts` | `Product`, `ProductSearchResult`, `Facet`, `Pagination` types |

## How They Connect

```
Products.tsx
├── reads search/sort/page/facets from useSearchParams
├── calls api.searchProducts() on param change
├── renders FacetSidebar (filter selection)
├── renders ActiveFacetTags (active filter display)
├── renders ProductCard[] (product grid)
└── renders Pagination (page navigation)

ProductDetail.tsx
├── reads :code from useParams
├── calls api.getProduct(code) on mount
└── renders full product info with add-to-cart
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/products/search?query={q}:{sort}&currentPage={p}&pageSize=20&fields=FULL` | Paginated search |
| GET | `/products/{code}?fields=FULL` | Product detail |
