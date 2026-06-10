# MCP Tool Definitions

Complete tool definitions as returned by `tools/list`. Each entry shows the
exact JSON the server returns, plus notes on the underlying commerce facade
method.

---

## product_search

Search the product catalog by keyword with optional pagination.

**Delegates to:** `ProductSearchFacade.textSearch(query, searchState)`

```json
{
  "name": "product_search",
  "description": "Search the ThinkShop catalog by keyword, with optional category filter and pagination. [...] Known category codes — Electronics: 'computing' (laptops, monitors), 'mobile' (smartphones, tablets, smartwatches), 'audio' (headphones, speakers), 'accessories' (keyboards, mice, webcams). Merch: 'swag', 'swag-apparel', 'swag-drinkware', 'swag-accessories'. [...] Some products may be out of stock (stockLevelStatus 'outOfStock') — say so honestly and suggest an in-stock alternative.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "Search keyword or phrase"
      },
      "categoryCode": {
        "type": "string",
        "description": "Optional category code to filter results"
      },
      "currentPage": {
        "type": "integer",
        "description": "Page number (0-based)",
        "default": 0
      },
      "pageSize": {
        "type": "integer",
        "description": "Number of results per page (max 100). Default is small to keep the conversation light.",
        "default": 5
      },
      "sort": {
        "type": "string",
        "description": "Sort code (e.g., 'relevance', 'name-asc', 'price-asc')"
      }
    },
    "required": ["query"]
  }
}
```

The full agent-facing description (category guidance, keyword-vs-category usage,
out-of-stock handling) lives in `ProductSearchToolHandler.getDescription()` —
the source string is authoritative; the JSON above abridges it with `[...]`.

**Example response content:**

```json
{
  "products": [
    {
      "code": "1934793",
      "name": "PowerShot A480",
      "price": { "value": 99.85, "currencyIso": "USD", "formattedValue": "$99.85" },
      "averageRating": 4.2,
      "stock": { "stockLevelStatus": "inStock" }
    }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalResults": 23,
    "totalPages": 2
  },
  "sorts": [
    { "code": "relevance", "name": "Relevance", "selected": true }
  ]
}
```

---

## product_get

Get detailed information about a specific product.

**Delegates to:** `ProductFacade.getProductForCodeAndOptions(code, options)`

```json
{
  "name": "product_get",
  "description": "Get detailed product information by product code. Returns full product data including description, price, stock, images, categories, and reviews.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "code": {
        "type": "string",
        "description": "Product code (e.g., '1934793')"
      },
      "options": {
        "type": "array",
        "items": {
          "type": "string",
          "enum": ["BASIC", "PRICE", "STOCK", "DESCRIPTION", "GALLERY", "CATEGORIES", "REVIEW", "CLASSIFICATION", "REFERENCES", "PROMOTIONS"]
        },
        "description": "Data options to include. Defaults to all if omitted.",
        "default": ["BASIC", "PRICE", "STOCK", "DESCRIPTION", "CATEGORIES"]
      }
    },
    "required": ["code"]
  }
}
```

**Example response content:**

```json
{
  "code": "1934793",
  "name": "PowerShot A480",
  "description": "10.0 megapixel, 3.3x optical zoom...",
  "price": { "value": 99.85, "currencyIso": "USD", "formattedValue": "$99.85" },
  "stock": { "stockLevel": 583, "stockLevelStatus": "inStock" },
  "categories": [
    { "code": "576", "name": "Digital Compacts" }
  ],
  "images": [
    { "format": "product", "url": "/medias/1934793-product.jpg" }
  ]
}
```

---

## order_get

Get details of a specific order by order code.

**Delegates to:** `OrderFacade.getOrderDetailsForCode(code)`

```json
{
  "name": "order_get",
  "description": "Get order details by order code. Returns order status, line items, totals, delivery address, and payment information. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "code": {
        "type": "string",
        "description": "Order code (e.g., '00001001')"
      }
    },
    "required": ["code"]
  }
}
```

**Example response content:**

