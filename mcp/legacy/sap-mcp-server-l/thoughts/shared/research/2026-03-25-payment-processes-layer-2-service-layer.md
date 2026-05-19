# Layer 2: Service Layer — Payment Processes
Date: 2026-03-25

## Executive Summary
Payment processing in SAP Commerce 2211.50 follows a well-defined chain: the MCP tool handler (`CheckoutSetPaymentToolHandler`) delegates to `CheckoutFacade` which routes payment operations through `CommerceCheckoutService` strategies. Payment creation uses `CustomerAccountService.createPaymentSubscription()`, authorization flows through `CommercePaymentAuthorizationStrategy`, and order fulfillment capture happens via `TakePaymentAction` in the order process. The system uses OOTB mock payment commands with no custom PSP integration.

---

## MCP Tool → OCC Controller Chain

### CheckoutSetPaymentToolHandler (MCP Entry Point)
**File:** `/core-customize/hybris/bin/custom/coremcp/src/com/coremcp/tools/impl/CheckoutSetPaymentToolHandler.java`

The MCP tool handler intercepts `checkout_set_payment` calls and routes them to the `CheckoutFacade`:

1. **Input handling** (lines 60-88):
   - Accepts either `paymentId` (pre-existing) or new card details (cardNumber, expiryMonth, expiryYear, cardType, nameOnCard)
   - Falls back to delivery address as billing address if not provided
   - Defaults: Visa 4111111111111111, expiry 12/2028

2. **Two-path execution**:
   - **Path A (paymentId)**: Calls `checkoutFacade.setPaymentDetails(paymentId)` to select existing payment
   - **Path B (new card)**: Calls `checkoutFacade.createPaymentSubscription(paymentInfo)` to create new subscription, then implicitly sets it

3. **Card masking**: Returns masked card (last 4 digits only) in response

### Spring Bean Wiring (coremcp-spring.xml)
**File:** `/core-customize/hybris/bin/custom/coremcp/resources/coremcp-spring.xml` (lines 96-100)

```xml
<alias name="defaultCheckoutSetPaymentToolHandler" alias="checkoutSetPaymentToolHandler"/>
<bean id="defaultCheckoutSetPaymentToolHandler"
      class="com.coremcp.tools.impl.CheckoutSetPaymentToolHandler">
    <property name="checkoutFacade" ref="checkoutFacade"/>
</bean>
```

The handler depends on the `checkoutFacade` bean reference (defined elsewhere in OOTB).

---

## Facade Layer (CheckoutFacade Payment Methods)

### CheckoutFacade Interface
**File:** `/core-customize/hybris/bin/modules/commerce-services/commercefacades/src/de/hybris/platform/commercefacades/order/CheckoutFacade.java`

Defines three key payment methods:

| Method | Purpose | Key Parameters |
|--------|---------|-----------------|
| `setPaymentDetails(paymentInfoId)` | Select saved payment | Payment info ID |
| `createPaymentSubscription(CCPaymentInfoData)` | Create new card subscription | Card info + billing address |
| `authorizePayment(securityCode)` | Authorize payment for order | CVV/CVV2 security code |

### DefaultCheckoutFacade Implementation
**File:** `/core-customize/hybris/bin/modules/commerce-services/commercefacades/src/de/hybris/platform/commercefacades/order/impl/DefaultCheckoutFacade.java`

#### setPaymentDetails() (lines 456-477)
Selects an existing saved payment info for the cart:
```
1. Validates paymentInfoId is not null
2. Checks current user owns the cart
3. Calls CustomerAccountService.getCreditCardPaymentInfoForCode()
4. Wraps in CommerceCheckoutParameter
5. Delegates to CommerceCheckoutService.setPaymentInfo(parameter)
```

#### createPaymentSubscription() (lines 486-521)
Creates new payment subscription with card details:
```
1. Validates CCPaymentInfoData and billing address
2. Converts facade data to PaymentService DTOs:
   - CardInfo: cardNumber, expirationMonth/Year, cardType, cardHolderFullName
   - BillingInfo: address fields (firstName, lastName, street, city, postal, country)
3. Calls CustomerAccountService.createPaymentSubscription()
   - Params: currentUser, cardInfo, billingInfo, titleCode, paymentProvider, isSaved
4. Returns converted CCPaymentInfoData with saved subscription ID
```

