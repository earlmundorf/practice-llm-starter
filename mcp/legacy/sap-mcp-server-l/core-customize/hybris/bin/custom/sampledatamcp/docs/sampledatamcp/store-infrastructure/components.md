# Store Infrastructure — Components

Two ImpEx files create the complete store infrastructure. Both use the `essentialdata-*` pattern (loaded on initialize and updatesystem).

## essentialdata-00-infrastructure.impex

**File:** `resources/impex/essentialdata-00-infrastructure.impex`

### OAuth

| Type | Key | Purpose |
|---|---|---|
| `OAuthClientDetails` | `trusted_client` | Server-to-server, scope: extended, all grant types |
| `OAuthClientDetails` | `mobile_android` | Storefront/mobile, scope: basic, includes redirect URI |

### Catalog

| Type | Key | Purpose |
|---|---|---|
| `Catalog` | `electronicsProductCatalog` | Single product catalog |
| `CatalogVersion` | `Staged` | Content authoring version (active: false) |
| `CatalogVersion` | `Online` | Live storefront version (active: true) |

### Reference Data

| Type | Key | Purpose |
|---|---|---|
| `Language` | `en` | English, active |
| `Currency` | `USD` | US Dollar, symbol: $ |
| `Country` | `US` | United States, active |
| `Unit` | `pieces` | Default unit of measure |

### Fulfillment

| Type | Key | Purpose |
|---|---|---|
| `Vendor` | `electronics-vendor` | Required parent for warehouse |
| `Warehouse` | `electronics-warehouse` | Single warehouse, default=true |
| `AtpFormula` | `thinkshop-atp-formula` | Availability-to-promise (availability only) |

### Tax

| Type | Key | Purpose |
|---|---|---|
| `UserTaxGroup` | `us-taxes` | Customer tax classification |
| `ProductTaxGroup` | `us-sales-tax-full` | Product tax classification |
| `Tax` | `us-sales-tax-full` | 0% rate |
| `TaxRow` | (Staged + Online) | Links Tax, ProductTaxGroup, UserTaxGroup per catalog version |

ImpEx pattern: TaxRow is catalog-versioned (part-of Product), so it must be imported into both Staged and Online versions separately.

### Delivery

| Type | Code | Price | Notes |
|---|---|---|---|
| `Zone` | `usa` | — | Maps to country US |
| `ZoneDeliveryMode` | `thinkshop-standard` | $5.99 | User-selectable |
| `ZoneDeliveryMode` | `thinkshop-express` | $14.99 | User-selectable |
| `ZoneDeliveryMode` | `thinkshop-free-delivery` | $0.00 | Promotion engine only, NOT in BaseStore.deliveryModes |

### Payment

| Type | Code | Purpose |
|---|---|---|
| `StandardPaymentMode` | `advance` | Payment in advance, uses AdvancePaymentInfo |
| `StandardPaymentModeValue` | advance/USD | $0.00 surcharge |

### Store & Site

| Type | Key | Purpose |
|---|---|---|
| `BaseStore` | `electronics` | Wires catalog, currency, warehouse, tax, delivery modes, ATP formula |
| `PromotionGroup` | `thinkshopPromoGrp` | Promotion targeting group |
| `CMSSite` | `electronics` | B2C site, links store + promotion group, URL pattern for all requests |
| `UserGroup` | `customergroup` | Standard customer group |

Key ImpEx patterns:
- BaseStore uses a separate `UPDATE` for `deliveryCountries` and `deliveryModes` (collection attributes after initial INSERT)
- CMSSite extends BaseSite (accelerator pattern) — the `channel(code)` B2C and `urlPatterns` regex are CMSSite-specific

---

## essentialdata-10-solr.impex

**File:** `resources/impex/essentialdata-10-solr.impex`

Sorts after infrastructure (`s` > `i`) so Catalog, Currency, and CatalogVersion already exist.

### Search Config

| Type | Key | Purpose |
|---|---|---|
| `SolrFacetSearchConfig` | `thinkshopIndex` | Top-level config, references Default server/search/index configs |
| `SolrIndexedType` | `thinkshopProductType` | Indexes `Product` type, not a variant |

ImpEx pattern: Collection attributes (`currencies`, `catalogVersions`) require a separate `UPDATE` after the initial `INSERT_UPDATE` of SolrFacetSearchConfig.

### Indexed Properties — Text Search

| Property | Type | Localized | Autocomplete | Spellcheck | Provider |
|---|---|---|---|---|---|
| `code` | string | no | yes | no | `springELValueProvider` (code) |
| `name` | text | yes | yes | yes | `springELValueProvider` (getName) |
| `summary` | text | yes | no | yes | `springELValueProvider` (getSummary) |
| `description` | text | yes | no | yes | `springELValueProvider` (getDescription) |

### Indexed Properties — Facets

| Property | Type | Facet Type | Provider | Range Set |
|---|---|---|---|---|
| `priceValue` | double | MultiSelectOr | `productPriceValueProvider` | thinkshopPriceRangeUSD |
| `price` | double | (not faceted) | `productPriceValueProvider` | — |
| `inStockFlag` | boolean | MultiSelectOr | `productInStockFlagValueProvider` | — |
| `stockLevelStatus` | string | MultiSelectOr | `productStockLevelStatusValueProvider` | — |

### Price Ranges

| Range | From | To |
|---|---|---|
| $0-$99.99 | 0 | 99.99 |
| $100-$299.99 | 100 | 299.99 |
| $300-$699.99 | 300 | 699.99 |
| $700-$999.99 | 700 | 999.99 |
| $1,000-$2,000 | 1000 | 2000 |

### Sorts

| Code | Field | Direction | Boost |
|---|---|---|---|
| `relevance` | score, inStockFlag | desc, desc | yes |
| `name-asc` | name | asc | no |
| `name-desc` | name | desc | no |
| `price-asc` | price | asc | no |
| `price-desc` | price | desc | no |

### Search Query Template

The `DEFAULT` template configures free-text search boosting:

| Property | Phrase Boost | Query Boost | Fuzzy Boost | Wildcard |
|---|---|---|---|---|
| code | — | 90 | — | — |
| name | 100 | 50 | 25 | POSTFIX |
| summary | 50 | 25 | — | — |
| description | — | 10 | — | — |

### Indexer

| Type | Query | Schedule |
|---|---|---|
| Full | `SELECT {PK} FROM {Product}` | Manual (cron job inactive) |
| Update | `WHERE {modifiedtime} >= ?lastIndexTime` | Every 5 min (trigger inactive) |

Both cron jobs are created inactive — indexing is triggered manually via `./scripts/index-solr.sh` (from `core-customize/`) or Backoffice.

### Wiring

Final `UPDATE` statements attach the Solr config to both `BaseStore` and `CMSSite` so OCC product search uses this index.
