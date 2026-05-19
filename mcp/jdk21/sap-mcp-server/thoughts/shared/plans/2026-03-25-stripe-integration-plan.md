# Stripe Payment Integration Plan
Date: 2026-03-25
Status: Draft

## Overview

Replace the mock payment provider with Stripe Payment Intents API, supporting two client channels:
- **React frontend:** Client-side confirmation via Stripe.js/Elements (handles 3D Secure/SCA)
- **MCP client:** Server-side confirmation (`confirm=true`, no browser)

Both channels share the same Stripe command implementations and fulfillment capture path.

## Architecture Decision

**Why Payment Intents (not Charges API):**
- Payment Intents handles both client-side (3D Secure) and server-side confirmation
- SCA/PSD2 compliant for European cards
- Stripe's recommended path for all new integrations
- Single API for both React and MCP flows

**How it maps to Commerce's command pattern:**

| Commerce Command | Stripe API Call | When |
|------------------|----------------|------|
| `CreateSubscriptionCommand` | `Customer.create()` + `PaymentMethod.attach()` | checkout_set_payment (save card) |
| `AuthorizationCommand` | `PaymentIntent.create(capture_method=manual)` | order_place (authorize) |
| `CaptureCommand` | `PaymentIntent.capture()` | TakePaymentAction (fulfillment) |

**React vs MCP divergence point:**

| Step | React Frontend | MCP Client |
|------|---------------|------------|
| Set payment | Stripe Elements → `PaymentMethod` ID sent to OCC | Tool handler creates `PaymentMethod` server-side |
| Authorize | OCC creates `PaymentIntent` → returns `client_secret` → Stripe.js confirms | Tool handler creates `PaymentIntent` with `confirm=true` |
| Capture | Same (server-side `TakePaymentAction`) | Same |

---

## Phase 1: Stripe Foundation

**Scope:** Add Stripe Java SDK, configuration service, and properties infrastructure.

**Why first:** Every subsequent phase depends on the SDK and configuration being available.

### Story 1.1: Add Stripe Java SDK Dependency

**Files to create/modify:**
- `core-customize/hybris/bin/custom/coremcp/external-dependencies.xml` (CREATE)

**Details:**
Create `external-dependencies.xml` to pull Stripe Java SDK from Maven Central. This is the CCv2-recommended pattern for third-party JARs.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.coremcp</groupId>
    <artifactId>coremcp-dependencies</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>
    <dependencies>
        <dependency>
            <groupId>com.stripe</groupId>
            <artifactId>stripe-java</artifactId>
            <version>28.3.0</version>
        </dependency>
    </dependencies>
</project>
```

**Verification:**
- [ ] `./gradlew yclean yall` — build succeeds with Stripe SDK on classpath
- [ ] Stripe classes importable in coremcp source

### Story 1.2: Stripe Configuration Service

**Files to create:**
- `coremcp/src/com/coremcp/services/StripeConfigurationService.java` (interface)
- `coremcp/src/com/coremcp/services/impl/DefaultStripeConfigurationService.java` (implementation)

**Files to modify:**
- `coremcp/resources/coremcp-spring.xml` — add bean definition

**Details:**
Service reads Stripe API keys from `configurationService` (backed by `local.properties`). Provides:
- `getSecretKey()` → reads `coremcp.stripe.secret.key`
- `getPublishableKey()` → reads `coremcp.stripe.publishable.key`
- `getWebhookSecret()` → reads `coremcp.stripe.webhook.secret`
- `isLiveMode()` → derived from key prefix (`sk_live_` vs `sk_test_`)

Spring wiring:
```xml
<alias name="defaultStripeConfigurationService" alias="stripeConfigurationService"/>
<bean id="defaultStripeConfigurationService"
      class="com.coremcp.services.impl.DefaultStripeConfigurationService">
    <property name="configurationService" ref="configurationService"/>