#### authorizePayment() (lines 524-543)
Authorizes payment during checkout:
```
1. Extracts cart and payment info
2. Creates CommerceCheckoutParameter with securityCode and paymentProvider
3. Calls CommerceCheckoutService.authorizePayment(parameter)
4. Returns true if transaction status is ACCEPTED or REVIEW
5. Creates PaymentTransaction and PaymentTransactionEntry records
```

#### placeOrder() (lines 546-563)
Final checkout step that places the order:
```
1. Validates current user owns cart
2. Calls placeOrder(cartModel) which delegates to:
   CommerceCheckoutService.placeOrder(parameter) → returns CommerceOrderResult
3. Converts OrderModel to OrderData facade object
4. Removes session cart on success
```

---

## Service Layer (CommerceCheckoutService & Payment Strategies)

### CommerceCheckoutService Interface
**File:** `/core-customize/hybris/bin/modules/commerce-services/commerceservices/src/de/hybris/platform/commerceservices/order/CommerceCheckoutService.java`

Core checkout service methods:
- `setPaymentInfo(CommerceCheckoutParameter)` → delegates to `CommercePaymentInfoStrategy`
- `authorizePayment(CommerceCheckoutParameter)` → delegates to `CommercePaymentAuthorizationStrategy`
- `placeOrder(CommerceCheckoutParameter)` → delegates to `CommercePlaceOrderStrategy`

### DefaultCommerceCheckoutService
**File:** `/core-customize/hybris/bin/modules/commerce-services/commerceservices/src/de/hybris/platform/commerceservices/order/impl/DefaultCommerceCheckoutService.java`

```java
// Line 211-222: authorizePayment()
public PaymentTransactionEntryModel authorizePayment(final CommerceCheckoutParameter parameter) {
    // Set authorization amount from cart total if not provided
    if (parameter.getAuthorizationAmount() == null) {
        parameter.setAuthorizationAmount(calculateAuthAmount(cartModel));
    }
    // Delegate to authorization strategy
    return getCommercePaymentAuthorizationStrategy().authorizePaymentAmount(parameter);
}

// Line 232-242: calculateAuthAmount()
// Calculates cart total + tax (if external taxes enabled)
```

### DefaultCommercePaymentInfoStrategy
**File:** `/core-customize/hybris/bin/modules/commerce-services/commerceservices/src/de/hybris/platform/commerceservices/order/impl/DefaultCommercePaymentInfoStrategy.java`

Stores payment info on cart:
```java
public boolean storePaymentInfoForCart(final CommerceCheckoutParameter parameter) {
    // 1. Extract cart and payment info from parameter
    // 2. Set paymentInfo on cart: cartModel.setPaymentInfo(paymentInfoModel)
    // 3. Save cart and payment info to DB
    // 4. Recalculate cart totals via CommerceCartCalculationStrategy
    // 5. Return true
}
```

---

## Payment Strategies (Authorization & Creation)

### CommercePaymentAuthorizationStrategy
**File:** `/core-customize/hybris/bin/modules/commerce-services/commerceservices/src/de/hybris/platform/commerceservices/order/CommercePaymentAuthorizationStrategy.java`

Interface defines single method:
```java
PaymentTransactionEntryModel authorizePaymentAmount(CommerceCheckoutParameter parameter);
```

### DefaultCommercePaymentAuthorizationStrategy
**File:** `/core-customize/hybris/bin/modules/commerce-services/commerceservices/src/de/hybris/platform/commerceservices/order/impl/DefaultCommercePaymentAuthorizationStrategy.java`

**Authorization flow (lines 43-87):**

