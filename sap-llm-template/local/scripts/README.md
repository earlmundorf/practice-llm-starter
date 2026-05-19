# Local Scripts

Utility scripts for managing the SAP Commerce (hybris) server. **Claude should always use these scripts** instead of running `hybrisserver.sh`, `ant`, HAC curl commands, or the HAC web UI directly.

All scripts output structured status lines for programmatic monitoring. Run from any directory — they resolve paths relative to their own location.

All HAC scripts (`hac-flexquery.sh`, `hac-groovy.sh`, `hac-impex.sh`) accept three input modes:
- **Inline string** — for simple one-liners
- **File path** — for complex multi-line input (write to `/tmp/` first, avoids shell escaping issues)
- **`-` for stdin** — for piping

## Scripts

### restart-server.sh

Stop the server, wait 5 seconds, start it back up, and monitor logs until startup completes.

**Use when:** You need a quick restart after a code change and `ant build`.

```bash
./local/scripts/restart-server.sh
```

### update-server.sh

Stop the server (if running), run `ant clean all`, run `ant updatesystem`, then start and monitor.

**Use when:** After `*-items.xml` or `*-beans.xml` changes that require a full rebuild and database schema update. Preserves existing data.

```bash
./local/scripts/update-server.sh
```

### build-server.sh

Run `ant build`, then stop and restart the server.

**Use when:** After Java source changes that just need a compile and restart (no type system or bean changes).

```bash
./local/scripts/build-server.sh
```

### initialize-server.sh

⚠️ **DESTROYS ALL DATA.** Stop the server, run `ant clean all`, run `ant initialize`, then start and monitor. Requires interactive confirmation (type "initialize") or `--force` flag.

**Use when:** You need a full data reset — wipes the database and reimports all essential/project data from ImpEx.

```bash
./local/scripts/initialize-server.sh          # prompts for confirmation
./local/scripts/initialize-server.sh --force   # skips confirmation
```

### index-solr.sh

Run a full Solr reindex for all configured search indexes (backoffice and ThinkShop storefront). Required after `initialize-server.sh` or whenever search results are empty/stale.

**Use when:** After a full initialize, or if backoffice shows "Could not execute search".

```bash
./local/scripts/index-solr.sh
```

### hac-flexquery.sh

Run FlexibleSearch queries against HAC from the command line. Requires the server to be running. Accepts a query string, a file path, or stdin.

```bash
# Inline query
./local/scripts/hac-flexquery.sh "SELECT {pk}, {code} FROM {Product}"

# From a file
./local/scripts/hac-flexquery.sh /tmp/my-query.sql 50

# From stdin
echo "SELECT {pk} FROM {Product}" | ./local/scripts/hac-flexquery.sh -
```

### hac-groovy.sh

Execute Groovy scripts against HAC from the command line. Requires the server to be running. Accepts a script string, a file path, or stdin. Runs in rollback mode by default — use `--commit` to persist changes.

```bash
# Inline script
./local/scripts/hac-groovy.sh "return spring.getBean('flexibleSearchService')"

# From a file
./local/scripts/hac-groovy.sh /tmp/my-script.groovy

# From a file with commit mode
./local/scripts/hac-groovy.sh fix-data.groovy --commit

# From stdin
echo "println 'Hello'" | ./local/scripts/hac-groovy.sh -
```

### hac-impex.sh

Run ImpEx imports against HAC from the command line. Requires the server to be running. Accepts an ImpEx string, a file path, or stdin.

```bash
# From a file (most common)
./local/scripts/hac-impex.sh /tmp/my-data.impex

# Inline (simple cases)
./local/scripts/hac-impex.sh "INSERT_UPDATE Title; code[unique=true]; name[lang=en]
; mr ; Mr"

# From stdin
cat data.impex | ./local/scripts/hac-impex.sh -
```

## Output Format

The server lifecycle scripts (`restart`, `build`, `update`, `initialize`) print progress headers for each step and always end with one of:

- `STARTED` — server is up and ready (exit code 0)
- `ERROR: <reason>` — something failed (exit code 1)

This makes them suitable for background execution with programmatic monitoring.
