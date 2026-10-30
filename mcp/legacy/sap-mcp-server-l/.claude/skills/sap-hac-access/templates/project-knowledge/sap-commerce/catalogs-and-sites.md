# Catalogs, sites, and stores

> Seeded by the `sap-hac-access` skill. Confirm each row with a FlexibleSearch query before
> relying on it, and record the query used.

## Catalogs

| Catalog ID | Kind (product/content/classification) | Notes |
|---|---|---|
| TBD | | |

## Catalog versions

| Catalog ID | Version | Active | Notes |
|---|---|---|---|
| TBD | | | |

Verified with:

```
./scripts/hac-flexquery.sh --content "SELECT {cv.pk}, {c.id}, {cv.version}, {cv.active} FROM {CatalogVersion AS cv JOIN Catalog AS c ON {cv.catalog} = {c.pk}}" --max-count 50
```

## BaseSites and BaseStores

| BaseSite UID | BaseStore UID(s) | Product catalog(s) | Content catalog | Channel |
|---|---|---|---|---|
| TBD | | | | |

## Sync jobs

| Job code | Source → target | Notes |
|---|---|---|
| TBD | | |

## Gotchas

- TBD
