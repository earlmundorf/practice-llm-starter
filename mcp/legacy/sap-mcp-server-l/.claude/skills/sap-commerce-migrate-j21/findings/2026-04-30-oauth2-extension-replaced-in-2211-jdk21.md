---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: 0
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: new-incident
status: promoted
related_refs:
  - references/sap-docs/08-oauth-authorization-server.md
  - references/decision-tree.md
  - references/00-overview.md
  - references/known-incidents.md (incident 0)
promotion_target: references/known-incidents.md (new entry) + references/decision-tree.md Branch 3 (add up-front check)
promoted_commit: 4e752eb (symptom + pre-check); 74f15f5 (subset decision validated at B.1)
promotion_note: Symptom + pre-check + replacement-extension names promoted. Subset-choice-logic deferred until empirically validated during this project's Phase D.
---

## What happened

After `bootstrapPlatform` installed 2211-jdk21.9, the platform's extension inventory confirms that **the legacy `oauth2` extension no longer exists**. It has been replaced by three new extensions, which were visible in the bootstrap's "Extensions in dependency order" listing:

- `oauth2commons` (shared OAuth bits)
- `authorizationserver` (new Spring Authorization Server based — the replacement for the authorization flow)
- `resourceserver` (resource-server side — the OCC protection layer)

Our legacy `localextensions.xml` (and the checked-in source at `dev-config/localextensions.xml`) still declares `<extension name="oauth2" />`. On the next build (`./gradlew yclean yall`), platform bootstrap will fail to resolve that extension name because it isn't in the new platform's `bin/platform/ext/` or any module under `bin/modules/`.

## Context

- Bootstrap output, extensions section:
  ```
  oauth2commons->platformservices 2211-jdk21.9 [p*cib]
  authorizationserver->oauth2commons 2211-jdk21.9 [p*cibw]
  resourceserver->oauth2commons 2211-jdk21.9 [p*cibw]
  ```
- Legacy `localextensions.xml` (both at `dev-config/localextensions.xml` and the generated `hybris/config/localextensions.xml` after setupConfig overlay) contains:
  ```xml
  <!-- ===== Auth ===== -->
  <extension name="oauth2" />
  ```
- There are also references in `hybris/config/local.properties` like `oauth2.webroot=...` that assume the old extension's name. These will need to be updated in Phase D alongside the extension swap.
- Our skill reference `references/sap-docs/08-oauth-authorization-server.md` covers the full migration of the auth layer; that's what Phase D of the migration plan drives.

## The SAP-doc gap (if applicable)

SAP's general update guide mentions that "OAuth2 is replaced with the Spring Authorization Server framework, which introduces new extensions" — but does not call out the **exact extension rename** explicitly in the condensation. Our skill's `00-overview.md` and `08-oauth-authorization-server.md` should name the three new extensions up front so projects can grep for `<extension name="oauth2" />` in their localextensions.xml before touching any Java code. Right now the skill describes the concept but not the literal replacement names needed to update localextensions.xml.

## The fix that worked

Not fixed yet — this is Phase D work, documented here for visibility. The fix will be:

1. Update `dev-config/localextensions.xml` (source of truth; `hybris/config/localextensions.xml` is generated):
   ```xml
   <!-- Before -->
   <extension name="oauth2" />

   <!-- After — see references/sap-docs/08-oauth-authorization-server.md for which subset applies to this project -->
   <extension name="oauth2commons" />
   <extension name="authorizationserver" />
   <extension name="resourceserver" />
   ```

2. Grep all `hybris/config/local*.properties` and the project's Spring XML files for `oauth2.webroot`, `oauth2.password`, `oauth2.*` property keys. Many of these are renamed or split across the new extensions. See `sap-docs/08-oauth-authorization-server.md` and `sap-docs/09-resource-server.md` for the new property names.

3. Re-run `./gradlew bootstrapPlatform setupConfig` after the XML edit so the new config is re-generated.

4. Any Java code in `coremcp` that uses `OAuth2RestTemplate` — handled by `sap-docs/10-resttemplate-removal.md`.

## Why this generalizes

Every SAP Commerce project with OAuth on Spring 5 will have `<extension name="oauth2" />` in its localextensions.xml. Migrating to 2211-jdk21.x without updating that declaration will fail at build time with an opaque "extension not found" error. Naming the three replacement extensions in the skill's up-front materials — so the reader doesn't have to decode the full Auth Server doc to discover them — is cheap, accurate, and prevents a confusing first-build failure.

## Promotion suggestion

1. **`references/known-incidents.md`** — add a new incident before "1. JDK21 server startup bean errors":

   > **0. `<extension name="oauth2" />` fails to resolve after platform upgrade**
   > - **Symptom:** `./gradlew yclean yall` or `bootstrapPlatform setupConfig` logs "extension 'oauth2' not found" or similar. The legacy `oauth2` extension is removed in 2211-jdk21.x.
   > - **Fix:** replace it in `dev-config/localextensions.xml` with `oauth2commons` + `authorizationserver` + `resourceserver`, picking the subset appropriate for the project. See `sap-docs/08-oauth-authorization-server.md` for the decision (authorization-server-only vs full trio). Then re-run `./gradlew bootstrapPlatform setupConfig`.
   > - **Also check:** `hybris/config/local*.properties` for legacy `oauth2.*` property keys — many of these are renamed or split across the new extensions.

2. **`references/decision-tree.md`** Branch 3 — add as the FIRST check:

   > **Pre-check:** `grep -n "extension name=\"oauth2\"" dev-config/localextensions.xml` — if it hits, you're doing the full OAuth path; the extension rename is the first concrete edit.

3. **`references/00-overview.md`** under the Major structural changes bullet "OAuth2 framework replaced with Spring Authorization Server" — inline the extension names: `oauth2` → `oauth2commons` + `authorizationserver` + `resourceserver`. Mentioning the literal names up front saves a doc hop.
