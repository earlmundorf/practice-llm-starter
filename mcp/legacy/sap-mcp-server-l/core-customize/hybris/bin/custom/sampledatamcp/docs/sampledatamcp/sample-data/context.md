# Sample Data — Context

## What It Does

Creates 10 electronics products (with prices and stock), 3 test customers (with addresses), and 3 pre-populated orders. This gives the store realistic data for development, testing, and demos.

**ImpEx pattern:** `projectdata-*` — loaded on `ant initialize` only, not on updatesystem. A full initialize destroys and recreates this data.

## When It's Used

- **After initialize:** Products appear in the catalog, customers can log in, orders show in order history
- **Solr reindex required:** After initialize, run `./scripts/index-solr.sh` (from `core-customize/`) for products to appear in search
- **Promotion setup:** Run `setup-promotions.groovy` after this data exists for promotions to target these products

## Key Decisions

- **Dual catalog versioning** — Every product is imported into both Staged and Online versions. Staged is for content authoring; Online is what the storefront sees. PriceRow and StockLevel-to-Product links are also duplicated to both versions. This mirrors production catalog sync workflows.
- **Order calculation via Groovy** — Orders are created with `calculated=false`, then a Groovy snippet (`impex.getLastImportedItem().calculate()`) triggers the platform's price calculation engine. This ensures totals, taxes, and delivery costs are computed by the platform rather than hardcoded.
- **Address assignment via Groovy** — Order delivery and payment addresses are set via inline Groovy (`impex.getLastImportedItem().setDeliveryAddress(...)`) because ImpEx cannot easily reference a customer's default address by composite key. The Groovy pulls the address from the order's user.
- **Simple password strategy** — All test customers use password `1234`. Suitable for development only. The `$defaultPassword` macro makes it easy to change in one place.
- **StockLevel is global, linking is per-version** — StockLevel items are not catalog-versioned, but the Product-to-StockLevel relation is. The ImpEx creates StockLevels once, then links them to products in both Staged and Online via `[mode = append]`.
- **Free express shipping tier** — A second `ZoneDeliveryModeValue` for express delivery at $0.00 with minimum $1,000 is added at the end. The platform picks the tier where the order total meets the minimum, giving free express on large orders.