```json
{
  "code": "00001001",
  "status": "COMPLETED",
  "statusDisplay": "completed",
  "created": "2025-12-15T10:30:00Z",
  "totalPrice": { "value": 199.70, "currencyIso": "USD", "formattedValue": "$199.70" },
  "entries": [
    {
      "entryNumber": 0,
      "product": { "code": "1934793", "name": "PowerShot A480" },
      "quantity": 2,
      "totalPrice": { "value": 199.70 }
    }
  ],
  "deliveryAddress": {
    "firstName": "John",
    "lastName": "Doe",
    "line1": "123 Main St",
    "town": "New York",
    "postalCode": "10001"
  }
}
```

---

## order_history

Get paginated order history for the current customer.

**Delegates to:** `OrderFacade.getPagedOrderHistoryForStatuses(searchPageData, statuses)`

```json
{
  "name": "order_history",
  "description": "Get paginated order history for the authenticated customer. Optionally filter by order status. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "statuses": {
        "type": "array",
        "items": {
          "type": "string",
          "enum": ["CREATED", "CHECKED_VALID", "PAYMENT_AUTHORIZED", "PAYMENT_CAPTURED", "READY", "COMPLETED", "CANCELLED"]
        },
        "description": "Filter by order statuses. Returns all statuses if omitted."
      },
      "currentPage": {
        "type": "integer",
        "description": "Page number (0-based)",
        "default": 0
      },
      "pageSize": {
        "type": "integer",
        "description": "Number of orders per page",
        "default": 20
      },
      "sort": {
        "type": "string",
        "description": "Sort field (e.g., 'byDate', 'byOrderNumber')"
      }
    },
    "required": []
  }
}
```

**Example response content:**

```json
{
  "orders": [
    {
      "code": "00001001",
      "status": "COMPLETED",
      "statusDisplay": "completed",
      "placed": "2025-12-15T10:30:00Z",
      "total": { "value": 199.70, "formattedValue": "$199.70" }
    },
    {
      "code": "00001002",
      "status": "CREATED",
      "statusDisplay": "created",
      "placed": "2025-12-20T14:15:00Z",
      "total": { "value": 49.99, "formattedValue": "$49.99" }
    }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalResults": 2,
    "totalPages": 1
  }
}
```

---

## cart_get

Get the current session cart for the authenticated customer.

**Delegates to:** `CartFacade.getSessionCart()`

```json
{
  "name": "cart_get",
  "description": "Get the current session cart including all entries, totals, and applied promotions. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {},
    "required": []
  }
}
```

**Example response content:**

```json
{
  "code": "00001003",
  "totalItems": 2,
  "totalPrice": { "value": 299.55, "currencyIso": "USD", "formattedValue": "$299.55" },
  "totalUnitCount": 3,
  "entries": [
    {
      "entryNumber": 0,
      "product": { "code": "1934793", "name": "PowerShot A480" },
      "quantity": 2,
      "totalPrice": { "value": 199.70 }
    },
    {
      "entryNumber": 1,
      "product": { "code": "300938", "name": "Photosmart E702" },
      "quantity": 1,
      "totalPrice": { "value": 99.85 }
    }
  ]
}
```

---

## cart_add_product

Add a product to the current cart.

**Delegates to:** `CartFacade.addToCart(code, quantity)`

```json
{
  "name": "cart_add_product",
  "description": "Add a product to the current session cart. Creates a new cart entry or increases quantity of an existing entry. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "productCode": {
        "type": "string",
        "description": "Product code to add to cart"
      },
      "quantity": {
        "type": "integer",
        "description": "Number of units to add",
        "default": 1,
        "minimum": 1
      }
    },
    "required": ["productCode"]
  }
}
```

**Example response content:**

```json
{
  "statusCode": "success",
  "quantityAdded": 1,
  "entry": {
    "entryNumber": 0,
    "product": { "code": "1934793", "name": "PowerShot A480" },
    "quantity": 1,
    "totalPrice": { "value": 99.85, "formattedValue": "$99.85" }
  }
}
```

---

## customer_get

Get the profile of the currently authenticated customer.

**Delegates to:** `CustomerFacade.getCurrentCustomer()`

```json
{
  "name": "customer_get",
  "description": "Get the profile of the currently authenticated customer, including name, email, and default addresses. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {},
    "required": []
  }
}
```

**Example response content:**

```json
{
  "uid": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "name": "John Doe",
  "titleCode": "mr",
  "currency": { "isocode": "USD" },
  "language": { "isocode": "en" },
  "defaultAddress": {
    "line1": "123 Main St",
    "town": "New York",
    "postalCode": "10001",
    "country": { "isocode": "US" }
  }
}
```

