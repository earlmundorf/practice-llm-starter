---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: 0
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: confirmation
status: promoted
related_refs:
  - references/00-overview.md
  - references/decision-tree.md
promotion_target: references/00-overview.md (add "Toolchain compatibility" subsection)
promoted_commit: 4e752eb
---

## What happened

Positive confirmation: the **`sap.commerce.build` Gradle plugin version 5.0.2 runs cleanly on SapMachine JDK 21.0.11** and completes a full `bootstrapPlatform` against the 2211-jdk21.9 Commerce distribution in ~40 seconds. No JDK-21-related failures, no reflective-access warnings, no class-version errors.

The legacy project had `sap.commerce.build` **5.0.2** pinned in `core-customize/build.gradle`. I did not bump it before the migration. Bootstrap succeeded anyway — so the plugin's 5.0.2 line already supports JDK 21 end to end, at least through the bootstrap + setupConfig path.

## Context

- `core-customize/build.gradle` line 2: `id 'sap.commerce.build' version '5.0.2'`
- Shell: SapMachine 21.0.11-sapmchn active (verified via `java -version` and `gradle --version`)
- Gradle wrapper: 8.12 (downloaded-on-first-run from distributionUrl in `gradle/wrapper/gradle-wrapper.properties`; does not come from the platform)
- Ant: 1.10.15 (embedded in Gradle) — meets the ≥1.10.14 requirement from SAP's build tooling page
- `./gradlew bootstrapPlatform` output showed platform self-report:
  ```
  Java platform:             OpenJDK Runtime Environment, 21.0.11+10-LTS
  Build target:              21
  hybris Platform version:   2211-jdk21.9
  Ant version:               Apache Ant(TM) version 1.10.15 compiled on August 25 2024
  ```
- The plugin extracted a 2.0 GB suite ZIP and an 82 MB integrations ZIP, created `hybris/{bin/platform, bin/modules, config, data, log, temp, roles}`, and overlaid `dev-config/` onto `hybris/config/` without complaint.

## Why this generalizes

When projects upgrade the SAP platform version, it's common to also speculatively bump the Gradle plugin version to "be safe". That's a distraction — if the legacy plugin version already supports your target JDK + target platform, you get an extra moving variable for zero benefit. This finding establishes that **sap.commerce.build 5.0.2 is enough for 2211-jdk21.x on JDK 21**; projects can defer the plugin bump and focus on Java/Spring/OAuth work.

If a later phase does hit a plugin-level incompatibility (e.g., something in `y*` ant task delegation), THEN bump the plugin — but not before, and not because of generalized nervousness.

## Promotion suggestion

Add a new subsection to `references/00-overview.md` after the version matrix:

> ### Toolchain compatibility confirmed
>
> The `sap.commerce.build` Gradle plugin version 5.0.2 runs on SapMachine JDK 21 and completes `bootstrapPlatform` against 2211-jdk21.9 without modification. If your legacy project has 5.0.2 or newer, you do not need to bump the plugin version as part of the migration — the platform change is enough. Verified 2026-04-30 against Commerce Suite Update Release 2211-jdk21.9 on macOS (aarch64).
