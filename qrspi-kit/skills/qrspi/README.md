# qrspi — the skill

One stack-neutral implementation of the QRSPI workflow: **Q**uestions → **R**esearch →
**S**tructure/Design → **P**lan → **I**mplement, with validation to close. Seven stages,
each in a fresh context window, with developer gates at Design, Structure, and Validate.

Nothing in this directory is stack-specific. Every project detail — build verbs, research
layers, protected paths, Jira mode, what a slice looks like, where a human verifies —
comes from `working-docs/config.json`, written by the kit installer from a profile.

## Usage

```
/cq:go <TICKET-KEY or description> [trivial|simple|full|comprehensive]
```

One command. With no tier given, the skill recommends one from the ticket's scope and asks
you to confirm — it never starts work on an unagreed tier. Artifacts land in
`working-docs/<TICKET-KEY>/` (gitignored per-ticket scratch; `config.json` and `findings/`
are the committed exceptions).

| Tier | When | What runs |
|---|---|---|
| `trivial` | Typo, config value, <3 files, no design choice | No workflow — fix, verify, show the diff |
| `simple` | Bug with a known cause, small change in one area | `brief.md` → gate → implement → validate-lite |
| `full` | Standard feature: multiple files or layers, real design choices | Stages 1→2 chained, then gates at 3 and 4, then 5→6→7 |
| `comprehensive` | Cross-cutting, risky, migration, or team review wanted | `full` + worktree isolation + all verbs per slice + design/structure published for review |

Safety rails apply at **every** tier: verification verbs must pass, you own the diff before
any PR, all I/O through the configured boundary layer, never touch `protectedPaths`, never
push or deploy without consent. A verb that ran nothing is not a pass.

If a ticket turns out bigger than its tier, say so — promote mid-flight and run the skipped
stages then; the artifacts are compatible across tiers.

## Layout

```
SKILL.md            the workflow contract: stages, gates, ground rules, config schema
commands/           the 8 stage files; also published to .claude/commands/cq/ as /cq:*
findings-seed/      README + TEMPLATE copied into working-docs/findings/ on install
QUICKREF.md         one-page cheat sheet
WALKTHROUGH.md      a full ticket, start to finish
```

## The contract

- **Stages are generated.** This directory is installed by the QRSPI kit and replaced
  wholesale on update. Never hand-edit a stage here — edit
  `qrspi-kit/skills/qrspi/commands/` in the kit and re-install.
- **Config is yours.** `working-docs/config.json` is committed with your project and is
  the only place project specificity belongs.
- **Findings are yours.** `working-docs/findings/` accumulates what tickets taught; stage 1
  loads the relevant ones, stage 7 writes new ones and proposes promotions.

`.installed-from` in this directory records which profile and kit version produced this
copy.

## Profiles

`qrspi-kit/profiles/` ships six ready-made configs. Install with the closest one, then edit
the config it writes:

| Profile | Stack | Notes |
|---|---|---|
| `react-storefront` | React + Vite + TypeScript | npm verbs, Playwright e2e, routing/components/state/api layers |
| `composable-storefront` | Angular SAP Composable Storefront (Spartacus) | ng verbs, CMS/OCC/NgRx layers |
| `sap-commerce-ant` | SAP Commerce (Hybris), raw ant | the common on-prem shape; adds type-system and ImpEx verbs |
| `sap-commerce-gradle` | SAP Commerce CCv2, gradle wrapper | same platform, CCv2 driver |
| `springboot` | Spring Boot (Maven) | `mvnw` verbs, web/service/data layers |
| `fastapi` | FastAPI (uv) | `uv run` ruff/mypy/pytest verbs, routers/schemas/services layers |

Every verb a profile names in `changeTypeVerbs` or `sliceExample` must exist in its `build`
table, and a `MANUAL:` verb must describe a *procedure* a human follows — not an *absence*
of tooling. A mapping pointing at an absent verb is a checkpoint that can never pass.

## Adding support for a new stack

You don't edit this skill. Write a profile: a `config.json`-shaped JSON file naming that
stack's verbs, research layers, protected paths, boundary layer, slice shape, manual
verification surfaces, and trigger vocabulary. Drop it in `qrspi-kit/profiles/` and install
with it. If a stack genuinely needs a mechanic no config field can express, that's a change
to the canonical stages — route it through the findings-promotion loop so every project
gets it.

---

QRSPI, and the RPI workflow it grew from, are the work of Dex Horthy (HumanLayer). This is a
config-driven packaging of that method, not a new one.
