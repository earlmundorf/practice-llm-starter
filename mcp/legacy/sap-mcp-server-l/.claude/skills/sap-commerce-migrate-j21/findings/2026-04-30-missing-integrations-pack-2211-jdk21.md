---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: 0
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: gap
status: promoted
related_refs:
  - references/sap-docs/01-general-update-guide.md
  - references/additional-changes.md (Prerequisites section)
  - scripts/plan-template.md (Phase 0.4)
  - scripts/detect_state.sh (Commerce ZIPs section)
promotion_target: scripts/plan-template.md + scripts/detect_state.sh + references/additional-changes.md — warn-not-fail semantics
promoted_commit: 4e752eb
---

## What happened

The new SAP distribution at `CCL2211J2100U_9-80009731/` contains the Commerce Suite for 2211-jdk21.9 but **does NOT include a matching Integration Extension Pack**. The legacy project pinned `hybris-commerce-integrations-2211.42.zip` via `intExtPackVersion = '2211.42'` in `build.gradle`. No version-matched 2211-jdk21 pack was available at migration time.

Initial reaction (wrong): copy the legacy 2211.42 pack into `core-customize/dependencies/` as a stopgap so `bootstrapPlatform` could resolve the dependency. This works at build time — the platform came up and `version=2211-jdk21.9` resolved correctly — but it's a real risk: an older integration pack against a newer platform can silently break at runtime (missing/changed extensions, Spring bean incompatibility, OCC contract drift). The failures would surface late, at Phase F smoke tests or worse, in production, with a confusing blame path.

Corrected approach (new principle adopted mid-Phase 0): **prefer absence over a wrong-version pack.** The integration pack is optional-but-recommended. If a matching version isn't available, leave it OFF entirely rather than substitute — `bootstrapPlatform` and all pre-Phase-F work run fine without it. Only Phase F (OCC smoke tests) genuinely requires a version-matched pack.

## Context

- `core-customize/build.gradle` previously declared:
  ```gradle
  ext {
      intExtPackVersion = '2211.42'
  }
  dependencies {
      hybrisPlatform "de.hybris.platform:hybris-commerce-integrations:${intExtPackVersion}@zip"
  }
  ```
- After adopting warn-not-fail, both declarations are commented out (with a TODO to restore when a version-matched pack is available).
- Legacy pack location (not copied anymore): `/Users/emundorf/development/cap-dev/projects/upgrade/sap-mcp-server-g/core-customize/dependencies/hybris-commerce-integrations-2211.42.zip`.
- The new Commerce distribution (`CCL2211J2100U_9-80009731/`) ships `build-tools/`, `hybris/`, `hybris-sbg/`, `installer/`, `licenses/`, `README`, `SIGNATURE.SMF` — no `hybris-commerce-integrations-*.zip`. Integration packs live on a separate cadence on the SAP Software Download Center.

## The SAP-doc gap (if applicable)

SAP's general update guide (`references/sap-docs/01-general-update-guide.md`) talks about Commerce Suite updates but does not explicitly call out that the Integration Extension Pack has its own release cadence, its own SAP Software Download Center download, and that a mismatched pack is dangerous enough to justify leaving it out entirely until matched.

## The fix that worked

**Remove the stopgap, leave integrations off, re-bootstrap clean.** Concretely (see Step 0.9 in `docs/migration-log.md`):

1. Comment out the `ext { intExtPackVersion = '2211.42' }` block and the integrations `hybrisPlatform` dependency line in `core-customize/build.gradle` — keep TODOs pointing to where to re-add when a matching pack is available.
2. Delete `core-customize/dependencies/hybris-commerce-integrations-*.zip` (the wrong-version pack). Gitignored; purely local hygiene.
3. Run `./gradlew cleanAll` followed by `./gradlew bootstrapPlatform`. Verify: no "zip entry … overwriting" messages (suite is the sole source), `hybris/bin/platform/build.number` shows `version=2211-jdk21.9`, and the extensions list shows `oauth2commons` + `authorizationserver` + `resourceserver` from the platform trio (no integration-pack extensions, as expected).
4. Re-add integrations in Phase F when a matching pack is available from SAP SDC.

## Why this generalizes

Every SAP Commerce major upgrade (2205 → 2211, 2211 → 2211-jdk21, future LTS jumps) ships with its own integration-pack cadence, and an exact version match isn't always available at migration start. The skill should encode **warn-not-fail** semantics throughout:

- `detect_state.sh` reports integrations-zip presence as an advisory, not an error — missing ZIP is OK, present ZIP gets a "verify it matches target release" nudge.
- `plan-template.md` Step 0.4 marks the integrations ZIP as OPTIONAL with an explicit note to leave it out rather than substitute an older version.
- `additional-changes.md` Prerequisites section spells out the principle in one paragraph at the top, so anyone running the skill reads it before bootstrapping.

This prevents projects from making the same wrong stopgap decision while not blocking the migration when the matched pack genuinely isn't available yet.

## Promotion suggestion

1. **`scripts/plan-template.md`** Step 0.4 — mark suite ZIP REQUIRED, integrations ZIP OPTIONAL with a warn-not-fail note:
   > If you cannot source a matching integration pack from SAP SDC, **leave it out rather than use an older/mismatched version**. `bootstrapPlatform` works fine without it; only Phase F OCC smoke tests require a matching pack. Re-add when available.

2. **`scripts/detect_state.sh`** — new "Commerce ZIPs in core-customize/dependencies/" section:
   - Suite ZIP missing → warn with ⚠ (bootstrapPlatform will fail).
   - Integrations ZIP missing → advisory only ("absent — OK to proceed; Phase F OCC tests will need a matching pack later").
   - Integrations ZIP present → report filename with "verify it matches target release" nudge.

3. **`references/additional-changes.md`** — add a "Prerequisites (before starting a migration)" header at the top with a Required / Recommended split, and spell out the "leave it OFF rather than use an older/mismatched pack" principle in one paragraph.
