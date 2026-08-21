# Spartacus Upgrade Patterns

Migration patterns and breaking changes by version boundary. Each section covers what changed, what schematics handle, and what may need manual fixing.

---

## Version Detection

Reliably detect the current Spartacus major from `package.json`:

```bash
# Extract Spartacus version
cat package.json | grep '"@spartacus/core"' | grep -oE '[0-9]+\.[0-9]+\.[0-9]+'

# Map to major:
# "4.3.6"    → Spartacus 4.x
# "6.8.2"    → Spartacus 6.x
# "2211.28.0" → Spartacus 2211.x
```

The 2211.x line uses a different numbering scheme: `2211.{minor}.{patch}`. The `2211` is the major, aligned with SAP Commerce Cloud version numbering.

---

## Package Manager Detection

Detect from lockfile and use the correct commands consistently:

```bash
# Detection
if [ -f pnpm-lock.yaml ]; then
  PKG_MGR="pnpm"
elif [ -f yarn.lock ]; then
  PKG_MGR="yarn"
else
  PKG_MGR="npm"
fi
```

| Action | npm | yarn | pnpm |
|--------|-----|------|------|
| Install | `npm install` | `yarn install` | `pnpm install` |
| Install (ignore peers) | `npm install --legacy-peer-deps` | `yarn install` | `pnpm install --no-strict-peer-dependencies` |
| Add package | `npm install pkg` | `yarn add pkg` | `pnpm add pkg` |

---

## Angular Version Stepping

Angular cannot skip major versions. Each step must be performed individually.

### Angular 9 → 10 → 11 (for Spartacus 2.x → 3.x)

```bash
npx ng update @angular/core@10 @angular/cli@10 --allow-dirty --force
npm install
npx ng update @angular/core@11 @angular/cli@11 --allow-dirty --force
npm install
```

Key changes:
- Angular 10: stricter `tsconfig.json` defaults, `ModuleWithProviders` requires generic type
- Angular 11: stricter type checking, `async` pipe requires `null` handling

### Angular 11 → 12 (for Spartacus 3.x → 4.x)

```bash
npx ng update @angular/core@12 @angular/cli@12 --allow-dirty --force
npm install
```

Key changes:
- Ivy is default (View Engine support deprecated)
- `emitDecoratorMetadata` no longer needed in `tsconfig.json`
- Strict mode enabled by default in new projects

### Angular 12 → 13 → 14 (for Spartacus 4.x → 5.x)

```bash
npx ng update @angular/core@13 @angular/cli@13 --allow-dirty --force
npm install
npx ng update @angular/core@14 @angular/cli@14 --allow-dirty --force
npm install
```

Key changes:
- Angular 13: View Engine removed entirely; all libraries must be Ivy-compiled
- Angular 13: `TestBed.teardown` enabled by default
- Angular 14: typed reactive forms (`FormControl<string>` instead of `FormControl`)
- Angular 14: standalone components introduced (opt-in)

### Angular 14 → 15 → 16 → 17 (for Spartacus 5.x → 6.x)

```bash
npx ng update @angular/core@15 @angular/cli@15 --allow-dirty --force
npm install
npx ng update @angular/core@16 @angular/cli@16 --allow-dirty --force
npm install
npx ng update @angular/core@17 @angular/cli@17 --allow-dirty --force
npm install
```

Key changes:
- Angular 15: standalone APIs stable, `RouterModule` changes
- Angular 16: signals introduced (opt-in), `DestroyRef`, required inputs
- Angular 17: new control flow (`@if`/`@for`/`@switch`), esbuild as default builder, deferrable views

Note: Spartacus 6.x still uses NgModules; the new Angular 17 features are available but optional.

---

## Spartacus 2.x → 3.x Breaking Changes

**Schematics handle:** Most import path updates, basic module restructuring.

**May need manual fixing:**

### B2cStorefrontModule Deprecation

