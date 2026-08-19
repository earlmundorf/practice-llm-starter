# Decision tree — which path to take

Use this to decide which SAP reference(s) a given migration step should consult, and whether to use OpenRewrite or manual steps.

## Entry point: what's the project's starting state?

Run `scripts/detect_state.sh <project-dir>`. It reports Java version, Spring version, Commerce version, existing platform extraction (if any), and flags likely-migration-impacting files. Feed that into the branches below.

## Pre-branch decision: migration strategy

Upstream of every other branch. Determines Phase 0 layout only — Phases A through H are identical regardless.

**In-place** (default when no separate target repo is named):

- Upgrade the existing project repo on a new branch, e.g., `migration/jdk21`.
- Single rollback point: `git reset --hard pre-migration-{{DATE}}` or `git switch main`.
- Existing CI, branch protection, and deploy wiring keep working unchanged.
- Wipe `core-customize/hybris/bin/platform` and `bin/modules` before `bootstrapPlatform` to force a clean re-extract of the new commerceSuiteVersion. The old extraction won't upgrade in place.
- Status: **unvalidated as of 2026-05-01.** First in-place run is finding-generating — see `findings/2026-05-01-inplace-bootstrap-platform-behavior-unknown.md`.

**Copy-to-new-repo** (default when the user explicitly names a separate target):

- `git archive HEAD` legacy → tar-extract into a fresh repo. Validated 2026-04-30.
- Cleanest possible diff between "old" and "new" trees.
- Legacy repo stays buildable in parallel — useful if customers still need fixes on the old stack mid-migration.
- Phase 0 includes `.gitignore` / `CLAUDE.md` / `README.md` reconcile, which in-place skips entirely.

Heuristic:

- Single-repo team that owns CI + deploy wiring → **in-place**.
- Customers needing parallel patches on the legacy stack → **copy**.
- Wanting a clean before/after artefact for review → **copy**.
- No second repo on disk and no parallel-stack requirement → **in-place**.

The chosen strategy populates Phase 0 in `migration-docs/migration-plan.md` from one of two subsections in `phase-guide.md` (`Phase 0-inplace` or `Phase 0-copy`). The other is dropped.

## Branch 0 — Which Phase B path: Claude-driven sweeps or OpenRewrite?

**Default: Claude-driven sweeps.** Validated 2026-04-30 on a 2-extension project: ~15 min active work, 80 → 0 compile errors in three passes. Each residue (javax, `@Required`, Mockito Matchers, OAuth2 rename, etc.) becomes a grep-and-edit pass on the upgraded platform source. No toolchain rollback needed. See `phase-guide.md` Phase B for the residue catalog.

**Alternative: OpenRewrite recipes (`../sap-notes/3618495-openrewrite-framework-update.md`).** Worth the setup cost (16 GB JVM, login-gated JAR downloads, Gradle 7.6.6 pin, `.git` rename, JDK 17 + pre-jdk21 2211 input-state requirement) only when the custom-extension count makes grep-per-pattern sweep volume unmanageable.

Heuristic:
- **≤5 custom extensions** → Claude sweeps. Sweep time scales linearly with extension count; setup is zero.
- **5–15 custom extensions** → judgment call. If the team's already familiar with OpenRewrite, run it. Otherwise sweeps are usually still faster because the setup cost is fixed and the per-sweep grep-then-edit is ~2 minutes.
- **15+ custom extensions** → OpenRewrite. The setup cost amortizes; parallel recipe runs beat sequential sweeps.

Either way, keep Phase 0's upfront platform + JDK bump. The sweep path runs on that state directly; the OpenRewrite path requires a temporary rollback-then-roll-forward, documented in the SAP Note mirror.

## Branch 1 — Is the project on Spring 5 at all?

**Yes** → full Spring 6 path. Load:
- `sap-docs/03-spring-6.md` (TOC of sub-changes)
- `sap-docs/04-spring-model-changes.md` (exhaustive bean/model diff)

**No (already Spring 6)** → skip Spring branch entirely.

## Branch 2 — javax → jakarta sweep

Required for any project that builds. Path:

1. **First choice: OpenRewrite.** Run the SAP-provided recipes. Covers the 95% case.
   - See `sap-docs/01-general-update-guide.md` → section "Update with the help of the OpenRewrite recipes".
   - See `sap-docs/02-openrewrite-recipes.md` (pointer to the same anchored section).
2. **Only after OpenRewrite:** scan for residues. Typical misses:
   - XML config files referring to `javax.*` by fully-qualified classname in string attributes
   - Generated code paths the recipe skipped
   - Third-party dependencies that haven't shipped a Jakarta build — these need replacement, not migration
   - **Mockito `org.mockito.Matchers`** — removed in Mockito 3.x, not covered by the SAP recipe scope. Rename imports to `org.mockito.ArgumentMatchers`; method signatures are identical.
3. Record each manual fix as a `findings/` entry so the next run of the skill knows about it.

## Branch 3 — OAuth / authentication

**Pre-check (cheap; run first):** `grep -n "extension name=\"oauth2\"" dev-config/localextensions.xml` — if it matches, the legacy `oauth2` extension is dead in 2211-jdk21.x and the rename is the first concrete Phase D edit. See `known-incidents.md` incident 0.

Check if the project uses OAuth (manifest.json `oauthConfigurations`, any `*-oauth2-*.xml` file, any `OAuth2RestTemplate` or `RestTemplate` Bean for OAuth). If any → full OAuth path:

