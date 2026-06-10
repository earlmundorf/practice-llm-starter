# Architecture & Structure Review

This document assesses the repository layout, CCv2 compliance, extension design, and — critically — how the custom code integrates with the SAP Commerce platform. Integration claims below were verified against the actual unpacked 2211 platform and module source under `core-customize/hybris/bin/` (not just inferred from the custom code).

## 1. Repository layout and CCv2 compliance

```
sap-mcp-server-l/
├── core-customize/                  # CCv2 root — cloud builds start here
│   ├── manifest.json                # commerceSuiteVersion 2211.50, useConfig pattern
│   ├── build.gradle                 # sap.commerce.build + sap.commerce.build.ccv2 v5.0.2
│   ├── dev-config/                  # Tracked property sources (overlaid into hybris/config)
│   ├── scripts/                     # Solr indexing, promotions setup
│   └── hybris/
│       ├── bin/custom/coremcp/      # MCP server extension (53 Java classes)
│       └── bin/custom/sampledatamcp/# Data-only extension (11 ImpEx files, 20 images)
├── docs/                            # Generic guides (getting-started, data, extending)
├── .claude/skills/                  # 15 domain skills for AI-assisted development
├── CLAUDE.md / README.md
└── .gitignore
```

**Verdict: fully CCv2-compliant.** Verified specifics:

- `manifest.json` declares `commerceSuiteVersion: 2211.50` and defers extension lists to `localextensions.xml` (`extensions: []` + useConfig) — the documented pattern; the two files are consistent.
- Persona-based property files (`local.properties` + `local-dev/-stg/-prod.properties`) map cleanly to CCv2 environment types. Production persona disables Swagger and raises log levels — the right defaults.
- The Gradle `setupConfig` task overlays tracked `dev-config/` into gitignored `hybris/config/` — an excellent separation of tracked source config from generated runtime config.
- `.gitignore` correctly excludes platform, modules, `gensrc/`, `classes/`, config, logs, and licensed ZIPs (verified via `git ls-files` — no build artifacts are committed).

### Finding (Medium): local platform version drift

`manifest.json` declares **2211.50**, but the locally unpacked platform is **2211.38** (`hybris/bin/platform/build.number`, and `dependencies/hybris-commerce-suite-2211.38.zip`). Cloud builds will compile against .50 while local development runs .38. Low practical risk within a patch line, but it undermines "works locally == works in cloud" and should be reconciled (download the .50 suite ZIP, or pin the manifest to .38 deliberately). Note this also interacts with the JDK 21 timeline — see document 06.

### Finding (Medium): documentation names the wrong repository

`README.md` and `CLAUDE.md` show directory trees rooted at `sap-mcp-server-g`; the repository is `sap-mcp-server-l`. Cosmetic, but it is the first thing a careful reader notices. Fix by using the real name or a generic `<repo-root>/`.

## 2. Extension design: a clean two-extension split

The separation of concerns between the two custom extensions is textbook:

| | `coremcp` | `sampledatamcp` |
|---|---|---|
| Role | All logic: MCP protocol, tool handlers, agent service, LLM providers, knowledge search | All data: infrastructure ImpEx, catalog, customers/orders, knowledge content, Solr configs, promotions |
| Java | 53 classes | 1 class |
| items.xml | `KnowledgeEntry` type (typecode 14001 — verified safe in the custom 10000+ range) | empty |
| Spring | Full service wiring | stub |
| Can ship to production without demo data? | **Yes** | Excluded by dropping one extension from `localextensions.xml` |

This split is exactly what lets the same codebase serve as demo (with `sampledatamcp`) and as a production accelerator (without it). It should be called out as a design strength in any presentation.

### `coremcp` internal structure

Standard hybris extension layout (extensioninfo.xml, project.properties, src/, testsrc/, resources/, web context), with packages organized by responsibility:

```
com.coremcp/
├── controllers/   # McpController, AgentController, VisualSearchController, KnowledgeController
├── services/      # interfaces; impl/ holds Default* implementations
├── tools/         # McpToolHandler strategy interface + ~20 handlers
└── dto/           # JsonRpcRequest/Response/Error, McpSession
```

Interface + `impl/Default*` naming follows SAP convention throughout, which is what makes the alias-override pattern (section 4) meaningful.

## 3. Platform integration — verified against platform source

This is the part of the review that distinguishes "looks right" from "is right." Each integration mechanism was traced into the platform/module source on disk:

