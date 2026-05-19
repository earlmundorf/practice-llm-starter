# Spartacus Component Patterns

Reference snippets for CMS component development in Spartacus 6.x with NgModules.

---

## CMS Component Data Injection

Components receive their CMS model data via `CmsComponentData<T>`. This is the primary way CMS-mapped components get their configuration from the CMS backend.

```typescript
import { Component } from '@angular/core';
import { CmsComponentData } from '@spartacus/storefront';
import { CmsBannerComponent } from '@spartacus/core';
import { Observable } from 'rxjs';

@Component({
  selector: 'cx-custom-banner',
  templateUrl: './custom-banner.component.html',
})
export class CustomBannerComponent {
  data$: Observable<CmsBannerComponent> = this.componentData.data$;

  constructor(protected componentData: CmsComponentData<CmsBannerComponent>) {}
}
```

Template usage — always use async pipe with a null guard:

```html
@if (data$ | async; as data) {
  <div class="cx-banner">
    <h2>{{ data.headline }}</h2>
    <p>{{ data.content }}</p>
    @if (data.urlLink) {
      <a [routerLink]="data.urlLink">{{ data.urlLink }}</a>
    }
  </div>
}
```

---

## CMS Component Mapping via provideDefaultConfig

Map your Angular component to a CMS component type. This tells Spartacus which Angular component to render for a given CMS component type from the backend.

```typescript
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { provideDefaultConfig, CmsConfig } from '@spartacus/core';
import { CustomBannerComponent } from './custom-banner.component';

@NgModule({
  declarations: [CustomBannerComponent],
  imports: [CommonModule],
  providers: [
    provideDefaultConfig(<CmsConfig>{
      cmsComponents: {
        CustomBannerComponent: {
          component: CustomBannerComponent,
        },
      },
    }),
  ],
})
export class CustomBannerModule {}
```

The key `CustomBannerComponent` in `cmsComponents` must match the component type ID in SAP Commerce Backoffice exactly.

---

## Feature Module with Lazy Loading

Feature modules should be self-contained and lazy-loadable. Spartacus lazy-loads feature modules when the CMS page containing their mapped components is requested.

```typescript
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nModule, provideDefaultConfig, CmsConfig } from '@spartacus/core';
import { WishlistButtonComponent } from './wishlist-button.component';

@NgModule({
  declarations: [WishlistButtonComponent],
  imports: [
    CommonModule,
    I18nModule,    // for cxTranslate pipe
  ],
  providers: [
    provideDefaultConfig(<CmsConfig>{
      cmsComponents: {
        WishlistButtonComponent: {
          component: WishlistButtonComponent,
        },
      },
    }),
  ],
})
export class WishlistButtonModule {}
```

Register the feature module for lazy loading in your app configuration:

```typescript
provideDefaultConfig({
  featureModules: {
    wishlistButton: {
      module: () => import('./features/wishlist-button/wishlist-button.module').then(m => m.WishlistButtonModule),
      cmsComponents: ['WishlistButtonComponent'],
    },
  },
});
```

---

## Outlet Injection

Outlets let you inject content into existing Spartacus layout slots without modifying the page template. Use `cxOutletRef` to target a specific outlet position.

```html
<!-- Inject content before the product summary -->
<ng-template cxOutletRef="ProductSummarySlot" cxOutletPos="before">
  <cx-custom-promo-banner></cx-custom-promo-banner>
</ng-template>
```

Outlet positions: `before`, `after`, `replace`. Prefer `before`/`after` over `replace` — replacing removes the original content entirely.

For programmatic outlet registration:

```typescript
import { OutletPosition, provideOutlet } from '@spartacus/storefront';

@NgModule({
  providers: [
    provideOutlet({
      id: 'ProductSummarySlot',
      position: OutletPosition.BEFORE,
      component: CustomPromoBannerComponent,
    }),
  ],
})
export class CustomPromoBannerModule {}
```

---

## Slot and Page Template Configuration

Define custom page templates and slots when the default Spartacus layout doesn't fit your design.

```typescript
provideDefaultConfig(<LayoutConfig>{
  layoutSlots: {
    CustomLandingPageTemplate: {
      slots: ['Section1', 'Section2A', 'Section2B', 'Section3'],
      lg: {
        slots: ['Section1', ['Section2A', 'Section2B'], 'Section3'],
      },
    },
  },
});
```

The `lg` key defines a responsive layout variant — slots in a nested array render side-by-side at that breakpoint.

---

## Smart vs Presentational Component Split

Smart (container) components handle data and state. Presentational (dumb) components receive data via `@Input()` and emit events via `@Output()`.

**Smart component** — fetches data, delegates display:

```typescript
@Component({
  selector: 'cx-product-reviews-container',
  template: `
    @if (reviews$ | async; as reviews) {
      <cx-review-list
        [reviews]="reviews"
        (deleteReview)="onDelete($event)">
      </cx-review-list>
    }
  `,
})
export class ProductReviewsContainerComponent {
  reviews$ = this.reviewService.getReviews(this.productCode);

  constructor(
    protected reviewService: ProductReviewService,
    protected currentProduct: CurrentProductService
  ) {}

  get productCode(): string {
    // resolved from CurrentProductService
  }

  onDelete(reviewId: string): void {
    this.reviewService.deleteReview(reviewId);
  }
}
```

**Presentational component** — pure display, no service injection:

```typescript
@Component({
  selector: 'cx-review-list',
  templateUrl: './review-list.component.html',
})
export class ReviewListComponent {
  @Input() reviews: Review[] = [];
  @Output() deleteReview = new EventEmitter<string>();
}
```

---

## Guard Pattern for Route-Level Data

Use `CmsPageGuard` for CMS-driven pages. For custom guards, follow the Spartacus pattern of returning `Observable<boolean | UrlTree>`.

```typescript
import { Injectable } from '@angular/core';
import { CanActivate, UrlTree, Router } from '@angular/router';
import { AuthService } from '@spartacus/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class WishlistGuard implements CanActivate {
  constructor(
    protected authService: AuthService,
    protected router: Router
  ) {}

  canActivate(): Observable<boolean | UrlTree> {
    return this.authService.isUserLoggedIn().pipe(
      map(loggedIn => loggedIn || this.router.parseUrl('/login'))
    );
  }
}
```

---

## ConfigModule.withConfig for Component Configuration

Provide default configuration values that components can read at runtime. This decouples component behavior from hardcoded values.

```typescript
provideDefaultConfig({
  wishlist: {
    maxItems: 50,
    enableSharing: true,
  },
} as WishlistConfig);
```

Inject and use in component:

```typescript
@Injectable({ providedIn: 'root' })
export class WishlistConfig {
  wishlist?: {
    maxItems?: number;
    enableSharing?: boolean;
  };
}

@Component({ /* ... */ })
export class WishlistComponent {
  constructor(protected config: WishlistConfig) {}

  get maxItems(): number {
    return this.config.wishlist?.maxItems ?? 50;
  }
}
```
