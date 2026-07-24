# UCP checkout — context

## What this flow does

The full UCP checkout-session lifecycle over hybris carts:

```
create_checkout → update_checkout (items / buyer / destination) →
complete_checkout (mock payment, idempotent) | cancel_checkout
```

Each checkout is addressed by an **opaque id** (`ucp_chk_…`) persisted on a
`UcpCheckoutSessionEntry` item (typecode 14003, design R5) that maps the id to
a hybris `cartCode` plus protocol-side state a bare cart cannot carry: the
derived status, the stored idempotency key, the replayable completion
response, the placed order code, and the buyer block.

## When it's used

Every checkout tool call resolves the id → loads the cart into the hybris
thread-local session (`CartLoaderStrategy`, the coremcp bridge pattern) →
performs facade calls → persists cart code + derived status back → marshals
`CartData` to the UCP `checkout` object (design S2 bracket).

## Key decisions

- **Status is derived, never trusted from the client** (design S5):
  `ready_for_complete` = ≥1 line item + delivery address + delivery mode;
  `completed`/`canceled` are terminal (guards reject further mutations);
  `complete_in_progress` covers a concurrent/crashed completion.
- **`line_items` on update is declarative** — the desired end state of the
  cart (absent items removed, differing quantities updated, new items added).
- **Destinations go through `UserFacade.addAddress`** (OCC-conformant), then
  `CheckoutFacade.setDeliveryAddress`; the cheapest supported delivery mode is
  auto-selected when none was requested. Drools promotions fire during
  recalculation — discounts and free-shipping mode swaps become visible in
  `totals`/`fulfillment`.
- **Mock payment behind one declared handler** (design R9):
  `thinkshop_mock_card` accepts any credential token and runs the existing
  mock path — `createPaymentSubscription` (default Visa) →
  `authorizePayment("123")` (return value ignored, exactly as coremcp's
  `order_place` does) → `placeOrder`. Credentials are never read, logged, or
  stored.
- **Idempotency** (runbook §5.2): `complete_checkout`/`cancel_checkout`
  require `meta["idempotency-key"]`. A replay with the same key returns the
  stored response verbatim — never a second `placeOrder`. The completion
  response is recorded in the same `modelService.save` as the status/order
  code ("atomic with order placement").
- **`get_checkout` on a completed checkout replays the stored response**
  (`placeOrder` consumes the backing cart, so the terminal state IS the
  stored payload, including the embedded minimal `order` block).
- **TTL cleanup**: lazy eviction on access + a 30-min CronJob
  (`UcpCheckoutSessionCleanupJob`) for abandoned entries — terminal
  completed/canceled entries persist protocol state until swept.