```typescript
// Before (2.x)
import { B2cStorefrontModule } from '@spartacus/storefront';

@NgModule({
  imports: [B2cStorefrontModule.withConfig({ ... })]
})

// After (3.x) — import individual feature modules
import { BaseStorefrontModule } from '@spartacus/storefront';
import { UserModule } from '@spartacus/user';
import { CartModule } from '@spartacus/cart';

@NgModule({
  imports: [BaseStorefrontModule, UserModule, CartModule]
})
```

### Feature Library Extraction

Several features were extracted into their own `@spartacus/*` packages:
- `@spartacus/user` — account and profile features
- `@spartacus/cart` — cart features
- `@spartacus/order` — order history

Check imports referencing `@spartacus/core` or `@spartacus/storefront` for symbols that moved.

---

## Spartacus 3.x → 4.x Breaking Changes

**Schematics handle:** `ConfigModule.withConfig()` → `provideDefaultConfig()` migration, most import restructuring.

**May need manual fixing:**

### ConfigModule → provideDefaultConfig

```typescript
// Before (3.x)
import { ConfigModule } from '@spartacus/core';

@NgModule({
  imports: [
    ConfigModule.withConfig({
      cmsComponents: { ... }
    })
  ]
})

// After (4.x)
import { provideDefaultConfig } from '@spartacus/core';

@NgModule({
  providers: [
    provideDefaultConfig({
      cmsComponents: { ... }
    })
  ]
})
```

### StorefrontLib Entry Point Splits

`@spartacus/storefront` split into multiple secondary entry points:

```typescript
// Before (3.x)
import { CmsComponentData, OutletDirective } from '@spartacus/storefront';

// After (4.x)
import { CmsComponentData } from '@spartacus/storefront';
import { OutletDirective } from '@spartacus/storefront/cms-structure';
```

Search the project for all imports from `@spartacus/storefront` and verify they still resolve.

---

## Spartacus 4.x → 5.x Breaking Changes

**This is the most disruptive upgrade step.** Major library splits occurred.

**Schematics handle:** Package renames, many import path updates.

**May need manual fixing:**

### Checkout Library Split

```typescript
// Before (4.x)
import { CheckoutModule } from '@spartacus/checkout';
import { CheckoutDeliveryService } from '@spartacus/checkout';

// After (5.x) — split into base, b2b, scheduled-replenishment
import { CheckoutModule } from '@spartacus/checkout/base';
import { CheckoutDeliveryAddressFacade } from '@spartacus/checkout/base/root';
```

Key renames:
- `CheckoutDeliveryService` → `CheckoutDeliveryAddressFacade`
- `CheckoutPaymentService` → `CheckoutPaymentFacade`
- `CheckoutService` → `CheckoutFacade` (in `@spartacus/checkout/base/root`)

### Cart Library Split

```typescript
// Before (4.x)
import { CartModule, WishListModule } from '@spartacus/cart';

// After (5.x) — split into base, wish-list, saved-cart, quick-order, import-export
import { CartModule } from '@spartacus/cart/base';
import { WishListModule } from '@spartacus/cart/wish-list';
```

Sub-packages:
- `@spartacus/cart/base` — core cart
- `@spartacus/cart/wish-list` — wish list feature
- `@spartacus/cart/saved-cart` — saved carts (B2B)
- `@spartacus/cart/quick-order` — quick order (B2B)
- `@spartacus/cart/import-export` — cart import/export

### User Library Split

```typescript
// Before (4.x)
import { UserModule } from '@spartacus/user';

// After (5.x)
import { UserAccountModule } from '@spartacus/user/account';
import { UserProfileModule } from '@spartacus/user/profile';
```

### Order Library Extraction

```typescript
// Before (4.x)
import { OrderHistoryModule } from '@spartacus/core';

// After (5.x)
import { OrderModule } from '@spartacus/order';
```

### Service → Facade Renames

Many services were renamed to facades across all libraries:
- `*Service` → `*Facade` (public API)
- The concrete service implementations moved to `/core` sub-entry points

If you see errors about missing `*Service` classes, check if they were renamed to `*Facade` in the `/root` sub-entry point.

---

## Spartacus 5.x → 6.x Breaking Changes

