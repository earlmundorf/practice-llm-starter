---
source_topic: OPENREWRITE_SUPPORT
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/9efd1f6212134dec8236a146cac4c98a.html#update-with-the-help-of-the-openrewrite-recipes
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: 9efd1f6212134dec8236a146cac4c98a
topic_anchor: update-with-the-help-of-the-openrewrite-recipes
sap_version: v2211
---

> **Status: alternative reference path.** The skill's canonical Phase B uses Claude-driven grep-and-edit sweeps over the residues this recipe would auto-fix — see `../phase-guide.md` Phase B and `../../SKILL.md` Phase 2. The OpenRewrite path documented here is kept for codebases with 5+ custom extensions where per-pattern sweep volume outweighs OpenRewrite's setup cost. The content below describes the OpenRewrite option for projects that choose it.

> This file is a pointer. For actual recipe execution, the authoritative reference is **SAP Note 3618495**, mirrored in the skill at `references/sap-notes/3618495-openrewrite-framework-update.md` (with the source PDF alongside it).

## Where the real instructions live

**Primary reference — use this when running recipes:**
- `../sap-notes/3618495-openrewrite-framework-update.md` — step-by-step execution guide, active recipe ID (`com.sap.cx.rewrite.java.SelectiveCommerceCloudFrameworkUpdate`), full `rewriteRun` command, sequencing prereqs, and troubleshooting.
- `../sap-notes/3618495-openrewrite-framework-update.pdf` — the original SAP note (PDF, version 20, released 2026-01-16) if anything in the .md is ambiguous.

**Conceptual overview — use this when deciding whether to use recipes at all:**
- `sap-docs/01-general-update-guide.md` section "Update with the Help of the OpenRewrite Recipes"
- Source URL: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/9efd1f6212134dec8236a146cac4c98a.html#update-with-the-help-of-the-openrewrite-recipes

## Critical sequencing prereq (from SAP Note 3618495)

OpenRewrite must run **while the project is still on JDK 17 + pre-jdk21 2211 package**, NOT on the already-upgraded platform. The recipe modifies source so it will compile under the new runtime — it needs the old runtime to exist as the starting state. Switch to JDK 21 + swap to `2211-jdk21.x` AFTER the recipe run.

If a project has already bumped the platform before running recipes (e.g. by following a plan that front-loads Phase 0 toolchain changes), either:
- Roll the platform back to the latest 2211.x pre-jdk21 temporarily, run recipes, then roll forward, OR
- Attempt the recipes against the new platform anyway (risk: `./gradlew assemble testClasses` prereq fails because custom extensions' `javax.*` imports no longer resolve).

See `../sap-notes/3618495-openrewrite-framework-update.md` "Critical sequencing" section for full rationale.

## Why this file exists separately

The skill's decision-tree and plan templates reference the OpenRewrite pathway often enough that giving it its own filename makes the plan more scannable. The underlying SAP page is the same LOIO as the general update guide — SAP treats it as a sub-section rather than a separate topic.

## What OpenRewrite covers (from .docx; verify against 01-general-update-guide.md)

- `javax.*` → `jakarta.*` namespace migration
- Removal of `@Required` annotation invocations
- Apache Commons Collections / BeanUtils upgrade (most migration steps automated)
- `HttpPutFormContentFilter` → `FormContentFilter` rename (for extensions generated from `ycommercewebservices` template)
- Spring 5 → 6 API adjustments where mechanical

## What OpenRewrite does NOT cover

See `decision-tree.md` Branch 2 and `00-overview.md` for the full list. Summary:
- Tomcat `server.xml` / connector changes
- Custom Ant task definitions
- OAuth / Spring Authorization Server wiring decisions
- `LocalizedHybrisConstraintViolation` subclass changes
- Orphaned type cleanup
- Eclipse `.classpath` + JDK settings
- Third-party libs without a Jakarta build (require replacement, not migration)
