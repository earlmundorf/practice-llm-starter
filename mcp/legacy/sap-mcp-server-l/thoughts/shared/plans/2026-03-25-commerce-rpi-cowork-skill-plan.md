# Skill Build Plan: `commerce-rpi-cowork`

**RPI for SAP Commerce — No Terminal Required**

---

## 1. What This Skill Does

A Cowork-native skill that enables team leads (functional or technical) to drive the Research and Plan phases of the RPI methodology through conversation, against a mounted SAP Commerce CCv2 git repo, producing reviewable artifacts in Confluence, Jira, and the repo's `thoughts/` directory. Implementation is then picked up by developers in Claude Code.

**The user never touches a terminal.** They describe the feature or change they need, review research findings, iterate on the plan, and approve the final deliverables — all through Cowork's chat UI. The skill handles codebase analysis, artifact generation, and publishing to their team's systems.

---

## 2. Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Primary persona | Team leads (functional or technical) | They have the domain knowledge for R&P; devs pick up implementation |
| Research strategy | Layered sub-researches with compaction | SAP Commerce repos are large; single-pass research causes context bloat |
| Output targets | Confluence + Jira + Git `thoughts/` | Functional reviewers live in Confluence/Jira; devs need `thoughts/` for Claude Code |
| Context management | Fresh context per research layer, write-then-compact | Apply Ralph Loop principle within phases, not just between them |
| Review checkpoints | Two: Layer 0 (extension map) + Layer 5 (consolidated) | Six per-layer reviews cause fatigue; two catches errors at the foundation and the summary |
| Commerce awareness | Integrate with project-specific skills if available | Reuse team contacts, initiative context, dependency tracking from any co-installed Commerce skill |
| Jira hierarchy | Epic → Stories → Tasks | Stories are the broader "what"; Tasks under Stories are the "how" with specific implementation steps |
| Acceptance criteria | Single list, grouped by category | Categories like "Data Model", "Service Layer", "Storefront Behavior", "Integration" — at both Story and Task level |
| Assumptions | Captured at every level | Epic, Story, and Task all surface assumptions explicitly — this is where misunderstandings get caught |
| CCv2 repo layout | `core-customize/` is the build root | All hybris paths are relative to `core-customize/hybris/`; `manifest.json` at `core-customize/` controls personas and build config |
| Codebase scope | `core-customize/hybris/bin/custom/`, `js-storefront/` (if present), `core-customize/hybris/config/`, `localextensions.xml` | Custom extensions, optional Spartacus storefront, Hybris config, and the extension manifest |
| OOTB reference | `core-customize/hybris/bin/modules/`, `core-customize/hybris/bin/platform/ext/` | Read-only reference to understand what custom code is overriding; modules + platform/ext are downloaded by CCv2, never in the repo |
| OOTB degradation | Three modes: Full / Hybrid / Limited based on available source | CCv2 repos often lack OOTB source in Git; convention-based inference ensures research works regardless |
| Build system | Gradle wrapper (`./gradlew`) wrapping ant | Verification steps use `./gradlew yclean yall`, `./gradlew ybuild`, `./gradlew yupdatesystem`, not raw ant |
| Storefront layer | Conditional — only runs if `js-storefront/` exists | Not all Commerce projects have a Spartacus frontend; headless/MCP projects skip this layer |
| Iterate phase | Lightweight targeted revision via `[RPI-ITERATE]` Jira tags | Without iteration, R→P→I is one-way and fragile; targeted layer re-runs respect the team lead's time while closing the feedback loop |
| Documentation convention | `thoughts/` = working drafts; flow docs = final product | Research and plan artifacts in `thoughts/` drive R&P&I; flow docs (`context.md`, `components.md`, `diagram.md`) are created as a final implementation Task, distilled from research — no duplication |

---

## 3. Skill Architecture

```
commerce-rpi-cowork/
├── SKILL.md                         # Main skill (< 500 lines)
├── references/
│   ├── research-layers.md           # SAP Commerce layer definitions + prompts
│   ├── ccv2-layout.md               # CCv2 repo structure (core-customize/, manifest.json, personas)
│   ├── hybris-layout.md             # Standard hybris/ directory structure within core-customize/
│   ├── spartacus-layout.md          # Spartacus/Composable Storefront structure (sourced from SAP help/blogs/community)
│   ├── impex-patterns.md            # ImpEx naming conventions, ordering rules, macro patterns
│   ├── plan-template.md             # Plan document template with phase structure
│   ├── confluence-publishing.md     # How to format and push to Confluence
│   └── jira-ticket-template.md      # Jira Epic/Story/Task templates with grouped AC format
├── scripts/
│   └── parse_plan_to_tickets.py     # Extracts phases from plan → structured ticket data
└── assets/
    ├── research-summary-template.md  # Template for consolidated research output
    ├── example-research-layer1.md    # Concrete Layer 1 output example (from real project)
    ├── example-plan-story.md         # Concrete plan Story + Tasks example
    └── jira-field-mapping.md         # Exact Jira API field mapping for Epic/Story/Task creation
```

**Note on reference files:**

The `ccv2-layout.md` captures the CCv2-specific wrapper: `core-customize/` as the build root, `manifest.json` with persona-based properties (dev/stg/prod), and the fact that platform and modules are downloaded by the cloud build — not in the repo.

The `impex-patterns.md` captures ImpEx conventions validated against the real project: files live directly in `resources/impex/` (not in subdirectories), with naming conventions that control load behavior and ordering: `essentialdata-*.impex` (loaded on init AND update, alphabetical sort determines order) and `projectdata-*.impex` (loaded on init ONLY). Common macros like `$productCatalog`, `$currency`, `$onlineVersion`, `$stagedVersion` are documented.

