# Promotions Setup — Components

Single Groovy script creates all promotion rules, coupons, and publishes to Drools.

**File:** `resources/sampledatamcp/promotions/setup-promotions.groovy`

## Helper Functions

### getOrCreateRule(code)

Queries `PromotionSourceRule` by code via FlexibleSearch. Returns the existing model if found (for update), or creates a new `PromotionSourceRuleModel` with that code. Prints whether it is creating or updating.

### getOrCreateCoupon(couponId)

Queries `SingleCodeCoupon` by couponId via FlexibleSearch. Same create-or-update pattern. Returns a `SingleCodeCouponModel`.

Both helpers use Spring beans injected via `spring.getBean()` — `flexibleSearchService` for queries and `modelService` for create/save.

## Promotion Rules

### 1. free_shipping_1000 — Free Shipping on Orders >= $1,000

| Aspect | Detail |
|---|---|
| **Priority** | 100 (highest) |
| **Window** | 90 days |
| **Condition** | `y_cart_total` >= $1,000 USD |
| **Action** | `y_change_delivery_mode` to `thinkshop-free-delivery` |
| **Trigger** | Automatic — fires on any cart meeting the threshold |

Swaps the delivery mode to the hidden free-delivery mode. The customer sees $0.00 shipping without being able to select it directly.

### 2. laptop_10pct_coupon — 10% Off Laptop with Coupon

| Aspect | Detail |
|---|---|
| **Priority** | 50 |
| **Window** | 90 days |
| **Condition** | Coupon `LAPTOP10` applied AND cart contains `LAPTOP_PRO_15` (qty >= 1) |
| **Action** | `y_order_entry_percentage_discount` at 10% |
| **Trigger** | Coupon-gated — customer must enter LAPTOP10 |

Uses two chained conditions: `y_qualifying_coupons` validates the coupon, `y_qualifying_products` checks the product. Both must pass.

### 3. bogo_mouse — Buy One Get One Free Gaming Mouse

| Aspect | Detail |
|---|---|
| **Priority** | 75 |
| **Window** | 90 days |
| **Condition** | Cart contains `WIRELESS_GAMING_MOUSE` (qty >= 2) |
| **Action** | `y_order_entry_percentage_discount` at 50% |
| **Trigger** | Automatic — fires when 2+ mice are in the cart |

Implemented as 50% off the line item (not literally free second unit). When buying 2 at $79.99 each, the 50% discount on the line total ($159.98) effectively makes one free.

### 4. headphones_10pct — 10% Off Wireless Headphones

| Aspect | Detail |
|---|---|
| **Priority** | 60 |
| **Window** | 90 days |
| **Condition** | Cart contains `WIRELESS_HEADPHONES` (qty >= 1) |
| **Action** | `y_order_entry_percentage_discount` at 10% |
| **Trigger** | Automatic — no coupon required |

Simplest product discount. Always active for the 90-day window.

### 5. speaker_5pct_coupon — 5% Off Bluetooth Speaker with Coupon

| Aspect | Detail |
|---|---|
| **Priority** | 40 (lowest) |
| **Window** | 30 days (shorter than others) |
| **Condition** | Coupon `SPEAKER5` applied AND cart contains `BLUETOOTH_SPEAKER` (qty >= 1) |
| **Action** | `y_order_entry_percentage_discount` at 5% |
| **Trigger** | Coupon-gated — customer must enter SPEAKER5 |

Demonstrates a time-limited coupon offer. Same dual-condition pattern as the laptop coupon.

## Coupons

| Coupon ID | Name | Max Per Customer | Max Total | Window |
|---|---|---|---|---|
| `LAPTOP10` | 10% off Laptop | 1 | 30 | 90 days |
| `SPEAKER5` | 5% off Bluetooth Speaker | 1 | 50 | 30 days |

Both are `SingleCodeCoupon` — the coupon code IS the coupon ID (no generated codes). Active immediately on creation.

## Publish Step

After all rules and coupons are saved:

1. Queries for the `DroolsKIEModule` named `promotions-module`
2. Loads all `PromotionSourceRule` items from the database
3. Calls `ruleMaintenanceService.compileAndPublishRules(allRules, "promotions-module", false)`
4. The `false` parameter means "do not publish incrementally" — all rules are compiled and deployed together

If the module is not found or publish fails, the script prints an error with a manual fallback path via Backoffice.
