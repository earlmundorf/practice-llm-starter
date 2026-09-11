---
name: sap-hac-access
description: 'SAP Commerce HAC access specialist for local server validation, data operations, SAP Commerce data-model investigation, and authoring HAC-ready artifacts. Use whenever the user asks how SAP Commerce stores or relates types; asks about the type system, item inheritance, composed types, attributes, relation deployment tables, or data model; asks how CronJobs are stored or how warehouses, inventory, stock levels, CMSSites, BaseSites, BaseStores, catalogs, products, categories, category/product relations, variants, base products, classification catalogs, classification attributes, product features, carts, orders, users, user groups, principals, catalog permissions, search restrictions, Solr, or sync jobs relate; asks to write, draft, generate, review, improve, validate, or run a FlexibleSearch/FlexQuery query, ImpEx script, or HAC Groovy script; get information through FlexibleSearch/FlexQuery; validate Commerce data; import or verify ImpEx; execute HAC Groovy; modify local data through Groovy; or debug HAC import/query/script failures. Also trigger on: sap-hac-access, HAC, local HAC, data model, type system, ComposedType, AttributeDescriptor, RelationDescriptor, itemtype extends, relation table, how are cronjobs stored, how do warehouses relate, inventory, stock level, CategoryProductRelation, VariantProduct, baseProduct, variantType, classification catalog, ClassificationSystem, ClassificationClass, ClassificationAttribute, ClassAttributeAssignment, ProductFeature, product to cart, cart to order, Principal, UserGroup, PrincipalGroupRelation, readPrincipals, writePrincipals, SearchRestriction, Principal2SearchRestrictionRelation, catalog permissions, access rights, user groups, write impex, generate impex, write groovy, HAC groovy, write flex query, FlexibleSearch, flexquery, hac-impex, hac-flexquery, hac-groovy, FlexibleSearch console, impex import, Console -> ImpEx Import, Console -> FlexibleSearch, Console -> Scripting Languages, https://localhost:9002/hac. Prefer this skill over generic shell/curl/browser approaches for local SAP Commerce HAC reads, approved writes, data-model discovery, and HAC artifact generation; use bundled ./scripts tools first when execution or validation is needed.'
argument-hint: 'HAC task, FlexibleSearch query, ImpEx file, or Groovy script to write/review/run'
allowed-tools: [Read, Write, Edit, Grep, Glob, "Bash(./scripts/hac-flexquery.sh *)", "Bash(./scripts/hac-impex.sh *)", "Bash(./scripts/hac-groovy.sh *)"]
---

# SAP HAC Access

Use this skill for local SAP Commerce HAC automation, validation, and HAC-ready artifact authoring.

## Reference docs

Load these progressive docs when more detail is needed:

- [Core HAC tools](./references/core-hac-tools.md) — canonical scripts, defaults, input modes, and output expectations.
- [Environment selection](./references/environments.md) — how to resolve local, dev, qa, uat, and stage HAC targets from `hac.env`.
- [Commerce diagnostics](./references/commerce-diagnostics.md) — first-pass diagnostic branches for BaseSite/BaseStore, catalogs, products, categories, category/product relations, variants, classification attributes, content, inventory, carts, orders, users, groups, catalog permissions, search restrictions, sync, Solr, CronJobs, media, and type system checks.
- [Recipes](./references/recipes.md) — repeatable import, validation, FlexibleSearch, Groovy, and QRSPI workflows.
- [Troubleshooting](./references/troubleshooting.md) — CSRF/login failures, rejected rows, zero-row validations, and parser failures.
- [Safety](./references/safety.md) — environment boundaries, approval gates, prohibited operations, and reporting expectations.
- [Approved learnings](./references/approved-learnings.md) — user-approved HAC usage facts and gotchas discovered during real work.
- [SAP Commerce object model expert map](./references/experts/commerce-object-model.md) — stable BaseSite, BaseStore, catalog, category, product, variant, warehouse, stock, CMS, classification, user/group, catalog permission, search restriction, Solr, sync, and index relationships.
- [FlexibleSearch expert guidance](./references/experts/flexquery.md) — SAP Commerce data-model, source-code, and runtime best practices for generating HAC FlexibleSearch queries.
- [HAC Groovy expert guidance](./references/experts/hac-groovy.md) — ServiceLayer-safe Groovy patterns for local validation, repair scripts, and CronJob/index triggers.
- [ImpEx expert guidance](./references/experts/impex.md) — idempotent, model-aware ImpEx generation and import validation guidance.

## Bundled tool directory

