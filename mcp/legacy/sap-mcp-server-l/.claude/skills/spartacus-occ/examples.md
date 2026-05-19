# Spartacus OCC Integration Examples

Focused code snippets showing correct and incorrect OCC adapter patterns in Spartacus.

---

## GOOD: Custom Adapter with Extended Endpoint

Adapter uses `OccEndpointsService` for URL construction and `ConverterService` for response normalization.

```typescript
@Injectable({ providedIn: 'root' })
export class OccWishlistAdapter implements WishlistAdapter {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService,
    protected converter: ConverterService
  ) {}

  getItems(wishlistId: string): Observable<WishlistItem[]> {
    const url = this.occEndpoints.buildUrl('wishlistItems', {
      urlParams: { wishlistId },
    });
    return this.http.get<Occ.WishlistItemList>(url).pipe(
      pluck('entries'),
      this.converter.pipeable(WISHLIST_ITEM_NORMALIZER)
    );
  }

  addItem(wishlistId: string, productCode: string): Observable<WishlistItem> {
    const url = this.occEndpoints.buildUrl('addWishlistItem', {
      urlParams: { wishlistId },
    });
    const body = this.converter.convert({ productCode } as WishlistItem, WISHLIST_ITEM_SERIALIZER);
    return this.http.post<Occ.OrderEntry>(url, body).pipe(
      this.converter.pipeable(WISHLIST_ITEM_NORMALIZER)
    );
  }
}
```

Why this is correct:
- URL built via `OccEndpointsService` — configurable, no hardcoded paths
- Response normalized via `ConverterService.pipeable()` — clean model in state
- Request serialized via `ConverterService.convert()` — correct DTO to OCC
- Typed HTTP generic (`<Occ.WishlistItemList>`) for IDE support

---

## GOOD: Normalizer with Null-Safe Transforms

```typescript
@Injectable({ providedIn: 'root' })
export class OccWishlistItemNormalizer implements Converter<Occ.OrderEntry, WishlistItem> {
  convert(source: Occ.OrderEntry, target?: WishlistItem): WishlistItem {
    return {
      ...target,
      productCode: source.product?.code ?? '',
      productName: source.product?.name ?? 'Unknown Product',
      imageUrl: source.product?.images?.find(img => img.format === 'thumbnail')?.url,
      price: source.basePrice?.value,
      formattedPrice: source.basePrice?.formattedValue ?? '',
      inStock: source.product?.stock?.stockLevelStatus === 'inStock',
      addedDate: source.updateDate ? new Date(source.updateDate) : undefined,
    };
  }
}
```

Why this is correct:
- Spreads `...target` first — preserves fields from earlier normalizers in chain
- Every OCC field access uses `?.` — OCC responses have many optional fields
- Provides sensible defaults with `??` — UI won't break on missing data
- Image lookup filters by format — OCC returns multiple image formats
- Date parsing from ISO string — OCC dates are ISO 8601 strings

---

## GOOD: Endpoint Config with Scope Control

```typescript
provideDefaultConfig(<OccConfig>{
  backend: {
    occ: {
      endpoints: {
        // Minimal data for list views
        wishlistItems: {
          default: 'users/${userId}/wishlists/${wishlistId}/entries?fields=DEFAULT&pageSize=${pageSize}',
          // Full data for detail views
          full: 'users/${userId}/wishlists/${wishlistId}/entries?fields=FULL',
        },
      },
    },
  },
});
```

Scope-specific endpoints let you request only the data needed. Use `buildUrl('wishlistItems', { scope: 'full' })` for the full version.

---

## BAD: Direct HttpClient in Component

```typescript
@Component({ selector: 'cx-wishlist', template: '...' })
export class WishlistComponent implements OnInit {
  items: any[] = [];

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get('/occ/v2/electronics/users/current/wishlists/default/entries')
      .subscribe((res: any) => {
        this.items = res.entries;
      });
  }
}
```

**What's wrong:**
- Component calls `HttpClient` directly — bypasses adapter/connector/facade pipeline
- Hardcoded OCC URL — breaks across environments and base sites
- No converter — raw OCC DTO in component state, tightly coupled to backend shape
- Manual subscribe with no cleanup — memory leak on navigation
- `any` types — no TypeScript safety
- No error handling — silent failure

