# Promotions Setup — Context

## What It Does

Groovy script that creates 5 promotion rules and 2 coupons, then publishes them to the Drools rule engine. Covers common promotion patterns: cart-level free shipping, product-specific percentage discounts, BOGO, and coupon-gated offers.

**Execution:** Manual — run via HAC Groovy console (with commit) or `./scripts/setup-promotions.sh` (from `core-customize/`). Not auto-loaded by the platform.

## When It's Used

- **After initialize:** Run after sample data exists so the PromotionGroup and referenced products are in place
- **Anytime:** Safe to re-run — idempotent, updates existing rules instead of creating duplicates
- **Before demos:** Ensures promotions are active and published to the Drools engine

## Key Decisions

- **Groovy, not ImpEx** — Promotion rules store their conditions and actions as JSON blobs. These JSON strings contain nested UUIDs, enum references, and product catalog qualifiers that are extremely fragile in ImpEx column format. Groovy allows setting these as raw strings without escaping issues.
- **Idempotency pattern** — `getOrCreateRule` and `getOrCreateCoupon` helper functions query by code/ID first. If found, they return the existing model for update. If not, they create a new one. This makes the script safe to run repeatedly.
- **90-day vs 30-day windows** — Most rules use a 90-day window from execution time. The SPEAKER5 coupon uses a shorter 30-day window to demonstrate time-limited offers. All dates are relative to when the script runs.
- **Priority ordering** — Higher priority = evaluated first. Free shipping (100) takes precedence over product discounts. BOGO (75) beats automatic headphone discount (60). Coupon rules have lower priority (50, 40) since they require explicit activation.
- **Hidden delivery mode** — The free shipping rule uses `y_change_delivery_mode` to swap to `thinkshop-free-delivery`, which was created in essentialdata but intentionally excluded from the BaseStore's user-facing delivery modes.
- **Publish step** — After creating/updating all rules, the script calls `ruleMaintenanceService.compileAndPublishRules()` to compile rules into Drools and deploy them to the `promotions-module`. Without this, rules exist in the database but are not evaluated at cart calculation time.

## Priority Map

| Priority | Rule | Type |
|---|---|---|
| 100 | Free Shipping >= $1,000 | Cart-level, automatic |
| 75 | BOGO Gaming Mouse | Product-level, automatic |
| 60 | 10% Off Headphones | Product-level, automatic |
| 50 | 10% Off Laptop (LAPTOP10) | Product-level, coupon-gated |
| 40 | 5% Off Speaker (SPEAKER5) | Product-level, coupon-gated |
