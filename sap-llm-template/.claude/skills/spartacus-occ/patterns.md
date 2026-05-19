# Spartacus OCC Integration Patterns

Reference snippets for OCC adapter customization in Spartacus 6.x.

---

## Endpoint Configuration

Define OCC endpoints via `provideDefaultConfig`. Use `${param}` for path parameters.

```typescript
import { provideDefaultConfig, OccConfig } from '@spartacus/core';

provideDefaultConfig(<OccConfig>{
  backend: {
    occ: {
      endpoints: {
        wishlist: 'users/${userId}/wishlists/${wishlistId}?fields=FULL',
        wishlistItems: 'users/${userId}/wishlists/${wishlistId}/entries?fields=FULL&currentPage=${currentPage}&pageSize=${pageSize}',
        addWishlistItem: 'users/${userId}/wishlists/${wishlistId}/entries',
      },
    },
  },
});
```

Path params (`${userId}`, `${wishlistId}`) are resolved at runtime by `OccEndpointsService.buildUrl()`. Spartacus auto-resolves `${userId}` to `current` for authenticated users.

---

## OccEndpointsService — URL Construction

Never hardcode OCC URLs. Use `OccEndpointsService.buildUrl()` with the endpoint key and params.

```typescript
import { OccEndpointsService } from '@spartacus/core';

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
      queryParams: { currentPage: 0, pageSize: 50 },
    });
    return this.http.get(url).pipe(
      this.converter.pipeable(WISHLIST_ITEM_NORMALIZER)
    );
  }
}
```

`buildUrl` accepts:
- `urlParams`: replaces `${param}` in the endpoint template
- `queryParams`: appended as `?key=value` query string (merges with template query params)

---

## Custom Adapter Extending OCC Base

When extending an existing Spartacus OCC adapter, override only the methods you need.

```typescript
import { OccProductAdapter } from '@spartacus/core';

@Injectable()
export class CustomOccProductAdapter extends OccProductAdapter {
  // Override to add custom query params
  load(productCode: string, scope?: string): Observable<Product> {
    const url = this.occEndpoints.buildUrl('product', {
      urlParams: { productCode },
      queryParams: { fields: 'FULL', reviews: 'true' },
    });
    return this.http.get(url).pipe(
      this.converter.pipeable(PRODUCT_NORMALIZER)
    );
  }
}
```

Register the override in a module:

```typescript
providers: [
  { provide: ProductAdapter, useClass: CustomOccProductAdapter },
]
```

---

## Converter Token Declaration

Converter tokens are `InjectionToken`s. Normalizers and serializers register as multi-providers against these tokens.

```typescript
import { InjectionToken } from '@angular/core';
import { Converter } from '@spartacus/core';
import { WishlistItem } from '../models/wishlist.model';

export const WISHLIST_ITEM_NORMALIZER = new InjectionToken<Converter<any, WishlistItem>>(
  'WishlistItemNormalizer'
);

export const WISHLIST_ITEM_SERIALIZER = new InjectionToken<Converter<WishlistItem, any>>(
  'WishlistItemSerializer'
);
```

---

## Normalizer — OCC DTO to Spartacus Model

Normalizers transform OCC response DTOs into Spartacus models. They implement `Converter<Source, Target>`.

```typescript
import { Injectable } from '@angular/core';
import { Converter, Occ } from '@spartacus/core';
import { WishlistItem } from '../models/wishlist.model';

@Injectable({ providedIn: 'root' })
export class OccWishlistItemNormalizer implements Converter<Occ.OrderEntry, WishlistItem> {
  convert(source: Occ.OrderEntry, target?: WishlistItem): WishlistItem {
    return {
      ...target,
      productCode: source.product?.code ?? '',
      productName: source.product?.name ?? '',
      addedDate: source.updateDate ? new Date(source.updateDate) : undefined,
      imageUrl: source.product?.images?.[0]?.url,
      price: source.basePrice?.value,
      formattedPrice: source.basePrice?.formattedValue,
      inStock: source.product?.stock?.stockLevelStatus === 'inStock',
    };
  }
}
```