**Fix:** Use the facade → connector → adapter pipeline. Component injects `WishlistService`, which dispatches to the store, which triggers an effect calling the connector.

---

## BAD: Normalizer Without Null Guards

```typescript
@Injectable({ providedIn: 'root' })
export class BadNormalizer implements Converter<Occ.OrderEntry, WishlistItem> {
  convert(source: Occ.OrderEntry): WishlistItem {
    return {
      productCode: source.product.code,           // crashes if product is null
      productName: source.product.name,            // crashes if product is null
      imageUrl: source.product.images[0].url,      // crashes if images is empty
      price: source.basePrice.value,               // crashes if basePrice is null
      formattedPrice: source.basePrice.formattedValue,
    };
  }
}
```

**What's wrong:**
- No null guards — OCC responses frequently have optional/missing nested objects
- Missing `...target` spread — breaks normalizer chaining
- No default values — UI receives `undefined` instead of a safe fallback
- Array access without bounds check — `images[0]` crashes on empty array

**Fix:** Use optional chaining (`?.`), nullish coalescing (`??`), and spread `...target`:

```typescript
convert(source: Occ.OrderEntry, target?: WishlistItem): WishlistItem {
  return {
    ...target,
    productCode: source.product?.code ?? '',
    imageUrl: source.product?.images?.[0]?.url,
  };
}
```

---

## BAD: Converter Not Registered as Multi-Provider

```typescript
// Missing multi: true
providers: [
  { provide: WISHLIST_ITEM_NORMALIZER, useClass: OccWishlistItemNormalizer },
]
```

**What's wrong:**
- Without `multi: true`, this replaces any existing normalizer on the token
- Other modules may have registered normalizers on the same token — this silently overrides them
- Breaks the normalizer chain where multiple converters process the same response

**Fix:** Always use `multi: true` for converter providers:

```typescript
{ provide: WISHLIST_ITEM_NORMALIZER, useExisting: OccWishlistItemNormalizer, multi: true }
```

---

## GENERATE OUTPUT: /spartacus-occ generate Wishlist

Running `/spartacus-occ generate Wishlist` produces these files:

### File structure
```
src/app/features/wishlist/
├── connectors/
│   ├── wishlist.adapter.ts         # Abstract adapter interface
│   └── converters.ts               # WISHLIST_NORMALIZER, WISHLIST_SERIALIZER tokens
├── occ/
│   ├── occ-wishlist.adapter.ts     # OCC adapter implementation
│   ├── occ-wishlist.normalizer.ts  # OCC DTO → WishlistItem
│   ├── occ-wishlist.config.ts      # Endpoint configuration
│   └── wishlist-occ.module.ts      # Module wiring adapter + converters
└── models/
    └── wishlist.model.ts           # WishlistItem interface
```

### occ-wishlist.config.ts

```typescript
import { OccConfig } from '@spartacus/core';

export const occWishlistConfig: OccConfig = {
  backend: {
    occ: {
      endpoints: {
        wishlist: 'users/${userId}/wishlists/${wishlistId}?fields=FULL',
        wishlistItems: 'users/${userId}/wishlists/${wishlistId}/entries?fields=FULL',
        addWishlistItem: 'users/${userId}/wishlists/${wishlistId}/entries',
        removeWishlistItem: 'users/${userId}/wishlists/${wishlistId}/entries/${entryNumber}',
      },
    },
  },
};
```

### wishlist-occ.module.ts

```typescript
@NgModule({
  providers: [
    provideDefaultConfig(occWishlistConfig),
    { provide: WishlistAdapter, useClass: OccWishlistAdapter },
    { provide: WISHLIST_ITEM_NORMALIZER, useExisting: OccWishlistNormalizer, multi: true },
    { provide: WISHLIST_ITEM_SERIALIZER, useExisting: OccWishlistSerializer, multi: true },
  ],
})
export class WishlistOccModule {}
```

Each generated file includes necessary imports, proper typing, and follows the patterns documented in [patterns.md](patterns.md).
