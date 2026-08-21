# SAP Commerce Cloud Update Release 2211-jdk21.1 — Migration Overview

Condensed from SAP's framework-update changelog (September 2025). Source `.docx` is bundled in the skill at `references/upstream/SAPCommerceUpgradeJDK21Changes.docx` for verbatim reference. Read this overview first before any migration plan.

## The headline

Move from **Java 17 + Spring 5** or **Java 21 + Spring 5** → **Java 21 + Spring 6** running on **Tomcat 10.1 / Jakarta EE 10**.

**The clock is real:** SAP's adopt-by guidance was **2026-06-30**, and **after 2026-08-31 new CCv2 builds targeting Java 17 are disallowed** — a Java-17 project cannot ship fixes through the cloud pipeline past that date. Every migration plan should state the project's runway against these dates in its intake. The 2211-jdk21 line receives monthly-cadence updates (2211-jdk21.10 as of May 2026); target the **latest** patch at migration time, not the version this skill was first validated on.

"Definition of done" for a project migration: the system builds, Solr works, code works, data matches, and new OCC access is testable end-to-end. Compiles-clean is necessary but not sufficient.

## Supported target versions

| Component | From | To |
|---|---|---|
| JDK | Oracle/SAP JDK 17 | **SapMachine 21.0** (required) |
| Spring Framework | 5.x | **6.2.10** |
| Spring Security | 5.8.x | **6.5.0** |
| Spring Security SAML2 | 5.8.11 | 6.4.1 |
| Spring Integration | 5.5.20 | ≥ 6.4.1 |
| Tomcat | 9.0.95 | **10.1.x** (Jakarta EE 10) |
| Gradle (ant gradle task) | 7.3.3 | **8.5** |
| Ant | 1.10.5 | ≥ **1.10.14** |
| Groovy | 3.0.13 | 4.0.26 |
| Ehcache | 2.x | **3.x** |
| SiteMesh | 3.0-alpha | 3.2.1 |
| Olingo | 2.0.13 (public) | **5.0.0-sap-02** (SAP fork) |
| Apache Commons Lang | 2 (EOL) | 3 |
| Apache Commons Configuration | 1.x | 2.x |
| Apache HttpComponents | 4 | 5 |
| Apache Commons Math 3.6 | present | **removed** |
| Apache Commons Pool 1.6 | present | removal upcoming |
| jakarta.servlet.jsp.jstl | 1.2.6 | 3.0.1 |
| Byte-Buddy | prior | 1.14.19 |

## Toolchain compatibility confirmed

The skill assumes **CCv2 layout** — SAP files under `core-customize/`; non-CCv2 projects fall back to the project root with degraded detection (`detect_state.sh`). `sap.commerce.build` Gradle plugin 5.0.2 runs cleanly on SapMachine JDK 21 and completes `bootstrapPlatform` against 2211-jdk21.9 without modification. Gradle wrapper 8.12 + bundled Ant 1.10.15 both satisfy the new minimums. **Verified 2026-04-30** on macOS (aarch64) / upgrade-21-mcp-server-g. If your legacy project has `sap.commerce.build` 5.0.2 or newer, do not bump the plugin as part of the migration — the platform change is enough.

**Why this matters for Phase B pathing:** because the platform + JDK upgrade works cleanly upfront (Phase 0 bumps toolchain → `bootstrapPlatform` → green), the canonical Phase B path is **Claude-driven sweeps on the upgraded source** (see `phase-guide.md` Phase B, and SKILL.md Phase 2). No runtime rollback needed — sweeps edit Java source, which is a runtime-agnostic operation. The OpenRewrite alternative has different sequencing: it parses source through a recipe that requires JDK 17 + pre-jdk21 2211 as input state (see `sap-notes/3618495-openrewrite-framework-update.md` "Critical sequencing"). Don't conflate the two paths.

## Major structural changes

