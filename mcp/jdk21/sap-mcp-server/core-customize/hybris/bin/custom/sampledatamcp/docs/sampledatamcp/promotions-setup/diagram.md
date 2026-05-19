# Promotions Setup — Diagrams

## Rule Structure

Each promotion rule has conditions (when to fire) and actions (what to do). Coupons add a gating condition that requires the customer to enter a code.

```mermaid
%%{ init: { 'theme': 'neutral' } }%%
graph TD
    subgraph "Automatic Rules"
        R1["free_shipping_1000<br/>Priority: 100"]
        R1C["y_cart_total >= $1,000"]
        R1A["y_change_delivery_mode<br/>→ thinkshop-free-delivery"]
        R1 --> R1C --> R1A

        R3["bogo_mouse<br/>Priority: 75"]
        R3C["y_qualifying_products<br/>WIRELESS_GAMING_MOUSE >= 2"]
        R3A["y_order_entry_percentage_discount<br/>50%"]
        R3 --> R3C --> R3A

        R4["headphones_10pct<br/>Priority: 60"]
        R4C["y_qualifying_products<br/>WIRELESS_HEADPHONES >= 1"]
        R4A["y_order_entry_percentage_discount<br/>10%"]
        R4 --> R4C --> R4A
    end

    subgraph "Coupon-Gated Rules"
        C1["LAPTOP10 Coupon"]
        R2["laptop_10pct_coupon<br/>Priority: 50"]
        R2C1["y_qualifying_coupons<br/>LAPTOP10"]
        R2C2["y_qualifying_products<br/>LAPTOP_PRO_15 >= 1"]
        R2A["y_order_entry_percentage_discount<br/>10%"]
        C1 -.-> R2C1
        R2 --> R2C1
        R2 --> R2C2
        R2C1 --> R2A
        R2C2 --> R2A

        C2["SPEAKER5 Coupon"]
        R5["speaker_5pct_coupon<br/>Priority: 40"]
        R5C1["y_qualifying_coupons<br/>SPEAKER5"]
        R5C2["y_qualifying_products<br/>BLUETOOTH_SPEAKER >= 1"]
        R5A["y_order_entry_percentage_discount<br/>5%"]
        C2 -.-> R5C1
        R5 --> R5C1
        R5 --> R5C2
        R5C1 --> R5A
        R5C2 --> R5A
    end

    style R1 fill:#e1f5fe
    style R3 fill:#e1f5fe
    style R4 fill:#e1f5fe
    style R2 fill:#fff3e0
    style R5 fill:#fff3e0
    style C1 fill:#f3e5f5
    style C2 fill:#f3e5f5
```

## Publish Flow

The script creates rules in the database, then compiles and deploys them to the Drools engine so they are evaluated during cart calculation.

```mermaid
%%{ init: { 'theme': 'neutral' } }%%
graph LR
    subgraph "Groovy Script"
        S1["Create/Update Rules"] --> S2["Create/Update Coupons"]
        S2 --> S3["modelService.save()"]
    end

    subgraph "Publish"
        S3 --> P1["Query DroolsKIEModule<br/>(promotions-module)"]
        P1 --> P2["Query all PromotionSourceRules"]
        P2 --> P3["ruleMaintenanceService<br/>.compileAndPublishRules()"]
        P3 --> P4["Drools Engine<br/>(rules active)"]
    end

    subgraph "Runtime"
        P4 --> RT1["Cart Calculation"]
        RT1 --> RT2["Rules Evaluated"]
        RT2 --> RT3["Discounts Applied"]
    end

    style S3 fill:#e8f5e9
    style P4 fill:#e1f5fe
    style RT3 fill:#fff3e0
```

## All Rules Summary

| # | Code | Priority | Trigger | Condition | Effect | Window |
|---|---|---|---|---|---|---|
| 1 | `free_shipping_1000` | 100 | Automatic | Cart total >= $1,000 | Free delivery (mode swap) | 90 days |
| 2 | `laptop_10pct_coupon` | 50 | Coupon LAPTOP10 | Laptop Pro 15 in cart | 10% off line item | 90 days |
| 3 | `bogo_mouse` | 75 | Automatic | 2+ Gaming Mouse in cart | 50% off line item | 90 days |
| 4 | `headphones_10pct` | 60 | Automatic | Wireless Headphones in cart | 10% off line item | 90 days |
| 5 | `speaker_5pct_coupon` | 40 | Coupon SPEAKER5 | Bluetooth Speaker in cart | 5% off line item | 30 days |
