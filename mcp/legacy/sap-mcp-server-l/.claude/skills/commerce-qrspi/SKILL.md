---
name: commerce-qrspi
description: >
  QRSPI workflow for SAP Commerce in Claude Code — Ticket/Questions, Research, Design,
  Structure, Plan, Implement, Validate. Use when a developer wants to work a Jira ticket
  against a Commerce (CCv2/hybris) codebase with structured stages: decompose a ticket into
  research questions; run blind layered codebase research; align on design, assumptions, and
  success criteria; break work into vertical slices; produce a tactical plan; implement
  manually or via Claude with gradlew verification; validate against success criteria.
  Triggers on QRSPI, "work this ticket", items.xml, impex, OCC, Backoffice, hybris, CCv2
  combined with research/plan/implement intent.
---

# Commerce QRSPI

You orchestrate a 7-stage workflow. **Each stage runs in a fresh context window, reads only
its declared input artifacts, stays under 40 instructions, and ends by printing the exact
next command.** Never merge stages. Never skip a developer gate.

## Entry point — one command, four tiers

Developers remember `/cq:go <TICKET-KEY> [tier]` and four tier names:
**trivial** (no workflow — just fix and verify), **simple** (brief → implement →
validate-lite), **full** (all stages; 1+2 auto-chained), **comprehensive** (full +
worktree + mandatory integration tests + team review of design/structure).
`commands/0_go.md` holds the recommendation heuristics and tier mechanics. Recommend a
tier, confirm it, record it. The numbered stages below are the machinery behind
full/comprehensive — developers can still invoke them directly, but don't have to.

## Stages

| # | Command | Reads | Writes | Dev gate? |
|---|---------|-------|--------|-----------|
| 0 | `commands/0_go.md` | ticket/description | tier decision | confirm tier |
| 1 | `commands/1_ticket.md` | Jira ticket / text | `ticket.md`, `questions.md` | skim |
| 2 | `commands/2_research.md` | `questions.md` only — **never ticket.md** | `research.md` | no |
| 3 | `commands/3_design.md` | `ticket.md` + `research.md` | `design.md` (~200 lines) | ★ YES |
| 4 | `commands/4_structure.md` | `design.md` | `structure.md` (~2 pages) | ★ YES |
| 5 | `commands/5_plan.md` | all artifacts | `plan.md` (checkboxes) | spot-check |
| 6 | `commands/6_implement.md` | `structure.md` + `plan.md` | code, 1 commit/slice | no |
| 7 | `commands/7_validate.md` | `design.md` success criteria | validation report, PR | ★ YES |

Artifacts live in `working-docs/<TICKET-KEY>/`.

## Tier selection — recommend the lightest sufficient path

Heuristics live in `commands/0_go.md`. When in doubt, recommend the lower tier and say
what would bump it. Promotion mid-flight is fine — run the skipped stages then. Tier
changes ceremony, never safety: verification verbs, the diff-ownership gate, and OOTB
protections apply at every tier.

## Going backward

Research reveals bad questions → re-run 1. Design finds missing facts → re-run 1+2.
Structure exposes a flawed design → re-run 3. Implementation hits a fundamental plan error
→ re-run 5 (or 3). Adapt small mismatches in place; go back only for fundamentals.

## Commerce ground rules (apply in every stage)

- Never modify `gensrc/`, platform, or OOTB modules — override in custom extensions.
- **Stages never hardcode build commands.** They reference verification VERBS, resolved
  through the build adapter in `working-docs/config.json` (see below).
- Jira/Confluence publishing is optional; local `working-docs/` is first-class.

## Build adapter — projects differ; detect once, confirm, persist

SAP Commerce projects vary: CCv2 vs on-prem, gradle wrapper vs raw ant, `core-customize/`
vs `hybris/` at repo root, Spartacus vs accelerator vs headless. On first run (any stage),
if `working-docs/config.json` is missing:

1. **Detect layout:** `core-customize/manifest.json` (CCv2) vs `hybris/bin/platform/` at
   root (on-prem). Locate custom extensions via `**/hybris/bin/custom/*/extensioninfo.xml`
   and `localextensions.xml`. Check `js-storefront/` and accelerator `*storefront` extensions.
2. **Detect build system:** `gradlew` + SAP/y-task gradle plugin → gradle profile;
   `hybris/bin/platform/build.xml` only → ant profile; neither/CI-script → ask.
2b. **Detect Jira mode** and store as `jira.mode` in config:
   - `mcp` — Atlassian MCP tools respond: full automation (fetch, comment, transition).
   - `manual` — team uses Jira but MCP is unavailable (no connector, VPN-only server,
     auth restrictions): the developer pastes ticket content in, and every outbound
     update is written to `working-docs/<TICKET-KEY>/jira-updates.md` as paste-ready
     text. Never silently drop an update.
   - `none` — no Jira: skip all ticket integration; slug-named directories.
3. **Resolve the verb table**, confirm with the developer, and save. Verbs:

| Verb | gradle-wrapper example | raw-ant example |
|------|------------------------|-----------------|
| BUILD | `./gradlew ybuild` | `. ./setantenv.sh && ant build` |
| FULL_BUILD | `./gradlew yclean yall` | `ant clean all` |
| TYPE_SYSTEM_UPDATE | `./gradlew yupdatesystem` | `ant updatesystem` |
| UNIT_TEST | `./gradlew yunittests -Dtestclasses.extensions=<ext>` | `ant unittests -Dtestclasses.extensions=<ext>` |
| INTEGRATION_TEST | `./gradlew yintegrationtests ...` | `ant integrationtests ...` |
| IMPEX_IMPORT | `./gradlew impex -Pfile=<path>` | MANUAL: import via HAC console |
| SERVER_RESTART | `./gradlew stopServer startServer` | `./hybrisserver.sh` stop/start |

   A verb may resolve to `MANUAL: <instructions>` — stages then print the instructions
   and wait for developer confirmation instead of running a command.
4. **Change-type → verbs mapping** (also in config, editable): items.xml →
   BUILD + SERVER_RESTART + TYPE_SYSTEM_UPDATE; Java/beans → BUILD + SERVER_RESTART;
   impex → IMPEX_IMPORT; any slice checkpoint → UNIT_TEST minimum.

Present detection as one paragraph for confirmation; the developer can override any
command. Config is versioned in git, so the team sets it up once per repo.

## Combined repos (core-customize/ + js-storefront/)

When the storefront lives in the same repo (common for Composable Commerce), this is
still **one workflow — do not also install storefront-qrspi** (the /cq commands would
collide, and cross-stack tickets want vertical slices spanning both sides). Instead:
- Add a frontend verb namespace to the config (`FE_INSTALL`, `FE_BUILD`, `FE_TEST`,
  `FE_LINT` — each prefixed `cd js-storefront/<app> && ...`) and map
  `js-storefront ts|scss|html` change types to them.
- Research Layer 3 (Storefront) activates because `js-storefront/` exists; scope it to
  the storefront app's structure (for Spartacus: CMS components, feature modules,
  facades/connectors, OCC config).
- Stage-4 slices should cut vertically across the stack: backend type/endpoint in one
  slice checkpoint (BE verbs), the consuming storefront piece in the next (FE verbs).
- Pair with the `spartacus-storefront` knowledge skill (explicit opt-in) for Composable
  Storefront specifics.

## What this skill does NOT do

Deploy, push without consent, modify OOTB code, write design.md before the stage-3 Q&A,
proceed past failed verification, or run more ceremony than the ticket warrants.