```java
public PaymentTransactionEntryModel authorizePaymentAmount(final CommerceCheckoutParameter parameter) {
    // 1. Extract parameters:
    final CartModel cartModel = parameter.getCart();
    final BigDecimal amount = parameter.getAuthorizationAmount();
    final String securityCode = parameter.getSecurityCode();
    final String paymentProvider = parameter.getPaymentProvider();

    // 2. Execute hooks (before):
    beforeAuthorizePaymentAmount(parameter);

    // 3. Generate merchant transaction code:
    final String merchantTransactionCode = getGenerateMerchantTransactionCodeStrategy()
        .generateCode(cartModel);

    // 4. Call PaymentService to authorize:
    transactionEntryModel = getPaymentService().authorize(
        merchantTransactionCode,  // Unique transaction ID
        amount,                   // Cart total
        currency,                 // Cart currency
        cartModel.getDeliveryAddress(),  // Billing/delivery address
        ((CreditCardPaymentInfoModel) paymentInfo).getSubscriptionId(),  // Stored card
        securityCode,            // CVV/CVV2
        paymentProvider          // "Mockup" in mock system
    );

    // 5. Handle transaction entry response:
    if (transactionEntryModel != null) {
        final PaymentTransactionModel paymentTransaction = transactionEntryModel.getPaymentTransaction();

        // 6. If ACCEPTED or REVIEW: persist PaymentTransaction to cart
        if (TransactionStatus.ACCEPTED.name().equals(transactionEntryModel.getTransactionStatus())
            || TransactionStatus.REVIEW.name().equals(transactionEntryModel.getTransactionStatus())) {
            paymentTransaction.setOrder(cartModel);  // Link to cart (will be order later)
            paymentTransaction.setInfo(paymentInfo);
            getModelService().saveAll(cartModel, paymentTransaction);
        }
        // Otherwise: remove failed transaction
        else {
            getModelService().removeAll(Arrays.asList(paymentTransaction, transactionEntryModel));
        }
    }

    // 7. Execute hooks (after):
    afterAuthorizePaymentAmount(parameter, transactionEntryModel);

    return transactionEntryModel;
}
```

**Key points:**
- Calls `PaymentService.authorize()` which invokes mock payment commands
- Creates `PaymentTransactionEntry` with status (ACCEPTED, REVIEW, ERROR, REJECTED)
- Links `PaymentTransaction` to cart (before order conversion)
- Only persists transaction if status is ACCEPTED/REVIEW

### Mock Payment Implementation
**File:** `/core-customize/hybris/bin/modules/commerce-services/commerceservices/resources/commerceservices/mock-payment-spring.xml`

Spring bean configuration for mock payment provider:

```xml
<bean name="mockupCommandFactory" class="de.hybris.platform.payment.commands.factory.impl.DefaultCommandFactoryImpl">
    <property name="paymentProvider" value="Mockup"/>
    <property name="commands">
        <map>
            <!-- Authorization for credit cards -->
            <entry>
                <key><value type="java.lang.Class">
                    de.hybris.platform.payment.commands.AuthorizationCommand
                </value></key>
                <bean class="de.hybris.platform.payment.commands.impl.AuthorizationMockCommand" />
            </entry>
            
            <!-- Subscription authorization (stored cards) -->
            <entry>
                <key><value type="java.lang.Class">
                    de.hybris.platform.payment.commands.SubscriptionAuthorizationCommand
                </value></key>
                <bean class="de.hybris.platform.payment.commands.impl.SubscriptionAuthorizationMockCommand" />
            </entry>
            
            <!-- Capture during fulfillment -->
            <entry>
                <key><value type="java.lang.Class">
                    de.hybris.platform.payment.commands.CaptureCommand
                </value></key>
                <bean class="de.hybris.platform.payment.commands.impl.CaptureMockCommand" />
            </entry>
            
            <!-- Subscription management -->
            <entry>
                <key><value type="java.lang.Class">
                    de.hybris.platform.payment.commands.CreateSubscriptionCommand
                </value></key>
                <bean class="de.hybris.platform.payment.commands.impl.CreateSubscriptionMockCommand" />
            </entry>
        </map>
    </property>
</bean>
```

**No custom payment provider** — the system uses OOTB mock commands that always return SUCCESS (ACCEPTED status).

---

## Fulfillment Process — TakePayment Action

### Order Process Definition
**File:** `/core-customize/hybris/bin/modules/base-accelerator/yacceleratorfulfilmentprocess/resources/yacceleratorfulfilmentprocess/process/order-process.xml`

Order workflow includes payment capture (lines 65-72):

```xml
<action id="sendOrderPlacedNotification" bean="sendOrderPlacedNotificationAction">
    <transition name="OK" to="takePayment"/>
</action>

<action id="takePayment" bean="takePaymentAction">
    <transition name="OK" to="splitOrder"/>
    <transition name="NOK" to="sendPaymentFailedNotification"/>
</action>

<action id="splitOrder" bean="splitOrderAction">
    <transition name="OK" to="waitForWarehouseSubprocessEnd"/>
</action>
```

**Flow:**
1. Order placed notification sent
2. **TakePayment action triggered** ← Payment capture happens here
3. If capture succeeds (OK): split order and proceed to fulfillment
4. If capture fails (NOK): send payment failed notification and fail order

