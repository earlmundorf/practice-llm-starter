# Changelog

## Validated against

| Component | Version |
|---|---|
| SAP Commerce | 2211-jdk21.9 |
| JDK | SapMachine 21.0.11 |
| `sap.commerce.build` Gradle plugin | 5.0.2 |
| Gradle wrapper | 8.12 |
| Ant (platform-bundled) | 1.10.15 |

## Validation history

| Date | Project | Strategy | Outcome |
|---|---|---|---|
| 2026-04-30 | upgrade-21-mcp-server-g | Copy-to-new-repo | ✓ Validated. 80 → 0 compile errors in three Phase B sweep passes (~15 min active). Generated 12 findings. |
| 2026-05-01 | (pending) | In-place | ⏳ Pending. First in-place run is finding-generating — see `findings/2026-05-01-inplace-bootstrap-platform-behavior-unknown.md`. |

### Caveats on the validation sample

**Sample size is small.** Validated against **one** project with **two** custom extensions (`coremcp`, `sampledatamcp`). Inferences about scale and timing should be taken with appropriate uncertainty:

- **Time-per-sweep does not scale linearly with extension count.** A real B2C accelerator with 30–50 custom extensions will exceed the 2026-04-30 wall-clock estimate (~15 min) substantially. Realistic estimate at scale: 4–6 hours of active editing for Phase B alone, possibly more if extensions have heavy cross-coupling around OAuth or the cart/order types.
- **The decision-tree's "≤5 extensions → sweeps default" heuristic** (`references/decision-tree.md` Branch 0) is informed by ONE data point at the low end of that range. At 5–15 extensions sweeps are still likely the right call; at 15+ the OpenRewrite path's setup cost may amortize better.
- **Some categories of findings cannot surface from a 2-extension project.** Things that scale only with extension count (cross-extension Spring alias conflicts, multi-extension OAuth wiring, extensions extending other extensions' platform types) won't be caught until a higher-extension project runs the skill.
- **No production data has been migrated under this skill.** The 2026-04-30 run was on dev data with no prod-grade row counts, no consumer apps depending on OCC contracts, no real cart/order persistence load. Architect concerns flagged in v0.4.0 (cart deserialization, OCC contract drift, Solr major-version timing) are not yet validated against real prod scale.

**Implication:** the skill is well-suited for guiding migrations but its **estimates** are anchored to one small project. Tighten estimates as more projects run; capture deviations in `findings/`.

## Releases

### 0.5.0 — 2026-05-01

**Intake template + scenario-conditional safety net:**

- **`references/intake-template.md`** — single fillable form capturing all migration inputs the skill needs. Replaces scattered `{{placeholders}}` across `phase-guide.md` with one centralized artefact written to `migration-docs/intake.md` per project. Auto-detected fields populate from `detect_state.sh`; user-input fields are collected interactively.
- **SKILL.md Phase 1 Step 3 — "Capture intake."** New step between detect-state and check-blockers. When the skill runs, it asks the user to choose **Q&A walkthrough** (one question at a time, approval gate per answer), **batched** (all questions in one message), or **self-fill** (user edits intake.md directly). Step 4 (was "Choose migration strategy") absorbed into intake; renumbered downstream steps.
- **Persistence-story branching (intake 4.1).** Five scenarios for "data persistence story": A (HSQLDB / in-memory), B (ImpEx-seeded dev), C (prod with backup), D (prod without backup), E (session-only). Each routes Phase 0-prep Steps 0.0a/b to a different behavior — skip, conditional, or **HARD-HALT plan generation on Scenario D until backup capability exists.**
- **Phase 0-prep Steps 0.0a and 0.0b are now scenario-conditional.** Replaces the previous "mandatory for prod" coarse rule with five explicit branches per step. Scenario D explicitly halts; scenarios A/B/E document why backup isn't applicable; scenario C executes the verified-restorable backup.
- **Known-incident #7** — "Migration started without backup capability → unrecoverable on failure." Captures the worst-case failure mode the intake-step's Scenario-D halt is designed to prevent. Includes preventive and reactive remediation paths.
- **Substitution map in intake template** — explicit table mapping intake fields to `phase-guide.md` `{{placeholders}}`. Plan-generation step (SKILL.md Phase 1 Step 7) now substitutes from `intake.md`, not from ad-hoc user input.

### 0.4.0 — 2026-05-01

**Architect-driven hardening (joint review with Claude skills expert):**

- **Phase 0-prep — pre-migration safety net (mandatory).** New subsection in `phase-guide.md` Phase 0, applies to both strategies. Steps 0.0a–0.0f: DB backup, Solr core export, OCC contract baseline capture, legacy build sanity, cart/order quiesce decision, baseline tag. Code is git-rollback-able; databases and search indexes are not — without this step, a failed migration is unrecoverable.
- **Operationalized the five "done" criteria.** SKILL.md Core principle now includes a per-criterion operational test (e.g., "Solr works" → indexed doc count ±0.5% AND top-10 query relevance preserved, not just "indexes reachable"). `verification-checklist.md` Gates 5/6/7 tightened to match.
- **OCC contract diff in Phase F + Gate 7.** Status-code parity is necessary but not sufficient. Added schema-diff substep that replays Step 0.0c baseline payloads and JSON-diffs against post-migration responses. Catches silent shape changes (field added/removed/null-able-changed) that return 200 but break consumers.
- **Cart/order deserialization risk → known-incidents #6.** Spring 5 → 6 + javax → jakarta can change effective `serialVersionUID` for persisted-blob types. Stale carts/orders may fail to deserialize post-migration with `ClassCastException` or `InvalidClassException`. Documented symptom, three-option fix tree, and mitigation in Step 0.0e.
- **Validation-sample-size caveat.** This CHANGELOG now explicitly notes the 2026-04-30 validation was on ONE 2-extension project. Time estimates do not scale linearly to 30+ extension projects. Architect concerns flagged in this release have not been validated against real production scale.
- **Spring 6 subtleties supplement.** New section in `additional-changes.md` covering eight behaviors not in SAP's digest: `@Transactional` propagation defaults, AOP proxy class generation, `HandlerMethodArgumentResolver` order, `@ModelAttribute` binding nullability, `WebMvcConfigurer` interface changes, reactive/WebFlux interop, AOT/native (do-not-enable for Commerce), parameter name retention, Spring Security 6 authorization rule format. These are diagnostic candidates when the build is green but runtime behavior is subtly wrong.

### 0.3.0 — 2026-05-01

**Skill structure cleanup:**

- Renamed `scripts/plan-template.md` → `references/phase-guide.md` (it's not a template — it's a 280-line phase guide with checkboxes).
- Folded standalone `Phase E2 — Apache libraries + caching` into `Phase B.12`. Phase E2 was a parallel-track artefact; the work is residue-style sweeping that fits naturally in Phase B's catalog.
- Reordered `Phase H — JVM + language housekeeping` to come after `Phase G — SmartEdit`, matching alphabetical sequence.
- Lifted the user's "definition-of-done" into a top-level `## Core principle` section in SKILL.md.
- Added `allowed-tools: [Read, Edit, Write, Bash, Grep, Glob]` to SKILL.md frontmatter.
- Moved version metadata (`version`, `last_updated`, `validated_against`, `validation_history`) out of SKILL.md frontmatter into this file. They cost tokens on every skill-list load and weren't being parsed by Claude Code.
- Consolidated authority hierarchy as canonical in `decision-tree.md`; `00-overview.md` now cross-links instead of restating.

### 0.2.0 — 2026-05-01

**Self-containment + in-place strategy:**

- Added in-place migration strategy as a peer to copy-to-new-repo (`decision-tree.md` "Pre-branch decision"; `phase-guide.md` `Phase 0-inplace` subsection).
- Renamed skill output target from `docs/` to `migration-docs/` to avoid collision with target projects' existing docs.
- Moved upstream source materials into the skill: `docs/upgrade-references/` files now live at `references/upstream/` + `scripts/upgrade_resources.md`. Skill is fully self-contained.
- Dropped duplicate `docs/open_rewrite.pdf` (byte-identical to `references/sap-notes/3618495-openrewrite-framework-update.pdf`).
- Added `INSTALL.md` covering project- vs user-level install, prerequisites, invocation triggers.
- Added `references/upstream/README.md` documenting bundled source materials.
- Added `detect_state.sh` reporting on existing `bin/platform` extraction (informs in-place wipe decision).

### 0.1.0 — 2026-04-30

**Initial release.** Validated end-to-end on `upgrade-21-mcp-server-g`:

- Plan + Execute two-phase workflow.
- Phase 0–H phase guide with per-step checkboxes and Go/No-Go gates.
- 14 SAP Help Portal mirrors at `references/sap-docs/`.
- SAP Note 3618495 mirrored at `references/sap-notes/`.
- Decision tree (`references/decision-tree.md`) dispatches references per detected state.
- Findings system (`findings/`) for self-improvement across runs.
- Verification checklist (`references/verification-checklist.md`) gates each phase.
- 12 initial findings captured during 2026-04-30 validation run.

## Promotion log

Findings promoted into `references/` (with `status: promoted` in their frontmatter):

- (none yet)

## Known unknowns

- **In-place `bootstrapPlatform` behavior** — does the `sap.commerce.build` plugin upgrade an existing `bin/platform/` extraction in place when `commerceSuiteVersion` changes, or must it be wiped first? See `findings/2026-05-01-inplace-bootstrap-platform-behavior-unknown.md`. Resolves on first in-place run.
- **OpenRewrite recipe path validation** — the skill defaults to Claude-driven sweeps, but the OpenRewrite alternative remains documented. The OpenRewrite path itself has not been validated end-to-end against a real project under this skill.
