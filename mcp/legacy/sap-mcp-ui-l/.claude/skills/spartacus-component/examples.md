# Spartacus Component Examples

Focused code snippets showing correct and incorrect patterns for CMS component development.

---

## GOOD: CMS-Mapped Component with Typed Data

Component correctly injects `CmsComponentData`, exposes an observable, and uses async pipe in the template.

```typescript
// custom-banner.component.ts
@Component({
  selector: 'cx-custom-banner',
  templateUrl: './custom-banner.component.html',
  styleUrls: ['./custom-banner.component.scss'],
})
export class CustomBannerComponent {
  data$: Observable<CmsBannerComponent> = this.componentData.data$;

  constructor(protected componentData: CmsComponentData<CmsBannerComponent>) {}
}
```

```html
<!-- custom-banner.component.html -->
@if (data$ | async; as data) {
  <div class="cx-banner-content">
    <h2>{{ data.headline | cxTranslate }}</h2>
    @if (data.media) {
      <cx-media [container]="data.media"></cx-media>
    }
    @if (data.urlLink) {
      <a [routerLink]="data.urlLink" class="btn btn-primary">
        {{ 'customBanner.cta' | cxTranslate }}
      </a>
    }
  </div>
}
```

Why this is correct:
- Data flows from CMS via `CmsComponentData` — no direct service calls
- Template guards against null with `@if (data$ | async; as data)`
- User-visible strings use `cxTranslate` for i18n
- Media rendered via Spartacus `cx-media` component (handles responsive images)

---

## GOOD: Feature Module with Lazy CMS Config

Module is self-contained, provides its own CMS mapping, and imports only what it needs.

```typescript
// custom-banner.module.ts
@NgModule({
  declarations: [CustomBannerComponent],
  imports: [
    CommonModule,
    I18nModule,
    RouterModule,
    MediaModule,
  ],
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

Why this is correct:
- CMS mapping is in the feature module, not `AppModule`
- Only imports modules it actually uses
- Module is lazy-loadable — no circular dependencies or eager imports

---

## BAD: Component Calling Service Directly for CMS Data

```typescript
// bad-banner.component.ts
@Component({ selector: 'cx-bad-banner', templateUrl: './bad-banner.component.html' })
export class BadBannerComponent implements OnInit {
  data: any;

  constructor(private cmsService: CmsService) {}

  ngOnInit(): void {
    this.cmsService.getComponentData('CustomBannerComponent').subscribe(data => {
      this.data = data;  // manual subscribe, untyped, no unsubscribe
    });
  }
}
```

**What's wrong:**
- Bypasses `CmsComponentData` injection — loses automatic component-level data binding
- Manual `.subscribe()` without cleanup (memory leak on navigation)
- `any` type loses TypeScript safety
- Hardcoded component UID instead of receiving it from the CMS slot context

**Fix:** Inject `CmsComponentData<CmsBannerComponent>` and use `data$` observable with async pipe.

---

## BAD: CMS Mapping in AppModule

```typescript
// app.module.ts — DON'T do this
@NgModule({
  imports: [
    BrowserModule,
    SpartacusModule,
    CustomBannerModule,  // imported here — defeats lazy loading
  ],
  providers: [
    provideDefaultConfig(<CmsConfig>{
      cmsComponents: {
        CustomBannerComponent: {
          component: CustomBannerComponent,  // mapping in wrong module
        },
      },
    }),
  ],
})
export class AppModule {}
```

**What's wrong:**
- CMS mapping belongs in the feature module (`CustomBannerModule`), not in `AppModule`
- Eagerly importing `CustomBannerModule` defeats Spartacus lazy loading
- Centralizing all CMS mappings in `AppModule` creates a maintenance bottleneck

**Fix:** Move the `provideDefaultConfig` into `CustomBannerModule.providers` and configure lazy loading via `featureModules`.

---

## BAD: Direct DOM Manipulation

```typescript
@Component({ selector: 'cx-bad-tabs', template: '<div id="tabs"></div>' })
export class BadTabsComponent implements AfterViewInit {
  ngAfterViewInit(): void {
    const el = document.getElementById('tabs');
    el!.innerHTML = '<div class="tab active">Tab 1</div>';  // direct DOM mutation
    el!.addEventListener('click', () => { /* ... */ });       // unmanaged listener
  }
}
```

**What's wrong:**
- `document.getElementById` bypasses Angular change detection and SSR compatibility
- `innerHTML` is an XSS risk and breaks Angular template binding
- Event listener is never cleaned up

**Fix:** Use `@ViewChild` with `ElementRef` or `Renderer2`, or better yet, express the UI entirely in the Angular template with `@for` and event bindings.

---

## GENERATE OUTPUT: /spartacus-component generate WishlistButton

Running `/spartacus-component generate WishlistButton` produces these files:

### wishlist-button.component.ts

```typescript
import { Component } from '@angular/core';
import { CmsComponentData } from '@spartacus/storefront';
import { CmsComponent } from '@spartacus/core';
import { Observable } from 'rxjs';

