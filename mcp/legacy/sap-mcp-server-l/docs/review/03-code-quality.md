# Code Quality Review

Two independent passes were run over `coremcp`: one against SAP Commerce platform best practices (layering, wiring, FlexibleSearch, type system) and one against general Java engineering standards (Effective Java-style design, error handling, concurrency, HTTP clients, testing). This document merges both. File paths are relative to `core-customize/hybris/bin/custom/coremcp/`.

## 1. What is genuinely good

These are strengths worth defending in any review by SAP or a client architect:

- **Strict layer separation.** Controllers handle HTTP concerns only and delegate to services; commerce operations go through platform facades; FlexibleSearch is confined to a dedicated query service. No business logic leaks upward or downward.
- **The tool handler strategy pattern.** `McpToolHandler` is a lean interface (`getName/getDescription/getInputSchema/execute`); ~20 thin handlers wrap facades with MCP metadata. Adding a tool is one class + one Spring list entry. Tool definitions are built once at `@PostConstruct` and reused.
- **Parameterized, batched FlexibleSearch.** `DefaultPromotionQueryService` uses `addQueryParameter` everywhere (injection-safe, cache-friendly) and loads coupon redemption counts in two batch queries instead of per-coupon lookups — deliberate N+1 avoidance.
- **A defensible agent loop.** `DefaultAgentService` bounds tool iterations (max 10), detects duplicate tool calls (same tool + same args in one turn is skipped — prevents LLM-induced infinite loops), truncates large tool results in conversation history while keeping full results in-loop, prunes image content when the configured provider lacks vision support, and logs per-round LLM and tool timings.
- **Streaming done carefully.** SSE endpoints set the right headers (`Cache-Control`, `X-Accel-Buffering`), flush before the loop, fall back to non-streaming when disabled, and the Anthropic provider drains partial responses on stream failure.
- **Concurrency hygiene.** `ConcurrentHashMap` session store with lazy TTL eviction; services are stateless; handler maps are immutable after init; Java 11+ `HttpClient` instances are correctly shared per provider. No data races were found.
- **Clean codebase.** No `System.out`, no commented-out blocks, parameterized SLF4J logging throughout, one honest TODO (the persisted-session note), stack traces preserved on error logs.

## 2. Findings

### 2.1 High — No read timeout, no retries on LLM HTTP calls

`AbstractOpenAiCompatibleLlmProvider` (~line 31) and `AnthropicLlmProvider` (~line 48) configure only `connectTimeout(Duration.ofSeconds(10))`. There is no request/read timeout, and the streaming path reads the response body in a loop with no time guard. A slow or wedged upstream LLM holds a Tomcat thread **indefinitely**; under load this exhausts the pool. Separately, any transient failure (429 rate limit, brief 502/503) immediately surfaces as an error to the user — there is no retry.

**Fix:** add `.timeout(...)` on each `HttpRequest` (configurable, e.g. 60s non-streaming / 120s streaming), and a small bounded retry with exponential backoff for 429/5xx at the `LlmClient` level. (Phase 2, task 2.1.)

### 2.2 High — Test coverage does not protect the most important code

7 test classes cover ~53 source classes (~13%). What's tested is tested well (`DefaultMcpSessionServiceTest` covers session CRUD + TTL; cart/checkout/info handler tests mock facades properly with meaningful assertions). What's *not* tested is the core:

| Untested | Why it matters |
|---|---|
| `DefaultAgentService` | The multi-turn agent loop — iteration bounds, duplicate detection, result summarization, entity extraction. A regression here breaks the product silently. |
| `AgentController` | SSE streaming, error fallback, cart code propagation |
| `DefaultVisualSearchService` / `VisualSearchController` | Vision JSON parsing, 3-tier catalog search, image validation |
| `KnowledgeController`, ~17 of 20 tool handlers | Request validation, error paths |

This aligns with SAP's own guidance (every service class unit-tested with mocked collaborators, `@UnitTest` for non-platform tests). Phase 3 of the plan targets the agent loop and controllers first — highest risk per test written.

### 2.3 Medium — `DefaultAgentService.runChat()` is a 170-line method

`services/impl/DefaultAgentService.java:142-311` contains the entire loop: message assembly, LLM round-trips, tool dispatch, duplicate detection, streaming callbacks, entity-reference extraction, history summarization. It works and is readable line-by-line, but it is hard to unit test (one reason for finding 2.2) and risky to extend. Decompose into focused collaborators (tool invocation, state-snapshot building, entity-ref collection) — which also unlocks targeted tests. (Phase 3, task 3.1.)

