# Security & Secrets Policy

This document defines how secrets are handled in this repository and what must be
verified before any deployment beyond local development. It complements the full
security review in [docs/review/04-configuration-and-security.md](docs/review/04-configuration-and-security.md).

## Secrets policy

**Never commit a secret to this repository.** The enforced patterns:

| Secret | Where it lives | Never in |
|---|---|---|
| LLM API keys (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OPENAI_COMPATIBLE_API_KEY`) | Environment variables locally; Cloud Portal service properties on CCv2. The provider code reads **only** `System.getenv` — a key placed in a properties file is ignored by design. | Git, properties files, ImpEx |
| Database credentials | `dev-config/local.properties` carries local-MySQL defaults (`hybris`/`hybris`) for developer machines **only**. CCv2 injects real credentials via Cloud Portal. | Production property files |
| OAuth client secrets | `sampledatamcp` ships demo clients (`trusted_client`/`mobile_android`, secret `secret`) for local development. Production clients must be created by a production-only ImpEx with strong secrets injected via environment/Cloud Portal placeholders. | Any tracked file |
| Admin password | `initialpassword.admin=nimda` is a local-dev bootstrap value. Override in production via Cloud Portal. | Production deployments |

The structural safeguard: **all demo data — including demo OAuth clients and demo
customers (password `1234`) — lives in the `sampledatamcp` extension.** A production
build excludes that extension from `localextensions.xml`, which removes every demo
credential in one move.

## Endpoint security model

- All MCP and agent endpoints (`/{baseSiteId}/mcp`, `/agent/chat[/stream]`,
  `/agent/visual-search`) require `ROLE_CUSTOMERGROUP` or `ROLE_TRUSTED_CLIENT`
  via `@Secured`, enforced by the OCC v2 servlet's global method security.
- MCP requests additionally require a valid `MCP-Session-Id` (except `initialize`).
- The knowledge base (`/info/**`) is deliberately public (`ROLE_ANONYMOUS`) —
  informational content only, no PII.
- `/agent/*` endpoints are rate-limited per user (`coremcp.agent.rateLimit.perMinute`,
  default 20) because each request triggers paid LLM calls.
- Visual search enforces a payload cap (`coremcp.visualsearch.maxImageBytes`),
  a MIME whitelist, and base64 validation before any vision-model call.

## Production deployment checklist (CCv2)

Work through every item before promoting a build to staging or production:

1. **LLM keys** set as Cloud Portal service properties / environment variables
   (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, and/or `OPENAI_COMPATIBLE_API_KEY`).
2. **Database** credentials provided by Cloud Portal — the `dev-config` defaults
   must not reach a deployed environment.
3. **`sampledatamcp` excluded** from the production `localextensions.xml`
   (removes demo customers, demo orders, and demo OAuth clients).
4. **Production OAuth clients** created via a production-only ImpEx with strong,
   environment-injected secrets; the demo `trusted_client` must not exist.
5. **`initialpassword.admin`** overridden.
6. **CORS origins** (`corsfilter.acceleratorocc.allowedOrigins`) overridden to the
   production storefront domain(s) — never wildcards, never localhost.
7. **`coremcp.storefront.baseUrl`** set to the production storefront URL (deep
   links fall back to `http://localhost:5173` and log a warning otherwise).
8. **Swagger disabled** and **log level WARN** (both already set in
   `local-prod.properties` — verify the production persona is active).
9. **Patch level current** — SAP Commerce patch days regularly include security
   fixes for the platform itself; track the 2211 update releases.
10. **Session store** is `coremcp.session.store=persistent` (the default) so MCP
    conversations are cluster-safe and survive rolling deployments.

## Reporting

This is a reference/demo implementation. If you find a security issue, open an
issue marked confidential or contact the repository owner directly rather than
publishing details.
