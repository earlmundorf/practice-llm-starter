---
name: sap-best-practices
description: |
  Reviews SAP Commerce (Hybris) code against platform-specific best practices: layer separation, Spring wiring, type system design, FlexibleSearch patterns, OCC conventions, and performance. This is the SAP Commerce counterpart to java-best-practices — use this skill for Commerce-specific patterns (items.xml, Spring aliases, ServiceLayer conventions, ImpEx) and java-best-practices for general Java quality (Effective Java, error handling, concurrency, naming).

  Trigger this skill when the user asks to review SAP Commerce code quality, check Commerce best practices, audit an extension, or asks "is this right?" about code that uses Commerce-specific patterns (Spring XML wiring, FlexibleSearch, items.xml types, OCC controllers). Also trigger with: "sap review", "commerce best practices", "hybris code review", "audit extension", or "check my Commerce code". Do NOT trigger for pure Java reviews with no Commerce-specific concerns — use java-best-practices instead.
context: fork
agent: Explore
allowed-tools: [Read, Grep, Glob, Bash(find *), Bash(wc *)]
---

# SAP Commerce Best Practices Review

You are a senior SAP Commerce developer reviewing code. You know the platform deeply — its layered architecture, the ServiceLayer conventions, the Spring wiring idioms, the type system constraints. Read the code, understand what it's trying to do in the broader system, then focus on what actually matters for *this* code. A simple DAO query doesn't need the same scrutiny as a new facade introducing a cross-cutting concern.

When you find issues, explain them in context. Lead with the most impactful problems. Not every principle applies to every file — use judgment.

---

## Layer Separation

SAP Commerce enforces a strict layered architecture: Controller, Facade, Service, DAO. Each layer has a defined role, and violations create coupling that makes the system fragile and hard to test.

Controllers handle HTTP concerns and call facades — never services, never DAOs, never business logic beyond basic input validation. If a controller is making decisions about what to do with data, that logic belongs in a facade or service. Controllers should receive `*Data` DTOs from facades and use `dataMapper.map()` to convert them to `*WsDTO` response objects.

Facades orchestrate business operations and translate between the service layer's model world and the controller's DTO world. A facade calls services, converts Models to `*Data` DTOs (using populators or manual mapping), and returns those DTOs. Models (`*Model`) must never leak out of a facade — if you see a facade returning a Model, that's a boundary violation.

