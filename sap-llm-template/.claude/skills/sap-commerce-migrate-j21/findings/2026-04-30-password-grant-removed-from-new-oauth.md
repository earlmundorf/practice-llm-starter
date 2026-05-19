---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: D
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: new-incident
status: open
related_refs:
  - references/sap-docs/08-oauth-authorization-server.md (line 416)
  - references/known-incidents.md (incident 0 already documents the extension rename)
  - scripts/plan-template.md (Phase D.1)
promotion_target: references/known-incidents.md (add incident #2 alongside the oauth2 rename) + scripts/plan-template.md Phase D — add a grep line item for `password` in `authorizedGrantTypes` strings
---

## What happened

At Phase D.1, found that the OAuth client impex in `sampledatamcp/resources/impex/essentialdata-infrastructure.impex` lists `password` as one of the `authorizedGrantTypes` for both `trusted_client` and `mobile_android`. Per SAP Note + `08-oauth-authorization-server.md` line 416:

> The password flow and implicit flows are not available in the new implementation.

On a fresh `yinitialize` against 2211-jdk21.x, the `DefaultOAuthClientDetailsValidator` runs preconditions on each inserted OAuthClientDetails record. A `password` grant would fail validation at ImpEx load time (validator throws `InterceptorException`), aborting the create-data phase. Combined with the separate platform change that create-data now FAILS on unsuccessful phases (see Phase A.5 audit), this is a hard stop for Phase F.

## Context

Before Phase D edit:
```
INSERT_UPDATE OAuthClientDetails ; clientId[unique = true] ; resourceIds ; scope    ; authorizedGrantTypes                                         ; authorities         ; clientSecret ; registeredRedirectUri
                                 ; trusted_client          ; hybris      ; extended ; authorization_code,refresh_token,password,client_credentials ; ROLE_TRUSTED_CLIENT ; secret       ;
                                 ; mobile_android          ; hybris      ; basic    ; authorization_code,refresh_token,password,client_credentials ; ROLE_CLIENT         ; secret       ; http://localhost:9001/authorizationserver/oauth2_callback
```

After: `password` removed from both; kept `authorization_code,refresh_token,client_credentials` (all three are supported on confidential clients in the new impl per lines 395–416 of the reference doc).

Also noted but NOT removed: `resourceIds=hybris` is soft-deprecated ("aren't used in the default configuration", line 198). Kept with a comment — platform still tolerates it; cleaning up is cosmetic and risks reformatting an essentialdata impex that other tooling may expect a stable column layout for.

Also noted: `oauth2.webroot=/authorizationserver` in `local.properties` — this property belonged to the old `oauth2` extension. The replacement `authorizationserver` extension hardcodes its webroot in `extensioninfo.xml` (`<webmodule webroot="/authorizationserver"/>`); no override is read from `local.properties`. Removed the property as dead config, replaced with a comment pointing to the extensioninfo.xml.

## The SAP-doc gap (if applicable)

The skill's `sap-docs/08-oauth-authorization-server.md` has the information (line 416), but it's buried deep in the "Authorized Grant Types for Public and Confidential Clients" section. A single-sentence callout in `00-overview.md`'s "Major structural changes" list or in `known-incidents.md` would flag this as a first-class expected residue. Right now a reader has to know to look for it.

## The fix that worked

In `essentialdata-infrastructure.impex`:
1. Remove `password` from the `authorizedGrantTypes` column for every OAuthClientDetails row. Any caller that was using the password grant must switch to authorization_code + PKCE or client_credentials.
2. Optional but tidy: remove `oauth2.webroot=` from `local.properties` — it's dead config under the new OAuth impl.

Build verification: `./gradlew yall` → BUILD SUCCESSFUL (no compile impact; the change is data-file only). Runtime verification deferred to Phase F (`yinitialize`), where the new validator will accept the clean grant list.

## Why this generalizes

Every SAP Commerce project migrating to 2211-jdk21.x with the stock sample/essential data ships with `password` grant in at least one client. That's the legacy default. Without removing it, Phase F `yinitialize` will fail at the create-data phase with a `InterceptorException` from `DefaultOAuthClientDetailsValidator` — a cryptic error for a team that's not expecting a grant-type change buried in an impex.

The related gotcha — `resourceIds` and `auto-approve` deprecation — is soft; platform still accepts them. Worth mentioning in the skill but not a build-breaker.

## Promotion suggestion

1. **`references/known-incidents.md`** — add a new incident (number TBD; at least #2, after the extension rename #0 and any other existing incidents):

   > **N. `password` grant type in OAuthClientDetails impex fails validation on `yinitialize`**
   > - **Symptom:** Phase F `yinitialize` fails at the create-data phase with `InterceptorException` from `DefaultOAuthClientDetailsValidator`, typically citing an OAuth client in essentialdata-*.impex.
   > - **Fix:** in every `INSERT_UPDATE OAuthClientDetails` row, remove `password` (and `implicit` if present) from the `authorizedGrantTypes` column. Kept grants on confidential clients: `authorization_code,refresh_token,client_credentials` + optional `custom_saml_token` if SAML is used. Any calling client that used the password grant must switch to authorization_code + PKCE or client_credentials.
   > - **Also check:** `resourceIds` column — soft-deprecated per `sap-docs/08-oauth-authorization-server.md` line 198 ("aren't used in the default configuration"). Platform tolerates it; clean up is cosmetic.
   > - **Reference:** `sap-docs/08-oauth-authorization-server.md` line 416 explicitly: *"The password flow and implicit flows are not available in the new implementation."*

2. **`scripts/plan-template.md` Phase D** — add a grep line item to D.1 "OAuth client data" checklist:

   ```markdown
   - [ ] `grep -rnE "authorizedGrantTypes.*password|authorizedGrantTypes.*implicit" core-customize/hybris/bin/custom --include="*.impex"` → zero matches expected. If hits: remove `password` and/or `implicit` from the grant-type list (not available in new OAuth impl).
   ```

3. **`references/00-overview.md` "Major structural changes" bullet "OAuth2 framework replaced"** — append a one-liner:
   > Additionally, the `password` and `implicit` grant flows are removed; update your OAuthClientDetails impex accordingly (see `sap-docs/08-oauth-authorization-server.md` line 416).

4. **`scripts/plan-template.md` Phase D** — also add a note about `oauth2.webroot=`:
   > - [ ] `grep -nE "oauth2\.webroot" core-customize/dev-config/local.properties core-customize/hybris/config/local.properties` → zero matches expected. If hits: remove; the property belongs to the removed `oauth2` extension. The new `authorizationserver` extension hardcodes its webroot (`/authorizationserver`) in `extensioninfo.xml`; no property override is supported.

## Downstream follow-ups

- At Phase F `yinitialize`, confirm create-data passes with the cleaned grant types. If any project still breaks, check whether any other impex (e.g., `projectdata-*.impex`) has additional OAuthClientDetails records.
- At Phase E tests, if any integration test acquires tokens via the password grant, those tests must be updated to use one of the supported flows.