Always use the scripts bundled with this skill first for local HAC work:

`./scripts`

Available tools:

- `hac-impex.sh` — imports ImpEx through HAC.
- `hac-flexquery.sh` — runs HAC FlexibleSearch queries.
- `hac-groovy.sh` — runs HAC Groovy scripts.

These scripts are bundled with the skill and are kept under `./scripts` for repository portability.

Assumed local defaults, no environment variables needed unless overriding:

- `HAC_URL=https://localhost:9002`
- `HAC_JAVA_VERSION=21`
- `HAC_CONTEXT_PATH=`
- `HAC_USER=admin`
- `HAC_PASS=nimda`

Assume Java 21/Spring 6 runtime unless the user says Java 17. Do not assume the Java line alone determines the HAC web context. If HAC is mounted under `/hac` on a Java 21 environment, set `HAC_CONTEXT_PATH=/hac` while keeping `HAC_JAVA_VERSION=21`.

For `dev`, `qa`, `uat`, or `stage` HAC work, load [Environment selection](./references/environments.md) and resolve the target from the active project or workspace root `hac.env`. Do not point these tools at PROD services or PROD data.

## When to use

Use this skill whenever the task involves:

- HAC access or local SAP Commerce admin automation.
- SAP Commerce data-model questions: how types are stored, which type extends which parent, which attributes exist, and which relation/deployment table connects two objects.
- Generic Commerce object graph questions, such as how CronJobs are persisted or how warehouses relate to CMSSite/BaseSite through BaseStore.
- Writing, drafting, reviewing, or improving FlexibleSearch/FlexQuery queries.
- Writing, drafting, reviewing, or improving HAC Groovy scripts.
- Writing, drafting, reviewing, or improving ImpEx intended for HAC import or HAC-backed validation.
- ImpEx import through HAC.
- FlexibleSearch validation queries.
- Groovy execution in HAC.
- Catalog, catalog version, sync job, Solr, BaseSite, BaseStore, or data-load validation.
- Product catalog search, category search/navigation, SolrFacetSearchConfig, SolrIndexedType, SolrIndexedProperty, categoryField, facets, facet types, and search visibility restrictions.
- Content catalog, CMS pages, page templates, content slots, CMS components, CMS navigation nodes/entries, CMS restrictions, and how CMS category/page/product links fit into a site/store context.
- QRSPI verification where `IMPEX_IMPORT` is required.

## Tool preference rules

1. Prefer `./scripts/hac-impex.sh` for HAC ImpEx import.
2. Prefer `./scripts/hac-flexquery.sh` for HAC FlexibleSearch.
3. Prefer `./scripts/hac-groovy.sh` for HAC Groovy.
4. Do not default to raw `curl` for HAC endpoints.
5. Use the external core-HAC directory only as the upstream/fallback copy.
6. Do not use `.claude/skills/impex/scripts/hac-import.sh` unless explicitly requested or the bundled HAC tool is unavailable.
7. Offline ImpEx lint may still use the ImpEx skill linter before live import.

## Optional project knowledge overlays

This skill is portable. Keep generic SAP Commerce and HAC knowledge inside this skill, and keep client or project-specific facts outside the skill in the workspace.

Before answering project-specific SAP Commerce data-model, catalog, Solr, CMS, environment, or HAC questions, look in this default workspace-root overlay:

- `project-knowledge/sap-commerce/`

Use these additional locations only when `project-knowledge/sap-commerce/` does not cover the topic or the user asks for broader local context:

- `project-knowledge/hac/`
- `working-docs/knowledge/`
- repository memory under `/memories/repo/`

Load only the overlays that match the user's topic. Do not require these files for normal skill operation, and do not include project overlays when packaging or sharing this skill.

## Expert generation rules

Before answering data-model questions or generating, reviewing, or running a HAC artifact, load the matching expert guidance and use it as the local standard:

- Site, store, catalog, category, product, variant, inventory, cart, order, CMS, content catalog, CMS page, slot, component, navigation, classification, user, group, principal, permission, access, search restriction, Solr, SolrFacetSearchConfig, SolrIndexedType, SolrIndexedProperty, facet, category search, sync, or index work: load [SAP Commerce object model expert map](./references/experts/commerce-object-model.md).
- FlexibleSearch or FlexQuery: load [FlexibleSearch expert guidance](./references/experts/flexquery.md).
- HAC Groovy: load [HAC Groovy expert guidance](./references/experts/hac-groovy.md).
- ImpEx: load [ImpEx expert guidance](./references/experts/impex.md).

