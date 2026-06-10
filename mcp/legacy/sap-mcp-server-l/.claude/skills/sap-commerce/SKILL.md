---
name: sap-commerce
description: |
  SAP Commerce Cloud (Hybris) development knowledge base and coding assistant. Provides deep reference knowledge of SAP Commerce architecture, coding patterns, and tooling — including the type system (items.xml), ImpEx data import/export, FlexibleSearch queries, Spring configuration, extension development, the Service/Facade/Controller layered architecture, OCC REST APIs, and build/deployment with Gradle. Use this skill for "how do I do X in Commerce?" questions and hands-on coding tasks. For structured ticket workflows (research/design/plan/implement), use commerce-qrspi instead.

  Use this skill whenever the user asks how to do something in SAP Commerce, needs help writing or modifying Commerce code, or mentions SAP Commerce, Hybris, SAP CX, Commerce Cloud, or any of these concepts: items.xml, ImpEx, FlexibleSearch, extensions, backoffice, HAC, OCC API, Spartacus, SmartEdit, accelerator, storefront, cronjob, solr indexing, type system, or hybris platform. Also trigger when working with Java files that import from `de.hybris.platform.*` packages, or XML files following hybris conventions (*-items.xml, *-spring.xml, *-beans.xml). Do NOT trigger for structured ticket workflows — use commerce-qrspi (Claude Code) or commerce-rpi-cowork (Cowork) for those.
allowed-tools: [Read, Grep, Glob, Bash(find *), Bash(ant *)]
---

# SAP Commerce Cloud Development Skill

You are an expert SAP Commerce Cloud (Hybris) developer. You understand the full platform stack — from the type system and persistence layer through services and facades to OCC REST endpoints and Spartacus storefronts.

**This repository's CLAUDE.md is authoritative.** Where this skill's generic guidance differs from the project's rules — build commands (`yclean` is mandatory; `yupdatesystem` runs with the server stopped), test scoping (`testCustomExtensions` / direct ant, never gradle `-D` passthrough), config editing (`dev-config/`, never the generated `hybris/config/`), ImpEx naming (numeric prefixes) — follow CLAUDE.md.

## When to Use Reference Files

This skill bundles detailed reference docs. Read the relevant ones based on what the user needs:

| User's task | Read this reference |
|---|---|
| Creating/modifying extensions, understanding project structure | `references/architecture.md` |
| Writing or debugging items.xml, type system, relations, enums | `references/items-xml.md` |
| Writing ImpEx scripts for data import/export | `references/impex.md` |
| Writing or optimizing FlexibleSearch queries | `references/flexiblesearch.md` |
| Spring bean config, dependency injection, aliasing | `references/spring-config.md` |
| Code quality, layer separation, naming, performance | `references/best-practices.md` |

Read the reference file **before** writing code for that topic. For complex tasks spanning multiple areas (e.g., "add a new product attribute end-to-end"), read all relevant references.

## Core Architecture

SAP Commerce uses a layered architecture within modular **extensions**:

```
┌──────────────────────────────────────┐
│  Controller (@Controller)            │  HTTP/REST request handling
├──────────────────────────────────────┤
│  Facade (+ Converters/Populators)    │  Model→DTO conversion, orchestration
├──────────────────────────────────────┤
│  Service (@Service)                  │  Business logic, transactions
├──────────────────────────────────────┤
│  DAO (FlexibleSearch)                │  Data access, queries
├──────────────────────────────────────┤
│  Type System (items.xml → Models)    │  Persistent entities, generated code
└──────────────────────────────────────┘
```

Each layer has a distinct responsibility. Never skip layers — a controller should call a facade, not a DAO directly. This separation enables omni-channel reuse (same facade/service for web, mobile, OCC).

## Extension Structure

Every SAP Commerce extension follows this layout:

```
my-extension/
├── extensioninfo.xml              # Metadata, dependencies
├── project.properties             # Extension config
├── buildcallbacks.xml             # Custom Ant build hooks
├── resources/
│   ├── myextension-items.xml      # Type system definitions
│   ├── myextension-spring.xml     # Spring bean definitions
│   ├── myextension-beans.xml      # DTO/VO definitions (Hybris beans)
│   └── impex/                     # ImpEx data scripts
├── src/                           # Hand-written Java
│   └── com/company/extension/
│       ├── constants/
│       ├── services/
│       │   └── impl/
│       ├── facades/
│       │   ├── impl/
│       │   └── data/              # Facade DTOs (*Data classes)
│       ├── daos/
│       │   └── impl/
│       └── controllers/
├── gensrc/                        # AUTO-GENERATED (from items.xml, don't edit)
├── testsrc/                       # Tests
├── web/                           # Web module (if applicable)
│   └── webroot/WEB-INF/
│       └── config/spring-*.xml    # Web Spring context
└── lib/                           # External JARs
```

## Key Concepts Quick Reference

### Adding a New Attribute End-to-End

This is the most common task. The full flow is:

1. **items.xml** — Define the attribute on the item type
2. **Build** — Regenerate models (`./gradlew yclean ybuild`)
3. **-beans.xml** — Add matching field to the DTO bean (if exposing via facade)
4. **Populator** — Write a populator to copy Model→DTO
5. **-spring.xml** — Register the populator bean and wire it into the converter
6. **ImpEx** — Load sample/test data for the new attribute
7. **FlexibleSearch** — Update any queries if needed
8. **Controller/OCC** — Expose via REST if needed

### Build Commands

All commands run from `core-customize/` using `./gradlew`.

```bash
# Build + restart after code changes (yclean is mandatory — incremental
# compile silently skips recompiles in this project)
./gradlew yclean ybuild stopServer startServer

# After items.xml changes — yupdatesystem runs with the server STOPPED
./gradlew yclean ybuild stopServer yupdatesystem startServer

# Initialize (reset DB + load data — destroys existing data)
./gradlew yinitialize

# Run tests (never the gradle yunittests/yintegrationtests passthrough
# with -D flags — the plugin drops them and runs the whole platform suite)
./gradlew testCustomExtensions    # unit tests, all custom extensions
cd hybris/bin/platform && . ./setantenv.sh && ant integrationtests -Dtestclasses.extensions=coremcp

# HAC console commands
./gradlew impex -Pfile=<path>
./gradlew flexquery -Pfile=<path>
./gradlew groovy -Pfile=<path> [-Pcommit=true]
```

### Common Pitfalls

