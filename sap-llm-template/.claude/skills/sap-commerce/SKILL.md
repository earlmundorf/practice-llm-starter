---
name: sap-commerce
description: |
  SAP Commerce Cloud (Hybris) development assistant. Provides deep knowledge of SAP Commerce architecture, coding patterns, and tooling — including the type system (items.xml), ImpEx data import/export, FlexibleSearch queries, Spring configuration, extension development, the Service/Facade/Controller layered architecture, OCC REST APIs, and build/deployment with Ant and Gradle.

  Use this skill whenever the user mentions SAP Commerce, Hybris, SAP CX, Commerce Cloud, or any of these SAP Commerce concepts: items.xml, ImpEx, FlexibleSearch, extensions, backoffice, HAC, OCC API, Spartacus, SmartEdit, accelerator, storefront, cronjob, solr indexing, type system, or hybris platform. Also trigger when the user is working with Java files that import from `de.hybris.platform.*` packages, or XML files following hybris conventions (*-items.xml, *-spring.xml, *-beans.xml).
allowed-tools: [Read, Grep, Glob, Bash(find *), Bash(ant *)]
---

# SAP Commerce Cloud Development Skill

You are an expert SAP Commerce Cloud (Hybris) developer. You understand the full platform stack — from the type system and persistence layer through services and facades to OCC REST endpoints and Spartacus storefronts.

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
2. **ant build** — Regenerate models (`ant clean all`)
3. **-beans.xml** — Add matching field to the DTO bean (if exposing via facade)
4. **Populator** — Write a populator to copy Model→DTO
5. **-spring.xml** — Register the populator bean and wire it into the converter
6. **ImpEx** — Load sample/test data for the new attribute
7. **FlexibleSearch** — Update any queries if needed
8. **Controller/OCC** — Expose via REST if needed

### Build Commands

```bash
# Full rebuild (after items.xml changes)
ant clean all

# Initialize (reset DB + load data)
ant initialize

# Update system (apply changes without data loss)
ant updatesystem

# Run tests
ant unittests -Dtestclasses.packages=com.company.*
ant integrationtests

# Generate a new extension
ant extgen
```

### Common Pitfalls

- **Editing gensrc/** — Never. These files are regenerated on every build.
- **Skipping `ant clean all`** after items.xml changes — Models won't match the type system.
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

- **`docs/`** (project root) — Generic patterns: extension setup, architecture diagrams, new feature walkthrough and checklist
- **`bin/custom/<extension>/docs/`** — Extension-specific feature flows

Each feature flow subdirectory contains three files:

- **`context.md`** — What the flow does, when it's used, key decisions
- **`components.md`** — The files that implement it and what each one does
- **`diagram.md`** — Mermaid diagrams with descriptive context

**Before working on a feature,** read its flow directory. **When adding a new feature,** create a new flow directory first (docs before code).

### Project Configuration Files

| What | File | Why read it |
|---|---|---|
| Active extensions | `config/localextensions.xml` | Which extensions are loaded and their dependency order |
| Runtime config | `config/local.properties` | Database, ports, CORS, logging, Solr settings |
| Extension metadata | `bin/custom/llmtemplate/extensioninfo.xml` | How an extension declares its name, dependencies, and modules |
| Extension properties | `bin/custom/llmtemplate/project.properties` | Spring context loading and web module registration |
| Core Spring beans | `bin/custom/llmtemplate/resources/llmtemplate-spring.xml` | Service/facade bean definitions with alias pattern |
| Web Spring context | `bin/custom/llmtemplate/resources/occ/v2/llmtemplateocc/web/spring/llmtemplate-web-spring.xml` | Controller component scanning |
| Type definitions | `bin/custom/llmtemplate/resources/llmtemplate-items.xml` | Where custom types, attributes, and enums go |
| DTO definitions | `bin/custom/llmtemplate/resources/llmtemplate-beans.xml` | Where Hybris bean DTOs are defined |
| ImpEx data | `bin/custom/sampledatamcp/resources/impex/` | Store infrastructure, Solr config, sample data |

When adding new beans, use the alias pattern: `<alias name="defaultMyService" alias="myService"/>`.

### Platform Modules (OOTB reference)

| Module | Path | What to learn |
|---|---|---|
| Commerce services | `bin/modules/commerce-services/commerceservices/` | Cart, checkout, order, customer services |
| Base commerce | `bin/modules/base-commerce/basecommerce/` | Base types (payment, voucher, promotions) |
| OCC infrastructure | `bin/modules/commerce-services/commercewebservicescommons/` | REST API patterns, error handling, DTO mapping |

### How to Use This Section

**For Claude:** Before writing any new code, read the relevant config files listed above and the inline examples in the java-best-practices and sap-best-practices skills. Match the project's existing patterns for naming, package structure, Spring wiring, and annotation style.

**For the developer:** These file paths are relative to the `hybris/` directory. Trace the chain: Controller → Facade → Service → Spring XML → items.xml.
