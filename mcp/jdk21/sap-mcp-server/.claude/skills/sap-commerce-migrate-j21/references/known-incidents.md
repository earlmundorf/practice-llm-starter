# Known incidents from SAP cases

Real-world issues SAP support has seen on the 2211-jdk21.1 migration. Use these as:
- Detection checklist during planning (flag projects likely to hit them)
- Diagnostic reference when something breaks

These are summaries. The authoritative cases require SAP customer login.

## 0. `<extension name="oauth2" />` fails to resolve after platform upgrade

- **Symptom:** `./gradlew yclean yall` (or any ant `y*` target after bootstrap) logs "extension 'oauth2' not found" or a platform extension-load failure referencing `oauth2`. The legacy `oauth2` extension is removed in 2211-jdk21.x.
- **Likely cause:** `dev-config/localextensions.xml` still declares `<extension name="oauth2" />`. In 2211-jdk21.x it was replaced with three new extensions.
- **First diagnostic:** `grep "extension name=\"oauth2\"" dev-config/localextensions.xml`
- **Fix:** replace with the appropriate subset of:
  - `oauth2commons` — shared OAuth bits (always needed if any OAuth is in use)
  - `authorizationserver` — Spring Authorization Server based flow
  - `resourceserver` — OCC protection layer

  Decide which subset per `sap-docs/08-oauth-authorization-server.md`. Also audit `hybris/config/local*.properties` for `oauth2.*` property keys — many renamed or split across the new extensions.
- **Source:** Field-observed 2026-04-30 on upgrade-21-mcp-server-g Phase 0.7.

## 1. JDK21 server startup bean errors

- **Symptom:** Hybris server fails to boot with Spring bean instantiation errors referencing removed classes or missing dependencies.
- **Likely causes:**
  - Lingering `@Required` annotations (Spring 6 removes the post-processor).
  - Beans referencing `org.springframework.util.SocketUtils` (removed).
  - Extensions declaring bean aliases that now conflict under Spring 6's fixed overriding behavior — see "Configure Extension Load Order to Support Fixed Spring 6 Alias Overriding Behavior" in `sap-docs/03-spring-6.md`.
  - Missing `mvcHandlerMappingIntrospector` bean (Spring MVC + Security sharing context issue).
- **First diagnostic:** search boot log for `BeanCreationException`, `NoSuchBeanDefinitionException`, `ConflictingBeanDefinitionException`. Cross-reference class name against `sap-docs/04-spring-model-changes.md` "Removed Spring Beans" and "Modified Models".
- **SAP case:** 002075129500011258462025

## 2. Custom storefront is not loading after JDK21 upgrade

- **Symptom:** Server boots OK, but storefront URL returns 404 or blank page.
- **Likely causes:**
  - PathPatternParser vs. AntPathMatcher mismatch — routes that relied on trailing-slash matching silently stop resolving (default is now `false`).
  - DispatcherServlet configuration missing after Spring 6's tightened config — see "Resolving DispatcherServlet Exceptions During Server Startup" in `sap-docs/03-spring-6.md`.
  - SiteMesh 3.0-alpha incompatibility with Jakarta EE 10 (must upgrade to 3.2.1).
- **First diagnostic:** enable Spring MVC tracing; check which handler mapping is selected; grep `*-web-spring.xml` for `AntPathMatcher`/`patternParser` references.
- **SAP case:** 002075129500011585642025

## 3. OAuth client validation rejects legacy client configs (redirect URI, PKCE, public/confidential)

- **Symptom:** OAuth login fails with "invalid_redirect_uri", or `yinitialize`/Backoffice save fails with `InterceptorException` from `DefaultOAuthClientDetailsValidator` (e.g. "redirect uri attribute needs to be configured for authorization_code flow", or "confidential client should have a client secret").
- **Likely cause:** the new Spring Authorization Server validator enforces 8 preconditions that legacy platforms tolerated. Legacy "do-everything" clients (e.g. `trusted_client` listing every grant type) trip them.
- **The 8 preconditions** (see `sap-docs/08-oauth-authorization-server.md` "Client Validation"):
  1. ≥1 authorized grant type. 2. ≥1 authority. 3. authorization_code ⇒ ≥1 redirect URI.
  4. public client has no secret. 5. public client has PKCE on. 6. public client has no client_credentials.
  7. confidential client has a secret. 8. converts cleanly to a Spring `RegisteredClient`.
- **First diagnostic:** `grep -rnE 'authorization_code[^;]*;[^;]*;\s*$' core-customize/hybris/bin/custom --include="*.impex"` (authorization_code rows with an empty trailing redirect URI — the most common hit).
- **Fix:** review each `OAuthClientDetails` row against the 8 preconditions. Fast path: if a server-to-server client carries `authorization_code` as cruft, drop it (make it `client_credentials`-only); if it genuinely needs the flow, add a redirect URI. See `oauth-migration-guide.md` §4 and the cookbook §5.
- **Dev workaround for large catalogs:** `authserver.client.validation.dry.run=true` (logs instead of throwing).
- **Source:** SAP case 002075129500012671912025; field-observed 2026-04-30/05-01; promoted from `findings/2026-04-30-oauth-validator-redirect-uri-required.md`.