export interface CmsWishlistButtonComponent extends CmsComponent {
  productCode?: string;
  label?: string;
}

@Component({
  selector: 'cx-wishlist-button',
  templateUrl: './wishlist-button.component.html',
  styleUrls: ['./wishlist-button.component.scss'],
})
export class WishlistButtonComponent {
  data$: Observable<CmsWishlistButtonComponent> = this.componentData.data$;

  constructor(
    protected componentData: CmsComponentData<CmsWishlistButtonComponent>
  ) {}

  onAddToWishlist(productCode: string): void {
    // TODO: implement via WishlistService facade
  }
}
```

### wishlist-button.component.html

```html
@if (data$ | async; as data) {
  <button
    class="btn btn-secondary"
    (click)="onAddToWishlist(data.productCode ?? '')"
    [attr.aria-label]="'wishlistButton.addAriaLabel' | cxTranslate">
    <cx-icon [type]="'HEART'"></cx-icon>
    {{ data.label ?? ('wishlistButton.add' | cxTranslate) }}
  </button>
}
```

### wishlist-button.component.scss

```scss
%cx-wishlist-button {
  :host {
    display: inline-block;
  }

  button {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
  }
}
```

### wishlist-button.module.ts

```typescript
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nModule, provideDefaultConfig, CmsConfig } from '@spartacus/core';
import { IconModule } from '@spartacus/storefront';
import { WishlistButtonComponent } from './wishlist-button.component';

@NgModule({
  declarations: [WishlistButtonComponent],
  imports: [CommonModule, I18nModule, IconModule],
  providers: [
    provideDefaultConfig(<CmsConfig>{
      cmsComponents: {
        CmsWishlistButtonComponent: {
          component: WishlistButtonComponent,
        },
      },
    }),
  ],
})
export class WishlistButtonModule {}
```

### wishlist-button.component.spec.ts

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CmsComponentData } from '@spartacus/storefront';
import { I18nTestingModule } from '@spartacus/core';
import { of } from 'rxjs';
import { WishlistButtonComponent, CmsWishlistButtonComponent } from './wishlist-button.component';

describe('WishlistButtonComponent', () => {
  let component: WishlistButtonComponent;
  let fixture: ComponentFixture<WishlistButtonComponent>;

  const mockData: CmsWishlistButtonComponent = {
    uid: 'test',
    typeCode: 'CmsWishlistButtonComponent',
    productCode: '12345',
    label: 'Add to Wishlist',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [WishlistButtonComponent],
      imports: [I18nTestingModule],
      providers: [
        { provide: CmsComponentData, useValue: { data$: of(mockData) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WishlistButtonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render button with label', () => {
    const button = fixture.nativeElement.querySelector('button');
    expect(button.textContent).toContain('Add to Wishlist');
  });
});
```