`spartacus-layout.md` captures Spartacus/Composable Storefront conventions sourced from SAP's public documentation — help.sap.com (official Composable Storefront dev guide), SAP Community blogs, and SAP developer tutorials. This includes standard directory structure (`src/app/`, feature modules, CMS component mappings), routing conventions, lazy-loading patterns, and CMS-driven page/slot/component hierarchy. The skill populates this reference during build by fetching current Spartacus docs from SAP's public sites. At runtime, Layer 3 only activates if `js-storefront/` is detected during setup — otherwise it's skipped for headless/API-only projects.

---

## 4. The Four Phases

### Phase A: Research (Layered, with Compaction)

The skill breaks SAP Commerce research into up to six sequential layers (Layer 0-5). Layer 3 (Storefront) is conditional. Each layer runs as a subagent with a fresh context, writes its findings to a markdown file, and only the summary is carried forward.

**Layer 0 — Extension Manifest & Dependency Map (Pre-Research)**
- Parses: `core-customize/hybris/config/localextensions.xml` to build the complete list of active extensions
- Notes the `<path>` directives that control extension discovery:
  - `${HYBRIS_BIN_DIR}/platform/ext` — platform core extensions
  - `${HYBRIS_BIN_DIR}/modules` — OOTB feature modules
  - `${HYBRIS_BIN_DIR}/custom` — our custom extensions
- Categorizes each `<extension>` entry into three tiers:
  - **Custom** (`core-customize/hybris/bin/custom/`) — our code, the primary modification target
  - **OOTB modules** (`core-customize/hybris/bin/modules/`) — SAP-provided feature modules (commerceservices, basecommerce, solrfacetsearch, promotionengineservices, etc.)
  - **Platform core** (`core-customize/hybris/bin/platform/ext/`) — core extensions (core, processing, hac, etc.)
- For custom extensions, reads each `extensioninfo.xml` to map `<requires-extension>` dependencies (e.g., `coremcp` requires `commercewebservices`; `sampledatamcp` requires `commercewebservices`, `solrfacetsearch`, `promotionengineservices`, `couponservices`)
- Reads `core-customize/manifest.json` to identify Commerce Suite version and persona-based properties files (dev/stg/prod)
- Produces: Extension dependency graph showing the full chain: custom → OOTB modules → platform core
- This layer is fast and lightweight — it's reading XML manifests, not source code
- Output: `thoughts/shared/research/YYYY-MM-DD-feature-extension-map.md`

This map is the foundation for every subsequent layer. When Layer 1 finds a custom type in `coremcp/resources/coremcp-items.xml`, the extension map tells us that `coremcp` depends on `commercewebservices` → `commerceservices` → `basecommerce`, so we know the full inheritance chain.

**Layer 1 — Type System & Data Model**
- Reads: Layer 0 extension map
- Scans: `core-customize/hybris/bin/custom/**/resources/*-items.xml` for custom types, enums, relations
- Cross-references: Relevant OOTB items.xml in `core-customize/hybris/bin/modules/` to understand what's being extended (e.g., reads `commerceservices-items.xml` to understand the base type before looking at the custom override). **In limited research mode** (OOTB source absent), infers base types from extension dependency names and known SAP Commerce conventions (see Section 7a).
- Notes: Some extensions may have empty items.xml (e.g., `coremcp` in Phase 1 has no custom types — uses in-memory storage). This is a valid finding, not an error.
- Documents: custom types, extended OOTB types, enum values, relation definitions, and explicitly notes "extends OOTB type X from module Y"
- Assumptions surfaced: "We assume X type doesn't already exist", "We assume this relation is 1:many"
- Output: `thoughts/shared/research/YYYY-MM-DD-feature-type-system.md`

**Layer 2 — Service Layer & Business Logic**
- Reads: Layer 0 extension map + Layer 1 summary (not raw output)
- Scans: `core-customize/hybris/bin/custom/**/resources/*-spring.xml` for bean definitions, `core-customize/hybris/bin/custom/**/src/` for service implementations
- Identifies SAP Commerce patterns: `alias` → `default*` bean naming, interface + `impl/Default*` convention, property injection via `<property>` refs to OOTB facades/services
- Cross-references: OOTB Spring configs and service interfaces in `core-customize/hybris/bin/modules/` to identify which beans are being overridden. **In limited research mode**, infers overridden beans from `<alias>` names in custom Spring XML (see Section 7a).
- Also checks: `core-customize/hybris/config/local.properties` (and persona variants: `local-dev.properties`, `local-stg.properties`, `local-prod.properties`) for property overrides, feature flags, API keys
- Documents: overridden OOTB services, custom strategies, interceptor chains, promotion rule engines — always noting "overrides DefaultXxxService from module Y"
- Assumptions surfaced: "We assume DefaultCalculationService is not already overridden", etc.
- Output: `thoughts/shared/research/YYYY-MM-DD-feature-service-layer.md`

**Layer 3 — Storefront & Integration (CONDITIONAL)**

**This layer only runs if the project has a storefront.** During first-run config, the skill checks for `js-storefront/` (Spartacus/Angular) at the repo root. If no storefront exists (e.g., headless MCP server projects), this layer is skipped and Layer 4 reads Layer 2's summary directly.

When present:
- Reads: Layer 0 extension map + Layer 1 + 2 summaries
- Scans: `js-storefront/` for Spartacus/Angular components, modules, routing, CMS component mappings; `core-customize/hybris/bin/custom/**/web/` for OCC API extensions and controllers
- Cross-references: OOTB OCC controllers and CMS component types in `core-customize/hybris/bin/modules/` to understand what's being extended vs. net-new
- Documents: page/component structure, API contracts, cart/checkout flow modifications, CMS slot/component hierarchy
- Assumptions surfaced: "We assume the checkout flow uses OOTB steps", "We assume no custom Angular module exists for X"
- Output: `thoughts/shared/research/YYYY-MM-DD-feature-storefront.md`

