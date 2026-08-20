# Product reviews via the MCP `product_get` tool

## Why this flow exists

The ThinkShop assistant answers "what did other customers say about this product?" by returning
real review content (rating, headline, comment, reviewer name) through the MCP `product_get`
tool. Reviews are surfaced read-only (THINK-201).

## How reviews are surfaced

`product_get` (`ProductGetToolHandler`) passes the requested `ProductOption`s — including
`REVIEW` — to the platform `ProductFacade.getProductForCodeAndOptions(code, options)` and
serialises the resulting `ProductData` (its `reviews` list included) to JSON.

`ProductOption.REVIEW` is mapped, in the commercefacades `defaultProductConfiguredPopulator`
option map, to a populator list containing the `productReviewsPopulator` bean. This project
overrides that bean (alias swap) with `McpProductReviewsPopulator`, which:

- reads reviews from the DB via the OOTB `CustomerReviewService.getReviewsForProduct(product)`;
- keeps only reviews that are **approved and not blocked** (what a shopper should see);
- sets each `ReviewData`'s reviewer name to the review **alias**, falling back to the author's
  **name**, then **uid**;
- always sets a list (empty, never null) so a product with no visible reviews serialises as
  `"reviews": []` rather than erroring.

## Read path

Review data is read from the **database** through the service layer. Reviews are **not** indexed
in Solr (`thinkshopIndex` indexes only code/name/summary/description, price, stock, category), so
there is no reindex step for reviews — unlike product/knowledge content changes.

## Reuse, not reinvention

No new review model or service was introduced. The platform `customerreview` extension
(`CustomerReview` type + `CustomerReviewService`) is reused; only the populator and sample data
live in the custom extensions.

## Scope

Read-only. Writing/submitting reviews, moderation/approval-workflow changes, and storefront
rendering are out of scope.
