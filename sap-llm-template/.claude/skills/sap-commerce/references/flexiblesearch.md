# FlexibleSearch Reference

## Table of Contents
1. [Overview](#overview)
2. [Basic Syntax](#basic-syntax)
3. [Joins](#joins)
4. [Subqueries](#subqueries)
5. [Localized Attributes](#localized-attributes)
6. [Pagination](#pagination)
7. [Raw SQL Mode](#raw-sql-mode)
8. [Using FlexibleSearch in Java](#using-flexiblesearch-in-java)
9. [Performance Tips](#performance-tips)
10. [Common Query Patterns](#common-query-patterns)

---

## Overview

FlexibleSearch is SAP Commerce's query language. It looks like SQL but operates on the **type system** rather than raw database tables. Curly braces `{}` reference item types and attributes, which the engine resolves to actual table/column names.

FlexibleSearch queries go in DAO classes — never in services, facades, or controllers.

## Basic Syntax

```sql
SELECT {pk} FROM {Product} WHERE {code} = ?code
```

- `{Product}` — References the Product item type (resolved to its DB table)
- `{pk}` — The primary key (every item has one)
- `{code}` — An attribute on Product (resolved to the DB column)
- `?code` — A named parameter (bound in Java)

### SELECT specific attributes

```sql
SELECT {p.code}, {p.name}, {p.description}
FROM {Product AS p}
WHERE {p.approvalStatus} = ?status
```

### Aliases

```sql
SELECT {p.pk} FROM {Product AS p} WHERE {p.code} LIKE ?pattern
```

### Ordering

```sql
SELECT {pk} FROM {Product}
WHERE {catalogVersion} = ?cv
ORDER BY {name} ASC
```

Pagination (start/count) is handled in Java, not in the query.

## Joins

### Implicit joins (via relations)

```sql
SELECT {p.pk}
FROM {Product AS p
  JOIN CatalogVersion AS cv ON {p.catalogVersion} = {cv.pk}
  JOIN Catalog AS c ON {cv.catalog} = {c.pk}}
WHERE {c.id} = ?catalogId AND {cv.version} = ?version
```

Everything inside `{}` is part of the FROM clause's type resolution.

### Join with subtypes

```sql
SELECT {p.pk}
FROM {Product AS p}
WHERE {p.code} LIKE ?pattern
```

This returns all Products **including subtypes** (e.g., ToolProduct extends Product). To exclude subtypes:

```sql
SELECT {pk} FROM {Product!} WHERE ...
```

The `!` suffix restricts to the exact type.

### LEFT JOIN

```sql
SELECT {p.pk}, {m.pk}
FROM {Product AS p
  LEFT JOIN Media AS m ON {p.picture} = {m.pk}}
WHERE {p.catalogVersion} = ?cv
```

## Subqueries

```sql
SELECT {pk} FROM {Product}
WHERE {pk} NOT IN (
  {{ SELECT {product} FROM {StockLevel} WHERE {available} > 0 }}
)
```

Subqueries use double curly braces `{{ }}`.

## Localized Attributes

```sql
-- Search localized attributes (uses the session language by default)
SELECT {pk} FROM {Product} WHERE {name} LIKE ?searchTerm

-- Specify a language explicitly
SELECT {pk} FROM {Product} WHERE {name[en]} LIKE ?searchTerm

-- Multiple languages
SELECT {pk}, {name[en]}, {name[de]} FROM {Product}
```

## Pagination

FlexibleSearch supports result-set pagination via the Java API:

```java
FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Product}");
query.setStart(0);       // offset
query.setCount(20);      // page size
query.setNeedTotal(true); // get total count for UI pagination
```

## Raw SQL Mode

For complex queries that FlexibleSearch can't express:

```java
FlexibleSearchQuery query = new FlexibleSearchQuery("...");
query.setFlexibleSearchMode(FlexibleSearchMode.DIRECT_SQL);
```

Use sparingly — raw SQL bypasses type system safety and is DB-vendor-specific.

## Using FlexibleSearch in Java

### In a DAO (recommended pattern)

```java
public class DefaultToolProductDao implements ToolProductDao {

  private static final String FIND_BY_CATEGORY =
      "SELECT {pk} FROM {ToolProduct} WHERE {toolCategory} = ?category AND {catalogVersion} = ?cv";

  @Autowired
  private FlexibleSearchService flexibleSearchService;

  @Override
  public List<ToolProductModel> findByCategory(ToolCategoryEnum category, CatalogVersionModel cv) {
    FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_BY_CATEGORY);
    query.addQueryParameter("category", category);
    query.addQueryParameter("cv", cv);

    SearchResult<ToolProductModel> result = flexibleSearchService.search(query);
    return result.getResult();
  }
}
```

### Query parameter types

```java
// String, Integer, etc. — bound directly
query.addQueryParameter("code", "HAMMER-001");

// Model references — use the model object, not the PK
query.addQueryParameter("cv", catalogVersionModel);

// Enum values — use the enum instance
query.addQueryParameter("category", ToolCategoryEnum.HAND_TOOL);

// Collections (for IN clauses)
query.addQueryParameter("codes", Arrays.asList("HAMMER-001", "WRENCH-001"));
```

### Returning raw data (not models)

```java
FlexibleSearchQuery query = new FlexibleSearchQuery(
    "SELECT {code}, {name} FROM {Product} WHERE {catalogVersion} = ?cv");
query.addQueryParameter("cv", cv);
query.setResultClassList(Arrays.asList(String.class, String.class));

SearchResult<List> result = flexibleSearchService.search(query);
for (List row : result.getResult()) {
    String code = (String) row.get(0);
    String name = (String) row.get(1);
}
```

### Count queries

```java
FlexibleSearchQuery query = new FlexibleSearchQuery(
    "SELECT COUNT({pk}) FROM {Product} WHERE {catalogVersion} = ?cv");
query.addQueryParameter("cv", cv);
query.setResultClassList(Collections.singletonList(Integer.class));

SearchResult<Integer> result = flexibleSearchService.search(query);
int count = result.getResult().get(0);
```

## Performance Tips

1. **Always use parameters** — Never concatenate values into query strings. Parameters enable query plan caching and prevent SQL injection.

2. **Select only {pk} when possible** — Selecting `{pk}` returns Model proxies that lazy-load attributes. Selecting multiple attributes forces immediate materialization.

3. **Use pagination** — Never load unbounded result sets. Use `setStart()` and `setCount()`.

4. **Index your WHERE clause attributes** — If you're frequently querying by a custom attribute, add an index in items.xml.

5. **Avoid N+1 queries** — If you load 1000 products and then call `product.getUnit()` on each, that's 1000 extra DB queries. Consider pre-fetching or joining.

6. **Use `!` for exact type matching** — `{Product!}` is faster than `{Product}` when you don't need subtypes, because it queries a single table.

7. **Cache results where appropriate** — Use `FlexibleSearchQuery.setCacheable(true)`.

## Common Query Patterns

### Find by code and catalog version

```sql
SELECT {pk} FROM {Product}
WHERE {code} = ?code
  AND {catalogVersion} = ?catalogVersion
```

### Search with LIKE

```sql
SELECT {pk} FROM {Product}
WHERE LOWER({name}) LIKE CONCAT('%', LOWER(?searchTerm), '%')
ORDER BY {name}
```

### Find items in a date range

```sql
SELECT {pk} FROM {Order}
WHERE {date} >= ?startDate AND {date} <= ?endDate
ORDER BY {date} DESC
```

### Find items with IN clause

```sql
SELECT {pk} FROM {Product}
WHERE {code} IN (?codes)
  AND {catalogVersion} = ?cv
```

### Aggregate queries

```sql
SELECT {category}, COUNT({pk})
FROM {Product}
WHERE {catalogVersion} = ?cv
GROUP BY {category}
ORDER BY COUNT({pk}) DESC
```

### EXISTS pattern

```sql
SELECT {p.pk} FROM {Product AS p}
WHERE EXISTS (
  {{ SELECT 1 FROM {StockLevel AS s}
     WHERE {s.productCode} = {p.code}
       AND {s.available} > 0 }}
)
```
