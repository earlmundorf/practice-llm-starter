# OCC REST Endpoint Mapping

Maps every UI operation to its SAP Commerce OCC REST endpoint. All endpoints are prefixed with `/occ/v2/electronics` and require `Authorization: Bearer {token}` (except product search which works anonymously).

## Auth

```
POST /authorizationserver/oauth/token
  grant_type=password
  &client_id=trusted_client
  &client_secret=secret
  &username=user@example.com
  &password=1234

Response: { access_token: "xyz", expires_in: 43200 }
```

## Summary Table

| Operation | Method | OCC Endpoint | Notes |
|-----------|--------|-------------|-------|
| Search products | GET | `/products/search?query={q}:{sort}&currentPage={p}&pageSize=20` | Paginated, Solr-powered |
| Get product | GET | `/products/{code}?fields=FULL` | Full product detail |
| Create cart | POST | `/users/current/carts` | Returns cartCode |
| Get cart | GET | `/users/current/carts/{cartCode}?fields=FULL` | Full cart contents |
| Add to cart | POST | `/users/current/carts/{cartCode}/entries` | `{ product: { code }, quantity }` |
| Update quantity | PATCH | `/users/current/carts/{cartCode}/entries/{entryNumber}` | `{ quantity }` |
| Remove entry | DELETE | `/users/current/carts/{cartCode}/entries/{entryNumber}` | 200 OK, no body |
| Set address | POST | `/users/current/carts/{cartCode}/addresses/delivery` | Address object in body |
| Get delivery modes | GET | `/users/current/carts/{cartCode}/deliverymodes` | After address is set |
| Set delivery mode | PUT | `/users/current/carts/{cartCode}/deliverymode?deliveryModeId={code}` | |
| Set payment | POST | `/users/current/carts/{cartCode}/paymentdetails` | Card + billing address |
| Place order | POST | `/users/current/orders?cartId={cartCode}&fields=FULL` | Returns full order |
| Get orders | GET | `/users/current/orders?fields=FULL&pageSize=20&currentPage={p}` | Paginated |
| Get order detail | GET | `/users/current/orders/{orderCode}?fields=FULL` | Full order |
| Cancel order | POST | `/users/current/orders/{orderCode}/cancellation` | Entry-level |
| Get current user | GET | `/users/current?fields=FULL` | From auth token |

## Key Differences from Typical REST APIs

1. **No userId in URLs** — OCC uses `current` (resolved from auth token)
2. **Cart requires creation** — `POST /users/current/carts` before adding items
3. **Cart entries use `entryNumber`** — Integer index, not product code
4. **Checkout is multi-step** — Address → delivery mode → payment → place order
5. **Prices are objects** — `{ value: 99.85, currencyIso: "USD", formattedValue: "$99.85" }`
6. **Product ID is `code`** — String like `"LAPTOP_PRO_15"`, not numeric
7. **Order cancel is entry-level** — Must specify entries and quantities
8. **Search is always paginated** — 0-based page index, use `pageSize` to control
