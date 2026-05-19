---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: A
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: gap
status: promoted
promoted_commit: 4f4f247
related_refs:
  - scripts/plan-template.md (Phase A.6)
  - references/verification-checklist.md
promotion_target: scripts/plan-template.md Phase A.6 — replace "./gradlew clean" with "./gradlew tasks"
---

## What happened

Phase A.6 gate in the migration plan says `./gradlew clean` must succeed. On Gradle 8.12 in an SAP Commerce project (`sap.commerce.build` plugin), this fails:

```
FAILURE: Build failed with an exception.
* What went wrong:
Task 'clean' is ambiguous in root project 'sap-mcp-server-g'. Candidates are: 'cleanAll', 'cleanPlatform', 'cleanPlatformIfVersionChanged'.
```

Gradle 8's task name matching is stricter: abbreviation resolves to one candidate or it errors out. `clean` matches three tasks — two from the SAP Commerce plugin (`cleanPlatform`, `cleanPlatformIfVersionChanged`), one from this project's `build.gradle` (`cleanAll`). There is no single task literally named `clean` in a Commerce project.

## Context

- This is NOT a bug — it's the intended Gradle 8 behavior. Earlier Gradle versions tolerated ambiguous abbreviations; Gradle 8 tightened that.
- SAP Commerce projects typically define multiple `clean*` tasks deliberately: `cleanPlatform` wipes only `hybris/bin/platform`, `cleanPlatformIfVersionChanged` is conditional, and project-level `cleanAll` is a broader wipe.
- The intent of the gate check was "does the Gradle build graph resolve?" — not "actually clean anything". `./gradlew tasks` serves that purpose non-destructively.

## The SAP-doc gap (if applicable)

The SAP update guide doesn't call this out because it's a downstream consequence of the Gradle 8 name-matching change, not an SAP-platform change. But the skill's plan-template shouldn't reproduce Gradle 7-era advice verbatim.

## The fix that worked

For the A.6 gate on this project: `./gradlew tasks` — BUILD SUCCESSFUL in 494ms, full task list populated. This verifies:
- Build graph compiles (the `build.gradle` is syntactically valid for Gradle 8.12)
- Plugin `sap.commerce.build` loads correctly
- Project-level tasks resolve (e.g., `bootstrapPlatform`, `startServer`, `groovy`, `impex`, `flexquery`)

Non-destructive, fast (<1s), equivalent gate for the intent.

## Why this generalizes

Every project migrating to 2211-jdk21.x will be on Gradle 8+ (Phase A.2 verifies ≥ 8.5). Every SAP Commerce project defines multiple `clean*` tasks. So every next project hits the same A.6 failure if the plan-template keeps `./gradlew clean`.

## Promotion suggestion

1. **`scripts/plan-template.md`** Phase A.6 — replace the gate bullet:

   ```markdown
   # Before:
   - [ ] `./gradlew clean` succeeds.

   # After:
   - [ ] `./gradlew tasks` succeeds (verifies the Gradle build graph resolves cleanly on Gradle 8+).
   - [ ] If the project has a specific cleanup intent at this gate, use the explicit task name: `./gradlew cleanAll` (broadest, destructive — wipes `hybris/bin/platform` + config + data), `./gradlew cleanPlatform` (platform only), or `./gradlew cleanPlatformIfVersionChanged` (conditional). **Never `./gradlew clean`** — ambiguous on Gradle 8+ in SAP Commerce projects.
   ```

2. **`references/verification-checklist.md`** — if there's a Phase 1 / Gate 1 check referencing `./gradlew clean`, update the same way.

3. **`references/known-incidents.md`** — optional minor entry:

   > **N. `./gradlew clean` is ambiguous on Gradle 8 SAP Commerce projects**
   > - **Symptom:** `Task 'clean' is ambiguous in root project`. Candidates: `cleanAll`, `cleanPlatform`, `cleanPlatformIfVersionChanged`.
   > - **Cause:** Gradle 8 tightened task-name matching. SAP Commerce projects define multiple `clean*` tasks.
   > - **Fix:** use the explicit task name. For a non-destructive build-graph check, prefer `./gradlew tasks`.