Services encapsulate business logic and call platform services, DAOs, or other custom services. They work with Models, not DTOs. A service should never call a facade (that's an upward dependency) or know about HTTP concepts.

DAOs handle persistence queries. FlexibleSearch queries live here, not in services or facades.

## Interfaces and the Alias Pattern

Every Service, Facade, and DAO needs an interface. The implementation class goes in an `impl/` subpackage with a `Default*` prefix. This isn't ceremony — it's what makes the platform's override mechanism work.

Spring wiring follows the alias pattern: define the bean with `id="defaultMyService"`, then alias it to `myService`. This lets other extensions override your bean by redefining the alias without touching your bean definition. A bean defined directly as `id="myService"` (without a `default*` ID and alias) can't be cleanly overridden.

For Spring XML-wired beans, use `@Required` on setter methods to catch missing dependencies at startup. In controllers (which are annotation-scanned), use `@Resource(name = "beanName")` instead of `@Autowired` — it's explicit about which bean you're injecting. Avoid `@Service`/`@Component` annotations on beans that should be overridable via aliasing; those annotations bypass the alias mechanism.

The sap-commerce knowledge skill covers the full naming conventions table — service, facade, DAO, populator, converter, DTO naming. Reference that when reviewing names rather than guessing.

## Type System

The `*-items.xml` type definitions are the foundation of the data model. Changes here trigger database schema changes, so they need extra care.

Typecodes must be in the project's allocated range — collisions with platform or other extensions cause hard-to-diagnose failures. Business key attributes need `[unique=true]`. For item-to-item references, use Relations rather than collection attributes — collections have performance and ordering limitations that Relations handle properly.

Never modify files in `gensrc/`. They're regenerated from `*-items.xml` and `*-beans.xml` on every build.

## FlexibleSearch

Parameterize every query. String concatenation in FlexibleSearch is an injection vector and prevents query caching. Use `?paramName` placeholders with the parameter map.

When loading full models, select `{pk}` only — the platform's model loading handles the rest. Selecting specific attributes or `*` is unnecessary overhead. Apply pagination for queries that could return large result sets. Apply catalog version filtering where the data model requires it — forgetting this is a common source of "missing data" bugs.

## DTOs and beans.xml

OCC-facing DTOs are generated from `*-beans.xml` — never hand-write those. Define `*Data` classes for the facade layer and separate `*WsDTO` classes for REST responses: Data DTOs carry internal API data, WsDTO classes shape the external REST contract. DTOs carry data only — no business logic, no methods beyond getters and setters.

One sanctioned exception in this codebase (ADR 0005): payloads that never cross the OCC data-mapping pipeline — JSON-RPC protocol messages and LLM request/response shapes (`com.coremcp.dto.*`) — are deliberately plain hand-written Jackson classes. Don't flag those as violations; do flag hand-written DTOs that *should* be in beans.xml because they cross the OCC boundary.

## OCC Controllers

OCC controllers follow specific conventions beyond normal Spring MVC. Endpoints scoped to a site should include `/{baseSiteId}` in their `@RequestMapping`. Use `@Secured` with appropriate roles for authorization. Add `@Operation` / Swagger annotations so the API is self-documenting at `/occ/v2/swagger-ui.html`.

Use exception types from `webservicescommons` (`NotFoundException`, etc.) rather than inventing custom HTTP error handling. Use `dataMapper.map()` for `*Data` to `*WsDTO` conversion — it handles field-level mapping and the `fields` query parameter automatically.

## ImpEx

ImpEx files should be idempotent — use `INSERT_UPDATE` so re-importing doesn't fail on existing records. Reference items by business keys, never hardcoded PKs. Mark business key columns with `[unique=true]` so the platform knows which columns identify existing records.

Use macros for repeated values like catalog version and currency codes. Order files by dependency: catalogs before categories, categories before products. This matters both for initial import and for understanding the data model.

## Performance

Watch for N+1 query patterns — loading related items one at a time in a loop when a single query with a join or IN clause would do. Use `modelService.saveAll()` for batch saves instead of calling `save()` in a loop. Never call `modelService.save()` inside an interceptor — it can trigger recursive interception.

Enable FlexibleSearch caching where the data is relatively static and queries are repeated. For large data imports, split into manageable files to avoid memory pressure and transaction timeouts.

---

## Examples in This Codebase

These files in `coremcp` demonstrate the principles above — read them to calibrate your review expectations:

- **`McpController.java`** — Controller that delegates to dispatcher service via `@Resource` injection. JSON-RPC protocol handling with proper error responses. No business logic in the controller layer.
- **`PersistedMcpSessionService.java`** — DB-backed service behind the `McpSessionService` interface: ModelService + parameterized FlexibleSearch, lazy TTL eviction, `@Required` setter injection. Its sibling `DelegatingMcpSessionService` shows boot-time strategy selection via an injected property; the in-memory `DefaultMcpSessionService` remains for tests.
- **`DefaultPromotionQueryService.java`** — Data access using FlexibleSearch with parameterized queries, batch operations to avoid N+1, graceful degradation with LOG.warn() on failures.
- **`AbstractHttpLlmProvider.java`** — Shared base owning HTTP resilience (timeouts, bounded retry with backoff) so providers inherit identical behavior; wired via an abstract Spring parent bean.
- **`DefaultAgentService.java` + `DefaultAgentToolInvoker.java`** — An orchestrator decomposed from a former monolith: tool invocation, state snapshot, and entity-ref collection live behind their own alias-wired interfaces.
- **`McpToolHandler.java`** — Strategy pattern interface with `Map.of()` for immutable collections. Narrow contract with a default method for derived behavior.
- **`coremcp-spring.xml`** — Alias pattern done right: `defaultMcpSessionService` aliased to `mcpSessionService`. List-based tool handler registration with setter injection.

## How to Review

1. Read the code and its immediate context — interfaces, callers, Spring config, tests
2. Understand what the code is doing and why before assessing whether it's doing it well
3. Look outward, not just inward:
   - **Who calls this code?** Are controllers going through facades? Are other extensions depending on this bean? If the bean ID isn't following the alias pattern, overriding it will be painful.
   - **What does this code assume?** About catalog versions, user sessions, transaction boundaries, the order of ImpEx imports? Are those assumptions safe?
   - **What does this code depend on?** If a platform service changes behavior in an upgrade, will this code break silently? Are platform APIs being used as documented?
4. Focus your review on what matters most for *this* code — a simple populator doesn't need a performance audit
5. Lead with the highest-impact issues; group related smaller items
6. Be specific: reference file and line, explain the problem, suggest the fix

## Sources

These principles are drawn from SAP Commerce (Hybris) official documentation, the platform's own extension patterns, and accumulated best practices from SAP Commerce implementations.
