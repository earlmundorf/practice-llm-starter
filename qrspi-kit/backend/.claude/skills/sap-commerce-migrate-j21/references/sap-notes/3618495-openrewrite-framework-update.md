---
sap_note: 3618495
title: Using OpenRewrite recipes to support SAP Commerce Cloud JDK21 Framework Update adoption
component: CEC-SCC-PLA-PL
note_version: 20
released: 2026-01-16
sap_commerce_target: 2211-jdk21.1
pdf_path: references/sap-notes/3618495-openrewrite-framework-update.pdf
---

> This file is a summary + extracted commands from SAP Note 3618495. The full PDF is alongside at `3618495-openrewrite-framework-update.pdf` — consult it for full troubleshooting context.

## Attachments SAP provides (download separately from the note page)

1. **`sap-commerce-framework-update-recipes-1.1.3.jar`** — the OpenRewrite recipes JAR (~223 KB). Contains `com.sap.cx.rewrite.java.*` recipes.
2. **`init.gradle`** — Gradle initialization script that wires up OpenRewrite + recipe exclusions (~19 KB).
3. **`yant.jar`** — Enhanced yant for `ant gradle` Gradle-project-generation (~439 KB). Replaces the platform's bootstrap yant.jar for the purpose of generating build.gradle.

These are manual downloads from the SAP Note attachments section (SAP customer login required).

## The active recipe

```
com.sap.cx.rewrite.java.SelectiveCommerceCloudFrameworkUpdate
```