When absent:
- Skipped entirely. Layer 4 reads Layers 0-2 summaries.
- If OCC controllers exist in `core-customize/hybris/bin/custom/**/web/`, they are documented in Layer 2 as API endpoints (not storefront components).

**Layer 4 — ImpEx & Data Configuration**

ImpEx is where a huge amount of SAP Commerce work lives — store infrastructure, product catalog, Solr indexing, promotions, CMS setup, customer groups, and more. For functional team leads, this is often the most important research layer.

- Reads: Layer 0 extension map + Layer 1 (type system) + Layer 2 (services) summaries
- Scans: `core-customize/hybris/bin/custom/**/resources/impex/` — the standard location within each extension
- ImpEx files follow a **naming convention** (not subdirectories) that controls load behavior:
  - `essentialdata-*.impex` — loaded on BOTH `./gradlew yinitialize` AND `./gradlew yupdatesystem`. Alphabetical filename ordering determines execution sequence (e.g., `essentialdata-infrastructure.impex` sorts before `essentialdata-solr.impex`)
  - `projectdata-*.impex` — loaded ONLY on `./gradlew yinitialize` (full data reset). Not loaded on update.
- Also checks: `core-customize/hybris/config/` for any global ImpEx files (uncommon but possible — not present in every project)
- Cross-references: OOTB ImpEx in `core-customize/hybris/bin/modules/**/resources/impex/` to understand baseline data
- Documents:
  - Macro conventions (e.g., `$productCatalog`, `$currency`, `$onlineVersion`, `$stagedVersion`, `$warehouse`)
  - Store infrastructure: OAuth clients, currencies, catalogs, base store/site, delivery modes, payment modes
  - Product catalog: products, pricing (PriceRow), stock levels, catalog versioning (Staged + Online)
  - Solr configuration: SolrFacetSearchConfig, indexed types, properties, value ranges, sorts, search query templates
  - Customer data: customers, addresses, user groups
  - Order data: orders, order entries, calculation triggers
  - Promotion setup: PromotionGroup, promotion rules (if configured via ImpEx)
  - ImpEx ordering dependencies (e.g., infrastructure must exist before Solr can reference catalog)
  - Embedded Groovy/BeanShell scripts within ImpEx (e.g., `"#% impex.getLastImportedItem().calculate();"`)
- Flags: any ImpEx that references types from Layer 1 that will need to change
- Assumptions surfaced: "We assume CMS components for X page already exist", "We assume Solr indexed type Y includes field Z"
- Output: `thoughts/shared/research/YYYY-MM-DD-feature-impex-data.md`

**Layer 5 — Consolidated Research**
- Reads: All previous layer summaries (Layer 0: extension map, Layer 1: type system, Layer 2: service layer, Layer 3: storefront if present, Layer 4: ImpEx/data)
- Produces: Single consolidated research document with:
  - Cross-cutting concerns and dependency map
  - Risk flags
  - **Master assumptions list** gathered from all layers
  - **OOTB extension dependency summary** — which modules we touch, what version constraints exist
  - **ImpEx change impact summary** — which existing data configurations are affected, ordering constraints for new ImpEx
  - **Environment differences** — any dev/stg/prod property differences that affect this feature
- The assumptions list is critical — this is what the team lead reviews most carefully
- Output: `thoughts/shared/research/YYYY-MM-DD-feature-consolidated.md`

**Review Checkpoints (Two, Not Six):**

Six layer-by-layer reviews create fatigue. By Layer 3, the team lead is saying "yes fine" without reading. Instead, the skill uses two high-leverage checkpoints:

1. **After Layer 0 (Extension Map)** — This is the foundation. If the extension map is wrong, everything downstream is wrong. The skill presents the extension dependency graph and asks: "Does this match your understanding of the project? Any extensions missing or miscategorized?" This catches repo-level misunderstandings before any source reading begins.

2. **After Layer 5 (Consolidated)** — After all research is complete, the skill presents the full consolidated findings with the master assumptions list. This is the thorough review: "Here's what I found across the entire codebase. Are these assumptions correct? Anything missing?" The team lead reviews one comprehensive document instead of five incremental ones.

Layers 1-4 run without interruption. Their individual outputs are still written to `thoughts/shared/research/` and are available if the team lead wants to drill into a specific layer, but the skill doesn't stop to ask for approval between them.

**Publishing:** The consolidated research doc is pushed to Confluence as a page under a configurable space/parent.

**Research-Only Fork:** After presenting the consolidated research, the skill asks: "Do you want to proceed to planning, or is the research what you needed?" Some of the most valuable uses of this skill will be pure research — architecture reviews, incident investigation, onboarding a new team member. If the team lead chooses research-only, the skill publishes to Confluence and `thoughts/` and stops. No plan, no Jira tickets. This is a first-class workflow, not an edge case.

---

### Phase B: Plan (Interactive, with Jira + Confluence Output)

The plan phase reads the consolidated research and works interactively with the team lead.

**Step 1 — Context & Clarification**
- Reads consolidated research
- If a project-specific skill is co-installed, cross-references initiative dependencies and critical dates
- Presents understanding with 2-3 focused clarifying questions
- Stops on any unresolved ambiguity — does NOT proceed with assumptions

**Step 2 — Plan Structure Proposal**
- Proposes phase breakdown (e.g., "Phase 1: Data Model, Phase 2: Service Layer, Phase 3: ImpEx Configuration, Phase 4: Integration Tests")
- Each phase includes: scope description, specific files to modify, estimated complexity, dependencies on prior phases
- Gets team lead approval on structure before writing details