---

## customer_lookup

Look up a customer by their UID (email). Requires trusted client or
admin privileges.

**Delegates to:** `CustomerFacade.getUserForUID(uid)`

```json
{
  "name": "customer_lookup",
  "description": "Look up a customer by their UID (email address). Returns customer profile data. Requires ROLE_TRUSTED_CLIENT or admin authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "uid": {
        "type": "string",
        "description": "Customer UID (typically email address)"
      }
    },
    "required": ["uid"]
  }
}
```

**Example response content:**

```json
{
  "uid": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "name": "John Doe"
}
```

---

## checkout_set_delivery_address

Set the delivery address on the current cart. Uses an existing address
from the customer's address book, or creates a new one.

**Delegates to:** `CheckoutFacade.setDeliveryAddress(addressData)`

```json
{
  "name": "checkout_set_delivery_address",
  "description": "Set the delivery address on the current cart. Provide either an existing address ID from the customer's address book, or full address fields to create a new one. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "addressId": {
        "type": "string",
        "description": "ID of an existing address from the customer's address book. If provided, other address fields are ignored."
      },
      "firstName": {
        "type": "string",
        "description": "First name (required if addressId not provided)"
      },
      "lastName": {
        "type": "string",
        "description": "Last name (required if addressId not provided)"
      },
      "line1": {
        "type": "string",
        "description": "Street address line 1 (required if addressId not provided)"
      },
      "line2": {
        "type": "string",
        "description": "Street address line 2"
      },
      "town": {
        "type": "string",
        "description": "City/town (required if addressId not provided)"
      },
      "postalCode": {
        "type": "string",
        "description": "Postal/ZIP code (required if addressId not provided)"
      },
      "country": {
        "type": "string",
        "description": "Country ISO code, e.g., 'US' (required if addressId not provided)"
      },
      "region": {
        "type": "string",
        "description": "Region/state ISO code, e.g., 'US-NY'"
      }
    },
    "required": []
  }
}
```

**Example response content:**

```json
{
  "success": true,
  "deliveryAddress": {
    "id": "8796093054975",
    "firstName": "John",
    "lastName": "Doe",
    "line1": "123 Main St",
    "town": "New York",
    "postalCode": "10001",
    "country": { "isocode": "US" }
  }
}
```

---

## checkout_set_delivery_mode

Set the delivery mode (shipping method) on the current cart.

**Delegates to:** `CheckoutFacade.setDeliveryMode(deliveryModeCode)`

```json
{
  "name": "checkout_set_delivery_mode",
  "description": "Set the delivery mode (shipping method) on the current cart. Call with no arguments to list available delivery modes. Requires a delivery address to be set first. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "deliveryModeCode": {
        "type": "string",
        "description": "Delivery mode code (e.g., 'standard-gross', 'premium-gross'). Omit to list available modes."
      }
    },
    "required": []
  }
}
```

**Example response content (listing modes):**

```json
{
  "deliveryModes": [
    {
      "code": "standard-gross",
      "name": "Standard Delivery",
      "deliveryCost": { "value": 4.99, "formattedValue": "$4.99" }
    },
    {
      "code": "premium-gross",
      "name": "Premium Delivery",
      "deliveryCost": { "value": 14.99, "formattedValue": "$14.99" }
    }
  ]
}
```

**Example response content (setting mode):**

```json
{
  "success": true,
  "deliveryMode": {
    "code": "standard-gross",
    "name": "Standard Delivery",
    "deliveryCost": { "value": 4.99, "formattedValue": "$4.99" }
  }
}
```

---

## checkout_set_payment

Set payment details on the current cart. Uses mock payment for
development/testing — no real payment processing occurs.

**Delegates to:** `CheckoutFacade.createPaymentSubscription(paymentDetailsData)` / `CheckoutFacade.setPaymentDetails(paymentDetailsId)`

