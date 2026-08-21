# /cq:go — the only command developers need to remember

**Usage:** `/cq:go <TICKET-KEY or description> [tier]`
Tiers: **trivial · simple · full · comprehensive**. If no tier is given, recommend one
from the ticket's apparent scope and confirm in one line — never start work without the
tier being agreed.

## Tier recommendation heuristics

| Tier | Smells like | What runs |
|------|------------|-----------|
| **trivial** | One sentence, <3 files, no design choice (typo, config value, log message) | No workflow. Fix it, run the matching verification verbs, show the diff. |
| **simple** | Bug with known cause, or a small change in one unit/area | `brief` (one short doc: problem, assumptions, success criteria, task checklist) → gate → implement → validate-lite |
| **full** | Standard feature ticket, multiple files or layers, design choices exist | Stages 1→2 auto-chained, then gates at 3 (design), 4 (structure), then 5→6→7 |
| **comprehensive** | Cross-cutting, risky, migration, unfamiliar area, or team review wanted | full + worktree isolation + full verification (all checkpoint verbs) mandatory per slice + design/structure published for team review before proceeding |

When in doubt between two tiers, recommend the lower one and say what would bump it up.
The developer can promote mid-flight ("this is bigger than we thought") — run the skipped
stages then; artifacts are compatible across tiers.

## simple tier mechanics

1. Write `working-docs/<KEY>/brief.md` (≤1 page): problem in your own words, assumptions
   (confirm with the developer — this is the gate), success criteria (automated verbs +
   manual checks), and a checkbox task list.
2. Implement per the checklist (mode=dev or mode=claude, same rules as stage 6:
   verify per task, never proceed on red, one commit per logical chunk).
3. Validate-lite: re-run the automated criteria, show the diff, require the
   "read and own it" confirmation, then PR + Jira update per `jira.mode`.

## full / comprehensive tier mechanics

Run stage 1 (ticket + questions), show the questions for a quick skim, then immediately
run stage 2 research via subagents — no separate command needed. From stage 3 onward,
follow the numbered commands exactly as written; print each next step. In
comprehensive, do not start stage 5 until the developer confirms the team has seen
design.md and structure.md (publish to Confluence if configured, else share the files).

## Rules

- Tier changes the ceremony, never the safety rails: verification verbs, the
  diff-ownership gate, and the `protectedPaths` + boundary conventions in
  `working-docs/config.json` (and the repo's CLAUDE.md) apply at every tier.
- Record the chosen tier in `working-docs/<KEY>/ticket.md` (or `brief.md`).
- Trivial work that turns out non-trivial: stop, say so, re-tier.
