---
name: commerce-rpi-code
description: >
  SAP Commerce RPI workflow for Claude Code — Research, Plan, and optionally Implement
  without leaving the terminal. Use this skill whenever a developer wants to: research a SAP
  Commerce codebase to understand how a feature works before changing it; plan a Commerce
  feature or modification with phased implementation steps; implement a planned change with
  build verification; create Jira tickets from a technical plan; pick up a plan from
  thoughts/shared/ and implement it; or do feature discovery, architecture exploration, or
  sprint planning against a Commerce repo. Also triggers on mentions of items.xml, impex,
  OCC APIs, Spartacus, Backoffice, Commerce extensions, hybris, CCv2, cart/checkout
  modifications, promotion rules, or any SAP Commerce technical concepts combined with
  research, planning, or implementation intent.
---

# Commerce RPI — Research, Plan, and (Optionally) Implement

You are driving the RPI (Research → Plan → Implement → Iterate) methodology for SAP Commerce
Cloud (CCv2) projects. Your user is a developer working in Claude Code's terminal CLI. They
are comfortable with code, builds, and terminal output. You provide the same layered research
discipline as the Cowork variant but with the **option to continue into implementation**.

The core principle: **layered research with compaction**. SAP Commerce repos are large and
context bloats fast. You break research into sequential layers, each running with fresh context,
writing findings to files, and carrying forward only summaries. This is the Ralph Loop applied
within phases — ruthless context management so you stay accurate deep into complex codebases.

## Three Stopping Points

Every phase boundary is a decision point. The developer chooses how far to go:

| Mode | Flow | When to Use |
|------|------|-------------|
| **Research only** | R → stop | Understanding a codebase area before deciding what to do |
| **Research + Plan** | R → P → stop | Producing a plan for someone else, a future session, or team review |
| **Full RPI** | R → P → I (→ iterate) | Research, plan, and implement in one flow |

After each phase, ask: **"Continue to [next phase], or stop here?"** Respect the answer.

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

On first invocation against a new repo, ask exactly **two questions**, then auto-detect
everything else. A developer should be able to say "research how our cart works" and be
doing useful work within a minute.

**Two questions:**
1. "Publish to Confluence/Jira, or work locally with `thoughts/` only?"
   - If Confluence/Jira: "Which space/project?" — list via `getConfluenceSpaces` / `getVisibleJiraProjects`
   - If local only: skip Atlassian setup entirely — this is a first-class workflow, not a degraded one
2. At each phase boundary: "Continue to [next phase], or stop here?"

**Auto-detect silently** (same as Cowork):
- CCv2 structure: look for `core-customize/manifest.json` + `core-customize/hybris/config/localextensions.xml`
- Commerce Suite version: parse `manifest.json` → `commerceSuiteVersion`
- Custom extensions: scan `core-customize/hybris/bin/custom/*/extensioninfo.xml`
- Extension dependencies: parse `<requires-extension>` from each `extensioninfo.xml`
- OOTB modules presence: check if `core-customize/hybris/bin/modules/` has content
  - If absent or empty → enable "limited research mode" (convention-based inference)
  - Read `references/research-layers.md` Section "Known Module Mappings" for fallback tables
- Storefront: check for `js-storefront/` at repo root
- Environment personas: parse `manifest.json` → `useConfig.properties` entries

Present a one-paragraph summary for confirmation:
> "I found a Commerce 2211.50 project with 2 custom extensions (coremcp, sampledatamcp),
> no storefront, and 3 environment personas. Ready to research."

Save config to `thoughts/shared/config/commerce-rpi-config.json`.

If `thoughts/shared/` already has artifacts from a previous session (Cowork or Claude Code),
offer: "Found existing research/plan artifacts. Pick up where you left off, or start fresh?"

## The Workflow

### Phase A: Research (Layered)

**Identical to the Cowork skill.** Break research into up to 6 sequential layers. Read
`references/research-layers.md` for the detailed prompts and scan targets for each layer.

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
  understanding? Any extensions missing?"
- **After Layer 5**: Present the full consolidated research with master assumptions list.
  Ask: "Are these assumptions correct? Anything missing?"

**Publishing (if Atlassian enabled):** Push the consolidated research to Confluence via
`createConfluencePage`. Save page ID to config.

**Research-only fork:** After presenting consolidated research, ask: "Continue to planning,
or is the research what you needed?" If research-only, publish and stop.

### Phase B: Plan (Interactive)

**Identical structure to the Cowork skill.** Read `references/plan-template.md` for the
full plan document structure.

**Step 1 — Context & Clarification**
- Read the consolidated research from `thoughts/shared/research/`
- Present understanding with 2-3 focused clarifying questions

**Step 2 — Structure Proposal**
- Propose phase breakdown (e.g., "Phase 1: Data Model, Phase 2: Service Layer...")
- Each phase: scope, files to modify, estimated complexity, dependencies
- Get approval before writing details

**Step 3 — Detailed Plan**
- Write to `thoughts/shared/plans/YYYY-MM-DD-feature-plan.md`
- Each phase has: scope/rationale, specific file changes (with paths), automated verification
  using `./gradlew` commands, manual verification, acceptance criteria grouped by category
- Final phase always includes a "Documentation" Story
- Read `assets/example-plan-story.md` for the exact format

**Step 4 — Review & Publish**
- Present the plan section by section, iterate until approved
- On approval, publish based on config:

| Target | When | What |
|--------|------|------|
| Git `thoughts/` | Always | Plan with checkboxes |
| Confluence | If Atlassian enabled | Formatted plan page linked to research page |
| Jira | If Atlassian enabled | Epic → Stories → Tasks hierarchy |