1. `sap-docs/08-oauth-authorization-server.md` — the new Spring Authorization Server framework (SAP's replacement for OAuth2). Exhaustive: covers PKCE, confidential/public clients, token flows, revocation, introspection.
2. `sap-docs/09-resource-server.md` — resource server config (the OCC side).
3. `sap-docs/10-resttemplate-removal.md` — deprecation of RestTemplate/OAuth2RestTemplate + replacement guidance.

If the project has only non-OAuth auth (basic, SAML-only, etc.), you still need `09-resource-server.md` for OCC protection, but `08-oauth-authorization-server.md` is skippable.

## Branch 4 — Tomcat config

Check `core-customize/hybris/config/tomcat/conf/server.xml` (or wherever the project's Tomcat customization lives). If present → consult `sap-docs/05-tomcat.md` for:
- `setAttribute()` method replacement
- SSL connector attribute migration
- Filter API adjustments beyond the javax rename
- Security header config (X-XSS-Protection removed)

## Branch 5 — Build tooling

Always consult `sap-docs/06-build-ant-gradle.md`. Affects every project:
- Ant must be ≥ 1.10.14
- Gradle used by `ant gradle` task must be 8.5
- Custom Ant task definitions need review
- `gradle.project.name` default change (projects pinning the old value are safe; projects relying on defaults get a rename)
- `create-data` unsuccessful phase default change (imports now fail by default — review ImpEx error tolerance)

## Branch 6 — Tests

Always consult `sap-docs/07-testing.md` if the project has test code. Covers:
- JUnit 4 → 5 migration
- Mockito version jump
- Spring Test changes (`@TestExecutionListener` registration)
- ycommercewebservicestest template adjustments for HttpComponents 5

## Branch 7 — OCC / web services specifics

If the project has custom OCC controllers (`*CommercewebservicesController`, `@RestController`, `@ControllerAdvice`):
- `sap-docs/12-web-service-exception-handling.md` — error handler signature changes
- `sap-docs/04-spring-model-changes.md` — `@ModelAttribute` binding behavior changes
- Known incident: "POST mappings not working after jdk21 upgrade" (see `known-incidents.md`)

## Branch 8 — SmartEdit

If the project uses SmartEdit: `sap-docs/11-smartedit.md`. Mostly URL-pattern and auth config adjustments for Spring 6's PathPatternParser default.

## Branch 9a — Apache libraries + caching

These affect almost every project and OpenRewrite handles the bulk, but the residue can break initialize/update. Consult `references/additional-changes.md` (Apache libraries + Caching sections).

Required checks:

1. `grep -rE "org\.apache\.commons\.lang\b" custom/ bin/custom` — replace with `org.apache.commons.lang3`.
2. `grep -rE "org\.apache\.commons\.configuration\b" custom/ bin/custom` — migrate to Configuration 2.x.
3. `grep -rE "org\.apache\.commons\.math3\b" custom/ bin/custom` — Math 3.6 removed; inline or replace.
4. `grep -rE "EhCacheRegion" custom/ bin/custom` — swap for `DefaultCacheRegion`.
5. Any Ehcache XML config file — migrate to Ehcache 3.x schema.
6. If the project has `ycommercewebservicestest`-derived extensions — review HttpComponents 4 → 5 request-building code.

## Branch 9b — JVM + language housekeeping

Always applies when moving to Java 21. Consult `references/additional-changes.md` ("Other Topics" + "javax → jakarta specifics").

- Byte-Buddy 1.14.19 visibility checks (proxies, aspects)
- Deprecated `Thread` methods (`stop`, `suspend`, etc.)
- Groovy 3 → 4 script compatibility
- Olingo 2.0.13 → 5.0.0-sap-02 (only if OData in use)
- DdlUtils removal — replace with platform-internal APIs
- `jakarta.servlet.jsp.jstl` 1.2.6 → 3.0.1
- `yacceleratorstorefront`-template-specific jakarta steps
- LocalizedHybrisConstraintViolation subclass refactor
- Orphaned types cleanup (post-initialize)

## Branch 10 — Deprecated extensions

If any of these appear in `manifest.json` or `localextensions.xml`, the project has a blocking decision to make BEFORE the migration:

- Any `*cockpit*` extension (deprecated cockpit-related extensions are removed)
- `amazoncloud` extensions (removed)

These require replacement plans, not migration. Flag as P0 in the migration plan and stop until the user decides.

## Glue: how the plan phase uses this

The plan-phase workflow in SKILL.md walks the branches in roughly this order:
1. Run detection → report state.
2. Gate on Branch 10 (deprecated extensions). If hit, produce a blocking finding and stop.
3. Produce the migration plan as an ordered sequence of the remaining branches that apply, with per-step SAP references linked.
4. Mark each step's preferred path: **OpenRewrite** / **Manual** / **Decision** / **Verify**.
5. Include Go/No-Go gates (build succeeds, Solr reachable, OCC auth works, data parity check) between phases.

## When two references disagree

The hierarchy is:
1. `sap-docs/` mirrors of SAP Help — **authoritative**.
2. `additional-changes.md` — items verbatim from SAP's framework-update .docx that don't have a dedicated sap-docs mirror yet (most Spring 6 sub-topics, Apache libs, caching, JVM/language housekeeping). Treat as SAP-authoritative.
3. `upstream/` — bundled source materials. The `.docx` is the source-of-truth `additional-changes.md` was derived from. The `SPRING_UPGRADES_*.md` files are Spring's own upstream release notes — consult when `sap-docs/` is silent on a Spring 6 behavior.
4. `00-overview.md` — condensed digest for anchoring scope. Defer to the three above on specifics.
5. `findings/` — learnings from prior runs; the most recent findings override older overview text.
6. Internet searches — context-only, never override SAP.
