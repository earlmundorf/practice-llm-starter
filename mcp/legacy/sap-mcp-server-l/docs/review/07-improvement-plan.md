# Improvement Plan

> **Execution status (2026-06-09): Phases 1–4 are COMPLETE. Only Phase 5
> (JDK 21 migration, SAP Storefront MCP convergence, accelerator packaging)
> remains.** 80 unit tests green; persisted-session integration test 6/6; data
> changes verified end-to-end through the live MCP endpoint (fresh handshake,
> category-filtered search, out-of-stock product surfaced).
>
> Phase 4 delivery detail:
> - 4.1: `SECURITY.md` at repo root (secrets policy + 10-point CCv2 production
>   checklist); `DeepLinkBuilder` warns once when the localhost default is used.
> - 4.2: `docs/adr/` — 6 ADRs covering MCP-as-OCC-extension, the session-store
>   evolution, Groovy promotions, post-Solr date filtering, internal Jackson
>   DTOs, and demo-data isolation + the dual-import/sync-job strategy.
> - 4.3: numeric load-order prefixes on all sampledatamcp ImpEx (fixed a latent
>   first-init bug — product media imported before products); electronics
>   category tree (computing/mobile/audio/accessories) + assignments, live in
>   the Solr category facet; `CatalogVersionSyncJob` Staged→Online for ongoing
>   authoring; `KEYBOARD_LTD_ALUMINUM` out-of-stock edge-case product;
>   `product_search` tool description updated with the new categories and
>   honest out-of-stock guidance.
> - Deferred from 4.3: localizing the 7 Unsplash knowledge-image URLs — doing it
>   properly means converting `KnowledgeEntry.imageUrl` (string) to a Media
>   reference, a breaking type change not worth it for a demo; revisit if
>   offline demos become a requirement. 4.4 optional polish (second locale,
>   DeepLinkBuilder interface, Spartacus-skill pruning) also deferred.
>
> Phase 3 delivery detail:
> - 3.1: `runChat()` is now a ~70-line orchestrator. Extracted collaborators (all
>   alias-wired): `AgentStateSnapshotBuilder`, `EntityRefCollector`,
>   `AgentToolInvoker` (+ per-turn `AgentTurnContext`). Behavior pinned by
>   `DefaultAgentServiceTest` (12 tests) written against the loop contract.
> - 3.2: `com.coremcp.dto.llm` — `LlmChatResponse`/`LlmToolCall` (one-place parse
>   + validation of the normalized provider response; raw assistant message kept
>   for history echo) and `VisionAnalysisResult` (typed vision parsing with
>   `@JsonAnySetter` extras; malformed model JSON now logged + graceful).
>   Zero `@SuppressWarnings("unchecked")` cast chains remain in the agent/vision path.
> - 3.3: new tests — `DefaultAgentServiceTest` (12), `AgentControllerTest` (10:
>   validation 400s, rate-limit 429, SSE event stream, fallback, error shaping),
>   `DefaultVisualSearchServiceTest` (6: fenced/malformed JSON, tiering, dedup,
>   result cap). Controllers expose protected config getters so unit tests run
>   without the platform.
>
> Phase 1–2 execution notes:
> - 2.1 amendment: per-request timeouts already existed (wired to
>   `coremcp.llm.timeout.seconds`) — the review overstated that gap. The retry
>   layer and a separate streaming timeout (`coremcp.llm.stream.timeout.seconds`)
>   were added as planned.
> - 1.5 resolved by pinning `manifest.json` to 2211.38 (matching the local suite
>   ZIP) with a note in docs/getting-started.md; supersede via the JDK-21 bump (5.1).
> - Found & fixed during verification: fragile nested Mockito stubbing in
>   `InfoToolHandlersTest` (mock creation inside `thenReturn` args).
> - Known pre-existing failure (NOT from these changes — proven by re-running on a
>   fresh junit tenant without the new ImpEx):
>   `ThinkShopDemoDataIntegrationTest.testStockLevelsMatchDemoData` reads ATP 0 for
>   LAPTOP_PRO_15 in a freshly initialized junit tenant (interaction between the
>   test fixture and `sampledatamcp` essential data, likely the `thinkshop-atp-formula`
>   on the shared `electronics` base store). Track as a Phase 3 test-depth item.
> - Process fixes recorded in CLAUDE.md: run `yupdatesystem` with the server
>   stopped; run integration tests via ant with `-Dtestclasses.extensions`;
>   `ant yunitinit` required after items.xml changes.