### 2.4 Medium — Pervasive untyped JSON (`Map<String, Object>`)

~254 uses of raw `Map<String, Object>` with cast chains and `@SuppressWarnings("unchecked")` (e.g. `DefaultAgentService:186-194` walking choices→message→tool_calls). Vision analysis parsing strips markdown fences with regex and `readValue(content, Map.class)` (`DefaultVisualSearchService:184`). The defensive null-checking is good, but the shape of every LLM API response is enforced nowhere — a provider API change degrades into silent fallbacks or `ClassCastException`s. Introduce small typed DTOs for the LLM request/response and vision-analysis shapes (plain Jackson classes; these are internal, not `*-beans.xml` generated). (Phase 3, task 3.2.)

### 2.5 Medium — Duplicated HTTP plumbing across LLM providers

`AnthropicLlmProvider` (651 lines, standalone) re-implements request construction, status validation, error wrapping, and streaming fallback that `AbstractOpenAiCompatibleLlmProvider` provides for the other two providers — with subtly different behavior (Anthropic drains partial streams; OpenAI-compatible doesn't). Extract a common `HttpLlmProvider` base owning the HttpClient, timeout/retry policy, and fallback semantics. (Phase 2, task 2.2 — do together with 2.1 so resilience lands once, not three times.)

### 2.6 Medium — Hardcoded operational values

| Value | Location | Should be |
|---|---|---|
| `MAX_TOOL_ITERATIONS = 10` | `DefaultAgentService:33` | `coremcp.agent.maxToolIterations` |
| Summary thresholds 300/200 | `DefaultAgentService:38-39` | properties |
| Session TTL 30 min | `DefaultMcpSessionService` | `coremcp.session.ttl.minutes` |
| `DEFAULT_MAX_TOKENS = 1024` | `AnthropicLlmProvider:45` | property (1024 is low for an agent turn) |
| `VISION_MODEL = "gpt-4o"` | `DefaultVisualSearchService:37` | property — currently bypasses the provider abstraction entirely |
| `MAX_IMAGE_SIZE = 10MB` | `VisualSearchController:30` | property |
| System/vision prompts (46 and 28 lines) | embedded constants | acceptable short-term; externalize for tuning without rebuilds |

Create `project.properties` defaults for all of these (hybris `Config.getParameter` / `configurationService`). (Phase 1, task 1.3.)

### 2.7 Low/Medium — Endpoint input guards

- `AgentController`: no cap on message count or request size beyond Jackson defaults.
- `KnowledgeController`: no max `pageSize` (a client can request 999999).
- `VisualSearchController`: size and MIME checks are good; base64 validity isn't checked before shipping to the vision model; no rate limiting on an expensive endpoint.

### 2.8 Low — Swallowed exceptions without logging

A handful of `catch (Exception ignored) {}` blocks (e.g. `AgentController:104` SSE write, `DefaultAgentService:250` tool-event consumer) are individually defensible but invisible to operations. Add `LOG.debug` lines; downgrade nothing else. Also: 42 occurrences of broad `catch (Exception e)` where `IOException`/`JsonProcessingException` would document intent.

### 2.9 Low — Naming and dead weight

- `DefaultPromotionQueryService` is comment-described as a "DAO" but is a service returning maps — fix the comments (or rename) for the next reader.
- `coremcp-beans.xml` is empty with a stale "Phase 2 will add DTOs" comment — remove the comment or make it a real TODO.
- `DeepLinkBuilder` lacks an interface/alias (consistency only).

## 3. Scorecard

| Category | Rating |
|---|---|
| Layering & platform patterns | Excellent |
| Spring wiring & overridability | Very good (LLM provider aliases pending) |
| Data access (FlexibleSearch/Solr tiers) | Excellent |
| Concurrency | Excellent (within the single-node design) |
| Protocol implementation (JSON-RPC 2.0 / MCP) | Excellent |
| HTTP client resilience | **Weak** — finding 2.1 |
| Type safety of LLM I/O | Fair — finding 2.4 |
| Method-level design | Good, one outlier — finding 2.3 |
| Test depth | **Weak** — finding 2.2 |
| Logging & cleanliness | Very good |

The shape of this scorecard is the right one to show an architect: the *hard* things (platform integration, layering, concurrency, protocol correctness) are done well; the gaps are the *disciplined-follow-through* things (timeouts, tests, typed DTOs) that a plan can close on a schedule. The reverse profile would be unfixable; this one is just work.