**Step 3 — Detailed Plan with Success Criteria**
- Writes full plan to `thoughts/shared/plans/YYYY-MM-DD-feature-plan.md`
- Each phase has:
  - Scope and rationale
  - Specific file changes (with paths from research)
  - Automated verification: `./gradlew yclean yall` (build), `./gradlew yunittests -Dtestclasses.extensions=<ext>` (tests), `./gradlew impex -Pfile=<path>` (ImpEx import), `./gradlew yupdatesystem` (after items.xml changes)
  - Manual verification (Backoffice checks, OCC API tests, storefront behavior if applicable)
  - Acceptance criteria grouped by category, written in team lead language
- **Final phase always includes:** "Create flow documentation" — a Task to distill the research into the project's `context.md`, `components.md`, `diagram.md` convention (see Documentation Convention below)

**Step 4 — Review & Iteration**
- Presents the plan to the team lead section by section
- Iterates until approved
- On approval, publishes to all three targets:

**Output Targets on Approval:**

| Target | What Gets Created |
|--------|-------------------|
| **Git `thoughts/`** | Plan markdown with checkboxes — this is what Claude Code reads during `/implement_plan` |
| **Confluence** | Formatted plan page linked to the research page, with table of phases, acceptance criteria, and assumptions |
| **Jira** | Full hierarchy — see below |

**Jira Ticket Hierarchy:**

```
Epic: "Gift Card as Payment Method"
  ├── Assumptions (in Epic description)
  │
  ├── Story: "Gift Card Type & Data Model"
  │   ├── Assumptions (Story-level)
  │   ├── Acceptance Criteria (grouped by category)
  │   │   ├── Data Model: "GiftCardPaymentInfo type extends PaymentInfoModel with..."
  │   │   ├── Service Layer: "PaymentService supports gift card balance check and redemption..."
  │   │   └── Integration: "ImpEx imports seed data for gift card configuration..."
  │   │
  │   ├── Task: "Create GiftCardPaymentInfo in items.xml with attributes"
  │   │   ├── Assumptions
  │   │   └── AC (grouped): Data Model validation, ./gradlew yclean yall passes
  │   ├── Task: "Implement GiftCardPaymentInfoPopulator and register in Spring"
  │   │   └── AC (grouped): Service Layer tests pass, converter chain works
  │   └── Task: "Create ImpEx for gift card payment mode configuration"
  │       └── AC (grouped): Import succeeds, Backoffice shows entries
  │
  ├── Story: "Gift Card Balance & Redemption Service"
  │   ├── Assumptions (Story-level)
  │   ├── Acceptance Criteria (grouped by category)
  │   │   ├── Service Layer: "Balance check returns remaining value, partial redemption supported"
  │   │   ├── ImpEx/Data Config: "Gift card payment mode registered in base store"
  │   │   └── Build/Deploy: "Unit tests pass on ./gradlew yunittests with no errors"
  │   │
  │   ├── Task: "Create GiftCardService interface and DefaultGiftCardService implementation"
  │   │   ├── Assumptions: "External gift card API is REST-based with balance endpoint"
  │   │   └── AC (grouped): Service Layer — balance check + redemption work; Integration — API mock passes
  │   ├── Task: "Create essentialdata-giftcard-paymentmode.impex for payment mode setup"
  │   │   └── AC (grouped): ImpEx/Data Config — payment mode appears in Backoffice; Build/Deploy — imports on yupdatesystem
  │   └── Task: "Create projectdata-giftcard-sampledata.impex for test cards"
  │       └── AC (grouped): ImpEx/Data Config — sample gift cards with balances for testing
  │
  ├── Story: "Gift Card Checkout Integration"
  │   ├── Tasks...
  │
  ├── Story: "Gift Card Order History & Refund Handling"
  │   └── Tasks...
  │
  └── Story: "Gift Card Feature Documentation"
      ├── Task: "Create docs/gift-card/context.md from research findings"
      ├── Task: "Create docs/gift-card/components.md from plan file list"
      └── Task: "Create docs/gift-card/diagram.md with Mermaid flow diagrams"
```

Each **Story** represents a broad capability (the "what") with its own assumptions and categorized acceptance criteria. Each **Task** under a Story is a specific implementation chunk (the "how") — small enough for a developer to pick up in a sprint, with its own AC and assumptions. Stories are linked sequentially where dependencies exist. The Confluence plan page is linked as a remote link on the Epic.

Note: ImpEx Task names use actual file naming conventions (e.g., `essentialdata-giftcard-paymentmode.impex`) so developers know exactly what to create and where load ordering will place it.

---

### Phase C: Handoff to Implementation

The skill does NOT implement code. It produces a clean handoff:

1. `thoughts/shared/plans/` contains the plan with checkboxes
2. `thoughts/shared/research/` contains all research documents
3. Confluence has reviewable versions of both
4. Jira has the ticket hierarchy ready for sprint planning

A developer opens the repo in Claude Code, runs `/implement_plan`, and the agent picks up exactly where the team lead left off. The plan was already validated by someone who understands the business requirements. The developer's job is execution and verification.

---

### Phase D: Iterate (Lightweight Feedback Loop)

**RPI has four phases, not three.** Without an iterate phase, the R→P→I pipeline is one-way and fragile. When implementation reveals that the plan was wrong — an assumption was invalid, a type already exists, an ImpEx ordering dependency was missed — there needs to be a path back.

**How it works:**

