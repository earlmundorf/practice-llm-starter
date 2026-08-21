# Spartacus Upgrade Examples

Example upgrade sequences showing the commands executed, expected output, and common issues encountered at each step.

---

## EXAMPLE: Full Path — Spartacus 2.1 → 2211.x

The longest possible upgrade path, covering every major version hop.

### Starting State

```json
{
  "@spartacus/core": "~2.1.4",
  "@spartacus/storefront": "~2.1.4",
  "@angular/core": "~9.1.0",
  "@ngrx/store": "~9.2.0"
}
```

### Upgrade Path

```
2.x → 3.x (Angular 10-11) → 4.x (Angular 12) → 5.x (Angular 14) → 6.x (Angular 17) → 2211.x (Angular 17)
Total: 5 major version steps
```

### Step 1: 2.x → 3.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 3.x upgrade"

# Angular 9 → 10 → 11
npx ng update @angular/core@10 @angular/cli@10 --allow-dirty --force
npm install
npx ng update @angular/core@11 @angular/cli@11 --allow-dirty --force
npm install

# Spartacus 3.x
npx ng update @spartacus/schematics@3 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 3.x"
```

### Step 2: 3.x → 4.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 4.x upgrade"

# Angular 11 → 12
npx ng update @angular/core@12 @angular/cli@12 --allow-dirty --force
npm install

# Spartacus 4.x
npx ng update @spartacus/schematics@4 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 4.x"
```

### Step 3: 4.x → 5.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 5.x upgrade"

# Angular 12 → 13 → 14
npx ng update @angular/core@13 @angular/cli@13 --allow-dirty --force
npm install
npx ng update @angular/core@14 @angular/cli@14 --allow-dirty --force
npm install

# Spartacus 5.x
npx ng update @spartacus/schematics@5 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 5.x"
```

### Step 4: 5.x → 6.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 6.x upgrade"

# Angular 14 → 15 → 16 → 17
npx ng update @angular/core@15 @angular/cli@15 --allow-dirty --force
npm install
npx ng update @angular/core@16 @angular/cli@16 --allow-dirty --force
npm install
npx ng update @angular/core@17 @angular/cli@17 --allow-dirty --force
npm install

# Spartacus 6.x
npx ng update @spartacus/schematics@6 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 6.x"
```

### Step 5: 6.x → 2211.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 2211.x upgrade"

# No Angular update needed
npx ng update @spartacus/schematics@2211 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 2211.x"
```

### Final State

```json
{
  "@spartacus/core": "~2211.28.0",
  "@spartacus/storefront": "~2211.28.0",
  "@angular/core": "~17.3.0",
  "@ngrx/store": "~17.2.0"
}
```

---

## EXAMPLE: Common Path — Spartacus 4.3 → 2211.x

The most common scenario — a project stuck on Spartacus 4.x needing to reach current LTS.

### Starting State

```json
{
  "@spartacus/core": "~4.3.6",
  "@spartacus/storefront": "~4.3.6",
  "@angular/core": "~12.2.0",
  "@ngrx/store": "~12.4.0"
}
```

### Upgrade Path

```
4.x → 5.x (Angular 14) → 6.x (Angular 17) → 2211.x (Angular 17)
Total: 3 major version steps
```

### Step 1: 4.x → 5.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 5.x upgrade"

npx ng update @angular/core@13 @angular/cli@13 --allow-dirty --force
npm install
npx ng update @angular/core@14 @angular/cli@14 --allow-dirty --force
npm install

npx ng update @spartacus/schematics@5 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 5.x"
```

### Step 2: 5.x → 6.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 6.x upgrade"

npx ng update @angular/core@15 @angular/cli@15 --allow-dirty --force
npm install
npx ng update @angular/core@16 @angular/cli@16 --allow-dirty --force
npm install
npx ng update @angular/core@17 @angular/cli@17 --allow-dirty --force
npm install

npx ng update @spartacus/schematics@6 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 6.x"
```

### Step 3: 6.x → 2211.x

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 2211.x upgrade"

npx ng update @spartacus/schematics@2211 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 2211.x"
```

---

## EXAMPLE: Single Step — Spartacus 5.x → 6.x

A single major version hop for a relatively recent project.

### Starting State

```json
{
  "@spartacus/core": "~5.2.0",
  "@angular/core": "~14.2.0"
}
```

### Upgrade

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 6.x upgrade"

# Angular 14 → 15 → 16 → 17
npx ng update @angular/core@15 @angular/cli@15 --allow-dirty --force
npm install
npx ng update @angular/core@16 @angular/cli@16 --allow-dirty --force
npm install
npx ng update @angular/core@17 @angular/cli@17 --allow-dirty --force
npm install

# Spartacus 6.x
npx ng update @spartacus/schematics@6 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: upgrade Spartacus to 6.x"
```

---

## EXAMPLE: Minor/Patch — 2211.19 → 2211.32

Within-line update for a project already on 2211.x.

### Starting State

```json
{
  "@spartacus/core": "~2211.19.0",
  "@angular/core": "~17.1.0"
}
```

### Upgrade

```bash
git add -A && git commit -m "chore: checkpoint before Spartacus 2211.32 update"

