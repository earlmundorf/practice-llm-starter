# UCP REST binding — components

| File | Role |
|------|------|
| `src/com/ucpcommerce/controllers/AbstractUcpRestController.java` | Shared plumbing: JSON serialization, HTTP-400 UCP error envelope for client protocol bugs, int/csv query-param parsing (malformed values → 400, never 500) |
| `src/com/ucpcommerce/controllers/UcpCheckoutRestController.java` | The five checkout-session routes (`POST /checkout-sessions`, `GET`/`PUT /{id}`, `POST /{id}/complete`, `POST /{id}/cancel`) → `UcpCheckoutService`; parses the raw body, enforces the payload-must-not-carry-an-id rule, maps the `Idempotency-Key` header |
| `src/com/ucpcommerce/controllers/UcpCatalogRestController.java` | `GET /catalog/search`, `GET /catalog/lookup`, `GET /products/{id}` → `UcpCatalogService` (same defaults as the MCP tools) |
| `src/com/ucpcommerce/controllers/UcpOrderRestController.java` | `GET /orders/{id}`, `GET /orders` → `UcpOrderService` (statuses as a comma-separated filter) |
| `src/com/ucpcommerce/services/impl/DefaultUcpProfileService.java` | Adds the `rest` transport entry (`services.dev.ucp.shopping.rest.endpoint` = the public base + `/occ/v2/{baseSiteId}/ucp`) — advertised only now that the routes work |
| `docs/adr/0002-rest-binding-checkout-sessions-path.md` | The `/checkout-sessions` naming decision, full route map, error-taxonomy and header mappings |

No Spring wiring changes: the controllers are picked up by the existing
`additional-web-spring-context.xml` component scan of
`com.ucpcommerce.controllers`, and they inject the already-published
`ucpCatalogService`/`ucpCheckoutService`/`ucpOrderService` aliases.

Tests: `testsrc/com/ucpcommerce/controllers/UcpCheckoutRestControllerTest.java`,
`UcpCatalogRestControllerTest.java`, `UcpOrderRestControllerTest.java` —
controller-level request→service parameter mapping, header mapping, and the
protocol-bug→400 / business-error→200 taxonomy only (the capability services
are already covered by their own suites).

Harness: `core-customize/scripts/ucp-e2e.py --transport rest` — the same
payload assertions as the MCP run, on a different wire (`rest_call` maps each
logical operation to its route; `protocol_rejected` abstracts MCP `isError`
vs REST 400).