</bean>
```

**Verification:**
- [ ] `./gradlew ybuild` compiles
- [ ] Unit test: service reads keys from config

### Story 1.3: Properties Configuration

**Files to modify:**
- `core-customize/hybris/config/local.properties` — add Stripe keys (test mode)
- `core-customize/hybris/config/local-dev.properties` — dev persona (test keys)
- `core-customize/hybris/config/local-stg.properties` — staging persona (placeholder)
- `core-customize/hybris/config/local-prod.properties` — prod persona (placeholder)

**Properties:**
```properties
# Stripe Payment Configuration
coremcp.stripe.secret.key=sk_test_XXXXXXXXXXXX
coremcp.stripe.publishable.key=pk_test_XXXXXXXXXXXX
coremcp.stripe.webhook.secret=whsec_XXXXXXXXXXXX
```

**Verification:**
- [ ] Properties load correctly via HAC → Platform → Configuration

### Acceptance Criteria — Phase 1
- [ ] Stripe Java SDK is on the classpath and importable
- [ ] `StripeConfigurationService` returns correct keys per environment
- [ ] Build compiles cleanly with no new warnings
- [ ] No runtime errors on server start

---

## Phase 2: Stripe Payment Commands

**Scope:** Implement Stripe-backed payment commands that replace the mock implementations, plus a command factory and Spring wiring.

**Why:** The command pattern is the integration point — everything above (facades, services, strategies) stays unchanged. Only the commands change.

### Story 2.1: StripePaymentCommandFactory

**Files to create:**
- `coremcp/src/com/coremcp/payment/StripePaymentCommandFactory.java`

**Files to modify:**
- `coremcp/resources/coremcp-spring.xml` — register factory bean

**Details:**
Implements `CommandFactory` interface. Returns Stripe command implementations for:
- `AuthorizationCommand` → `StripeAuthorizationCommand`
- `SubscriptionAuthorizationCommand` → `StripeSubscriptionAuthorizationCommand`
- `CaptureCommand` → `StripeCaptureCommand`
- `CreateSubscriptionCommand` → `StripeCreateSubscriptionCommand`

Spring wiring — override the mock factory:
```xml
<alias name="stripeCommandFactory" alias="commandFactoryRegistry"/>
<bean id="stripeCommandFactory" class="com.coremcp.payment.StripePaymentCommandFactory">
    <property name="stripeConfigurationService" ref="stripeConfigurationService"/>
    <property name="paymentProvider" value="Stripe"/>
