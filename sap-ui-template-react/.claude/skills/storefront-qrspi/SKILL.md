---
name: storefront-qrspi
description: >
  QRSPI workflow for storefront projects in Claude Code — Ticket/Questions, Research,
  Design, Structure, Plan, Implement, Validate. Framework-neutral: the research layers
  and verification commands come from working-docs/config.json, so the same skill
  serves a React/Vite storefront or an Angular SAP Composable Storefront with only a
  different config. Use when a developer wants to work a Jira ticket against this
  storefront with structured stages:
  decompose a ticket into research questions; run blind layered codebase research;
  align on design, assumptions, and success criteria; break work into vertical slices;
  produce a tactical plan; implement manually or via Claude with toolchain
  verification; validate against success criteria. Triggers on QRSPI, "work this
  ticket", components, pages, state management, OCC API client, styling, routing
  combined with research/plan/implement intent.
---

# Storefront QRSPI

You orchestrate a 7-stage workflow. **Each stage runs in a fresh context window, reads only
its declared input artifacts, stays under 40 instructions, and ends by printing the exact
next command.** Never merge stages. Never skip a developer gate.

This is the storefront sibling of commerce-qrspi (which lives in the
`sap-mcp-server-l` backend repo). Same architecture; the research layers and verb
table live in `working-docs/config.json`, which is what makes this skill portable
across frontend stacks (React/Vite today; Angular Composable Storefront with a
different config).

## Entry point — one command, four tiers

Developers remember `/cq:go <TICKET-KEY> [tier]` and four tier names:
**trivial** (no workflow — just fix and verify), **simple** (brief → implement →
validate-lite), **full** (all stages; 1+2 auto-chained), **comprehensive** (full +
worktree + mandatory full verification per slice + team review of design/structure).
`commands/0_go.md` holds the recommendation heuristics and tier mechanics. Recommend a
tier, confirm it, record it.

## Stages

| # | Command | Reads | Writes | Dev gate? |
|---|---------|-------|--------|-----------|
| 0 | `commands/0_go.md` | ticket/description | tier decision | confirm tier |
| 1 | `commands/1_ticket.md` | Jira ticket / text | `ticket.md`, `questions.md` | skim |
| 2 | `commands/2_research.md` | `questions.md` only — **never ticket.md** | `research.md` | no |
| 3 | `commands/3_design.md` | `ticket.md` + `research.md` | `design.md` (~200 lines) | ★ YES |
| 4 | `commands/4_structure.md` | `design.md` | `structure.md` (~2 pages) | ★ YES |
| 5 | `commands/5_plan.md` | all artifacts | `plan.md` (checkboxes) | spot-check |
| 6 | `commands/6_implement.md` | `structure.md` + `plan.md` | code, 1 commit/slice | no |
| 7 | `commands/7_validate.md` | `design.md` success criteria | validation report, PR | ★ YES |

Artifacts live in `working-docs/<TICKET-KEY>/`.

## Going backward

Research reveals bad questions → re-run 1. Design finds missing facts → re-run 1+2.
Structure exposes a flawed design → re-run 3. Implementation hits a fundamental plan error
→ re-run 5 (or 3). Adapt small mismatches in place; go back only for fundamentals.

## Storefront ground rules (apply in every stage)

- **The repo's CLAUDE.md is authoritative** for component/state patterns, the
  designated API boundary layer (no direct fetch/HTTP in components), typed responses,
  user feedback and formatting helpers, styling conventions, and loading + error
  states on every async operation.
- Never edit `node_modules/` or `dist/`; never commit `.env`.
- **Stages never hardcode build commands.** They reference verification VERBS, resolved
  through the build adapter in `working-docs/config.json` (see below).
- Feature flow docs (`docs/<flow>/context|components|diagram.md`) are docs-before-code,
  and the final plan task is always Documentation.
- Jira/Confluence publishing is optional; local `working-docs/` is first-class.

## Build adapter — detect once, confirm, persist

On first run (any stage), if `working-docs/config.json` is missing:

1. **Detect stack:** `package.json` scripts + lockfile (npm/pnpm/yarn), bundler/CLI
   (Vite, Angular CLI, ...), TypeScript (`tsconfig.json`), test runner (vitest/jest/
   karma — may be absent), router/styling libs from dependencies. Derive
   `researchLayers` for the stack (React: routing/pages → components → state →
   API/types → build; Angular Composable Storefront: CMS components/feature modules →
   facades/connectors/adapters → state → OCC config → build) and confirm.
2. **Detect Jira mode** (`jira.mode`: `mcp` | `manual` | `none`) exactly as in
   commerce-qrspi: MCP tools respond → `mcp`; Jira exists but unreachable → `manual`
   (developer pastes ticket in; outbound updates written paste-ready to
   `working-docs/<KEY>/jira-updates.md`); no Jira → `none`.
3. **Resolve the verb table**, confirm with the developer, save. Typical verbs:

| Verb | Example |
|------|---------|
| INSTALL | `npm install` |
| TYPECHECK | `npx tsc --noEmit` |
| LINT | `npm run lint` |
| BUILD | `npm run build` |
| UNIT_TEST | `npx vitest run` — or `MANUAL:` when no runner is configured |
| DEV_SERVER | `npm run dev` (long-running; for manual verification steps) |
| PREVIEW | `npm run preview` |

   A verb may resolve to `MANUAL: <instructions>` — stages then print the instructions
   and wait for developer confirmation instead of running a command.
4. **Change-type → verbs mapping** (in config, editable): ts/tsx → TYPECHECK + LINT +
   BUILD; styles/Tailwind → BUILD; package.json/lockfile → INSTALL + BUILD; any slice
   checkpoint → TYPECHECK + LINT minimum.

Config is versioned in git, so the team sets it up once per repo.

## What this skill does NOT do

Deploy, push without consent, edit node_modules/dist, write design.md before the
stage-3 Q&A, proceed past failed verification, or run more ceremony than the ticket
warrants.
