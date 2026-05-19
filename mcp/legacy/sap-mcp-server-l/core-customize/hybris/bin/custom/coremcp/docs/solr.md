# Solr Search Configuration — coremcp / ThinkShop

## Overview

The coremcp extension configures Solr faceted search for the 10 ThinkShop electronics products. This enables full-text search, price range faceting, stock status faceting, and multiple sort options via the OCC `/products/search` endpoint.

## Architecture

```
OCC /products/search → ProductSearchFacade → SolrProductSearchFacade
                                                    ↓
                                          SolrFacetSearchConfig (thinkshopIndex)
                                                    ↓
                                          Embedded Solr (port 8983, HTTPS)
```

**Index name**: `thinkshopIndex`
**Indexed type**: `thinkshopProductType`
**Catalog**: `electronicsProductCatalog` (Online + Staged)

## Indexed Properties

| Property | Type | Searchable | Facet | Sortable |
|----------|------|------------|-------|----------|
| code | string | autocomplete | no | no |
| name | text (localized) | phrase + fuzzy + wildcard | no | yes (sortabletext) |
| summary | text (localized) | yes | no | no |
| description | text (localized) | yes | no | no |
| priceValue | double (currency) | no | MultiSelectOr (ranges) | yes |
| inStockFlag | boolean | no | MultiSelectOr | no |
| stockLevelStatus | string | no | MultiSelectOr | no |

## Price Range Facets (USD)

| Range | From | To |
|-------|------|----|
| $0–$99.99 | 0 | 99.99 |
| $100–$299.99 | 100 | 299.99 |
| $300–$699.99 | 300 | 699.99 |
| $700–$999.99 | 700 | 999.99 |
| $1,000–$2,000 | 1000 | 2000 |

## Sort Options

| Code | Fields |
|------|--------|
| relevance | score desc, inStockFlag desc |
| name-asc | name ascending |
| name-desc | name descending |
| price-asc | priceValue ascending |
| price-desc | priceValue descending |

## Setup

### Prerequisites

These extensions must be in `localextensions.xml`:

```xml
<extension name="solrserver" />
<extension name="solrfacetsearch" />
<extension name="solrfacetsearchbackoffice" />
<extension name="backofficesolrsearch" />
```

### Build & Initialize

```bash
cd bin/platform && . ./setantenv.sh
ant clean all initialize
./hybrisserver.sh
```

The Solr ImpEx (`essentialdata-coremcp_solr.impex`) loads automatically during initialize/updatesystem.

### Trigger Full Index

**Option A — HAC UI:**
1. Go to HAC → Platform → Solr → Indexing Operations
2. Select `thinkshopIndex`
3. Choose "Full Index" → Start

**Option B — HAC ImpEx Console:**
```impex
$START = "PERFORM"
INSERT_UPDATE SolrIndexerCronJob; code[unique=true]; active
; full-thinkshopIndex-cronJob ; true
```
Then trigger via HAC → System → Background Processes → CronJobs → find `full-thinkshopIndex-cronJob` → Run.

## Verification

### Check Solr is Running

Visit https://localhost:8983/solr/ (accept the self-signed certificate).

### Verify Index Contents

HAC → Platform → Solr → Search → select `thinkshopIndex` → query `*:*` → should return 10 documents.

### Test via OCC

```bash
# Search by text
curl -s -k "https://localhost:9002/occ/v2/electronics/products/search?query=laptop&fields=FULL"

# Search with facets
curl -s -k "https://localhost:9002/occ/v2/electronics/products/search?query=:relevance&fields=FULL"

# Search with price sort
curl -s -k "https://localhost:9002/occ/v2/electronics/products/search?query=:price-asc&fields=FULL"

# Search with price range filter
curl -s -k "https://localhost:9002/occ/v2/electronics/products/search?query=:relevance:price:%240-%2499.99&fields=FULL"
```

## Cron Jobs

| Job | Operation | Schedule | Active |
|-----|-----------|----------|--------|
| `full-thinkshopIndex-cronJob` | Full reindex | Manual | false |
| `update-thinkshopIndex-cronJob` | Incremental update | Every 5 min | false |

Both are inactive by default. Activate via HAC or ImpEx as needed.

## Files

| File | Purpose |
|------|---------|
| `resources/impex/essentialdata-solr-coremcp.impex` | Solr config (auto-loaded on init/update) |
| `docs/solr.md` | This documentation |

The Solr ImpEx is named `essentialdata-solr-coremcp.impex` so it sorts alphabetically after
`essentialdata-coremcp.impex` (`s` > `m`), ensuring Currency and Catalog exist first.
