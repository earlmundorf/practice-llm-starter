---
date: 2026-06-09
ticket: SAMPLE-01
tier: trivial
stage: 6-implement (surfaced at verification)
applies_to:
  area: knowledge-base
  ticket_type: data
kind: project-knowledge
status: unpromoted
promotion_target: commands/2_research.md research layers (KB-area awareness) + the repo's index-solr behavior is now fixed in-tree
---

## What happened

Editing a `KnowledgeEntry` in `projectdata-50-knowledge.impex` and importing it updates
the DB, but the change is **invisible to `/info/*` endpoints and the agent's `info_search`
tool until `knowledgeIndex` is reindexed**. Worse: at the time, `scripts/index-solr.groovy`
only reindexed `thinkshopIndex` and the two backoffice configs — it omitted `knowledgeIndex`
entirely, so the documented `./scripts/index-solr.sh` reindex flow never refreshed knowledge
content. Verification caught the stale read; the script was fixed in the same ticket.

## Context

- Stage 6 verification of SAMPLE-01 (a one-line KB copy change) came back red: DB had the
  new text, the live `/info/about-thinkshop` endpoint did not.
- Root cause was two-layered: (1) KB content is Solr-served, not DB-served, on the read path;
  (2) `index-solr.groovy` was missing `knowledgeIndex` from its config list.
- Fix shipped in SAMPLE-01: added `knowledgeIndex` to `index-solr.groovy`.

## The fix / the knowledge

For any future ticket whose **area is knowledge-base** (KnowledgeEntry content, `/info/*`,
`info_search`/`info_get`): the verification plan MUST include a `knowledgeIndex` reindex
(`./scripts/index-solr.sh`, which now covers it) after the impex import, and design-stage
success criteria should assert the change is live via the endpoint/tool, not just in the DB.
The same DB-vs-Solr split applies to product content via `thinkshopIndex`.

## Why this generalizes

Every KB or product-content ticket in this repo hits the same DB-vs-Solr read-path gap. A
research stage that knows it up front writes a correct verification plan the first time
instead of discovering it red at stage 6. This is exactly the kind of accumulated project
knowledge the findings loop exists to carry forward.

## Promotion suggestion

Add a one-line note to `commands/2_research.md` (or the repo CLAUDE.md) under the Solr /
knowledge-base research area: "Content changes (KnowledgeEntry, Product) are Solr-served on
the read path — a `knowledgeIndex` / `thinkshopIndex` reindex (`./scripts/index-solr.sh`) is
part of verification, not optional." The script gap itself is already fixed in-tree, so this
finding promotes as *workflow awareness*, not a code change.
