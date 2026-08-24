---
name: qrspi
description: >
  Generic QRSPI workflow — Ticket/Questions, Research, Design, Structure, Plan, Implement,
  Validate. One stack-neutral skill: verification verbs, research layers, protected paths
  and Jira mode all live in working-docs/config.json, so the same skill serves a React/Vite
  or Angular frontend, an SAP Commerce backend, or a Spring Boot / FastAPI service with only
  a different config ("profile"). Use when a developer wants to work a ticket through
  structured stages — blind codebase research, developer gates on design and structure,
  vertical slices, toolchain-verified implementation. Triggers on QRSPI, "work this ticket",
  and research/design/plan/implement intent against any codebase — as well as:
  {{TRIGGER_VOCABULARY}}
---

# QRSPI

You orchestrate a 7-stage workflow. **Each stage runs in a fresh context window, reads only
its declared input artifacts, stays under 40 instructions, and ends by printing the exact
next command.** Never merge stages. Never skip a developer gate.

This is **one generic skill**. There is nothing stack-specific in the stage commands —
the research layers, verification verbs, protected paths, and Jira mode all live in
`working-docs/config.json` (the "profile"). The same skill folder serves a frontend
(React/Vite, Angular Spartacus), a backend (SAP Commerce, Spring Boot, FastAPI), or any
other codebase; only the config differs.

## Entry point — one command, four tiers

Developers remember `/cq:go <TICKET-KEY> [tier]` and four tier names:
**trivial** (no workflow — just fix and verify), **simple** (brief → implement →
validate-lite), **full** (all stages; 1+2 auto-chained), **comprehensive** (full +
worktree + mandatory full verification per slice + team review of design/structure).
[`commands/0_go.md`](./commands/0_go.md) holds the recommendation heuristics and tier mechanics. Recommend a
tier, confirm it, record it. The numbered stages below are the machinery behind
full/comprehensive — developers can still invoke them directly, but don't have to.

## Stages

| # | Command | Reads | Writes | Dev gate? |
|---|---------|-------|--------|-----------|
| 0 | [`commands/0_go.md`](./commands/0_go.md) | ticket/description | tier decision | confirm tier |
| 1 | [`commands/1_ticket.md`](./commands/1_ticket.md) | Jira ticket / text | `ticket.md`, `questions.md` | skim |
| 2 | [`commands/2_research.md`](./commands/2_research.md) | `questions.md` only — **never ticket.md** | `research.md` | no |
| 3 | [`commands/3_design.md`](./commands/3_design.md) | `ticket.md` + `research.md` | `design.md` (~200 lines) | ★ YES |
| 4 | [`commands/4_structure.md`](./commands/4_structure.md) | `design.md` | `structure.md` (~2 pages) | ★ YES |
| 5 | [`commands/5_plan.md`](./commands/5_plan.md) | all artifacts | `plan.md` (checkboxes) | spot-check |
| 6 | [`commands/6_implement.md`](./commands/6_implement.md) | `structure.md` + `plan.md` | code, 1 commit/slice | no |
| 7 | [`commands/7_validate.md`](./commands/7_validate.md) | `design.md` success criteria | validation report, PR | ★ YES |

Artifacts live in `working-docs/<TICKET-KEY>/`.

## Tier selection — recommend the lightest sufficient path

Heuristics live in [`commands/0_go.md`](./commands/0_go.md). When in doubt, recommend the lower tier and say
what would bump it. Promotion mid-flight is fine — run the skipped stages then. **Tier
changes ceremony, never safety:** verification verbs, the diff-ownership gate, and the
`protectedPaths` protections apply at every tier.

## Going backward

Research reveals bad questions → re-run 1. Design finds missing facts → re-run 1+2.
Structure exposes a flawed design → re-run 3. Implementation hits a fundamental plan error
→ re-run 5 (or 3). Adapt small mismatches in place; go back only for fundamentals.

## Ground rules (apply in every stage)

- **Ground every document — never speculate.** Docs state only verified facts (with
  `file:line` for code); unknowns are flagged as open questions or `unconfirmed` and
  clarified with the developer, not guessed. No editorializing, no inferring intent, no
  tangential padding "for completeness" — comprehensive on what the work needs, and
  readable. Unverified detail misleads later stages and seeds hallucinations.
- **The repo's CLAUDE.md is authoritative** for naming/structure patterns, the designated
  boundary layer (`apiBoundary` in config — no I/O bypassing it), typed responses, user
  feedback and formatting helpers, and loading + error states on every async operation.
- **Never modify anything in `protectedPaths`** (config) — generated, vendored, or
  build-output trees (e.g. `node_modules/`/`dist/` for a JS app; `target/`/`build/` for a
  JVM app; `gensrc/`/platform/OOTB trees for SAP Commerce; `.venv/`/`__pycache__/` for
  Python). Never commit `.env` or secrets.
- **Stages never hardcode build commands.** They reference verification VERBS, resolved
  through the build adapter in `working-docs/config.json` (see below).
- Feature flow docs (`docs/<flow>/context|components|diagram.md`) are docs-before-code,
  and the final plan task is always Documentation.
- Jira/Confluence publishing is optional; local `working-docs/` is first-class.