1. **Jakarta EE namespace migration.** `javax.*` → `jakarta.*` across servlet, JSP/JSTL, filter APIs, persistence, validation.
2. **OAuth2 framework replaced with Spring Authorization Server.** New SAP extensions are introduced for the authorization server, resource server, and OCC integration paths. `RestTemplate`/`OAuth2RestTemplate` are deprecated for removal. **The `password` and `implicit` grant flows are removed** — purge them from OAuthClientDetails impex (see `known-incidents.md` #8) and migrate every password-grant caller (storefront logins, test harnesses, bridges) to authorization_code + PKCE or client_credentials. See `references/oauth-migration-guide.md` for the ordered make-it-work path, the 8 client-validation preconditions, a client cookbook, and SAML impact.
3. **URL matcher default changed.** Spring 6 uses `PathPatternParser` instead of `AntPathMatcher`. Trailing-slash matching default is now `false`. Many OCC endpoint paths and security intercept-URL rules need review.
4. **Spring bean changes.** `@Required` annotation is removed (OpenRewrite removes invocations automatically). `CommonsMultipartResolver` → `StandardServletMultipartResolver`. `HttpPutFormContentFilter` → `FormContentFilter`. `SocketUtils` is gone.
5. **Tomcat 10 brings Jakarta servlet API.** SSL connector attributes moved. `X-XSS-Protection` header deprecated.
6. **Deprecated extensions removed.** Cockpit-related extensions (deprecated for years) and `amazoncloud` extensions are gone. Projects still depending on them need to migrate off.
7. **Build defaults changed.** `gradle.project.name` default is now `sap-commerce`. `create-data` unsuccessful-phase behavior now fails by default (previously tolerated errors).

## The two recommended migration paths

SAP publishes **OpenRewrite recipes** that automate the bulk of the refactor: javax → jakarta, removal of `@Required`, Spring bean updates, Apache Commons version upgrades, `HttpPutFormContentFilter` rewrite, and more. See `sap-docs/01-general-update-guide.md` (section: *Update with the help of the OpenRewrite recipes*) and `sap-docs/02-openrewrite-recipes.md`.

The alternative is the **fully manual update path** (same source document, different anchor). Fully manual is appropriate only when a project has heavy bespoke tooling that OpenRewrite would misinterpret. In practice the right answer is almost always: run the OpenRewrite recipes first, then manually patch what the recipes miss.

## What OpenRewrite does NOT cover (per the .docx)

- Custom Ant task definitions (adjust manually after the recipes run)
- Tomcat `server.xml` / connector config
- `setAttribute()` replacement for Tomcat connector
- SSL connector attribute migration
- Many OAuth/Spring Authorization Server config decisions (new extensions must be enabled and wired)
- `LocalizedHybrisConstraintViolation` subclass changes
- Orphaned type cleanup
- Eclipse `.classpath` files and JDK settings

## Known incidents (SAP-flagged + field-observed)

`known-incidents.md` carries nine entries — five from real SAP support cases in the .docx, four added from this skill's own runs and architectural review:

0. `oauth2` extension fails to resolve (replaced by three new extensions) — field-observed
1. JDK21 server startup bean errors — SAP case
2. Custom storefront not loading after JDK21 upgrade — SAP case
3. OAuth registered redirect URI issue — SAP case
4. POST mappings not working after jdk21 upgrade — SAP case
5. Deployment stuck in D1 environment — SAP case
6. Stale carts/orders fail to deserialize post-migration — architectural review
7. Migration started without backup capability → unrecoverable — architectural review
8. `password` grant in OAuthClientDetails impex fails `yinitialize` validation — field-observed

## Additional authoritative references

Beyond `sap-docs/` (direct SAP Help mirrors), more references carry authoritative content. **Precedence ranking lives in `decision-tree.md` "When two references disagree" — this section is just an inventory of what exists.**

- **`additional-changes.md`** — items verbatim from SAP's `SAPCommerceUpgradeJDK21Changes.docx` that don't have a dedicated sap-docs mirror yet. Covers every Spring 6 sub-topic, Apache libraries, caching, JVM/language housekeeping, javax→jakarta specifics, Olingo, LocalizedHybrisConstraintViolation, orphaned types, DdlUtils.
- **`sap-docs/13-release-notes-2211-jdk21.md`** — release notes for the 2211-jdk21 update stream.
- **`sap-docs/14-update-release-2211.46.md`** — specific point-release notes.
- **`upstream/`** — bundled source materials. Consult when `sap-docs/` is silent on a behavior or wording. Contents:
  - `SAPCommerceUpgradeJDK21Changes.docx` — primary SAP source for `additional-changes.md` + this overview.
  - `SPRING_UPGRADES_60.md`, `SPRING_UPGRADES_61.md`, `SPRING_UPGRADES_62.md` — Spring's own upstream release notes (6.0 → 6.1 → 6.2). Detail SAP's digest doesn't always carry: PathPatternParser quirks, deprecation removals by minor version, etc.

### External links from the .docx worth knowing

- **SAP Note 3618495** — `https://me.sap.com/notes/3618495/E`. Customer-login required. Primary SAP note for this update release. Refer customers here when they need the canonical note ID.
- **SAP support cases** referenced in `known-incidents.md` — case URLs are customer-login only; use the case numbers (002075129...) when opening related tickets.

## What this skill does with this overview

The plan phase reads this file first to anchor the scope. The detection script pins the project's starting point. The plan then maps detected state → SAP reference pages under `sap-docs/` (+ `additional-changes.md` for items without dedicated pages) and produces an ordered migration checklist with per-step references back here. See `decision-tree.md` for the dispatch logic.
