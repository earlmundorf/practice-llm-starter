# UCP checkout — components

| File | Role |
|------|------|
| `resources/ucpcommerce-items.xml` | `UcpCheckoutSessionEntry` (typecode 14003, table `UcpCheckoutSessions`): `checkoutId` (unique), `cartCode`, `status` (`UcpCheckoutStatus` enum with UCP wire codes), `buyerJson`, `idempotencyKey`, `completionResponseJson`, `orderCode`, `lastAccessedAt` |
| `src/com/ucpcommerce/services/UcpCheckoutSessionService.java` + `impl/PersistedUcpCheckoutSessionService.java` | Mints `ucp_chk_…` ids, FlexibleSearch lookup, lazy TTL eviction, `updateBuyer`, and the completion trio: `beginCompletion` (key stored, status `complete_in_progress`), `failCompletion` (back to `ready_for_complete`, key cleared), `recordCompletion` (one atomic save: `completed` + response JSON + order code) |
| `src/com/ucpcommerce/services/UcpCheckoutService.java` + `impl/DefaultUcpCheckoutService.java` | The binding-agnostic five operations (`create`/`get`/`update`/`complete`/`cancel`): resolve → load → facade → persist-back bracket, line-item diffing, destination + delivery-mode handling, status derivation (S5), handler validation, idempotent replay/terminal decision table |
| `src/com/ucpcommerce/services/impl/UcpCheckoutMarshaller.java` | `CartData`/`OrderData` → UCP `checkout` object: line_items, totals (minor units, positive `discount`), buyer, `fulfillment` echo read back off the cart |
| `src/com/ucpcommerce/services/impl/UcpOrderMarshaller.java` | The minimal `order` block (`id`, `created_at`) embedded in a completed checkout |
| `src/com/ucpcommerce/jobs/UcpCheckoutSessionCleanupJob.java` + `resources/impex/essentialdata-ucp-session-cleanup.impex` | 30-min CronJob sweeping stale entries past TTL |
| `src/com/ucpcommerce/tools/impl/{Create,Get,Update,Complete,Cancel}CheckoutTool.java` | MCP tool adapters (top-level `id` param; the `checkout` payload MUST NOT contain an id; complete/cancel require `meta["idempotency-key"]`) |
| `project.properties` | `ucpcommerce.checkout.session.ttl.minutes` |

Tests: `DefaultUcpCheckoutServiceTest` (~48 tests: create/get/update/complete/
cancel incl. idempotent replay without facade calls, handler rejection,
terminal guards, status-derivation matrix), `UcpCheckoutMarshallerTest`,
`PersistedUcpCheckoutSessionServiceIntegrationTest` (`@IntegrationTest`:
persistence, TTL eviction, cleanup-job sweep).
