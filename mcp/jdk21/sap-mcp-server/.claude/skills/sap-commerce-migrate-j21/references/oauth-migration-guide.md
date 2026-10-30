# New OAuth (Spring Authorization Server) — Migration Guide

> **Make-it-work companion to** `sap-docs/08-oauth-authorization-server.md` (the authoritative SAP
> mirror). This file is the curated, ordered path to get OAuth working on **2211-jdk21.x**, plus a
> validated client cookbook and the SAML impact. When this guide and doc-08 ever disagree, doc-08 wins.

## 1. What changed, and why (general explanation)

The EOL Spring Security OAuth 2.5.x library (end-of-life May 2022) has been replaced by **Spring
Authorization Server** on Spring Security 6.x. In 2211-jdk21.x the single `oauth2` extension is gone,
split into three extensions that ship in the default configuration:

| Extension | Role |
|---|---|
| `oauth2commons` | Shared OAuth types/config used by both the auth-server and resource-server sides. |
| `authorizationserver` | The authorization server: token issuance, flows, client validation, the `/authorizationserver/oauth/*` endpoints. Hardcodes its webroot in `extensioninfo.xml`. |
| `resourceserver` | The resource-server side that protects OCC and verifies presented tokens. |

**Tokens are now signed JWTs only — opaque tokens are no longer supported.** The runtime flow:

1. A client requests an access token from the authorization server.
2. The authorization server returns a **signed JWT** access token.
3. The client calls a protected resource with the JWT (`Authorization: Bearer <jwt>`).
4. The resource server verifies the JWT signature against public keys at the **JWK endpoint** and
   checks permissions. For the authorization-code flow, **user roles are not baked into the token** —
   they are resolved from the database at request time from the signed-in user's groups.

Conceptually: authorization decisions moved from library-managed opaque-token lookups to
standards-based signed JWTs verified via JWKS, and client configuration is now validated strictly
up front (see §4).

**Endpoints** (all under `/authorizationserver/oauth/`): `authorize`, `token`, `jwks`, `userinfo`,
`revoke`, `introspect`, and the issuer root. Token/revoke/introspect accept `client_secret_basic`
and `client_secret_post`. In Spring 6, OAuth parameters must be sent in the **request body**, not the
URL query string.

## 2. Make-it-work checklist

Work these in order. Each links to its step below.

- [ ] Swap the `oauth2` extension → `oauth2commons` + `authorizationserver` + `resourceserver` (§3.1)
- [ ] Delete dead `oauth2.*` properties (§3.2)
- [ ] Remove `password` / `implicit` grants; migrate their callers (§3.3)
- [ ] Set `public=true` + `requireProofKey=true` on public clients (§3.4)
- [ ] Expect PKCE by default on authorization-code flows (§3.5)
- [ ] Use absolute redirect URIs (§3.6)
- [ ] Clean up orphaned OAuth types after init (§3.7)
- [ ] Verify with FlexibleSearch + a token smoke test (§3.8)

## 3. Steps

### 3.1 Swap the extension

`grep -n 'extension name="oauth2"' core-customize/dev-config/localextensions.xml`

If it hits, replace the single line with the subset you need (most OAuth projects need all three):

```xml
<!-- before -->
<extension name="oauth2" />
<!-- after -->
<extension name="oauth2commons" />
<extension name="authorizationserver" />
<extension name="resourceserver" />
```

Re-run `./gradlew bootstrapPlatform setupConfig` after the edit. See `known-incidents.md` #0.

### 3.2 Delete dead `oauth2.*` properties

`grep -nE "oauth2\.webroot" core-customize/dev-config/local*.properties core-customize/hybris/config/local*.properties 2>/dev/null`

Remove `oauth2.webroot=` and other `oauth2.*` keys tied to the removed extension — the new
`authorizationserver` hardcodes its webroot (`/authorizationserver`) in `extensioninfo.xml`; no
property override is read.

### 3.3 Remove `password` / `implicit` grants

