---
date: 2026-05-01
project: sap-mcp-server-g
phase: D (also relevant to E and to anyone adding public OAuth clients post-migration)
applies_to:
  java_from: any
  spring_from: any
  commerce_from: any
  target: 2211-jdk21.x
  feature: Spring Authorization Server public clients (frontend / SPA / mobile native)
kind: gotcha
status: unpromoted
related_refs:
  - findings/2026-04-30-oauth-validator-redirect-uri-required.md (the parent — 8 validator preconditions, item #5 "public client" enforcement)
  - findings/2026-04-30-password-grant-removed-from-new-oauth.md
  - references/sap-docs/08-oauth-authorization-server.md (lines 248–268 client validation)
  - references/known-incidents.md (incident #0 oauth2 ext rename)
promotion_target: references/known-incidents.md (new entry "OAuthClientDetails public-client setup") + references/phase-guide.md Phase D D.2 (note about public=true requirement)
---

## What happened

Adding a frontend OAuth client (Vite dev server at `http://localhost:5173`) to SAP Commerce 2211-jdk21.x via ImpEx. Initial attempt followed the obvious shape — empty `clientSecret`, `authorization_code,refresh_token` grants, `ROLE_CLIENT` authority:

```impex
INSERT_UPDATE OAuthClientDetails;clientId[unique=true];resourceIds;scope;authorizedGrantTypes;authorities;clientSecret;registeredRedirectUri
;client-side;hybris;basic;authorization_code,refresh_token;ROLE_CLIENT;;http://localhost:5173/auth/callback
```

`DefaultOAuthClientDetailsValidator` rejects with:

```
Validation error in OAuthClientDetailsModel - confidential client should have a client secret.
```

Adding `requireProofKey=true` (PKCE) does NOT fix it — the validator still treats the client as confidential.

## Root cause

`OAuthClientDetails` has a separate **`public`** boolean attribute (column `p_public` in DB) that classifies the client as confidential vs public. The classification is **not** inferred from `requireProofKey` or from an empty `clientSecret`. The validator preconditions are:

- **Confidential** client (`public=false`, the default) → MUST have a non-empty `clientSecret`. The check is the literal "is clientSecret null/empty" question, gated by `public=false`.
- **Public** client (`public=true`) → must NOT have a `clientSecret`; SHOULD have `requireProofKey=true` (PKCE).

So an empty `clientSecret` with `public=false` (default) trips the "confidential client should have a client secret" error every time. The fix is to set BOTH `public=true` and `requireProofKey=true` explicitly.

## The fix that worked

```impex
INSERT_UPDATE OAuthClientDetails;clientId[unique=true];resourceIds;scope;authorizedGrantTypes;authorities;clientSecret;registeredRedirectUri;public;requireProofKey
;client-side;hybris;basic;authorization_code,refresh_token;ROLE_CLIENT;;http://localhost:5173/auth/callback;true;true
```

Verified via FlexibleSearch:

```
p_clientid      p_public  p_requireproofkey
--------------  --------  -----------------
trusted_client  false     true
mobile_android  false     true
client-side     true      true
```

## Why this generalizes

Any project adding a frontend / SPA / mobile-native OAuth client to 2211-jdk21.x will hit this. The error message ("confidential client should have a client secret") is misleading because the developer thinks "I want a public client, that's why I left the secret empty" — but the platform's classification of public vs confidential isn't inferred, it's an explicit attribute that has to be set.

This is the exact same shape of problem as the existing `2026-04-30-oauth-validator-redirect-uri-required.md` finding (the validator enforces preconditions strictly that previous platforms tolerated). That finding documented preconditions #1–#8 in prose; this finding adds the operational detail of HOW to set `public=true` via ImpEx (which the prose finding didn't include).

## Promotion suggestion

1. **`references/known-incidents.md`** — new incident:

   > **N. Adding a public OAuth client (frontend / SPA / mobile-native) to 2211-jdk21.x ImpEx**
   > - **Symptom:** ImpEx `INSERT_UPDATE OAuthClientDetails` with empty `clientSecret` rejected with "confidential client should have a client secret" even when `requireProofKey=true` is set.
   > - **Cause:** `OAuthClientDetails` has a separate `public` boolean attribute (defaults to `false`). Empty `clientSecret` only passes validation when `public=true` is also set explicitly.
   > - **Fix:** add the columns `public` and `requireProofKey` (both `true`) to the ImpEx header. Example:
   >   ```impex
   >   INSERT_UPDATE OAuthClientDetails;clientId[unique=true];...;clientSecret;registeredRedirectUri;public;requireProofKey
   >   ;client-side;...;;http://localhost:5173/auth/callback;true;true
   >   ```
   > - **Verify:** `SELECT {clientId},{public},{requireProofKey} FROM {OAuthClientDetails}` should show `true,true` for the new row.

2. **`references/phase-guide.md` Phase D D.2** — append a bullet:

   > For each OAuthClientDetails row whose `clientSecret` is intentionally empty (frontend / SPA / mobile-native public clients), set `public=true` AND `requireProofKey=true`. The validator does NOT infer public/confidential from emptiness — it reads the `public` attribute directly.

3. **`scripts/hac-impex.sh` parsing fix (already landed in this same commit)** — the success/error markers in the 2211-jdk21.x HAC are `data-result="Import finished successfully."` / `data-result="Import has encountered problems."`, NOT the older `successfull` / `unsuccessfull` strings. Updated the script's parser; replaced the old string-match with a regex on `data-result="..."`.