## Writing standard — every artifact (ticket → validation)

QRSPI documents are read by a human first and the next stage's LLM second. Write for both;
this is the bar for all seven stages (the per-stage "Grounding — no speculation" blocks
enforce it).

- **Thorough but concise.** Cover everything the work needs; cut everything it doesn't. No
  padding, no restating the ticket, no "for completeness" tangents. Length follows the work.
- **Human- and LLM-readable.** Plain language, short sentences, scannable structure (headers,
  tables, tight bullets). A developer skims it in minutes; a later stage parses it without
  ambiguity. Prefer a `file:line` or an exact command over prose describing them.
- **Complete and usable.** Someone with only this document can act on it — inputs, decisions,
  and the next step are all present. Don't rely on unstated context.
- **Never make things up.** Every claim traces to something you actually read (ticket, code,
  command output). Mark unknowns `unconfirmed` or as open questions and take them to the
  developer — never fill a gap with a plausible guess. Uncertain-but-flagged beats
  confident-and-wrong.

## Build adapter — the config IS the profile

All project specificity lives in `working-docs/config.json`, written by the kit installer
from the profile you chose. Config fields:

| Field | What it holds |
|-------|---------------|
| `profile` / `profileVersion` | Which profile this config came from, and its version stamp |
| `stack` | Detected stack (drives example vocabulary only) |
| `workingDir` | Directory build commands run from (usually `.`, or a subdir) |
| `protectedPaths` | Paths the workflow must never modify |
| `apiBoundary` | The designated I/O boundary layer (path or convention), or `null` |
| `build` | The verb table: `VERB → command` (a command may be `MANUAL: <steps>`) |
| `changeTypeVerbs` | `glob → [VERBS]` — which checks a given change type requires |
| `jira` | `{ mode: mcp \| manual \| none, project }` |
| `researchLayers` | `[{ name, targets }]` — one stage-2 subagent per layer |
| `questionCategories` | Stage-1 question categories; `null` = use `researchLayers` names |
| `manualVerificationSurfaces` | Where a human checks this stack (admin console, route, endpoint) |
| `sliceExample` | What a vertical slice looks like in this stack |
| `verbNamespaces` | Multi-stack repos: verb prefix → command prefix (e.g. `FE_`) |
| `triggerVocabulary` | Stack jargon rendered into this skill's frontmatter at install |
| `_notes` | Hard-won rules worth carrying with the config |

If `config.json` is missing, say so and stop — re-run the kit installer with the right
profile rather than guessing a config. A verb may resolve to `MANUAL: <instructions>`:
stages then print the instructions and wait for developer confirmation instead of running
a command. Every verb referenced by `changeTypeVerbs` must exist in `build`.

Config is versioned in git, so the team sets it up once per repo.

## Multi-stack repos (one repo, two toolchains)

When a repo holds two stacks (e.g. a backend and its storefront), this is still **one
workflow with one config** — never install a second QRSPI skill; the `/cq` commands would
collide and cross-stack tickets want slices spanning both sides. Instead:

- Add a second verb namespace via `verbNamespaces` (e.g. `FE_INSTALL`, `FE_BUILD`,
  `FE_TEST`, each prefixed with that stack's `cd <subdir> && `) and map that stack's
  change-type globs to those verbs.
- Give each stack its own entries in `researchLayers`, scoped to its own tree.
- Stage-4 slices cut vertically across the stack: the boundary/contract change on one side
  in one slice checkpoint, the consuming piece on the other side in the next.
- Pair with that stack's knowledge skill (explicit opt-in) for framework specifics.

## Self-improvement (the contract)

This skill gets sharper every ticket. `working-docs/findings/` holds what prior runs
learned — workflow improvements and accumulated project knowledge (see its README).
Findings live with the project, not in this skill directory, because this directory is
installed by the kit and replaced wholesale on update.

- **Start of a ticket (stage 1):** list `working-docs/findings/*.md` (excluding
  README/TEMPLATE) and load any whose `applies_to.area` / `ticket_type` matches this
  ticket, so research starts informed.
- **During a ticket:** when a stage hits something the references didn't cover — a wrong
  verb mapping, a missed research category, a recurring codebase quirk — write
  `working-docs/findings/YYYY-MM-DD-{slug}.md` from `TEMPLATE.md`. Small ones count.
- **End of a ticket (stage 7):** summarize new findings and propose which to **promote**
  into the config, the repo's CLAUDE.md, or — for anything that should reach every
  project — the kit's canonical stage commands. Promotion is user-approved; mark promoted
  findings `status: promoted`.

Unpromoted findings still load next run — they help before promotion.

## This directory is installed, not edited

`.claude/skills/qrspi/` and `.claude/commands/cq/` are **generated** by the kit installer.
`working-docs/` is yours. Never hand-edit a stage file here: the change would be silently
lost on the next install and would make `/cq` behave differently from every other project.
Edit `qrspi-kit/skills/qrspi/commands/` in the kit and re-install. `.installed-from`
records which profile and kit version produced this copy.

## What this skill does NOT do

Deploy, push without consent, modify `protectedPaths` (generated/vendored/OOTB code),
write design.md before the stage-3 Q&A, proceed past failed verification, or run more
ceremony than the ticket warrants.
