# Stripe Payment Integration — Stories & Tasks

**Epic:** Replace mock payment provider with Stripe Payment Intents API
**Date:** 2026-03-25
**Plan:** [2026-03-25-stripe-integration-plan.md](./2026-03-25-stripe-integration-plan.md)

---

## Story 1.1: Add Stripe Java SDK Dependency

**Summary:** Add Stripe Java SDK to coremcp extension via external-dependencies.xml
**Phase:** 1 — Foundation
**Blocked by:** None
**Blocks:** 1.2, 2.1, 2.2, 2.3, 2.4

### Tasks

- [ ] **T-1.1.1** Create `core-customize/hybris/bin/custom/coremcp/external-dependencies.xml` with `com.stripe:stripe-java:28.3.0` dependency
- [ ] **T-1.1.2** Run `./gradlew yclean yall` — verify build succeeds with Stripe SDK on classpath
- [ ] **T-1.1.3** Create a scratch Java file that imports `com.stripe.Stripe` — verify IDE/compiler resolves it
- [ ] **T-1.1.4** If `external-dependencies.xml` doesn't resolve: fallback — download Stripe JAR and place in `coremcp/lib/`

### Acceptance Criteria

- [ ] `com.stripe.Stripe` is importable in coremcp source
- [ ] `./gradlew yclean yall` completes without errors
- [ ] No new dependency conflicts in build output

---

## Story 1.2: Stripe Configuration Service

**Summary:** Create a service that reads Stripe API keys from local.properties
**Phase:** 1 — Foundation
**Blocked by:** 1.1
**Blocks:** 2.1, 3.1, 3.3

### Tasks

- [ ] **T-1.2.1** Create interface `coremcp/src/com/coremcp/services/StripeConfigurationService.java` with methods: `getSecretKey()`, `getPublishableKey()`, `getWebhookSecret()`, `isLiveMode()`
- [ ] **T-1.2.2** Create implementation `coremcp/src/com/coremcp/services/impl/DefaultStripeConfigurationService.java` — inject `configurationService`, read properties `coremcp.stripe.secret.key`, `coremcp.stripe.publishable.key`, `coremcp.stripe.webhook.secret`
- [ ] **T-1.2.3** Add Spring bean to `coremcp/resources/coremcp-spring.xml`: `defaultStripeConfigurationService` aliased to `stripeConfigurationService`, inject `configurationService` ref
- [ ] **T-1.2.4** Run `./gradlew ybuild` — verify compilation
- [ ] **T-1.2.5** Write unit test: mock `configurationService`, verify `getSecretKey()` returns expected value

### Acceptance Criteria

- [ ] Bean loads without Spring context errors on server start
- [ ] `getSecretKey()` reads from `coremcp.stripe.secret.key` property
- [ ] `isLiveMode()` returns `true` for `sk_live_*` prefix, `false` for `sk_test_*`
- [ ] Unit test passes

---

## Story 1.3: Properties Configuration

**Summary:** Add Stripe API key properties to all environment config files
**Phase:** 1 — Foundation
**Blocked by:** None
**Blocks:** 1.2 (runtime)

### Tasks

- [ ] **T-1.3.1** Add to `core-customize/hybris/config/local.properties`:
  ```properties
  coremcp.stripe.secret.key=sk_test_REPLACE_ME
  coremcp.stripe.publishable.key=pk_test_REPLACE_ME
  coremcp.stripe.webhook.secret=whsec_REPLACE_ME
  ```
- [ ] **T-1.3.2** Add same properties to `core-customize/hybris/config/local-dev.properties` with test keys
- [ ] **T-1.3.3** Add placeholder properties to `core-customize/hybris/config/local-stg.properties` with `REPLACE_WITH_STAGING_KEY` values
- [ ] **T-1.3.4** Add placeholder properties to `core-customize/hybris/config/local-prod.properties` with `REPLACE_WITH_PRODUCTION_KEY` values
- [ ] **T-1.3.5** Verify properties load: start server, check HAC → Platform → Configuration for `coremcp.stripe.*`

