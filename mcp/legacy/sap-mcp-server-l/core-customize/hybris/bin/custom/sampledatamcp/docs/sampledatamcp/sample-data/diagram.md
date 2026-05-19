# Sample Data — Diagrams

## Data Model

Shows the relationships between sample data entities. Solid arrows indicate ownership or direct reference. Dashed arrows indicate catalog-versioned links.

```mermaid
%%{ init: { 'theme': 'neutral' } }%%
graph TD
    subgraph "Catalog (Staged + Online)"
        Product["Product<br/>(10 products)"]
        PriceRow["PriceRow<br/>(per catalog version)"]
        TaxRow["TaxRow<br/>(us-sales-tax-full)"]
    end

    StockLevel["StockLevel<br/>(per warehouse, global)"]
    Warehouse["Warehouse<br/>(electronics-warehouse)"]

    subgraph "Customers"
        Customer["Customer<br/>(3 users)"]
        Address["Address<br/>(1 per customer)"]
    end

    subgraph "Orders"
        Order["Order<br/>(3 orders)"]
        OrderEntry["OrderEntry<br/>(8 entries total)"]
    end

    BaseStore["BaseStore<br/>(electronics)"]
    DeliveryMode["ZoneDeliveryMode<br/>(standard)"]
    PaymentMode["StandardPaymentMode<br/>(advance)"]

    Product --> PriceRow
    Product -.-> StockLevel
    Product --> TaxRow
    StockLevel --> Warehouse

    Customer --> Address
    Customer --> Order
    Order --> OrderEntry
    OrderEntry --> Product

    Order --> DeliveryMode
    Order --> PaymentMode
    Order --> BaseStore
    Order -.-> Address

    style Product fill:#e1f5fe
    style Customer fill:#e8f5e9
    style Order fill:#fff3e0
```

## Import Sequence

The ImpEx file processes top-to-bottom. Later blocks depend on earlier ones.

```mermaid
%%{ init: { 'theme': 'neutral' } }%%
graph TD
    P1["Products (Staged)"] --> P2["Products (Online)"]
    P2 --> P3["PriceRows (Staged)"]
    P3 --> P4["PriceRows (Online)"]
    P4 --> P5["StockLevels"]
    P5 --> P6["Link Stock → Products (Staged)"]
    P6 --> P7["Link Stock → Products (Online)"]
    P7 --> C1["Customers"]
    C1 --> C2["Addresses"]
    C2 --> C3["Set Default Addresses"]
    C3 --> O1["Orders + Address Assignment"]
    O1 --> O2["Order Entries"]
    O2 --> O3["Calculate Orders"]
    O3 --> D1["Free Express Tier ($1,000+)"]

    style P1 fill:#e1f5fe
    style P2 fill:#e1f5fe
    style C1 fill:#e8f5e9
    style O1 fill:#fff3e0
    style O3 fill:#fff3e0
```

## Order Composition

Detail of what each order contains and its total before calculation.

| Order | Customer | Entry | Product | Qty | Line Total |
|---|---|---|---|---|---|
| THINK-0001 | John Doe | 0 | Laptop Pro 15 | 1 | $1,299.99 |
| THINK-0001 | John Doe | 1 | Wireless Headphones | 2 | $399.98 |
| THINK-0002 | Jane Smith | 0 | Smartphone X | 1 | $799.99 |
| THINK-0002 | Jane Smith | 1 | Tablet Air | 1 | $649.99 |
| THINK-0002 | Jane Smith | 2 | Smart Watch Pro | 1 | $349.99 |
| THINK-0003 | John Doe | 0 | Mechanical Keyboard | 1 | $149.99 |
| THINK-0003 | John Doe | 1 | Wireless Gaming Mouse | 1 | $79.99 |
| THINK-0003 | John Doe | 2 | 4K Monitor 27" | 1 | $499.99 |
