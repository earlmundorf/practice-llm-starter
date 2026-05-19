---
date: YYYY-MM-DD
project: {{project-name}}
phase: {{A-build | B-spring | C-tomcat | D-oauth | E-tests | F-data-occ | G-smartedit | cross-cutting}}
applies_to:
  java_from: {{17|21}}
  spring_from: {{5.x}}
  commerce_from: {{e.g. 2211.0}}
kind: {{gap | surprise | new-incident | practical-fix | confirmation}}
status: unpromoted
related_refs:
  - references/sap-docs/{{nn-filename}}.md
  - references/known-incidents.md
promotion_target: {{e.g. references/known-incidents.md OR references/decision-tree.md Branch X}}
---

## What happened

_One or two sentences. Plain English. What did we encounter that wasn't in the references?_

## Context

_Enough to reproduce / recognize next time. Which extension, which config file, which command, which log line. Relevant file paths._

## The SAP-doc gap (if applicable)

_Which reference was silent or incorrect, and what it should say._

## The fix that worked

_Concrete change — diff, config snippet, command. If exploratory, note what was tried and rejected._

## Why this generalizes

_Why this is worth promoting to a reference rather than leaving as a one-off. If it doesn't generalize, note that and mark `status: project-specific` so promotion review can skip it._

## Promotion suggestion

_Exactly what to change in `promotion_target` and where. Leave this blank if the finding still needs validation on a second project._