## 4. POST mappings not working after jdk21 upgrade

- **Symptom:** POST-mapped OCC endpoints return 404 / 405 / malformed binding after migration.
- **Likely causes:**
  - `@ModelAttribute` binding behavior changed in Spring 6 — see `sap-docs/04-spring-model-changes.md`.
  - `HttpPutFormContentFilter` → `FormContentFilter` rename missed.
  - `CommonsMultipartResolver` → `StandardServletMultipartResolver` not migrated; multipart POSTs fail.
  - Trailing-slash matching default now `false`; clients POSTing to `/path/` when handler is `/path` now miss.
- **First diagnostic:** check if the failing endpoint is multipart, check for `@ModelAttribute` on the handler, check trailing slash in client request.
- **SAP case:** 002075129500012022772025

## 5. Deployment stuck in D1 environment

- **Symptom:** Deployment pipeline hangs in the D1 (dev) environment after the framework update.
- **Likely causes:**
  - `create-data` unsuccessful-phase default changed to fail — import errors that used to be tolerated now block boot.
  - Orphaned types from deprecated extensions not cleaned up.
- **First diagnostic:** check recent ImpEx import logs for errors that used to be warnings. Run the orphaned-type cleanup referenced in the general update guide.
- **SAP case:** 002075129500011660642025

## 6. Stale carts/orders fail to load after migration

- **Symptom:** Post-migration, attempts to load a pre-existing cart or order via the cart facade or OCC `/cart` endpoint return `ClassCastException`, `ItemNotFoundException`, or `InvalidClassException`. Storefronts may show "your cart could not be restored" errors. Affected percentage scales with how many sessions had open carts at migration time.
- **Likely causes:**
  - **Persisted serialized blobs.** `Cart`, `Order`, or related types (e.g. `CartEntry`, `OrderProcess`) sometimes have serialized state in DB columns. Spring 5 → 6 + javax→jakarta can change effective `serialVersionUID` even when fields don't move, especially when transitive parent classes change package.
  - **Custom subtypes of platform cart/order types.** If your project has `MyProjectCart extends Cart`, the inherited fields may shift in size/order between versions. Items that loaded fine on the old stack throw `ClassCastException` during deserialization on the new one.
  - **Discriminator column drift.** The platform's discriminator-based polymorphism for carts/orders can desync if a parent type's discriminator value changed between major versions.
- **First diagnostic:**
  ```bash
  # Sample 100 carts from the legacy DB before migration; replay loading them on the upgraded stack.
  # If loading fails for >0% of the sample → flag as Phase F gate failure.
  ./gradlew flexquery -Pfile=/tmp/sample-carts.sql   # SELECT pk, code FROM carts ORDER BY rand() LIMIT 100
  # Then on upgraded stack, attempt cartFacade.getSessionCartForGuid(guid) for each PK.
  ```
- **Fix:** Three options, in increasing destructiveness:
  1. **Filter affected carts** — identify the unrecoverable subset, mark them inactive, accept the loss. Communicate to affected customers.
  2. **Recreate via ImpEx replay** — extract cart contents from the DB pre-migration, re-import after migration. Works only if cart state (line items, prices) can be re-derived without serialized blobs.
  3. **Quiesce before migrating** — close/expire all carts before the cutover. Most disruptive but cleanest. Decision belongs in Step 0.0e.
- **Mitigation in plan:** Step 0.0e (cart quiesce decision) and Phase F's stale-deserialization check substep. Also: keep the DB backup from Step 0.0a — if cart loss is unacceptable and quiesce wasn't an option, you may need to roll back the DB and choose option 2 with extra care.
- **Source:** Architectural review 2026-05-01. No specific SAP case number; this is a class of issue that recurs on any major Spring + jakarta combined upgrade and is well-known to senior Commerce architects.

## 7. Migration started without backup capability → unrecoverable on failure

- **Symptom:** Mid- or post-migration, something goes wrong in Phase F (initialize fails, data corruption, partial type system migration). The team realizes they cannot roll back to the pre-migration state because:
  - There is no DB backup (or backup mechanism was never tested for restorability).
  - Solr index can't be restored either (relied on "rebuild from DB" but the DB is now unrecoverable).
  - Customer data is partially migrated; in-flight orders may be in inconsistent states.
