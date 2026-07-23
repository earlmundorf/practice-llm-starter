# UCP profile — context

## What this flow does

Serves the public UCP discovery document at

```
GET /occ/v2/{baseSiteId}/.well-known/ucp        (ROLE_ANONYMOUS)
```

A UCP client reads this profile first and learns everything it needs: the
pinned protocol version, the declared capabilities, the transport endpoint(s),
and the accepted payment handlers. Nothing about ThinkShop is hard-coded in a
client — "hook up UCP" means publishing a correct profile and implementing
what it advertises.

## When it's used

Once per client bootstrap (diagram S1 in the design discussion): the harness
(`scripts/ucp-e2e.py`) fetches it anonymously before obtaining a token; a
future agent client (Gemini/ADK) would do the same.

## Key decisions

- **Non-root path concession** (design R6, ADR 0001): UCP wants the profile at
  the domain root; SAP Commerce lives under context paths. Locally the profile
  is served inside OCC; production needs an edge rewrite (see docs/README.md).
- **The profile only advertises what works.** Capabilities were added phase by
  phase as they became fully functional: `dev.ucp.shopping.catalog` (Phase 2),
  `.checkout` + the `thinkshop_mock_card` payment handler (Phase 5),
  `.order` + the custom `com.thinkshop.promotions` / `com.thinkshop.knowledge`
  capabilities (Phase 6). The `rest` transport entry appears only when the
  REST binding lands (Phase 7).
- **Custom reverse-domain capabilities** (design R7): promotions metadata and
  knowledge-base content are first-class, discoverable capabilities under the
  `com.thinkshop.*` namespace — versioned like the standard set, but with no
  hosted spec/schema URLs.
- **Pinned version everywhere**: every capability entry and every payload
  envelope carries `ucpcommerce.ucp.version` (default `2026-04-08`).
- **Configurable public base URL** (`ucpcommerce.public.base.url`): the
  advertised transport endpoints must be the base a client can actually reach,
  so tunnel/edge deployments can override the local default.
