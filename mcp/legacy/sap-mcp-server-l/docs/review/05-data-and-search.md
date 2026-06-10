# Data & Search Review

Scope: the `sampledatamcp` data extension (11 ImpEx files, promotions Groovy, 20 product images), the `KnowledgeEntry` type and content, and both Solr indexes. Paths relative to `core-customize/hybris/bin/custom/`.

## 1. Data architecture

The data design follows platform conventions precisely:

- **Naming-convention loading:** `essentialdata-*.impex` (infrastructure, Solr configs) and `projectdata-*.impex` (products, customers, orders, knowledge) auto-load on `yinitialize`/`yupdatesystem` in the documented order. No custom SystemSetup hooks needed.
- **Idempotent ImpEx:** `INSERT_UPDATE` with business keys (`code[unique=true]`, `uid[unique=true]`) throughout; no hardcoded PKs; macros (`$catalogVersion`, `$defaultPassword`) for reuse. Re-import is safe.
- **Correct dependency ordering** inside `essentialdata-infrastructure.impex` (441 lines): language → currency → country → units → catalog/versions → vendor/warehouse → tax → delivery/payment modes → ATP formula → base store → promotion group → CMS site → customer groups.
- **Demo data fully isolated** in `sampledatamcp` — dropping one extension from `localextensions.xml` yields a data-free production build (see document 04).

## 2. Inventory and quality

| Asset | Count | Quality notes |
|---|---|---|
| Products | 20 (10 electronics + 10 ThinkShop merch) | Realistic 100–200-word descriptions with genuine specs; loaded into **both** Staged and Online catalog versions; prices (`PriceRow`, USD, gross) and stock (single warehouse) for every product |
| Product images | 20 PNGs (160–255KB) | **All verified present on disk**; wired as thumbnail + picture in both catalog versions via `jar:` classpath media import |
| Customers | 3 | Full default shipping/billing addresses; password `1234` via macro (demo-only, see doc 04) |
| Orders | 3 (`THINK-0001..3`) | Reference real Online-version products, realistic totals, `Order.calculate()` invoked — order history demos work out of the box |
| Knowledge entries | 26 across 8 categories (policy, brand, promo, event, howto, contact, guide, loyalty) | Realistic content, priority weights for ranking, `validFrom/validTo` windows on time-bound entries, all `published` |
| Promotion rules | 5 + 2 coupons | See section 4 |

## 3. Solr index design

Two purpose-built indexes, both using the platform indexer framework (no raw Solr config hacking):

**`thinkshopIndex` (products):** code/name/summary/description indexed with the right flags (sortable name, autocomplete + spellcheck on name/code); three facets (5-band price ranges, in-stock flag, stock level status, plus category added by a follow-up ImpEx); five sorts; and a weighted DEFAULT free-text template (code 90, name 50/100-phrase, summary 25, description 10, fuzzy + postfix wildcard on name). This is a thought-through relevance profile, not a copied template.

**`knowledgeIndex` (knowledge base):** uid/title/summary/body/tags/searchableText with their own boost profile (uid 200, title 120/150-phrase, tags 80…), category facet, relevance + priority sorts. Pairing a content index with the product index mirrors the consolidated content+product search pattern long advocated in the SAP community (hybrismart) — and it's what makes the agent able to answer "what's your returns policy?" alongside "show me laptops."

**Design judgment call done right:** `validFrom/validTo` are deliberately *not* Solr-indexed (a SpringEL provider would emit non-ISO-8601 dates); date filtering happens in `DefaultKnowledgeSearchService` after the Solr query. The constraint is documented in a comment at the exclusion site — exactly where the next developer will look.

## 4. Promotions

Defined in an idempotent Groovy script (`sampledatamcp/resources/sampledatamcp/promotions/setup-promotions.groovy`, get-or-create pattern, safe to re-run) rather than fragile promotion ImpEx — a defensible choice that should be recorded as an ADR:

| Rule | Pattern demonstrated |
|---|---|
| Free shipping ≥ $1,000 | Cart-threshold + delivery-mode action, auto-apply |
| `LAPTOP10` coupon, 10% off laptop | Coupon-gated entry discount, 1/customer, 30 total redemptions |
| BOGO gaming mouse | Container-based partner action, CHEAPEST source, `maxAllowedRuns=0` — the correct anti-stacking pattern |
| Headphones 10% | Auto-apply product discount |
| `SPEAKER5` coupon | Short-window (30-day) coupon |

Sensible priority laddering (100→40), per-customer redemption caps, catalog-qualified product references. The set deliberately exercises distinct promotion-engine patterns — good demo engineering. Note the activation step (`publish-promotions.groovy` to Drools) is manual; see gap D-4.

## 5. Findings

| # | Finding | Severity | Notes / action |
|---|---|---|---|
| D-1 | **No category hierarchy.** Electronics products carry no category; merch sits in a single flat `swag` category. Category faceting/browsing — table stakes in any director-facing demo — has almost nothing to show | Medium | Add a small hierarchy (Computing → Laptops…, Audio, Accessories) — ImpEx-only change, Phase 4 |
| D-2 | **No Staged→Online sync job.** Both versions are double-imported instead; fine for a demo, but a `CatalogVersionSyncJob` is what an SAP architect expects to see, and it halves future data maintenance | Medium | Phase 4 |
| D-3 | **Single language/currency (en/USD).** All localization machinery is in place (`localized:` attributes, language-scoped Solr) but unexercised | Low | One additional locale would prove the i18n story |
| D-4 | **Solr update cronjobs inactive; indexing and promotion publishing are manual script steps** after init | Low | Deliberate for local dev; document in getting-started, and activate update jobs in cloud personas |
| D-5 | **Knowledge images on external Unsplash URLs** (7 entries) — breaks offline demos | Low | Localize the images into media, like products already do |
| D-6 | **Load-order coupling by filename:** the category-facet ImpEx relies on alphabetical ordering after `essentialdata-solr.impex` (acknowledged in a comment) | Low | Rename with a numeric prefix convention (`essentialdata-10-solr.impex`, `-20-product-category.impex`) to make ordering explicit |
| D-7 | **No edge-case demo data** — nothing out-of-stock, no zero-result searches scripted | Low | A couple of rows make agent demos more honest ("sorry, that's out of stock — here's an alternative") |

## 6. Verdict

The data layer is **demo-engineered to a high standard**: every product has an image that exists, every order references real products, the knowledge base is genuinely useful content, the Solr relevance profiles are tuned rather than templated, and the promotion set is a deliberate tour of engine patterns. The gaps are enrichment (categories, a second locale, sync job), not repair — and all are plain ImpEx work scheduled in Phase 4.