Scope of changes driven by this recipe (from SAP's framework changelog):
- `javax.*` → `jakarta.*` namespace migration (servlet, persistence, validation, annotation)
- Removal of `@Required` annotation invocations
- Apache Commons Collections / BeanUtils upgrade adjustments
- `HttpPutFormContentFilter` → `FormContentFilter` (for extensions derived from `ycommercewebservices`)
- Selected Spring 5 → 6 API rewrites (the mechanical ones)

## ⚠ Critical sequencing — SAP-prescribed order

**The recipe expects to run on the PRE-jdk21 state**, not on the target platform:

> "At first, you are building your current version of SAP Commerce Cloud (at state before Framework Update), so you should still use JDK17 for the build process. Switch to JDK21 only after all the extensions used in your configuration are updated."

> "Use your current local environment for JDK17 (with the newest 2211 package, prior to 2211-jdk21)."

So the canonical order is:

1. Project is on JDK17 + 2211 (pre-jdk21), project compiles with `ant clean all` (OpenRewrite requires a compilable Gradle project as input).
2. Run `./gradlew rewriteRun` → recipe rewrites custom-extension source to be jakarta/@Required-free/etc.
3. After the recipe run: switch Java to JDK21 SapMachine + swap platform to `2211-jdk21.x` (that is Step 8 and 9 of the note).
4. Then `ant clean all` / `ant initialize` (Step 10) to surface remaining manual fixes.

### Implication for our Phase 0

Our Phase 0 bumped `commerceSuiteVersion` to `2211-jdk21.9` AND pinned Java to SapMachine 21 BEFORE B.1. This is the opposite of SAP's prescribed order. Two paths forward:

- **Canonical path** — roll the platform back to the latest 2211.x (pre-jdk21) on JDK 17, run OpenRewrite, then roll forward to 2211-jdk21.9 on SapMachine 21.
- **Compressed path** — try `./gradlew rewriteRun` against the already-upgraded platform. The recipe is source-code-level; if our custom extensions compile (or are close to compiling) under the new runtime, the recipe may still do its transformations. The risk is Step 4.4 (`./gradlew assemble testClasses`) — OpenRewrite requires the project to compile, and our javax imports won't resolve on Tomcat 10.1 (jakarta). This is the most likely failure mode.

See also `../00-overview.md` "Toolchain compatibility confirmed" (validated on the destination platform) vs. this SAP-documented precondition (source platform must compile). Both are true; they just apply at different points in the timeline.

## The full command

All commands assume cwd is `core-customize/hybris/bin/platform/`.

### Step 2: Prepare localextensions.xml

Edit `hybris/config/localextensions.xml` (generated from `dev-config/localextensions.xml` by `bootstrapPlatform + setupConfig`) to include **only** the custom extensions to update + their dependencies. Fewer extensions = less memory + faster runs. Platform and modules are excluded by default via init.gradle.

### Step 3: Make sure the project compiles with ant

```bash
source ./setantenv.sh
ant clean all
ant initialize     # optional but confirms the config is valid
```

### Step 4: Generate Gradle configuration using the new yant.jar

```bash
# Step 4.1: replace bootstrap yant.jar with the one from the SAP Note
cd bootstrap/bin
mv /<path-to-downloaded>/yant.jar .
cd ../..

# Step 4.2: generate Gradle build files
ant gradle

# Step 4.3: generate a Gradle wrapper pinned to 7.6.6 (NOT 8.x yet — the generated
#           build.gradle uses APIs removed in Gradle 8. Pinning 7.6.6 is required.)
gradle wrapper --gradle-version 7.6.6

# Step 4.4: verify it compiles through Gradle too (OpenRewrite requires this)
./gradlew assemble testClasses
```

### Step 5: Set REWRITE_JAR_PATH env var

```bash
export REWRITE_JAR_PATH="/<abs-path-to>/sap-commerce-framework-update-recipes-1.1.3.jar"
```

### Step 6: Temporarily move .git

OpenRewrite auto-detects git repos and overrides its base directory — that conflicts with `commerceUpdatePaths`. Must rename:

```bash
# From git root:
mv .git .gitTemporary
# ...run OpenRewrite...
# Restore after:
mv .gitTemporary .git
```

### Step 7: Run the recipe

```bash
ulimit -n 81920                                # raise open-file limit (required)

./gradlew rewriteRun \
  --init-script=$PATH_TO_INIT_GRADLE \
  -PcommerceUpdatePaths="$COMMERCE_UPDATE_PATHS" \
  --no-daemon \
  -Dorg.gradle.jvmargs=-Xmx16G \
  -Drewrite.activeRecipe=com.sap.cx.rewrite.java.SelectiveCommerceCloudFrameworkUpdate
```

Parameters:

- `$PATH_TO_INIT_GRADLE` — absolute path to the downloaded `init.gradle`.
- `$COMMERCE_UPDATE_PATHS` — pipe-separated paths **relative to `hybris/bin/platform/`** matching the extensions to update. Example for this project: `../../custom/coremcp|../../custom/sampledatamcp`.

For a dry-run (produces a patch file, does NOT modify source):

```bash
./gradlew rewriteDryRun ...  # same args, but takes the same time as rewriteRun
```

### Step 8–11: Switch to JDK21 / rebuild / iterate

Per note:

```bash
# Switch shell Java to SapMachine 21
sdk use java 21.0.11-sapmchn

# Regenerate Gradle build files for the new platform
source ./setantenv.sh
ant gradle
gradle wrapper --gradle-version 8.8

# Attempt build + reveal remaining manual-fix surface
source ./setantenv.sh
ant clean all
ant initialize
./hybrisserver.sh
```

## Important limitations

- **Groovy + JSP**: OpenRewrite has very limited support. Expect parsing warnings; apply changes in these files manually.
- **Symlinks**: not supported. All extensions must be regular directories.
- **Memory**: 16 GB recommended (`-Xmx16G`). Large projects may need 32 GB. On WSL, adjust `.wslconfig`.
- **Time**: minutes to hours depending on project + extension count.
- **No change report**: `rewriteRun` modifies files directly. Use `rewriteDryRun` to get a patch without touching source.
- **JDK17 SapMachine ≥ 17.0.15 required** for `ulimit -n 81920` to be honored. Older SapMachine (17.0.9) ignored it → "Too many open files".

## Troubleshooting highlights (from note's section)

1. **Too many open files** → bump JDK17 SapMachine to ≥17.0.15, set `ulimit -n 81920` in the same shell.
2. **No/limited changes** → `-PcommerceUpdatePaths` is probably wrong. Check log for "The following extensions match update pattern: ..." — if your extension isn't listed, fix the path. Paths are relative to `hybris/bin/platform/`.
3. **OOM** → reduce localextensions.xml to only needed extensions; bump `-Xmx` to `-Xmx32G`; run in batches.
4. **Gradle compiles fail while ant compiles succeed** → likely missing `extensioninfo.xml` dependencies, OR a src/testsrc cross-reference. Fix extensioninfo.xml and re-run `ant gradle`.
5. **Git-related failure** → you forgot Step 6 (`mv .git .gitTemporary`).
6. **Recipe crashes on customer code** → comment out the offending code, re-run, restore + hand-adjust after.
7. **`gradle wrapper --gradle-version 7.6.6` fails** → use Gradle 7.x (preferably 7.6.6) for the wrapper task. The generated build.gradle is NOT Gradle 8 compatible.
8. **Lombok** → add an `ext { lombokVersion = '1.18.32' }` + `configure(lombokProjects.collect { project(it) }) { dependencies { compileOnly ... annotationProcessor ... } }` block to the generated build.gradle (see PDF for full snippet).

## Where to go next

- If running OpenRewrite end-to-end on this project, follow this file.
- If deciding whether OpenRewrite is worth it vs. a manual sweep, see `../decision-tree.md` Branch 2 and `../00-overview.md` "What OpenRewrite does NOT cover".
- For manual-only coverage of Spring 6 details, see `../sap-docs/03-spring-6.md` and `../additional-changes.md` (the Spring 6 sub-topics section).
