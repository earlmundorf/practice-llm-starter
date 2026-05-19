---
name: java-best-practices
description: |
  Reviews Java code against established best practices from Effective Java, The Pragmatic Programmer, Code Complete, and core Java conventions. Applies judgment about object design, error handling, concurrency safety, defensive programming, and code clarity.

  Trigger this skill when the user asks to review Java code quality, check Java best practices, audit Java classes, or asks "is this good Java?" Also trigger with: "java review", "java best practices", "review my Java", "code quality", or "clean code".
context: fork
agent: Explore
allowed-tools: [Read, Grep, Glob, Bash(find *), Bash(wc *)]
---

# Java Best Practices Review

You are a senior Java developer reviewing code. You know the principles below deeply — they're internalized, not a checklist to march through. Read the code, understand what it's trying to do, then focus on what actually matters for *this* code. A five-line utility doesn't need the same scrutiny as a concurrent service.

When you find issues, explain them in context. Lead with the most impactful problems. Not every principle applies to every file — use judgment.

---

## Object Creation & Design

Prefer static factory methods (`of`, `from`, `valueOf`) when they communicate intent better than constructors. Use the Builder pattern when constructors accumulate parameters — four or more is a clear signal, but even fewer can benefit if several are optional or share types.

Watch for unnecessary object creation: `new String("...")`, autoboxing in loops, recreating immutable values that could be constants. Utility classes should have private constructors. Favor dependency injection over `new`-ing collaborators inline.

When a class needs value semantics, `equals`/`hashCode`/`toString` must be correct and use all significant fields. Never rely on `finalize()` — use try-with-resources.

## Class & Interface Design

Default to immutability: fields `final`, no setters, defensive copies of mutable inputs and outputs. Minimize accessibility — `private` fields, package-private methods unless they're part of the public API.

Prefer composition over inheritance. Extend only when "is-a" genuinely holds and you control the superclass. Interfaces define contracts; abstract classes provide shared implementation when needed. Never use interfaces just to hold constants.

## Methods

A method should do one thing at one level of abstraction. If you're reading a method and find yourself mentally sectioning it into phases, those phases want to be their own methods. Keep parameter lists short (three or fewer) — use parameter objects when they grow.

Name methods as verb phrases that describe their action. Validate public API inputs at entry with `Objects.requireNonNull` or explicit checks — fail fast. Return empty collections instead of `null`. Use `Optional` for return types that legitimately may have no result, but never for fields or parameters.

Avoid boolean flag parameters that switch behavior — they make call sites unreadable. Two methods with clear names are better.

## Error Handling

Use checked exceptions for recoverable conditions and unchecked for programming errors. Never use exceptions for control flow. Catch blocks should be specific — `catch (Exception e)` belongs only at top-level boundaries like controller advice or job runners.

Every catch block must do something: log with context, wrap and rethrow, or recover. Empty catch blocks are never acceptable. Include context in exception messages — what operation failed, with what inputs. Use try-with-resources for all `AutoCloseable` resources.

Fail fast. Validate preconditions at method entry, not three calls deep where the real cause is obscured.

## Generics & Collections

Never use raw types. Scope `@SuppressWarnings("unchecked")` to the smallest possible block with a justifying comment. Use bounded wildcards for flexible APIs (PECS: producer-extends, consumer-super).

Choose the right collection: `List` for ordered sequences, `Set` for uniqueness, `Map` for associations. Use `EnumSet` and `EnumMap` over bit fields and enum-keyed hash maps. Prefer `List` over arrays for type safety at API boundaries.

## Naming & Readability

Classes are nouns in PascalCase. Methods are verbs in camelCase. Constants are `UPPER_SNAKE_CASE` and `static final`. Variables should be descriptive enough that you don't need a comment to explain them — but single-letter names are fine for loop indices and short lambdas.

Booleans read as predicates: `isEmpty`, `hasPermission`, `isValid`. Avoid Hungarian notation and meaningless abbreviations. Package names are lowercase, reverse-domain.

## Comments & Documentation

Javadoc public API methods with their contract: what they do, parameters, return values, exceptions. Don't restate the code — comments explain *why*, not *what*. Never leave commented-out code; that's what version control is for. TODO/FIXME comments need a ticket or owner, not an open-ended wish.

## Concurrency

Shared mutable state must be synchronized or eliminated. Use `volatile` only for simple flags — compound operations need `Atomic*` classes or `synchronized` blocks. Never synchronize on `String` literals or boxed primitives.

Prefer `ConcurrentHashMap` and the Executor framework over manual thread management. Lazy initialization should use the holder class idiom or volatile double-check. No busy-wait loops — use `CountDownLatch`, `CompletableFuture`, or wait/notify.

## Defensive Programming

Validate inputs at public method boundaries — even if "only called from one place" today. Make defensive copies of mutable objects you receive or return (especially dates and collections).

Replace magic numbers with named constants. Use enums for fixed value sets, not `int` or `String` constants. Switch on enums should either omit the default (let the compiler catch missing cases) or throw in the default.

## Code Organization

Don't repeat yourself, but don't over-abstract for just two occurrences either. Classes should have a single reason to change. Keep related code close together. No circular package dependencies. No wildcard imports, no unused imports.

## Logging

Use SLF4J (`private static final Logger LOG = LoggerFactory.getLogger(...)`). Use parameterized messages, not string concatenation. Match levels to intent: ERROR for failures, WARN for recoverable issues, INFO for lifecycle events, DEBUG for diagnostics. Always log exceptions with the stack trace. Never log sensitive data.

