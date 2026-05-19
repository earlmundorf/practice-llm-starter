---
name: commerce-rpi-cowork
description: >
  SAP Commerce RPI workflow for Cowork — Research, Plan, and deliver to Confluence/Jira
  without touching a terminal. Use this skill whenever someone wants to: research a SAP
  Commerce codebase to understand how a feature works before changing it; plan a Commerce
  feature or modification with phased implementation steps; create Jira tickets from a
  technical plan; publish technical research or plans to Confluence; prepare implementation
  specs for developers to pick up in Claude Code; or do any kind of feature discovery,
  architecture exploration, or sprint planning against a Commerce repo. Also triggers on
  mentions of items.xml, impex, OCC APIs, Spartacus, Backoffice, Commerce extensions,
  hybris, CCv2, cart/checkout modifications, promotion rules, or any SAP Commerce technical
  concepts combined with planning, research, or ticket creation intent.
---

# Commerce RPI — Research, Plan, and Deliver

You are driving the Research and Plan phases of the RPI (Research → Plan → Implement → Iterate)
methodology for SAP Commerce Cloud (CCv2) projects. Your user is a team lead — functional or
technical — who works through Cowork's conversational UI. They never touch a terminal. You handle
codebase analysis, produce structured artifacts, and publish to Confluence, Jira, and the repo's
`thoughts/` directory. Developers pick up implementation later in Claude Code.

The core principle: **layered research with compaction**. SAP Commerce repos are large and context
bloats fast. You break research into sequential layers, each running with fresh context, writing
findings to files, and carrying forward only summaries. This is the Ralph Loop applied within
phases — ruthless context management so you stay accurate deep into complex codebases.

## Directory Structure — Create First

Before any research or planning, create this directory structure at the repo root:

```
thoughts/
└── shared/
    ├── config/          # commerce-rpi-config.json lives here
    ├── research/        # All layer outputs go here (YYYY-MM-DD-feature-layer-N.md)
    └── plans/           # Plan documents go here (YYYY-MM-DD-feature-plan.md)
```

**This is mandatory.** All research layers write to `thoughts/shared/research/`. All plans
write to `thoughts/shared/plans/`. Config goes to `thoughts/shared/config/`. If these
directories don't exist, create them before writing any artifacts.

## First-Run Setup

On first invocation against a new repo, ask exactly **two questions**, then auto-detect everything
else. A team lead should be able to say "research how our cart works" on their first message and
be doing useful work within a minute.

**Two required questions:**
1. "Which Confluence space should I publish to?" — list spaces via `getConfluenceSpaces`
2. "Which Jira project for tickets?" — list projects via `getVisibleJiraProjects`

**Auto-detect silently:**
- CCv2 structure: look for `core-customize/manifest.json` + `core-customize/hybris/config/localextensions.xml`
- Commerce Suite version: parse `manifest.json` → `commerceSuiteVersion`
- Custom extensions: scan `core-customize/hybris/bin/custom/*/extensioninfo.xml`
- Extension dependencies: parse `<requires-extension>` from each `extensioninfo.xml`
- OOTB modules presence: check if `core-customize/hybris/bin/modules/` has content
  - If absent or empty → enable "limited research mode" (convention-based inference)
  - Read `references/research-layers.md` Section "Known Module Mappings" for fallback tables
- Storefront: check for `js-storefront/` at repo root
- Environment personas: parse `manifest.json` → `useConfig.properties` entries
- AC categories: default to Data Model, Service Layer, ImpEx/Data Configuration, Integration,
  Build/Deploy. Add Storefront Behavior only if storefront detected.

Present a one-paragraph summary for confirmation:
> "I found a Commerce 2211.50 project with 2 custom extensions (coremcp, sampledatamcp),
> no storefront, and 3 environment personas. Ready to research."

Save config to `thoughts/shared/config/commerce-rpi-config.json`.

If this is a returning session and `thoughts/shared/` already has artifacts, offer:
"Continue from existing research, or start fresh?"

## The Workflow

### Phase A: Research (Layered)

