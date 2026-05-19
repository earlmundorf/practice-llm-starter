# Product Browse — Diagrams

## Search and Display Flow

When the user searches or changes filters, the Products page reads state from URL params, calls the OCC API, and renders the results. Search input is debounced so the API call fires only after 300ms of inactivity.

```mermaid
sequenceDiagram
    participant User
    participant Products as Products.tsx
    participant URL as useSearchParams
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    User->>Products: types search query
    Products->>Products: debounce 300ms
    Products->>URL: setSearchParams({ q, page: 0 })
    URL-->>Products: params change triggers useEffect
    Products->>API: searchProducts(query, sort, page, 12, facetFilters)
    API->>OCC: GET /products/search?query=laptop:relevance&currentPage=0&pageSize=12&fields=FULL
    OCC-->>API: ProductSearchPageWsDTO
    API-->>Products: { products[], pagination, facets[] }
    Products->>Products: render ProductCard[], Pagination, FacetSidebar
```

## Visual Search Flow

When the user uploads an image (camera icon) or pastes one into the search bar, the image is base64-encoded and sent to the backend AI vision endpoint. Results overlay the normal product grid with match-type badges and confidence scores.

```mermaid
sequenceDiagram
    participant User
    participant Products as Products.tsx
    participant API as api.ts
    participant OCC as SAP Commerce OCC
    participant AI as AI Vision (backend)

    alt Camera icon click
        User->>Products: clicks camera icon
        Products->>Products: opens file picker
        User->>Products: selects image file
    else Paste image
        User->>Products: pastes image into search bar
        Products->>Products: detects image in clipboard
    end

    Products->>Products: validate file type & size
    Products->>Products: FileReader.readAsDataURL → base64
    Products->>Products: show image preview + loading spinner
    Products->>API: visualSearch(base64, mimeType)
    API->>OCC: POST /agent/visual-search { image, mimeType }
    OCC->>AI: analyze image, search catalog
    AI-->>OCC: { visionAnalysis, aiDetail, products[] }
    OCC-->>API: VisualSearchResult
    API->>API: mapOccProduct() for each match
    API-->>Products: { visionAnalysis, mappedProducts[], aiDetail }
    Products->>Products: hide normal grid, show overlay
    Products->>Products: render AI analysis banner
    Products->>Products: render ProductCard[] with match badges (Best Match / Similar / Explore)

    User->>Products: clicks "Clear"
    Products->>Products: clear visual results, show normal grid
```

## Facet Interaction Flow

When the user toggles a facet checkbox, the URL is updated with serialized facet state, which triggers a new search with the facet values appended to the OCC query string.

```mermaid
sequenceDiagram
    participant User
    participant Sidebar as FacetSidebar
    participant Products as Products.tsx
    participant URL as useSearchParams
    participant API as api.ts
    participant OCC as SAP Commerce OCC

    User->>Sidebar: checks "In Stock" under Availability
    Sidebar->>Products: onToggle("inStockFlag", "true")
    Products->>Products: toggleFacet() — update activeFacets map
    Products->>URL: setSearchParams({ facets: "inStockFlag:true", page: 0 })
    URL-->>Products: params change triggers useEffect
    Products->>API: searchProducts("", "relevance", 0, 12, { inStockFlag: ["true"] })
    API->>OCC: GET /products/search?query=:relevance:inStockFlag:true&...
    OCC-->>API: filtered results + updated facet counts
    API-->>Products: { products[], pagination, facets[] }
    Products->>Products: re-render grid, sidebar (updated counts), ActiveFacetTags
```

## Component Layout

```mermaid
graph TD
    Products[Products Page]
    Products --> Header[Header<br/>title + grid/list toggle]
    Products --> SearchSort[Search Bar + Sort<br/>debounced input, camera icon, paste detection, sort dropdown]
    Products --> Tags[ActiveFacetTags<br/>removable filter pills + clear all]
    Products --> VisualOverlay[Visual Search Overlay<br/>AI analysis banner + matched ProductCards with badges]
    Products --> MainContent[Main Content Area]
    MainContent --> Facets[FacetSidebar<br/>price, availability filters<br/>mobile: slide-out drawer<br/>desktop: sticky sidebar]
    MainContent --> ResultArea[Result Area]
    ResultArea --> Grid[Product Grid / List]
    ResultArea --> Pagination[Pagination<br/>ellipsis algorithm, prev/next]
    Grid --> Card1[ProductCard<br/>name, price, stock, qty, add to cart]
    Grid --> Card2[ProductCard]
    Grid --> CardN[ProductCard ...]

    Detail[ProductDetail Page]
    Detail --> BackLink[Back to Products link]
    Detail --> Image[Product Image]
    Detail --> Info[Product Info<br/>code, name, price, stock badge]
    Detail --> QtyCart[Quantity Selector + Add to Cart]
    Detail --> Desc[Description Section]
```
