# QRSPI kit — install

Drop-in QRSPI workflow for Claude Code, config-driven the same way
[`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi) works. **One** stack-neutral skill;
the stack lives in a profile.

## Install

Unzip the kit at the root of the project you want it in, list the profiles, install one:

**macOS / Linux / WSL / Git Bash**

```bash
unzip qrspi-kit.zip
./qrspi-kit/install.sh list
./qrspi-kit/install.sh sap-commerce-ant
```

**Windows PowerShell**

```powershell
Expand-Archive qrspi-kit.zip -DestinationPath .
.\qrspi-kit\install.ps1 list
.\qrspi-kit\install.ps1 sap-commerce-ant
```

> Blocked by the execution policy?
> `powershell -ExecutionPolicy Bypass -File .\qrspi-kit\install.ps1 sap-commerce-ant`

That's it. No Node, no Python, no `jq` — just the shell you already have.

## Which profile?

`install.sh list` prints all of them with a one-line summary. The short version:

| Profile | Use when |
|---|---|
| `sap-commerce-ant` | SAP Commerce (Hybris) with `hybris/bin/platform/build.xml` + `setantenv.sh` — the common on-prem setup |
| `sap-commerce-gradle` | SAP Commerce CCv2 with a `gradlew` wrapper and the SAP y-task plugin |
| `react-storefront` | React + TypeScript + Vite |
| `composable-storefront` | Angular + SAP Composable Storefront (Spartacus) |
| `springboot` | Java + Spring Boot (Maven or Gradle) |
| `fastapi` | Python + FastAPI (uv, pip, or Poetry) |

Nothing fits? Copy the closest profile, edit it, install with that. See `profiles/README.md`.

## What the installer writes

```
.claude/skills/qrspi/         the skill            GENERATED — never hand-edit
.claude/commands/cq/          the /cq:* commands   GENERATED — never hand-edit
.github/prompts/cq-*.md       Copilot launchers    GENERATED — never hand-edit
working-docs/config.json      your stack's config  yours, committed
working-docs/findings/         what tickets teach   yours, committed
```

The rule: **`.claude/` and the generated `.github/prompts/cq-*` are generated,
`working-docs/` is yours.** Re-installing replaces the first and never touches the second.

## Which editor sees what

| | Claude Code | Copilot in VS Code | Cursor |
|---|---|---|---|
| the `qrspi` skill (`.claude/skills/`) | ✅ | ✅ | ✅ |
| the seven stages | `/cq:1_ticket` | `/cq-1-ticket` | via the skill |
| launcher directory read | `.claude/commands/cq/` | `.github/prompts/` | — |

`.claude/skills/` is a shared search path — Copilot and Cursor both read it, so the skill
itself needs no export. Only the stage launchers differ, because Copilot does not read
`.claude/commands/` and colons are illegal in its command names, so `/cq:1_ticket` becomes
`/cq-1-ticket`. The prompt files are thin: each points at the same
`.claude/skills/qrspi/commands/*.md` the Claude commands use, so there is one copy of every
stage instruction and no chance of the two drifting.

Two limits worth knowing. Prompt files are not consumed by VS Code's **Agent Host**
sessions (skills are), and **Cursor does not read `.github/prompts/`** — in Cursor, invoke
the skill (`/qrspi`, or Option+Enter to pin it for the session) and name the stage you
want. Only `cq-*.prompt.md` is regenerated, so your own prompt files in that folder are
left alone.

- An existing `config.json` is **never overwritten**. The installer writes
  `config.json.new` beside it instead, for you to diff and merge.
- Existing findings are never touched; only `README.md`/`TEMPLATE.md` are seeded if missing.
- The kit is left where you unzipped it and added to `.gitignore`. Delete it whenever you
  like — re-unzip to switch profiles or update.

## After installing

1. **Fill in the placeholders** the config names. For SAP Commerce that's `<ext>` — your
   custom extension(s) — in the test verbs.
2. **Set `jira.mode`**: `mcp` if the Atlassian connector is live in your session, `manual` if
   Jira exists but is unreachable (you paste tickets in; outbound updates are written
   paste-ready), `none` if there's no Jira.
3. **Check the verb table** against how the project really builds. This is the one file that
   matters — the stages never hardcode commands, they resolve verbs from it.
4. **Commit** `working-docs/config.json` and the `.claude/` install.

## Use

```
/cq:go YOUR-TICKET
```

The skill recommends a tier — `trivial` (fix + verify), `simple` (brief → implement →
validate), `full` (all seven stages, gates at Design/Structure/Validate), `comprehensive`
(full + worktree + all verbs per slice + team review) — and you confirm it. See
`skills/qrspi/QUICKREF.md` for the one-page version and `WALKTHROUGH.md` for a worked ticket.

## Updating, and changing the workflow

The kit is the source of truth. To pick up a newer kit, unzip it over the old one and
re-install: the skill is replaced, your config and findings survive. To change the workflow
itself, edit `qrspi-kit/skills/qrspi/commands/` and re-install — a stage edited inside
`.claude/skills/qrspi/` is lost on the next install and would make `/cq` behave differently
here than everywhere else.
