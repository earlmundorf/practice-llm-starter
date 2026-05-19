# Addendum: `commerce-rpi-code` — Claude Code Variant

**Companion to:** `2026-03-25-commerce-rpi-cowork-skill-plan.md`

---

## Purpose

The Cowork skill (`commerce-rpi-cowork`) targets team leads who never touch a terminal. This addendum defines a companion skill (`commerce-rpi-code`) for developers working in Claude Code who want the same RPI discipline — layered research, structured plans, Jira output — with the **option** to continue into implementation in the same session.

The Claude Code skill supports three stopping points — each a first-class workflow:

| Mode | Flow | When to Use |
|------|------|-------------|
| **Research only** | R → stop | Understanding a codebase area before deciding what to do |
| **Research + Plan** | R → P → stop | Producing a plan and tickets for someone else to implement, or for a future session |
| **Full RPI** | R → P → I (→ iterate) | Developer wants to research, plan, and implement in one flow |

After each phase completes, the skill asks: "Continue to [next phase], or stop here?" The developer chooses. This means the Claude Code skill is a strict superset of the Cowork skill — it can do everything Cowork does (R, R+P) plus implementation.

---

## What Stays the Same

The vast majority of the Cowork plan carries over unchanged:

| Component | Reuse? | Notes |
|-----------|--------|-------|
| Research layers (0-5) | **Identical** | Same layered research, same compaction, same `thoughts/` output |
| Layer definitions | **Identical** | Extension manifest, type system, service layer, storefront (conditional), ImpEx, consolidated |
| Two review checkpoints | **Identical** | Layer 0 + Layer 5 — same logic applies |
| Graceful OOTB degradation | **Identical** | Same three modes (Full / Hybrid / Limited) |
| Plan structure | **Identical** | Same Epic → Story → Task hierarchy, same grouped AC, same assumptions |
| Documentation convention | **Identical** | `thoughts/` = working, flow docs = permanent |
| Jira ticket creation | **Identical** | Same field mapping, same hierarchy, same `[RPI-ITERATE]` tags |
| ImpEx patterns reference | **Identical** | Same naming conventions, same ordering rules |
| CCv2/hybris layout references | **Identical** | Same repo structure knowledge |
| Spartacus layout reference | **Identical** | Same SAP docs-sourced reference |
| `references/` directory | **Shared** | Both skills read the same reference files |
| `assets/` directory | **Shared** | Same examples, same templates |
| `scripts/` directory | **Shared** | Same ticket parsing script |

**Bottom line:** ~80% of the skill content is shared. The reference files, research layer definitions, plan templates, and Jira mappings are identical.

---

## What Changes

### 1. Persona

| | Cowork | Claude Code |
|--|--------|-------------|
| **Primary user** | Team lead (functional or technical) | Developer (writes code) |
| **Terminal comfort** | None — never touches terminal | High — lives in terminal |
| **Goal** | Produce research + plan + tickets for handoff | Flexible: research only, R+P for handoff, or full R+P+I |
| **Session boundary** | R&P only; handoff to developer for I | Developer chooses stopping point after each phase |

### 2. Interaction Model

| | Cowork | Claude Code |
|--|--------|-------------|
| **UI** | Chat conversation in Cowork desktop app | Terminal CLI with slash commands |
| **Invocation** | Skill triggers on natural language | `/research`, `/plan`, `/implement_plan` commands |
| **Review** | Interactive chat Q&A | Review in terminal or via `thoughts/` files |
| **Artifact viewing** | Inline in Cowork UI | Read files directly or open in editor |
| **Confluence publishing** | Primary output target | Optional — developer may skip if working solo |

### 3. Implementation Phase Is Available (Not Required)

In Cowork, Phase C is always a handoff. In Claude Code, the developer chooses after the plan is approved:

- **"Stop here"** — Plan is written to `thoughts/`, optionally published to Confluence/Jira. Same outcome as Cowork. Useful when the developer is planning for a teammate, a future sprint, or wants to review the plan before committing to implementation.
- **"Implement"** — Skill continues into execution:


**Phase C (Claude Code): Implement**

- Reads the plan from `thoughts/shared/plans/`
- Reads research from `thoughts/shared/research/`
- Executes plan Tasks sequentially, checking off plan items as completed
- After each Task:
  - Runs verification commands (`./gradlew yclean yall`, `./gradlew yunittests`, etc.)
  - Updates `thoughts/shared/plans/` with completion status
  - If verification fails, diagnoses and fixes before moving to next Task
