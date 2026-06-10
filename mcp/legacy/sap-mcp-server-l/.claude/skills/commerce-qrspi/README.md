# commerce-qrspi

QRSPI workflow for SAP Commerce development in Claude Code: **Ticket/Questions →
Research → Design → Structure → Plan → Implement → Validate.** Take a Jira ticket from
"what does this even mean" to a verified PR, with developer review concentrated where it
has the most leverage.

## Why

The original Research-Plan-Implement (RPI) workflow failed at scale in three ways
(per its creator, Dex Horthy, 2026): instruction-budget overflow (models silently skip
steps past ~150-200 instructions; RPI phases packed 85+), magic words (interactive
planning only triggered with specific phrasing), and the plan-reading illusion (a
1,000-line plan reads as authoritative whether or not its assumptions hold). QRSPI fixes
these structurally: small stages (<40 instructions) in fresh context windows, mandatory
interaction, and human review of two short artifacts — design (~200 lines) and structure
(~2 pages) — instead of one long plan.

This skill is QRSPI specialized for SAP Commerce: layered codebase research
(extensions → type system → service layer → storefront → impex), a build adapter that
works across project shapes, and Jira as the entry point.

## Install

```bash
cp -r commerce-qrspi <repo>/.claude/skills/commerce-qrspi
mkdir -p <repo>/.claude/commands/cq
cp commerce-qrspi/commands/*.md <repo>/.claude/commands/cq/
```

Verify in Claude Code: `/cq` autocompletes 7 commands. See `WALKTHROUGH.md` for a full
worked example (new OCC controller endpoint).

## Usage — one command, four tiers

```
/cq:go CMRC-1234
```

Claude recommends a tier from the ticket's scope; you confirm. That's the whole mental
model:

| Tier | When | What you get |
|---|---|---|
| **trivial** | Typo, config value, <3 files | No workflow — fix, verify, diff |
| **simple** | Bug with known cause, small change | 1-page brief (gate) → implement → validate-lite |
| **full** | Standard feature ticket | Questions+research auto-chained, design gate, structure gate, plan, implement, validate |
| **comprehensive** | Cross-extension, risky, migration | full + worktree + mandatory integration tests + team review of design/structure |

Promote mid-flight if a ticket turns out bigger than tiered ("this is bigger than we
thought" — the skipped stages run then). Safety rails (verification, diff-ownership
gate, OOTB protection) apply at every tier.

## The stages (machinery behind full/comprehensive)

| Command | Does | You spend |
|---|---|---|
| `/cq:go <KEY> [tier]` | Recommends + confirms tier, drives the rest | 1 min |
| `/cq:1_ticket <KEY>` | Ticket → problem statement, assumptions, draft success criteria, neutral research questions | 3 min skim |
| `/cq:2_research <dir>` | Layered subagents answer the questions, blind to the ticket; facts with file:line | 0 (agent) |
| `/cq:3_design <dir>` | ★ Mandatory Q&A (options A/B/C), then design.md: decisions, confirmed assumptions, success criteria | 10 min |
| `/cq:4_structure <dir>` | ★ Vertical slices with verification checkpoints | 5 min |
| `/cq:5_plan <dir>` | Tactical checkboxed plan, commands resolved from config | 2 min spot-check |
| `/cq:6_implement <dir> mode=dev\|claude` | You code with Claude pairing, or Claude executes slice-by-slice; one commit per slice, never proceeds on red | varies |
| `/cq:7_validate <dir>` | ★ Re-runs success criteria, diff-ownership gate, PR grounded in design.md, Jira update | 10 min |

Run each stage in a **fresh session**. Each command prints the next one. Artifacts live
in `working-docs/<TICKET-KEY>/` (`ticket.md`, `questions.md`, `research.md`, `design.md`,
`structure.md`, `plan.md`, `validation.md`, plus `jira-updates.md` in manual mode).

## Configuration — `working-docs/config.json`

Created on first run by detection + developer confirmation; commit it. Shape:

```json
{
  "layout": "ccv2",
  "commerceVersion": "2211.45",
  "customExtensions": ["acmecore", "acmefacades", "acmewebservices"],
  "storefront": "spartacus",
  "build": {
    "BUILD": "./gradlew ybuild",
    "FULL_BUILD": "./gradlew yclean yall",
    "TYPE_SYSTEM_UPDATE": "./gradlew yupdatesystem",
    "UNIT_TEST": "./gradlew yunittests -Dtestclasses.extensions={ext}",
    "INTEGRATION_TEST": "./gradlew yintegrationtests -Dtestclasses.extensions={ext}",
    "IMPEX_IMPORT": "./gradlew impex -Pfile={path}",
    "SERVER_RESTART": "./gradlew stopServer startServer"
  },
  "changeTypeVerbs": {
    "items.xml": ["BUILD", "SERVER_RESTART", "TYPE_SYSTEM_UPDATE"],
    "java|beans.xml": ["BUILD", "SERVER_RESTART"],
    "impex": ["IMPEX_IMPORT"]
  },
  "jira": { "mode": "mcp", "project": "CMRC" }
}
```

Any build entry may be `"MANUAL: <instructions>"` (e.g., impex via HAC on ant projects) —
the workflow prints the instructions and waits for confirmation instead of running a
command. Stages reference verbs, never literal commands, so one skill serves gradle,
ant, and bespoke-CI projects alike.

## Jira modes

- **`mcp`** — Atlassian MCP connected: tickets fetched, comments and transitions automated.
- **`manual`** — Jira exists but is unreachable from Claude Code (no connector, VPN-only,
  auth policy): you paste ticket content at stage 1; every outbound update (plan comment,
  PR link, status transition) is written paste-ready to
  `working-docs/<TICKET-KEY>/jira-updates.md`. Nothing is silently dropped.
- **`none`** — no Jira; slug-named directories, integration skipped.

A failed MCP call always degrades to manual — it never blocks the workflow.

## Guardrails

Research never sees the ticket (facts, not opinions). Design cannot be written before
the Q&A. No Epic/Story/sub-task creation — your ticket is the only Jira artifact. No PR
without an explicit "I read this diff and own it." Never modifies `gensrc/`, platform,
or OOTB modules; never pushes or deploys. Checkboxes in plan.md make every stage
resumable after a context reset.

## References

- Horthy, "From RPI to QRSPI" (Coding Agents 2026): youtube.com/watch?v=5MWl3eRXVQk
- Lavaee, "From RPI to QRSPI": alexlavaee.me/blog/from-rpi-to-qrspi
- Command mechanics adapted from github.com/matanshavit/qrspi (MIT)
