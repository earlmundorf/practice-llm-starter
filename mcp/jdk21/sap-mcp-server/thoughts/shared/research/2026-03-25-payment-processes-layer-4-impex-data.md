# Layer 4: ImpEx & Data — Payment Processes

**Date:** 2026-03-25

**System:** SAP Commerce 2211.50 (ThinkShop demo store)

---

## Payment Mode ImpEx

### Location: essentialdata-infrastructure.impex (sampledatamcp)

**Payment Modes Defined:**
- **Code:** `advance` (mapped to variable `$paymentMode`)
- **Name:** Advance (localized en)
- **Description:** payment in advance
- **Active:** true
- **Payment Info Type:** AdvancePaymentInfo (OOTB type)

**Payment Mode Value:**
```impex
INSERT_UPDATE StandardPaymentModeValue ; paymentMode(code)[unique = true] ; value ; currency(isocode)[unique = true]
                                       ; advance                          ; 0.00  ; USD
```

**Note:** No surcharge/fee on advance payment (value = 0.00 USD)

**Test Data Variant:** testdata-thinkshop.impex (coremcp) uses identical config with same payment mode.

---

## BaseStore Payment Configuration

### Location: essentialdata-infrastructure.impex (sampledatamcp)

**BaseStore: electronics**

```impex
INSERT_UPDATE BaseStore ; uid[unique = true] ; ... ; paymentProvider ; ...
                        ; electronics        ; ... ; Mockup          ; ...
```

**Key Attributes:**
- **paymentProvider:** Mockup (OOTB mock payment provider — no real PSP integration)
- **Net:** false (prices shown as gross)
- **TaxGroup:** us-taxes (0% effective for display)
- **Warehouses:** electronics-warehouse
- **DefaultCurrency:** USD
- **DefaultLanguage:** en

**Delivery Modes Linked to BaseStore:**
```impex
UPDATE BaseStore ; uid[unique = true] ; deliveryModes(code)
                 ; electronics        ; thinkshop-standard,thinkshop-express
```

**Note:** Free delivery mode (`thinkshop-free-delivery`) is NOT listed for user selection. It exists only for promotion engine rules (via `y_change_delivery_mode` action).

---

## Test Payment Data (Credit Cards, Payment Info)

**No test credit card records found in ImpEx.**

The system uses a **mock payment provider** (`Mockup` in BaseStore config):
- No CreditCardPaymentInfo records are pre-seeded
- MockCommands always return success (per previous layer analysis)
- Payment authorization happens via MockCommerceCheckoutService (no real card validation)
- At checkout time, customers can submit any payment details; they are stored but not validated against a real card provider

**Payment flow for orders:**
1. Checkout sets payment mode (`checkout_set_payment`)
2. Order placed with `paymentMode(code) = advance`
3. Order status transitions through: CREATED → CHECKED_VALID → PAYMENT_AUTHORIZED → PAYMENT_CAPTURED → COMPLETED
4. Mock commands approve all payments without real card processing

---

## Delivery Mode Setup (Related to Checkout Flow)

### Location: essentialdata-infrastructure.impex (sampledatamcp)

**Delivery Modes are ZoneDeliveryMode (OCC requirement):**

```impex
INSERT_UPDATE Zone ; code[unique = true] ; countries(isocode)
                   ; usa                 ; US

INSERT_UPDATE ZoneDeliveryMode ; code[unique = true]        ; name[lang = $language] ; active ; net
                               ; thinkshop-standard         ; Standard Delivery      ; true   ; false
                               ; thinkshop-express          ; Express Delivery       ; true   ; false
                               ; thinkshop-free-delivery    ; Free Delivery          ; true   ; false

INSERT_UPDATE ZoneDeliveryModeValue ; deliveryMode(code)[unique = true] ; zone(code) ; currency(isocode) ; value ; minimum[unique = true]
                                    ; thinkshop-standard                ; usa        ; USD               ; 5.99  ; 0.00
                                    ; thinkshop-express                 ; usa        ; USD               ; 14.99 ; 0.00
                                    ; thinkshop-free-delivery           ; usa        ; USD               ; 0.00  ; 0.00
```