1. **Developer flags an issue** — During implementation in Claude Code, the developer encounters a problem. They add a Jira comment to the affected Task or Story with a structured tag: `[RPI-ITERATE] Assumption X was wrong because...` or `[RPI-ITERATE] Missing requirement: need Y before Z`.
2. **Team lead re-enters Cowork** — The team lead opens a new Cowork session with the skill. The skill detects existing `thoughts/` artifacts and offers: "Continue from existing research?" or "Start fresh?"
3. **Skill loads context** — Reads the existing plan from `thoughts/shared/plans/`, the consolidated research from `thoughts/shared/research/`, and searches Jira for `[RPI-ITERATE]` comments on tickets in the Epic.
4. **Targeted revision** — The skill presents the flagged issues and proposes specific plan revisions. It does NOT re-run all research layers — only the layers affected by the flagged issue. For example, if a type system assumption was wrong, only Layer 1 and Layer 5 (consolidated) are re-run.
5. **Republish** — Updated plan sections are pushed to `thoughts/`, Confluence (as a page revision, not a new page), and Jira (updated Task descriptions/AC). The Confluence page gets a "Revision History" section at the bottom tracking what changed and why.

**What triggers an iterate cycle:**
- Jira comments tagged `[RPI-ITERATE]` on any ticket in the Epic
- Developer explicitly asking the team lead to revisit the plan
- Team lead noticing during sprint review that implementation diverged from plan

**What does NOT require iteration:**
- Minor implementation details (variable naming, exact method signatures) — those are developer decisions
- Build failures from typos or syntax — that's debugging, not plan revision
- Scope additions — those are new Epics/Stories, not revisions to the existing plan

This keeps the iterate phase lightweight. It's not a full re-planning cycle — it's a targeted revision mechanism that respects the team lead's time while closing the feedback loop that makes RPI work in practice.

---

## 5. Documentation Convention: `thoughts/` vs Flow Docs

The project has an existing documentation convention (from CLAUDE.md):

```
docs/feature-name/
├── context.md      # What the flow does, when it's used, key decisions
├── components.md   # The files that implement it and what each one does
└── diagram.md      # Mermaid diagrams with descriptive context
```

RPI introduces a parallel artifact structure:

```
thoughts/shared/
├── research/       # Working research documents (timestamped, layered)
└── plans/          # Implementation plans (with checkboxes)
```

**These overlap but serve different purposes.** The solution: no duplication.

| Artifact | Lifecycle | Audience | Location |
|----------|-----------|----------|----------|
| Research docs | Created during R phase, consumed during P&I, archived after | Team lead + developer during R&P&I | `thoughts/shared/research/` |
| Plan docs | Created during P phase, consumed during I, archived after | Team lead + developer during P&I | `thoughts/shared/plans/` |
| Flow docs | Created as final implementation Task, permanent | Anyone working on the feature later | `docs/feature-name/` or `extension/docs/` |

**The rule:** `thoughts/` files are working artifacts that drive the process. Flow docs are the permanent documentation created by distilling research findings after implementation is complete. The plan always includes a final "Documentation" Story with Tasks to create `context.md`, `components.md`, and `diagram.md` from the research.

After implementation, `thoughts/` files can be archived or deleted. The flow docs remain as the source of truth for "how does this feature work."

---

## 6. Integration Points

### Atlassian MCP (Already Available)
- `createConfluencePage` — Publish research and plan docs
- `createJiraIssue` — Create Epics, Stories, and Tasks from plan phases
- `createIssueLink` — Link sequential Stories as dependencies
- `searchJiraIssuesUsingJql` — Check for existing tickets to avoid duplicates; search for `[RPI-ITERATE]` comments during iterate phase
- `addCommentToJiraIssue` — Post revision notes during iterate phase
- `getConfluenceSpaces` — Let user pick target space on first run
- `updateConfluencePage` — Update existing research/plan pages during iterate phase (revision, not new page)

### Project-Specific Skills (Optional)
- If a project-specific skill is co-installed (e.g., a PI planning skill, initiative tracker, or team roster), the RPI skill cross-references its data during the Plan phase
- Flags research findings that conflict with known constraints from the project skill
- Pulls critical dates or dependency info into plan timelines
- Treated as advisory, not authoritative — the actual codebase always wins

### Git Repository (Mounted Workspace)
- Read source code for research layers
- Write `thoughts/` artifacts for Claude Code consumption
- Optionally commit research/plan docs to a branch

---

## 7. Configuration (First-Run Setup)

**Principle: Useful on first message, customizable over time.** The skill auto-detects everything it can and asks only two questions before doing useful work. A team lead who just mounted their repo should be able to say "research how our cart works" immediately — not answer eight setup questions first.

### Two Required Questions

1. **Confluence space** — "Which Confluence space should I publish to?" (lists available spaces via `getConfluenceSpaces`)
2. **Jira project** — "Which Jira project for tickets?" (lists visible projects via `getVisibleJiraProjects`)

### Everything Else Is Auto-Detected

The skill silently detects and configures on first run:

| What | How | Fallback |
|------|-----|----------|
| CCv2 repo structure | Detect `core-customize/manifest.json` + `localextensions.xml` | Error: "This doesn't look like a CCv2 Commerce repo" |
| Commerce Suite version | Parse `manifest.json` → `commerceSuiteVersion` | Warn but continue |
| Custom extensions | Scan `core-customize/hybris/bin/custom/*/extensioninfo.xml` | Error: "No custom extensions found" |
| Extension dependencies | Parse `<requires-extension>` from each `extensioninfo.xml` | Proceed with no dependency info |
| OOTB modules presence | Check if `core-customize/hybris/bin/modules/` has content | Enable "limited research mode" (see Section 7a) |
| Storefront presence | Check for `js-storefront/` at repo root | Skip Layer 3, mark as headless/API-only |
| Environment personas | Parse `manifest.json` → `useConfig.properties` for persona files | Assume single properties file |
| Jira issue type hierarchy | Detect Epic → Story → Task via `getJiraProjectIssueTypesMetadata` | Default to standard hierarchy |
| AC categories | Default set: Data Model, Service Layer, ImpEx/Data Configuration, Integration, Build/Deploy; add Storefront Behavior only if storefront detected | Defaults always work |

