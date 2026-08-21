# One generic QRSPI skill, personality in config

**Date:** 2026-08-20
**Status:** Approved design, ready for implementation planning
**Reference model:** [`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi) — local clone at
`/Users/emundorf/development/mundo-dev/projects/rice-qrspi`

## Problem

Five hand-authored copies of the same 7-stage QRSPI workflow live in this monorepo, drifting
independently:

| Copy | Skill name | Has config? | Has findings? |
|---|---|---|---|
| `mcp/legacy/sap-mcp-server-l/.claude/skills/commerce-qrspi/` | `commerce-qrspi` | yes | 3 |
| `mcp/legacy/sap-mcp-ui-l/.claude/skills/storefront-qrspi/` | `storefront-qrspi` | yes | 2 |
| `sap-ui-template-react/.claude/skills/storefront-qrspi/` | `storefront-qrspi` | yes | 0 |
| `qrspi-kit/backend/.claude/skills/commerce-qrspi/` | `commerce-qrspi` | template | 0 |
| `qrspi-kit/ui/.claude/skills/storefront-qrspi/` | `storefront-qrspi` | template | 0 |

The skeleton and stage semantics already match across all of them. What differs is *vocabulary
baked into the prose*: 41 stack-specific mentions in `commerce-qrspi`'s stage commands + SKILL.md
(`gradlew`, `hybris`, `items.xml`, `gensrc`, `OOTB`, `OCC`, `Backoffice`), and 20 in
`storefront-qrspi` (`npm`, `tsc`, `vite`, `playwright`, `node_modules`). rice-qrspi's equivalent
files contain **zero** such tokens — every specific is a config field.

Consequences today: each copy must be edited separately for any workflow improvement; both
`commerce-qrspi` and `storefront-qrspi` expose a colliding `/cq:*` namespace with *different*
file contents; and `commerce-qrspi`'s own SKILL.md already documents the collision as a hazard
("do not also install storefront-qrspi — the /cq commands would collide").

The configs, by contrast, are already ~80% right. `sap-mcp-server-l/working-docs/config.json` is
fully rice-shaped. The two React configs are missing only four fields.

## Goal

**One canonical, stack-neutral `qrspi` skill.** Everything project-specific lives in that
project's `working-docs/config.json`. No flavored variant skill — the config is the flavor.

Non-goals: changing the 7 stages or 4 tiers, changing the `/cq:` namespace, touching the domain
knowledge skills (`sap-commerce`, `impex`, `sap-best-practices`, `java-best-practices`,
`sap-commerce-migrate-j21`, `spartacus-*`, `react-ecommerce`, `commerce-storefront`,
`react-typescript`) — those are legitimately stack-specific and stay as they are.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | **Canonical source + sync into each project** | Each project must stay self-contained so `sap-commerce-claude-kit.zip` still ships standalone; a canonical copy plus `--check` drift detection gets single-source editing without breaking distribution. |
| 2 | **`sync.sh` renders frontmatter from config** | Skill triggering is decided from SKILL.md frontmatter, which config cannot reach at load time. Sync substitutes `{{TRIGGER_VOCABULARY}}` per repo; the body stays byte-identical, so trigger words survive with no prose drift and no second skill. |
| 3 | **rice body as the base, port SAP's generic gains forward** | rice is already 0-token generic; the SAP side has stack-neutral improvements rice lacks. Additive merge means no jargon can survive by accident. |
| 4 | **Profiles ship inside the skill; effective config is one committed file per repo** | Profiles belong to the tool (they are currently scattered across 7 files in 5 directories). The *effective* config is not a thin selection — it carries load-bearing repo specifics and must stay one readable, git-diffable, PR-reviewable artifact. Silent propagation into build verbs is a hazard, not a convenience. |
| 5 | **Findings move to `working-docs/findings/`** | Once the skill directory is machine-synced, repo-owned knowledge cannot live inside it. Clean ownership split lets sync overwrite the skill dir wholesale. |

## Architecture

### Ownership boundary

The design rests on one invariant:

> `.claude/skills/qrspi/` and `.claude/commands/cq/` are **generated**. `working-docs/` is
> **repo-owned**.

Nothing repo-owned lives under the skill directory, so sync can `rm -rf` and re-copy it safely.
Nothing generated lives under `working-docs/`, so a sync never touches a team's tuning.

The invariant retires the old edit-then-`sync-commands.sh` workflow. Stages are edited **only**
in `qrspi-kit/skills/qrspi/commands/`; a synced copy is never hand-edited, and `--check` fails if
one is. The in-skill `sync-commands.sh` survives solely as a republish helper — it re-copies the
synced `commands/` into `.claude/commands/cq/` when only the `/cq:` publish is stale — and its
header must say so, because existing docs reference it as the editing entry point.

### Canonical layout

```
qrspi-kit/
├── skills/qrspi/                    ← THE canonical skill; the only editable copy
│   ├── SKILL.md                     ← frontmatter carries {{TRIGGER_VOCABULARY}}
│   ├── commands/
│   │   0_go.md 1_ticket.md 2_research.md 3_design.md
│   │   4_structure.md 5_plan.md 6_implement.md 7_validate.md
│   ├── profiles/                    ← the one profile library
│   │   sap-commerce.json  react-storefront.json  composable-storefront.json
│   │   springboot.json  fastapi.json  README.md
│   ├── findings-seed/{README.md,TEMPLATE.md}
│   ├── QUICKREF.md  WALKTHROUGH.md  README.md
│   └── sync-commands.sh             ← republish-only (see note below)
├── sync.sh                          ← install / update / --check
├── backend/  ui/                    ← UNCHANGED: domain knowledge skills only
└── INSTALL.md  README.md  WALKTHROUGH.md
```

### Target repo layout (identical in all three)

```
.claude/skills/qrspi/          ← synced bytes + .synced-from stamp      (generated)
.claude/commands/cq/*.md       ← published from commands/               (generated)
working-docs/config.json       ← seeded from a profile, then repo-owned (committed)
working-docs/findings/*.md     ← learned per repo                       (committed)
```

Side effect worth noting: the `.claude/commands/cq/` directories in this monorepo currently hold
different files under the same names — a genuine collision when more than one project is in
session. After convergence they are byte-identical, so the collision becomes harmless.

### Sync targets

| Target | Profile seed | Findings to migrate |
|---|---|---|
| `mcp/legacy/sap-mcp-server-l/` | `sap-commerce` | 3 |
| `mcp/legacy/sap-mcp-ui-l/` | `react-storefront` | 2 |
| `sap-ui-template-react/` | `react-storefront` | 0 |

## Config schema

### New fields

| Field | Type | Replaces (hardcoded today) | SAP value | React value |
|---|---|---|---|---|
| `triggerVocabulary` | string | SKILL.md frontmatter trigger list | `items.xml, impex, OCC, Backoffice, hybris, CCv2, gensrc, FlexibleSearch` | `components, pages, routing, state, OCC API client, styling` |
| `manualVerificationSurfaces` | string[] | `3_design.md:21`, `7_validate.md:10` | Backoffice path · storefront URL · HAC console | dev-server route · browser console |
| `sliceExample` | string | `4_structure.md:9-10` | `items.xml type + service stub + unit test → business logic + impex → OCC endpoint + integration test` | `types + api method → component + state → route + e2e spec` |
| `questionCategories` | string[] \| null | `1_ticket.md:29` | `null` (defaults to `researchLayers[].name`) | `null` |
| `verbNamespaces` | object \| null | the "Combined repos" section | `null` today; `{"FE_": "cd js-storefront/<app> && "}` when a storefront shares the repo | `null` |
| `profileVersion` | string | — | seed stamp (`2026-08-20`), so a repo can tell when its source profile improved | same |

`questionCategories` defaults to `researchLayers[].name` and is expected to stay `null` in all
three repos; it exists so a repo whose ticket-decomposition categories legitimately differ from
its research layers can say so without editing the stage.

### Fields the two React configs must gain

`sap-mcp-ui-l` and `sap-ui-template-react` are missing four fields that already exist in the SAP
config and in rice's schema. Today their equivalent rules live only in stage prose:

| Field | Value | Currently hardcoded at |
|---|---|---|
| `profile` | `react-storefront` | — |
| `workingDir` | `.` | — |
| `protectedPaths` | `["node_modules/", "dist/", ".env"]` | `0_go.md:42`, `5_plan.md:18`, `6_implement.md:32` |
| `apiBoundary` | `src/services/api.ts` | `0_go.md:42` |

### Validation

`sync.sh` schema-checks the config: **warn** on unknown top-level keys (likely typo), **error**
when `changeTypeVerbs` references a verb absent from `build` (a mapping that can never run).

## The canonical body

rice's 8 stage files and SKILL.md verbatim as the base, plus these stack-neutral gains ported
forward from `commerce-qrspi`:

1. **"Ground every document — never speculate"** ground rule — docs state only verified facts
   with `file:line`; unknowns are flagged `unconfirmed` or as open questions, never guessed.
2. **The "Writing standard" section** — thorough-but-concise · human- and LLM-readable ·
   complete and usable · never make things up.
3. **Per-stage Grounding blocks** — the per-command enforcement of #1.
4. **"Tier changes ceremony, never safety"** — verification verbs, the diff-ownership gate, and
   `protectedPaths` apply at every tier.
5. **The "Combined repos" section, generalized** to multi-stack repos and driven by
   `verbNamespaces` rather than naming `core-customize/` and `js-storefront/`. The clause telling
   the reader not to install a second QRSPI skill is deleted — convergence makes it moot.

Then the three residual hardcodes become config lookups (`manualVerificationSurfaces`,
`sliceExample`, `questionCategories`).

**Acceptance:** a grep for stack tokens over `skills/qrspi/commands/` returns zero hits, and CI
keeps it that way. Frontmatter `name` is `qrspi`; the description body is generic with
`{{TRIGGER_VOCABULARY}}` as its final trigger clause.

## `sync.sh` contract

```bash
./qrspi-kit/sync.sh <target-repo> [--init --profile <name>] [--check]
```

1. **`--init`** — copy `profiles/<name>.json` → `<target>/working-docs/config.json`, stamp
   `profile` + `profileVersion`, then stop and print which placeholders the developer must fill
   (e.g. `<ext>` for SAP). Never overwrite an existing `config.json`.
2. Replace the skill: `rm -rf <target>/.claude/skills/qrspi` then copy canonical. Safe wholesale
   because nothing repo-owned lives there.
3. Read `<target>/working-docs/config.json`, substitute `{{TRIGGER_VOCABULARY}}` into that
   copy's SKILL.md frontmatter. This is the **only** per-repo byte difference in the skill.
4. Publish `commands/*.md` → `<target>/.claude/commands/cq/`.
5. Seed `working-docs/findings/{README.md,TEMPLATE.md}` from `findings-seed/` if absent. Never
   touch dated findings.
6. Write `<target>/.claude/skills/qrspi/.synced-from` — canonical path, git commit, date.
7. Schema-check the config (see Validation) and print a summary.
8. **`--check`** — verify the target's synced copy matches canonical (ignoring the rendered
   frontmatter line) and exit non-zero on drift. This is the CI drift detector.

Failure handling: a missing `config.json` without `--init` is an error naming the available
profiles. An unwritable target aborts before step 2, so a failed run never leaves a repo with a
deleted skill and no replacement.

## Migration

Ordered; step 2 must precede step 3 or findings are silently lost.

1. **Build canonical `qrspi-kit/skills/qrspi/`** per "The canonical body", and consolidate the
   scattered profiles into `skills/qrspi/profiles/`:
   - `sap-commerce.json` ← `mcp/legacy/sap-mcp-server-l/working-docs/profiles/sap-commerce.json`
     (the most evolved copy; `qrspi-kit/backend/working-docs/profiles/sap-commerce.json` is a
     duplicate — reconcile and keep one)
   - `react-storefront.json` ← merge `qrspi-kit/profiles/react-storefront-generic.json` +
     `qrspi-kit/ui/working-docs/profiles/react-vite.json`
   - `composable-storefront.json` ← `qrspi-kit/ui/working-docs/profiles/composable-storefront.json`
   - `springboot.json`, `fastapi.json` ← `qrspi-kit/profiles/`
   - each gains the new fields from "Config schema" and a `profileVersion`
2. **Fix `.gitignore` in all three targets.** Each has `working-docs/*` with only
   `!working-docs/config.json` re-included (`sap-mcp-server-l/.gitignore:52`,
   `sap-mcp-ui-l/.gitignore:32`, `sap-ui-template-react/.gitignore:32`). Add
   `!working-docs/findings/` and `!working-docs/findings/**`. Without this, migrated findings
   become untracked and vanish from git and from the kit zip.
3. **`git mv` findings** out of the skill dirs into `working-docs/findings/`:
   - `sap-mcp-server-l`: `2026-06-09-kb-changes-need-knowledgeindex-reindex.md`,
     `2026-07-31-pricedatafactory-in-commercefacades.md`,
     `2026-08-20-ant-build-before-ant-unittests.md`
   - `sap-mcp-ui-l`: `2026-06-16-manual-smoke-module-import.md`,
     `2026-06-19-e2e-gate-runnability.md`
   - `README.md` / `TEMPLATE.md` come from `findings-seed/` instead of being moved
4. **Extend the three configs** per "Config schema" — new fields everywhere, plus the four
   missing fields in both React configs.
5. **Run `sync.sh`** into all three targets.
6. **Delete the old skills** — `commerce-qrspi/` and `storefront-qrspi/` from all five locations
   (three target repos + `qrspi-kit/backend` + `qrspi-kit/ui`).
7. **Update the docs that name them:**
   - `mcp/legacy/sap-mcp-server-l/CLAUDE.md` (skill table + the stage-sync paragraph)
   - `mcp/legacy/sap-mcp-ui-l/CLAUDE.md` (skill table + QRSPI paragraph)
   - `sap-ui-template-react/CLAUDE.md` (skill table)
   - `mcp/legacy/sap-mcp-server-l/qrspi-readme.md`
   - `mcp/legacy/sap-mcp-server-l/docs/strb-15min-demo.md`
   - `qrspi-kit/INSTALL.md`, `qrspi-kit/README.md`, `qrspi-kit/WALKTHROUGH.md`,
     `qrspi-kit/backend/CLAUDE.md`, `qrspi-kit/ui/CLAUDE.md`
   - cross-references inside domain skills: `sap-commerce/SKILL.md`,
     `sap-commerce-migrate-j21/{SKILL.md,CHANGELOG.md}`, `spartacus-storefront/SKILL.md`
   - **leave alone:** historical ticket artifacts (`working-docs/THINK-201/ticket.md`,
     `working-docs/KB-02/jira-updates.md`, `archive_2026-08-19/`) — they record what was true
8. **Re-zip `sap-commerce-claude-kit.zip`** (it currently ships `commerce-qrspi/` and its
   findings). A `.bak-2026-08-20` already exists alongside it.
9. **Re-cut the demo baseline.** Tags `qrspi-demo-baseline`, `qrspi-demo-v1`, `qrspi-demo-v2`
   point at commits containing the old skills, so a reset restores the retired layout. Cut a new
   baseline after this lands. (No `reset.sh` exists anywhere in this repo despite a note
   referencing `demo/qrspi-demo/reset.sh` — locate or recreate the reset flow as part of this
   step.)

## Verification

| Check | How |
|---|---|
| Zero stack tokens in canonical stages | `grep -riE 'gradlew\|hybris\|items\.xml\|gensrc\|OOTB\|npm\|tsc\|vite\|node_modules' qrspi-kit/skills/qrspi/commands/` → no hits |
| Skill loads and triggers per repo | Confirm `qrspi` is discovered in each target; frontmatter shows that repo's `triggerVocabulary` |
| `/cq:` commands resolve | `/cq:go` present in all three targets; the 8 files byte-identical across them |
| No drift | `sync.sh <target> --check` green for all three |
| Config drives behavior | Stage 2 dry run lists this repo's `researchLayers`; stage 4 shows this repo's `sliceExample` |
| Findings survive | All 5 findings tracked by git at their new paths; `git ls-files working-docs/findings/` non-empty in both repos |
| Schema check works | A config referencing an undefined verb in `changeTypeVerbs` errors |
| Kit installs clean | Extract the re-zipped kit into a scratch dir; `sync.sh --init --profile sap-commerce` produces a working setup |

## Risks

| Risk | Mitigation |
|---|---|
| Findings lost to `.gitignore` | Migration step 2 precedes step 3; verification greps `git ls-files` |
| Trigger regression — stack jargon no longer activates the skill | `triggerVocabulary` per repo, rendered into frontmatter by sync; verify by inspection after sync |
| Sync clobbers repo-owned work | Ownership invariant: nothing repo-owned under the skill dir. Sync aborts before deleting if the target is unwritable |
| Kit zip ships a stale skill | Re-zip is migration step 8; kit-install verification is a listed check |
| Profile improvements don't reach existing repos | Accepted by decision 4. `profileVersion` makes the gap visible; re-seeding is a deliberate, reviewable diff |
| A future third stack needs a field nobody anticipated | The `_notes` array absorbs prose-shaped knowledge; genuinely new mechanics go through the findings-promotion contract |