- **Editing gensrc/** — Never. These files are regenerated on every build.
- **Skipping `yclean`** before `ybuild` — stale `.class` files deploy silently; after items.xml changes the models won't match the type system.
- **Editing `hybris/config/`** — it's generated; edit `core-customize/dev-config/` and re-run `setupConfig`.
- **Injecting DAOs into controllers** — Always go through Service→Facade layers.
- **Using Jalo layer** — The Jalo layer is deprecated. Use the ServiceLayer (Models, Services, DAOs).
- **Hardcoding catalog versions** — Use `catalogVersion(catalog(id),version)` in ImpEx and parameterized queries in Java.
- **Not using interfaces** — Every Service, Facade, and DAO should have an interface. Implementations use `Default*` prefix.

## Naming Conventions

| Artifact | Convention | Example |
|---|---|---|
| Extension | lowercase, hyphenated | `myproject-core` |
| Service interface | `*Service` | `ProductEnrichmentService` |
| Service impl | `Default*Service` | `DefaultProductEnrichmentService` |
| Facade interface | `*Facade` | `ProductEnrichmentFacade` |
| Facade impl | `Default*Facade` | `DefaultProductEnrichmentFacade` |
| DAO interface | `*Dao` | `ProductEnrichmentDao` |
| DAO impl | `Default*Dao` | `DefaultProductEnrichmentDao` |
| DTO (facade) | `*Data` | `ProductEnrichmentData` |
| DTO (web/OCC) | `*WsDTO` | `ProductEnrichmentWsDTO` |
| Spring bean ID | camelCase, interface name | `productEnrichmentService` |
| Populator | `*Populator` | `ProductEnrichmentPopulator` |
| Converter | `*Converter` | `productEnrichmentConverter` (bean ID) |

## Codebase Reference — Learn from the Code

### Project Documentation

Documentation is split across two locations:

- **`docs/`** (project root) — Generic guides, `extending/` checklists, `adr/` (decision records), `review/` (architecture review + improvement plan)
- **`bin/custom/<extension>/docs/`** — README index + feature-flow directories + `reference/` for cross-cutting flat docs (tools, endpoints, solr, llm-providers)

Each feature flow subdirectory contains exactly three files:

- **`context.md`** — What the flow does, when it's used, key decisions
- **`components.md`** — The files that implement it and what each one does
- **`diagram.md`** — Mermaid diagrams with descriptive context

**Before working on a feature,** read its flow directory. **When adding a new feature,** create a new flow directory first (docs before code). When changing tool schemas, endpoints, or config properties, update the matching `reference/` doc in the same commit.

### Source Files Quick Reference

### Configuration

| What | File | Why read it |
|---|---|---|
| Active extensions (editable source) | `core-customize/dev-config/localextensions.xml` | Which extensions are loaded and their dependency order |
| Runtime config (editable source) | `core-customize/dev-config/local.properties` | Database, ports, CORS, logging, Solr settings. The copies under `hybris/config/` are generated — never edit them |
| Extension metadata | `bin/custom/coremcp/extensioninfo.xml` | How an extension declares its name, dependencies, and modules |
| Extension properties + tunables | `bin/custom/coremcp/project.properties` | Spring context loading, web module registration, and all `coremcp.*` operational defaults |

### Type System & Data Model

| What | File | Why read it |
|---|---|---|
| items.xml example | `bin/custom/coremcp/resources/coremcp-items.xml` | How to define custom types, attributes, and enums |
| DTO/VO definitions | `bin/custom/coremcp/resources/coremcp-beans.xml` | How Hybris bean DTOs are defined (code-generated value objects) |

### Spring Configuration

| What | File | Why read it |
|---|---|---|
| Core Spring beans | `bin/custom/coremcp/resources/coremcp-spring.xml` | Service/facade bean definitions with alias pattern |
| Web Spring context | `bin/custom/coremcp/resources/commercewebservices/v2/additional-web-spring-context.xml` | Controller component scanning via the OCC v2 servlet's classpath hook |

When adding new beans, use the alias pattern: `<alias name="defaultMyService" alias="myService"/>`.

### Java — Layered Architecture Examples

Each layer has a working example in the `coremcp` extension:

**Controller Layer** (HTTP handling → calls Service/Dispatcher)
- `bin/custom/coremcp/src/com/coremcp/controllers/McpController.java` — JSON-RPC protocol handling, `@Resource` injection, error handling

**Service Layer** (business logic, session management, dispatching)
- Interface: `bin/custom/coremcp/src/com/coremcp/services/McpSessionService.java`
- Implementations: `impl/PersistedMcpSessionService.java` (DB-backed default), `impl/DefaultMcpSessionService.java` (in-memory), selected by `impl/DelegatingMcpSessionService.java` via `coremcp.session.store`
- Interface: `bin/custom/coremcp/src/com/coremcp/services/McpDispatcherService.java`
- Implementation: `bin/custom/coremcp/src/com/coremcp/services/impl/DefaultMcpDispatcherService.java`

**Strategy Pattern** (tool handlers as pluggable components)
- Interface: `bin/custom/coremcp/src/com/coremcp/tools/McpToolHandler.java`
- Implementations: `bin/custom/coremcp/src/com/coremcp/tools/impl/` (20 tool handlers)

**Data Access** (FlexibleSearch queries)
- Interface: `bin/custom/coremcp/src/com/coremcp/services/PromotionQueryService.java`
- Implementation: `bin/custom/coremcp/src/com/coremcp/services/impl/DefaultPromotionQueryService.java`

### Platform Modules (OOTB reference)

| Module | Path | What to learn |
|---|---|---|
| Commerce services | `bin/modules/commerce-services/commerceservices/` | Cart, checkout, order, customer services |
| Base commerce | `bin/modules/base-commerce/basecommerce/` | Base types (payment, voucher, promotions) |
| OCC infrastructure | `bin/modules/commerce-services/commercewebservicescommons/` | REST API patterns, error handling, DTO mapping |

### How to Use This Section

**For Claude:** Before writing any new code, read the relevant example files listed above. Match the project's existing patterns for naming, package structure, Spring wiring, and annotation style.

**For the developer:** These file paths are relative to the `hybris/` directory. Trace the chain: Controller → Facade → Service → Spring XML → items.xml.