For non-trivial generated queries, scripts, or imports, inspect the existing SAP Commerce model and implementation before inventing type names, attributes, beans, or business keys. Prefer the active database for runtime facts and `bin/custom` plus `bin/modules` for framework and project conventions.

For data-model questions, start with the generic SAP Commerce type system before applying project-specific meaning. Use `ComposedType` to identify the type and parent chain, `AttributeDescriptor` to identify attributes, and `RelationDescriptor` or `items.xml` relation definitions to identify relation names, source/target qualifiers, and deployment tables. Then inspect the specific project type, such as a hotel type, to see what it extends and which custom attributes or relations it adds.

When the user is only asking to write or review an artifact, do not execute it unless they also ask for validation or execution. Still design it as if it may be run through HAC: local-safe, parameterized where appropriate, clear about target data, and explicit about whether it is read-only or mutating.

For authoring work:

- FlexibleSearch output should use Commerce type and attribute qualifiers, include a row limit or suggested `--max-count`, and state any required catalog version, site, language, currency, user, or restriction context.
- HAC Groovy output should default to read-only or rollback-safe behavior, copy Commerce model collections before sorting/filtering, avoid accidental mutation, and require explicit approval before committed changes.
- ImpEx output should be idempotent where possible, use business keys rather than PKs, define macros for repeated catalog/site/store values, and include validation queries for imported references.

## Safety rules

- Server writes require explicit user approval before running `hac-impex.sh` or committed Groovy scripts.
- Non-local HAC targets such as `dev`, `qa`, `uat`, and `stage` must be resolved from the active project or workspace root `hac.env`; never invent URLs or credentials.
- By default, `hac-groovy.sh` should run in rollback mode. Use `--commit` only after explicit approval.
- Never run destructive initialization through HAC.
- Never target PROD.
- For imports, state what file/data will be imported and wait for approval.

## Common commands

Run from the skill directory when using `./scripts/...`, or use the absolute path to the bundled script from other directories. Pass absolute or repo-relative data files as appropriate.

### Import ImpEx

Use after explicit approval:

`./scripts/hac-impex.sh --file <file.impex>`

### Run FlexibleSearch

`./scripts/hac-flexquery.sh --content "SELECT {pk} FROM {User}" --max-count 10`

Supports query files and stdin:

- `./scripts/hac-flexquery.sh --file /path/to/query.sql --max-count 50`
- `echo "SELECT {pk} FROM {Product}" | ./scripts/hac-flexquery.sh --stdin`
- `./scripts/hac-flexquery.sh --content "SELECT {pk} FROM {Product}" --json`

### Run Groovy

Rollback/default mode:

`./scripts/hac-groovy.sh --file <script.groovy>`

Committed mode requires explicit approval:

`./scripts/hac-groovy.sh --file <script.groovy> --commit`

## Verification workflow

For an ImpEx task:

1. Run offline lint first when available.
2. Ask for approval before live import.
3. Import with `./scripts/hac-impex.sh`.
4. Validate data with `./scripts/hac-flexquery.sh`.
5. Capture command outputs in the task report or validation artifact.

For read-only validation:

1. Load [Commerce diagnostics](./references/commerce-diagnostics.md) when the request asks what exists, what is wired, or why storefront/Backoffice/HAC behavior is wrong.
2. Choose one diagnostic branch before the first HAC call.
3. State the hypothesis that the query will prove or disprove.
4. Run the smallest FlexibleSearch query that proves the fact.
5. Include a max row count argument.
6. Report the normalized target, row count, and key returned values.
7. Do not retry the same query unless the prior result changes the hypothesis.

## Failure handling

- If login or CSRF extraction fails, check that local SAP Commerce is running and whether HAC is mounted at `https://localhost:9002` or `https://localhost:9002/hac`. Use `HAC_CONTEXT_PATH=/hac` for the latter, even on Java 21.
- If the requested target is `dev`, `qa`, `uat`, or `stage`, check the active project or workspace root `hac.env`, pass the selected HAC URL and credentials through environment variables, and do not print them. The scripts normalize trailing `/login`, `/console/...`, and slash values internally, then reject non-HAC paths.
- If credentials fail, ask the user to confirm non-secret connection details; do not request passwords through chat.
- If import fails, report rejected rows/error output and stop before further writes.
- If FlexibleSearch returns zero rows, treat that as a failed validation unless zero rows were expected.
- If Groovy fails, report the summarized exception and first relevant frame first; rerun with `--verbose` only when the summary is not enough.