```json
{
  "name": "checkout_set_payment",
  "description": "Set payment details on the current cart. For development/testing, use mock payment with any card number. Provide either an existing payment ID or new card details. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "paymentId": {
        "type": "string",
        "description": "ID of existing saved payment details. If provided, other fields are ignored."
      },
      "cardNumber": {
        "type": "string",
        "description": "Card number. Use '4111111111111111' for mock/test payments.",
        "default": "4111111111111111"
      },
      "cardType": {
        "type": "string",
        "description": "Card type code",
        "enum": ["visa", "master", "amex"],
        "default": "visa"
      },
      "expiryMonth": {
        "type": "string",
        "description": "Expiry month (01-12)",
        "default": "12"
      },
      "expiryYear": {
        "type": "string",
        "description": "Expiry year (YYYY)",
        "default": "2028"
      },
      "nameOnCard": {
        "type": "string",
        "description": "Cardholder name. Defaults to customer's name if omitted."
      },
      "billingAddress": {
        "type": "object",
        "description": "Billing address. Defaults to delivery address if omitted.",
        "properties": {
          "firstName": { "type": "string" },
          "lastName": { "type": "string" },
          "line1": { "type": "string" },
          "town": { "type": "string" },
          "postalCode": { "type": "string" },
          "country": { "type": "string" }
        }
      }
    },
    "required": []
  }
}
```

**Example response content:**

```json
{
  "success": true,
  "paymentDetails": {
    "id": "8796093087743",
    "cardType": { "code": "visa", "name": "Visa" },
    "cardNumber": "************1111",
    "expiryMonth": "12",
    "expiryYear": "2028",
    "billingAddress": {
      "firstName": "John",
      "lastName": "Doe",
      "line1": "123 Main St",
      "town": "New York",
      "postalCode": "10001",
      "country": { "isocode": "US" }
    }
  }
}
```

---

## order_place

Place the order from the current cart. Requires delivery address,
delivery mode, and payment details to be set.

**Delegates to:** `CheckoutFacade.placeOrder()`

```json
{
  "name": "order_place",
  "description": "Place an order from the current cart. The cart must have a delivery address, delivery mode, and payment details set. On success, the cart is consumed and an order is created. Requires customer authentication.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "securityCode": {
        "type": "string",
        "description": "Card security code (CVV). Use '123' for mock/test payments.",
        "default": "123"
      }
    },
    "required": []
  }
}
```

**Example response content:**

```json
{
  "code": "00001005",
  "status": "CREATED",
  "statusDisplay": "created",
  "created": "2026-03-03T14:30:00Z",
  "totalPrice": { "value": 204.69, "currencyIso": "USD", "formattedValue": "$204.69" },
  "totalPriceWithTax": { "value": 204.69, "formattedValue": "$204.69" },
  "deliveryCost": { "value": 4.99, "formattedValue": "$4.99" },
  "entries": [
    {
      "entryNumber": 0,
      "product": { "code": "1934793", "name": "PowerShot A480" },
      "quantity": 2,
      "totalPrice": { "value": 199.70 }
    }
  ],
  "deliveryAddress": {
    "firstName": "John",
    "lastName": "Doe",
    "line1": "123 Main St",
    "town": "New York",
    "postalCode": "10001"
  },
  "paymentInfo": {
    "cardType": { "code": "visa" },
    "cardNumber": "************1111"
  }
}
```

---

## Complete Purchase Flow

The full browse-to-buy sequence using all relevant tools:

```
  1. product_search(query: "camera")             # Browse catalog
  2. product_get(code: "1934793")                 # View details
  3. cart_add_product(productCode: "1934793", quantity: 2)  # Add to cart
  4. cart_get()                                   # Review cart
  5. checkout_set_delivery_address(               # Set shipping
       firstName: "John", lastName: "Doe",
       line1: "123 Main St", town: "New York",
       postalCode: "10001", country: "US")
  6. checkout_set_delivery_mode(                  # Pick shipping speed
       deliveryModeCode: "standard-gross")
  7. checkout_set_payment()                       # Mock payment (defaults)
  8. order_place()                                # Place order
  9. order_get(code: "00001005")                  # Confirm order
```

---

## Tool Response Format

All tools return results wrapped in MCP's content array format:

```json
{
  "content": [
    {
      "type": "text",
      "text": "<JSON-serialized facade response>"
    }
  ]
}
```

On error, the response includes `isError: true`:

```json
{
  "content": [
    {
      "type": "text",
      "text": "Product not found: INVALID_CODE"
    }
  ],
  "isError": true
}
```
