# Components — product reviews

## Custom (coremcp / sampledatamcp)

- **`McpProductReviewsPopulator`** — `coremcp/src/com/coremcp/product/populator/McpProductReviewsPopulator.java`
  - `Populator<ProductModel, ProductData>`. Fetches via `CustomerReviewService.getReviewsForProduct`,
    filters approved & non-blocked, maps to `ReviewData` (rating/headline/comment + reviewer name),
    sets an always-non-null `reviews` list and `numberOfReviews`.
- **Spring wiring** — `coremcp/resources/coremcp-spring.xml`
  - `<bean id="mcpProductReviewsPopulator" .../>` + `<alias name="mcpProductReviewsPopulator" alias="productReviewsPopulator"/>`.
    coremcp loads after commercefacades, so this alias replaces the OOTB `productReviewsPopulator`
    in the `ProductOption.REVIEW` populator list. Only the MCP `product_get` path consumes
    `ProductOption.REVIEW` in this project (no storefront), so the swap is contained.
- **Unit test** — `coremcp/testsrc/com/coremcp/product/populator/McpProductReviewsPopulatorTest.java`
  (filtering, alias fallback, empty-list; Mockito, no DB).
- **Sample data** — `sampledatamcp/resources/impex/projectdata-80-reviews.impex`
  (approved reviews for LAPTOP_PRO_15 / SMARTPHONE_X / WIRELESS_HEADPHONES in Staged + Online;
  HD_WEBCAM left with none). Loads on `ant initialize`; import at demo time via HAC (`./gradlew impex`).
- **Integration test** — `coremcp/testsrc/com/coremcp/test/ProductReviewsIntegrationTest.java`
  with fixture `coremcp/resources/coremcp/test/testdata-reviews.impex`.

## Platform (reused, not modified)

- `de.hybris.platform.customerreview.CustomerReviewService` (bean `customerReviewService`) —
  `getReviewsForProduct(ProductModel)` etc.
- `CustomerReview` item type — `customerreview-items.xml` (rating, headline, comment, blocked,
  alias, approvalStatus[approved/pending/rejected]; relations to Product and User).
- `de.hybris.platform.commercefacades.product.data.ReviewData` — payload fields:
  `rating` (Double), `headline`, `comment`, `alias` (reviewer display name), `date`, `principal`.
- `ProductFacade.getProductForCodeAndOptions` + `defaultProductConfiguredPopulator`
  (`REVIEW → productReviewPopulatorList → productReviewsPopulator`) — commercefacades-spring.xml.
- `ProductGetToolHandler` (`coremcp/src/com/coremcp/tools/impl/ProductGetToolHandler.java`) —
  unchanged; serialises `ProductData` (incl. `reviews`) to the MCP JSON payload.