# Check if Angular minor update is needed
# 2211.32 may require Angular 17.3+ — check release notes
npx ng update @angular/core@17 @angular/cli@17 --allow-dirty --force
npm install

# Update Spartacus
npx ng update @spartacus/schematics@2211.32 --allow-dirty --force
npm install

npx ng build --configuration=production
git add -A && git commit -m "chore: update Spartacus to 2211.32"
```

### Notes

- No major Angular stepping needed (stays within 17.x)
- Schematics will handle any minor migration scripts
- Check [release notes](https://github.com/SAP/spartacus/releases) for breaking changes between 2211.19 and 2211.32

---

## EXAMPLE: Build Failure Diagnosis and Fix

Shows how to handle a build failure mid-upgrade at the 4.x → 5.x boundary.

### Error Output

```
ERROR in src/app/features/checkout/checkout-shipping.component.ts:3:10
  TS2305: Module '"@spartacus/checkout"' has no exported member 'CheckoutDeliveryService'.

ERROR in src/app/features/checkout/checkout-review.component.ts:4:10
  TS2305: Module '"@spartacus/checkout"' has no exported member 'CheckoutPaymentService'.

ERROR in src/app/features/cart/mini-cart.component.ts:2:10
  TS2305: Module '"@spartacus/cart"' has no exported member 'ActiveCartService'.
```

### Diagnosis

The schematics updated standard Spartacus components but missed these custom components. In Spartacus 5.x:
- `@spartacus/checkout` was split into `@spartacus/checkout/base`, `/b2b`, `/scheduled-replenishment`
- `@spartacus/cart` was split into `@spartacus/cart/base`, `/wish-list`, etc.
- Several services were renamed from `*Service` to `*Facade`

### Fixes

```typescript
// checkout-shipping.component.ts
// Before
import { CheckoutDeliveryService } from '@spartacus/checkout';
// After
import { CheckoutDeliveryAddressFacade } from '@spartacus/checkout/base/root';

// checkout-review.component.ts
// Before
import { CheckoutPaymentService } from '@spartacus/checkout';
// After
import { CheckoutPaymentFacade } from '@spartacus/checkout/base/root';

// mini-cart.component.ts
// Before
import { ActiveCartService } from '@spartacus/cart';
// After
import { ActiveCartFacade } from '@spartacus/cart/base/root';
```

### Search and Fix All Occurrences

```bash
# Find all files with old imports
grep -rl "from '@spartacus/checkout'" src/ --include="*.ts"
grep -rl "from '@spartacus/cart'" src/ --include="*.ts"
grep -rl "CheckoutDeliveryService" src/ --include="*.ts"
grep -rl "ActiveCartService" src/ --include="*.ts"
```

Fix each file, then rebuild:

```bash
npx ng build --configuration=production
```

---

## BAD: Skipping Major Versions

```bash
# DO NOT DO THIS — jumping from 4.x directly to 2211.x
npx ng update @spartacus/schematics@2211 --allow-dirty --force
```

**What goes wrong:**
- Migration schematics are designed to run incrementally — each version's schematic assumes the previous version's migrations have already been applied
- Angular also cannot skip majors — internal metadata formats and compiler behavior change at each major
- Result: hundreds of errors with no clear path to fix, because breaking changes from multiple versions compound

**Correct approach:** Always step through each major: 4 → 5 → 6 → 2211. Each step is debuggable in isolation.

---

## BAD: Not Verifying Build Between Steps

```bash
# DO NOT DO THIS — upgrading without checking intermediate builds
npx ng update @angular/core@14 @angular/cli@14 --allow-dirty --force
npx ng update @spartacus/schematics@5 --allow-dirty --force
npx ng update @angular/core@17 @angular/cli@17 --allow-dirty --force
npx ng update @spartacus/schematics@6 --allow-dirty --force
npm install
npx ng build  # 50+ errors — impossible to tell which step caused them
```

**What goes wrong:**
- Errors compound across steps — a missing import from step 1 cascades into step 2
- Cannot tell which major version upgrade introduced a problem
- Rollback is all-or-nothing instead of per-step
- Debugging is exponentially harder with mixed-version errors

**Correct approach:** Build and commit after every major version hop. Each intermediate state should compile cleanly before proceeding.

---

## BAD: Skipping Angular Intermediate Majors

```bash
# DO NOT DO THIS — jumping Angular from 12 to 17 directly
npx ng update @angular/core@17 @angular/cli@17 --allow-dirty --force
```

**What goes wrong:**
- Angular's `ng update` schematics for version N assume you're on version N-1
- Internal compiler metadata, decorator formats, and dependency resolution change at each major
- Result: corrupted `angular.json`, broken TypeScript config, missing polyfills

**Correct approach:** Step through each Angular major: 12 → 13 → 14 → 15 → 16 → 17. Run `npm install` after each step.