| Integration | Mechanism used | Verified against | Verdict |
|---|---|---|---|
| OCC v2 web layer | `resources/commercewebservices/v2/additional-web-spring-context.xml` with component-scan of `com.coremcp.controllers` | `commercewebservices` `v2-web-spring.xml` loads `classpath*:/commercewebservices/v2/additional-web-spring-context.xml` | ✅ The SAP-sanctioned hook, used exactly as designed |
| URL namespace | All controllers under `/{baseSiteId}/...` | OCC v2 controller conventions in `commercewebservices` | ✅ `/v2/{baseSiteId}/mcp`, `/agent/chat`, `/agent/visual-search`, `/info/*` |
| Endpoint security | `@Secured({"ROLE_CUSTOMERGROUP","ROLE_TRUSTED_CLIENT"})` | `springmvc-v2-servlet.xml` enables `global-method-security secured-annotations="enabled"` | ✅ Annotations are actually enforced (a common silent failure in custom OCC extensions — not here) |
| Commerce operations | `CartFacade`, `CheckoutFacade`, `OrderFacade`, `ProductFacade`, `ProductSearchFacade`, `CustomerFacade`, `VoucherFacade` | Facade signatures in 2211.38 modules | ✅ Correct abstraction tier (facades, not raw services) and **zero deprecated APIs in use** |
| Cart context per MCP session | `CartLoaderStrategy` (OCC pattern) + `CartService` for session state; cart code persisted on the MCP session and restored per request | `commercewebservicescommons` | ✅ Multi-turn agent conversations keep a consistent cart without breaking parallel user sessions |
| Knowledge search | `FacetSearchService` / `FacetSearchConfigService` (platform Solr API) | `solrfacetsearch` module | ✅ No raw Solr HTTP; query templates with weighted boosts |
| Promotions read | Parameterized FlexibleSearch over `PromotionSourceRule` + coupon models, with batch redemption-count queries | `promotionengineservices`, `couponservices` | ✅ Right tier (admin domain models aren't Solr-indexed); no N+1 |

### Finding (High): three undeclared extension dependencies

`coremcp/extensioninfo.xml` declares only `commercewebservices`, but the source imports directly from three more modules:

- `de.hybris.platform.solrfacetsearch.*` — 7 imports in `DefaultKnowledgeSearchService`
- `de.hybris.platform.promotionengineservices.model.PromotionSourceRuleModel` — `DefaultPromotionQueryService`
- `de.hybris.platform.couponservices.model.*` — `DefaultPromotionQueryService`

It compiles today because those extensions happen to be on the classpath transitively. Any platform update that reshuffles transitive requirements breaks the build with a confusing error. Fix is purely declarative:

```xml
<requires-extension name="solrfacetsearch"/>
<requires-extension name="promotionengineservices"/>
<requires-extension name="couponservices"/>
```

(`sampledatamcp`, notably, declares its dependencies correctly — the gap is only in `coremcp`.)

## 4. Spring wiring

The extension uses the SAP alias pattern (`defaultFooService` bean + `fooService` alias) consistently for services and all ~20 tool handlers, which is what allows downstream projects to override any piece without touching this extension. Two deviations:

- **(Medium)** The three LLM provider beans (`openAiLlmProvider`, `anthropicLlmProvider`, `openAiCompatibleLlmProvider`) are defined directly without `default*` + alias. Substituting a custom provider — the single most likely customization a client project will want — currently requires fragile bean-ID override.
- **(Low)** `DeepLinkBuilder` is a concrete utility without an interface/alias. Acceptable for a utility; inconsistent with the rest.

## 5. Runtime topology and the single-node assumption

The one genuinely architectural finding of this review:

**Finding (High): the MCP session store is single-node.** `DefaultMcpSessionService` keeps sessions in a `ConcurrentHashMap` with lazy TTL eviction. The implementation is well-built for what it is (thread-safe, documented limits, honest code comments about the constraint). But on CCv2:

1. API aspects run **multiple nodes**;
2. CCv2's sticky routing is **cookie-based**, and MCP/LLM agent clients send a header (`MCP-Session-Id`), not cookies — so requests in one agent conversation can land on different nodes;
3. **rolling deployments** discard node memory mid-conversation.

SAP's sanctioned mechanism for exactly this problem is DB-persisted session state (the platform's HTTP Session Failover is built on Spring Session persisting to the database; the region cache is invalidation-based and not a shared store, so it is *not* a substitute). The fix — model `McpSession` as a persisted item type (the code already carries a TODO pointing at `coremcp-items.xml`) with a cronjob for TTL cleanup — is scheduled as the centerpiece of Phase 2 in the improvement plan.

## 6. Documentation system

A standout strength. The project documents itself at four layers (CLAUDE.md operating manual → `.claude/skills/` domain expertise → generic `docs/` guides → per-extension feature flows), and the **three-file flow convention** (`context.md` / `components.md` / `diagram.md` with Mermaid) is applied consistently across six feature flows totaling ~1,600 lines. Spot-checks found the docs current against the code. Gaps are modest: no ADR log, no Javadoc on public service interfaces, and the repo-name mismatch noted above. The `.claude/skills/` directory also carries nine Spartacus skills irrelevant to this backend-only project — harmless, but pruning them sharpens the story that the skill set is curated for this codebase.

## Summary

| Area | Verdict |
|---|---|
| CCv2 layout & build | ✅ Compliant, verified |
| Extension split (logic vs. data) | ✅ Exemplary |
| OCC/web integration | ✅ SAP-sanctioned mechanism, verified against platform source |
| Platform API usage | ✅ Correct tiers, no deprecated APIs |
| Dependency declarations | ⚠️ 3 missing in coremcp (High, trivial fix) |
| Bean overridability | ⚠️ LLM providers not aliased (Medium) |
| Cluster readiness | ⚠️ In-memory session store (High, Phase 2) |
| Version consistency | ⚠️ Local 2211.38 vs manifest 2211.50 (Medium) |
| Documentation | ✅ Strong, minor naming fix needed |