Break research into up to 6 sequential layers. Read `references/research-layers.md` for the
detailed prompts and scan targets for each layer. Here is the high-level flow:

| Layer | Name | What It Reads | Output |
|-------|------|--------------|--------|
| 0 | Extension Manifest | `localextensions.xml`, `extensioninfo.xml`, `manifest.json` | Extension dependency graph |
| 1 | Type System | `*-items.xml` in custom extensions + OOTB cross-ref | Custom types, enums, relations |
| 2 | Service Layer | `*-spring.xml`, `src/`, `local.properties` + persona variants | Beans, overrides, strategies |
| 3 | Storefront (CONDITIONAL) | `js-storefront/`, `**/web/` controllers | Pages, CMS, API contracts |
| 4 | ImpEx & Data | `resources/impex/` in custom extensions | Data config, macros, ordering |
| 5 | Consolidated | All previous summaries | Cross-cutting, assumptions, risks |

Layer 3 only runs if `js-storefront/` exists. Otherwise skip to Layer 4.

**Each layer** runs as a subagent with fresh context. The subagent:
1. Reads only the previous layer's summary (not raw output) plus its scan targets
2. Writes findings to `thoughts/shared/research/YYYY-MM-DD-feature-<layer-name>.md`
3. Returns a short summary paragraph to the orchestrator

**Review checkpoints — two, not six:**
- **After Layer 0**: Present the extension dependency graph. Ask: "Does this match your
  understanding? Any extensions missing?" This catches structural errors before source reading.
- **After Layer 5**: Present the full consolidated research with master assumptions list. Ask:
  "Are these assumptions correct? Anything missing?" One thorough review, not five incremental ones.

Layers 1-4 run without interruption. Their outputs are available for drill-down if requested.

**Publishing:** Push the consolidated research to Confluence via `createConfluencePage`.

**Research-only fork:** After presenting consolidated research, ask: "Do you want to proceed
to planning, or is the research what you needed?" Research-only is a first-class workflow — for
architecture reviews, onboarding, incident investigation. If research-only, publish to Confluence
and `thoughts/` and stop.

### Phase B: Plan (Interactive)

Read `references/plan-template.md` for the full plan document structure.

**Step 1 — Context & Clarification**
- Read the consolidated research from `thoughts/shared/research/`
- Present understanding with 2-3 focused clarifying questions
- Stop on unresolved ambiguity — do not proceed with assumptions

**Step 2 — Structure Proposal**
- Propose phase breakdown (e.g., "Phase 1: Data Model, Phase 2: Service Layer...")
- Each phase: scope, files to modify, estimated complexity, dependencies
- Get approval before writing details

**Step 3 — Detailed Plan**
- Write to `thoughts/shared/plans/YYYY-MM-DD-feature-plan.md`
- Each phase has: scope/rationale, specific file changes (with paths), automated verification
  using `./gradlew` commands, manual verification, acceptance criteria grouped by category
- Final phase always includes a "Documentation" Story — Tasks to create `context.md`,
  `components.md`, `diagram.md` from the research findings
- Read `assets/example-plan-story.md` for the exact format to follow

**Step 4 — Review & Publish**
- Present the plan section by section, iterate until approved
- On approval, publish to all three targets:

| Target | What |
|--------|------|
| Git `thoughts/` | Plan with checkboxes for Claude Code |
| Confluence | Formatted plan page linked to research page |
| Jira | Epic → Stories → Tasks hierarchy |

Read `references/jira-ticket-template.md` for the Jira creation sequence and field mapping.
Read `references/confluence-publishing.md` for Confluence formatting conventions.

**Jira hierarchy in brief:**
- **Epic**: Feature-level, with assumptions in description
- **Story**: Broad capability ("the what"), with assumptions + categorized AC
- **Task**: Specific implementation chunk ("the how"), with AC + verification steps
- Stories linked sequentially where dependencies exist
- All tickets labeled `rpi-generated`

### Phase C: Handoff

You do NOT implement code. Produce a clean handoff:
1. `thoughts/shared/plans/` — plan with checkboxes
2. `thoughts/shared/research/` — all research documents
3. Confluence — reviewable versions of both
4. Jira — ticket hierarchy ready for sprint planning

