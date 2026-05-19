---
date: 2026-05-01
project: skill-design
phase: 0-inplace
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.x
  strategy: in-place
kind: gap
status: partially-resolved
resolved_2026-05-01:
  sub_case: fresh-target (no prior bin/platform extraction)
  outcome: A — clean first extract
  evidence: |
    Run on SAP-JDK21-Migration testbed 2026-05-01 13:53.
    `./gradlew bootstrapPlatform --no-daemon` against an absent bin/platform/
    with manifest pinned at "2211-jdk21.9": BUILD SUCCESSFUL in 38s.
    `bin/platform/build.number` reports version=2211-jdk21.9.
    All modules extracted under bin/modules/. hybris/config/ generated with
    dev-config overlay applied.
unresolved_sub_case:
  sub_case: existing-extraction-version-upgrade (bin/platform/ already present at a different version)
  status: still unknown — requires a project that's been bootstrapped on a prior version
related_refs:
  - references/decision-tree.md (Pre-branch decision: migration strategy)
  - references/phase-guide.md (Phase 0-inplace, Step 0.5)
  - references/00-overview.md (Toolchain compatibility confirmed)
promotion_target: references/phase-guide.md (Phase 0-inplace Step 0.5) + references/00-overview.md (Toolchain compatibility confirmed)
---

## What happened

Skill design call on 2026-05-01: added an in-place migration strategy as a peer to the validated copy-to-new-repo strategy. The in-place flow assumes `bootstrapPlatform` will not version-upgrade an already-extracted `core-customize/hybris/bin/platform/` from a prior `commerceSuiteVersion` — so Phase 0-inplace Step 0.5 wipes `bin/platform`, `bin/modules`, and `hybris/config` before bootstrap.

This wipe step is **defensive** and may be unnecessary. The first in-place migration run is the validation event.

## Context

- Skill: `sap-commerce-migrate-j21`
- Build plugin: `sap.commerce.build` 5.0.2 (per `references/00-overview.md` "Toolchain compatibility confirmed")
- `bootstrapPlatform` is a Gradle task provided by that plugin; it extracts `dependencies/hybris-commerce-suite-<version>.zip` into `core-customize/hybris/bin/`.
- The validated copy-to-new-repo path (2026-04-30) ran against an EMPTY target — no prior extraction. So that run does not answer the in-place question.

## The SAP-doc gap (if applicable)

Neither the SAP framework-update .docx nor the OpenRewrite SAP Note (3618495) addresses what happens when `bootstrapPlatform` finds an existing `bin/platform/` at a different version. SAP's docs assume fresh-target setup.

## The fix that worked

Not yet known. Two outcomes to disambiguate on first in-place run:

**Outcome A — `bootstrapPlatform` upgrades in place.** If running `./gradlew bootstrapPlatform --no-daemon` against a populated `bin/platform/` extracts the new ZIP cleanly and `build.number` reports the new version: the wipe step is over-cautious and should be removed from Phase 0-inplace. Promote into a finding marked `kind: confirmation` and edit the plan-template to drop Step 0.5.

**Outcome B — `bootstrapPlatform` skips or errors on existing extraction.** If it logs "platform already extracted" or fails with a hash mismatch: the wipe step is correct. Promote into `00-overview.md` toolchain section as a documented in-place prerequisite.

To test:
```bash
# On the first in-place migration, try the optimistic path first:
git switch -c migration/jdk21
# (skip Step 0.5 — leave bin/platform from old version in place)
./gradlew bootstrapPlatform --no-daemon
grep -E "^version" core-customize/hybris/bin/platform/build.number
# If version matches the bumped commerceSuiteVersion → Outcome A
# If still old version, or build failed → Outcome B; run Step 0.5's wipe and retry
```

## Why this generalizes

Every in-place migration after the first will benefit from a definitive answer. The current "wipe defensively" advice is safe but adds 5–10 minutes of re-extract time and may obscure that the build plugin handles the case fine on its own.

## Promotion suggestion

After the first in-place run resolves Outcome A vs. B:

- **If A:** edit `references/phase-guide.md` Phase 0-inplace to delete Step 0.5; merge the relevant prose into Step 0.6's note. Edit `references/00-overview.md` "Toolchain compatibility confirmed" to add: "`bootstrapPlatform` performs in-place version upgrade on an existing `bin/platform/`."
- **If B:** keep Step 0.5; promote a one-line note into `references/00-overview.md` "Toolchain compatibility confirmed": "In-place migrations must wipe `bin/platform`, `bin/modules`, `hybris/config` before `bootstrapPlatform` — the build plugin does not version-upgrade an existing extraction." Then mark this finding `status: promoted`.
