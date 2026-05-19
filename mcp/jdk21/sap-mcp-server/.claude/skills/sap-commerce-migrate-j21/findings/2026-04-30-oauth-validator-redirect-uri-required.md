---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: F (surfaced at F.1; should be promoted to D)
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: new-incident
status: open
related_refs:
  - references/sap-docs/08-oauth-authorization-server.md (lines 248–268, validator preconditions)
  - references/known-incidents.md
  - findings/2026-04-30-password-grant-removed-from-new-oauth.md (same file; related residue)
  - scripts/plan-template.md (Phase D)
promotion_target: references/known-incidents.md (new entry); scripts/plan-template.md Phase D (grep pattern for authorization_code without redirect)
---

## What happened

At Phase F.1 `yinitialize`, the sample-data impex import failed with:

```
line 37: cannot create OAuthClientDetails with values ... {clientid=trusted_client,
    authorizedgranttypes=[authorization_code, refresh_token, client_credentials],
    registeredredirecturi=[], ...}
due to [com.sap.cx.commerce.platform.oauth2.authorizationserver.client.DefaultOAuthClientDetailsValidator]:
Validation error in OAuthClientDetailsModel - redirect uri attribute needs to be configured for authorization_code flow.
```

Phase D.1's fix had already removed the deprecated `password` grant. But the 2211-jdk21.x validator enforces MORE preconditions than just the grant-type allowlist:

> **"For a client using the authorization code grant type, at least one redirect URI is registered."** — `sap-docs/08-oauth-authorization-server.md` line 256.

Our `trusted_client` declared `authorization_code` but had an empty `registeredRedirectUri`. Legacy platforms ignored this; the new validator throws `InterceptorException`, which bubbles up to `createAutoImpexEssentialData` and aborts the entire create-data phase.

## Context

- **The full precondition list** per `sap-docs/08-oauth-authorization-server.md` lines 248–268:
  1. At least one grant type.
  2. At least one authority.
  3. Authorization_code grant REQUIRES a redirect URI.
  4. Public client has no secrets.
  5. Public client has PKCE enabled.
  6. Public client has no client_credentials grant.
  7. Confidential client has a client secret.
  8. The model successfully converts to Spring's `RegisteredClient`.
- Phase D's sweep caught only #1 (grant-type allowlist). The other 7 preconditions are only surfaced at yinitialize — or Backoffice edit — time, not by reading the source.
- Our fix: made `trusted_client` a pure server-to-server client (`client_credentials` only, no redirect URI). Kept `mobile_android` with `authorization_code,refresh_token` since it has a redirect URI.
- Validator dry-run property `authserver.client.validation.dry.run=true` exists (line 275) — logs WARNs instead of throwing. Useful for in-place migrations of large client catalogs, but default (strict) is correct for fresh installs.

## The SAP-doc gap (if applicable)

`sap-docs/08-oauth-authorization-server.md` does document the preconditions (lines 248–268), but as a prose list inside a section titled "Client Validation" that most readers skip because it doesn't read as migration-relevant. The skill should surface these as first-class Phase D residues — one grep pattern per precondition — so projects catch all 7 in the same pass instead of hitting them one-at-a-time via failed yinitializes.

The general update guide doesn't mention client-level validation at all.

## The fix that worked

Edit `essentialdata-infrastructure.impex` (or equivalent):

```impex
# BEFORE — trusted_client had authorization_code but no redirect URI
; trusted_client ; hybris ; extended ; authorization_code,refresh_token,client_credentials ; ROLE_TRUSTED_CLIENT ; secret ;

# AFTER — trusted_client is server-to-server only
; trusted_client ; hybris ; extended ; client_credentials                                   ; ROLE_TRUSTED_CLIENT ; secret ;
```

Verified by `./gradlew yinitialize` BUILD SUCCESSFUL on the retry (3m 1s vs. the earlier 2m 31s failure).

## Why this generalizes

Legacy SAP Commerce sample data commonly declared one "do-everything" OAuth client (`trusted_client`) that listed every supported grant type regardless of whether the client's usage actually needed them. This was wasteful even on legacy platforms (why expose an `authorization_code` flow from a backend admin client?) but worked because the validator was lenient. On 2211-jdk21.x, the new validator catches the cruft.

Similarly, the other 6 preconditions will trip projects whose sample data predates them. Worth grep-ing for:
- Public clients with non-null clientSecret
- Public clients with `requireProofKey=false`
- Clients with no authority assigned
- authorization_code clients with an empty redirect URI (the one we hit)

All are mechanical fixes once spotted.

## Promotion suggestion

1. **`references/known-incidents.md`** — add a new incident (number TBD):

   > **N. OAuthClientDetails validator rejects legacy client configurations at yinitialize**
   > - **Symptom:** Phase F `yinitialize` fails at `Creating essential data for sampledatamcp` (or similar) with `InterceptorException` from `DefaultOAuthClientDetailsValidator`. Error message cites the specific precondition violated (e.g., "redirect uri attribute needs to be configured for authorization_code flow").
   > - **Fix:** review each `OAuthClientDetails` row in custom impex against the 8 preconditions in `sap-docs/08-oauth-authorization-server.md` lines 248–268:
   >   1. authorization_code grant → must have a non-empty `registeredRedirectUri`
   >   2. public client → must have null/empty `clientSecret`
   >   3. public client → must have `requireProofKey=true` (PKCE)
   >   4. public client → must NOT have `client_credentials` grant
   >   5. confidential client → must have a `clientSecret`
   >   6. every client → ≥1 grant type, ≥1 authority
   >   7. model must convert cleanly to Spring's `RegisteredClient`
   > - **Fast path:** if a client carries `authorization_code` purely as legacy cruft (server-to-server clients often do), remove it. If it's genuinely needed, add a redirect URI.
   > - **Dev-time workaround for large client catalogs:** temporarily set `authserver.client.validation.dry.run=true` to log WARNs instead of throwing.

2. **`scripts/plan-template.md` Phase D** — add a grep line item:

   ```markdown
   - [ ] Audit every OAuthClientDetails impex row for the 7 validator preconditions (see known-incidents entry N). Quick grep for the common one:
         `grep -rnE 'authorization_code[^;]*;[^;]*;$' core-customize/hybris/bin/custom --include="*.impex"` (matches rows with authorization_code and empty trailing registeredRedirectUri column — refine per your impex's column layout).
   ```

3. **`references/00-overview.md` "Major structural changes" OAuth bullet** — append:
   > The new validator enforces 8 preconditions on `OAuthClientDetails` rows; legacy "do-everything" clients typically fail on the redirect-URI-for-authorization_code rule. See `sap-docs/08-oauth-authorization-server.md` lines 248–268.

## Downstream follow-ups

- None immediate. Our `trusted_client`'s client_credentials-only shape is the right one for server-to-server admin usage; no functional regression.
- If a project's ACTUAL usage pattern needs `authorization_code` on an admin client (unusual), add a redirect URI — typically an absolute localhost URL for dev or a real callback endpoint.