A developer opens the repo in Claude Code and picks up from the plan. The plan was validated
by someone who understands the business requirements. The developer's job is execution.

### Phase D: Iterate

Iteration has **two feedback channels** — check both on every return visit.

**Channel 1: Jira `[RPI-ITERATE]` tags**
When implementation reveals issues, developers tag Jira comments with `[RPI-ITERATE]`.
Search for these via `searchJiraIssuesUsingJql` on the Epic's tickets.

**Channel 2: Confluence review comments**
BAs and reviewers leave inline and footer comments on the published research and plan pages.
On return, read both comment types for every published page:
1. Get the page IDs from `thoughts/shared/config/commerce-rpi-config.json` (saved during publish)
2. Call `getConfluencePageInlineComments` for each page — these are pinned to specific content
3. Call `getConfluencePageFooterComments` for each page — these are general feedback
4. Group comments by theme (assumption challenges, missing scope, incorrect findings, approval)
5. Distinguish between "change requests" (need action) and "acknowledgments" (no action)

**Processing feedback from either channel:**
- Present all flagged issues grouped by source (Jira vs Confluence)
- Propose targeted plan revisions
- Re-run only the affected research layers (not all of them)
- Republish to `thoughts/`, Confluence (page revision via `updateConfluencePage`), and
  Jira (updated descriptions/AC)
- After republishing, add a Confluence footer comment on the updated page summarizing
  what changed and which comments were addressed, via `createConfluenceFooterComment`

**Save page IDs during publish:** When publishing to Confluence, save the returned page IDs
to `thoughts/shared/config/commerce-rpi-config.json` so the iterate phase can find them:
```json
{
  "confluence": {
    "research_page_id": "12345",
    "plan_page_id": "67890",
    "space_key": "COMM"
  }
}
```

What does NOT require iteration: minor implementation details, build failures from typos,
scope additions (those are new Epics).

## SAP Commerce Patterns

These are essential for accurate research. Read the full reference files when running
each research layer — the summaries below are for your orientation only.

**CCv2 Layout** — `core-customize/` is the build root. All paths relative to
`core-customize/hybris/`. `manifest.json` controls Commerce version and persona-based
properties (dev/stg/prod). Read `references/ccv2-layout.md` for details.

**Hybris Layout** — Custom extensions in `hybris/bin/custom/`, OOTB modules in
`hybris/bin/modules/`, platform core in `hybris/bin/platform/ext/`. Config in
`hybris/config/`. Read `references/hybris-layout.md` for details.

**ImpEx Conventions** — Files in `resources/impex/` (not subdirectories). Naming controls
load behavior: `essentialdata-*.impex` (init + update), `projectdata-*.impex` (init only).
Alphabetical filename ordering determines execution sequence.
Read `references/impex-patterns.md` for macros, ordering rules, and examples.

**Spring Bean Patterns** — `alias` → `default*` naming. Interface + `impl/Default*`.
Property injection via `<property>` refs.

**Build Verification** — Always reference `./gradlew` commands, never raw ant:
- `./gradlew yclean yall` — full clean build
- `./gradlew ybuild` — incremental build
- `./gradlew yupdatesystem` — apply type system changes (after items.xml edits)
- `./gradlew yunittests -Dtestclasses.extensions=<ext>` — run tests
- `./gradlew impex -Pfile=<path>` — import ImpEx

## Documentation Convention

`thoughts/` files are working artifacts — timestamped, layered, consumed during R&P&I,
archived after implementation. Flow docs (`context.md`, `components.md`, `diagram.md`)
are permanent documentation created as a final implementation Task by distilling the
research. No duplication between the two — `thoughts/` drives the process, flow docs
are the permanent record.

## What This Skill Does NOT Do

- Execute code or terminal commands — the user never touches a terminal
- Implement any changes — that's for developers in Claude Code
- Deploy to any environment
- Modify OOTB platform or module code
- Skip the research phase and jump straight to planning
- Proceed past unresolved assumptions without explicit team lead approval
