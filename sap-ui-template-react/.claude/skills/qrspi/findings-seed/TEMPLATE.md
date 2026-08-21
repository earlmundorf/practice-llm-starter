---
date: YYYY-MM-DD
ticket: {{TICKET-KEY or slug}}
tier: {{trivial | simple | full | comprehensive}}
stage: {{0-go | 1-ticket | 2-research | 3-design | 4-structure | 5-plan | 6-implement | 7-validate}}
applies_to:
  area: {{a researchLayers name, or: styling | build | config | cross-cutting}}
  ticket_type: {{feature | bug | data | config | refactor}}
kind: {{workflow | project-knowledge}}
status: unpromoted
promotion_target: {{e.g. working-docs/config.json verb table | repo CLAUDE.md | kit commands/2_research.md research layers}}
---

## What happened

_One or two sentences, plain English. What did the workflow or the codebase do that the
next ticket should know?_

## Context

_Enough to recognize it next time: which stage, which files/commands, which ticket area._

## The fix / the knowledge

_For a workflow finding: the concrete config or stage-command change.
For a project-knowledge finding: the fact and where it bit (file:line, command, log line)._

## Why this generalizes

_Why it's worth promoting rather than leaving as a one-off. If it doesn't generalize,
say so and mark `status: project-specific` so promotion review skips it._

## Promotion suggestion

_Exactly what to change in `promotion_target`. Leave blank if it needs a second
ticket to confirm._
