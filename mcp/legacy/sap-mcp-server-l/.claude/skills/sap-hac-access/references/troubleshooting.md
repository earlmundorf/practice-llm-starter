# HAC Troubleshooting

## Login or CSRF extraction fails

Symptoms:

- `Could not extract CSRF token`
- `Login failed or CSRF token missing`
- `HAC request failed during ...`

Checks:

1. Confirm local SAP Commerce is running.
2. Confirm HAC URL normalizes to the actual HAC mount, either `https://localhost:9002` or `https://localhost:9002/hac`.
3. Confirm the target is local, not PROD.
4. If credentials are not the local defaults, ask the user to configure them in their terminal/environment; do not ask them to paste secrets into chat.

Notes:

- The bundled HAC tools assume Java 21 by default, but Java version and HAC mount are separate. Use `HAC_CONTEXT_PATH=/hac` when HAC is mounted under `/hac`, even on Java 21.
- They strip a trailing `/login`, trailing `/console/...`, and trailing slash before applying the context-path rule.
- Some other scripts expect the base host without `/hac`; do not mix assumptions.
- If using `dev`, `qa`, `uat`, or `stage`, read the active project or workspace root `hac.env` and pass the selected URL as `HAC_URL`; the scripts enforce the final Java-version-specific shape.

## Import fails

Symptoms:

- `ERROR: ImpEx import failed`
- HAC rejected rows in the response.
- Missing referenced item/type/attribute errors.

Response:

1. Stop further write operations.
2. Report the rejected rows or HAC error text.
3. Fix the ImpEx artifact if the cause is clear and within scope.
4. Re-run offline lint.
5. Ask before re-importing.

Do not run initialize to fix an import problem.

## FlexibleSearch returns zero rows

Treat zero rows as failed validation unless the expected result is explicitly zero.

Use [Commerce diagnostics](./commerce-diagnostics.md) to choose the next query by domain instead of broadening randomly.

Response:

1. Confirm the query targets the exact ID/code/version from the imported artifact.
2. Check whether import succeeded and had no rejected rows.
3. Run a broader diagnostic query with a small row limit.
4. Do not mark validation passed until expected rows are present.

## FlexibleSearch parse or endpoint failure

Symptoms:

- `Failed to parse HAC response`
- HTML returned instead of JSON.
- Login page returned after query execution.

Checks:

1. Confirm login succeeded.
2. Confirm the HAC server did not restart during the query.
3. Confirm the query syntax is valid FlexibleSearch, not SQL.
4. Confirm `HAC_JAVA_VERSION` matches the runtime and `HAC_CONTEXT_PATH` matches the actual HAC mount.
5. Re-run with `--json` if table formatting hides the response field you need.

## Argument or input mode failure

Symptoms:

- `Only one ... input may be provided`
- `Unknown option`
- `Unexpected argument`
- `max count must be a non-negative integer`

Response:

1. Prefer one explicit input mode: `--file`, `--stdin`, or `--content`.
2. For FlexibleSearch, pass row limits with `--max-count <n>` or as the second positional argument.
3. Use `--help` on the target script to confirm supported flags.

## Groovy failure

Response:

1. Prefer rollback/default mode while debugging.
2. Report the script exception and stack trace summary.
3. Do not retry with `--commit` unless the user explicitly approves.
4. If the script mutates data, describe exactly what it will change before asking for approval.

## Server not running

If HAC cannot be reached, stop HAC validation and report that local SAP Commerce must be started. Do not attempt to start or restart the server unless the user asks.

## Mixed tool behavior

If `.claude/skills/impex/scripts/hac-import.sh` fails but the bundled HAC tools are available, prefer `./scripts`. They use the local workspace's validated HAC URL convention and login flow.

## Relative script path fails

Symptoms:

- `no such file or directory: ./scripts/hac-flexquery.sh`
- `no such file or directory: ./scripts/hac-impex.sh`
- `no such file or directory: ./scripts/hac-groovy.sh`

Cause:

- `./scripts/...` is relative to this skill directory, not the user's current project directory.

Response:

1. `cd` to the `sap-hac-access` skill directory and rerun the command.
2. Or invoke the bundled script by absolute path.
3. Keep docs and examples relative; do not introduce Claude-specific path variables.

## Named environment URL fails

Symptoms:

- HAC URL contains `/hac/login/login`.
- HAC URL contains a double slash before the console path.
- CSRF extraction fails only for a named environment.
- Java 21 HAC redirects repeatedly or returns login unexpectedly when using `/hac/...`.
- The script rejects `HAC_URL` as non-HAC or ambiguous.

Cause:

- `hac.env` values may omit `/hac`, include `/hac` for Java 21, point to a non-HAC endpoint, or include an unexpected path after `/hac/login`.

Response:

1. Confirm the selected environment URL is a HAC URL, not a storefront/OCC/API URL.
2. Use the default `HAC_JAVA_VERSION=21` unless the user says Java 17.
3. Pass the selected value as `HAC_URL`; the script will strip trailing `/login`, `/console/...`, and trailing slash.
4. If the script still fails, set `HAC_JAVA_VERSION=17` only when the target is a Java 17/Spring 5 Commerce runtime.
5. If the URL points at Backoffice, storefront, OCC, or another path, correct `hac.env` instead of forcing the script to accept it.
