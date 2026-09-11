# Core HAC Tools

## Bundled location

Use the local SAP Commerce HAC helpers bundled with this skill first:

`./scripts`

The `./scripts/...` commands are relative to the skill directory. If the terminal is in another directory, either `cd` to this skill directory first or invoke the script by absolute path.

Tools:

| Task | Tool |
|---|---|
| ImpEx import | `hac-impex.sh` |
| FlexibleSearch query | `hac-flexquery.sh` |
| Groovy script execution | `hac-groovy.sh` |

Prefer these scripts over generic `curl`, browser automation, or `.claude/skills/impex/scripts/hac-import.sh` for live HAC access.

Use raw `curl` only when:

- the bundled scripts do not support the target HAC endpoint;
- debugging the bundled script itself;
- testing a non-HAC OCC/API endpoint.

The scripts are bundled under `./scripts` and should remain self-contained for repository portability. When updating them, keep this skill's local behavior documented here.

## What the scripts handle

These tools handle the HAC mechanics that make raw `curl` fragile:

- local HTTPS/self-signed certificate handling;
- HAC login flow;
- session cookie handling;
- CSRF token extraction;
- correct HAC endpoint selection;
- `HAC_URL` normalization with Java runtime and HAC context tracked separately;
- temp-file-backed request payloads for large queries, ImpEx, and Groovy scripts;
- curl connect and request timeouts;
- dependency checks for required local commands;
- readable command output;
- response/error parsing with HTTP status labels.

## Requirements

The upstream scripts require these command-line tools:

- `curl`
- `python3`

The scripts check for these dependencies at startup. If a script fails before reaching HAC, install or fix the missing command before rewriting the request as raw `curl`.

## Defaults

The scripts assume local HAC defaults. Provide only the environment values that need to override the defaults:

- `HAC_URL=https://localhost:9002`
- `HAC_JAVA_VERSION=21`
- `HAC_CONTEXT_PATH=`
- `HAC_USER=admin`
- `HAC_PASS=nimda`

Override non-secret values inline only when needed:

`HAC_CONTEXT_PATH=/hac <tool> ...`

Optional timeout overrides:

- `HAC_CONNECT_TIMEOUT=10` controls the curl connection timeout in seconds.
- `HAC_MAX_TIME=120` controls the total curl request timeout in seconds.

Assume Java 21/Spring 6 runtime unless the user says Java 17. Track HAC mount separately from Java version:

- `HAC_JAVA_VERSION=21` is the default runtime assumption.
- `HAC_CONTEXT_PATH=` uses root-context HAC paths: `/login`, `/j_spring_security_check`, and `/console/...`.
- `HAC_CONTEXT_PATH=/hac` uses `/hac/login`, `/hac/j_spring_security_check`, and `/hac/console/...`, even when `HAC_JAVA_VERSION=21`.

Example for a Java 21 environment with HAC mounted under `/hac`:

`HAC_CONTEXT_PATH=/hac <tool> ...`

Do not request or echo passwords, tokens, or other secrets through chat.

For named non-local environments, load [Environment selection](./environments.md) and map the selected `hac.env` keys to these script variables. The scripts strip a trailing `/login`, trailing `/console/...`, and trailing slash, then apply the explicit HAC context path when provided.

The scripts reject ambiguous pathful `HAC_URL` values that are not a HAC root, `/hac`, `/login`, or `/console/...` URL. This prevents accidentally sending HAC credentials to storefront, OCC, Backoffice, or unrelated service paths.

## Paths

Skill-relative paths:

- `./scripts/hac-impex.sh`
- `./scripts/hac-flexquery.sh`
- `./scripts/hac-groovy.sh`

These paths are intentionally relative for VS Code/GitHub harness portability. Do not replace them with Claude-specific variables.

## Input modes

All tools accept explicit input modes and preserve the original positional behavior:

- Direct string: `./scripts/hac-flexquery.sh --content "SELECT {pk} FROM {User}" --max-count 10`
- File path: `./scripts/hac-impex.sh --file /path/to/import.impex`
- Stdin: `echo "SELECT {pk} FROM {Product}" | ./scripts/hac-flexquery.sh --stdin`

Backward-compatible positional examples still work:

- `./scripts/hac-flexquery.sh "SELECT {pk} FROM {User}" 10`
- `./scripts/hac-impex.sh /path/to/import.impex`
- `echo "SELECT {pk} FROM {Product}" | ./scripts/hac-flexquery.sh -`

Every tool supports `--help`.

Submitted query, ImpEx, and Groovy content is materialized into temporary files before curl submission. This avoids large shell arguments and keeps behavior consistent across direct content, stdin, and file input modes.

## Output expectations

- `hac-impex.sh` prints `OK: ImpEx import successful` on successful import.
- `hac-flexquery.sh` prints a table and row count; default max row count is `200` when omitted. Use `--json` for the raw HAC JSON response.
- `hac-groovy.sh` prints `[rollback mode]` or `[commit mode]`, then script output and result.

A zero-row FlexibleSearch result is only acceptable when zero rows are the expected outcome.

## Upstream endpoint assumptions

- FlexibleSearch posts to `/console/flexsearch/execute` after loading `/console/flexsearch` without a trailing slash.
- Groovy posts to `/console/scripting/execute` after loading `/console/scripting`.
- ImpEx posts to `/console/impex/import` after loading `/console/impex/import`.
- `HAC_CONTEXT_PATH=/hac` prefixes those endpoint suffixes with `/hac`.
- All three scripts reject unknown options and conflicting input modes.
