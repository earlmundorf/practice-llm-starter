# ADR 0003: Wire-shape corrections from the official UCP schemas + reference-client acceptance

- **Status:** accepted (2026-07-23, post-Phase-7 iteration)
- **Context refs:** ADR 0001 (provisional-shape caveat), ADR 0002 (REST path),
  design R6/R8/R12

## Context

Phases 1–7 designed all UCP wire shapes from the task runbook because no clone
of the pinned spec was available; ADR 0001 flagged them **provisional**. The
official repos have now been cloned (gitignored, under
`working-docs/ucp-client/`) and used as the oracle:

| Repo | Pin |
|------|-----|
| `Universal-Commerce-Protocol/ucp` (spec + JSON schemas) | `f9bf815` |
| `Universal-Commerce-Protocol/python-sdk` (pydantic request/response models) | `c1ffd1b` |
| `Universal-Commerce-Protocol/samples` (reference server + happy-path client) | `f59d963` |

The reference *server* (`samples/rest/python/server`) was run locally and its
live output captured as ground truth where prose and schema were ambiguous.

## Decision — corrections applied

All corrections stayed within DTOs/marshalling + tests + harness assertions
(the blast radius ADR 0001 predicted), plus the fulfillment negotiation logic
in the checkout service:

1. **Discovery profile**: `capabilities`, `services`, and `payment_handlers`
   are **reverse-DNS-keyed registries** (objects mapping name → list of
   dated version entries) nested **inside the top-level `ucp` object** — not
   the flat top-level arrays we served before. The mock handler is registered
   under `com.thinkshop.mock_card` with handler id `thinkshop_mock_card`.
   A `dev.ucp.shopping.fulfillment` capability entry (`extends:
   dev.ucp.shopping.checkout`) is advertised now that the negotiation flow
   exists.
2. **Totals** (per `total.json` and sample-server output): discount entries
   carry a **negative** amount (hybris reports magnitudes — sign flipped in
   the marshaller); delivery cost uses the well-known type **`fulfillment`**
   (was `shipping`); the `total` entry is emitted **last** because clients
   read `totals[-1]` as the running total; line items carry a
   `subtotal` + `total` breakdown.
3. **Checkout envelope**: `links` is required (emitted empty, like the sample
   server); `payment` is echoed on every response because the reference
   client feeds `response.payment` back into its next request.
4. **Fulfillment negotiation** (the sample flow, steps 4–6 of the happy
   path): client PUTs `fulfillment.methods[{id, type, line_item_ids}]` →
   server offers `methods[].destinations[]` from the customer's address
   book → client PUTs `selected_destination_id` → server offers
   `methods[].groups[].options[]` (the supported delivery modes, with
   minor-unit `totals`) → client PUTs `groups[].selected_option_id` → server
   applies the delivery mode. The pre-existing direct
   `fulfillment.destination` shape is still accepted (both e2e transports
   assert it), preserving R12.
5. **`order.permalink_url`** is required in the completion's order block
   (the reference client dereferences it); built with `coremcp`'s
   `DeepLinkBuilder` pointing at the storefront order page. Completion
   responses stored before the field existed replay without it.
6. **Update payload id tolerance**: the binding text says the checkout
   payload must not contain an id, but the official client sends
   `id == <top-level id>` on every PUT. An id **matching** the addressed
   resource is now accepted; a mismatched id is still rejected.

## Acceptance

`./scripts/run-ucp-reference-client.sh` runs Google's out-of-the-box
happy-path client with exactly three documented constant substitutions
(two demo SKUs + the payment-handler id) through
`scripts/ucp-local-proxy.py` (the local stand-in for the production edge
rewrite (R6) + agent-gateway auth (R8)). Result on 2026-07-23:
**"Happy Path completed successfully."** — full discovery → checkout →
fulfillment negotiation → mock payment, order `00010016` placed for
john.doe. Regression: 23 unit-test suites green; `ucp-e2e.py` 192 (rest) /
223 (mcp) pass, 0 fail; `smoke-test.sh` 25 pass.

## Consequences

- The `ucp-schema validate` SKIPs in the harness can now be replaced by
  validation against the cloned schemas (follow-up).
- The official `conformance` suite remains the outstanding external check.
- If the pinned spec version advances, re-capture the sample server's output
  before adjusting shapes — prose docs lag the schemas.