### TakePaymentAction Implementation
**File:** `/core-customize/hybris/bin/modules/base-accelerator/yacceleratorfulfilmentprocess/src/de/hybris/platform/yacceleratorfulfilmentprocess/actions/order/TakePaymentAction.java`

Captures authorized payment during order fulfillment:

```java
public class TakePaymentAction extends AbstractSimpleDecisionAction<OrderProcessModel> {
    private PaymentService paymentService;

    @Override
    public Transition executeAction(final OrderProcessModel process) {
        final OrderModel order = process.getOrder();

        // 1. Iterate over all payment transactions linked to order
        for (final PaymentTransactionModel txn : order.getPaymentTransactions()) {
            
            // 2. Check if credit card payment
            if (txn.getInfo() instanceof CreditCardPaymentInfoModel) {
                
                // 3. Call PaymentService to capture (finalize) the authorized payment
                final PaymentTransactionEntryModel txnEntry = getPaymentService().capture(txn);

                // 4. Check capture result
                if (TransactionStatus.ACCEPTED.name().equals(txnEntry.getTransactionStatus())) {
                    LOG.debug("Payment captured for order: " + order.getCode());
                    setOrderStatus(order, OrderStatus.PAYMENT_CAPTURED);
                } else {
                    // 5. If capture failed: set order status and return NOK
                    LOG.error("Payment capture failed for order: " + order.getCode());
                    setOrderStatus(order, OrderStatus.PAYMENT_NOT_CAPTURED);
                    return Transition.NOK;
                }
            }
        }
        return Transition.OK;  // All payments captured successfully
    }
}
```

**Key points:**
- Runs **after** order is converted from cart (OrderModel created with paymentTransactions)
- Calls `PaymentService.capture(PaymentTransactionModel)` → invokes CaptureMockCommand
- Creates second `PaymentTransactionEntry` with capture status
- Sets OrderStatus to PAYMENT_CAPTURED or PAYMENT_NOT_CAPTURED
- If any capture fails, order process transitions to NOK path

### Spring Bean Configuration
**File:** `/core-customize/hybris/bin/modules/base-accelerator/yacceleratorfulfilmentprocess/resources/yacceleratorfulfilmentprocess/process/order-process-spring.xml` (lines 34-36)

```xml
<bean id="takePaymentAction" class="de.hybris.platform.yacceleratorfulfilmentprocess.actions.order.TakePaymentAction"
      parent="abstractAction">
    <property name="paymentService" ref="paymentService"/>
</bean>
```

Injects `paymentService` (OOTB service for payment command execution).

---

## PaymentTransaction & PaymentTransactionEntry Models

### Data Model Flow

**During Checkout (authorization):**
```
Cart (has paymentInfo: CreditCardPaymentInfoModel)
  ↓ [authorizePayment()]
PaymentService.authorize() → PaymentService.authorize() invokes AuthorizationMockCommand
  ↓
PaymentTransaction (created, linked to Cart via paymentTransactions collection)
├── info: CreditCardPaymentInfoModel (subscription ID)
├── order: CartModel
├── code: generated transaction code
└── entries: [PaymentTransactionEntry]
    └── PaymentTransactionEntry (AUTHORIZATION entry)
        ├── transactionStatus: ACCEPTED
        ├── transactionStatusDetails: null
        ├── amount: authorized amount
        ├── code: merchant transaction code
        └── type: AUTHORIZATION

Cart converted to Order → Order inherits paymentTransactions collection
```

**During Fulfillment (capture):**
```
Order (has paymentTransactions from cart)
  ↓ [TakePaymentAction.executeAction()]
PaymentService.capture(PaymentTransaction) → invokes CaptureMockCommand
  ↓
PaymentTransactionEntry (CAPTURE entry, added to same transaction)
├── transactionStatus: ACCEPTED
├── amount: captured amount (same as auth)
├── type: CAPTURE
└── code: merchant transaction code
```

---

## Spring Bean Wiring Summary

### Key Bean Dependencies

| Bean | Type | Injected Into | Purpose |
|------|------|---------------|---------|
| `checkoutFacade` | DefaultCheckoutFacade | CheckoutSetPaymentToolHandler | Facade for checkout operations |
| `commerceCheckoutService` | DefaultCommerceCheckoutService | DefaultCheckoutFacade | Checkout service (delegates to strategies) |
| `commercePaymentAuthorizationStrategy` | DefaultCommercePaymentAuthorizationStrategy | DefaultCommerceCheckoutService | Authorization logic |
| `commercePaymentInfoStrategy` | DefaultCommercePaymentInfoStrategy | DefaultCommerceCheckoutService | Payment info storage logic |
| `paymentService` | PaymentService (OOTB) | DefaultCommercePaymentAuthorizationStrategy, TakePaymentAction | Payment command execution |
| `mockupCommandFactory` | DefaultCommandFactoryImpl | PaymentService | Mock command provider |
| `customerAccountService` | CustomerAccountService (OOTB) | DefaultCheckoutFacade | Subscription/card management |