</bean>
```

### Story 2.2: StripeCreateSubscriptionCommand

**Files to create:**
- `coremcp/src/com/coremcp/payment/commands/StripeCreateSubscriptionCommand.java`

**Details:**
Called during `checkout_set_payment` when creating a new card. Maps to:
1. `Stripe.apiKey = secretKey`
2. `Customer.create(email, name)` — or retrieve existing by email
3. `PaymentMethod.create(card details)` — from the subscription request
4. `PaymentMethod.attach(customer)` — link to customer
5. Return `subscriptionId` = `pm_XXXXX` (Stripe PaymentMethod ID) stored on `CreditCardPaymentInfo`

**Key mapping:**
| Commerce Field | Stripe Field |
|----------------|-------------|
| `subscriptionId` | PaymentMethod ID (`pm_...`) |
| `cardNumber` | `card.number` (only for server-side/MCP) |
| `expiryMonth/Year` | `card.exp_month/year` |
| Customer email | `Customer.email` |

**Note for React flow:** React will send a `PaymentMethod` ID directly from Stripe.js — this command only runs for MCP (server-side card creation). See Phase 4.

### Story 2.3: StripeAuthorizationCommand

**Files to create:**
- `coremcp/src/com/coremcp/payment/commands/StripeAuthorizationCommand.java`
- `coremcp/src/com/coremcp/payment/commands/StripeSubscriptionAuthorizationCommand.java`

**Details:**
Called during `order_place` → `authorizePayment()`. Creates a Stripe PaymentIntent with manual capture:

```
PaymentIntent.create({
    amount: authAmount (in cents),
    currency: currency.toLowerCase(),
    payment_method: subscriptionId (pm_XXXXX from CreditCardPaymentInfo),
    customer: stripeCustomerId,
    capture_method: "manual",       // authorize only, capture later
    confirm: true,                  // server-side confirmation (MCP path)
    off_session: true,              // no cardholder present
    metadata: { merchantTransactionCode: "..." }
})
```

**Response mapping:**
| Stripe Status | Commerce TransactionStatus |
|---------------|---------------------------|
| `requires_capture` | ACCEPTED (auth succeeded, capture pending) |
| `requires_action` | REVIEW (needs 3D Secure — React only) |
| `canceled` / error | REJECTED |

**Important:** Store `PaymentIntent.id` (`pi_XXXXX`) on the `PaymentTransactionEntry.requestId` so `CaptureCommand` can find it later.

### Story 2.4: StripeCaptureCommand

**Files to create:**
- `coremcp/src/com/coremcp/payment/commands/StripeCaptureCommand.java`

**Details:**
Called by `TakePaymentAction` during order fulfillment. Captures a previously authorized PaymentIntent:

```
PaymentIntent.retrieve(paymentIntentId)  // from authorization entry's requestId
PaymentIntent.capture()
```

**Response mapping:**
| Stripe Status | Commerce TransactionStatus |
|---------------|---------------------------|
| `succeeded` | ACCEPTED |
| `canceled` / error | ERROR |

### Acceptance Criteria — Phase 2
- [ ] All four command classes compile and are wired into Spring
- [ ] `StripePaymentCommandFactory` registered and overrides mock factory
- [ ] Unit tests: each command maps Stripe responses to correct Commerce transaction statuses
- [ ] `./gradlew ybuild stopServer startServer` — server starts without errors

---

## Phase 3: React Checkout Flow (OCC Endpoints)

**Scope:** New OCC endpoints for the React frontend to create PaymentIntents, handle client-side confirmation callbacks, and receive Stripe webhooks.

**Why:** React uses Stripe.js for client-side card collection and 3D Secure. The Commerce backend must create the PaymentIntent and return `client_secret` so Stripe.js can confirm it. This is a different flow than MCP (which confirms server-side).

### Story 3.1: Create PaymentIntent OCC Endpoint

**Files to create:**
- `coremcp/src/com/coremcp/controllers/StripePaymentController.java`
- `coremcp/src/com/coremcp/facades/StripeCheckoutFacade.java` (interface)
- `coremcp/src/com/coremcp/facades/impl/DefaultStripeCheckoutFacade.java`

**Files to modify:**
- `coremcp/resources/coremcp-spring.xml` — add facade + controller beans

**Endpoint:**
```
POST /occ/v2/{baseSiteId}/users/{userId}/carts/{cartId}/stripe/payment-intent
```

**Request body:**
```json
{
    "paymentMethodId": "pm_XXXXX"   // from Stripe.js Elements
}
```

**Flow:**
1. `StripeCheckoutFacade` receives `paymentMethodId` from React (already tokenized by Stripe.js)
2. Creates/retrieves Stripe Customer for the Commerce user
3. Attaches PaymentMethod to Customer
4. Stores `CreditCardPaymentInfo` on cart (with `subscriptionId = pm_XXXXX`)
5. Creates `PaymentIntent` with `capture_method=manual`, `confirm=false` (React will confirm client-side)
6. Returns:
```json
{
    "clientSecret": "pi_XXXXX_secret_YYYYY",
    "paymentIntentId": "pi_XXXXX",
    "publishableKey": "pk_test_XXXXX",
    "requiresAction": false
}
```

**Why a new endpoint (not modifying existing `checkout_set_payment`):**
The existing OCC checkout endpoints use `CCPaymentInfoData` with raw card numbers. Stripe.js tokenizes client-side — raw card numbers never reach our server. This is a fundamentally different data flow that needs its own endpoint. The existing endpoint continues to work for MCP.

### Story 3.2: Confirm Payment OCC Endpoint

**Endpoint:**
```
POST /occ/v2/{baseSiteId}/users/{userId}/carts/{cartId}/stripe/confirm
```

**Request body:**
```json
{
    "paymentIntentId": "pi_XXXXX"
}
```

**Flow:**
1. After React's Stripe.js confirms the PaymentIntent (including 3D Secure if needed)
2. React calls this endpoint to notify Commerce
3. Facade retrieves the PaymentIntent from Stripe, verifies status is `requires_capture`
4. Creates `PaymentTransaction` + `PaymentTransactionEntry(AUTHORIZATION, ACCEPTED)` on the cart
5. Returns success — cart is now ready for `placeOrder`

**Why needed:** In the React flow, authorization happens client-side via Stripe.js. Commerce needs to record the successful authorization before `placeOrder` can proceed. Without this, `placeOrder` would try to authorize again via the command pattern.

### Story 3.3: Stripe Webhook Endpoint

**Files to create:**
- `coremcp/src/com/coremcp/controllers/StripeWebhookController.java`
- `coremcp/src/com/coremcp/services/StripeWebhookService.java` (interface)
- `coremcp/src/com/coremcp/services/impl/DefaultStripeWebhookService.java`

**Endpoint:**
```
POST /occ/v2/{baseSiteId}/stripe/webhook
```

**Handled events:**
| Stripe Event | Action |
|--------------|--------|
| `payment_intent.succeeded` | Update PaymentTransactionEntry status to ACCEPTED |
| `payment_intent.payment_failed` | Update PaymentTransactionEntry status to REJECTED |
| `charge.refunded` | (Future — not in scope but stub the handler) |

**Security:**
- Verify webhook signature using `coremcp.stripe.webhook.secret`
- Reject requests with invalid signatures (return 400)
- Endpoint is unauthenticated (Stripe calls it) but signature-verified

### Story 3.4: Modify placeOrder for React Flow

**Files to modify:**
- `coremcp/src/com/coremcp/facades/impl/DefaultStripeCheckoutFacade.java` — add `placeOrderWithStripe()`

**Details:**
The React flow needs a modified `placeOrder` that skips the authorization step (already done client-side):
1. Verify cart has a `PaymentTransaction` with AUTHORIZATION entry status ACCEPTED
2. Skip `checkoutFacade.authorizePayment()`
3. Call `checkoutFacade.placeOrder()` directly

This could be:
- A new OCC endpoint: `POST /{baseSiteId}/users/{userId}/orders/stripe-place`
- Or: the existing `placeOrder` detects that authorization already exists and skips re-auth

**Recommended:** Override `DefaultCheckoutFacade` via the alias pattern to check for existing Stripe authorization before calling `authorizePayment()`. This way the standard `placeOrder` OCC endpoint works for both flows.

### Acceptance Criteria — Phase 3
- [ ] `POST .../stripe/payment-intent` returns `clientSecret` for Stripe.js
- [ ] `POST .../stripe/confirm` records authorization on cart
- [ ] Webhook endpoint verifies signatures and handles `payment_intent.succeeded/failed`
- [ ] `placeOrder` works for React flow (skips re-authorization when Stripe auth exists)
- [ ] Build compiles, server starts, endpoints appear in Swagger

---

## Phase 4: MCP Checkout Flow

**Scope:** Update the MCP tool handlers for the server-side Stripe flow (no browser, no Stripe.js).

**Why separate from Phase 3:** MCP creates PaymentMethods server-side (sends card numbers through the Commerce layer), and confirms PaymentIntents server-side with `confirm=true`. Different data flow, same Stripe commands underneath.

### Story 4.1: Update CheckoutSetPaymentToolHandler

**Files to modify:**
- `coremcp/src/com/coremcp/tools/impl/CheckoutSetPaymentToolHandler.java`

**Changes:**
The existing handler already creates a payment subscription via `checkoutFacade.createPaymentSubscription()`. Under the hood, this now calls `StripeCreateSubscriptionCommand` (from Phase 2), which:
1. Creates a Stripe Customer
2. Creates a PaymentMethod with the card details
3. Returns `subscriptionId = pm_XXXXX`

**Minimal changes needed:**
- Update tool description to mention Stripe (remove "mock payment" language)
- Update default test card to Stripe's test card: `4242424242424242` (instead of `4111111111111111`)
- Add `stripeCustomerId` to the response (useful for debugging)

The `createPaymentSubscription` → `StripeCreateSubscriptionCommand` mapping handles the rest automatically.

### Story 4.2: Update OrderPlaceToolHandler

**Files to modify:**
- `coremcp/src/com/coremcp/tools/impl/OrderPlaceToolHandler.java`

**Changes:**
The existing handler calls `checkoutFacade.authorizePayment(securityCode)` → `placeOrder()`. Under the hood, `authorizePayment` now calls `StripeAuthorizationCommand` (from Phase 2), which creates a PaymentIntent with `confirm=true` server-side.

**Minimal changes needed:**
- The `securityCode` parameter becomes less relevant for Stripe (CVC is validated at PaymentMethod creation time). Keep it for backward compatibility but it won't be passed to Stripe during authorization.
- Add error handling for Stripe-specific failures (e.g., card declined, insufficient funds)
- Return Stripe PaymentIntent ID in the response for traceability

### Story 4.3: Update Tool Descriptions and Schema

**Files to modify:**
- `CheckoutSetPaymentToolHandler.java` — update `getDescription()` and `getInputSchema()`
- `OrderPlaceToolHandler.java` — update `getDescription()`

**New `checkout_set_payment` description:**
```
Set payment details for the current order. This is step 3 of checkout.
For testing with Stripe test mode, call with no arguments to use default test card (Visa ending 4242).
All parameters have sensible defaults. Must be called after checkout_set_delivery_mode.
Requires customer authentication.
```

### Acceptance Criteria — Phase 4
- [ ] MCP `checkout_set_payment` creates a Stripe PaymentMethod and Customer
- [ ] MCP `order_place` creates and confirms a Stripe PaymentIntent server-side
- [ ] Fulfillment `TakePaymentAction` captures the Stripe PaymentIntent
- [ ] End-to-end MCP checkout completes with Stripe test keys
- [ ] Error messages surface Stripe decline reasons (card_declined, insufficient_funds, etc.)

---

## Phase 5: Data & Configuration

**Scope:** Update BaseStore payment provider, ImpEx seed data, and ensure clean initialization.

### Story 5.1: Update BaseStore Payment Provider

**Files to modify:**
- `sampledatamcp/resources/impex/essentialdata-infrastructure.impex`

**Change:**
```impex
# Before:
; electronics ; ... ; Mockup ; ...