`grep -rnE "authorizedGrantTypes.*(password|implicit)" core-customize/hybris/bin/custom --include="*.impex"`

Both flows are **removed**. Delete them from every `OAuthClientDetails` row. Migrate callers:
user-facing flows → **authorization_code + PKCE**; service-to-service → **client_credentials**.
On a fresh `yinitialize` a leftover `password` grant fails create-data with an `InterceptorException`
from `DefaultOAuthClientDetailsValidator`. See `known-incidents.md` #8.

### 3.4 Public clients need `public=true` AND `requireProofKey=true`

The validator does **not** infer public vs confidential from an empty `clientSecret`. A public client
(SPA / mobile-native) with an empty secret but `public` unset (default `false`) is treated as
confidential and rejected: *"confidential client should have a client secret."* Set both attributes
explicitly (see the cookbook §5). See `known-incidents.md` #9.

### 3.5 PKCE is on by default

`requireProofKey` defaults to `true`, so authorization-code requests must send
`code_challenge` + `code_challenge_method=S256`. Missing it yields
`error=invalid_request&error_description=OAuth 2.0 Parameter: code_challenge`. For confidential
clients you may set `requireProofKey=false` if PKCE isn't wanted.

### 3.6 Absolute redirect URIs

Relative redirect URIs are rejected. Use absolute URIs. Dev-only escape hatch:
`authserver.authorizationCode.absolute.redirect.uri.check=false` (default `true`).

### 3.7 Orphaned-type cleanup (after init)

In HAC → Maintenance → Cleanup → Type System, clean up the deprecated `oauth2` types:
`OAuthAccessToken`, `OAuthAuthorizationCode`, `OAuthRefreshToken`, `User2TokenRelation`.

### 3.8 Verify

```sql
SELECT {clientId},{public},{requireProofKey} FROM {OAuthClientDetails}
```

```bash
# client_credentials smoke test (confidential client)
curl -k -X POST "https://<server>/authorizationserver/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials' \
  --basic --user <client_id>:<secret>
```

Expect a JWT in the `access_token` field.

## 4. The 8 client-validation preconditions

`DefaultOAuthClientDetailsValidator` enforces these on every create/update of `OAuthClientDetails`
(doc-08 "Client Validation"). Any failure throws `InterceptorException`.

1. At least one authorized grant type.
2. At least one assigned authority.
3. Authorization-code grant ⇒ at least one registered redirect URI.
4. A public client has no client secret.
5. A public client has PKCE activated.
6. A public client has no `client_credentials` grant.
7. A confidential client has a client secret.
8. The model converts cleanly to a Spring `RegisteredClient`.

Detection greps for the common failures:

```bash
# authorization_code rows with an empty trailing registeredRedirectUri (precondition 3)
grep -rnE 'authorization_code[^;]*;[^;]*;\s*$' core-customize/hybris/bin/custom --include="*.impex"
# empty-secret clients (candidate public clients missing public=true — preconditions 4/7)
grep -rnE 'INSERT_UPDATE OAuthClientDetails' core-customize/hybris/bin/custom --include="*.impex"
```

**Dev-time workaround for large client catalogs:** `authserver.client.validation.dry.run=true` logs
violations as warnings instead of throwing. Keep it `false` (strict) for fresh installs.

## 5. Client cookbook

Copy-paste `OAuthClientDetails` rows that pass validation on 2211-jdk21.x. Column order shown in each
header; adjust to your impex's layout.

**Server-to-server (backend/admin, no user login):** `client_credentials` only, has a secret, no redirect URI.

```impex
INSERT_UPDATE OAuthClientDetails;clientId[unique=true];resourceIds;scope;authorizedGrantTypes;authorities;clientSecret;registeredRedirectUri
;trusted_client;hybris;extended;client_credentials;ROLE_TRUSTED_CLIENT;secret;
```

**Confidential web app (server-side login):** secret + auth-code/refresh + redirect URI.

