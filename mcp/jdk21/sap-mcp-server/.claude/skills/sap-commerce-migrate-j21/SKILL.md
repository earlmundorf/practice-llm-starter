---
name: sap-commerce-migrate-j21
description: Guides SAP Commerce Cloud migrations from Java 17 or 21 + Spring 5 to Java 21 + Spring 6 + Tomcat 10.1 / Jakarta EE 10 (Update Release 2211-jdk21.1). Use this whenever a developer mentions migrating or upgrading SAP Commerce / Hybris to Java 21, Spring 6, Tomcat 10, Jakarta EE, or the 2211-jdk21.1 framework update — even when they don't say "skill" or "migration" explicitly. Also triggers on: javax→jakarta namespace work in a Commerce codebase, OAuth2 / Spring Authorization Server swaps in Commerce, Spring 5 to 6 Commerce work, OpenRewrite recipe runs against Commerce, or any mention of the September 2025 framework update. Two phases: Plan produces a structured markdown plan in migration-docs/migration-plan.md; Execute (narrow first slice) drives OpenRewrite recipe runs under user checkpoints. Self-improves — captures findings during every run into findings/ for future projects to benefit from.
allowed-tools: [Read, Edit, Write, Bash, Grep, Glob]
---

# SAP Commerce Cloud — Java 21 + Spring 6 migration

## Core principle

**Done = the system builds, Solr works, code works, data matches, and new OCC access is testable end-to-end.** Compiles-clean is necessary but not sufficient. Every phase gate verifies one of these criteria; the migration is not done until all of them pass.

Each criterion has an **operational test** — vibes don't pass gates:

