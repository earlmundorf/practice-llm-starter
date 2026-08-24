---
name: cq-4-structure
description: 'QRSPI Stage 4 — Structure (DEV GATE 2)'
argument-hint: '<TICKET-KEY or description>'
agent: agent
---

Follow [`.claude/skills/qrspi/commands/4_structure.md`](../../.claude/skills/qrspi/commands/4_structure.md)
exactly — it is the canonical instruction for this stage. Do not summarize or
reorder it.

Resolve every verification VERB, research layer, protected path and manual-check
surface from [`working-docs/config.json`](../../working-docs/config.json); never
hardcode a build command. Prior learnings are in
[`working-docs/findings/`](../../working-docs/findings/) — load the ones whose
area matches before starting.

Stage artifacts belong in `working-docs/<TICKET-KEY>/`. Honor every developer gate
the stage declares: stop and wait rather than continuing past one.

The ticket key or task description follows this command.
