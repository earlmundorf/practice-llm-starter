# ImpEx Expert Guidance

Use this guidance before generating, reviewing, linting, or importing ImpEx through HAC.

For any ImpEx involving BaseSite, BaseStore, catalogs, CMS, classification, inventory, warehouses, Solr, sync, or index jobs, also load [SAP Commerce object model expert map](./commerce-object-model.md) before generating or reviewing headers.

## Expert stance

ImpEx is executable data architecture. Treat it as part of the SAP Commerce model and extension initialization story, not as a flat CSV. Generate ImpEx from confirmed item types, attributes, relations, unique keys, catalog versions, and existing project conventions.

Before writing non-trivial ImpEx, inspect the project and runtime model:

- `*-items.xml` for type definitions, mandatory attributes, relations, and unique keys.
- Existing project ImpEx under extension resources, setup folders, and data-load directories.
- Java constants and setup services in `bin/custom` that define catalog IDs, base site IDs, store IDs, Solr config names, and enum codes.
- SAP-delivered examples in `bin/modules` when loading platform-owned structures such as Solr, CMS, sync jobs, or Backoffice config.
- Current database facts with FlexibleSearch before assuming a referenced item exists.

## Core ImpEx rules

- Prefer `INSERT_UPDATE` for idempotent local setup and repeatable imports.
- Use business keys, never hardcoded PKs.
- Mark identifying columns with `[unique=true]`.
- Use macros for repeated catalog versions, languages, currencies, base stores, sites, Solr configs, and content/product catalog IDs.
- Order data by dependency: enums/reference data, catalogs, catalog versions, classification, categories, products/content, relations, prices/stock/media, sync/index jobs.
- Keep headers narrow and purposeful. Avoid writing attributes that are not needed for the task.
- Use explicit catalog version references for catalog-aware types.
- Use relation headers for many-to-many links when the platform model defines a relation.
- Use localized attributes deliberately with language qualifiers, for example `name[lang=en]`.
- Avoid `mode=append` unless adding to a collection is truly intended and safe to rerun.
- Quote values that contain delimiters, leading/trailing spaces, line breaks, or special characters. Escape embedded quotes consistently.
- Be deliberate about clearing values. Empty cells, `<ignore>`, and explicit null markers have different behavior depending on header modifiers and translators.
- Treat collection columns as replacement by default. Use append/remove modes only when repeat imports cannot create duplicates or stale links.
- Specify `dateformat` and `numberformat` when importing dates or locale-sensitive numbers.
- Use translators and cell decorators only when the target type requires them; document why they are needed in the header or nearby context.
- Use `batchmode=true` only when duplicates are expected and intentionally handled. Do not use it to hide ambiguous unique-key design.

For implementation setup files, preserve the core dependency graph: catalogs and catalog versions first, then base stores, then CMS/base sites, then warehouses and stock, then Solr/sync/index jobs, then catalog-aware business data. A BaseSite row without its store, catalog, warehouse, and Solr relationships is not a usable implementation.

## Runtime validation before import

For every referenced business key that could be missing, run a cheap FlexibleSearch check before importing. Validate especially:

- Catalog and `CatalogVersion` rows.
- BaseSite and BaseStore identifiers.
- Product, category, CMS, and media container catalog versions.
- Enum values and classification system versions.
- Solr indexed type, Solr facet search config, and index CronJob codes.
- Custom item types and relation qualifiers from `AttributeDescriptor`.

Use type-system discovery when a header fails or when the exact qualifier is uncertain:

```sql
SELECT {ad.qualifier}, {ad.attributeType}, {ad.localized}, {ad.optional}, {ad.unique}
FROM {AttributeDescriptor AS ad JOIN ComposedType AS ct ON {ad.enclosingType} = {ct.pk}}
WHERE {ct.code} = '<TypeCode>'
ORDER BY {ad.qualifier}
```

## Catalog version pattern

Use macros for catalog-aware data:

```impex
$productCatalog=sample_ProductCatalog
$productCatalogVersion=catalogVersion(catalog(id),version)
$stagedProductCatalogVersion=$productCatalog:Staged

INSERT_UPDATE Product;code[unique=true];$productCatalogVersion[unique=true,default=$stagedProductCatalogVersion];name[lang=en]
;sample-code;;Sample Product
```

Keep the reference macro separate from column modifiers. Apply `[unique=true]`, `[default=...]`, or `[mode=...]` at the use site so the same macro can be reused for required keys, optional references, and relation columns without accidental uniqueness.

Only target `Online` directly when the business process requires online-only data. For product/content authoring data, prefer `Staged` plus catalog sync unless local validation specifically requires an online row.

## Reference and relation patterns

Use references that match the owning type's declared unique key:

```impex
INSERT_UPDATE Category;code[unique=true];$productCatalogVersion;name[lang=en]
;sample-category;;Sample Category

INSERT_UPDATE Product;code[unique=true];$productCatalogVersion;supercategories(code,$productCatalogVersion)
;sample-product;;sample-category
```

When an item participates in a named relation, confirm the relation attribute name from `items.xml` or `AttributeDescriptor` before choosing a collection column.

## Import strategy

- Run offline lint first when available.
- For local HAC import, state the target file and wait for approval.
- Import once, then validate with FlexibleSearch using the same business keys.
- If import fails, stop after reporting the first meaningful rejected row category: unresolved reference, missing type, unknown attribute, ambiguous unique key, mandatory attribute, catalog version, macro, syntax, translator, or disabled code execution.
- Do not generate a second import until the rejected-row cause is understood.

## Anti-patterns

- Hardcoded PK values.
- Environment-specific URLs or PROD-like data in local setup files.
- Re-import-sensitive headers without unique keys.
- Broad `REMOVE` blocks for diagnosis.
- ImpEx that assumes accelerator sample data exists without validating it.
- Mixing unrelated data domains in one file, such as BaseSite setup, CMS content, products, Solr, and test orders.

## Output expectations

When generating ImpEx, include:

- Intended import scope.
- Assumed catalog/site/store identifiers.
- Required pre-existing references.
- Validation FlexibleSearch query for the created or updated rows.

The generated file should be easy to rerun locally and easy to review in a diff.
