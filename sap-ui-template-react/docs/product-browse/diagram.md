# Product Browse — Diagrams

## Search and Display Flow

When the user searches or changes filters, the Products page reads state from URL params, calls the OCC API, and renders the results.

```mermaid
sequenceDiagram
    participant User
    participant Products as Products.tsx
    participant URL as useSearchParams
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    User->>Products: types search query
    Products->>Products: debounce 300ms
    Products->>URL: setSearchParams({ q, sort, page: 0 })
    URL-->>Products: params change triggers useEffect
    Products->>API: searchProducts({ query, sort, page })
    API->>OCC: GET /products/search?query=laptop:relevance&currentPage=0
    OCC-->>API: ProductSearchPageWsDTO
    API-->>Products: { products[], pagination, facets[] }
    Products->>Products: render ProductCard[], Pagination, FacetSidebar
```

## Product Detail Flow

When the user clicks a product card, React Router navigates to the detail page which fetches full product data.

```mermaid
sequenceDiagram
    participant User
    participant Card as ProductCard
    participant Router as React Router
    participant Detail as ProductDetail.tsx
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    User->>Card: clicks product
    Card->>Router: <Link to="/products/LAPTOP_PRO_15">
    Router->>Detail: renders with code param
    Detail->>API: getProduct("LAPTOP_PRO_15")
    API->>OCC: GET /products/LAPTOP_PRO_15?fields=FULL
    OCC-->>API: ProductWsDTO (full)
    API-->>Detail: Product with description, images, categories
    Detail->>Detail: render product detail + add to cart
```

## Component Layout

```mermaid
graph TD
    Products[Products Page]
    Products --> SearchBar[Search Bar<br/>debounced input]
    Products --> Facets[FacetSidebar<br/>price, stock filters]
    Products --> Tags[ActiveFacetTags<br/>removable filter chips]
    Products --> Grid[Product Grid]
    Products --> Pagination[Pagination<br/>page navigation]
    Grid --> Card1[ProductCard]
    Grid --> Card2[ProductCard]
    Grid --> CardN[ProductCard ...]
```
