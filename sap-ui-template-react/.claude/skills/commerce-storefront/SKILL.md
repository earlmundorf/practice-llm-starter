---
name: commerce-storefront
description: |
  Constructing a commerce front end against SAP Commerce (Hybris): the OCC v2 REST
  contracts and the patterns this storefront uses to consume them — OAuth2 auth, cart
  lifecycle, product search/facets, the checkout sequence, order history — plus the
  AI-commerce endpoints exposed by the coremcp backend (agent chat with SSE streaming,
  knowledge base, visual search) and how to build UI for them. Use this skill when
  adding or changing anything that talks to the backend.

  Trigger this skill when the user mentions: OCC, OAuth, token, login, cart, checkout,
  place order, delivery mode, payment, product search, facets, order history, agent
  chat, chat widget, SSE/streaming, knowledge base/info endpoints, visual search, or
  asks how the storefront integrates with SAP Commerce / the MCP backend. For general
  React how-to use react-typescript; for reviews use react-ecommerce.
allowed-tools: [Read, Write, Edit, Grep, Glob, Bash(curl *), Bash(npm *), Bash(find *)]
---

# Building a Commerce Front End (SAP Commerce + coremcp)

You are an expert storefront engineer for SAP Commerce backends. The backend for this
template is the `sap-mcp-server-l` project (monorepo sibling at
`../mcp/legacy/sap-mcp-server-l/`); its API reference docs are authoritative for
contracts: `core-customize/hybris/bin/custom/coremcp/docs/reference/endpoints.md` and
`tools.md`. The frontend ground truth is `src/services/api.ts` + `src/types/index.ts` —
extend those; never call `fetch` from components.

## Topology

SPA (Vite, :5173) → dev proxy (`/occ`, `/authorizationserver` → `VITE_API_URL`, default
`https://localhost:9002`, self-signed; the proxy strips the Origin header to sidestep
CORS) → SAP Commerce OCC v2 at `/occ/v2/electronics`. In production the SPA is static
(S3/CloudFront) and `VITE_API_URL` points at the real Commerce API.

## OCC conventions that bite

- **`fields=FULL`** on detail-bearing calls or you get skeleton responses.
- **Pagination is 0-indexed** (`currentPage=0` is the first page).
- **Search query syntax** packs the sort into the query param: `query=<text>:<sort>`
  (e.g. `laptop:price-asc`); facet selections append `:<facet>:<value>` pairs.
- **Prices:** render `price.formattedValue`, never format `value` yourself
  (`formatPrice()` exists for app-side cases).
- **Empty bodies:** DELETE and some PUTs return nothing — `apiFetch` already tolerates it.
- Category codes for this catalog: `computing`, `mobile`, `audio`, `accessories`,
  `swag`, `swag-apparel`, `swag-drinkware`, `swag-accessories`.

## Auth (implemented — `auth` in api.ts)

Resource-owner password grant against `/authorizationserver/oauth/token`
(`trusted_client`/`secret` demo client; HTTPS required — plain HTTP 302s). Token +
expiry in `localStorage` (dev-acceptable; a production app uses a BFF or httpOnly
cookies — see the review skill's caveat). `apiFetch` attaches the Bearer header, and on
**401** logs out and dispatches `authExpired` — UI listens and prompts re-login.
`authChanged` fires on login/logout for header state.

## Cart lifecycle (implemented)

`ensureCart()` lazily POSTs `/users/current/carts` and caches the code per session;
every cart/checkout call goes through it. Mutations dispatch `cartUpdated`
(CartContext re-fetches). `placeOrder` consumes the cart — reset the cached code after.
Anonymous carts (`/users/anonymous/carts/<guid>`) are not implemented; the template
assumes login-first shopping.

## Checkout sequence (order matters — same as the backend's MCP tools)

1. cart has entries → 2. `POST .../addresses/delivery` → 3. `GET .../deliverymodes`,
`PUT .../deliverymode?deliveryModeId=` → 4. `POST .../paymentdetails` →
5. `POST /users/current/orders?cartId=`. Disable the Place Order button during the call
— double-submit creates duplicate orders.

## AI endpoints (coremcp — the build-next surface)

These exist on the backend and have **no client code in this template yet**. Contracts
(full detail in the backend's `reference/endpoints.md`):

| Endpoint | Notes |
|---|---|
| `POST /{site}/agent/chat` | Body `{messages:[{role,content}], cartCode?}` → `{reply, messages, cartCode?, action?, entityRefs?}`. Requires Bearer token. |
| `POST /{site}/agent/chat/stream` | Same body; `text/event-stream` when enabled: `event: text` (JSON-encoded delta), `event: tool` (`{name}` — render "Looking up…"), `event: done` (full chat response), `event: error`. **Transparently returns plain JSON** when streaming is off or stripped — branch on the response Content-Type, never assume SSE. |
| `GET /{site}/agent/capabilities` | `{vision: boolean}` — gate image attachments on this. |
| `GET /{site}/info/search?q=&category=&pageSize=` and `/info/{uid}` | Knowledge base (policies, events, how-tos). **Public** — works without a token. |
| `POST /{site}/agent/visual-search` | `{image: <base64>, mimeType}` → vision analysis + tiered product matches in OCC product shape. Max ~10MB base64; jpeg/png/webp/gif. |

Agent response fields the UI must honor:
- **`reply`** ends with a `SUGGESTIONS:["...","..."]` line — parse it off the text and
  render as quick-action buttons; never display it raw.
- **`entityRefs`** `[{type: product|order|orderHistory, code?}]` — render as clickable
  chips (the agent is prompted NOT to emit URLs; chips are the navigation).
- **`cartCode`** — store and send back on the next turn so multi-turn tool calls hit
  the same cart; refresh CartContext when it changes.
- **`action`** (e.g. `"checkout"`) — client-side navigation trigger.
- **Guards:** 429 (rate limit, default 20/min/user) → friendly retry message;
  400 for empty/oversized `messages` (cap 50).

### Chat client pattern

Send the full message history each turn (the server prepends its own system prompts and
returns updated history — keep server truth). For the stream endpoint: POST with
`Accept: text/event-stream`, read the body with a `ReadableStream` reader, split on
double newlines, dispatch on `event:`; if Content-Type is `application/json`, parse it
as a complete non-streamed response (the documented fallback). Abort with
`AbortController` when the user navigates away.

## When adding any backend call

1. Contract first: check the backend reference docs (or `curl -k` the endpoint).
2. Types in `src/types/index.ts`, method on `api` in `src/services/api.ts`.
3. Loading + error + empty states in the consuming component; `showToast` on mutations.
4. Feature flow doc in `docs/<flow>/` (context/components/diagram) — docs before code.

## Related skills

- **react-typescript** — general React/TS/npm how-to in this repo
- **react-ecommerce** — review skill (includes API-integration review patterns)
- **storefront-qrspi** — `/cq:go` ticket workflow; backend changes pair with the
  `commerce-qrspi` workflow in `sap-mcp-server-l`
