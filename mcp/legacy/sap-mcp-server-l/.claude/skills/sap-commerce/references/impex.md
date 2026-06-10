# ImpEx Reference — Data Import/Export

## Table of Contents
1. [Overview](#overview)
2. [Syntax Basics](#syntax-basics)
3. [Header Modes](#header-modes)
4. [Type References and Translations](#type-references-and-translations)
5. [Macros](#macros)
6. [Special Translators](#special-translators)
7. [Catalog Versioning](#catalog-versioning)
8. [Catalog-Versioned vs Global Types](#catalog-versioned-vs-global-types)
9. [Catalog Synchronization](#catalog-synchronization)
10. [Media Import](#media-import)
11. [Conditional and Scripted ImpEx](#conditional-and-scripted-impex)
12. [Common Patterns](#common-patterns)
13. [Debugging ImpEx](#debugging-impex)

---

## Overview

ImpEx (Import/Export) is SAP Commerce's text-based data loading language. It's used for:
- Initial data setup (countries, currencies, base site config)
- Sample/test data loading
- Data migration between environments
- Bulk updates

ImpEx files typically live in `resources/impex/` within an extension and are executed during `ant initialize` or `ant updatesystem`, or manually through HAC (Hybris Administration Console).

## Syntax Basics

```impex
# This is a comment

# Header line defines the operation and columns
INSERT_UPDATE Product ; code[unique=true] ; name[lang=en]       ; name[lang=de]
                      ; HAMMER-001        ; Claw Hammer          ; Klauenhammer
                      ; WRENCH-001        ; Adjustable Wrench    ; Verstellbarer Schraubenschlüssel
```

Rules:
- The **header** starts with a mode (`INSERT`, `UPDATE`, `INSERT_UPDATE`, `REMOVE`) followed by the type
- **Columns** are separated by `;`
- **Data lines** start with `;` (the first column is effectively blank or continues the header)
- `[unique=true]` marks the column as part of the lookup key
- **Blank lines** separate different header blocks
- Lines starting with `#` are comments

## Header Modes

| Mode | Behavior |
|---|---|
| `INSERT` | Create new items. Fails if item already exists. |
| `UPDATE` | Modify existing items. Fails if item doesn't exist. |
| `INSERT_UPDATE` | Create or update. Most commonly used. |
| `REMOVE` | Delete matching items. |

## Type References and Translations

### Referencing related types

```impex
# Reference by unique attribute(s)
INSERT_UPDATE Product ; code[unique=true] ; unit(code)   ; catalogVersion(catalog(id),version)
                      ; HAMMER-001        ; pieces       ; myProductCatalog:Staged
```

Parentheses `()` denote navigation into a related type. `unit(code)` means "find the Unit whose `code` matches this value."

### Multi-level references

```impex
; catalogVersion(catalog(id), version)
```

This means: "find the CatalogVersion where catalog.id = X AND version = Y"

### Localized attributes

```impex
# Use [lang=xx] for localized strings
INSERT_UPDATE Product ; code[unique=true] ; name[lang=en]     ; name[lang=de]     ; description[lang=en]
                      ; HAMMER-001        ; Claw Hammer        ; Klauenhammer       ; A sturdy claw hammer
```

### Boolean values

```impex
; active[default=true]
```

## Macros

Macros reduce repetition:

```impex
$productCatalog=myProductCatalog
$catalogVersion=catalogVersion(catalog(id[default=$productCatalog]),version[default='Staged'])[unique=true,default=$productCatalog:Staged]
$lang=en

INSERT_UPDATE Product ; code[unique=true] ; $catalogVersion ; name[lang=$lang]
                      ; HAMMER-001        ;                 ; Claw Hammer
                      ; WRENCH-001        ;                 ; Adjustable Wrench
```

Macros are text substitution — they're replaced before parsing. Define them with `$name=value`.

### Default values in macros

```impex
$currency=currency(isocode)[default='USD']

INSERT_UPDATE PriceRow ; productId[unique=true] ; $currency ; price
                       ; HAMMER-001             ;           ; 29.99
```

When a column is left blank, the `[default=...]` value is used.

## Special Translators

### SolrIndexedProperty translator

```impex
$solrIndexedType=myIndex
INSERT_UPDATE SolrIndexedProperty ; solrIndexedType(identifier)[unique=true] ; name[unique=true] ; type(code)
                                  ; $solrIndexedType                          ; name               ; text
```

### Content slot translator

```impex
INSERT_UPDATE ContentSlotForPage ; uid[unique=true]       ; position[unique=true] ; page(uid,$contentCV)[unique=true] ; contentSlot(uid,$contentCV)
                                 ; Section1-ProductPage   ; Section1              ; productDetails                     ; Section1Slot-ProductPage
```

## Catalog Versioning

Most commerce data is catalog-versioned (Staged→Online sync):

```impex
$productCatalog=myProductCatalog
$catalogVersionStaged=catalogVersion(catalog(id[default=$productCatalog]),version[default='Staged'])[unique=true,default=$productCatalog:Staged]
$catalogVersionOnline=catalogVersion(catalog(id[default=$productCatalog]),version[default='Online'])[unique=true,default=$productCatalog:Online]

# Import to Staged
INSERT_UPDATE Product ; code[unique=true] ; $catalogVersionStaged ; name[lang=en]
                      ; HAMMER-001        ;                       ; Claw Hammer

# Sync to Online happens via catalog sync job, not ImpEx
```

## Catalog-Versioned vs Global Types

Not all SAP Commerce types participate in catalog versioning. A type is catalog-version-aware only if it has the `catalogItemType = java.lang.Boolean.TRUE` custom property in its `items.xml` definition, which gives it a `catalogVersion` attribute. These types exist once per catalog version (e.g. the same product code can appear in both Staged and Online as separate items).

### Verified Sources

These findings are verified against three independent sources:
1. **items.xml definitions** — `catalogItemType` custom property in platform/extension items.xml files
2. **Accelerator ImpEx patterns** — How the SAP-provided accelerator imports data (yacceleratorinitialdata, electronicsstore)
3. **Live database schema** — FlexibleSearch `SELECT * FROM {Type} WHERE 1=0` confirms which columns exist

### Catalog-Versioned Types (have `P_CATALOGVERSION` column)

| Type | Root Sync Type | Sync Order | items.xml Source | Notes |
|------|---------------|------------|------------------|-------|
| **Category** | Yes | 1 | `catalog-items.xml:1167` | Synced first — products reference categories |
| **Product** | Yes | 2 | `catalog-items.xml:1285` | Core catalog item, synced second |
| **Media** | Yes | 3 | `catalog-items.xml:1524` | Images, documents, synced third |
| **Keyword** | Yes | 4 | `catalog-items.xml:733` | Search keywords |
| **TaxRow** | Yes | 4 | `europe1-items.xml:364` | Tax rate assignments per product/group |
| **PriceRow** | Yes | 5 | `europe1-items.xml:261` | Product prices |
| **DiscountRow** | Yes | 7 | `europe1-items.xml:190` | Product discounts |
| **MediaContainer** | Yes | 8 | `catalog-items.xml:1585` | Groups responsive images |
| **CMSItem** (abstract) | No (CMS sync) | N/A | `cms2-items.xml:376` | Base for all CMS types (pages, components, slots) |
| **CMSRelation** | No (CMS sync) | N/A | `cms2-items.xml:421` | Slot-to-page/template mappings |

**Key details:**
- Product, Media, and MediaContainer are originally defined in `core-items.xml` WITHOUT catalog awareness. The `catalog` extension reopens these definitions with `autocreate="false" generate="false"` and adds the `catalogItemType` property + `catalogVersion` attribute
- CMS types live in a separate **Content Catalog** (not the Product Catalog) and have their own Staged/Online versions and sync mechanism
- `CatalogUnawareMedia` extends Media but explicitly sets `catalogItemType = FALSE` — use for system media that shouldn't be versioned (logos, email templates)

### Global Types (NO `P_CATALOGVERSION` column)

| Type | items.xml Source | Why Not Versioned |
|------|------------------|-------------------|
| **StockLevel** | `basecommerce-items.xml:1012` | Real-time warehouse data. Uses `productCode` string, not a Product reference. Inventory is operational, not content. |
| **BaseStore** | `basecommerce-items.xml:1189` | Site infrastructure — one store config shared across all catalog versions |
| **BaseSite** | `basecommerce-items.xml:1849` | Site infrastructure |
| **Warehouse** | `basecommerce-items.xml:936` | Physical location, not content |
| **Order** | `core-items.xml:2474` | Transactional data — orders snapshot product data at time of purchase |
| **Cart** | `core-items.xml:2455` | Transactional data |
| **Customer** | `core-items.xml:1793` | User data |
| **Address** | `core-items.xml:1911` | User data |
| **Currency** | `core-items.xml:3206` | Reference data (C2LItem) |
| **Country** | `core-items.xml:3187` | Reference data (C2LItem) |
| **Language** | `core-items.xml:3165` | Reference data (C2LItem) |
| **Unit** | `core-items.xml:3550` | Reference data |
| **DeliveryMode** | `core-items.xml:2801` | Configuration |
| **ZoneDeliveryMode** | `core-items.xml` (extends DeliveryMode) | Configuration |
| **PaymentMode** | `core-items.xml:2868` | Configuration |
| **Tax** | (type itself) | Only TaxRow is versioned, not the Tax definition |
| **Discount** | (type itself) | Only DiscountRow is versioned |
| **Promotions / PromotionRule** | `promotions-items.xml` | Global — references products by code, not catalog-versioned reference |
| **CustomerReview** | `customerreview-items.xml` | User-generated content |

### Verification Queries

Run these in HAC FlexibleSearch console to verify:

```sql
-- Show all catalog versions
SELECT {c.id}, {cv.version}, {cv.active}
FROM {CatalogVersion AS cv JOIN Catalog AS c ON {cv.catalog}={c.pk}}

-- Products per catalog version
SELECT {p.code}, {cv.version}
FROM {Product AS p JOIN CatalogVersion AS cv ON {p.catalogVersion}={cv.pk}}
ORDER BY {p.code}, {cv.version}

-- Confirm StockLevel has NO catalogVersion (this will ERROR)
SELECT {s.productCode}, {s.catalogVersion} FROM {StockLevel AS s}
-- ERROR: cannot search unknown field 'catalogVersion' within type StockLevel

-- Describe any type's columns
SELECT * FROM {Product} WHERE 1=0
-- Returns column headers only — look for P_CATALOGVERSION
```

## Catalog Synchronization

### How It Works

Synchronization copies items from a **source** catalog version (Staged) to a **target** catalog version (Online) using a `CatalogVersionSyncJob`.

Two mechanisms include types in sync:

1. **Root types** — Independently synchronized in a defined order (Category at 1, Product at 2, Media at 3, etc.). The sync job iterates each root type in order.
2. **Part-of (copy by value)** — Types synchronized as children of their parent. When a Product syncs, its PriceRows, TaxRows, and DiscountRows come along. Configured via `SyncAttributeDescriptorConfig`.

### The Standard Accelerator Pattern

The SAP-provided accelerator (yacceleratorinitialdata) follows this flow:

1. **All data imports go to Staged** — Every ImpEx file uses `$catalogVersion` defaulting to `'Staged'`
2. **Sync jobs are created** — Either via ImpEx or programmatically via `DefaultSetupSyncJobService`
3. **Sync is executed** — Copies Staged → Online, creating new items and removing missing items

```impex
# The standard macro — always points to Staged
$catalogVersion=catalogversion(catalog(id[default=$productCatalog]),version[default='Staged'])[unique=true,default=$productCatalog:Staged]

# All products, categories, media, prices imported to Staged
INSERT_UPDATE Product ; code[unique=true] ; $catalogVersion ; name[lang=en]
                      ; HAMMER-001        ;                 ; Claw Hammer

# Categories also Staged
INSERT_UPDATE Category ; code[unique=true] ; $catalogVersion
                       ; power-tools       ;

# Media also Staged
INSERT_UPDATE Media ; code[unique=true] ; $catalogVersion ; mime
                    ; hammer-001.jpg    ;                 ; image/jpeg

# TaxRow also Staged
INSERT_UPDATE TaxRow ; $catalogVersion ; tax(code)[unique=true] ; pg(code)[unique=true] ; ug(code)[unique=true]
                     ;                 ; us-sales-tax-full      ; us-sales-tax-full     ; us-taxes

# StockLevel is NOT Staged — no $catalogVersion column
INSERT_UPDATE StockLevel ; productCode[unique=true] ; warehouse(code)[unique=true] ; available
                         ; HAMMER-001               ; main-warehouse               ; 50
```

### Sync Job Configuration via ImpEx

```impex
$sourceProductCV=sourceVersion(catalog(id[default=$productCatalog]),version[default='Staged'])[unique=true,default='$productCatalog:Staged']
$targetProductCV=targetVersion(catalog(id[default=$productCatalog]),version[default='Online'])[unique=true,default='$productCatalog:Online']

INSERT_UPDATE CatalogVersionSyncJob ; code[unique=true]                          ; $sourceProductCV ; $targetProductCV
                                    ; sync myProductCatalog:Staged->Online       ;                  ;
```

Naming convention: `sync <catalogId>:Staged->Online`

### Sync Job Configuration via Java (Production Accelerator)

```java
// DefaultSetupSyncJobService.java
CatalogVersionSyncJobModel syncJob = getModelService().create(CatalogVersionSyncJobModel.class);
syncJob.setCode("sync " + catalogId + ":Staged->Online");
syncJob.setSourceVersion(getCatalogVersion(catalogId, "Staged"));
syncJob.setTargetVersion(getCatalogVersion(catalogId, "Online"));
syncJob.setCreateNewItems(Boolean.TRUE);
syncJob.setRemoveMissingItems(Boolean.TRUE);
getModelService().save(syncJob);
```

### Catalog Setup ImpEx Pattern

Every catalog must create both Staged and Online versions:

```impex
INSERT_UPDATE Catalog ; id[unique=true]
                      ; $productCatalog

INSERT_UPDATE CatalogVersion ; catalog(id)[unique=true] ; version[unique=true] ; active ; languages(isoCode) ; readPrincipals(uid)
                             ; $productCatalog          ; Staged               ; false  ; $languages          ; employeegroup
                             ; $productCatalog          ; Online               ; true   ; $languages          ; employeegroup
```

- **Staged** has `active=false` — editing version, not visible to customers
- **Online** has `active=true` — live version visible to customers
- Both grant `employeegroup` read access

### Workflow Summary

```
Content Manager edits in Staged
         ↓
    Review / Approve
         ↓
    Sync Staged → Online (CatalogVersionSyncJob)
         ↓
    Storefront reads Online only
```

This applies separately to:
- **Product Catalog** — Products, Categories, Media, Prices, Taxes, Discounts
- **Content Catalog** — CMS Pages, Components, Slots (separate catalog, separate sync job)

## Media Import

```impex
$siteResource=jar:com.company.core.setup.CoreSystemSetup&/coredata/import/images

# Import media files from the classpath
INSERT_UPDATE Media ; code[unique=true] ; $catalogVersion ; mime     ; realfilename   ; @media[translator=de.hybris.platform.impex.jalo.media.MediaDataTranslator]
                    ; hammer-001.jpg    ;                 ; image/jpeg ; hammer-001.jpg ; $siteResource/hammer-001.jpg
```

The `@media[translator=...]` column handles binary file import from the classpath or filesystem.

## Conditional and Scripted ImpEx

### if/endif blocks

```impex
#% if: de.hybris.platform.jalo.extension.ExtensionManager.getInstance().isExtensionInstalled("myextension")
INSERT_UPDATE MyType ; code[unique=true] ; name
                     ; ITEM-001          ; Conditional Item
#% endif:
```

### BeanShell scripting

```impex
#% impex.setLocale( new Locale( "en" , "US" ) );
```

### Groovy scripting

```impex
#% groovy:
   def ctx = spring.getBean('modelService')
   println "ModelService: ${ctx}"
#% endgroovy
```

## Common Patterns

**This project's naming convention:** data files carry numeric load-order
prefixes — `essentialdata-NN-*.impex`, `projectdata-NN-*.impex` (e.g.
`essentialdata-00-infrastructure.impex`, `projectdata-40-categories.impex`) —
because the platform imports alphabetically and order matters. Follow it for
new files (ADR 0006). Remember `projectdata-*` loads on **initialize only**;
to load a new file onto an existing database use `./gradlew impex -Pfile=...`.

### Essential data (runs on initialize AND updatesystem)

File: `resources/impex/essentialdata-myextension.impex`
```impex
# Enums, essential config, always needed
INSERT_UPDATE ToolCategoryEnum ; code[unique=true] ; name[lang=en]
                               ; HAND_TOOL          ; Hand Tool
                               ; POWER_TOOL         ; Power Tool
```

### Project data (runs on initialize only)

File: `resources/impex/projectdata-myextension.impex`
```impex
# Sample data, site config, loaded once
INSERT_UPDATE BaseSite ; uid[unique=true] ; stores(uid) ; ...
```

### Sample data via system setup

```java
@SystemSetup(extension = "myextension")
public class MySystemSetup extends AbstractSystemSetup {

  @SystemSetup(type = Type.PROJECT, process = Process.ALL)
  public void createProjectData(SystemSetupContext context) {
    importImpexFile(context, "/myextension/import/sampledata.impex", true);
  }
}
```

### Removing items

```impex
REMOVE Product ; code[unique=true] ; catalogVersion(catalog(id),version)[unique=true]
               ; OLD-PRODUCT-001   ; myProductCatalog:Staged
```

## Debugging ImpEx

### In HAC (hybris Administration Console)
1. Go to Console → ImpEx Import
2. Paste your ImpEx
3. Check "Enable code execution" if using scripting
4. Check "Legacy mode" only if needed for Jalo-layer imports

### Common errors

| Error | Cause | Fix |
|---|---|---|
| `unknown attribute` | Column name doesn't match any attribute | Check items.xml for the correct qualifier |
| `ambiguous unique keys` | Multiple items match the unique key | Add more `[unique=true]` columns |
| `cannot resolve reference` | Referenced item doesn't exist | Import dependencies first, or use INSERT_UPDATE |
| `no matching catalogversion` | Catalog/version not found | Ensure catalogs are created before products |
| `mandatory attribute missing` | Required attribute not provided | Add the column or set a `[default=...]` |

### Import order matters — Store Data Dependency Chain

Setting up a functioning B2C store requires importing data in a specific order because most types reference others. Below is the complete dependency chain, from foundational types up through transactional data. Each level depends on the levels above it.

#### Level 1 — Foundation Types (no dependencies)

| Type | Purpose | Key Attributes |
|---|---|---|
| `OAuthClientDetails` | API authentication clients | `clientId`, `scope`, `authorizedGrantTypes`, `clientSecret` |
| `Language` | Available languages | `isocode`, `active` |
| `Currency` | Available currencies | `isocode`, `active`, `conversion`, `digits`, `symbol` |
| `Country` | Available countries | `isocode`, `name`, `active` |
| `Unit` | Units of measure (pieces, kg, etc.) | `unitType`, `code`, `name`, `conversion` |
| `UserGroup` | Groups like `customergroup` | `uid` |

```impex
INSERT_UPDATE Language ; isocode[unique=true] ; active
                       ; en                   ; true

INSERT_UPDATE Currency ; isocode[unique=true] ; active ; conversion ; digits ; symbol
                       ; USD                  ; true   ; 1          ; 2      ; $

INSERT_UPDATE Country ; isocode[unique=true] ; name[lang=en]   ; active
                      ; US                   ; United States   ; true

INSERT_UPDATE Unit ; unitType[unique=true] ; code[unique=true] ; name[lang=en] ; conversion
                   ; pieces                ; pieces             ; Pieces        ; 1
```

#### Level 2 — Catalog Structure (depends on: Language)

| Type | Purpose | Dependencies |
|---|---|---|
| `Catalog` | Product catalog container | — |
| `CatalogVersion` | Staged/Online versions | Catalog, Language |

```impex
INSERT_UPDATE Catalog ; id[unique=true]           ; name[lang=en]
                      ; electronicsProductCatalog ; Electronics Product Catalog

INSERT_UPDATE CatalogVersion ; catalog(id)[unique=true]  ; version[unique=true] ; active ; languages(isocode)
                             ; electronicsProductCatalog ; Staged               ; false  ; en
                             ; electronicsProductCatalog ; Online               ; true   ; en
```

#### Level 3 — Warehouse Infrastructure (standalone, then Vendor)

| Type | Purpose | Dependencies |
|---|---|---|
| `Vendor` | Supplier/vendor entity | — |
| `Warehouse` | Stock storage location | Vendor |

```impex
INSERT_UPDATE Vendor ; code[unique=true] ; name[lang=en]
                     ; electronics-vendor ; Electronics Vendor

INSERT_UPDATE Warehouse ; code[unique=true]   ; name[lang=en]       ; vendor(code)       ; default
                        ; electronics-warehouse ; Electronics Warehouse ; electronics-vendor ; true
```

#### Level 4 — Tax Configuration (depends on: CatalogVersion)

| Type | Purpose | Dependencies |
|---|---|---|
| `UserTaxGroup` | Tax group for customers | — |
| `ProductTaxGroup` | Tax group for products | — |
| `Tax` | Tax rate definition | — |
| `TaxRow` | Links Tax + ProductTaxGroup + UserTaxGroup | Tax, ProductTaxGroup, UserTaxGroup, CatalogVersion |

```impex
INSERT_UPDATE UserTaxGroup ; code[unique=true]
                           ; us-taxes

INSERT_UPDATE ProductTaxGroup ; code[unique=true]
                              ; us-sales-tax-full

INSERT_UPDATE Tax ; code[unique=true]   ; value ; currency(isocode)
                  ; us-sales-tax-full   ; 0

INSERT_UPDATE TaxRow ; $catalogVersion ; tax(code)[unique=true] ; pg(code)[unique=true] ; ug(code)[unique=true]
                     ;                 ; us-sales-tax-full      ; us-sales-tax-full     ; us-taxes
```

#### Level 5 — Delivery & Payment (depends on: Country, Currency)

| Type | Purpose | Dependencies |
|---|---|---|
| `Zone` | Geographic delivery zone | Country |
| `ZoneDeliveryMode` | Delivery method definition | — |
| `ZoneDeliveryModeValue` | Delivery cost per zone | ZoneDeliveryMode, Zone, Currency |
| `StandardPaymentMode` | Payment method | — |
| `StandardPaymentModeValue` | Payment cost | PaymentMode, Currency |

```impex
INSERT_UPDATE Zone ; code[unique=true] ; countries(isocode)
                   ; us                ; US

INSERT_UPDATE ZoneDeliveryMode ; code[unique=true] ; name[lang=en]     ; active ; net
                               ; standard-net      ; Standard Delivery ; true   ; false
                               ; express-net       ; Express Delivery  ; true   ; false

INSERT_UPDATE ZoneDeliveryModeValue ; deliveryMode(code)[unique=true] ; zone(code)[unique=true] ; currency(isocode)[unique=true] ; value ; minimum[unique=true]
                                    ; standard-net                    ; us                      ; USD                            ; 0.00  ; 0.00
                                    ; express-net                     ; us                      ; USD                            ; 9.99  ; 0.00

INSERT_UPDATE StandardPaymentMode ; code[unique=true] ; name[lang=en] ; active ; paymentinfotype(code)
                                  ; advance           ; Advance       ; true   ; AdvancePaymentInfo

INSERT_UPDATE StandardPaymentModeValue ; paymentMode(code)[unique=true] ; value ; currency(isocode)[unique=true]
                                       ; advance                        ; 0.00  ; USD
```

#### Level 6 — BaseStore (depends on: Catalog, Currency, Language, UserTaxGroup, Warehouse, Country, DeliveryMode)

The BaseStore ties everything together. It must be created after all its dependencies, then updated to add delivery countries and modes.

```impex
INSERT_UPDATE BaseStore ; uid[unique=true] ; catalogs(id)              ; currencies(isocode) ; defaultCurrency(isocode) ; defaultLanguage(isocode) ; languages(isocode) ; net   ; taxGroup(code) ; warehouses(code)      ; paymentProvider
                        ; electronics      ; electronicsProductCatalog ; USD                 ; USD                      ; en                       ; en                 ; false ; us-taxes       ; electronics-warehouse ; Mockup

UPDATE BaseStore ; uid[unique=true] ; deliveryCountries(isocode)
                 ; electronics      ; US

UPDATE BaseStore ; uid[unique=true] ; deliveryModes(code)
                 ; electronics      ; standard-net,express-net
```

#### Level 7 — BaseSite (depends on: BaseStore, Language)

```impex
INSERT_UPDATE BaseSite ; uid[unique=true] ; stores(uid) ; defaultLanguage(isocode) ; channel(code)
                       ; electronics      ; electronics ; en                       ; B2C
```

#### Level 8 — Products (depends on: CatalogVersion, Unit, ProductTaxGroup)

```impex
$catalogVersion = catalogVersion(catalog(id[default=electronicsProductCatalog]),version[default='Online'])
$taxGroup = Europe1PriceFactory_PTG(code)[default=us-sales-tax-full]

INSERT_UPDATE Product ; code[unique=true] ; name[lang=en]   ; description[lang=en] ; $catalogVersion ; unit(code)[default='pieces'] ; approvalStatus(code)[default='approved'] ; $taxGroup
                      ; LAPTOP_PRO_15     ; Laptop Pro 15   ; High-performance...  ;                 ;                              ;                                          ;
```

#### Level 9 — Pricing (depends on: Product, Unit, Currency, CatalogVersion)

```impex
INSERT_UPDATE PriceRow ; productId[unique=true] ; unit(code)[default='pieces'] ; currency(isocode)[unique=true] ; price   ; minqtd ; net   ; $catalogVersion
                       ; LAPTOP_PRO_15          ;                              ; USD                            ; 1299.99 ; 1      ; false ;
```

#### Level 10 — Stock (depends on: Product, Warehouse, CatalogVersion)

Two steps: create StockLevel records, then link them to Products.

```impex
INSERT_UPDATE StockLevel ; productCode[unique=true] ; warehouse(code)[unique=true] ; available ; inStockStatus(code)
                         ; LAPTOP_PRO_15            ; electronics-warehouse        ; 25        ; notSpecified

UPDATE Product ; code[unique=true] ; $catalogVersion ; stockLevels(productCode,warehouse(code))[mode=append]
               ; LAPTOP_PRO_15    ;                 ; LAPTOP_PRO_15:electronics-warehouse
```

#### Level 11 — Customers & Addresses (depends on: UserGroup, Language, Currency, Country)

```impex
INSERT_UPDATE Customer ; uid[unique=true]       ; name     ; groups(uid)   ; password[default=1234] ; sessionLanguage(isocode) ; sessionCurrency(isocode) ; &userId
                       ; john@example.com       ; John Doe ; customergroup ;                        ; en                       ; USD                      ; johnDoe

INSERT_UPDATE Address ; &addId      ; owner(&userId)[unique=true] ; streetname[unique=true] ; postalcode[unique=true] ; duplicate[unique=true] ; town     ; country(isocode) ; shippingAddress ; firstname ; lastname
                      ; johnAddr    ; johnDoe                     ; 100 Main St             ; 10001                  ; false                  ; New York ; US               ; true            ; John      ; Doe

UPDATE Customer ; uid[unique=true] ; defaultShipmentAddress(&addId) ; defaultPaymentAddress(&addId)
                ; john@example.com ; johnAddr                       ; johnAddr
```

#### Level 12 — Orders (depends on: Customer, Currency, PaymentMode, DeliveryMode, BaseSite, BaseStore, Product, CatalogVersion)

Orders are the most complex — they depend on almost everything. Create the order first, then entries, then calculate.

```impex
# 1. Create the Order header
INSERT_UPDATE Order ; code[unique=true] ; user(uid)        ; date[dateformat=dd.MM.yyyy HH:mm] ; currency(isocode) ; net   ; paymentMode(code) ; deliveryMode(code) ; calculated ; site(uid)   ; store(uid)  ; status(code)
                    ; ORDER-001         ; john@example.com ; 15.01.2026 10:30                  ; USD               ; false ; advance           ; standard-net       ; false      ; electronics ; electronics ; COMPLETED
"#%   impex.getLastImportedItem().setDeliveryAddress(impex.getLastImportedItem().getUser().getDefaultDeliveryAddress());impex.getLastImportedItem().setPaymentAddress(impex.getLastImportedItem().getUser().getDefaultPaymentAddress());";

# 2. Add OrderEntries (line items)
INSERT_UPDATE OrderEntry ; order(code)[unique=true] ; entryNumber[unique=true] ; product(code,$catalogVersion) ; quantity ; unit(code)[default='pieces'] ; basePrice ; totalPrice
                         ; ORDER-001                ; 0                        ; LAPTOP_PRO_15                 ; 1        ;                              ; 1299.99   ; 1299.99

# 3. Trigger order calculation
UPDATE Order ; code[unique=true]
             ; ORDER-001
"#%   impex.getLastImportedItem().calculate();";
```

#### Complete Dependency Graph

```
Language ─────────────────────────────────────────┐
Currency ────────────────────────────┐             │
Country ──────────────┐              │             │
Unit ─────────────────┤              │             │
                      │              │             │
Vendor ───→ Warehouse─┤              │             │
                      │              │             │
UserTaxGroup ─────────┤              │             │
ProductTaxGroup ──────┤              │             │
Tax ──→ TaxRow ───────┤              │             │
                      │              │             │
Zone ─────────────────┤              │             │
ZoneDeliveryMode ─────┤              │             │
ZoneDeliveryModeValue─┤              │             │
PaymentMode ──────────┤              │             │
                      │              │             │
Catalog ──→ CatalogVersion ──────────┤             │
                      │              │             │
                      ▼              ▼             ▼
                BaseStore ──────→ BaseSite
                      │
                      ▼
          ┌─── Product ───┐
          │               │
       PriceRow      StockLevel
                          │
          ▼               ▼
    Customer ──→ Address
          │
          ▼
     Order ──→ OrderEntry ──→ Calculate
```
