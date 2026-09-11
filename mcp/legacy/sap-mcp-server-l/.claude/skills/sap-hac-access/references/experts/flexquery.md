# FlexibleSearch Expert Guidance

Use this guidance before generating or running FlexibleSearch through HAC.

For any query involving BaseSite, BaseStore, catalogs, CMS, classification, inventory, warehouses, Solr, sync, or index jobs, also load [SAP Commerce object model expert map](./commerce-object-model.md) before generating the query.

## Expert stance

Act like a SAP Commerce engineer validating the real runtime, not a SQL author guessing table names. FlexibleSearch is an SQL-based search language over SAP Commerce item types: it lets you query a type in `FROM {TypeCode AS alias}` form, but the braces are type-system references that Commerce resolves to physical tables, columns, joins, and restrictions before the database sees SQL. Generate queries from confirmed platform metadata, project code, and current database facts.

Before writing a non-trivial query, prefer one cheap discovery step that can disprove the assumed model:

- Check `ComposedType` for the item type and extension owner.
- Check `AttributeDescriptor` for qualifier names, relation ownership, localized flags, and optionality.
- Check existing items by business key before joining deeper.
- Check `bin/custom` and `bin/modules` for constants, DAOs, services, impex, and `items.xml` that define the same type or relation.

## FlexibleSearch mental model

Treat each composed type as queryable like a table, but never as the table itself:

```sql
SELECT {p.pk}, {p.code}
FROM {Product AS p}
WHERE {p.code} = 'ABC'
```

This means:

- `FROM {Product AS p}` asks Commerce for items of type `Product`, usually including subtypes. Use `{Product! AS p}` only when the query must return exactly `Product` and exclude subtypes.
- `{p.code}`, `{p.pk}`, and `{p.catalogVersion}` are item attribute qualifiers. They are not guaranteed to be physical column names.
- FlexibleSearch translates brace sections first, then executes database SQL. Plain SQL functions and clauses can surround brace sections, but database-specific SQL can reduce portability.
- Joins are joins between type abstractions or resolved relation tables. Do not assume a collection attribute can be joined like a normal column until `items.xml`, `AttributeDescriptor`, or a working runtime query proves the owning relation and qualifier.
- Localized and property-backed attributes may require implicit property-table joins. The current session language matters unless a language PK is specified in the field expression.
- Ordering or filtering on property-backed attributes can change join behavior. Absence checks on localized/property values need deliberate outer-join modifiers and `IS NULL` handling; avoid them when a simpler key-based query can answer the question.
- Query results can be item PKs/models or raw scalar values depending on the selected columns and the ServiceLayer/HAC execution path.

For diagnostics, the practical rule is: if a type exists, start with a narrow `SELECT {alias.pk} FROM {Type AS alias}` probe, then add only confirmed qualifiers and joins. If the runtime says a qualifier is unknown, the next step is type-system discovery, not retrying with guessed column names.

## Session and restriction model

FlexibleSearch does not run in a vacuum. Session context can change both query translation and returned rows:

- Search restrictions may be persisted by principal/group or added dynamically to the current session. They are evaluated for the searched type unless restrictions are disabled or the current user has admin rights.
- HAC commonly runs as `admin`, so restriction-filtered storefront behavior may not reproduce in a plain HAC query. If visibility differs between HAC and storefront/OCC, check restrictions, user, site, catalog versions, language, and currency before concluding data is missing.
- Session attributes can be referenced by queries, such as `?session.user`, `?session.language`, and `?session.currency`. Generated Groovy/Java diagnostics should set session context deliberately when validating storefront behavior.
- Catalog-aware visibility is often session-driven. A product or CMS item can exist in the database and still be invisible because the active session catalog versions, site, sync state, approval/status, restrictions, or Solr index do not line up.
- Do not disable restrictions as a first step. First state whether the query is intended to answer “does the item exist at all?” or “would this user/session see it?”. Only bypass restrictions for an explicit existence/admin diagnostic, and report that choice.

## Evidence sources

Use the running database first when the task is validation or diagnosis. Use source code first when the task is to mirror an existing implementation.

Useful runtime probes:

```sql
SELECT {ct.code}, {ct.extensionName}
FROM {ComposedType AS ct}
WHERE {ct.code} = '<TypeCode>'
```

```sql
SELECT {ad.qualifier}, {ad.attributeType}, {ad.unique}, {ad.partOf}, {ad.collection}
FROM {AttributeDescriptor AS ad JOIN ComposedType AS ct ON {ad.enclosingType} = {ct.pk}}
WHERE {ct.code} = '<TypeCode>'
ORDER BY {ad.qualifier}
```

```sql
SELECT {ad.qualifier}, {ad.attributeType}, {ad.partOf}, {ad.collection}
FROM {AttributeDescriptor AS ad JOIN ComposedType AS ct ON {ad.enclosingType} = {ct.pk}}
WHERE {ct.code} = '<TypeCode>'
  AND ({ad.qualifier} LIKE '%<Name>%' OR {ad.attributeType} IS NOT NULL)
ORDER BY {ad.qualifier}
```