### Acceptance Criteria

- [ ] All four property files contain Stripe key entries
- [ ] Properties visible in HAC after server start
- [ ] No secrets committed to git (test keys only in local.properties, placeholders in stg/prod)

---

## Story 2.1: StripePaymentCommandFactory

**Summary:** Create a command factory that returns Stripe command implementations, replacing the mock factory
**Phase:** 2 — Commands
**Blocked by:** 1.1, 1.2
**Blocks:** 2.2, 2.3, 2.4

### Tasks

- [ ] **T-2.1.1** Create `coremcp/src/com/coremcp/payment/StripePaymentCommandFactory.java` implementing `CommandFactory` interface
- [ ] **T-2.1.2** Implement `getCommand(Class<T> commandType)` — return Stripe implementations for `AuthorizationCommand`, `SubscriptionAuthorizationCommand`, `CaptureCommand`, `CreateSubscriptionCommand`
- [ ] **T-2.1.3** Inject `StripeConfigurationService` — pass to each command on creation
- [ ] **T-2.1.4** Add Spring bean to `coremcp-spring.xml`: override mock factory with `<alias name="stripeCommandFactory" alias="commandFactoryRegistry"/>`, set `paymentProvider` property to `"Stripe"`
- [ ] **T-2.1.5** Run `./gradlew ybuild` — verify compilation

### Acceptance Criteria

- [ ] Factory returns correct Stripe command for each command type
- [ ] Factory is wired as the active `commandFactoryRegistry` bean
- [ ] `paymentProvider` property is `"Stripe"`
- [ ] Build compiles cleanly

---

## Story 2.2: StripeCreateSubscriptionCommand

**Summary:** Implement Stripe Customer + PaymentMethod creation for card storage
**Phase:** 2 — Commands
**Blocked by:** 2.1
**Blocks:** 4.1

### Tasks

- [ ] **T-2.2.1** Create `coremcp/src/com/coremcp/payment/commands/StripeCreateSubscriptionCommand.java` implementing `CreateSubscriptionCommand`
- [ ] **T-2.2.2** Implement `perform()`:
  - Set `Stripe.apiKey` from `StripeConfigurationService`
  - Search for existing Stripe Customer by email, or create new
  - Create `PaymentMethod` with card number, exp month/year, CVC
  - Attach `PaymentMethod` to Customer
  - Return `CreateSubscriptionResult` with `subscriptionId = pm_XXXXX`
- [ ] **T-2.2.3** Handle Stripe API errors: map `StripeException` to appropriate `CreateSubscriptionResult` error codes
- [ ] **T-2.2.4** Write unit test: mock Stripe API calls, verify `subscriptionId` is returned as PaymentMethod ID
- [ ] **T-2.2.5** Write unit test: verify error handling for invalid card number

### Acceptance Criteria

- [ ] Stripe Customer created/retrieved by email
- [ ] PaymentMethod created and attached to Customer
- [ ] `subscriptionId` returned as `pm_XXXXX` format
- [ ] StripeException mapped to error result (not thrown)
- [ ] Unit tests pass

---

## Story 2.3: StripeAuthorizationCommand

**Summary:** Implement PaymentIntent creation with manual capture for authorization
**Phase:** 2 — Commands
**Blocked by:** 2.1
**Blocks:** 4.2

### Tasks

- [ ] **T-2.3.1** Create `coremcp/src/com/coremcp/payment/commands/StripeAuthorizationCommand.java` implementing `AuthorizationCommand`
- [ ] **T-2.3.2** Create `coremcp/src/com/coremcp/payment/commands/StripeSubscriptionAuthorizationCommand.java` implementing `SubscriptionAuthorizationCommand`
- [ ] **T-2.3.3** Implement `perform()` for both:
  - Set `Stripe.apiKey`
  - Convert amount to cents (multiply by 100, integer)
  - Create `PaymentIntent` with: `amount`, `currency`, `payment_method` (from `subscriptionId`), `customer`, `capture_method=manual`, `confirm=true`, `off_session=true`
  - Store `merchantTransactionCode` in PaymentIntent metadata
