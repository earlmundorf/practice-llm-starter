# HAC Groovy Expert Guidance

Use this guidance before generating or running a Groovy script through HAC.

For any script involving BaseSite, BaseStore, catalogs, CMS, classification, inventory, warehouses, Solr, sync, or index jobs, also load [SAP Commerce object model expert map](./commerce-object-model.md) before generating the script.

## Expert stance

HAC Groovy executes inside the SAP Commerce application context. Treat it as production-grade ServiceLayer code with a short lifespan: use Spring beans, model services, flexible search, cron job services, and platform APIs instead of direct database access or ad hoc SQL.

Default to rollback mode for inspection and dry-run logic. Use `--commit` only after explicit approval and only when the script is intentionally performing a local server-side action or write.

## Evidence sources

Before generating a script that mutates, triggers, imports, or repairs data, inspect the framework already present in the system:

- Runtime type metadata with `ComposedType` and `AttributeDescriptor`.
- Existing data with read-only FlexibleSearch.
- Existing Java services, DAOs, jobs, actions, and constants in `bin/custom`.
- Platform and module implementations in `bin/modules` when the behavior belongs to SAP-delivered code such as Solr indexing, catalog sync, cron jobs, or Backoffice.
- Existing ImpEx in setup folders and extension resources to match business keys and catalog versions.

Do not invent bean names when source or runtime checks can identify them. Prefer common platform beans only when they are stable and conventional: `modelService`, `flexibleSearchService`, `cronJobService`, `catalogVersionService`, `baseSiteService`, `userService`, `commonI18NService`, `enumerationService`, `configurationService`.

## Script design rules

- Use ServiceLayer APIs and models. Do not use JDBC or direct SQL from HAC Groovy.
- Use parameterized `FlexibleSearchQuery`; do not concatenate user input into query strings.
- Keep scripts small, single-purpose, and reversible where possible.
- Treat every Commerce model and model collection as read-only unless the user explicitly asked for a mutation and approved it. Do not call mutating methods such as `sort()`, `remove()`, `clear()`, setters, or collection add/remove operations on model-returned collections during diagnostics.
- Copy Commerce model collections before sorting, filtering, grouping, or reshaping them. Prefer `new ArrayList(model.getSomeCollection() ?: [])` or non-mutating Groovy APIs such as `sort(false)` when available.
- Print concise progress and final status. Avoid dumping full models or large collections.
- Use `searchUnique` only when uniqueness is guaranteed by business key; otherwise inspect row count and stop if ambiguous.
- Validate current state before mutation, and validate resulting state after mutation.
- For batch updates, collect models and use `modelService.saveAll()` rather than saving inside a tight loop.
- Avoid `modelService.save()` in interceptors, event listeners, and code paths that may recursively trigger platform logic.
- Avoid changing session user, language, currency, or catalog versions unless the task requires it; if required, set them deliberately and print what was set.
- Do not swallow exceptions. Let HAC return the stack trace, or catch exceptions only to print a clearer domain-specific message and rethrow.
- When validating or repairing storefront behavior, print and validate the connected site configuration chain rather than only the named model: site, store, catalog versions, warehouses/stock, Solr config/indexed type, and relevant sync/index CronJobs.

## Session, transaction, and restriction rules

- Be explicit when a script depends on session catalog versions, user, language, currency, or base site. Read current session state first, then set only what the task requires.
- Be aware of search restrictions. For admin diagnostics, say whether restrictions are acceptable or whether the script must temporarily disable them through the proper platform service.
- Do not wrap long-running jobs such as Solr indexing or catalog sync in a manual transaction. Trigger the platform job and validate its CronJob status separately.
- For small committed repairs, prefer a narrow transaction around the minimal mutation set. Do not hold a transaction while running broad searches or printing large output.
- After `modelService.saveAll()`, do not assume previously loaded related models reflect database state. Re-query important validation facts.

## CronJob and index triggering

For existing CronJobs, prefer `cronJobService.performCronJob(cronJob, false)` for asynchronous local triggers so HAC returns promptly. Use synchronous execution only when the user explicitly wants to wait and the job is known to be short.

Before triggering:

- Resolve the exact CronJob by code.
- Check readable `status` and `result` values.
- Stop if the job is already `RUNNING`, `RUNNINGRESTART`, or otherwise active.
- Confirm that the CronJob type matches the requested action, for example `SolrIndexerCronJob` for legacy Solr facet search indexing.

Copy-safe trigger pattern:

```groovy
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def code = '<cronJobCode>'
def flexibleSearchService = spring.getBean('flexibleSearchService')
def cronJobService = spring.getBean('cronJobService')

def query = new FlexibleSearchQuery('SELECT {pk} FROM {CronJob} WHERE {code}=?code')
query.addQueryParameter('code', code)
def result = flexibleSearchService.search(query)

if (result.result.empty) {
    throw new IllegalStateException("No CronJob found for code ${code}")
}
if (result.result.size() > 1) {
    throw new IllegalStateException("Expected one CronJob for code ${code}, found ${result.result.size()}")
}

def cronJob = result.result[0]
def statusCode = cronJob.status?.code
def resultCode = cronJob.result?.code

println "CronJob ${cronJob.code}: status=${statusCode}, result=${resultCode}, type=${cronJob.itemtype}"

if (statusCode in ['RUNNING', 'RUNNINGRESTART', 'PAUSED']) {
    throw new IllegalStateException("CronJob ${cronJob.code} is already active: status=${statusCode}")
}

println "Triggering ${cronJob.code} asynchronously"
cronJobService.performCronJob(cronJob, false)
println "Submitted ${cronJob.code}"
```

Validate after triggering with FlexibleSearch rather than assuming the submission succeeded.

## Mutation safety

Default stance: no mutation unless the script is intentionally written to change state, the intended change is named in plain language, and the user has explicitly approved the write. This includes accidental mutations caused by helper methods, collection operations, session changes, or model setters.

For local writes, the script must state what it will change before execution. If the script updates existing items, it should first print the matching business keys and count in rollback mode. If count is zero or unexpectedly high, stop.

For delete operations, do not generate committed HAC Groovy unless the user explicitly requests deletion and confirms the exact target. Prefer disabling or correcting local data over removal when diagnosing storefront behavior.

## Output expectations

A good HAC Groovy script prints:

- Target business key or CronJob code.
- Number of matched items.
- Action selected, such as dry-run, update, save, trigger, or validation only.
- Final status or validation query hint.

Keep output compact enough for the skill to reason over without burning tokens.
