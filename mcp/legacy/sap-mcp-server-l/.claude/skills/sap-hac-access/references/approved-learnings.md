# Approved HAC Learnings

Use this file for HAC usage facts and gotchas that Jon explicitly approves for reuse.

## Capture rule

Do not add tentative discoveries here automatically. When a new HAC behavior, local gotcha, command pattern, or failure mode is learned during work:

1. State the candidate learning and the evidence.
2. Ask User whether to capture it.
3. Add it here only after approval.

## Approved entries

### Core HAC scripts are preferred over raw curl

Source: external `core-hac/README.md` and bundled script headers.

For local HAC FlexibleSearch, Groovy, and ImpEx work, use the bundled scripts first. They handle login, cookies, CSRF extraction, self-signed local HTTPS, endpoint selection, and basic response parsing.

### HAC context depends on Java/Spring line

Source: Java 21 migration finding and bundled script behavior.

The bundled tools assume Java 21/Spring 6 by default and normalize `HAC_URL` to the HAC root context, for example `https://localhost:9002`. If the user says Java 17, set `HAC_JAVA_VERSION=17`; the scripts normalize to the legacy `/hac` context, for example `https://localhost:9002/hac`.

### Groovy rollback is the default

Source: external `hac-groovy.sh` and README.

`hac-groovy.sh` runs with `commit=false` by default and prints `[rollback mode]`. Use `--commit` only after explicit approval.

### FlexibleSearch max count defaults to 200

Source: external `hac-flexquery.sh`.

`hac-flexquery.sh` accepts an optional max count as the second argument. If omitted, it uses `200`. Prefer passing an explicit low row count for validation queries.

### Relative script paths are skill-relative

Source: VS Code/GitHub harness portability requirement and local command behavior.

Docs should use `./scripts/...` relative links and paths. When executing from another working directory, `cd` to the skill directory first or use the absolute path to the bundled script. Do not use Claude-specific variables.

### Named HAC environments come from `hac.env`

Source: Jon-approved skill behavior and local `hac.env` shape.

When HAC work names `dev`, `qa`, `uat`, or `stage`, read `hac.env` from the active project or workspace root and map the matching environment keys into `HAC_URL`, `HAC_USER`, and `HAC_PASS`. Use Java 21 endpoint normalization unless the user says Java 17. Do not copy or print credential values.