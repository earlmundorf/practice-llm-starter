# Layer 1: Type System — Payment Processes
Date: 2026-03-25

## Custom Payment Types (from custom extensions' items.xml)

**None.** Both `coremcp` and `sampledatamcp` have empty/minimal items.xml files with no custom payment types. Payment functionality relies entirely on OOTB types.

## OOTB Payment Types

### Core Platform (core-items.xml)
| Type | Typecode | Description |
|------|----------|-------------|
| `PaymentMode` | 41 | Payment method definition (e.g., "credit card", "invoice") |
| `PaymentInfo` | 42 | Base payment info attached to orders/carts |

### Payment Module (payment-items.xml)
| Type | Typecode | Description |
|------|----------|-------------|
| `PaymentTransaction` | 2100 | Represents a payment transaction against an order |
| `PaymentTransactionEntry` | 2101 | Individual entry in a transaction (auth, capture, etc.) |
| `CreditCardPaymentInfo` | — | Extends PaymentInfo with card details + subscriptionId |

### Base Commerce (basecommerce-items.xml)
| Type | Description |
|------|-------------|
| `SAPGenericPaymentInfo` | Extends PaymentInfo with SAP-specific fields: sapCartId, sapPaymentMethod, sapCardType, sapCapturePattern |

## Payment Enums

| Enum | Values | Purpose |
|------|--------|---------|
| `PaymentTransactionType` | AUTHORIZATION, CAPTURE, PARTIAL_CAPTURE, REFUND_FOLLOW_ON, REFUND_STANDALONE, CANCEL, + subscription types | Classifies transaction entries |
| `TransactionStatus` | ACCEPTED, ERROR, REJECTED, REVIEW | Transaction outcome |
| `TransactionStatusDetails` | 50+ codes | Detailed failure/success reasons |
| `SAPCapturePattern` | AUTO_CAPTURE, CAPTURE_PER_SHIPMENT, PARTIAL_CAPTURE | When capture happens relative to fulfillment |

## Payment Relations

```
Order ──(1:N)──> PaymentTransaction ──(1:N)──> PaymentTransactionEntry
  │
  └──(1:1)──> PaymentInfo (or CreditCardPaymentInfo)

Cart ──(1:1)──> PaymentInfo
```

- Order → PaymentTransaction: one order can have multiple transactions (auth + capture + refund)
- PaymentTransaction → PaymentTransactionEntry: each transaction has entries for each step
- Order/Cart → PaymentInfo: stores the payment method details

## Payment DTOs (from beans.xml)

| DTO | Extension | Key Fields |
|-----|-----------|------------|
| `CCPaymentInfoData` | commercefacades | cardNumber, cardType, expiryMonth/Year, billingAddress |
| `PaymentModeData` | commercefacades | code, name, description |
| `PaymentMethodData` | commercefacades | code, name |
| `GenericPaymentInfoData` | commercefacades | SAP payment method fields |

## Summary

The custom extensions define zero payment types — all payment processing uses OOTB types from the platform core, payment module, and basecommerce. The key chain is: PaymentMode (defines available methods) → PaymentInfo/CreditCardPaymentInfo (stores chosen method on cart/order) → PaymentTransaction + PaymentTransactionEntry (records auth/capture/refund lifecycle). Enums track transaction types, statuses, and capture patterns. No PSP-specific types are loaded.
