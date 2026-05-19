# Layer 0: Extension Manifest — Payment Processes
Date: 2026-03-25

## Extension Dependency Graph (Payment-Relevant)

```
basecommerce
  └── commerceservices
        └── commercefacades
              └── acceleratorfacades
              └── commercewebservicescommons
                    └── commercewebservices  ← OCC API layer
                          └── coremcp (custom)

yacceleratorcore  ← order/fulfillment process definitions
yacceleratorfulfilmentprocess  ← BPM for order processing (includes payment capture)
```

## Payment-Relevant Extensions in localextensions.xml

| Extension | Role in Payment |
|-----------|----------------|
| `basecommerce` | Core payment type system (PaymentInfo, PaymentMode, PaymentTransaction) |
| `commerceservices` | Payment service layer (CommerceCheckoutService, payment strategies) |
| `commercefacades` | Payment facades (CheckoutFacade, payment DTOs) |
| `acceleratorfacades` | Accelerator-specific checkout/payment facades |
| `commercewebservices` | OCC REST endpoints for payment operations |
| `commercewebservicescommons` | Shared OCC payment DTOs and validators |
| `yacceleratorcore` | Order process definitions including payment actions |
| `yacceleratorfulfilmentprocess` | Business process for order fulfillment — TakePayment action |
| `oauth2` | Token-based auth (secures payment endpoints) |

## Custom Extension Payment Touchpoints

- **coremcp**: Depends on `commercewebservices`. Exposes MCP tools for checkout including `checkout_set_payment`. Routes through the OCC/facade layer for payment operations.
- **sampledatamcp**: Provides sample data. Likely includes payment mode ImpEx, delivery mode setup, and test order data with payment info.

## OOTB Payment Modules (Present but NOT in localextensions.xml)

| Module Directory | Status | Description |
|------------------|--------|-------------|
| `open-payment-framework` | Not loaded | OPF — pluggable PSP integration framework |
| `sap-digital-payments` | Not loaded | SAP Digital Payments connector |
| `china-accelerator-payment` | Not loaded | China-specific payment handling |
| `china-accelerator-alipay-psp` | Not loaded | Alipay PSP integration |

**Key observation:** No dedicated payment gateway module is loaded. The system uses the base Commerce payment infrastructure (mock/manual payment), not OPF or SAP Digital Payments.

## Summary

The payment stack is built on the standard Commerce foundation: `basecommerce` → `commerceservices` → `commercefacades` → `commercewebservices` → `coremcp`. Order fulfillment including payment capture runs through `yacceleratorcore` + `yacceleratorfulfilmentprocess`. No external payment gateway integration is loaded — the system operates with Commerce's built-in mock payment mode, which is typical for a demo/MCP server setup. The `coremcp` extension exposes payment via MCP tools, and `sampledatamcp` likely seeds payment modes and test data.
