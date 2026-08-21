# One generic QRSPI skill, personality in config

**Date:** 2026-08-20
**Status:** Approved design, ready for implementation planning
**Reference model:** [`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi) — local clone at
`/Users/emundorf/development/mundo-dev/projects/rice-qrspi`

## Problem

Five hand-authored copies of the same 7-stage QRSPI workflow live in this monorepo, drifting
independently:

| Copy | Skill name | Config | Findings |
|---|---|---|---|
| `mcp/legacy/sap-mcp-server-l/.claude/skills/commerce-qrspi/` | `commerce-qrspi` | tuned | 3 |
| `mcp/legacy/sap-mcp-ui-l/.claude/skills/storefront-qrspi/` | `storefront-qrspi` | tuned | 2 |
| `sap-ui-template-react/.claude/skills/storefront-qrspi/` | `storefront-qrspi` | tuned | 0 |
| `qrspi-kit/backend/.claude/skills/commerce-qrspi/` | `commerce-qrspi` | template | 0 |
| `qrspi-kit/ui/.claude/skills/storefront-qrspi/` | `storefront-qrspi` | template | 0 |

The skeleton and stage semantics already match. What differs is *vocabulary baked into the prose*:
41 stack-specific mentions in `commerce-qrspi`'s stage commands + SKILL.md (`gradlew`, `hybris`,
`items.xml`, `gensrc`, `OOTB`, `OCC`, `Backoffice`) and 20 in `storefront-qrspi` (`npm`, `tsc`,
`vite`, `playwright`, `node_modules`). rice-qrspi's equivalent files contain **zero** such tokens —
every specific is a config field.

Consequences today: any workflow improvement must be applied five times; `commerce-qrspi` and
`storefront-qrspi` expose a colliding `/cq:*` namespace holding *different* file contents; and
`commerce-qrspi`'s own SKILL.md documents the collision as a hazard ("do not also install
storefront-qrspi — the /cq commands would collide").

The kit is itself part of the problem. `qrspi-kit/` currently carries two full skill copies, two
template `working-docs/config.json` files, **three** separate profile directories
(`profiles/`, `ui/working-docs/profiles/`, `backend/working-docs/profiles/` — seven profile files
between them), and duplicate domain skills. The `backend/` domain skills are byte-identical to the
in-repo copies (`sap-commerce`, `impex`, `sap-best-practices`, `java-best-practices` — 0 diff
lines); only `sap-commerce-migrate-j21` differs, and there the in-repo copy is newer.

The configs, by contrast, are already ~80% right. `sap-mcp-server-l/working-docs/config.json` is
fully rice-shaped; the two React configs are missing only four fields.

## Goal

**One canonical, stack-neutral `qrspi` skill.** Everything project-specific lives in that project's
`working-docs/config.json`. No flavored variant skill — the config is the flavor.

Distribution is a zip you drop at a project root, list profiles from, and install with one command.

Non-goals: changing the 7 stages or 4 tiers, changing the `/cq:` namespace, or touching the domain
knowledge skills (`sap-commerce`, `impex`, `sap-best-practices`, `java-best-practices`,
`sap-commerce-migrate-j21`, `spartacus-*`, `react-ecommerce`, `commerce-storefront`,
`react-typescript`) — those are legitimately stack-specific and stay where they are.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | **`qrspi-kit/` is the single source of truth, and holds QRSPI only** | One thing, one purpose. The `backend/`/`ui/` trees are verified duplicates of in-repo skills, so removing them loses nothing and stops the kit from versioning workflow and domain knowledge on the same clock. |
| 2 | **The kit ships no `config.json` and nothing to run** | It's inert source: skill + profiles + installer. Nothing to build, nothing to sync before zipping. |
| 3 | **Profiles are the flavor definition; the installer writes one into the project** | Profile and config are the same shape, so there is one schema to know. The project owns its copy from then on: tuning is a reviewable diff, and re-installing can never silently overwrite it. |
| 4 | **The kit stays after install, gitignored** | Neither installer deletes anything. Re-installing, switching profiles, and updating the skill all work locally without hunting for the zip; the kit is gitignored so it never bloats the repo or a diff, and anyone who wants it gone can delete it by hand. Also removes the fragility of a script removing its own directory mid-execution. |
| 5 | **`install.sh` + `install.ps1`, kept logic-free** | Node is not guaranteed on any platform (the native install ships a standalone binary that "does not itself invoke Node"), but a shell is: Claude Code supports Bash, Zsh, PowerShell, CMD. On native Windows without the optional Git for Windows, Claude's shell tool *is* PowerShell — so `.ps1` is what it can invoke and `.sh` is what it cannot. Batch is ruled out: it can't read JSON or substitute text without pain, and PowerShell does both natively. |
| 6 | **rice body as the base; port SAP's stack-neutral gains forward** | rice is already 0-token generic; the SAP side has improvements rice lacks. An additive merge means no jargon survives by accident. |
| 7 | **Findings move to `working-docs/findings/`** | Repo-owned knowledge cannot live inside a machine-managed directory. |
| 8 | **Drift detection lives in the monorepo, not the target** | A target's local kit is an unversioned convenience copy that can itself be stale, so it isn't a trustworthy baseline. CI here compares each project's installed skill against the committed `qrspi-kit/skills/qrspi/`. |

## Architecture

### Ownership boundary

The design rests on one invariant:

> `.claude/skills/qrspi/` and `.claude/commands/cq/` are **generated**. `working-docs/` is
> **repo-owned**.

Nothing repo-owned lives under the skill directory, so the installer can replace it wholesale.
Nothing generated lives under `working-docs/`, so an install never touches a team's tuning.

This retires the old edit-then-`sync-commands.sh` workflow. Stages are edited **only** in
`qrspi-kit/skills/qrspi/commands/`; an installed copy is never hand-edited, and the monorepo drift
check fails if one is.

### Kit layout (the source of truth, and what gets zipped)

```
qrspi-kit/
├── skills/qrspi/                 ← the generic skill
│   ├── SKILL.md                  ← frontmatter carries {{TRIGGER_VOCABULARY}}
│   ├── commands/
│   │   0_go.md 1_ticket.md 2_research.md 3_design.md
│   │   4_structure.md 5_plan.md 6_implement.md 7_validate.md
│   ├── findings-seed/{README.md,TEMPLATE.md}
│   └── QUICKREF.md  WALKTHROUGH.md  README.md
├── profiles/
│   ├── sap-commerce.json  react-storefront.json  composable-storefront.json
│   ├── springboot.json  fastapi.json
│   └── README.md
├── install.sh                    ← mac / Linux / WSL / Git Bash
├── install.ps1                   ← Windows
├── INSTALL.md
└── README.md
```

No `config.json` anywhere in the kit. No build step. `zip -r qrspi-kit.zip qrspi-kit/` is the
whole release process.

### Install flow

```bash
# at the target project root
unzip qrspi-kit.zip

./qrspi-kit/install.sh list          #  .\qrspi-kit\install.ps1 list
./qrspi-kit/install.sh sap-commerce  #  .\qrspi-kit\install.ps1 sap-commerce
```

`list` prints each profile with its one-line description. `install <profile>` performs the seven
steps below. The kit stays where you unzipped it — gitignored, reusable, and yours to delete.

### Target layout after install (identical in all three repos)

```
.claude/skills/qrspi/          ← installed skill + .installed-from stamp   (generated)
.claude/commands/cq/*.md       ← published from commands/                  (generated)
working-docs/config.json       ← written from the profile, then repo-owned (committed)
working-docs/findings/*.md     ← learned per repo                          (committed)
qrspi-kit/                     ← left in place, gitignored                 (local tool)
```

Side effect worth noting: the `.claude/commands/cq/` directories in this monorepo currently hold
different files under the same names — a genuine collision when more than one project is in
session. After convergence they are byte-identical, so the collision becomes harmless.

### Install targets

| Target | Profile | Findings to migrate |
|---|---|---|
| `mcp/legacy/sap-mcp-server-l/` | `sap-commerce` | 3 |
| `mcp/legacy/sap-mcp-ui-l/` | `react-storefront` | 2 |
| `sap-ui-template-react/` | `react-storefront` | 0 |

## Installer contract

Both scripts implement the same seven steps and the same exit codes.

1. **Resolve the profile.** Unknown or missing name → print the `list` output and exit non-zero.
2. **Install the skill.** Replace `<target>/.claude/skills/qrspi/` wholesale from
   `skills/qrspi/`. Safe because nothing repo-owned lives there.
3. **Render the frontmatter.** Substitute `{{TRIGGER_VOCABULARY}}` in the installed `SKILL.md`
   with the profile's `triggerVocabulary`. This is the only per-repo byte difference in the skill.
4. **Publish the commands.** Copy `commands/*.md` → `<target>/.claude/commands/cq/`.
5. **Write the config.** If `working-docs/config.json` is absent, copy the profile there and stamp
   `profile` + `profileVersion`. **If it already exists, never overwrite it** — write
   `working-docs/config.json.new` instead and tell the developer to diff and merge. This is what
   protects the three tuned configs on re-install.
6. **Seed and stamp.** Copy `findings-seed/{README,TEMPLATE}.md` into `working-docs/findings/` if
   absent, never touching dated findings. Write `.claude/skills/qrspi/.installed-from` recording
   profile, `profileVersion`, kit version, and date — since the kit is deleted, this stamp is the
   only record of what was installed.

7. **Ignore the kit.** When the kit sits inside the target (the unzip case), ensure `/qrspi-kit/`
   is in the target's `.gitignore` — append it under a marker comment if absent — and say so in the
   summary. **Nothing is ever deleted.** When installing via `--target` the kit lives outside the
   target, so this step is skipped.

Then schema-check the config (below) and print a summary plus the `/cq:go` next step. Maintainer
form, used from this monorepo to install into a sibling project:

```bash
./qrspi-kit/install.sh sap-commerce --target mcp/legacy/sap-mcp-server-l
```

**Failure handling:** validate the target is writable and the profile resolves *before* step 2, so
a failed run never leaves a repo with a deleted skill and no replacement.

**Portability constraints** that keep both scripts thin:

- PowerShell reads the profile with `ConvertFrom-Json`. Bash extracts `triggerVocabulary` with a
  single `grep` + `sed`, because neither `jq` nor a JSON parser is guaranteed. Therefore
  `triggerVocabulary` **must be a single-line JSON string with no embedded quotes** — a documented
  profile constraint, enforced by a lint in this monorepo. A missing or unparseable value is a
  hard error, never a silent empty substitution.
- In-place edits are written to a temp file and moved, avoiding the BSD/GNU `sed -i` split.
- `install.ps1` documents the `ExecutionPolicy` workaround (`pwsh -File .\install.ps1 …`).

## Config schema

A profile and a `config.json` are the same shape — installing is a copy plus two stamps.

### New fields

| Field | Type | Replaces (hardcoded today) | `sap-commerce` | `react-storefront` |
|---|---|---|---|---|
| `triggerVocabulary` | string | SKILL.md frontmatter trigger list | `items.xml, impex, OCC, Backoffice, hybris, CCv2, gensrc, FlexibleSearch` | `components, pages, routing, state, OCC API client, styling` |
| `manualVerificationSurfaces` | string[] | `3_design.md:21`, `7_validate.md:10` | Backoffice path · storefront URL · HAC console | dev-server route · browser console |
| `sliceExample` | string | `4_structure.md:9-10` | `items.xml type + service stub + unit test → business logic + impex → OCC endpoint + integration test` | `types + api method → component + state → route + e2e spec` |
| `questionCategories` | string[] \| null | `1_ticket.md:29` | `null` (defaults to `researchLayers[].name`) | `null` |
| `verbNamespaces` | object \| null | the "Combined repos" section | `null`; `{"FE_": "cd js-storefront/<app> && "}` when a storefront shares the repo | `null` |
| `profileVersion` | string | — | install stamp, so a repo can tell when its source profile moved on | same |

`questionCategories` is expected to stay `null` everywhere; it exists so a repo whose
ticket-decomposition categories legitimately differ from its research layers can say so without
editing a stage.

### Fields the two React configs must gain

Both React configs lack four fields the SAP config and rice's schema already have. Today their
equivalent rules live only in stage prose:

| Field | Value | Currently hardcoded at |
|---|---|---|
| `profile` | `react-storefront` | — |
| `workingDir` | `.` | — |
| `protectedPaths` | `["node_modules/", "dist/", ".env"]` | `0_go.md:42`, `5_plan.md:18`, `6_implement.md:32` |
| `apiBoundary` | `src/services/api.ts` | `0_go.md:42` |

### Validation

The installer schema-checks the config: **warn** on unknown top-level keys (likely typo), **error**
when `changeTypeVerbs` references a verb absent from `build` (a mapping that can never run), and
**error** on a missing or multi-line `triggerVocabulary`.

## The canonical body

rice's 8 stage files and SKILL.md verbatim as the base, plus these stack-neutral gains ported from
`commerce-qrspi`:

1. **"Ground every document — never speculate"** — docs state only verified facts with
   `file:line`; unknowns are flagged `unconfirmed` or as open questions, never guessed.
2. **The "Writing standard" section** — thorough-but-concise · human- and LLM-readable · complete
   and usable · never make things up.
3. **Per-stage Grounding blocks** — the per-command enforcement of #1.
4. **"Tier changes ceremony, never safety"** — verification verbs, the diff-ownership gate, and
   `protectedPaths` apply at every tier.
5. **The "Combined repos" section, generalized** to multi-stack repos and driven by
   `verbNamespaces` rather than naming `core-customize/` and `js-storefront/`. The clause telling
   the reader not to install a second QRSPI skill is deleted — convergence makes it moot.

Then the three residual hardcodes become config lookups (`manualVerificationSurfaces`,
`sliceExample`, `questionCategories`).

**Acceptance:** a grep for stack tokens over `skills/qrspi/commands/` returns zero hits, and CI
keeps it that way. Frontmatter `name` is `qrspi`; the description is generic with
`{{TRIGGER_VOCABULARY}}` as its final trigger clause.

## Distribution

Two zips, one job each:

- **`qrspi-kit.zip`** — the workflow. Built from `qrspi-kit/` with no preprocessing.
- **`sap-commerce-claude-kit.zip`** — the SAP domain skills + `CLAUDE.md`, with `commerce-qrspi/`
  **removed**. Its QRSPI section instead points at `qrspi-kit.zip`.

## Migration

Ordered. Step 2 must precede step 3 or the findings are silently lost.

1. **Build `qrspi-kit/skills/qrspi/`** per "The canonical body", and consolidate the seven
   scattered profile files into `qrspi-kit/profiles/`:
   - `sap-commerce.json` ← `mcp/legacy/sap-mcp-server-l/working-docs/profiles/sap-commerce.json`
     (most evolved; `qrspi-kit/backend/working-docs/profiles/sap-commerce.json` is a duplicate —
     reconcile and keep one)
   - `react-storefront.json` ← merge `qrspi-kit/profiles/react-storefront-generic.json` +
     `qrspi-kit/ui/working-docs/profiles/react-vite.json`
   - `composable-storefront.json` ← `qrspi-kit/ui/working-docs/profiles/composable-storefront.json`
   - `springboot.json`, `fastapi.json` ← `qrspi-kit/profiles/`
   - each gains the new fields and a `profileVersion`
2. **Fix `.gitignore` in all three targets.** Each has `working-docs/*` with only
   `!working-docs/config.json` re-included (`sap-mcp-server-l/.gitignore:52`,
   `sap-mcp-ui-l/.gitignore:32`, `sap-ui-template-react/.gitignore:32`). Add
   `!working-docs/findings/` and `!working-docs/findings/**`. Without this, migrated findings
   become untracked and vanish from git and from the kit zip.
3. **`git mv` findings** into `working-docs/findings/`:
   - `sap-mcp-server-l`: `2026-06-09-kb-changes-need-knowledgeindex-reindex.md`,
     `2026-07-31-pricedatafactory-in-commercefacades.md`,
     `2026-08-20-ant-build-before-ant-unittests.md`
   - `sap-mcp-ui-l`: `2026-06-16-manual-smoke-module-import.md`,
     `2026-06-19-e2e-gate-runnability.md`
   - `README.md` / `TEMPLATE.md` come from `findings-seed/`, not moved
4. **Write `install.sh` + `install.ps1`** per the installer contract.
5. **Install into all three targets** with `--target`, from the monorepo's canonical kit. The
   existing tuned configs are kept; merge the new fields from the emitted `config.json.new` into
   each by hand, then delete the `.new` files.
6. **Delete the old skills** — `commerce-qrspi/` and `storefront-qrspi/` from all five locations,
   and drop the whole `qrspi-kit/backend/` and `qrspi-kit/ui/` trees (verified duplicates; the
   newer `sap-commerce-migrate-j21` lives in the repo).
7. **Update the docs that name them:**
   - `mcp/legacy/sap-mcp-server-l/CLAUDE.md` (skill table + the stage-sync paragraph)
   - `mcp/legacy/sap-mcp-ui-l/CLAUDE.md` (skill table + QRSPI paragraph)
   - `sap-ui-template-react/CLAUDE.md` (skill table)
   - `mcp/legacy/sap-mcp-server-l/qrspi-readme.md`, `docs/strb-15min-demo.md`
   - `qrspi-kit/INSTALL.md`, `README.md`, `WALKTHROUGH.md` (rewritten for the new flow)
   - cross-references inside domain skills: `sap-commerce/SKILL.md`,
     `sap-commerce-migrate-j21/{SKILL.md,CHANGELOG.md}`, `spartacus-storefront/SKILL.md`
   - **leave alone:** historical ticket artifacts (`working-docs/THINK-201/ticket.md`,
     `working-docs/KB-02/jira-updates.md`, `archive_2026-08-19/`) — they record what was true
8. **Rebuild both zips** per "Distribution". A `.bak-2026-08-20` of the SAP kit already exists.
9. **Re-cut the demo baseline.** Tags `qrspi-demo-baseline`, `qrspi-demo-v1`, `qrspi-demo-v2` point
   at commits containing the old skills, so a reset restores the retired layout. Cut a new baseline
   after this lands. No `reset.sh` exists anywhere in this repo despite a note referencing
   `demo/qrspi-demo/reset.sh` — locate or recreate the reset flow as part of this step.

## Verification

| Check | How |
|---|---|
| Zero stack tokens in canonical stages | `grep -riE 'gradlew\|hybris\|items\.xml\|gensrc\|OOTB\|npm\|tsc\|vite\|node_modules' qrspi-kit/skills/qrspi/commands/` → no hits |
| Skill loads and triggers per repo | `qrspi` discovered in each target; frontmatter shows that repo's `triggerVocabulary` |
| `/cq:` commands resolve | `/cq:go` present in all three; the 8 files byte-identical across them |
| No drift | Monorepo check: each target's `.claude/skills/qrspi/` matches `qrspi-kit/skills/qrspi/`, ignoring the rendered frontmatter line |
| Config drives behavior | Stage 2 lists this repo's `researchLayers`; stage 4 shows this repo's `sliceExample` |
| Findings survive | `git ls-files working-docs/findings/` non-empty in both repos; all 5 files tracked |
| Config never clobbered | Re-run install on a target with a tuned config → config untouched, `config.json.new` written |
| Schema check works | A config referencing an undefined verb errors; a multi-line `triggerVocabulary` errors |
| Both installers agree | Same profile installed via `install.sh` and `install.ps1` into scratch dirs → identical trees |
| Zip install is clean | Unzip `qrspi-kit.zip` into a scratch repo, `install.sh list`, `install.sh sap-commerce` → working setup; kit still present and `/qrspi-kit/` added to `.gitignore`; `git status` clean of kit files |
| Re-install is idempotent | Run `install.sh sap-commerce` twice → same tree, one `.gitignore` entry (not two), config untouched the second time |

## Risks

| Risk | Mitigation |
|---|---|
| Findings lost to `.gitignore` | Migration step 2 precedes step 3; verification greps `git ls-files` |
| The two installers diverge | Both are logic-free by design; the "both installers agree" check compares their output trees |
| Bash JSON extraction is fragile | `triggerVocabulary` constrained to a single-line string, linted in the monorepo; a parse failure is a hard error, never an empty substitution |
| Install clobbers a tuned config | Step 5 never overwrites; it emits `config.json.new` for a manual merge |
| A stale kit lingers in a project | Harmless by design: gitignored, inert, and `.installed-from` records what was actually installed. Re-running the installer refreshes it; deleting it by hand is safe |
| `.gitignore` gains duplicate entries on re-install | Step 7 appends only when the entry is absent; covered by the idempotence check |
| Trigger regression — stack jargon stops activating the skill | Per-repo `triggerVocabulary` rendered at install; verified by inspection after install |
| Kit and installed skill drift apart locally | The monorepo drift check (decision 8) is authoritative; a target's local kit is a convenience copy, and `.installed-from` records what produced the install |
| Profile improvements don't reach existing repos | Accepted by decision 3. `profileVersion` makes the gap visible; re-installing emits a reviewable `config.json.new` |
| A future stack needs an unanticipated field | `_notes` absorbs prose-shaped knowledge; new mechanics go through the findings-promotion contract |
