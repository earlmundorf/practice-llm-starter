# Configuration & Security Review

Scope: `manifest.json`, the persona property files under `core-customize/dev-config/`, OAuth and endpoint security, secrets handling, CORS, Solr security, and operational scripts — benchmarked against SAP's published Commerce Cloud security guidance (secrets in Cloud Portal service properties, non-wildcard production CORS, confidential OAuth clients for trusted operations).

## 1. Configuration architecture — done right

- **manifest.json** declares `commerceSuiteVersion: 2211.50`, uses the `useConfig` pattern with persona-targeted property files, and defers extensions to `localextensions.xml` (the two are consistent). The CCv2 Gradle plugins (`sap.commerce.build` / `sap.commerce.build.ccv2` v5.0.2) are wired in `build.gradle`.
- **Persona separation is exemplary:** base `local.properties` (OCC/OAuth, CORS, Swagger, debug logging) with `local-dev` (hot-reload, DEBUG), `local-stg` (caching, INFO), `local-prod` (caching, WARN, **Swagger disabled**). One artifact deploys across environments — exactly SAP's documented intent.
- **Tracked vs. generated config:** `dev-config/` is tracked; the Gradle `setupConfig` task overlays it into gitignored `hybris/config/`. Clean and reproducible.
- **Scripts** (`index-solr.sh`, `setup-promotions.sh`) use `set -e`, robust path resolution, file-existence checks, and embed no credentials.

## 2. Secrets handling

### Exemplary: LLM API keys

All three providers read keys **exclusively from environment variables** (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OPENAI_COMPATIBLE_API_KEY`), and `local.properties` carries an explicit comment saying keys must never be put in the file. No key, in any form, is committed anywhere in the repository. This matches SAP's recommendation (secrets via Cloud Portal service properties / environment, never in Git) and is the pattern to show off.

### Findings — demo credentials that need a documented production boundary

These are all *acceptable for a demo* but each needs an explicit "not past dev" guardrail, because the gap between "demo project" and "copied into a client project" is where these leak:

| # | Finding | Location | Severity | Required action |
|---|---|---|---|---|
| S-1 | DB credentials in tracked file (`db.username=hybris` / `db.password=hybris`, `useSSL=false`, `allowPublicKeyRetrieval=true`) | `dev-config/local.properties:19-22` | High (if promoted) | Document as local-only; CCv2 prod gets DB from Cloud Portal — add to the deployment checklist; prefer SSL even locally |
| S-2 | OAuth clients with literal secret `secret` (`trusted_client`, `mobile_android`) | `sampledatamcp/resources/impex/essentialdata-infrastructure.impex:32-34` | High (if promoted) | Keep for demo; production OAuth clients must come from a separate, environment-injected ImpEx (`${oauthclient.*.secret}` placeholders). Note `trusted_client` maps to `ROLE_TRUSTED_CLIENT`, which the MCP/agent endpoints accept — this client must be confidential in any real deployment |
| S-3 | `initialpassword.admin=nimda` | `dev-config/local.properties:6` | Medium | Override in prod persona / Cloud Portal; add to checklist |
| S-4 | Demo customer passwords `1234` | `projectdata-sampledatamcp.impex` (`$defaultPassword`) | Low (demo data) | Excluding `sampledatamcp` from prod (already the design) resolves it; document that fact |

The structural mitigation already exists: **all demo data lives in `sampledatamcp`, which production simply doesn't include.** The missing piece is writing that rule down — hence the `SECURITY.md` + deployment checklist task in Phase 4.

## 3. Endpoint security — verified, strong

Method security is genuinely enforced: the OCC v2 servlet enables `global-method-security secured-annotations="enabled"` (verified in the platform module source), so the `@Secured` annotations below are live, not decorative:

| Endpoint | Security | Notes |
|---|---|---|
| `POST/DELETE /{baseSiteId}/mcp` | `ROLE_CUSTOMERGROUP`, `ROLE_TRUSTED_CLIENT` + `MCP-Session-Id` validation on every method except `initialize` | JSON-RPC errors for invalid/missing session |
| `POST /{baseSiteId}/agent/chat[/stream]` | `ROLE_CUSTOMERGROUP`, `ROLE_TRUSTED_CLIENT` | |
| `POST /{baseSiteId}/agent/visual-search` | `ROLE_CUSTOMERGROUP`, `ROLE_TRUSTED_CLIENT` | 10MB size cap + MIME whitelist (jpeg/png/webp/gif) |
| `GET /{baseSiteId}/info/**` | + `ROLE_ANONYMOUS` | Deliberate: knowledge base is public content, no PII |

No `permitAll` on anything sensitive. Cart/checkout/order tools additionally require an authenticated customer session at the facade layer.

**Gaps (Medium/Low):** no rate limiting on `/agent/*` (each call costs real LLM money — an authenticated-but-hostile client can run up spend), no max `pageSize` on knowledge search, no message-count cap on chat. These are guards, not redesigns — Phase 2, task 2.4.

## 4. CORS

```
corsfilter.acceleratorocc.allowedOrigins=http://localhost:3000,http://localhost:4200,http://localhost:5173
corsfilter.acceleratorocc.allowedHeaders=...,mcp-session-id,mcp-protocol-version
corsfilter.acceleratorocc.exposedHeaders=mcp-session-id
```

Specific origins (no wildcard), MCP headers explicitly allowed and exposed, credentials enabled only with pinned origins. This matches SAP's guidance (wildcards acceptable in dev only, never prod). Action: prod persona must override origins to the real storefront domain — checklist item, not a defect.

## 5. Other configuration findings

| # | Finding | Severity | Action |
|---|---|---|---|
| C-1 | `DeepLinkBuilder` falls back to `http://localhost:5173` when `coremcp.storefront.baseUrl` is unset — production deep links would silently point at localhost | Medium | Warn-log when the default is used; list the property in the deployment checklist |
| C-2 | Local platform 2211.38 vs manifest 2211.50 (see document 02) | Medium | Reconcile |
| C-3 | Solr BasicAuth `security.json` with hashed credentials in the config tree | Low | Local-dev Solr only; CCv2 manages Solr as a service. Ensure the config path stays gitignored |
| C-4 | No HSTS/forced-HTTPS config locally | Low | CCv2 ingress terminates TLS; note in checklist |
| C-5 | Operational tunables hardcoded in Java (session TTL, max iterations, vision model…) | Medium | Externalize — see code review 2.6 / Phase 1 |

## 6. CCv2 production deployment checklist (to become part of SECURITY.md)

1. All LLM keys set as Cloud Portal service properties / environment variables — never in the repo. ✅ pattern already enforced by code
2. DB credentials from Cloud Portal (ignore `dev-config` defaults).
3. `sampledatamcp` **excluded** from production `localextensions.xml` (removes demo customers, demo OAuth secrets, demo orders).
4. Production OAuth clients created via separate ImpEx with strong, injected secrets; `trusted_client` replaced.
5. `initialpassword.admin` overridden.
6. CORS origins overridden to production domains.
7. `coremcp.storefront.baseUrl` set to the production storefront.
8. Swagger off (already in `local-prod.properties`), log level WARN (already), and current 2211 patch applied (SAP's May 2026 patch day included a Commerce RCE fix — patch currency is itself a security control).

## 7. Verdict

The security *architecture* is sound and in two places (LLM key handling, endpoint security verified down to the platform's method-security switch) genuinely above average. Every finding in this document is either a demo-by-design credential needing a documented production boundary, or an operational guard (rate limits, checklists) — all scheduled in the plan. Nothing here requires structural change.
