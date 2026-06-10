# 0003 — Define promotion rules in idempotent Groovy, not ImpEx

**Status:** accepted

## Context

The demo needs five promotion rules exercising distinct engine patterns
(cart-threshold delivery action, coupon-gated discounts with redemption caps, a
container-based BOGO). `PromotionSourceRule` rows authored via raw ImpEx are
brittle: the conditions/actions are large JSON blobs in columns, references must
be catalog-qualified, and a failed partial import leaves the rule module in a
half-published state.

## Decision

Define rules in `setup-promotions.groovy` using a get-or-create pattern over
`flexibleSearchService`/`modelService`, run via
`./scripts/setup-promotions.sh`, with publication to the Drools runtime as an
explicit second step (`publish-promotions.groovy`).

## Consequences

- Re-runnable without duplicates; readable diffs; rule JSON is built
  programmatically instead of hand-escaped in ImpEx columns.
- The trade-off: promotions are NOT loaded by the `projectdata-*.impex`
  convention — they require the documented manual step after initialization
  (see README and getting-started). Acceptable for a demo; a production project
  would wire this into a SystemSetup hook or CI step.