- After items.xml changes: `./gradlew ybuild stopServer startServer yupdatesystem`
- After beans.xml changes: `./gradlew ybuild stopServer startServer`
- After Java source changes: `./gradlew ybuild stopServer startServer`
- Creates flow documentation (`context.md`, `components.md`, `diagram.md`) as the final Task
- Optionally updates Jira ticket status as Tasks complete

**What the implement phase does NOT do:**
- Push to remote (developer decides when)
- Deploy to any environment
- Modify OOTB platform or modules (same critical rule from CLAUDE.md)
- Skip verification steps even if the build is slow

### 4. Configuration

| | Cowork | Claude Code |
|--|--------|-------------|
| **Confluence space** | Required (primary output) | Optional (may skip for solo work) |
| **Jira project** | Required (ticket creation) | Optional (may create tickets or just use `thoughts/`) |
| **Repo detection** | Same auto-detect | Same auto-detect |
| **Build commands** | Referenced in plan only | Actually executed when implementing; referenced only for R or R+P modes |

The Claude Code skill asks two optional questions at the start: "Publish to Confluence/Jira, or work locally with `thoughts/` only?" and at each phase boundary: "Continue to [next phase], or stop here?" This supports everything from a quick research spike to full R+P+I with tickets.

### 5. Iterate Phase Is Tighter

In Cowork, iteration requires the team lead to open a new session and review `[RPI-ITERATE]` tags. In Claude Code, the developer can iterate within the same session regardless of which mode they're in:

- **During R+P (no implementation):** Developer reviews the plan and says "assumption X is wrong" or "we also need to consider Y." Skill re-runs affected research layers and revises the plan before publishing.
- **During R+P+I (with implementation):** Developer hits a problem during implementation. Instead of tagging Jira, they say: "The plan assumed X but actually Y — revise." Skill re-runs affected layers, updates the plan in `thoughts/`, and continues implementation.
- If Jira integration is active, the skill also updates tickets with `[RPI-ITERATE]` comments in both cases.

This is faster but less auditable than the Cowork Jira tag approach. For team workflows, the Jira tag approach from the Cowork plan is still recommended. For solo work, in-session iteration is fine.

---

## Skill Architecture: Shared + Variant

```
commerce-rpi/                          # Shared root (both skills use this)
├── references/
│   ├── research-layers.md
│   ├── ccv2-layout.md
│   ├── hybris-layout.md
│   ├── spartacus-layout.md
│   ├── impex-patterns.md
│   ├── plan-template.md
│   ├── confluence-publishing.md
│   └── jira-ticket-template.md
├── scripts/
│   └── parse_plan_to_tickets.py
├── assets/
│   ├── research-summary-template.md
│   ├── example-research-layer1.md
│   ├── example-plan-story.md
│   └── jira-field-mapping.md
│
├── cowork/
│   └── SKILL.md                       # Cowork-specific skill (no terminal, team leads)
│
└── claude-code/
    └── SKILL.md                       # Claude Code-specific skill (terminal, developers)
```

The shared `references/`, `scripts/`, and `assets/` directories are identical. Each variant has its own `SKILL.md` that imports the shared references but defines its own interaction model, persona, and phase behavior.

---

## Build Sequence Delta

The Cowork build sequence (from the main plan) produces all shared artifacts. The Claude Code variant adds:

| Step | What | Depends On |
|------|------|------------|
| CC-1 | Write `claude-code/SKILL.md` | Main plan + this addendum |
| CC-2 | Add implementation phase instructions (build commands, verification, server lifecycle) | CC-1 + CLAUDE.md critical rules |
| CC-3 | Add slash command definitions (`/research`, `/plan`, `/implement_plan`, `/iterate`) | CC-1 |
| CC-4 | Create Claude Code-specific test cases | CC-1 + shared test cases as base |
| CC-5 | Run test cases with skill-creator eval framework | CC-4 |
| CC-6 | Package as separate skill or combined skill with mode detection | CC-5 |

### Packaging Decision: Two Skills or One?

**Option A: Two separate skills** — `commerce-rpi-cowork` and `commerce-rpi-code`. Simpler to maintain, clearer triggering, no mode confusion. Shared references live in a common directory both skills point to.

