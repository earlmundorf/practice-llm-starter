---
date: 2026-05-01
project: sap-mcp-server-g
phase: 0-inplace
applies_to:
  java_from: any
  spring_from: any
  commerce_from: any
  build_plugin: sap.commerce.build 5.0.2
kind: new-incident
status: unpromoted
related_refs:
  - references/phase-guide.md (Phase 0 Step 0.4)
  - references/known-incidents.md
promotion_target: references/known-incidents.md (new entry "validateManifest E-012") + references/phase-guide.md (Phase 0 Step 0.4 expected-errors note)
---

## What happened

After bumping `commerceSuiteVersion` and running `bootstrapPlatform` cleanly, `./gradlew validateManifest --no-daemon` failed with:

```
ERROR E-012 @ useConfig.extensions.location
`hybris/config/localextensions.xml`: Attribute `extension.dir` is not supported. Only use `extension.name` to declare extensions.
```

Source: `dev-config/localextensions.xml` declared custom extensions using:
```xml
<extension dir="${HYBRIS_BIN_DIR}/custom/sampledatamcp"/>
<extension dir="${HYBRIS_BIN_DIR}/custom/coremcp"/>
```

The `<extension dir="...">` syntax appears to be the older Commerce style (using absolute paths to extension directories). Newer CCv2 manifest validation requires `<extension name="...">` instead — the `<path dir="...">` declarations earlier in the file already establish the search roots, so name-only references resolve unambiguously.

This is **not** a migration-introduced error. The project was already using a syntax that the bumped `sap.commerce.build` plugin no longer accepts. It surfaces during Phase 0's `validateManifest` step, post-bootstrap.

## Context

- Project: sap-mcp-server-g
- Build plugin: `sap.commerce.build` 5.0.2 (declared in build.gradle)
- Pre-fix `dev-config/localextensions.xml` had `<path dir="${HYBRIS_BIN_DIR}/custom" />` at line 12 AND `<extension dir="${HYBRIS_BIN_DIR}/custom/X"/>` at lines 90-91 — redundant.

## The fix that worked

Replace the `dir=` form with `name=`:

```xml
<extension name="sampledatamcp"/>
<extension name="coremcp"/>
```

Re-run `./gradlew setupConfig` to re-overlay `dev-config/` into `hybris/config/`, then re-validate. E-012 clears.

## Why this generalizes

Any project that historically declared custom extensions with `dir=` will hit this when migrating, because the validation tightening lands in the same plugin version range that the migration consumes. The fix is mechanical and safe — `<path>` declarations make the directories discoverable, so name-only resolution is unambiguous.

## Promotion suggestion

1. Add to `references/known-incidents.md` as a new entry: "validateManifest E-012: extension.dir disallowed" with the inventory grep + fix.
2. Add to `references/phase-guide.md` Phase 0 Step 0.4 expected-errors note: "If the project's localextensions.xml uses `<extension dir=...>`, expect E-012 — fix by renaming to `<extension name=...>`."
3. Optionally add a sweep step `B.0a — Localextensions modernization` covering this and similar deprecated XML attributes.
