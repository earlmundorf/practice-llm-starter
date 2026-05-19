# Spartacus Routing Examples

Focused code snippets showing correct and incorrect patterns for CMS-driven routing.

---

## GOOD: CMS-Driven Route with Configurable Path

Route correctly uses `CmsPageGuard`, `PageLayoutComponent`, and configurable routes via `provideDefaultConfig`.

```typescript
// custom-page-routing.module.ts
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CmsPageGuard, PageLayoutComponent } from '@spartacus/storefront';
import { provideDefaultConfig, RoutingConfig } from '@spartacus/core';

const routes: Routes = [
  {
    path: null,
    canActivate: [CmsPageGuard],
    component: PageLayoutComponent,
    data: { cxRoute: 'customPage' },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  providers: [
    provideDefaultConfig(<RoutingConfig>{
      routing: {
        routes: {
          customPage: {
            paths: ['custom-page', 'my-custom-page'],
          },
        },
      },
    }),
  ],
})
export class CustomPageRoutingModule {}
```

Why this is correct:
- `CmsPageGuard` resolves the CMS page layout and slots automatically
- `PageLayoutComponent` renders the CMS page template — no custom component needed
- Route path is configurable via `provideDefaultConfig`, not hardcoded in the route
- Multiple path aliases supported via the `paths` array

---

## GOOD: Navigation with cxUrl Pipe

Component template uses `cxUrl` pipe for links and `SemanticPathService` for programmatic navigation.

```html
<!-- rewards-list.component.html -->
@if (rewards$ | async; as rewards) {
  <ul class="cx-rewards-list">
    @for (reward of rewards; track reward.id) {
      <li>
        <a [routerLink]="{ cxRoute: 'rewardDetail', params: { id: reward.id, name: reward.name } } | cxUrl">
          {{ reward.name }}
        </a>
        <span>{{ reward.points }} {{ 'loyalty.points' | cxTranslate }}</span>
      </li>
    }
  </ul>
}
```

```typescript
// rewards-list.component.ts
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { SemanticPathService } from '@spartacus/core';

@Component({
  selector: 'cx-rewards-list',
  templateUrl: './rewards-list.component.html',
})
export class RewardsListComponent {
  rewards$ = this.rewardsService.getRewards();

  constructor(
    protected rewardsService: RewardsService,
    protected router: Router,
    protected semanticPathService: SemanticPathService
  ) {}

  navigateToReward(rewardId: string): void {
    this.router.navigate(
      this.semanticPathService.transform({
        cxRoute: 'rewardDetail',
        params: { id: rewardId },
      })
    );
  }
}
```

Why this is correct:
- Template uses `cxUrl` pipe — URLs respect configurable route paths
- Programmatic navigation uses `SemanticPathService.transform()`
- No hardcoded URL strings anywhere
- Route params passed as an object, not interpolated into a string

---

## BAD: Hardcoded Router Links

```html
<!-- bad-navigation.component.html -->
<a routerLink="/product/12345">View Product</a>
<a [routerLink]="['/loyalty-rewards', reward.id]">View Reward</a>
<button (click)="goToRewards()">My Rewards</button>
```

```typescript
// bad-navigation.component.ts
goToRewards(): void {
  this.router.navigate(['/loyalty-rewards']);  // hardcoded path
}
```

**What's wrong:**
- Hardcoded `/product/12345` breaks if the route path is reconfigured
- Building URL arrays manually bypasses Spartacus configurable routing
- If the storefront reconfigures `loyaltyRewards` to use path `my-rewards`, all hardcoded links break
- No `paramsMapping` support — if the API field name differs from the URL param, the link is wrong

**Fix:** Use `cxUrl` pipe in templates and `SemanticPathService.transform()` in TypeScript.

---

## BAD: Route without CmsPageGuard

```typescript
// bad-routing.module.ts
const routes: Routes = [
  {
    path: 'loyalty-rewards',
    component: LoyaltyRewardsComponent, // custom component, not PageLayoutComponent
    // missing CmsPageGuard — CMS content will NOT load
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class BadRoutingModule {}
```

**What's wrong:**
- Missing `CmsPageGuard` means the CMS page data is never fetched
- Using a custom component instead of `PageLayoutComponent` bypasses the CMS page template and slot resolution
- CMS-managed content (banners, paragraphs, navigation) will not render
- Page meta (title, description) from the CMS will not be applied

**Fix:** Use `CmsPageGuard` in `canActivate` and `PageLayoutComponent` as the component. Map individual CMS components separately via `cmsComponents` config.

---

## GENERATE OUTPUT: /spartacus-routing generate loyalty-rewards

Running `/spartacus-routing generate loyalty-rewards` produces these files:

### loyalty-rewards-routing.module.ts

```typescript
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CmsPageGuard, PageLayoutComponent } from '@spartacus/storefront';
import { AuthGuard, provideDefaultConfig, RoutingConfig } from '@spartacus/core';
import { LoyaltyRewardsGuard } from './loyalty-rewards.guard';

const routes: Routes = [
  {
    path: null,
    canActivate: [LoyaltyRewardsGuard, CmsPageGuard],
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
            paramsMapping: {},
          },
        },
      },
    }),
  ],
})
export class LoyaltyRewardsRoutingModule {}
```

### loyalty-rewards.guard.ts

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
      map(loggedIn => {
        if (loggedIn) {
          return true;
        }
        return this.router.parseUrl('/login');
      })
    );
  }
}
```

### loyalty-rewards-page-meta.resolver.ts

```typescript
import { Injectable } from '@angular/core';
import {
  PageMetaResolver,
  PageTitleResolver,
  PageDescriptionResolver,
  PageRobotsResolver,
  PageRobotsMeta,
  TranslationService,
} from '@spartacus/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class LoyaltyRewardsPageMetaResolver
  extends PageMetaResolver
  implements PageTitleResolver, PageDescriptionResolver, PageRobotsResolver
{
  constructor(protected translationService: TranslationService) {
    super();
    this.pageType = 'ContentPage';
    this.pageTemplate = 'LoyaltyRewardsPageTemplate';
  }

  resolveTitle(): Observable<string> {
    return this.translationService.translate('loyaltyRewards.pageTitle');
  }

  resolveDescription(): Observable<string> {
    return this.translationService.translate('loyaltyRewards.pageDescription');
  }

  resolveRobots(): Observable<PageRobotsMeta[]> {
    return this.translationService.translate('loyaltyRewards.pageTitle').pipe(
      map(() => [PageRobotsMeta.INDEX, PageRobotsMeta.FOLLOW])
    );
  }
}
```

### loyalty-rewards.module.ts

```typescript
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nModule, provideDefaultConfig, CmsConfig } from '@spartacus/core';
import { LoyaltyRewardsRoutingModule } from './loyalty-rewards-routing.module';
import { LoyaltyRewardsPageMetaResolver } from './loyalty-rewards-page-meta.resolver';

@NgModule({
  imports: [
    CommonModule,
    I18nModule,
    LoyaltyRewardsRoutingModule,
  ],
  providers: [
    LoyaltyRewardsPageMetaResolver,
  ],
})
export class LoyaltyRewardsModule {}
```

Register the feature module for lazy loading in your app configuration:

```typescript
provideDefaultConfig({
  featureModules: {
    loyaltyRewards: {
      module: () =>
        import('./features/loyalty-rewards/loyalty-rewards.module').then(
          m => m.LoyaltyRewardsModule
        ),
    },
  },
});
```
