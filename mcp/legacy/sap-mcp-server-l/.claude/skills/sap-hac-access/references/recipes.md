# HAC Recipes

For investigative tasks, start with [Commerce diagnostics](./commerce-diagnostics.md) and choose the first query by hypothesis before using these repeatable recipes.

## Author a HAC artifact

Use this recipe when the user asks to write, draft, generate, review, or improve a FlexibleSearch query, HAC Groovy script, or ImpEx file, even if they are not ready to run it.

1. Load the matching expert guidance: FlexibleSearch, HAC Groovy, ImpEx, and the Commerce object model when site/store/catalog/Solr/inventory/CMS relationships are involved.
2. Identify whether the artifact is read-only, validation-only, or mutating. Treat unclear intent as read-only.
3. Inspect runtime metadata, project source, or existing ImpEx before inventing type names, qualifiers, bean names, relation names, or business keys.
4. Generate the smallest artifact that answers the user's goal.
5. Include the exact validation command or query separately from any write/import command.
6. Do not run imports, committed Groovy, sync, indexing, or any other server write without explicit approval.

Authoring defaults:

- FlexibleSearch: use type-system qualifiers, exact catalog versions where relevant, and a row limit.
- HAC Groovy: use ServiceLayer beans, parameterized `FlexibleSearchQuery`, rollback-safe behavior, and copied model collections before sorting/filtering.
- ImpEx: use `INSERT_UPDATE`, macros, stable business keys, dependency order, and post-import FlexibleSearch checks.

## Import an ImpEx file

Use only after explicit user approval.

1. Lint the ImpEx first when the offline linter is available.
2. Explain which file will be imported and confirm it targets local HAC only.
3. Run:

`./scripts/hac-impex.sh <file.impex>`

1. Treat anything other than `OK: ImpEx import successful` as failed.
2. If successful, run targeted FlexibleSearch validation.

## Validate catalog and catalog versions

Example query for catalog presence:

`./scripts/hac-flexquery.sh "SELECT {pk}, {id} FROM {Catalog} WHERE {id} IN ('sample_ProductCatalog','sample_ContentCatalog')" 10`

Example query for catalog versions:

`./scripts/hac-flexquery.sh "SELECT {cv.pk}, {c.id}, {cv.version}, {cv.active} FROM {CatalogVersion AS cv JOIN Catalog AS c ON {cv.catalog} = {c.pk}} WHERE {c.id} IN ('sample_ProductCatalog','sample_ContentCatalog')" 20`

Expected values depend on the artifact, but for standard catalog foundations:

- `Staged` should usually be inactive (`active = 0` / false).
- `Online` should usually be active (`active = 1` / true).

## Validate classification system version

`./scripts/hac-flexquery.sh "SELECT {csv.pk}, {cs.id}, {csv.version}, {csv.active} FROM {ClassificationSystemVersion AS csv JOIN ClassificationSystem AS cs ON {csv.catalog} = {cs.pk}} WHERE {cs.id} = 'sample_Classification' AND {csv.version} = '1.0'" 10`

## Validate catalog sync job

`./scripts/hac-flexquery.sh "SELECT {pk}, {code}, {removeMissingItems}, {createNewItems} FROM {CatalogVersionSyncJob} WHERE {code} = 'sync sample_ContentCatalog:Staged->sample_ContentCatalog:Online'" 10`

## Run FlexibleSearch from a file

Use when a query is long or needs to be kept as evidence.

`./scripts/hac-flexquery.sh /path/to/query.sql 50`

## Run Groovy in rollback mode

Default mode should be rollback/read-only style unless a committed mutation is explicitly approved.

`./scripts/hac-groovy.sh /path/to/script.groovy`

## Run Groovy with commit

Use only after explicit approval.

`./scripts/hac-groovy.sh /path/to/script.groovy --commit`

## QRSPI IMPEX_IMPORT checkpoint

When `working-docs/config.json` maps a `.impex` change to `IMPEX_IMPORT`:

1. Run offline lint first.
2. Ask for approval for live local import.
3. Use `./scripts/hac-impex.sh`, not raw `curl`.
4. Run `./scripts/hac-flexquery.sh` checks that directly prove the success criteria.
5. Record command, output summary, and row counts in the validation artifact or stage report.