```impex
INSERT_UPDATE OAuthClientDetails;clientId[unique=true];resourceIds;scope;authorizedGrantTypes;authorities;clientSecret;registeredRedirectUri
;web_app;hybris;basic;authorization_code,refresh_token;ROLE_CLIENT;secret;https://your.host/auth/callback
```

**Public SPA / mobile-native:** no secret, `public=true`, `requireProofKey=true`, redirect URI, no `client_credentials`.

```impex
INSERT_UPDATE OAuthClientDetails;clientId[unique=true];resourceIds;scope;authorizedGrantTypes;authorities;clientSecret;registeredRedirectUri;public;requireProofKey
;client-side;hybris;basic;authorization_code,refresh_token;ROLE_CLIENT;;https://your.host/auth/callback;true;true
```

**Punchout (B2B):** the `punchout_token` grant; confidential, secret required (doc-08 "Punchout Token for Punchout Flow").

```impex
INSERT_UPDATE OAuthClientDetails;clientId[unique=true];resourceIds;scope;authorizedGrantTypes;authorities;clientSecret;registeredRedirectUri
;punchout_client;hybris;basic;punchout_token;ROLE_CLIENT;secret;
```

Configure `b2bpunchout.oauthclient.clientId` / `b2bpunchout.oauthclient.clientSecret` and the
`b2bpunchout.oauth.*` host/token-URL/keystore properties as needed.

**SmartEdit (SAML SSO):** a real working row lives in `sap-docs/11-smartedit.md` — a public client
with `authorization_code,saml_token`, PKCE on. See §6.

## 6. What this means for SAML SSO

SAML single sign-on is **preserved but re-plumbed** onto the **custom SAML token flow**, supported for
both public and confidential clients. Key points:

- **Requires the `samlsinglesignon` extension** in the build.
- The client must **explicitly list the SAML grant** in `authorizedGrantTypes` — the attribute value
  is **`saml_token`** (validator precondition 1: at least one grant type). SmartEdit's shipped client
  uses `authorization_code,saml_token` (see `sap-docs/11-smartedit.md`).
- Request shape: `grant_type=saml_token`, `client_id`, and `client_secret` (confidential clients only),
  optional space-separated `scope`. The request must carry the SSO login token whose cookie name
  matches the `sso.cookie.name` property.
- Returns access tokens, and refresh tokens **only if** the client also lists `refresh_token`.

**Impact of the wider OAuth changes on SAML:**

- **Password/implicit removal (§3.3)** — any SSO-adjacent client that relied on the password or
  implicit flow must move to `saml_token` or authorization_code + PKCE.
- **Custom token granter mechanism changed** — the old `TokenGranter` interface is gone. Custom flows
  (including SAML-style ones) are now built with `CustomAuthenticationConverter` /
  `AbstractCustomAuthenticationToken`. See doc-08 "Custom Token Granter".
- **SmartEdit** — SSO authentication in SmartEdit has front-end breaking changes; relevant only if you
  have customized SmartEdit extensions. See doc-08 "Breaking Changes in SmartEdit" and
  `sap-docs/11-smartedit.md`.

## 7. Dev-time escape hatches

| Property | Effect | Use |
|---|---|---|
| `authserver.client.validation.dry.run=true` | Validator logs violations as warnings instead of throwing. | In-place migration of a large client catalog. Revert to `false`. |
| `authserver.authorizationCode.absolute.redirect.uri.check=false` | Allows relative redirect URIs. | Only if absolute URIs are impossible in your config. |

Fresh installs should keep both at their strict defaults.

## 8. See also (deep topics — not re-treaded here)

`sap-docs/08-oauth-authorization-server.md` covers, in depth: custom token granters, the full SAML
token flow, OIDC as an identity provider, token TTL configuration, keystore/JWK rotation, token
revocation, brute-force protection, scope-handling changes, and JWT customization.
Also: `sap-docs/09-resource-server.md` (OCC protection) and `sap-docs/10-resttemplate-removal.md`
(`RestTemplate`/`OAuth2RestTemplate` replacement).
