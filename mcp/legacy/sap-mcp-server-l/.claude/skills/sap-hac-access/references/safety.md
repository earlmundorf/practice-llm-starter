# HAC Safety Rules

## Environment boundary

Use these tools for local SAP Commerce HAC only unless the user explicitly authorizes a non-production target.

Default safe target:

- Java 21 default: `https://localhost:9002`
- Java 17 when requested: `https://localhost:9002/hac`

Hard rules:

- Never target PROD.
- Never use PROD data.
- For `dev`, `qa`, `uat`, or `stage`, resolve HAC details from the active project or workspace root `hac.env`; do not invent endpoint URLs or credentials.
- Do not print or paste passwords from `hac.env` into chat, logs, docs, or final responses.
- Never run remote-changing commands as part of HAC validation.
- Do not infer that a non-local URL is safe; ask first.

## Approval gates

Ask for explicit approval before any server write:

- ImpEx import through `hac-impex.sh`.
- Groovy scripts that mutate data.
- Groovy execution with `--commit`.
- Any operation that creates, updates, deletes, synchronizes, indexes, or reloads data.

Read-only FlexibleSearch validation does not normally need approval.

Read-only validation against a named non-local target still needs clear target confirmation in the command/report, but not a write approval unless the action mutates data.

Treat unintended mutation as a safety failure. Diagnostics must not call setters, save models, alter session context, mutate returned model collections, or trigger jobs unless that change is deliberate, described, and approved. For HAC Groovy, copy model collections before sorting/filtering so inspection code stays read-only.

## Required pre-write statement

Before asking for approval, state:

- Target HAC URL.
- Target environment name.
- Script/tool to be used.
- File or query to be executed.
- Expected data change.
- Rollback or remediation approach, if applicable.

## Groovy commit safety

Default to rollback mode:

`hac-groovy.sh <script.groovy>`

Only use committed mode with explicit approval:

`hac-groovy.sh <script.groovy> --commit`

Do not convert a rollback command to committed mode without a fresh approval.

## Prohibited operations

Do not run:

- System initialization.
- Destructive cleanup scripts.
- Bulk deletes.
- Unreviewed update scripts.
- Production imports.
- Production FlexibleSearch queries.

If a destructive action seems necessary, stop and ask for a safer remediation plan.

## Reporting expectations

After HAC work, report:

- Tool used.
- Target environment.
- Success/failure status.
- Key output lines.
- Validation query row counts and returned identifiers.

For failed write operations, report the failure and stop before attempting additional writes.