# After:
; electronics ; ... ; Stripe ; ...
```

This makes `BaseStore.paymentProvider = "Stripe"`, which the `PaymentService` uses to look up the correct `CommandFactory`.

### Story 5.2: Update Test Data

**Files to modify:**
- `coremcp/resources/coremcp/test/testdata-thinkshop.impex` — update payment provider references

**Changes:**
- Update any BaseStore references from `Mockup` to `Stripe`
- Keep test card defaults as Stripe test cards (`4242424242424242`)

### Story 5.3: Update local.properties Templates

**Files to modify:**
- `core-customize/hybris/config/local.properties`
- Create `core-customize/dev-config/local.properties` overlay if needed

**Add:**
```properties
# === Stripe Payment Configuration ===
coremcp.stripe.secret.key=sk_test_REPLACE_ME
coremcp.stripe.publishable.key=pk_test_REPLACE_ME
coremcp.stripe.webhook.secret=whsec_REPLACE_ME
```

### Acceptance Criteria — Phase 5
- [ ] `./gradlew yclean yall yinitialize` — clean init succeeds with Stripe provider
- [ ] `./gradlew ybuild stopServer startServer yupdatesystem` — update system succeeds
- [ ] BaseStore shows `paymentProvider = Stripe` in Backoffice
- [ ] No references to `Mockup` provider remain in custom extensions

---

## Phase 6: Documentation

**Scope:** Create flow documentation for the Stripe payment integration.

### Story 6.1: Create Payment Flow Documentation

**Files to create:**
- `coremcp/docs/stripe-payment/context.md` — what the flow does, key decisions, both client channels
- `coremcp/docs/stripe-payment/components.md` — all files and their roles
- `coremcp/docs/stripe-payment/diagram.md` — Mermaid sequence diagrams for React and MCP flows

**context.md content:**
- Why Stripe Payment Intents (SCA, dual-channel)
- React flow vs MCP flow decision points
- Key configuration (properties, BaseStore provider)
- Testing with Stripe test cards

**diagram.md content:**
- Sequence diagram: React checkout flow (Stripe.js → OCC → Stripe API → webhook)
- Sequence diagram: MCP checkout flow (tool handler → facade → command → Stripe API)
- Sequence diagram: Fulfillment capture (TakePaymentAction → Stripe capture)

### Acceptance Criteria — Phase 6
- [ ] All three doc files exist and are accurate
- [ ] Diagrams render correctly in Mermaid
- [ ] Both React and MCP flows are documented

---

## Dependency Graph

```
Phase 1 (Foundation)
  ↓