Key points:
- Always spread `...target` first — preserves fields from earlier normalizers in the chain
- Use nullish coalescing (`??`) and optional chaining (`?.`) for OCC fields that may be absent
- Pure transformation — no service calls, no side effects

---

## Serializer — Spartacus Model to OCC DTO

Serializers transform Spartacus models into OCC DTOs for write operations.

```typescript
import { Injectable } from '@angular/core';
import { Converter } from '@spartacus/core';
import { WishlistItem } from '../models/wishlist.model';

@Injectable({ providedIn: 'root' })
export class OccWishlistItemSerializer implements Converter<WishlistItem, any> {
  convert(source: WishlistItem): any {
    return {
      product: { code: source.productCode },
      quantity: 1,
    };
  }
}
```

Only include fields the OCC endpoint expects. Omit `undefined` fields rather than sending `null`.

---

## ConverterService Usage in Adapters

`ConverterService` applies normalizers/serializers. Two methods:

**For responses (normalizing):** Use `.pipeable()` in an RxJS pipe:

```typescript
getItem(id: string): Observable<WishlistItem> {
  return this.http.get(url).pipe(
    this.converter.pipeable(WISHLIST_ITEM_NORMALIZER)
  );
}
```

**For requests (serializing):** Use `.convert()` before sending:

```typescript
addItem(item: WishlistItem): Observable<WishlistItem> {
  const body = this.converter.convert(item, WISHLIST_ITEM_SERIALIZER);
  return this.http.post(url, body).pipe(
    this.converter.pipeable(WISHLIST_ITEM_NORMALIZER)
  );
}
```

---

## Multi-Provider Registration

Converters are registered as `multi: true` providers — multiple normalizers can chain on the same token.

```typescript
@NgModule({
  providers: [
    // Bind abstract adapter to OCC implementation
    { provide: WishlistAdapter, useClass: OccWishlistAdapter },

    // Register normalizer as multi-provider
    {
      provide: WISHLIST_ITEM_NORMALIZER,
      useExisting: OccWishlistItemNormalizer,
      multi: true,
    },

    // Register serializer as multi-provider
    {
      provide: WISHLIST_ITEM_SERIALIZER,
      useExisting: OccWishlistItemSerializer,
      multi: true,
    },
  ],
})
export class WishlistOccModule {}
```

Use `useExisting` (not `useClass`) when the normalizer is `providedIn: 'root'` — avoids creating duplicate instances.

---

## Interceptor Pattern

Interceptors handle cross-cutting concerns for OCC requests. Check `isOccUrl()` before modifying.

```typescript
import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { OccEndpointsService } from '@spartacus/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CustomOccInterceptor implements HttpInterceptor {
  constructor(protected occEndpoints: OccEndpointsService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.occEndpoints.isOccUrl(req.url)) {
      return next.handle(req);
    }

    const modified = req.clone({
      setHeaders: { 'X-Custom-Header': 'value' },
    });
    return next.handle(modified);
  }
}
```

---

## OCC DTO Typing

Use the `Occ` namespace from `@spartacus/core` for OCC response types.

```typescript
import { Occ } from '@spartacus/core';

// Occ.Product, Occ.Cart, Occ.Order, Occ.OrderEntry, etc.
// These match the SAP Commerce OCC REST API response shapes

function normalizeProduct(source: Occ.Product): Product {
  return {
    code: source.code ?? '',
    name: source.name ?? '',
    price: source.price?.value ?? 0,
    formattedPrice: source.price?.formattedValue ?? '',
    inStock: source.stock?.stockLevelStatus === 'inStock',
  };
}
```

For custom OCC DTOs not covered by the `Occ` namespace, declare your own interface in a `models/` directory.
