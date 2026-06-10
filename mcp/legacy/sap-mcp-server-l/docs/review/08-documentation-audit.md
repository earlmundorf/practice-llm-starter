# Documentation & Remnant Audit

**Date:** June 9, 2026 — after execution of improvement-plan Phases 1–4.
**Scope:** every Markdown file at root, `docs/`, and both extension `docs/` trees; every file in `core-customize/scripts/`; `thoughts/`; `.claude/skills/`; `.mcp.json`; stray config files. Each claim verified against current source.

## Verdict

The documentation system is structurally healthy — the extension-setup guides, agent-chat flow docs, sampledatamcp flow docs, ADRs, and review package are accurate. The staleness is concentrated where Phases 1–4 changed reality out from under pre-existing docs: **tool counts, the session-store description, missing API responses (429/400), and undocumented configuration properties.** Remnants are few: one superseded smoke script, one dead debug script, the abandoned `thoughts/` planning archive, and nine Spartacus skills with no frontend to serve.

---

## A. Must-fix documentation (factually wrong)

| # | File | Says | Reality |
|---|------|------|---------|
| A1 | `coremcp/docs/README.md`, `mcp-protocol/context.md` | "15 tool handlers" / "16 tools total" | **20 handlers** (19 on MCP + ui_action agent-only) — verified against live boot log |
| A2 | `coremcp/docs/agent-chat/components.md` | "18 tool handlers" table | 20 — `info_search`/`info_get` missing from the table |
| A3 | `coremcp/docs/mcp-protocol/context.md` | In-memory `ConcurrentHashMap` sessions; persistence is an "upgrade path" | Persistence **shipped**: `McpSessionEntry` + `PersistedMcpSessionService` + `DelegatingMcpSessionService`, default `coremcp.session.store=persistent`, cleanup cronjob |
| A4 | `coremcp/docs/endpoints.md` | No 429 documented on `/agent/*`; no 400 for oversized message arrays | Rate limit (default 20/min/user) returns 429; >50 messages returns 400; visual-search validates base64 (400) |
| A5 | `docs/extending/new-feature-walkthrough.md` + `checklist.md` | `./gradlew ybuild stopServer startServer yupdatesystem` | Missing `yclean` (silent stale-class hazard) and `yupdatesystem` must run with the server **stopped** — both lessons already recorded in CLAUDE.md |

## B. Should-fix documentation (incomplete)

| # | File | Gap |
|---|------|-----|
| B1 | `README.md` (root) | Features list predates Phases 1–4: no persistent sessions, rate limiting, LLM retry, smoke-test.sh; scripts list incomplete; "10 products" is now 11 (out-of-stock edge product); no pointer to SECURITY.md / docs/adr |
| B2 | `CLAUDE.md` | coremcp extension summary missing persistent session store + rate limiting |
| B3 | `coremcp/docs/llm-providers.md` + `llm/README.md` | New resilience properties undocumented: `coremcp.llm.retry.*`, `coremcp.llm.stream.timeout.seconds`, `coremcp.anthropic.maxTokens`; provider beans now aliased (`defaultOpenAiLlmProvider` …) |
| B4 | `coremcp/docs/tools.md` | `product_search` entry shows the old generic description and `pageSize: 20` default (now 5); new category codes + out-of-stock guidance missing |
| B5 | `docs/getting-started.md` / `docs/data.md` | Neither explains the numeric-prefix ImpEx convention, the essentialdata-vs-projectdata load semantics, or that projectdata only loads on initialize (manual `impex -Pfile` on existing DBs) |
| B6 | `coremcp/docs/solr.md` | Category facet now live for electronics categories — worth one line |

## C. Cosmetic

- `CLAUDE.md` access points use `http://…9001` while README/getting-started standardize on `https://…9002` (both work; pick one).
- `endpoints.md` visual-search error examples don't match exact current messages.
- `coremcp/docs/visual-search/implementation-steps.md` is a pre-implementation planning checklist for a feature that shipped — archive or delete (the flow's context/components/diagram remain the real docs).

## D. Remnants

| Path | Finding | Recommendation |
|------|---------|----------------|
| `scripts/smoke-info.sh` | Knowledge/swag-era smoke subset; fully superseded by `smoke-test.sh` (21 checks incl. all of these) | **Delete** |
| `scripts/probe-config.groovy` | One-off config dumper; references the **removed** intent-classifier properties (`coremcp.*.intent.model`), missing all Phase 1–2 properties; unreferenced | **Delete** (project.properties now documents all defaults) |
| `scripts/test-mcp-e2e.py` | Deeper protocol e2e (incl. session DELETE) that smoke-test.sh doesn't cover | **Keep**; mention in README scripts list |
| `thoughts/` (11 files) | Abandoned Mar-2026 planning artifacts: Stripe integration (zero Stripe code exists), RPI-skill build plans (skills since built), payment-process research | **Delete or archive** — they reference pre-rename impex names and unimplemented features; misleading to new readers |
| `.claude/skills/spartacus-*` (9 skills) | No Spartacus/Angular code anywhere in this repo | **Delete** — noise in the skill list; they auto-trigger on irrelevant prompts |
| `.mcp.json` + `scripts/mcp-stdio-bridge.py` | Active and correct (bridge is how this session's MCP tools connect) | Keep |
| All `hac-*.sh`, `index-solr.*`, `setup/publish-promotions.*`, `smoke-test.sh` | Referenced, current | Keep |

## E. False alarms (checked and cleared — do NOT remove)

- `dev-config/local-dev.properties` — looks sparse but is a **CCv2 persona file referenced by manifest.json `useConfig`**. Same for `-stg`/`-prod`.
- `core-customize/dependencies/.gitkeep` — deliberately whitelisted in `.gitignore` so the bootstrap target directory exists on clone.
- `.claude/skills/commerce-rpi-code` / `commerce-rpi-cowork` — full 250–300-line skills, not stubs.

## Recommended execution order

1. **Must-fix docs (A1–A5)** — five files, factual corrections; ~1 hour.
2. **Remnant removal (D)** — delete `smoke-info.sh`, `probe-config.groovy`, `thoughts/`, nine `spartacus-*` skills; one commit titled for easy revert.
3. **Should-fix docs (B1–B6)** — feature-list refresh + property reference; the natural place for the property table is one new section in `coremcp/docs/llm-providers.md` plus a "Configuration reference" pointer at `coremcp/project.properties` (which is already fully commented).
4. **Cosmetic (C)** — fold into the above where touched; don't chase separately.

A follow-on guard: the doc convention ("docs before code") held for flows but not for reference docs (tools.md, endpoints.md). Recommendation: add one line to CLAUDE.md's critical rules — *"When changing tool schemas, endpoints, or configuration properties, update the matching reference doc in the same commit."*
