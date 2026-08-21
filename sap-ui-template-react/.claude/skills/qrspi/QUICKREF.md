# QRSPI quick reference

## The only command you need

```
/cq:go <TICKET-KEY> [tier]
```

Tiers, lightest first — when in doubt, take the lower one:

| Tier | Use when | What runs |
|---|---|---|
| `trivial` | <3 files, no design choice | fix + verify, no workflow |
| `simple` | known cause, one area | brief → gate → implement → validate-lite |
| `full` | standard feature ticket | stages 1→7, gates at 3, 4, 7 |
| `comprehensive` | risky, cross-cutting, migration | full + worktree + all verbs per slice + team review |

## The stages

| # | Command | Writes | Gate |
|---|---|---|---|
| 1 | `/cq:1_ticket` | `ticket.md`, `questions.md` | skim |
| 2 | `/cq:2_research` | `research.md` | — |
| 3 | `/cq:3_design` | `design.md` | ★ |
| 4 | `/cq:4_structure` | `structure.md` | ★ |
| 5 | `/cq:5_plan` | `plan.md` | spot-check |
| 6 | `/cq:6_implement mode=dev\|claude` | code, 1 commit/slice | — |
| 7 | `/cq:7_validate` | `validation.md`, PR | ★ |

Each stage runs in a **fresh session** and prints the next command. Artifacts live in
`working-docs/<TICKET-KEY>/`.

## Going backward

| Symptom | Re-run |
|---|---|
| Research answered the wrong questions | 1 |
| Design needs facts nobody gathered | 1 + 2 |
| Structure exposes a flawed design | 3 |
| Implementation hits a fundamental plan error | 5 (or 3) |

## Where things live

| Path | Owner |
|---|---|
| `.claude/skills/qrspi/` | the kit installer — **never hand-edit** |
| `.claude/commands/cq/` | the kit installer |
| `working-docs/config.json` | you (verbs, layers, protected paths, Jira mode) |
| `working-docs/findings/` | you (what past tickets taught) |
| `working-docs/<KEY>/` | per-ticket artifacts |

## Verbs, not commands

Stages never hardcode build commands. They name VERBS — `BUILD`, `UNIT_TEST`,
`TYPECHECK`, `E2E_TEST`, whatever your profile defines — resolved through `build` in
`working-docs/config.json`. `changeTypeVerbs` says which verbs a given kind of change
requires. A verb may be `MANUAL: <steps>`, in which case the stage prints the steps and
waits for you.

Changing how this project builds means editing `config.json`, never a stage file.

## Changing the workflow itself

Edit `qrspi-kit/skills/qrspi/commands/` in the kit, then re-run the installer. A stage
edited in place is lost on the next install.
