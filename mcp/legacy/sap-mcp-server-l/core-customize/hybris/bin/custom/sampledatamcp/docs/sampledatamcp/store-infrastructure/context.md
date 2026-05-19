# Store Infrastructure — Context

## What It Does

Sets up all platform infrastructure needed for a working e-commerce store: OAuth authentication, product catalog, base store and site, delivery and payment modes, tax configuration, and Solr faceted search. This is the foundation layer that everything else depends on.

Loaded automatically on both `ant initialize` and `ant updatesystem` (essentialdata pattern), so infrastructure is always present and updated.

## When It's Used

- **Initialize:** Creates everything from scratch
- **Update system:** Re-applies configuration — safe to run repeatedly thanks to `INSERT_UPDATE`
- **Before sample data:** Must load before products, customers, or orders can be created

## Key Decisions

- **Single warehouse** — One `electronics-warehouse` with a single vendor. Simplifies stock management for a training/demo store. Production stores would have multiple warehouses with sourcing rules.
- **0% tax rate** — Prices display exactly as entered. Avoids tax calculation complexity for demos while still wiring the full tax infrastructure (UserTaxGroup, ProductTaxGroup, TaxRow) so it can be adjusted later.
- **Three delivery mode tiers** — Standard ($5.99), Express ($14.99), and Free ($0.00). Express has a second tier at $0 for orders >= $1,000 (defined in projectdata). Free delivery is not a user-facing option.
- **Hidden free-delivery mode** — `thinkshop-free-delivery` exists but is NOT listed in the BaseStore's `deliveryModes`. It is only applied by the promotion engine via `y_change_delivery_mode` when cart total >= $1,000. This prevents customers from selecting it manually.
- **Two OAuth scopes** — `trusted_client` (scope: extended) for server-to-server and admin tools, `mobile_android` (scope: basic) for storefront/mobile apps with a redirect URI for the authorization code flow.
- **Dual catalog versions** — Staged (content authoring) and Online (live storefront). Both created in essentialdata so they exist before products are imported into either version.
- **Solr in essentialdata** — Search configuration loads on every updatesystem, not just initialize. This means Solr config changes (new facets, sort options) are applied without a full data reset.