- [ ] **T-2.3.4** Map Stripe PaymentIntent status to Commerce TransactionStatus:
  - `requires_capture` → `ACCEPTED`
  - `requires_action` → `REVIEW`
  - `canceled` / exception → `REJECTED`
- [ ] **T-2.3.5** Store `PaymentIntent.id` (`pi_XXXXX`) on `AuthorizationResult.requestId` for later capture
- [ ] **T-2.3.6** Write unit test: mock PaymentIntent.create, verify ACCEPTED when `requires_capture`
- [ ] **T-2.3.7** Write unit test: verify REJECTED when Stripe throws `CardException`
- [ ] **T-2.3.8** Write unit test: verify amount conversion to cents (e.g., $99.99 → 9999)

### Acceptance Criteria

- [ ] PaymentIntent created with `capture_method=manual` and `confirm=true`
- [ ] Amount correctly converted to Stripe's smallest currency unit (cents)
- [ ] `PaymentIntent.id` stored on result for capture phase
- [ ] All three status mappings work correctly
- [ ] Unit tests pass

---

## Story 2.4: StripeCaptureCommand

**Summary:** Implement PaymentIntent capture for order fulfillment
**Phase:** 2 — Commands
**Blocked by:** 2.1
**Blocks:** None (consumed by existing TakePaymentAction)

### Tasks