| Criterion | Operational test |
|---|---|
| **System builds** | `./gradlew yall` → BUILD SUCCESSFUL **AND** `./gradlew startServer` reaches "Platform started" **AND** zero `SEVERE` entries in the boot log |
| **Solr works** | `/admin/cores?action=STATUS` returns healthy **AND** indexed doc counts within ±0.5% of pre-migration baseline (per type) **AND** the top-10 search queries return the same product IDs as legacy (relevance preserved) |
| **Code works** | `yunittests` + `yintegrationtests` pass **AND** N user journeys smoke-tested manually (PDP, cart, checkout) **AND** zero `NoClassDefFoundError` / `ClassNotFoundException` in runtime logs |
| **Data matches** | Row counts for 5–10 critical types within tolerance (Product, Customer, Order, Catalog, ContentPage at minimum) **AND** orphaned-types cleanup completed without removing intended data **AND** stale-cart/order deserialization sample passes (see `references/known-incidents.md` #6) |
| **OCC testable** | All endpoint **status codes** return as expected **AND** **response schemas** diff cleanly against the baseline captured in Phase 0 Step 0.0c (no silent shape changes) **AND** Swagger doesn't drift **AND** OAuth flows complete |

When in doubt during execution, ask: *which of the five "done" criteria does this step move us toward, and what does the operational test for that criterion say I need to verify?* If the answer isn't immediate, the step is probably wrong-shaped.

## Scope

This skill runs a SAP Commerce Cloud migration to **Update Release 2211-jdk21.1** (September 2025): Java 21, Spring 6.2.10, Tomcat 10.1, Jakarta EE 10.

For version pins, validation history, and changelog, see `CHANGELOG.md` next to this file.

## Before anything else

1. Read `references/00-overview.md`. Three minutes. It anchors version targets and headline changes so the rest of this file makes sense.
2. List `findings/*.md` (excluding `README.md` and `TEMPLATE.md`). Each finding represents something a prior run learned that isn't yet in the authoritative references. Unpromoted findings still apply — load any whose `applies_to:` frontmatter matches the target project's state.
3. Confirm with the user which project they want to migrate. This skill operates on ONE target project per invocation.

## Two phases

This skill has two phases. Default to **Plan** unless the user explicitly says "execute" or "run" or "apply".

### Phase 1 — Plan

Goal: produce `migration-docs/migration-plan.md` inside the target project, with ordered phased steps, per-step SAP references, Go/No-Go gates, and a watchlist for known incidents. Then stop and wait for user approval.

Steps:

1. **Ensure `migration-docs/` exists in the target project.** All skill outputs (plan, log, supporting-skill-findings) live there — separate from any pre-existing `docs/`. Create on first run if absent: `mkdir -p {{PROJECT_DIR}}/migration-docs`. The directory is the skill's exclusive write target; a target project's existing `docs/` is never touched.
2. **Detect state.** Run `scripts/detect_state.sh <target-project-path>`. Capture the output — it feeds Step 3 (intake). Don't ask the user to fill in fields the script already determined; for fields the script can't determine, Step 3 handles them through the intake template.
3. **Capture intake.** Copy `references/intake-template.md` → `migration-docs/intake.md`. Auto-populate the "Auto-detected state" table from Step 2's output. Then ASK the user how they want to fill in the remaining fields:

   > **Three modes — pick one:**
   > 1. **Q&A walkthrough** — I ask each remaining question one at a time; you approve each answer before moving on. Slower but auditable.
   > 2. **Batched** — I ask all remaining questions in a single message; you answer in one reply. Faster.
   > 3. **Self-fill** — you edit `migration-docs/intake.md` directly; tell me when ready and I'll validate.

   Apply the chosen mode. Validate at the end: all required fields (sections 1, 2, 3, 4 of the intake) must be filled, and the sign-off section must be checked. **If section 4.1 selects Scenario D (production with no backup), HALT — return a clear "plan blocked" message; do not proceed past intake until the user resolves to scenario C with a verified backup mechanism.**

4. **Check blockers.** Deprecated extensions are a hard stop — the project must decide a replacement path BEFORE migrating. If detected (per intake's auto-detected `DEPRECATED_EXTENSIONS` row), write that as the first finding and pause.
5. **Walk the decision tree.** Read `references/decision-tree.md`. For each branch that applies to the detected state (per intake fields), note which `references/sap-docs/*.md` file(s) the plan should cite. Strategy choice (intake 2.1) and Phase B path (intake 2.2) come directly from intake — no separate decision needed here.
6. **Consult authoritative refs as needed.** `references/sap-docs/` holds Markdown mirrors of the SAP Help Portal pages (fetched via the reverse-engineered JSON API — see script comment in `scripts/fetch_sap_docs.sh`). These are authoritative. The full list:
   - `01-general-update-guide.md` — overall update guide, including the fully-manual-update anchor and the OpenRewrite anchor
   - `02-openrewrite-recipes.md` — pointer to the OpenRewrite section of `01`
   - `03-spring-6.md` — Spring 6 landing page (TOC of sub-topics)
   - `04-spring-model-changes.md` — exhaustive bean/model diff
   - `05-tomcat.md` — Tomcat 10 changes landing
   - `06-build-ant-gradle.md` — Ant/Gradle changes
   - `07-testing.md` — test framework changes
   - `08-oauth-authorization-server.md` — Spring Authorization Server (the new OAuth)
   - `09-resource-server.md` — resource server config (OCC side)
   - `10-resttemplate-removal.md` — RestTemplate/OAuth2RestTemplate deprecation
   - `11-smartedit.md` — SmartEdit adjustments
   - `12-web-service-exception-handling.md` — error handler changes
   - `13-release-notes-2211-jdk21.md` — release notes stream for the 2211-jdk21 line
   - `14-update-release-2211.46.md` — specific point-release notes

   Also authoritative: `references/additional-changes.md` — items verbatim from SAP's framework-update .docx that don't have their own dedicated SAP Help page. Covers detailed Spring 6 sub-topics, Apache libraries, caching, JVM/language housekeeping, Olingo, DdlUtils, orphaned types, LocalizedHybrisConstraintViolation, and yacceleratorstorefront-specific jakarta steps. Treat this file as peer-authoritative to `sap-docs/`.
7. **Write the plan.** Start from `references/phase-guide.md`. Write to the target project's `migration-docs/migration-plan.md`. **Substitute all `{{...}}` placeholders from `migration-docs/intake.md`** — see the substitution map at the bottom of the intake template (e.g., `{{IN_PLACE | COPY}}` from intake 2.1, `{{LEGACY_PROJECT_DIR}}` from 3.2, `{{TARGET_COMMERCE_VERSION}}` from 1.1). Phases run A→H as in the guide; skip any that don't apply to the detected state. For Phase 0, copy ONLY the subsection matching the chosen strategy (`Phase 0-inplace` or `Phase 0-copy`); drop the other. **For Phase 0-prep Steps 0.0a/b**: tailor based on intake 4.1 (persistence scenario) — see the conditional branches inline in `phase-guide.md`. Every step must link to a specific `references/sap-docs/*.md` (and, if relevant, to `references/known-incidents.md` entries).
8. **Record supporting-skill findings.** Write `migration-docs/supporting-skill-findings.md` in the target project alongside the plan. This captures what the skill wants to learn FROM this run — e.g., "I didn't know whether extension X uses OAuth2RestTemplate; confirm during Phase D and update `decision-tree.md` Branch 3".
9. **Stop.** Tell the user the plan is ready at `migration-docs/migration-plan.md` and wait for approval to proceed to Phase 2.

**Commit cadence:** per-step, not per-phase. Tag each phase gate (`phase-0-complete`, `phase-A-complete`, etc.). This keeps `git bisect` useful and keeps `migration-docs/migration-log.md` entries aligned 1:1 with commits.

### Phase 2 — Execute (Claude-driven sweeps, canonical path)

Phase B's canonical path is **Claude-driven grep-and-edit sweeps** over each residue category. We use SAP Note 3618495's OpenRewrite recipe catalog as the specification for what residues exist; each recipe's scope becomes a grep-then-edit pass that Claude executes against the project's custom extensions. This runs on the upgraded platform state (JDK 21 + 2211-jdk21.x) — no toolchain rollback, no 16 GB JVM, no login-gated jar downloads, no git-rename dance. Validated 2026-04-30 on the first real project: 2 custom extensions, 27 files touched, 80 → 0 compile errors in three sweep passes (~15 min active work).

OpenRewrite itself remains a documented alternative in `references/sap-docs/02-openrewrite-recipes.md` + `references/sap-notes/3618495-openrewrite-framework-update.md` — use it for codebases with 5+ custom extensions where grep-per-pattern volume is unmanageable. For the ≤5-extension common case, Claude sweeps are faster to set up and easier to review.

Steps:

1. **Confirm the approved plan exists** at `migration-docs/migration-plan.md`.
2. **Walk Phase B's residue catalog** (see `references/phase-guide.md` Phase B). For each sweep:
   - Run the inventory grep to count matches.
   - If matches exist, apply the transform across files (Claude uses `Edit replace_all` per file, inventory-first to catch edge cases where the pattern appears outside import-lines).
   - Re-run the grep. Zero matches = sweep complete. Move on.
3. **Checkpoint frequently.** Each sweep is a natural commit boundary. Don't batch unrelated residues into one commit — the diff stays readable when each sweep is isolated.
4. **Verify.** `./gradlew yall` (or `ybuild`) → BUILD SUCCESSFUL confirms the whole sweep pass landed cleanly.
5. **Capture findings.** Anything the catalog didn't cover — new residues, project-specific edge cases, Mockito-like gaps — gets a `findings/YYYY-MM-DD-{slug}.md` entry so the next project benefits.
6. **Hand back.** Phases C (Tomcat), D (OAuth), E (tests), F (data/OCC), G (SmartEdit) remain human-driven for this skill version; surface the next phase's step and wait.

Do NOT try to drive Phase C onward automatically — that's out of scope for this skill iteration.

## Self-improvement (the contract)

This skill is designed to get sharper every time it runs. Enforce this contract:

- **Start of each run:** read `findings/*.md` into context alongside the overview.
- **During the run:** when the skill hits something that isn't in the authoritative refs, write a `findings/YYYY-MM-DD-{slug}.md` entry using `findings/TEMPLATE.md`. Even tiny findings count.
- **End of each run:** summarize new findings to the user and propose which ones should be promoted into `references/`. Promotion is always user-approved — we don't want to silently mutate references.
- **Confirmation from first run (2026-04-30):** the loop worked. Findings captured mid-execution refined each other before any needed acting on (e.g. a "missing ant" worry in Step 0.1 softened once Step 0.7 showed the platform uses bundled Ant). Keep doing this.

The mechanism is simple: findings are ordinary Markdown files with frontmatter. The SKILL.md loads them at the top of the plan phase. Promoted findings are edited into the reference files manually (or with a later supporting tool) and their finding file is marked `status: promoted`.

## Refreshing the SAP docs

The SAP Help Portal is a Vue SPA. `scripts/fetch_sap_docs.sh` mirrors the pages listed in `scripts/upgrade_resources.md` (next to the script) as Markdown into `references/sap-docs/` using the reverse-engineered `/http.svc/deliverableMetadata` + `/http.svc/pagecontent` JSON API. Re-run any time SAP updates docs:

```bash
bash .claude/skills/sap-commerce-migrate-j21/scripts/fetch_sap_docs.sh
```

Known limitations of the mirror:
- Some pages (e.g., `03-spring-6.md`, `05-tomcat.md`) are landing pages linking to sub-topics with their own LOIOs. The mirror captures the landing. If a sub-topic is needed verbatim, add its LOIO to the `LOIO_FILE` map in the script and re-run.
- Anchor fragments (`#fully-manual-update`) aren't separate pages — they're sections within one topic. The body has them all.

## When to defer to existing skills

This skill is narrow on purpose — it doesn't re-teach SAP Commerce from scratch. Cross-reference the peer skills when appropriate:
- `sap-commerce` — base Commerce reference (type system, ImpEx, FlexibleSearch, architecture)
- `sap-best-practices` — platform code-review criteria
- `java-best-practices` — Java code-review criteria
- `impex` — ImpEx linting and patterns
- `commerce-rpi-code` — Research/Plan/Implement workflow ergonomics this skill mirrors

If the user asks about something that's a general Commerce question (not migration-specific), point to those skills instead of answering from within this one.

## Scope boundaries

IN scope for this skill:
- Planning a migration to 2211-jdk21.1
- Running SAP's OpenRewrite recipes (guided, checkpointed)
- Verifying migration outcomes against the gates in `references/verification-checklist.md`
- Capturing findings to make the next project easier

OUT of scope (for this iteration):
- Jira/Confluence integration (user excluded)
- Autonomous execution of non-OpenRewrite phases
- Writing the actual extension code rewrites that OpenRewrite doesn't handle (user drives those; skill advises)
- General SAP Commerce onboarding (use `sap-commerce` skill)

## Operator notes for Claude

- **Skill invocation should be confirmatory.** When this skill fires, briefly confirm the target project and phase with the user before consuming context by reading all references. Load lazily based on the decision tree.
- **Don't paraphrase SAP.** When citing behavior, link to the exact `references/sap-docs/` file. If the reference disagrees with a finding, trust the finding FIRST (it's newer) but flag the conflict for the user.
- **Stop at phase gates.** The verification checklist's gates are user-approval points. Never blast past a gate even if the build is green — the user confirms.
- **Keep plans in the target project.** Both `migration-docs/migration-plan.md` and `migration-docs/supporting-skill-findings.md` live in the PROJECT being migrated, in a dedicated `migration-docs/` directory the skill creates on first run. Never write to the project's pre-existing `docs/` (if any) — that belongs to the project. The skill directory is for the skill's own assets.
- **Findings are for the skill.** They live inside the skill directory (`findings/`). Don't confuse them with `supporting-skill-findings.md`, which is a per-project artifact.