**Option B: One skill with mode detection** — Single `commerce-rpi` skill detects whether it's running in Cowork or Claude Code and adjusts behavior. Less duplication in SKILL.md, but more complex conditional logic.

**Recommendation: Option A (two skills, shared references).** The personas are different enough that the SKILL.md instructions diverge significantly. Trying to serve both from one file makes the skill harder to read and test. The shared references directory means there's no content duplication — just two thin SKILL.md wrappers over the same knowledge base.

---

## Test Cases (Claude Code-Specific)

These extend the shared test cases from the main plan with implementation verification:

**Test CC-1 — "Research and implement a new product attribute"**
> "I need to add a 'warranty period' attribute to products in our Commerce system. Research how products are set up, plan the change, and implement it."

Expected: Full R→P→I flow. Research discovers product types via items.xml and ImpEx. Plan creates Story + Tasks. Implementation modifies items.xml, creates ImpEx, runs `./gradlew yclean yall` and `./gradlew yupdatesystem`. Flow docs created at the end.

**Test CC-2 — "Research only, don't implement"**
> "I just need to understand how Solr indexing is configured in this project. Don't change anything."

Expected: Research-only flow. Same behavior as Cowork Test 3 — no plan, no Jira, just research docs in `thoughts/`.

**Test CC-3 — "Plan iteration mid-implementation"**
> Start implementation, then: "Wait — the plan assumed we'd extend ProductModel but actually we need a new type. Revise the plan."

Expected: In-session iteration. Skill re-runs Layer 1 (type system) and Layer 5 (consolidated), updates the plan in `thoughts/`, and continues implementation from the revised plan.

**Test CC-4 — "Local-only workflow (no Jira/Confluence)"**
> "Research and plan a new delivery mode. Just use thoughts/ files, don't create any tickets."

Expected: Full R→P flow with all artifacts in `thoughts/` only. No Atlassian MCP calls. Plan still follows the same structure with AC and assumptions.

**Test CC-5 — "Research and plan, but don't implement yet"**
> "I need to research and plan out adding a loyalty points system to our checkout. Create the tickets but don't implement — I want to review the plan with the team first."

Expected: R→P flow ending with Jira ticket creation and Confluence publishing. Skill asks "Continue to implementation?" and developer declines. `thoughts/` artifacts are ready for a future session or handoff to another developer. No code changes made.

---

## Risk Delta

| Risk | Claude Code-Specific | Mitigation |
|------|---------------------|------------|
| Developer skips research and jumps to implement | Loses the "validated plan" quality | Skill warns if `/implement_plan` is called with no research in `thoughts/` |
| In-session iteration is less auditable than Jira tags | Team loses visibility into plan changes | If Jira integration is active, still post `[RPI-ITERATE]` comments automatically |
| Long implementation sessions exhaust context | Context rot during implementation of large plans | Apply Ralph Loop within implementation — each Task runs in a fresh subagent, writes results, compacts |
| Developer implements in wrong order | Dependency violations (e.g., ImpEx before items.xml types exist) | Plan Tasks are ordered with dependencies; skill enforces sequential execution |
| Build commands fail and developer works around them | Verification is bypassed | Skill treats verification failure as a blocker — does not proceed to next Task until resolved |

---

## Success Criteria Delta

The Cowork success criteria (from the main plan) all apply. Additionally:

1. All three stopping points (R, R+P, R+P+I) work as first-class workflows — the skill asks at each phase boundary and respects the developer's choice
2. A developer choosing full R+P+I can go from "I need feature X" to working, tested, documented code in a single Claude Code session
3. A developer choosing R+P produces identical output quality to the Cowork skill — same `thoughts/` artifacts, same Jira tickets, same Confluence pages
4. The implement phase (when chosen) runs all verification commands and does not skip any
5. In-session iteration produces the same quality plan revisions as the Cowork iterate phase
6. Local-only workflows (no Jira/Confluence) are first-class — not degraded
7. `thoughts/` artifacts produced by either skill variant are interchangeable — a team lead can research in Cowork and a developer can implement in Claude Code from the same `thoughts/` files
8. A developer can pick up `thoughts/` artifacts from a previous R+P session (their own or from a Cowork handoff) and run implementation without re-researching