On completion, the skill presents a one-paragraph summary: "I found a Commerce 2211.50 project with 2 custom extensions (coremcp, sampledatamcp), no storefront, and 3 environment personas. Ready to research." The team lead confirms or corrects.

### 7a. Graceful Degradation: Missing OOTB Source

CCv2 repos often don't include OOTB platform and module source in Git — they're downloaded during `./gradlew bootstrapPlatform`. If someone mounts a repo that only has `core-customize/hybris/bin/custom/` and `core-customize/hybris/config/`, the research layers that cross-reference OOTB code need to degrade gracefully rather than fail silently.

**Detection:** During auto-detect, the skill checks whether `core-customize/hybris/bin/modules/` exists and has content. Three states:

| State | Indicator | Research Mode |
|-------|-----------|---------------|
| **Full source available** | `modules/` has subdirectories with `*.java`, `*-items.xml`, `*-spring.xml` | Full research — cross-reference OOTB source directly |
| **Partial source** | `modules/` exists but is sparse (some dirs, missing source) | Hybrid — use available source + convention inference |
| **No OOTB source** | `modules/` empty or absent | Limited research mode — convention-based inference only |

**Limited Research Mode** uses these fallbacks:

- **Type inheritance:** Infer from `extensioninfo.xml` `<requires-extension>` names. If an extension requires `commerceservices`, we know it has access to `CartModel`, `OrderModel`, `ProductModel`, etc. based on known SAP Commerce module conventions.
- **Bean overrides:** Infer from `<alias>` names in custom Spring XML. If custom code aliases `cartService`, we know it's overriding the OOTB `DefaultCartService` from `commerceservices` module — even without reading the OOTB source.
- **ImpEx types:** Infer from type names used in ImpEx `INSERT_UPDATE` statements. Types like `SolrFacetSearchConfig`, `BaseStore`, `CMSSite` are standard OOTB types with well-known attributes.
- **Known module mappings:** A reference table in `references/research-layers.md` maps common extension names to their key types, services, and beans (e.g., `promotionengineservices` → `PromotionGroup`, `AbstractPromotionAction`, `RuleBasedPromotionService`).

**The skill always reports its research mode** in Layer 0 output: "Research mode: Full / Limited (OOTB source not available — using convention-based inference)." This lets the team lead and downstream developers know what level of confidence to place on OOTB cross-references.

### 7b. Concrete Output Examples

These examples are the single highest-leverage addition for reducing variance in LLM output quality. Each one is derived from the real `sap-mcp-server-g` project.

**Example 1: Layer 1 Research Output (Type System)**

This is what a Layer 1 document looks like for the `coremcp` extension:

```markdown
# Type System Research: Cart & Checkout Capabilities
Date: 2026-03-25 | Extension: coremcp | Research Mode: Full

## Custom Types
**None.** `coremcp-items.xml` is empty — the extension currently uses in-memory
storage for MCP session state. No custom types, enums, or relations are defined.

## OOTB Types in Use (via dependency chain)
coremcp → commercewebservices → commerceservices → basecommerce

Key types accessed through the service layer (from coremcp-spring.xml bean refs):
- **CartModel** (basecommerce) — via `cartFacade` / `cartService` beans
- **OrderModel** (basecommerce) — via `orderFacade` beans
- **ProductModel** (catalog) — via `productFacade` / `productSearchFacade`
- **CustomerModel** (core) — via `customerFacade`

## Assumptions
- A: Any new cart entry type will require additions to coremcp-items.xml
  (currently empty) and a subsequent `./gradlew ybuild yupdatesystem`
- B: The existing CartModel from basecommerce is sufficient as a base —
  no evidence of custom cart model extensions elsewhere in the project
- C: coremcp depends on commercewebservices, so OCC cart/checkout
  controllers are available without additional extension registration
```

**Example 2: Plan Section (Single Story with Tasks)**

```markdown
## Story: Gift Card Type & Data Model

**Assumptions:**
- PaymentInfoModel from basecommerce is not already extended by another custom extension
- Gift card balances are managed by an external API, not stored in Commerce
- Partial redemption is supported (gift card can cover part of the order total)

**Acceptance Criteria:**
| Category | Criterion |
|----------|-----------|
| Data Model | GiftCardPaymentInfo type exists in items.xml extending PaymentInfoModel |
| Data Model | Gift card attributes (cardNumber, balance, expirationDate) are defined |
| Service Layer | PaymentService supports gift card balance check and redemption |
| Build/Deploy | `./gradlew yclean yall` passes with no compilation errors |
| Build/Deploy | `./gradlew yupdatesystem` applies the new type without data loss |

### Tasks:
1. **Create GiftCardPaymentInfo in items.xml**
   - AC: Type definition compiles, extends PaymentInfoModel, attributes match spec
   - Verification: `./gradlew yclean yall` succeeds
2. **Create GiftCardPaymentInfoPopulator and register in Spring**
   - AC: Populator follows `alias`/`default*` pattern, converter chain includes new populator
   - Verification: `./gradlew yunittests -Dtestclasses.extensions=<ext>` passes
3. **Create essentialdata-giftcard-paymentmode.impex for payment mode setup**
   - AC: ImpEx imports cleanly on `./gradlew yinitialize`
   - Note: Filename sorts after essentialdata-infrastructure ('g' > 'i' is false, 'p' > 'i') — verify ordering
```