A phased, executable plan closing every finding in documents 02–06. Each task lists the files to touch, how to verify, and acceptance criteria. Build/verify commands follow project rules (`CLAUDE.md`): always `yclean ybuild` for Java changes; stop the server before CLI test runs; scope tests to custom extensions.

**Standard verification cycle** (referenced below as *VC*):

```bash
cd core-customize
./gradlew yclean ybuild
./gradlew stopServer
./gradlew testCustomExtensions
./gradlew startServer
```

Paths below are relative to `core-customize/hybris/bin/custom/` unless noted.

---

## Phase 1 — Platform hygiene & quick wins (≈2–3 days)

Low-risk, high-credibility fixes. Do these before any external demo.

### 1.1 Declare the missing extension dependencies — **High**
- **File:** `coremcp/extensioninfo.xml`
- **Change:** add `<requires-extension name="solrfacetsearch"/>`, `<requires-extension name="promotionengineservices"/>`, `<requires-extension name="couponservices"/>`
- **Verify:** VC (a clean build proves the declarations resolve).
- **Accept:** build green; `extensioninfo.xml` declares every extension whose classes `coremcp` imports.

### 1.2 Alias the LLM provider beans — **Medium**
- **File:** `coremcp/resources/coremcp-spring.xml`
- **Change:** rename bean ids to `defaultOpenAiLlmProvider` / `defaultAnthropicLlmProvider` / `defaultOpenAiCompatibleLlmProvider`; add `<alias>` entries with the old names; update internal `ref`s to the aliases.
- **Verify:** VC; then hit `/agent/chat` once per configured provider.
- **Accept:** a downstream extension can swap any provider by redefining only the alias.

### 1.3 Externalize hardcoded operational values — **Medium**
- **Files:** `coremcp/project.properties` (new keys + defaults); `DefaultAgentService`, `DefaultMcpSessionService`, `AnthropicLlmProvider`, `DefaultVisualSearchService`, `VisualSearchController`, `KnowledgeController`
- **Keys:** `coremcp.agent.maxToolIterations=10`, `coremcp.agent.toolResult.summaryThreshold=300`, `coremcp.agent.toolResult.summarySnippet=200`, `coremcp.session.ttl.minutes=30`, `coremcp.llm.anthropic.maxTokens=1024`, `coremcp.visualsearch.model=gpt-4o`, `coremcp.visualsearch.maxImageBytes=10000000`, `coremcp.knowledge.maxPageSize=50`
- **Change:** read via `configurationService`/`Config.getParameter` with the current values as defaults. Behavior unchanged by default.
- **Verify:** VC; override one key in `local.properties` and confirm it takes effect.
- **Accept:** no operational constant requires a code change to tune.

### 1.4 Documentation corrections — **Medium**
- **Files:** `README.md`, `CLAUDE.md` (root): replace `sap-mcp-server-g` with `sap-mcp-server-l` (or `<repo-root>`); `coremcp/resources/coremcp-beans.xml`: remove the stale "Phase 2" comment; `DefaultPromotionQueryService`: fix "DAO" wording in comments.
- **Verify:** `grep -r "sap-mcp-server-g" .` returns nothing.

### 1.5 Reconcile platform version drift — **Medium**
- **Decision:** either download `hybris-commerce-suite-2211.50.zip` into `core-customize/dependencies/` and re-run `./gradlew bootstrapPlatform`, or deliberately pin `manifest.json` to `2211.38` until the JDK-21 migration (task 5.1) supersedes both.
- **Accept:** `manifest.json` version == `hybris/bin/platform/build.number` version, or a written note in `docs/getting-started.md` explaining the pin.

### 1.6 Log swallowed exceptions — **Low**
- **Files:** `AgentController` (~:104), `DefaultAgentService` (~:250), `AnthropicLlmProvider` (~:181) and remaining `catch (Exception ignored)` sites.
- **Change:** add `LOG.debug("...", e)` — no behavior change.

---

## Phase 2 — Resilience & scale (≈1–2 weeks)

Makes the system safe for a customer-facing pilot. Tasks 2.1/2.2 ship together.

