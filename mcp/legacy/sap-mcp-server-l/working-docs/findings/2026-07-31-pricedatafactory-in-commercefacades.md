---
date: 2026-07-31
ticket: THINK-142
tier: full
stage: 6-implement
applies_to:
  area: cross-cutting
  ticket_type: feature
kind: project-knowledge
status: unpromoted
promotion_target: repo CLAUDE.md (a short "price formatting" note) — or leave as a findings-only reference
---

## What happened

Building a price-formatting service, the first BUILD failed with `cannot find symbol: class
PriceDataFactory` because the import used `de.hybris.platform.commerceservices.price.PriceDataFactory`.
In this codebase (Commerce 2211), `PriceDataFactory` actually lives in **`commercefacades`** at
`de.hybris.platform.commercefacades.product.PriceDataFactory`, and `commerceservices` is not on
`coremcp`'s compile classpath.

## Context

- Stage 6, Slice 1 of THINK-142 (new `DefaultPromoPricingService` that formats a discounted price).
- Symptom: `[yjavac] ... DefaultPromoPricingService.java:9: error: cannot find symbol / class PriceDataFactory`.
- `coremcp/extensioninfo.xml` requires `commercewebservices, solrfacetsearch, promotionengineservices,
  couponservices` — `commercefacades` types (`ProductData`, `PriceData`, `PriceDataFactory`) resolve;
  `commerceservices` types do not.
- Fix: import `de.hybris.platform.commercefacades.product.PriceDataFactory` (no extensioninfo change).
  The Spring bean id is `priceDataFactory`; usable signature `create(PriceDataType, BigDecimal, String isoCode)`.

## The fix / the knowledge

For any `coremcp` code that formats prices: use `de.hybris.platform.commercefacades.product.PriceDataFactory`
(bean `priceDataFactory`), not the `commerceservices.price` package. Confirm a platform class's owning
module with `find core-customize/hybris/bin -name "<Class>.java"` before importing — the module layout
under `bin/modules/*` doesn't match package intuition.

## Why this generalizes

Price/currency formatting recurs across product, cart, checkout, and promotion tickets in this repo,
and the wrong-package import is an easy, build-only-caught mistake. Knowing the exact FQN and the
"grep the module tree first" habit saves a build cycle per occurrence.

## Promotion suggestion

Optional one-liner in repo CLAUDE.md near the price/OCC notes: "Price formatting uses
`de.hybris.platform.commercefacades.product.PriceDataFactory` (bean `priceDataFactory`)." Low urgency —
fine to keep as a findings-only reference until a second price-formatting ticket confirms the value.