### Alias Pattern
Throughout the codebase, beans use "default" prefix with aliases for swapping:
```xml
<alias name="defaultCheckoutFacade" alias="checkoutFacade"/>
<bean id="defaultCheckoutFacade" class="...DefaultCheckoutFacade">
    ...
</bean>
```

This allows custom implementations to override defaults without changing bean references.

---

## Complete Call Chain

```
MCP Tool Layer:
  checkout_set_payment (MCP call)
    ↓
  CheckoutSetPaymentToolHandler.execute()
    ├─ (Path A) checkoutFacade.setPaymentDetails(paymentId)
    │   ↓
    │   DefaultCheckoutFacade.setPaymentDetails()
    │     ↓
    │     customerAccountService.getCreditCardPaymentInfoForCode()
    │     ↓
    │     commerceCheckoutService.setPaymentInfo(parameter)
    │       ↓
    │       DefaultCommercePaymentInfoStrategy.storePaymentInfoForCart()
    │         ↓
    │         cartModel.setPaymentInfo(paymentInfo)
    │         modelService.save()
    │         commerceCartCalculationStrategy.calculateCart()
    │
    └─ (Path B) checkoutFacade.createPaymentSubscription()
        ↓
        DefaultCheckoutFacade.createPaymentSubscription()
          ↓
          customerAccountService.createPaymentSubscription(cardInfo, billingInfo)
            ↓
            paymentService.createSubscription()
              ↓
              mockupCommandFactory.getCommand(CreateSubscriptionCommand)
                ↓
                CreateSubscriptionMockCommand.perform()
                  ↓
                  return subscriptionId
          ↓
          commerceCheckoutService.setPaymentInfo() [implicit]
            ↓
            DefaultCommercePaymentInfoStrategy.storePaymentInfoForCart()

Checkout Complete:
  order_place (MCP call)
    ↓
  CheckoutFacade.placeOrder()
    ↓
    commerceCheckoutService.placeOrder(parameter)
      ↓
      CommercePlaceOrderStrategy.placeOrder()
        ↓
        orderService.createOrderFromCart()
          ↓
          OrderModel created with paymentTransactions linked
        ↓
        return CommerceOrderResult.getOrder()

Order Fulfillment:
  OrderProcess.execute() → [authorization → fraud → TakePayment]
    ↓
  TakePaymentAction.executeAction()
    ↓
    for each order.getPaymentTransactions():
      paymentService.capture(paymentTransaction)
        ↓
        mockupCommandFactory.getCommand(CaptureCommand)
          ↓
          CaptureMockCommand.perform()
            ↓
            PaymentTransactionEntry (CAPTURE) created with ACCEPTED status
        ↓
        OrderStatus.PAYMENT_CAPTURED
```

---

## Configuration & Properties

### No Custom Payment Properties Found
- System uses OOTB mock payment provider ("Mockup" hardcoded)
- No external PSP configuration in local.properties
- Payment authentication hook can be disabled via property:
  - `commerceservices.authorizepaymenthook.enabled=true` (default)

### Mock Payment Always Succeeds
All mock commands (authorization, capture, subscription) return:
- `transactionStatus = ACCEPTED`
- No declined or error responses

---

## Summary

The payment layer consists of:

1. **MCP Handler** → `CheckoutSetPaymentToolHandler` accepts card details and routes to facade
2. **Facade** → `DefaultCheckoutFacade` converts MCP data to domain models, delegates to service
3. **Service** → `DefaultCommerceCheckoutService` with injected strategies for payment info + authorization
4. **Authorization Strategy** → `DefaultCommercePaymentAuthorizationStrategy` calls `PaymentService.authorize()`, creates `PaymentTransaction/Entry` records
5. **Fulfillment Action** → `TakePaymentAction` in order process calls `PaymentService.capture()` to finalize payment
6. **Mock Commands** → All payment operations use OOTB mock command factory (always succeeds)

No custom payment processors or external PSPs are loaded — the system is entirely mock-based for testing and demonstration purposes.