`AttributeDescriptor` metadata differs by runtime. If qualifiers such as `localized`, `optional`, `partOf`, or `collection` fail, rerun the descriptor query with only `{ad.qualifier}` and `{ad.attributeType}`, then consult `items.xml` or generated model constants for richer metadata.

Use `RelationDescriptor` only when the runtime confirms that type and its attributes are queryable. Relation metadata varies by platform version; `items.xml` and `AttributeDescriptor` are the more portable discovery surfaces.

When source context matters, search in these areas before inventing qualifiers or job names:

- `bin/custom/**/resources/**/*-items.xml`
- `bin/custom/**/resources/**/*-spring.xml`
- `bin/custom/**/*.java`
- `bin/custom/**/*.impex`
- `bin/modules/**/resources/**/*-items.xml`
- `bin/modules/**/*.java`
- project setup/import directories that seed local data

## Query design rules

- Use type and attribute qualifiers, not physical table or column names.
- Query item types directly when that answers the question: `SELECT {alias.pk} FROM {TypeCode AS alias}` is the correct starting point for “does this type have rows?”. Add joins only when a relationship must be proven.
- Remember type hierarchy behavior. `{TypeCode}` can include subtype instances; use `{TypeCode!}` only when exact-type semantics are intentional.
- Select `{pk}` when the next step needs models. Select business keys and diagnostic attributes when reporting facts to a human.
- Keep read-only diagnostic queries small and decisive. Always include `--max-count` when using the HAC script.
- Filter by catalog version when querying catalog-aware data such as products, categories, content pages, media containers, CMS components, and price rows tied to products.
- When checking storefront visibility, include the relevant site, catalog version, language, currency, approval/status, and restriction context rather than only checking physical existence.
- Resolve enum codes with `EnumerationValue` when status/result values would otherwise return PKs.
- Prefer explicit joins over post-query interpretation when validating wiring across BaseSite, BaseStore, Catalog, CatalogVersion, Solr, CronJob, or SyncJob.
- Use `LIKE` only for discovery. Once the exact key is known, switch to exact predicates.
- Avoid broad `SELECT {pk} FROM {Product}` style queries without a catalog version, identifier, and row limit.
- Do not assume attributes that vary by project, such as `BaseSite.defaultBaseStore`. Confirm via `AttributeDescriptor` first.
- Do not treat HAC/admin results as proof that a non-admin storefront user can see the same rows. Restrictions and session context may be the difference.
- When generating Groovy or Java, use `FlexibleSearchQuery` with named parameters. HAC console diagnostics may use literals only after values are confirmed and non-secret.
- Do not use `failOnUnknownFields=false` by default. Use it only as a deliberate last-resort diagnostic after checking the type system, and call out that qualifier validation has been disabled.
- Prefer exact type aliases and readable select aliases in complex joins so returned columns can be interpreted without guessing.

## Parameterized query pattern

When a FlexibleSearch query is generated inside Groovy or Java, parameterize it:

```groovy
def query = new FlexibleSearchQuery('SELECT {pk} FROM {Product} WHERE {code}=?code')
query.addQueryParameter('code', productCode)
def result = flexibleSearchService.search(query)
```

Do not concatenate user input, catalog IDs, site IDs, CronJob codes, product codes, or enum codes into ServiceLayer query strings.

## Catalog and store patterns

BaseSite, BaseStore, catalog, and Solr wiring differs across Commerce projects. Validate through actual relations and attributes instead of assuming accelerator defaults.

When the user asks for a site or store configuration, do not stop at `BaseSite.uid`. Walk the runtime chain: `BaseSite`/`CMSSite` -> `BaseStore` -> product/content/classification catalogs and versions -> warehouses/stock -> Solr config/indexed type -> sync/index jobs. Missing runtime behavior is often caused by a broken relation in that chain, not by the named item being absent.

For product/content data, prove the catalog version explicitly:

```sql
SELECT {c.id}, {cv.version}, {cv.active}
FROM {CatalogVersion AS cv JOIN Catalog AS c ON {cv.catalog} = {c.pk}}
WHERE {c.id} = '<catalogId>'
ORDER BY {cv.version}
```

For Solr, start from the indexed type and config:

```sql
SELECT {it.identifier}, {it.type}, {cfg.name}
FROM {SolrIndexedType AS it JOIN SolrFacetSearchConfig AS cfg ON {it.solrFacetSearchConfig} = {cfg.pk}}
WHERE {it.identifier} = '<indexedType>'
```

For CronJobs, resolve status/result codes:

```sql
SELECT {cj.code}, {status.code}, {result.code}, {cj.startTime}, {cj.endTime}
FROM {CronJob AS cj
  LEFT JOIN EnumerationValue AS status ON {cj.status} = {status.pk}
  LEFT JOIN EnumerationValue AS result ON {cj.result} = {result.pk}}
WHERE {cj.code} = '<cronJobCode>'
```

## Output expectations

When reporting query results, include:

- The hypothesis the query tested.
- The normalized HAC target.
- Row count.
- The key values returned.
- Whether zero rows confirms absence, indicates wrong assumptions, or requires type-system discovery.

Do not repeat a failed query with cosmetic changes. Use the error details to inspect the type system, then generate a corrected query.