Phase 2 (Commands) ← depends on SDK + config
  ↓
Phase 3 (React OCC) ← depends on commands
Phase 4 (MCP Tools) ← depends on commands (parallel with Phase 3)
  ↓
Phase 5 (Data & Config) ← depends on commands being registered
  ↓
Phase 6 (Documentation) ← depends on all above being final
```

Phases 3 and 4 can run in parallel after Phase 2.

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Stripe SDK version conflict with platform JARs | Build failure | Pin version, test classpath isolation |
| `external-dependencies.xml` not resolving in CCv2 build | SDK not on classpath | Fallback: manually place JAR in `lib/` directory |
| 3D Secure flow requires frontend changes beyond OCC | React team blocked | Document the Stripe.js integration contract clearly; OCC endpoints are the boundary |
| PaymentIntent expires before capture (default 7 days) | Capture fails in fulfillment | Add monitoring; Stripe allows extending to 31 days via `payment_intent_data.capture_method` |
| Commerce `authorizePayment` called twice (once by React confirm, once by placeOrder) | Duplicate Stripe charges | Phase 3 Story 3.4 — detect existing authorization and skip |

---

## Out of Scope

- Refunds (can be added as a future phase)
- Partial capture / split payments
- Apple Pay / Google Pay (would use same PaymentIntent flow but different PaymentMethod types)
- Stripe Connect / marketplace payments
- PCI DSS compliance audit (Stripe.js handles PCI scope reduction for React; MCP server-side card handling requires SAQ-D)
- React frontend implementation (we provide OCC endpoints + Stripe.js contract only)