**Schematics handle:** Most updates. The Angular 14 → 17 jump is the bigger effort here.

**May need manual fixing:**

### TypeScript 5.2+ Required

```json
// tsconfig.json — ensure version is compatible
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "node"
  }
}
```

### RxJS 7 Patterns

If RxJS was not updated during earlier steps, ensure all operators use the new import paths:

```typescript
// Before (RxJS 6)
import { map, filter } from 'rxjs/operators';
import { Observable } from 'rxjs';

// After (RxJS 7) — same syntax, but ensure no deprecated operators
// pluck → map, throwError callback form, etc.
```

Deprecated in RxJS 7:
- `pluck('key')` → `map(x => x.key)`
- `throwError('msg')` → `throwError(() => new Error('msg'))`
- `toPromise()` → `firstValueFrom()` or `lastValueFrom()`

### SSR Setup Changes

If the project uses server-side rendering:

```typescript
// Before (5.x)
import { NgExpressEngineModule } from '@spartacus/setup/ssr';

// After (6.x) — may need updates for Angular 17 SSR
// Check server.ts for Angular 17 express engine setup
```

### Deprecated API Removals

APIs deprecated in 4.x that were kept through 5.x may be removed in 6.x. Run a build and check for any references to removed symbols. Common ones:

- `OccCartAdapter` method signature changes
- `AuthRedirectService` removed (replaced by `AuthFlowRouteGuard`)
- Various `*Module.forRoot()` calls simplified

---

## Spartacus 6.x → 2211.x Changes

**Minimal code changes.** This is primarily a version rebranding to align with SAP Commerce Cloud numbering.

**Schematics handle:** Package version bump, minor API adjustments.

**May need manual fixing:**

- Package versions jump from `~6.x.x` to `~2211.x.x` — lockfiles will change significantly
- Some new features may add new peer dependency requirements
- Check that any version pinning in `package.json` or CI scripts accommodates the `2211` numbering

This is typically the easiest step in the upgrade chain.

---

## 2211.x Minor/Patch Updates

Within the 2211.x line (e.g., 2211.19 → 2211.32), updates are less disruptive but can still include:

- Bug fixes and security patches
- New optional features
- Minor API additions (non-breaking)
- Occasional Angular minor version bumps

```bash
# Update within 2211.x
npx ng update @spartacus/schematics@2211.32 --allow-dirty --force
npm install
npx ng build --configuration=production
```

Check the [Spartacus GitHub releases](https://github.com/SAP/spartacus/releases) for release notes between your current and target 2211.x versions.

---

## Cross-Version Patterns

### Schematic Dry-Run

Preview what schematics will change before applying:

```bash
npx ng update @spartacus/schematics@{version} --allow-dirty --force --dry-run
```

Review the output to understand the scope of changes. This does not modify any files.

### Peer Dependency Conflicts

When `npm install` fails due to unresolvable peer deps:

```bash
# Option 1: Skip strict peer checking (npm 7+)
npm install --legacy-peer-deps

# Option 2: Force through (last resort)
npm install --force
```

After forcing, review `npm ls` for actual conflicts and resolve them by aligning versions.

### Manual Dependency Resolution

When schematics miss updating a `@spartacus/*` package:

```bash
# Check which packages are still on the old version
cat package.json | grep @spartacus | sort

# Manually update a missed package
npm install @spartacus/missed-package@{targetVersion}
```

### TypeScript Strict Mode Fixes

Common type errors after upgrades:

```typescript
// Error: Object is possibly 'undefined'
// Fix: Add null checks
const name = product?.name ?? '';

// Error: Type 'Observable<X | undefined>' not assignable to 'Observable<X>'
// Fix: Use filter with type guard
import { filter } from 'rxjs';
source$.pipe(
  filter((val): val is X => val !== undefined)
);
```

### Build Cache Issues

If the build produces stale errors after an upgrade:

```bash
# Clear Angular build cache
rm -rf .angular/cache
rm -rf node_modules/.cache

# Reinstall cleanly
rm -rf node_modules
npm install
```