**Example 3: Jira Field Mapping**

This defines exactly which Jira API fields the skill populates when creating tickets:

```
Epic:
  summary       → Epic title (e.g., "Gift Card as Payment Method")
  description   → Markdown: ## Overview + ## Assumptions (bulleted list)
  issuetype     → { name: "Epic" }
  project       → { key: from config }
  labels        → ["rpi-generated", "commerce"]

Story:
  summary       → Story title (e.g., "Gift Card Type & Data Model")
  description   → Markdown: ## Assumptions + ## Acceptance Criteria (table format, grouped by category)
  issuetype     → { name: "Story" }
  parent        → { key: Epic key } (uses parent field, not issueLink)
  labels        → ["rpi-generated"]

Task:
  summary       → Task title (e.g., "Create GiftCardPaymentInfo in items.xml")
  description   → Markdown: ## Assumptions + ## Acceptance Criteria + ## Verification Steps
  issuetype     → { name: "Task" }
  parent        → { key: Story key } (uses parent field)
  labels        → ["rpi-generated"]

Story-to-Story dependency links:
  type          → "Blocks" (via createIssueLink)
  inwardIssue   → predecessor Story key
  outwardIssue  → dependent Story key

Epic-to-Confluence link:
  type          → Remote Issue Link (via Jira API)
  url           → Confluence plan page URL
  title         → "Implementation Plan"
```

**Where these examples live in the skill:** All three go into `assets/` as `example-research-layer1.md`, `example-plan-story.md`, and `jira-field-mapping.md`. The SKILL.md references them when instructing subagents, and `references/jira-ticket-template.md` incorporates the field mapping directly.

### Optional Fine-Tuning (Available Anytime)

A `/configure` command lets the team lead adjust any auto-detected setting later:
- Team naming for Jira assignee suggestions
- AC category customization
- Confluence parent page for nesting
- Jira Epic link type and custom fields

Configuration is saved to `thoughts/shared/config/commerce-rpi-config.json` so it persists across sessions.

---

## 8. SKILL.md Outline

The main SKILL.md stays under 500 lines and follows this structure:

```
---
name: commerce-rpi-cowork
description: [trigger description - see section 12]
---

# Commerce RPI — Research, Plan, and Deliver

## What This Skill Does
[2-3 paragraphs: purpose, who it's for, what it produces]

## When to Use This Skill
[Trigger contexts: feature planning, initiative research, sprint prep, etc.]

## The Workflow
### Step 1: First-Run Configuration (2 Questions + Auto-Detect)
[Ask Confluence space + Jira project; auto-detect everything else; /configure for fine-tuning]

### Step 2: Research (Layered)
[Up to 6 layers: extension manifest, type system, service layer, storefront (conditional), ImpEx/data, consolidated]
[Points to references/research-layers.md for layer-specific prompts]

### Step 3: Plan (Interactive)
[Structure → detail → review → publish flow]
[Points to references/plan-template.md for format]

### Step 4: Publish & Handoff
[Confluence, Jira, and Git publishing]
[Points to references/confluence-publishing.md and jira-ticket-template.md]

### Step 5: Iterate (When Implementation Reveals Issues)
[Detect [RPI-ITERATE] Jira comments, load existing artifacts, targeted layer re-run, republish revisions]
[Lightweight — not a full re-plan, just targeted corrections]

## SAP Commerce Research Patterns
[Key patterns: CCv2 layout, where to find items.xml, spring configs, ImpEx naming]
[Points to references/ccv2-layout.md, hybris-layout.md, impex-patterns.md]

## Documentation Convention
[thoughts/ = working drafts, flow docs = final product — see plan section 5]

## Integration with Project-Specific Skills
[How to cross-reference data from co-installed skills]

## What This Skill Does NOT Do
[No implementation, no terminal commands for the user, no deployment]
```

---

## 9. Build Sequence

| Step | What | Depends On |
|------|------|------------|
| 1 | Write `SKILL.md` draft | This plan |
| 2 | Write `references/ccv2-layout.md` | Step 1 + real project validation |
| 3 | Write `references/hybris-layout.md` | Step 1 + real project validation |
| 3a | Write `references/spartacus-layout.md` | SAP help.sap.com + SAP Community blogs + SAP developer tutorials |
| 4 | Write `references/research-layers.md` | Steps 2-3, 3a |
| 5 | Write `references/impex-patterns.md` | Step 4 + real project ImpEx analysis |
| 6 | Write `references/plan-template.md` | Step 1 |
| 7 | Write `references/confluence-publishing.md` | Atlassian MCP testing |
| 8 | Write `references/jira-ticket-template.md` | Atlassian MCP testing |
| 9 | Write `scripts/parse_plan_to_tickets.py` | Step 6 |
| 10 | Write `assets/research-summary-template.md` | Step 4 |
| 10a | Write `assets/example-research-layer1.md` | Step 4 + real project |
| 10b | Write `assets/example-plan-story.md` | Step 6 |
| 10c | Write `assets/jira-field-mapping.md` | Step 8 |
| 11 | Create test cases (evals.json) | Steps 1-10 |
| 12 | Run test cases with skill-creator eval framework | Step 11 |
| 13 | Iterate based on eval feedback | Step 12 |
| 14 | Optimize skill description for triggering | Step 13 |
| 15 | Package as .skill file | Step 14 |

---

## 10. Test Cases (Draft)

These will be refined during the eval phase, but here's the initial set — all validated against the real `sap-mcp-server-g` project:

**Test 1 — "Research how cart and checkout work"**
> "We're planning to add a new payment method to our Commerce checkout. Can you research how our codebase currently handles cart entries, payment modes, and the checkout flow?"

