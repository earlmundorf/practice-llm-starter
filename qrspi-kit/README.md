# QRSPI kit

The QRSPI workflow for Claude Code as a drop-in kit: **one** stack-neutral skill plus a
library of profiles. Zip it, unzip it at a project root, pick a profile, done.

Modelled on [`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi). QRSPI itself is
Dex Horthy's (HumanLayer) method; this is a config-driven packaging of it.

```
qrspi-kit/
├── skills/qrspi/        the skill — SKILL.md, 8 stage commands, docs, findings seed
├── profiles/            the flavor library (one JSON per stack)
├── install.sh           macOS / Linux / WSL / Git Bash
├── install.ps1          Windows PowerShell
├── reference/           example project CLAUDE.md files, paired with profiles
├── VERSION
└── INSTALL.md           how to install — start here
```

## Quick start

```bash
unzip qrspi-kit.zip           # at the target project's root
./qrspi-kit/install.sh list
./qrspi-kit/install.sh sap-commerce-ant
```

Windows: `.\qrspi-kit\install.ps1 list`, then `.\qrspi-kit\install.ps1 sap-commerce-ant`.
Full detail in [INSTALL.md](INSTALL.md).

## The idea

Seven stages, each in a fresh context window, with human gates where they're cheap:

| Stage | Writes | Gate |
|---|---|---|
| 1 Ticket & questions | `ticket.md`, `questions.md` | skim |
| 2 Research (blind — never reads the ticket) | `research.md` | — |
| 3 Design | `design.md` | ★ |
| 4 Structure (vertical slices) | `structure.md` | ★ |
| 5 Plan | `plan.md` | spot-check |
| 6 Implement (one commit per slice) | code | — |
| 7 Validate & ship | `validation.md`, PR | ★ |

Four tiers scale the ceremony — `trivial`, `simple`, `full`, `comprehensive` — and tier
changes ceremony, never the safety rails.

**Nothing in the stages is stack-specific.** They reference verification *verbs*
(`BUILD`, `UNIT_TEST`, `TYPE_SYSTEM_UPDATE`, …), research *layers*, protected paths, and
manual-verification surfaces — all resolved from `working-docs/config.json`. That's why the
same eight files serve an ant-built SAP Commerce backend and a Vite React storefront.

## Ownership, in one line

`.claude/skills/qrspi/` and `.claude/commands/cq/` are **generated**. `working-docs/` is
**yours**. Re-installing replaces the first and never touches the second.

The installer only ever reads and writes those three paths. Any other skills a project
carries — `sap-commerce`, `impex`, `spartacus-*`, `react-ecommerce`, and so on — are left
completely alone.

## Profiles

| Profile | Stack |
|---|---|
| `sap-commerce-ant` | SAP Commerce (Hybris), raw ant — the common on-prem setup |
| `sap-commerce-gradle` | SAP Commerce CCv2, gradle wrapper |
| `react-storefront` | React + TypeScript + Vite |
| `composable-storefront` | Angular + SAP Composable Storefront (Spartacus) |
| `springboot` | Java + Spring Boot |
| `fastapi` | Python + FastAPI |

See [profiles/README.md](profiles/README.md) for the field reference and how to add one.

## Changing the workflow

Edit `skills/qrspi/commands/` here, then re-install. A stage edited inside an installed
`.claude/skills/qrspi/` is lost on the next install and makes `/cq` behave differently in
that one project — which is the drift this kit exists to end.

Per-project learnings don't belong in the stages at all: they go to
`working-docs/findings/`, and stage 7 proposes promoting the ones that generalize.

## Release

```bash
zip -r qrspi-kit.zip qrspi-kit/
```

No build step — the kit is inert source.
