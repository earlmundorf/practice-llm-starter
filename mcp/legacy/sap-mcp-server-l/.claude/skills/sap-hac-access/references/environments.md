# HAC Environment Selection

Use this reference whenever a HAC request names an environment such as `local`, `dev`, `qa`, `uat`, or `stage`.

## Default local target

If no environment is specified, use local HAC. These values are assumed by the scripts; provide only the values that need to override the local defaults:

- `HAC_URL=https://localhost:9002`
- `HAC_JAVA_VERSION=21`
- `HAC_CONTEXT_PATH=`
- `HAC_USER=admin`
- `HAC_PASS=nimda`

Assume Java 21/Spring 6 unless the user says Java 17. Track the HAC web context separately from Java version. Set `HAC_CONTEXT_PATH=/hac` when the running HAC is mounted under `/hac`, even if the runtime is Java 21.

## Named non-local targets

For `dev`, `qa`, `uat`, or `stage`, read the environment-specific values from the active project or workspace root:

`hac.env`

In a multi-root workspace, choose the root that owns the user's current HAC task. If that is unclear, prefer the workspace folder containing the file or artifact being validated, then look for `hac.env` at that folder root. Do not assume the skill directory is the project root.

Expected key pattern:

| Environment | URL key | User key | Password key |
|---|---|---|---|
| `dev` | `dev_hac_url` | `dev_admin_user` | `dev_admin_password` |
| `qa` | `qa_hac_url` | `qa_admin_user` | `qa_admin_password` |
| `uat` | `uat_hac_url` | `uat_admin_user` | `uat_admin_password` |
| `stage` | `stage_hac_url` | `stage_admin_user` | `stage_admin_password` |

Do not copy secret values from `hac.env` into docs, chat, terminal-visible output, or validation summaries.

## URL normalization

The bundled HAC scripts append endpoint paths to `HAC_URL` after normalizing the selected Java/Spring line:

- Java runtime: tracked with `HAC_JAVA_VERSION` for reporting and compatibility assumptions.
- HAC web context: tracked with `HAC_CONTEXT_PATH`; empty means root context, `/hac` means legacy-style mount.

The scripts normalize selected `hac.env` URLs during execution:

1. Remove a trailing `/login` if present.
2. Remove a trailing `/console/...` path if present.
3. Remove trailing slashes.
4. Use explicit `HAC_CONTEXT_PATH` if supplied.
5. If `HAC_CONTEXT_PATH` is not supplied, preserve `/hac` when it appears in `HAC_URL`; otherwise use `/hac` only for `HAC_JAVA_VERSION=17`.
6. Confirm the normalized URL still targets the requested environment.

Examples:

| Configured value shape | Script value shape |
|---|---|
| `https://host.example.com/hac/login` with Java 21 | `https://host.example.com/hac` |
| `https://host.example.com/hac/` with Java 17 | `https://host.example.com/hac` |
| `https://host.example.com` with Java 17 | `https://host.example.com/hac` |
| `https://host.example.com` with Java 21 | `https://host.example.com` |
| `https://host.example.com` with Java 21 and `HAC_CONTEXT_PATH=/hac` | `https://host.example.com/hac` |

## Execution pattern

When running from the skill directory for named environments, pass resolved values as environment variables:

```bash
HAC_URL="$SELECTED_HAC_URL" HAC_USER="$ENV_ADMIN_USER" HAC_PASS="$ENV_ADMIN_PASSWORD" ./scripts/hac-flexquery.sh --content "SELECT {pk} FROM {User}" --max-count 10
```

Use the same environment variable pattern for `hac-impex.sh` and `hac-groovy.sh`.

## Safety

- `PROD` is prohibited.
- Non-local writes require explicit approval.
- Read-only FlexibleSearch against a named non-local target should clearly state the target environment and normalized HAC URL.
- Do not ask the user to paste passwords into chat. If credentials fail, ask for non-secret confirmation or have the user update `hac.env` locally.