Read `references/jira-ticket-template.md` for Jira creation sequence and field mapping.
Read `references/confluence-publishing.md` for Confluence formatting and page ID saving.

**Decision point:** "Continue to implementation, or stop here?" If stopping, the plan is
complete — ready for this developer in a future session, another developer, or a Cowork
team lead to review.

### Phase C: Implement (Optional — Developer Chooses)

This phase only runs if the developer says "implement" at the Phase B decision point.
It can also be invoked directly if `thoughts/shared/plans/` already contains a plan from
a previous session or Cowork handoff.

**Picking up an existing plan:**
If the developer says "implement the plan" without having just run R+P, read the most
recent plan from `thoughts/shared/plans/` and the research from `thoughts/shared/research/`.
Present a summary and confirm before starting.

**Execution model:**
- Execute plan Tasks sequentially — order matters for dependencies
- Each Task runs as a subagent with fresh context (Ralph Loop for implementation)
- The subagent reads: the Task description from the plan, the relevant research layer
  summaries, and any outputs from previous Tasks

**For each Task:**
1. Read the Task's AC and verification steps from the plan
2. Implement the changes
3. Run verification:
   - After `*-items.xml` changes: `./gradlew ybuild stopServer startServer yupdatesystem`
   - After `*-beans.xml` changes: `./gradlew ybuild stopServer startServer`
   - After Java source changes: `./gradlew ybuild stopServer startServer`
   - After ImpEx changes: `./gradlew impex -Pfile=<path>`
   - Unit tests: `./gradlew yunittests -Dtestclasses.extensions=<ext>`
4. If verification fails: diagnose, fix, re-verify — do NOT skip or proceed
5. Update `thoughts/shared/plans/` — check off the completed Task
6. If Jira integration is active, transition the ticket

**Final Task: Documentation**
- Create flow documentation (`context.md`, `components.md`, `diagram.md`) by distilling
  the research findings — this is always the last Task in the plan

**What the implement phase does NOT do:**
- Push to remote (developer decides when)
- Deploy to any environment
- Modify OOTB platform or module code
- Skip verification steps even if the build is slow
- Proceed to next Task if current Task's verification fails

### Phase D: Iterate

Iteration works through **two channels** plus **in-session feedback**.

**Channel 1: Jira `[RPI-ITERATE]` tags** (if Atlassian enabled)
Search via `searchJiraIssuesUsingJql` on the Epic's tickets.

**Channel 2: Confluence review comments** (if Atlassian enabled)
Read inline and footer comments on published pages via `getConfluencePageInlineComments`
and `getConfluencePageFooterComments`. Group by theme, distinguish change requests from
acknowledgments.

**Channel 3: In-session feedback** (Claude Code-specific)
The developer says directly: "The plan assumed X but actually Y — revise." This is faster
than Jira tags but less auditable. For team workflows, still post `[RPI-ITERATE]` comments
to Jira automatically when integration is active.

**Processing feedback from any channel:**
- Present all flagged issues grouped by source
- Propose targeted plan revisions
- Re-run only the affected research layers (not all of them)
- Republish to `thoughts/`, and if Atlassian enabled: Confluence (page revision via
  `updateConfluencePage`) and Jira (updated descriptions/AC)
- If currently implementing, continue from the revised plan

What does NOT require iteration: minor implementation details, build failures from typos,
scope additions (those are new Epics).

## SAP Commerce Patterns

These are essential for accurate research and correct implementation. Read the full
reference files when running each research layer or implementing changes.

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

**Build Commands** — Always use `./gradlew`, never raw ant:
- `./gradlew yclean yall` — full clean build
- `./gradlew ybuild` — incremental build
- `./gradlew yupdatesystem` — apply type system changes (after items.xml edits)
- `./gradlew yunittests -Dtestclasses.extensions=<ext>` — run tests
- `./gradlew yintegrationtests -Dtestclasses.extensions=<ext>` — integration tests
- `./gradlew impex -Pfile=<path>` — import ImpEx
- `./gradlew flexquery -Pfile=<path>` — run FlexibleSearch queries
- `./gradlew groovy -Pfile=<path> [-Pcommit=true]` — execute Groovy scripts
- `./gradlew stopServer startServer` — restart without rebuild

**Critical Rules (from CLAUDE.md):**
1. Never modify `gensrc/` — auto-generated, overwritten on build
2. Never modify platform or modules — override in custom extensions
3. Use the alias pattern: define `defaultMyBean`, alias to `myBean`
4. Define interfaces for services, facades, DAOs — implementations in `impl/` with `Default*` prefix
5. DTOs are generated from `*-beans.xml` — never hand-write them
6. Register new extensions in `localextensions.xml` before building

## Documentation Convention

`thoughts/` files are working artifacts — timestamped, layered, consumed during R&P&I,
archived after implementation. Flow docs (`context.md`, `components.md`, `diagram.md`)
are permanent documentation created as a final implementation Task by distilling the
research. No duplication between the two — `thoughts/` drives the process, flow docs
are the permanent record.

## Interoperability with Cowork

Both skills read and write to the same `thoughts/shared/` directory. A team lead can
research in Cowork, and a developer can implement in Claude Code from the same artifacts.
A developer can research and plan in Claude Code, and a team lead can review and iterate
in Cowork via Confluence.

The `thoughts/shared/config/commerce-rpi-config.json` file is shared — either skill can
read the other's config (Confluence page IDs, Jira Epic key, etc.).

## What This Skill Does NOT Do

- Modify OOTB platform or module code
- Deploy to any environment
- Push to remote without developer consent
- Skip the research phase and jump straight to implementation
- Skip verification steps during implementation
- Proceed past unresolved assumptions without developer approval
- Force implementation — the developer always has the option to stop after research or plan
