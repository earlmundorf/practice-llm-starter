# Diagram — product review read path

```
MCP caller (agent / tool client)
        │  product_get { code, options: [ ... , REVIEW ] }
        ▼
ProductGetToolHandler                         (coremcp)
        │  productFacade.getProductForCodeAndOptions(code, options)
        ▼
DefaultProductFacade                          (commercefacades)
        │  productConfiguredPopulator → option map
        │  ProductOption.REVIEW → productReviewPopulatorList
        ▼
mcpProductReviewsPopulator                    (coremcp; alias-swapped over OOTB)
        │  getReviewsForProduct(product)
        ▼
CustomerReviewService  ──►  DB (CustomerReviews table)     [not Solr]
        │  List<CustomerReviewModel>
        ▼
mcpProductReviewsPopulator
        │  filter: approvalStatus == approved && !blocked
        │  map → ReviewData(rating, headline, comment, alias := alias|name|uid)
        │  productData.setReviews(list)   // empty list, never null
        ▼
ProductGetToolHandler
        │  objectMapper.valueToTree(productData)  (reviews included) + url
        ▼
MCP JSON payload  →  caller
        e.g. { "code": "...", ..., "reviews": [ {rating, headline, comment, alias}, ... ] }
        or   { ..., "reviews": [] }   when no visible reviews
```

Sample data: `sampledatamcp/.../projectdata-80-reviews.impex` seeds approved reviews
(LAPTOP_PRO_15, SMARTPHONE_X, WIRELESS_HEADPHONES); HD_WEBCAM has none.
