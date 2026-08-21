# Spartacus Routing Patterns

Reference snippets for CMS-driven routing in Spartacus 6.x with NgModules.

---

## CMS Page Route Configuration

Routes for CMS-driven pages use `CmsPageGuard` to resolve the page layout and slots, and `PageLayoutComponent` as the routed component. Configurable routes are defined via `provideDefaultConfig`.

```typescript
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CmsPageGuard, PageLayoutComponent } from '@spartacus/storefront';
import { provideDefaultConfig, RoutingConfig } from '@spartacus/core';

const routes: Routes = [
  {
    path: null, // path comes from configurable routes
    canActivate: [CmsPageGuard],
    component: PageLayoutComponent,
    data: { cxRoute: 'loyaltyRewards' },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  providers: [
    provideDefaultConfig(<RoutingConfig>{
      routing: {
        routes: {
          loyaltyRewards: {
            paths: ['loyalty-rewards', 'my-rewards'],
          },
        },
      },
    }),
  ],
})
export class LoyaltyRewardsRoutingModule {}
```

The `cxRoute` in route `data` links the Angular route to the configurable route name. The `paths` array supports multiple URL aliases for the same logical route.

---

## SemanticPathService Usage

Use `SemanticPathService` for programmatic navigation instead of hardcoded URLs. This ensures navigation respects configurable route paths.

```typescript
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { SemanticPathService } from '@spartacus/core';

@Injectable({ providedIn: 'root' })
export class LoyaltyNavigationService {
  constructor(
    protected router: Router,
    protected semanticPathService: SemanticPathService
  ) {}

  goToRewardsDetail(rewardId: string): void {
    this.router.navigate(
      this.semanticPathService.transform({
        cxRoute: 'rewardDetail',
        params: { id: rewardId },
      })
    );
  }
}
```

The `transform()` method resolves the configurable route path and interpolates params, returning a commands array suitable for `router.navigate()`.

---

## cxUrl Pipe in Templates

Use the `cxUrl` pipe in templates to generate route links from semantic route definitions. This keeps templates decoupled from URL structure.

```html
<a [routerLink]="{ cxRoute: 'product', params: { code: product.code, name: product.name } } | cxUrl">
  {{ product.name }}
</a>

<!-- Navigate to a route with no params -->
<a [routerLink]="{ cxRoute: 'loyaltyRewards' } | cxUrl">
  {{ 'loyalty.viewRewards' | cxTranslate }}
</a>
```

The `cxUrl` pipe resolves the semantic route to an actual URL path array. Always pass params as an object — never interpolate values into a string path.

---

## Custom URL Matcher

Use a custom URL matcher when the route pattern is too dynamic or ambiguous for a simple path string. Matchers return `UrlMatchResult` on match or `null` on no match.

```typescript
import { UrlMatchResult, UrlSegment } from '@angular/router';

export function loyaltyTierMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length === 2 && segments[0].path === 'loyalty') {
    const tier = segments[1].path;
    if (['bronze', 'silver', 'gold', 'platinum'].includes(tier)) {
      return {
        consumed: segments,
        posParams: {
          tier: segments[1],
        },
      };
    }
  }
  return null;
}
```

Register the matcher in the route config:

```typescript
const routes: Routes = [
  {
    matcher: loyaltyTierMatcher,
    canActivate: [CmsPageGuard],
    component: PageLayoutComponent,
    data: { pageLabel: '/loyalty-tier' },
  },
];
```

Use `posParams` to expose matched segments as route parameters that components and resolvers can read via `ActivatedRoute`.

---

## CmsPageGuard with Page Label

When a CMS page is identified by its page label (UID in the CMS) rather than a configurable route, use `pageLabel` in the route data.

```typescript
const routes: Routes = [
  {
    path: 'custom-landing',
    canActivate: [CmsPageGuard],
    component: PageLayoutComponent,
    data: { pageLabel: '/custom-landing' },
  },
];
```

The `pageLabel` tells `CmsPageGuard` which CMS page to load. The leading `/` is required for content pages. Use `cxRoute` instead when the page should participate in configurable routing.

---

## Custom Route Guard

Custom guards compose with Spartacus guards and return `Observable<boolean | UrlTree>`. Redirect via `router.parseUrl()` rather than `router.navigate()` inside guards.

```typescript
import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService } from '@spartacus/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class LoyaltyRewardsGuard implements CanActivate {
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

Stack custom guards with `CmsPageGuard` in the `canActivate` array — the custom guard runs first for access control, then `CmsPageGuard` resolves the CMS page.

```typescript
{
  path: null,
  canActivate: [LoyaltyRewardsGuard, CmsPageGuard],
  component: PageLayoutComponent,
  data: { cxRoute: 'loyaltyRewards' },
}
```

---

## Custom PageMetaResolver

Provide custom page titles, descriptions, and robots meta for SEO. Resolvers extend `PageMetaResolver` and implement the appropriate resolver interfaces.

```typescript
import { Injectable } from '@angular/core';
import { PageMetaResolver, PageTitleResolver, PageDescriptionResolver, PageRobotsResolver } from '@spartacus/core';
import { PageMeta, PageRobotsMeta } from '@spartacus/core';
import { Observable, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LoyaltyRewardsPageMetaResolver
  extends PageMetaResolver
  implements PageTitleResolver, PageDescriptionResolver, PageRobotsResolver
{
  constructor() {
    super();
    this.pageType = 'ContentPage';
    this.pageTemplate = 'LoyaltyRewardsPageTemplate';
  }

  resolveTitle(): Observable<string> {
    return of('My Loyalty Rewards | My Store');
  }

  resolveDescription(): Observable<string> {
    return of('View and redeem your loyalty rewards points.');
  }

  resolveRobots(): Observable<PageRobotsMeta[]> {
    return of([PageRobotsMeta.INDEX, PageRobotsMeta.FOLLOW]);
  }
}
```

Register the resolver in the feature module providers. Spartacus selects the resolver based on `pageType` and `pageTemplate` matching the current CMS page.

---

## External Route Configuration

Use `ExternalRoutesModule` to handle legacy URLs or redirects to external systems. This is useful during migration from a legacy storefront.

```typescript
import { provideDefaultConfig, RoutingConfig } from '@spartacus/core';

provideDefaultConfig(<RoutingConfig>{
  routing: {
    routes: {
      legacyProduct: {
        paths: ['old-products/:id'],
        redirectTo: 'product/:id',
      },
    },
  },
});
```

For truly external URLs (outside the SPA), configure external routes:

```typescript
import { ExternalRoutesConfig } from '@spartacus/core';

provideDefaultConfig(<ExternalRoutesConfig>{
  routing: {
    external: {
      patterns: [
        { match: /^\/blog\/.*/, destination: 'https://blog.mystore.com' },
      ],
    },
  },
});
```

External routes bypass the Angular router entirely and perform a full page navigation to the destination URL.