- **Likely cause:** The intake (SKILL.md Phase 1 Step 3, intake field 4.1) selected Scenario D ("production with customer data + NO backup capability") but the migration proceeded anyway — either the intake step was skipped, or the team rationalized "it'll be fine, we'll figure it out if it breaks." It's never fine.
- **First diagnostic:** Check `migration-docs/intake.md` field 4.1. If Scenario D is selected and the migration is in progress, **STOP IMMEDIATELY**. Restore is the priority.
- **Fix (preventive — the only effective fix):** never start the migration without a verified-restorable backup. Build the backup capability **before** invoking the skill. Verification means: take the backup, restore it to a scratch instance, smoke-test the restored data, confirm bit-for-bit equivalence on critical types. A backup not tested for restoration is not a backup.
- **Fix (reactive — once you're in the bad state):**
  1. Stop the migration. Do not run further `yupdatesystem` / `yinitialize` / impex steps.
  2. Capture the current corrupted state (a "bad backup" is still better than nothing) — take whatever DB snapshot you can.
  3. Engage SAP support. Reference SAP Note 3618495 for the framework update context.
  4. If customer data loss is confirmed: communicate to affected customers per your incident-response policy.
  5. Post-mortem: this incident becomes a finding. The skill's intake-step gate failed to prevent it; understand why (was the intake bypassed? Did the user override the Scenario D halt?) and tighten.
- **Mitigation in plan:**
  - SKILL.md Phase 1 Step 3 (intake) explicitly halts on Scenario D. Do not bypass.
  - Phase 0-prep Step 0.0a is conditional on intake 4.1; Scenario D triggers a hard "plan blocked."
  - The verification-checklist's Gate 0 should explicitly check intake 4.1 ≠ D before the migration begins.
- **Source:** Architectural review 2026-05-01. Generic to any production migration without backup discipline; included here because Spring 5 → 6 + javax → jakarta is exactly the kind of upgrade where rollback is hard, making backup-before-migration even more critical than usual.

## 8. `password` grant type in OAuthClientDetails impex fails validation on `yinitialize`

- **Symptom:** Phase F `yinitialize` fails at the create-data phase with `InterceptorException` from `DefaultOAuthClientDetailsValidator`, typically citing an OAuth client defined in `essentialdata-*.impex`.
- **Likely cause:** legacy sample/essential data ships `password` (and sometimes `implicit`) in `authorizedGrantTypes`. Per `sap-docs/08-oauth-authorization-server.md`: *"The password flow and implicit flows are not available in the new implementation."* The new validator rejects the record at ImpEx load time, and create-data now fails hard on unsuccessful phases.
- **First diagnostic:** `grep -rnE "authorizedGrantTypes.*(password|implicit)" core-customize/hybris/bin/custom --include="*.impex"`
- **Fix:** remove `password`/`implicit` from every `INSERT_UPDATE OAuthClientDetails` row. Supported grants on confidential clients: `authorization_code,refresh_token,client_credentials` (+ `saml_token` where SAML is used). **Every caller using the password grant must change** — storefront login flows, smoke-test scripts, MCP/stdio bridges, e2e harnesses — to authorization_code + PKCE (user flows) or client_credentials (service flows).
- **Also check:** `resourceIds` column (soft-deprecated, tolerated) and dead `oauth2.webroot=` properties.
- **Source:** Field-observed 2026-04-30 (Phase D) on upgrade-21-mcp-server-g; promoted from `findings/2026-04-30-password-grant-removed-from-new-oauth.md`.

## 9. Adding a public OAuth client (SPA / mobile-native) is rejected as confidential

- **Symptom:** `INSERT_UPDATE OAuthClientDetails` with an empty `clientSecret` is rejected with "confidential client should have a client secret" — even with `requireProofKey=true` set.
- **Likely cause:** `OAuthClientDetails` has a separate `public` boolean (default `false`). Public vs confidential is NOT inferred from an empty secret or from PKCE; it is read from the `public` attribute directly.
- **First diagnostic:** `SELECT {clientId},{public},{requireProofKey} FROM {OAuthClientDetails}` — a public client showing `public=false` is the bug.
- **Fix:** set BOTH `public=true` AND `requireProofKey=true` on the row:
  ```impex
  INSERT_UPDATE OAuthClientDetails;clientId[unique=true];...;clientSecret;registeredRedirectUri;public;requireProofKey
  ;client-side;...;;https://your.host/auth/callback;true;true
  ```
- **Reference:** `oauth-migration-guide.md` §3.4 + cookbook §5; `sap-docs/08-oauth-authorization-server.md` "Public Clients" / "Client Validation".
- **Source:** Field-observed 2026-05-01; promoted from `findings/2026-05-01-public-oauth-client-needs-public-true.md`.

## How to use these during a migration

1. **During plan phase:** grep the project for the symptom patterns (e.g., `@Required`, `AntPathMatcher`, `CommonsMultipartResolver`, OAuth redirect URI config). Any match → add an explicit mitigation step to `migration-docs/migration-plan.md`.
2. **During execute phase:** if one of these surfaces, don't improvise — consult the corresponding `sap-docs/` reference and follow the SAP-prescribed remedy.
3. **Post-execute:** if you hit a NEW incident that isn't in this list, write a `findings/YYYY-MM-DD-incident-{slug}.md` entry and flag it for promotion into this file.