Expected: Layered research produces documents for each layer. Should discover: `coremcp-items.xml` is currently empty (no custom types yet), `coremcp-spring.xml` has cart/checkout tool handlers referencing `cartFacade` and `checkoutFacade`, `sampledatamcp` ImpEx defines the store infrastructure including delivery modes and payment mode. Consolidated research surfaces assumption: "We assume a new extension or items.xml types will be needed since coremcp currently has no custom types."

**Test 2 — "Plan a new product type integration"**
> "Based on the research, I need a plan for adding a new product type to our catalog — it needs its own pricing, Solr searchability, and sample data for testing. It also needs a new service to handle business logic specific to this product type."

Expected: Interactive plan with Stories and Tasks, ImpEx Tasks using correct naming (e.g., `essentialdata-newtype-catalog.impex`, `essentialdata-newtype-solr.impex`, `projectdata-newtype-sampledata.impex`), verification steps using `./gradlew` commands, acceptance criteria grouped by category. Final Story is "Documentation" with Tasks for flow docs.

**Test 3 — "I need to understand how our promotions work before we change anything"**
> "Before we touch the promotion engine, I need a full picture of how promotions are set up in our system — the rules, the Backoffice config, and how they apply during cart calculation."

Expected: Research-only flow (no plan). Should discover: `sampledatamcp` depends on `promotionengineservices` and `couponservices`, promotions are set up via scripts (`setup-promotions.sh`, `publish-promotions.groovy`), PromotionGroup `thinkshopPromoGrp` is created in `essentialdata-infrastructure.impex`, free shipping promotion uses `y_change_delivery_mode` to switch to `thinkshop-free-delivery`.

---

## 11. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Large Commerce repos exceed subagent context | Research layers miss critical files | Layer 0 pre-scopes which extensions matter via `localextensions.xml`; subsequent layers use targeted file patterns scoped to relevant extensions only |
| Confluence/Jira MCP auth issues | Can't publish artifacts | Skill falls back to local markdown files + instructs user to copy-paste; never blocks the workflow |
| Research findings are wrong/incomplete | Plan builds on bad foundation | Two review checkpoints: Layer 0 (extension map — catches structural errors early) and Layer 5 (consolidated — thorough review of all findings + master assumptions list) |
| Story/Task granularity wrong | Jira tickets too big or too small for sprint work | Skill proposes Story breakdown, gets team lead approval, then proposes Task breakdown per Story before creating anything |
| Assumptions not surfaced early enough | Work proceeds on wrong foundation | Every layer, every Story, every Task surfaces assumptions explicitly; team lead must acknowledge before plan proceeds |
| Project-specific skill data becomes stale | Cross-references are wrong | Skill treats co-installed skill data as advisory, not authoritative; always defers to what's in the actual codebase |
| Storefront layer runs on headless project | Wasted time, confusing results | Layer 3 is conditional — only runs if `js-storefront/` detected during setup |
| ImpEx naming convention wrong | Files don't load at right time | Skill references validated naming patterns from real project; essentialdata-* vs projectdata-* with alphabetical ordering |
| CCv2 vs local dev path differences | Paths in plan don't match developer's environment | All paths relative to `core-customize/` root; `./gradlew` commands work in both CCv2 and local dev |
| Persona-specific config missed | Feature works in dev but not prod | Layer 2 explicitly checks all persona property files; consolidated research flags any environment differences |
| OOTB source not in repo | Research layers that cross-reference modules/ fail silently or produce incomplete results | Graceful degradation to "limited research mode" — convention-based inference from extension names, alias patterns, and known module mappings (see Section 7a) |
| Documentation duplication | thoughts/ and flow docs say the same thing | Clear convention: thoughts/ = working drafts (archived after), flow docs = permanent (created as final implementation Task) |

---

## 12. Skill Description (Draft for Triggering)

```
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
```

---

## 13. Success Criteria for the Skill Itself

**The skill is done when:**

1. A team lead with no terminal experience can mount a CCv2 Commerce repo and go from "I need to understand feature X" to research docs + plan + Jira tickets in a single Cowork session
2. Research documents are accurate enough that a developer reading them doesn't need to re-research the same codebase area
3. Plans are specific enough that Claude Code's `/implement_plan` can execute them without ambiguity
4. ImpEx Tasks use correct naming conventions and the plan specifies load ordering constraints
5. Verification steps use `./gradlew` commands, not raw ant
6. Confluence pages are formatted well enough that stakeholders can review them without asking "what does this mean?"
7. Jira tickets have acceptance criteria that map 1:1 to the plan's success criteria
8. Flow documentation Tasks are included in every plan, linking thoughts/ artifacts to permanent docs
9. The iterate phase detects `[RPI-ITERATE]` Jira comments and produces targeted plan revisions without re-running all research layers
10. Research-only workflows (no plan, no Jira) work as a first-class path — not an error or edge case

---

## 14. What Comes Next (Future Iterations)

- **Bitbucket PR integration** — Commit research/plan docs to a feature branch and create a PR for review
- **Implementation status tracking** — As developers complete plan phases in Claude Code, update Jira ticket status automatically
- **Multi-initiative support** — Research and plan across multiple related initiatives with cross-cutting dependency tracking
- **Template library** — Pre-built research layer configs for common Commerce patterns (cart mods, new payment types, promotion changes, new product types)
- **Automated feedback loop** — After implementation, automatically capture what the plan got right/wrong from Jira `[RPI-ITERATE]` history and feed patterns back into the skill's research prompts (building on the manual iterate phase in Phase D)
- **SAP Help auto-refresh** — Periodically re-fetch Spartacus/Composable Storefront docs from SAP help/blogs to keep `spartacus-layout.md` current with latest version conventions