### 2.1 LLM HTTP timeouts and retries — **High**
- **Files:** `AbstractOpenAiCompatibleLlmProvider`, `AnthropicLlmProvider` (and the new base from 2.2)
- **Change:** per-request `.timeout(...)` (configurable: `coremcp.llm.timeout.seconds=60`, `coremcp.llm.stream.timeout.seconds=120`); bounded retry with exponential backoff + jitter for 429/500/502/503 (`coremcp.llm.retry.maxAttempts=3`), never retrying after streaming has begun emitting deltas.
- **Tests:** unit tests for retry policy (mock 429→200 sequence, exhaust-retries path, no-retry-after-first-delta rule).
- **Accept:** a stalled upstream LLM fails the request within the configured timeout, freeing the Tomcat thread; transient 5xx no longer reaches the user on first occurrence.

### 2.2 Extract a shared `HttpLlmProvider` base — **Medium**
- **Files:** new `coremcp/src/com/coremcp/services/impl/AbstractHttpLlmProvider.java`; refactor the three providers onto it.
- **Change:** base owns HttpClient construction, timeout/retry policy, status validation, error wrapping, and **uniform** streaming-fallback semantics (adopt Anthropic's partial-response draining for all providers).
- **Verify:** VC + existing `AnthropicLlmProviderTest` still green; manual chat against each provider.
- **Accept:** no duplicated HTTP plumbing; all providers share identical resilience behavior.

### 2.3 Persist MCP sessions for multi-node CCv2 — **High** (the headline task)
- **Why:** CCv2 API aspects are multi-node; sticky routing is cookie-based and MCP clients send headers, not cookies; rolling deploys wipe node memory (see document 02 §5).
- **Files:** `coremcp/resources/coremcp-items.xml` (new `McpSessionEntry` item: `sessionId` unique, `cartCode`, `userUid`, `protocolVersion`, `lastAccessed`; typecode e.g. 14002); new `PersistedMcpSessionService` implementing `McpSessionService` via ModelService/FlexibleSearch; cleanup cronjob for expired sessions (ImpEx in `coremcp/resources/impex/`); alias switch in `coremcp-spring.xml` (keep the in-memory impl for tests/local via property toggle `coremcp.session.store=persistent|memory`).
- **Build sequence:** items.xml change → `./gradlew yclean ybuild stopServer startServer yupdatesystem`.
- **Tests:** integration test — create session, verify survives across service instantiation; TTL expiry honored; cronjob removes expired rows.
- **Accept:** an MCP conversation continues correctly when consecutive requests hit different nodes (simulate by clearing the in-memory map / restarting between calls); documented fallback to in-memory mode for unit tests.
- **Optional fast-follow:** add a read-through cache in front of the persisted store if HAC SQL shows per-request session reads becoming hot.

### 2.4 Endpoint guards — **Medium**
- **Files:** `AgentController` (max messages per request, e.g. 50; reject oversized bodies explicitly), `KnowledgeController` (clamp `pageSize` to `coremcp.knowledge.maxPageSize`), `VisualSearchController` (validate base64 decodes before calling the vision model), plus a simple per-user rate limit on `/agent/*` (e.g. token bucket keyed on user uid, `coremcp.agent.rateLimit.perMinute=20`) — LLM calls cost real money.
- **Tests:** unit tests for each rejection path (400 with structured error body).

---

## Phase 3 — Quality & test depth (≈1–2 weeks)

### 3.1 Decompose `DefaultAgentService.runChat()` — **Medium**
- **File:** `DefaultAgentService.java:142-311`
- **Change:** extract collaborators behind the existing service: `AgentToolInvoker` (dispatch + duplicate detection + result summarization), `AgentStateSnapshotBuilder` (cart/customer snapshot), `EntityRefCollector`. Wire via Spring with the alias pattern. `runChat()` becomes the orchestration skeleton (~40 lines).
- **Constraint:** pure refactor — no behavior change; verify with the new tests from 3.3 written *first* against the current behavior where practical.

### 3.2 Typed DTOs for LLM I/O — **Medium**
- **Files:** new `coremcp/src/com/coremcp/dto/llm/` package: `LlmChatResponse`, `LlmToolCall`, `VisionAnalysisResult`, etc. (plain Jackson classes — internal DTOs, deliberately not `*-beans.xml`-generated; record that choice in the ADR from 4.2).
- **Change:** replace the cast chains in `DefaultAgentService` and the regex-then-`Map.class` parsing in `DefaultVisualSearchService:184` with typed deserialization + explicit validation errors.
- **Accept:** zero `@SuppressWarnings("unchecked")` in the agent/vision path; malformed provider responses produce a logged, specific error instead of a silent fallback.

### 3.3 Close the test gap on critical paths — **High**
Priority order (highest risk per test written first):
1. `DefaultAgentServiceTest` — mocked `LlmClient` + tool handlers: single-turn; multi-turn with tool calls; duplicate-call skip; iteration cap; summarization threshold; entity-ref extraction.
2. `AgentControllerTest` — validation failures; non-stream fallback; SSE event sequence (MockMvc); error payload carries cartCode.
3. `DefaultVisualSearchServiceTest` — vision JSON happy path, malformed JSON, 3-tier search fallthrough, dedup.
4. Retry/timeout tests (with 2.1) and guard tests (with 2.4).
5. Remaining tool handlers (template: existing `CartToolHandlersTest`).
- **Run:** `./gradlew stopServer && ./gradlew testCustomExtensions && ./gradlew startServer`
- **Accept:** every class named in document 03 §2.2 has a test class; agent-loop behaviors enumerated above each have a dedicated test.

---

## Phase 4 — Enterprise readiness (≈1 week)

### 4.1 SECURITY.md + production deployment checklist — **High (cheap)**
- **File:** new `SECURITY.md` at repo root.
- **Content:** the checklist from document 04 §6 verbatim — secrets via Cloud Portal, exclude `sampledatamcp` in prod, production OAuth ImpEx with injected secrets, CORS/baseUrl/admin-password overrides, patch currency.
- Add a warn-log in `DeepLinkBuilder` when the localhost default is used.

### 4.2 Architecture Decision Records — **Medium**
- **Files:** new `docs/adr/` — backfill the decisions this review surfaced: 0001 MCP-server-as-OCC-extension; 0002 in-memory→persisted session store; 0003 Groovy-based promotions; 0004 knowledge dates filtered post-Solr; 0005 internal Jackson DTOs vs beans.xml; 0006 demo data isolated in `sampledatamcp`.
- These exist as scattered comments today; ADRs are what SAP architects look for.

### 4.3 Data enrichment — **Medium**
- **Files:** `sampledatamcp/resources/impex/`
- **Changes:** (a) category hierarchy (Computing/Audio/Accessories/Merch) + assignments — the category facet already exists and will light up; (b) `CatalogVersionSyncJob` Staged→Online instead of double-import; (c) numeric load-order prefixes for the Solr ImpEx files; (d) 1–2 out-of-stock products for honest agent demos; (e) localize the 7 Unsplash knowledge images into media.
- **Build:** `./gradlew yclean ybuild stopServer startServer yupdatesystem`, then `./scripts/index-solr.sh`.

### 4.4 Optional polish — **Low**
- Second locale (de) for one product + one knowledge entry to prove i18n; `DeepLinkBuilder` interface + alias; startup config logging in agent/session/knowledge services; prune the nine Spartacus skills from `.claude/skills/`.

---

## Phase 5 — Strategic runway (calendar-driven)

### 5.1 JDK 21 / 2211-jdk21 migration — **High, hard deadline**
- SAP disallows new Java-17 CCv2 builds after **2026-08-31** (adopt-by guidance: 2026-06-30). The parallel `mcp/jdk21/` tree with its planned parity work is the vehicle; the four-commit forward-port must complete and the jdk21 tree become primary **before end of July 2026**. Apply Phases 1–4 changes there too (or execute them on jdk21 first and back-port — decide once parity lands).

### 5.2 SAP Storefront MCP convergence evaluation — **High (timing: at SAP GA, ~Q2/Q3 2026)**
- Written comparison when SAP's server GAs: tool-by-tool overlap matrix; adopt SAP's server for overlapping read-side tools where it wins; retain this extension's transactional/agent/knowledge/visual tools as the differentiated layer; align tool names/semantics with SAP's where practical. Deliverable: one-page decision doc + updated positioning in document 06.

### 5.3 Accelerator packaging — **Medium**
- Turn the repo into the practice asset: demo runbook (script + reset procedure), client-pitch deck derived from documents 01/06, and a "delivery method" appendix covering the docs-first convention and `.claude/skills/` so the methodology is sellable independent of the code.

---

## Sequencing summary

| Phase | Duration | Gate it unlocks |
|---|---|---|
| 1 | 2–3 days | Clean review by any SAP architect; safe to demo widely |
| 2 | 1–2 weeks | Safe for a customer-facing pilot on CCv2 |
| 3 | 1–2 weeks | Safe for a delivery team to extend without fear |
| 4 | 1 week | Credible as a governed enterprise accelerator |
| 5 | calendar-driven | Ahead of, and aligned with, SAP's own roadmap |

Total engineering effort for Phases 1–4: **roughly 4–6 developer-weeks**, parallelizable across two developers after Phase 1.