**User-Selectable Modes:**
- thinkshop-standard: $5.99
- thinkshop-express: $14.99

**Promo-Only Mode:**
- thinkshop-free-delivery: $0.00 (applied by promotion engine only)

### Integration with Payment:
Delivery mode is **independent** of payment mode in ImpEx:
- Orders use: `paymentMode(code) = advance` AND `deliveryMode(code) = standard-gross` (in test data)
- Both are applied at order creation, not linked in type definition
- Promotion engine can change delivery mode (via `y_change_delivery_mode` action) but not payment mode

---

## ImpEx Load Order

**Essential Data (loaded during "ant initialize" AND "ant updatesystem"):**

1. **essentialdata-infrastructure.impex** (sorts first alphabetically 'i')
   - Creates: Languages, Currencies, Countries, Units, Catalogs, CatalogVersions
   - Creates: Vendors, Warehouses, Tax Groups, Delivery Modes, Payment Modes
   - Creates: BaseStore (with paymentProvider = Mockup)
   - Creates: PromotionGroup, CMSSite, UserGroups

2. **essentialdata-solr.impex** (sorts second alphabetically 's')
   - Creates: SolrFacetSearchConfig, SolrIndexedTypes, indexed properties, sorts, cron jobs
   - **Depends on:** Currency, Catalog, CatalogVersion from essentialdata-infrastructure

**Project Data (loaded during "ant initialize" ONLY):**

3. **projectdata-sampledatamcp.impex**
   - Creates: 10 Products (Staged + Online versions)
   - Creates: Price Rows, Stock Levels
   - Creates: 3 Customers, 3 Customer Addresses
   - Creates: 3 Orders with order entries
   - **Prerequisite:** essentialdata-infrastructure (catalog, store, delivery modes)

**Note on filename pattern:**
- `essentialdata-*.impex` → loaded during initialize and updatesystem
- `projectdata-*.impex` → loaded during initialize only (sample/project data)
- `testdata-*.impex` → loaded in JUnit tenant only (per coremcp test resources)

---

## Summary

The ThinkShop SAP Commerce system seeds **minimal payment configuration** via ImpEx:
- **Single payment mode:** Advance (no surcharge, OOTB AdvancePaymentInfo type)
- **Payment provider:** Mock (no real PSP; all authorization/capture succeeds)
- **No test credit cards:** System relies on mock payment endpoint; cards are stored but not validated
- **Delivery modes are primary:** Three ZoneDeliveryModes control checkout shipping costs; free delivery is promotion-driven, not payment-driven
- **Load sequence:** essentialdata-infrastructure (payment+delivery modes+basestore) → essentialdata-solr → projectdata-sampledatamcp (products+customers+orders)
- **BaseStore checkout config:** Payment provider hardcoded as `Mockup`; no PSP credentials, gateway URLs, or API keys in ImpEx

---

## File References

- **sampledatamcp ImpEx:**
  - `/core-customize/hybris/bin/custom/sampledatamcp/resources/impex/essentialdata-infrastructure.impex` (payment modes, basestore, delivery modes)
  - `/core-customize/hybris/bin/custom/sampledatamcp/resources/impex/essentialdata-solr.impex` (search config)
  - `/core-customize/hybris/bin/custom/sampledatamcp/resources/impex/projectdata-sampledatamcp.impex` (products, customers, orders)

- **coremcp ImpEx:**
  - `/core-customize/hybris/bin/custom/coremcp/resources/coremcp/test/testdata-thinkshop.impex` (JUnit test data — same payment modes as sampledatamcp)

- **Promotions (Groovy):**
  - `/core-customize/hybris/bin/custom/sampledatamcp/resources/sampledatamcp/promotions/setup-promotions.groovy` (5 promotion rules, including free shipping via delivery mode change)

- **Config:**
  - `/core-customize/hybris/config/local.properties` (no payment-specific settings; paymentProvider is in BaseStore ImpEx)