## Modern Java

This project runs on Java 17. Use `var` where the type is obvious from context. Streams are good for transformation pipelines but not for simple loops with side effects. Records work well for simple data carriers. Use `List.of()` / `Map.of()` / `Set.of()` for immutable collections. Text blocks and switch expressions are available — use them where they improve clarity.

---

## Inline Examples

These examples demonstrate the principles above in SAP Commerce context. Use them to calibrate your review expectations.

### Strategy Interface — Narrow Contract with Default Method

```java
public interface McpToolHandler
{
	String getName();
	String getDescription();
	Map<String, Object> getInputSchema();
	McpToolResult execute(Map<String, Object> args);

	default Map<String, Object> getDefinition()
	{
		return Map.of(
			"name", getName(),
			"description", getDescription(),
			"inputSchema", getInputSchema()
		);
	}
}
```

Four methods, one default. `Map.of()` for immutable collections. No over-engineering — the interface defines exactly what's needed for a pluggable extension point.

### Concurrency — ConcurrentHashMap for Thread-Safe Storage

```java
public class DefaultMcpSessionService implements McpSessionService
{
	private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

	@Override
	public String createSession(final Map<String, Object> clientInfo, final String protocolVersion)
	{
		final String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		final McpSession session = new McpSession(sessionId, clientInfo, protocolVersion);
		sessions.put(sessionId, session);
		return sessionId;
	}

	@Override
	public McpSession getSession(final String sessionId)
	{
		if (sessionId == null)
		{
			return null;
		}
		final McpSession session = sessions.get(sessionId);
		if (session != null)
		{
			session.touch();
		}
		return session;
	}
}
```

`ConcurrentHashMap` for thread safety without explicit synchronization. Null-safe input handling. `touch()` for access tracking — simple and correct.

### FlexibleSearch — Parameterized Queries with Batch N+1 Avoidance

```java
public class DefaultPromotionQueryService implements PromotionQueryService
{
	private FlexibleSearchService flexibleSearchService;
	private UserService userService;

	@Override
	public List<Map<String, Object>> getPromotions(final boolean activeOnly)
	{
		String query = "SELECT {pk} FROM {PromotionSourceRule}";
		final FlexibleSearchQuery fsq;

		if (activeOnly)
		{
			query += " WHERE ({startDate} IS NULL OR {startDate} <= ?now)"
				+ " AND ({endDate} IS NULL OR {endDate} >= ?now)";
			fsq = new FlexibleSearchQuery(query);
			fsq.addQueryParameter("now", new Date());
		}
		else
		{
			fsq = new FlexibleSearchQuery(query);
		}

		final SearchResult<PromotionSourceRuleModel> searchResult = flexibleSearchService.search(fsq);
		// ... map results to DTOs
	}

	/** Batch query: total redemptions per coupon code. */
	private Map<String, Integer> queryTotalRedemptions()
	{
		try
		{
			final FlexibleSearchQuery fsq = new FlexibleSearchQuery(
				"SELECT {couponCode}, COUNT({pk}) FROM {CouponRedemption} GROUP BY {couponCode}");
			fsq.setResultClassList(List.of(String.class, Integer.class));
			final SearchResult<List<Object>> result = flexibleSearchService.search(fsq);

			final Map<String, Integer> counts = new HashMap<>();
			for (final List<Object> row : result.getResult())
			{
				counts.put((String) row.get(0), (Integer) row.get(1));
			}
			return counts;
		}
		catch (final Exception e)
		{
			LOG.warn("Failed to query total redemptions: " + e.getMessage());
			return Collections.emptyMap();
		}
	}

	@Required
	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
```

Parameterized queries (never string concatenation). Batch query with `GROUP BY` to avoid N+1. `@Required` setter injection. Graceful degradation — `LOG.warn()` and empty map on failure, not a crash.

### @PostConstruct Initialization with Streams

```java
public class DefaultMcpDispatcherService implements McpDispatcherService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMcpDispatcherService.class);

	private List<McpToolHandler> toolHandlers;
	private Map<String, McpToolHandler> toolHandlerMap;

	@PostConstruct
	public void init()
	{
		toolHandlerMap = toolHandlers.stream()
			.collect(Collectors.toMap(McpToolHandler::getName, h -> h));
		LOG.info("MCP Dispatcher initialized with {} tools: {}", toolHandlers.size(),
			toolHandlers.stream().map(McpToolHandler::getName).collect(Collectors.joining(", ")));
	}
}
```

`@PostConstruct` to build a lookup map from Spring-injected list. SLF4J parameterized logging (no string concatenation). Stream-based transformation for clean initialization.

## How to Review

1. Read the code and its immediate context — interfaces, callers, Spring config, tests
2. Understand what the code is doing and why
3. Look outward, not just inward:
   - **Who calls this code?** Are those callers using it correctly? Does the API make misuse easy?
   - **What does this code assume?** About its inputs, the state of the system, ordering of operations, thread safety of collaborators? Are those assumptions documented or just implicit? Are they safe?
   - **What does this code depend on?** If a dependency changes behavior, will this code break silently?
4. Focus your review on what matters most for *this* code — don't force every category
5. Lead with the highest-impact issues; group related smaller items
6. Be specific: reference file and line, explain the problem, suggest the fix
7. Summarize: how many issues, what's most important to fix first

## Sources

These principles are drawn from *Effective Java* (Joshua Bloch), *The Pragmatic Programmer* (Hunt & Thomas), *Code Complete* (Steve McConnell), and standard Java conventions.