- [ ] **T-2.4.1** Create `coremcp/src/com/coremcp/payment/commands/StripeCaptureCommand.java` implementing `CaptureCommand`
- [ ] **T-2.4.2** Implement `perform()`:
  - Set `Stripe.apiKey`
  - Retrieve PaymentIntent by ID (from authorization entry's `requestId`)
  - Call `PaymentIntent.capture()`
  - Map response to `CaptureResult`
- [ ] **T-2.4.3** Map Stripe status: `succeeded` → `ACCEPTED`, error → `ERROR`
- [ ] **T-2.4.4** Handle edge cases: PaymentIntent already captured (idempotent), PaymentIntent expired
- [ ] **T-2.4.5** Write unit test: mock PaymentIntent.capture, verify ACCEPTED on success
- [ ] **T-2.4.6** Write unit test: verify ERROR when PaymentIntent is expired/canceled
- [ ] **T-2.4.7** Run `./gradlew ybuild stopServer startServer` — verify server starts with all commands wired

### Acceptance Criteria

- [ ] PaymentIntent captured successfully using ID from authorization phase
- [ ] Idempotent: re-capture of already-captured intent doesn't error
- [ ] Expired PaymentIntent returns ERROR (not exception)
- [ ] Server starts with full Stripe command stack wired
- [ ] Unit tests pass

---

## Story 3.1: Create PaymentIntent OCC Endpoint (React)

**Summary:** New OCC endpoint that creates a PaymentIntent and returns client_secret for Stripe.js
**Phase:** 3 — React Flow
**Blocked by:** 2.1, 1.2
**Blocks:** 3.2, 3.4

### Tasks

- [ ] **T-3.1.1** Create interface `coremcp/src/com/coremcp/facades/StripeCheckoutFacade.java` with method `createPaymentIntent(String paymentMethodId, CartModel cart)`
- [ ] **T-3.1.2** Create implementation `coremcp/src/com/coremcp/facades/impl/DefaultStripeCheckoutFacade.java`:
  - Inject `StripeConfigurationService`, `checkoutFacade`, `modelService`
  - Create/retrieve Stripe Customer for Commerce user
  - Attach PaymentMethod to Customer
  - Store `CreditCardPaymentInfo` on cart with `subscriptionId = pm_XXXXX`
  - Create PaymentIntent with `capture_method=manual`, `confirm=false`
  - Return DTO with `clientSecret`, `paymentIntentId`, `publishableKey`
- [ ] **T-3.1.3** Create `coremcp/src/com/coremcp/controllers/StripePaymentController.java`:
  - `POST /{baseSiteId}/users/{userId}/carts/{cartId}/stripe/payment-intent`
  - Accept `paymentMethodId` in request body
  - Call `StripeCheckoutFacade.createPaymentIntent()`
  - Return JSON response with `clientSecret`, `paymentIntentId`, `publishableKey`, `requiresAction`
- [ ] **T-3.1.4** Add Spring beans to `coremcp-spring.xml`: `defaultStripeCheckoutFacade` aliased to `stripeCheckoutFacade`
- [ ] **T-3.1.5** Run `./gradlew ybuild stopServer startServer` — verify endpoint appears in Swagger
- [ ] **T-3.1.6** Manual test: call endpoint with Stripe test PaymentMethod, verify `clientSecret` returned

### Acceptance Criteria

- [ ] `POST .../stripe/payment-intent` accepts `paymentMethodId` and returns `clientSecret`
- [ ] PaymentIntent created with `confirm=false` (React confirms client-side)
- [ ] `CreditCardPaymentInfo` stored on cart
- [ ] Endpoint visible in Swagger UI
- [ ] Returns 400 for invalid `paymentMethodId`

---

## Story 3.2: Confirm Payment OCC Endpoint (React)

**Summary:** Endpoint for React to call after Stripe.js confirms PaymentIntent (records authorization)
**Phase:** 3 — React Flow
**Blocked by:** 3.1
**Blocks:** 3.4

### Tasks

- [ ] **T-3.2.1** Add method to `StripeCheckoutFacade`: `confirmPayment(String paymentIntentId, CartModel cart)`
- [ ] **T-3.2.2** Implement in `DefaultStripeCheckoutFacade`:
  - Retrieve PaymentIntent from Stripe API
  - Verify status is `requires_capture` (auth succeeded)
  - Create `PaymentTransactionModel` linked to cart
  - Create `PaymentTransactionEntryModel` with type=AUTHORIZATION, status=ACCEPTED
  - Store `paymentIntentId` on transaction entry's `requestId`
  - Save via `modelService`
- [ ] **T-3.2.3** Add endpoint to `StripePaymentController`:
  - `POST /{baseSiteId}/users/{userId}/carts/{cartId}/stripe/confirm`
  - Accept `paymentIntentId` in request body
  - Return success/failure
- [ ] **T-3.2.4** Handle error cases: PaymentIntent not found, status is `canceled` or `requires_action`
- [ ] **T-3.2.5** Run `./gradlew ybuild stopServer startServer` — verify endpoint in Swagger

### Acceptance Criteria

- [ ] `POST .../stripe/confirm` records authorization transaction on cart
- [ ] PaymentTransaction + Entry created with correct type/status
- [ ] `requestId` stores PaymentIntent ID for capture phase
- [ ] Returns 400 if PaymentIntent status is not `requires_capture`
- [ ] Cart is ready for standard `placeOrder` after confirmation

---

## Story 3.3: Stripe Webhook Endpoint

**Summary:** Webhook receiver for async Stripe events with signature verification
**Phase:** 3 — React Flow
**Blocked by:** 1.2
**Blocks:** None

### Tasks

- [ ] **T-3.3.1** Create interface `coremcp/src/com/coremcp/services/StripeWebhookService.java` with method `handleEvent(String payload, String sigHeader)`
- [ ] **T-3.3.2** Create implementation `coremcp/src/com/coremcp/services/impl/DefaultStripeWebhookService.java`:
  - Verify webhook signature using `Webhook.constructEvent(payload, sigHeader, webhookSecret)`
  - Route to handlers by event type
  - Handle `payment_intent.succeeded`: find PaymentTransactionEntry by PaymentIntent ID, update status to ACCEPTED
  - Handle `payment_intent.payment_failed`: find entry, update status to REJECTED
  - Stub handler for `charge.refunded` (log only, not implemented)
- [ ] **T-3.3.3** Create `coremcp/src/com/coremcp/controllers/StripeWebhookController.java`:
  - `POST /{baseSiteId}/stripe/webhook`
  - Read raw request body + `Stripe-Signature` header
  - Call `StripeWebhookService.handleEvent()`
  - Return 200 on success, 400 on invalid signature
- [ ] **T-3.3.4** Add Spring beans to `coremcp-spring.xml`
- [ ] **T-3.3.5** Ensure endpoint is excluded from OAuth2 authentication (webhooks are unauthenticated)
- [ ] **T-3.3.6** Write unit test: valid signature → event processed
- [ ] **T-3.3.7** Write unit test: invalid signature → 400 returned, no processing

### Acceptance Criteria

- [ ] Webhook endpoint accepts Stripe events with valid signatures
- [ ] Invalid signatures rejected with 400
- [ ] `payment_intent.succeeded` and `payment_intent.payment_failed` update transaction entries
- [ ] Endpoint excluded from OAuth2 auth filter
- [ ] Unit tests pass

---

## Story 3.4: Modify placeOrder for React Flow

**Summary:** Override CheckoutFacade to skip re-authorization when Stripe auth already exists
**Phase:** 3 — React Flow
**Blocked by:** 3.1, 3.2
**Blocks:** None

### Tasks

- [ ] **T-3.4.1** Create `coremcp/src/com/coremcp/facades/impl/StripeCheckoutFacadeDecorator.java` extending `DefaultCheckoutFacade`
- [ ] **T-3.4.2** Override `authorizePayment(String securityCode)`:
  - Check if cart already has a PaymentTransaction with AUTHORIZATION entry status ACCEPTED where `requestId` starts with `pi_` (Stripe PaymentIntent)
  - If yes: return the existing entry (skip Stripe API call)
  - If no: delegate to `super.authorizePayment()` (MCP path)
- [ ] **T-3.4.3** Register override in `coremcp-spring.xml` via alias pattern:
  ```xml
  <alias name="stripeCheckoutFacade" alias="checkoutFacade"/>
  ```
- [ ] **T-3.4.4** Verify MCP flow still works: `order_place` tool → `authorizePayment()` → `StripeAuthorizationCommand` (no existing auth)
- [ ] **T-3.4.5** Verify React flow: after `/stripe/confirm`, `placeOrder` skips re-authorization
- [ ] **T-3.4.6** Write unit test: cart with existing Stripe auth → `authorizePayment` returns existing entry
- [ ] **T-3.4.7** Write unit test: cart without auth → delegates to super (Stripe command called)

### Acceptance Criteria

- [ ] React flow: `placeOrder` succeeds without duplicate authorization
- [ ] MCP flow: `placeOrder` still authorizes via Stripe command
- [ ] No duplicate PaymentIntents created
- [ ] Unit tests pass for both paths

---

## Story 4.1: Update CheckoutSetPaymentToolHandler

**Summary:** Update MCP payment tool for Stripe test cards and descriptions
**Phase:** 4 — MCP Flow
**Blocked by:** 2.2
**Blocks:** None

### Tasks

- [ ] **T-4.1.1** Update `getDescription()` in `CheckoutSetPaymentToolHandler.java`: replace "mock payment" with "Stripe test mode", update test card reference to 4242424242424242
- [ ] **T-4.1.2** Update `getInputSchema()`: change default `cardNumber` to `4242424242424242`
- [ ] **T-4.1.3** Update `execute()`: change fallback card number from `4111111111111111` to `4242424242424242`
- [ ] **T-4.1.4** Add `stripePaymentMethodId` to response map (from `created.getSubscriptionId()`)
- [ ] **T-4.1.5** Run `./gradlew ybuild` — verify compilation

### Acceptance Criteria

- [ ] Default test card is `4242424242424242` (Stripe test card)
- [ ] Tool description mentions Stripe, not mock
- [ ] Response includes Stripe PaymentMethod ID
- [ ] Build compiles

---

## Story 4.2: Update OrderPlaceToolHandler

**Summary:** Update MCP order placement tool with Stripe error handling
**Phase:** 4 — MCP Flow
**Blocked by:** 2.3
**Blocks:** None

### Tasks

- [ ] **T-4.2.1** Update `getDescription()` in `OrderPlaceToolHandler.java`: note that payment is processed via Stripe
- [ ] **T-4.2.2** Add Stripe-specific error handling in `execute()`: catch exceptions, extract Stripe decline codes (card_declined, insufficient_funds, expired_card), return human-readable messages
- [ ] **T-4.2.3** Add `paymentIntentId` to successful response (extract from order's PaymentTransaction)
- [ ] **T-4.2.4** Run `./gradlew ybuild` — verify compilation
- [ ] **T-4.2.5** End-to-end MCP test: `checkout_set_payment` → `order_place` → verify Stripe PaymentIntent created and authorized

### Acceptance Criteria

- [ ] Stripe decline reasons surfaced in error messages
- [ ] Successful response includes `paymentIntentId`
- [ ] End-to-end MCP checkout completes with Stripe test keys
- [ ] `TakePaymentAction` captures the PaymentIntent during fulfillment

---

## Story 4.3: Update Tool Descriptions and Schema

**Summary:** Align all payment tool descriptions with Stripe integration
**Phase:** 4 — MCP Flow
**Blocked by:** 4.1, 4.2
**Blocks:** None

### Tasks

- [ ] **T-4.3.1** Review `CheckoutSetPaymentToolHandler.getDescription()` — confirm no references to mock/Mockup remain
- [ ] **T-4.3.2** Review `OrderPlaceToolHandler.getDescription()` — confirm CVV/securityCode docs note Stripe validates at PaymentMethod creation
- [ ] **T-4.3.3** Review `getInputSchema()` defaults across both handlers — all test values are Stripe-compatible
- [ ] **T-4.3.4** Run full MCP tool listing — verify updated descriptions appear

### Acceptance Criteria

- [ ] No references to "mock", "Mockup", or `4111111111111111` in any tool handler
- [ ] Tool descriptions are accurate for Stripe flow

---

## Story 5.1: Update BaseStore Payment Provider

**Summary:** Change BaseStore.paymentProvider from Mockup to Stripe in ImpEx
**Phase:** 5 — Data & Config
**Blocked by:** 2.1
**Blocks:** 5.2

### Tasks

- [ ] **T-5.1.1** Edit `sampledatamcp/resources/impex/essentialdata-infrastructure.impex`: change `paymentProvider` value from `Mockup` to `Stripe` in BaseStore INSERT_UPDATE
- [ ] **T-5.1.2** Run `./gradlew ybuild stopServer startServer yupdatesystem` — verify update applies
- [ ] **T-5.1.3** Verify in Backoffice: BaseStore `electronics` → paymentProvider = `Stripe`
- [ ] **T-5.1.4** Alternatively verify via FlexibleSearch: `./gradlew flexquery -Pfile="SELECT {paymentProvider} FROM {BaseStore} WHERE {uid}='electronics'"`

### Acceptance Criteria

- [ ] BaseStore.paymentProvider is `Stripe` after update system
- [ ] No Mockup references in essentialdata-infrastructure.impex

---

## Story 5.2: Update Test Data

**Summary:** Align test ImpEx data with Stripe provider
**Phase:** 5 — Data & Config
**Blocked by:** 5.1
**Blocks:** None

### Tasks

- [ ] **T-5.2.1** Edit `coremcp/resources/coremcp/test/testdata-thinkshop.impex`: change any `paymentProvider` from `Mockup` to `Stripe`
- [ ] **T-5.2.2** Search all ImpEx files for remaining `Mockup` references: `grep -r "Mockup" core-customize/hybris/bin/custom/`
- [ ] **T-5.2.3** Run `./gradlew yunittests -Dtestclasses.extensions=coremcp` — verify tests pass with updated data

### Acceptance Criteria

- [ ] Zero references to `Mockup` in custom extension ImpEx files
- [ ] Unit tests pass with Stripe provider in test data

---

## Story 5.3: Update local.properties Templates

**Summary:** Ensure Stripe properties are in all environment config files
**Phase:** 5 — Data & Config
**Blocked by:** 1.3
**Blocks:** None

### Tasks

- [ ] **T-5.3.1** Verify `local.properties` has Stripe keys (added in Story 1.3)
- [ ] **T-5.3.2** Check `dev-config/local.properties` overlay — add Stripe keys if missing
- [ ] **T-5.3.3** Run `./gradlew yclean yall yinitialize` — verify clean init succeeds with Stripe provider
- [ ] **T-5.3.4** Run `./gradlew startServer` — verify server starts and Stripe config loads

### Acceptance Criteria

- [ ] Clean initialize succeeds
- [ ] Server starts without payment-related errors
- [ ] Stripe properties visible in HAC

---

## Story 6.1: Create Payment Flow Documentation

**Summary:** Document the Stripe integration with context, components, and diagrams
**Phase:** 6 — Documentation
**Blocked by:** All previous stories
**Blocks:** None

### Tasks

- [ ] **T-6.1.1** Create `coremcp/docs/stripe-payment/context.md`:
  - Why Stripe Payment Intents (SCA, dual-channel)
  - React flow vs MCP flow decision points
  - Key configuration (properties, BaseStore provider)
  - Testing with Stripe test cards (4242424242424242, etc.)
  - Out of scope items
- [ ] **T-6.1.2** Create `coremcp/docs/stripe-payment/components.md`:
  - List all new files with purpose
  - List all modified files with what changed
  - Spring bean dependency diagram
- [ ] **T-6.1.3** Create `coremcp/docs/stripe-payment/diagram.md`:
  - Mermaid sequence diagram: React checkout flow
  - Mermaid sequence diagram: MCP checkout flow
  - Mermaid sequence diagram: Fulfillment capture
  - Mermaid component diagram: Spring bean wiring
- [ ] **T-6.1.4** Review diagrams render correctly (paste into Mermaid live editor or similar)
- [ ] **T-6.1.5** Cross-reference with plan — verify all stories/phases are reflected in docs

### Acceptance Criteria

- [ ] All three doc files exist under `coremcp/docs/stripe-payment/`
- [ ] Both React and MCP flows are documented
- [ ] Diagrams render correctly in Mermaid
- [ ] No stale references to mock payment

---

## Summary

| Phase | Stories | Tasks | Status |
|-------|---------|-------|--------|
| 1 — Foundation | 1.1, 1.2, 1.3 | 14 | Pending |
| 2 — Commands | 2.1, 2.2, 2.3, 2.4 | 25 | Pending |
| 3 — React Flow | 3.1, 3.2, 3.3, 3.4 | 25 | Pending |
| 4 — MCP Flow | 4.1, 4.2, 4.3 | 14 | Pending |
| 5 — Data & Config | 5.1, 5.2, 5.3 | 11 | Pending |
| 6 — Documentation | 6.1 | 5 | Pending |
| **Total** | **15 stories** | **94 tasks** | |

### Dependency Chain (Critical Path)

```
1.1 → 1.2 → 2.1 → 2.2 ──→ 4.1
                  → 2.3 ──→ 4.2 → 4.3
                  → 2.4
              → 3.1 → 3.2 → 3.4
         → 3.3
1.3 (parallel)
5.1 → 5.2
5.3 (parallel)
6.1 (after all)
```

**Shortest critical path:** 1.1 → 1.2 → 2.1 → 2.3 → 4.2 → 4.3 → 6.1 (7 stories sequential)
