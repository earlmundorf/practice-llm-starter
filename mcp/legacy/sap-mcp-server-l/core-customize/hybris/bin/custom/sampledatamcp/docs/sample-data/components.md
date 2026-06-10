# Sample Data — Components

Sample data loads via the `projectdata-*` convention (initialize only). Files carry
numeric prefixes because the platform imports them alphabetically and the order
matters (products before media before category assignments — see ADR 0006):

| File | Loads |
|---|---|
| `projectdata-10-products.impex` | 10 electronics products, prices, stock, customers, orders (this document) |
| `projectdata-20-swag.impex` | 10 merch products + swag category tree |
| `projectdata-30-product-media.impex` | Product images (both catalog versions) |
| `projectdata-40-categories.impex` | Electronics categories (computing / mobile / audio / accessories) + assignments |
| `projectdata-50-knowledge.impex`, `-55-knowledge-extras.impex` | Knowledge base entries |
| `projectdata-60-edge-cases.impex` | `KEYBOARD_LTD_ALUMINUM` — forced out-of-stock, no image (honest-demo paths) |

**This document covers:** `resources/impex/projectdata-10-products.impex`

## Products

10 electronics products imported into both Staged and Online catalog versions. All use unit `pieces`, approval status `approved`, and tax group `us-sales-tax-full`.

| Code | Name | Price (USD) | Stock |
|---|---|---|---|
| `LAPTOP_PRO_15` | Laptop Pro 15 | $1,299.99 | 25 |
| `SMARTPHONE_X` | Smartphone X | $799.99 | 50 |
| `WIRELESS_HEADPHONES` | Wireless Headphones | $199.99 | 100 |
| `TABLET_AIR` | Tablet Air | $649.99 | 40 |
| `SMART_WATCH_PRO` | Smart Watch Pro | $349.99 | 75 |
| `MECHANICAL_KEYBOARD` | Mechanical Keyboard | $149.99 | 60 |
| `WIRELESS_GAMING_MOUSE` | Wireless Gaming Mouse | $79.99 | 80 |
| `MONITOR_4K_27` | 4K Monitor 27" | $499.99 | 30 |
| `HD_WEBCAM` | HD Webcam | $89.99 | 45 |
| `BLUETOOTH_SPEAKER` | Bluetooth Speaker | $129.99 | 90 |

### Price Rows

`PriceRow` is catalog-versioned (part-of Product). Imported twice — once for Staged, once for Online. All prices are gross (`net=false`), minimum quantity 1.

### Stock Levels

`StockLevel` is global (not catalog-versioned) — created once per product, linked to `electronics-warehouse`. The Product-to-StockLevel relation is catalog-versioned, so a separate `UPDATE Product` with `[mode = append]` links stock to products in both Staged and Online.

## Customers

3 test users, all in `customergroup`, password `1234`, session language `en`, session currency `USD`.

| UID | Name | Address | City |
|---|---|---|---|
| `john.doe@thinkshop.com` | John Doe | 100 Main St, 10001 | New York |
| `jane.smith@thinkshop.com` | Jane Smith | 200 Oak Ave, 90210 | Los Angeles |
| `bob.wilson@thinkshop.com` | Bob Wilson | 300 Pine Rd, 60601 | Chicago |

Each customer has one `Address` set as billing, contact, and shipping. A follow-up `UPDATE Customer` sets the address as both `defaultPaymentAddress` and `defaultShipmentAddress`.

ImpEx pattern: `&userId` and `&addId` reference aliases connect Customers to Addresses without needing PKs.

## Orders

3 orders using `advance` payment mode and `thinkshop-standard` delivery.

| Code | Customer | Date | Status | Entries |
|---|---|---|---|---|
| `THINK-0001` | john.doe | 15 Jan 2026 | COMPLETED | Laptop Pro 15 (x1), Wireless Headphones (x2) |
| `THINK-0002` | jane.smith | 20 Feb 2026 | CREATED | Smartphone X (x1), Tablet Air (x1), Smart Watch Pro (x1) |
| `THINK-0003` | john.doe | 25 Feb 2026 | COMPLETED | Mechanical Keyboard (x1), Gaming Mouse (x1), 4K Monitor (x1) |

### Order Entries

Entries reference Online catalog version products (orders use the live catalog). Each entry has explicit `basePrice` and `totalPrice` values, but final order totals are computed by the platform.

### Order Calculation

After entries are imported, each order is updated with an inline Groovy snippet:
```
"#%   impex.getLastImportedItem().calculate();";
```
This triggers the platform's calculation engine to compute subtotals, tax, and delivery cost.

### Address Assignment

Order delivery and payment addresses are set via inline Groovy immediately after each order INSERT:
```
"#%   impex.getLastImportedItem().setDeliveryAddress(
        impex.getLastImportedItem().getUser().getDefaultDeliveryAddress());";
```
This pulls the customer's default address rather than duplicating address data.

## Free Express Tier

A `ZoneDeliveryModeValue` at the end adds a $0.00 tier for express delivery on orders >= $1,000. The platform picks the tier with the highest minimum the order total meets.
