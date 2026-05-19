---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: B (surfaced at B.1)
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: critical-process-gap
status: promoted
promoted_commit: 1dbbb25
related_refs:
  - references/sap-notes/3618495-openrewrite-framework-update.md
  - scripts/plan-template.md (Phase B sweep catalog)
  - references/00-overview.md
  - references/decision-tree.md Branch 0
promotion_target: scripts/plan-template.md — re-order Phase 0 vs Phase B.1; either defer platform bump until after B.1 OR document the rollback-then-roll-forward pattern explicitly
---

## What happened

At B.1, while preparing to run the SAP-provided OpenRewrite recipe, discovered that **SAP Note 3618495 explicitly requires the project to be on JDK 17 + the latest pre-jdk21 2211 package** when `./gradlew rewriteRun` is invoked. Direct quotes from the note:

> "At first, you are building your current version of SAP Commerce Cloud (at state before Framework Update), so you should still use JDK17 for the build process. Switch to JDK21 only after all the extensions used in your configuration are updated."

> "Use your current local environment for JDK17 (with the newest 2211 package, prior to 2211-jdk21)."

But Phase 0 of our migration plan (and the skill's `plan-template.md` Phase 0) did the opposite:
- Step 0.5 pinned `org.gradle.java.home=/Users/.../21.0.11-sapmchn` (SapMachine 21)
- Step 0.6 bumped `manifest.json` `commerceSuiteVersion` from `2211.50` → `2211-jdk21.9`
- Step 0.7 ran `bootstrapPlatform` on the new platform, which installed 2211-jdk21.9

We front-loaded the platform+JDK jump that SAP says to do AFTER OpenRewrite. That inversion breaks the recipe's expected preconditions: Step 4.4 of the note requires `./gradlew assemble testClasses` to succeed before `rewriteRun`, and our custom extensions (`coremcp`, `sampledatamcp`) still contain `javax.*` imports that won't resolve on Tomcat 10.1 / Jakarta EE 10.

## Context

- SAP Note 3618495 (v20, released 2026-01-16) is the authoritative reference for the OpenRewrite path.
- The recipe's job is to modify source code — e.g. `javax.servlet.*` → `jakarta.servlet.*`, remove `@Required`, etc. To do that, it needs the source to parse + compile in its CURRENT state. That state is JDK 17 + Tomcat 9 / javax.*.
- After the recipe runs, the source becomes jakarta-native and compiles under JDK 21 + Tomcat 10.1. THEN you switch the runtime.
- Our project right now: platform = 2211-jdk21.9 (jakarta), source = 2211.50 (javax). They mismatch, and `./gradlew assemble` will fail compilation on any import resolution.
- The skill's Phase 0 plan-template combined "merge directories" (legitimate Phase 0 work) with "bump toolchain" (belongs AFTER recipe run). This is the root error.

## The SAP-doc gap (if applicable)

SAP's general update guide (`01-general-update-guide.md`) doesn't loudly warn about this ordering — you have to read SAP Note 3618495 to discover it. The skill's decision-tree and plan-template inherited the gap: they treat "set up the new runtime" as prep work rather than as a post-recipe activity.

The `00-overview.md` "Toolchain compatibility confirmed" subsection (promoted from an earlier finding) also contributes to the confusion. That finding showed `sap.commerce.build 5.0.2` works on JDK 21 + 2211-jdk21.x — a fact that's true but only relevant AFTER OpenRewrite. Readers may interpret it as "it's safe to jump early", which is exactly the trap we fell into.

## The fix that worked

**Chose Option C — skip OpenRewrite, keep Phase 0's upfront platform bump, and do Claude-driven sweeps on the upgraded source.** Validated end-to-end 2026-04-30 on this 2-extension project: ~15 min active work, 80 → 0 compile errors in three sweep passes (javax → jakarta, `@Required` removal, Mockito `Matchers` → `ArgumentMatchers`). Commits `74f15f5` (B.0+B.1+B.2a) and `241d04f` (B.2b); full Phase B landed at `2404b62` with tag `phase-B-complete`.

**Key insight from the user:** sweeps don't have the runtime-state precondition that OpenRewrite does. OpenRewrite parses source through a recipe engine that expects the source to compile in its CURRENT state — which means the upgraded platform blocks it because the upgrade broke javax imports. Claude-driven sweeps edit source directly: they don't need the runtime to reflect the pre-migration state, they just need the source on disk. So Phase 0's upfront platform + JDK bump works fine as-is — no rollback, no "phase ordering trap" when sweeps are the path.

The three originally-documented options now collapse to one canonical path (sweeps) with OpenRewrite as a documented alternative for large codebases. Options A (rollback + run + roll-forward) and B (try OpenRewrite on upgraded platform) were both predicated on OpenRewrite being the primary path. With sweeps canonical, they become footnotes.

## Why this generalizes

Every SAP Commerce project migrating to 2211-jdk21.x will hit Phase B source residues. The choice between sweep-and-edit and OpenRewrite is a project-size judgment, not a correctness issue:

- **≤5 custom extensions** → sweeps faster (validated).
- **5–15 extensions** → judgment call (OpenRewrite setup cost amortizes if the team's familiar with it).
- **15+ extensions** → OpenRewrite's parallel recipe runs win.

The "Phase 0 ordering trap" only exists under the old framing where OpenRewrite was the default. Reframing the skill around Claude-driven sweeps as canonical eliminates the trap entirely.

## Promotion suggestion (what actually got promoted)

1. **`scripts/plan-template.md` Phase B** — rewritten as a residue catalog (B.0 decision point, B.1–B.11 sweep subsections, B.gate). Each subsection: inventory grep, transform, verification grep. Phase 0 is NOT split — the upfront platform bump stays.

2. **`SKILL.md` Phase 2** — reframed from "drive OpenRewrite" to "Claude-driven sweeps canonical, OpenRewrite alternative."

3. **`references/decision-tree.md`** — new Branch 0 "Which Phase B path?" with the extension-count heuristic.

4. **`references/00-overview.md` "Toolchain compatibility confirmed"** — qualifier added: the Phase 0 upfront bump is what *makes* sweeps possible. OpenRewrite's different sequencing is explicitly scoped to its own path.

5. **`references/sap-docs/02-openrewrite-recipes.md`** — banner marking it as "alternative reference path," not primary.

6. **`references/sap-notes/3618495-openrewrite-framework-update.md`** — kept as-is. Its "Critical sequencing" section is correct for the OpenRewrite path; it's no longer the default but remains the authoritative spec for what residues exist (used as the sweep catalog source).

## What we rejected from the original promotion proposal

- **Split Phase 0 into 0a + 0b** — rejected. Not needed under sweeps; the upfront platform bump is fine.
- **New Phase B.5 runtime swap** — rejected. No swap needed; Phase 0's state IS the runtime Phase B runs on.
- **Add `note:` pointer to sap-commerce-build-plugin-works-on-jdk21 finding** — rejected. That finding is already correct and its promoted qualifier in `00-overview.md` now explains the sweep-path compatibility directly.
