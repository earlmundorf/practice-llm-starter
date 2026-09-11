# Commerce Diagnostics

Use this guide before the first HAC call when the user asks what is happening in SAP Commerce.

Goal: choose the smallest query that can disprove the current hypothesis. Report the HAC target, row count, and key returned values. Do not repeat the same query unless the prior result changes the hypothesis.

## Diagnostic loop

1. State the local hypothesis in one sentence.
2. Run one read-only FlexibleSearch query with a small `--max-count`.
3. Interpret the result as present, missing, ambiguous, or wrong context.
4. Choose the next query only if the result changes what you need to prove.
5. Stop before writes unless the user explicitly approves a local write.

## Data model and type relationships

Use when the user asks how SAP Commerce stores something, how two object types relate, what table/relation backs a relationship, or what a project-specific type extends.

Hypothesis: the answer is visible from the Commerce type system and only needs project-specific source lookup after the generic type/relation is known.

Type first query:

`./scripts/hac-flexquery.sh --content "SELECT {ct.code}, {ct.extensionName}, {ct.superType}, {ct.jaloclass} FROM {ComposedType AS ct} WHERE {ct.code} = '<TypeCode>'" --max-count 10`

Subtype discovery:

`./scripts/hac-flexquery.sh --content "SELECT {child.code}, {child.extensionName}, {parent.code} FROM {ComposedType AS child JOIN ComposedType AS parent ON {child.superType} = {parent.pk}} WHERE {parent.code} = '<ParentTypeCode>' OR {child.code} LIKE '%<NamePart>%' ORDER BY {parent.code}, {child.code}" --max-count 50`

Attribute discovery:

`./scripts/hac-flexquery.sh --content "SELECT {ad.qualifier}, {ad.attributeType}, {ad.unique}, {ad.partOf}, {ad.collection} FROM {AttributeDescriptor AS ad JOIN ComposedType AS ct ON {ad.enclosingType} = {ct.pk}} WHERE {ct.code} = '<TypeCode>' ORDER BY {ad.qualifier}" --max-count 100`

Relation discovery:

`./scripts/hac-flexquery.sh --content "SELECT {rd.qualifier}, {rd.attributeType}, {rd.source}, {rd.enclosingType} FROM {RelationDescriptor AS rd} WHERE {rd.qualifier} LIKE '%<qualifierOrObjectName>%'" --max-count 50`

If `RelationDescriptor` is unavailable or sparse in the runtime, inspect `*-items.xml` for the relation definition and deployment table. Use the source/target qualifiers from the relation definition, not guessed physical columns.

Interpretation:

- Type present: explain the type, extension owner, parent type, and whether it is core or project-specific.
- Type absent: extension not loaded, type system update missing, or wrong type code.
- Attribute present: use the qualifier in FlexibleSearch or ImpEx; do not switch to DB column names.
- Relation present: use the relation type in FlexibleSearch and confirm source/target orientation before joining.

Examples:

- `CronJob` is a core composed type that extends `GenericItem`; platform `items.xml` deploys it to the `CronJobs` table. Specific jobs such as Solr index jobs and import jobs are subtypes of `CronJob`.
- `Warehouse` usually does not relate directly to `CMSSite`. The common runtime path is `CMSSite/BaseSite -> BaseStore` through the site-store relation, then `BaseStore -> Warehouse` through `BaseStore2WarehouseRel`; inventory facts are `StockLevel` rows for a `productCode` and `warehouse`.
- `Product` does not become an order directly. A product is referenced by an `AbstractOrderEntry`; `CartEntry` belongs to a `Cart`, and `OrderEntry` belongs to an `Order` after checkout/place-order conversion.
- Classification values are not ordinary `Product` attributes. The definition lives in `ClassificationSystemVersion -> ClassificationClass -> ClassAttributeAssignment -> ClassificationAttribute`; product values live in `ProductFeature` rows attached to the product.
- Users, groups, catalog permissions, and search restrictions are in the data model. `User` and `UserGroup` are principal types; catalog-version access uses `readPrincipals` and `writePrincipals`; `SearchRestriction` can filter items by principal and restricted type.

## BaseSite and BaseStore

Use when page behavior, storefront routing, site configuration, currencies, languages, catalogs, or Solr context looks wrong.

Hypothesis: the BaseSite or BaseStore is missing or not connected to the expected catalog/site context.

First query, using the standard site-store relation:

`./scripts/hac-flexquery.sh --content "SELECT {site.uid}, {store.uid} FROM {CMSSite AS site LEFT JOIN StoresForCMSSite AS rel ON {rel.source} = {site.pk} LEFT JOIN BaseStore AS store ON {rel.target} = {store.pk}} WHERE {site.uid} = '<baseSiteUid>'" --max-count 20`

Interpretation:

- Zero rows: wrong BaseSite uid or missing site import.
- BaseSite present but store blank: BaseSite exists but BaseStore relation wiring is missing.
- Wrong store uid: site is present but points to another store context.

Follow-up only when needed: query `BaseStore` by uid and inspect catalogs/currencies/languages.

## Catalog and CatalogVersion

Use when imported product/content/category data is missing, duplicated, or appears in the wrong online/staged state.

Hypothesis: the expected catalog or catalogVersion is missing or inactive.

First query:

`./scripts/hac-flexquery.sh --content "SELECT {cv.pk}, {c.id}, {cv.version}, {cv.active} FROM {CatalogVersion AS cv JOIN Catalog AS c ON {cv.catalog} = {c.pk}} WHERE {c.id} = '<catalogId>'" --max-count 10`

Interpretation:

- Zero rows: catalog not imported or catalog id is wrong.
- Only Staged exists: Online validation will fail until sync/import creates Online data.
- Active flag unexpected: catalog version state may not match storefront expectations.

Follow-up only when needed: query the specific item by code and catalogVersion.

## Products, Categories, and Content

Use when a product, category, CMS item, or relation is absent from storefront or Backoffice views.

Hypothesis: the item exists in one catalogVersion but not the catalogVersion used by the runtime.

Product first query:

`./scripts/hac-flexquery.sh --content "SELECT {p.code}, {c.id}, {cv.version}, {p.modifiedtime} FROM {Product AS p JOIN CatalogVersion AS cv ON {p.catalogVersion} = {cv.pk} JOIN Catalog AS c ON {cv.catalog} = {c.pk}} WHERE {p.code} = '<code>'" --max-count 20`

Category first query:

`./scripts/hac-flexquery.sh --content "SELECT {cat.code}, {c.id}, {cv.version}, {cat.modifiedtime} FROM {Category AS cat JOIN CatalogVersion AS cv ON {cat.catalogVersion} = {cv.pk} JOIN Catalog AS c ON {cv.catalog} = {c.pk}} WHERE {cat.code} = '<code>'" --max-count 20`

Product-to-categories query:

`./scripts/hac-flexquery.sh --content "SELECT {product.code}, {cat.code}, {catalog.id}, {cv.version} FROM {Product AS product JOIN CategoryProductRelation AS rel ON {rel.target} = {product.pk} JOIN Category AS cat ON {rel.source} = {cat.pk} JOIN CatalogVersion AS cv ON {cat.catalogVersion} = {cv.pk} JOIN Catalog AS catalog ON {cv.catalog} = {catalog.pk}} WHERE {product.code} = '<productCode>'" --max-count 100`

Category-to-products query:

`./scripts/hac-flexquery.sh --content "SELECT {cat.code}, {product.code}, {catalog.id}, {cv.version} FROM {Category AS cat JOIN CategoryProductRelation AS rel ON {rel.source} = {cat.pk} JOIN Product AS product ON {rel.target} = {product.pk} JOIN CatalogVersion AS cv ON {cat.catalogVersion} = {cv.pk} JOIN Catalog AS catalog ON {cv.catalog} = {catalog.pk}} WHERE {cat.code} = '<categoryCode>'" --max-count 100`

Variant children query:

`./scripts/hac-flexquery.sh --content "SELECT {base.code}, {base.variantType}, {variant.code}, {variant.itemtype}, {catalog.id}, {cv.version} FROM {Product AS base JOIN VariantProduct AS variant ON {variant.baseProduct} = {base.pk} JOIN CatalogVersion AS cv ON {variant.catalogVersion} = {cv.pk} JOIN Catalog AS catalog ON {cv.catalog} = {catalog.pk}} WHERE {base.code} = '<baseProductCode>'" --max-count 100`

Variant-to-base query:

`./scripts/hac-flexquery.sh --content "SELECT {variant.code}, {variant.itemtype}, {base.code}, {base.variantType}, {catalog.id}, {cv.version} FROM {VariantProduct AS variant JOIN Product AS base ON {variant.baseProduct} = {base.pk} JOIN CatalogVersion AS cv ON {variant.catalogVersion} = {cv.pk} JOIN Catalog AS catalog ON {cv.catalog} = {catalog.pk}} WHERE {variant.code} = '<variantProductCode>'" --max-count 20`

CMS first query:

`./scripts/hac-flexquery.sh --content "SELECT {item.uid}, {c.id}, {cv.version}, {item.modifiedtime} FROM {CMSItem AS item JOIN CatalogVersion AS cv ON {item.catalogVersion} = {cv.pk} JOIN Catalog AS c ON {cv.catalog} = {c.pk}} WHERE {item.uid} = '<uid>'" --max-count 20`

Interpretation:

- Staged only: storefront Online view needs sync or Online import.
- Online only: Staged authoring view may not show it.
- Multiple rows across catalogs: include catalog id in later validation.
- Zero rows: import did not create the item or identifier is wrong.
- Product exists but has no category relation: listing/navigation may not show it even though the product row exists.
- Category relation exists only in Staged: Online storefronts usually need catalog sync or an Online import.
- Base product has `variantType` but no variants: variant data is missing or the variants are in a different catalog version.
- Variant exists with wrong or missing `baseProduct`: PDP selection, add-to-cart, or stock lookup can resolve the wrong product context.

## Users, Groups, Catalog Permissions, and Search Restrictions

Use when a product, category, CMS item, catalog version, Backoffice view, storefront response, or OCC response differs by user, group, or channel.

Hypothesis: the item exists, but the effective principal context cannot read it, is filtered by a search restriction, or is using a different session/catalog context.

User-to-groups query:

`./scripts/hac-flexquery.sh --content "SELECT {user.uid}, {group.uid} FROM {User AS user JOIN PrincipalGroupRelation AS relation ON {relation.source} = {user.pk} JOIN UserGroup AS group ON {relation.target} = {group.pk}} WHERE {user.uid} = '<userUid>'" --max-count 100`

Catalog-version readable principals query:

`./scripts/hac-flexquery.sh --content "SELECT {catalog.id}, {version.version}, {principal.uid} FROM {CatalogVersion AS version JOIN Catalog AS catalog ON {version.catalog} = {catalog.pk} JOIN Principal2ReadableCatalogVersionRelation AS relation ON {relation.target} = {version.pk} JOIN Principal AS principal ON {relation.source} = {principal.pk}} WHERE {catalog.id} = '<catalogId>' AND {version.version} = '<version>'" --max-count 100`

Catalog-version writable principals query:

`./scripts/hac-flexquery.sh --content "SELECT {catalog.id}, {version.version}, {principal.uid} FROM {CatalogVersion AS version JOIN Catalog AS catalog ON {version.catalog} = {catalog.pk} JOIN Principal2WriteableCatalogVersionRelation AS relation ON {relation.target} = {version.pk} JOIN Principal AS principal ON {relation.source} = {principal.pk}} WHERE {catalog.id} = '<catalogId>' AND {version.version} = '<version>'" --max-count 100`

Category allowed principals query:

`./scripts/hac-flexquery.sh --content "SELECT {category.code}, {catalog.id}, {version.version}, {principal.uid} FROM {Category AS category JOIN CatalogVersion AS version ON {category.catalogVersion} = {version.pk} JOIN Catalog AS catalog ON {version.catalog} = {catalog.pk} JOIN Category2PrincipalRelation AS relation ON {relation.source} = {category.pk} JOIN Principal AS principal ON {relation.target} = {principal.pk}} WHERE {category.code} = '<categoryCode>'" --max-count 100`

Search restrictions by principal query:

`./scripts/hac-flexquery.sh --content "SELECT {restriction.code}, {principal.uid}, {type.code}, {restriction.active}, {restriction.query} FROM {SearchRestriction AS restriction JOIN Principal AS principal ON {restriction.principal} = {principal.pk} JOIN ComposedType AS type ON {restriction.restrictedType} = {type.pk}} WHERE {principal.uid} = '<principalUid>'" --max-count 100`

Search restrictions by type query:

`./scripts/hac-flexquery.sh --content "SELECT {restriction.code}, {principal.uid}, {type.code}, {restriction.active}, {restriction.query} FROM {SearchRestriction AS restriction JOIN Principal AS principal ON {restriction.principal} = {principal.pk} JOIN ComposedType AS type ON {restriction.restrictedType} = {type.pk}} WHERE {type.code} IN ('Product', 'Category', 'CMSItem')" --max-count 100`

Relation-based search restriction fallback:

`./scripts/hac-flexquery.sh --content "SELECT {restriction.code}, {principal.uid}, {type.code}, {restriction.active}, {restriction.query} FROM {Principal AS principal JOIN Principal2SearchRestrictionRelation AS relation ON {relation.source} = {principal.pk} JOIN SearchRestriction AS restriction ON {relation.target} = {restriction.pk} JOIN ComposedType AS type ON {restriction.restrictedType} = {type.pk}} WHERE {principal.uid} = '<principalUid>'" --max-count 100`

Interpretation:

- User has no expected group: group assignment or customer/employee context is wrong.
- CatalogVersion has no readable principal/group for the effective user: product/category/CMS reads can fail even when the row exists.
- Category has `allowedPrincipals`: category navigation/access may be narrower than catalog-version access.
- Active `SearchRestriction` exists for `Product`, `Category`, or `CMSItem`: admin HAC results can differ from service-layer, storefront, OCC, or Backoffice results.
- Product/category exists but is still not visible: check catalog-version principal access, category allowed principals, search restrictions, approval/status, category relation, sync, Solr, CMS/personalization, and storefront/facade logic.

Do not assume products have direct allowed groups unless project source or runtime metadata proves a custom product attribute/relation. Core Commerce product visibility is usually indirect through catalog versions, categories, restrictions, search/indexing, and business logic.

## Content Catalogs, Pages, Slots, Components, and Navigation

Use when content exists in HAC/Backoffice but the storefront, OCC CMS endpoint, navigation, category landing page, or page component output does not match.

Hypothesis: the CMS item may exist, but it is in the wrong content catalog/version or is not connected through the site, page, template, slot, component, navigation, restriction, or sync graph.

Site to content catalogs query:

`./scripts/hac-flexquery.sh --content "SELECT {site.uid}, {catalog.id} FROM {CMSSite AS site JOIN CatalogsForCMSSite AS rel ON {rel.source} = {site.pk} JOIN ContentCatalog AS catalog ON {rel.target} = {catalog.pk}} WHERE {site.uid} = '<baseSiteUid>' ORDER BY {catalog.id}" --max-count 50`

Content catalog versions query:

`./scripts/hac-flexquery.sh --content "SELECT {catalog.id}, {version.version}, {version.active} FROM {ContentCatalog AS catalog JOIN CatalogVersion AS version ON {version.catalog} = {catalog.pk}} WHERE {catalog.id} = '<contentCatalogId>' ORDER BY {version.version}" --max-count 20`

Content page query:

`./scripts/hac-flexquery.sh --content "SELECT {page.uid}, {page.label}, {page.homepage}, {page.defaultPage}, {page.approvalStatus}, {page.pageStatus}, {template.uid}, {catalog.id}, {version.version} FROM {ContentPage AS page JOIN CatalogVersion AS version ON {page.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk} LEFT JOIN PageTemplate AS template ON {page.masterTemplate} = {template.pk}} WHERE ({page.uid} = '<pageUid>' OR {page.label} = '<pageLabel>') ORDER BY {catalog.id}, {version.version}" --max-count 20`

Template slots query:

`./scripts/hac-flexquery.sh --content "SELECT {template.uid}, {slotRel.position}, {slotRel.allowOverwrite}, {slot.uid}, {slot.active}, {catalog.id}, {version.version} FROM {PageTemplate AS template JOIN ContentSlotForTemplate AS slotRel ON {slotRel.pageTemplate} = {template.pk} JOIN ContentSlot AS slot ON {slotRel.contentSlot} = {slot.pk} JOIN CatalogVersion AS version ON {template.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk}} WHERE {template.uid} = '<templateUid>' ORDER BY {slotRel.position}" --max-count 100`

Page slots query:

`./scripts/hac-flexquery.sh --content "SELECT {page.uid}, {slotRel.position}, {slot.uid}, {slot.active}, {catalog.id}, {version.version} FROM {AbstractPage AS page JOIN ContentSlotForPage AS slotRel ON {slotRel.page} = {page.pk} JOIN ContentSlot AS slot ON {slotRel.contentSlot} = {slot.pk} JOIN CatalogVersion AS version ON {page.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk}} WHERE {page.uid} = '<pageUid>' ORDER BY {slotRel.position}" --max-count 100`

Slot components query:

`./scripts/hac-flexquery.sh --content "SELECT {slot.uid}, {component.uid}, {component.name}, {component.visible}, {component.itemtype}, {catalog.id}, {version.version} FROM {ContentSlot AS slot JOIN ElementsForSlot AS rel ON {rel.source} = {slot.pk} JOIN AbstractCMSComponent AS component ON {rel.target} = {component.pk} JOIN CatalogVersion AS version ON {component.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk}} WHERE {slot.uid} = '<slotUid>' ORDER BY {component.uid}" --max-count 100`

Navigation node entries query:

`./scripts/hac-flexquery.sh --content "SELECT {node.uid}, {node.title}, {node.visible}, {entry.uid}, {entry.item}, {entry.itemtype}, {catalog.id}, {version.version} FROM {CMSNavigationNode AS node LEFT JOIN CMSNavNodesToCMSNavEntries AS rel ON {rel.source} = {node.pk} LEFT JOIN CMSNavigationEntry AS entry ON {rel.target} = {entry.pk} JOIN CatalogVersion AS version ON {node.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk}} WHERE {node.uid} = '<navigationNodeUid>' ORDER BY {entry.uid}" --max-count 100`

Navigation children query:

`./scripts/hac-flexquery.sh --content "SELECT {parent.uid}, {parent.visible}, {child.uid}, {child.visible}, {catalog.id}, {version.version} FROM {CMSNavigationNode AS parent JOIN CMSNavigationNodeChildren AS rel ON {rel.source} = {parent.pk} JOIN CMSNavigationNode AS child ON {rel.target} = {child.pk} JOIN CatalogVersion AS version ON {child.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk}} WHERE {parent.uid} = '<navigationNodeUid>' ORDER BY {child.uid}" --max-count 100`

Page restrictions query:

`./scripts/hac-flexquery.sh --content "SELECT {page.uid}, {restriction.uid}, {restriction.itemtype}, {catalog.id}, {version.version} FROM {AbstractPage AS page JOIN RestrictionsForPages AS rel ON {rel.source} = {page.pk} JOIN AbstractRestriction AS restriction ON {rel.target} = {restriction.pk} JOIN CatalogVersion AS version ON {restriction.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk}} WHERE {page.uid} = '<pageUid>'" --max-count 100`

Component restrictions query:

`./scripts/hac-flexquery.sh --content "SELECT {component.uid}, {restriction.uid}, {restriction.itemtype}, {catalog.id}, {version.version} FROM {AbstractCMSComponent AS component JOIN RestrictionsForComponents AS rel ON {rel.source} = {component.pk} JOIN AbstractRestriction AS restriction ON {rel.target} = {restriction.pk} JOIN CatalogVersion AS version ON {restriction.catalogVersion} = {version.pk} JOIN ContentCatalog AS catalog ON {version.catalog} = {catalog.pk}} WHERE {component.uid} = '<componentUid>'" --max-count 100`

Category/product CMS restriction query:

`./scripts/hac-flexquery.sh --content "SELECT 'category' AS kind, {restriction.uid}, {category.code} FROM {CMSCategoryRestriction AS restriction JOIN CategoriesForRestriction AS rel ON {rel.source} = {restriction.pk} JOIN Category AS category ON {rel.target} = {category.pk}} WHERE {restriction.uid} = '<restrictionUid>' UNION ALL {{ SELECT 'product' AS kind, {restriction.uid}, {product.code} FROM {CMSProductRestriction AS restriction JOIN ProductsForRestriction AS rel ON {rel.source} = {restriction.pk} JOIN Product AS product ON {rel.target} = {product.pk}} WHERE {restriction.uid} = '<restrictionUid>' }}" --max-count 100`

Interpretation:

- Site has no expected content catalog: the storefront can have a valid BaseStore and product catalog while CMS page lookup still uses the wrong or missing content catalog.
- Page exists in Staged only: storefront Online views usually need content sync or Online import.
- Page exists but template is missing/inactive: page resolution can succeed while layout slot resolution fails.
- Template slots exist but page slots do not: the page may rely entirely on template slots; that can be normal unless an override was expected.
- Slot exists but component list is empty or inactive: the slot is wired but has no renderable CMS components.
- Component exists but is invisible or restricted: inspect component restrictions separately from page restrictions.
- Navigation entry points to a category/product: that target is still product-catalog data; validate the category/product catalog version separately from the content catalog navigation row.
- BaseStore affects CMS mostly through site context and specific restrictions such as `CMSBaseStoreTimeRestriction`; do not look for normal CMS pages directly under BaseStore.

## Classification Catalogs and Attributes

Use when product facets, classified attributes, comparison/listable attributes, or classification-backed Solr fields are missing or wrong.

Hypothesis: the product exists, but its classification system version, class assignment, attribute assignment, or product feature row is missing or in the wrong catalog version.

Classification system query:

`./scripts/hac-flexquery.sh --content "SELECT {system.id}, {version.version}, {version.active} FROM {ClassificationSystem AS system JOIN ClassificationSystemVersion AS version ON {version.catalog} = {system.pk}} WHERE {system.id} = '<classificationSystemId>'" --max-count 20`

Class assignment query:

`./scripts/hac-flexquery.sh --content "SELECT {class.code}, {system.id}, {version.version}, {attr.code}, {assignment.attributeType}, {assignment.localized}, {assignment.multiValued}, {assignment.searchable}, {assignment.listable}, {assignment.comparable} FROM {ClassAttributeAssignment AS assignment JOIN ClassificationClass AS class ON {assignment.classificationClass} = {class.pk} JOIN ClassificationAttribute AS attr ON {assignment.classificationAttribute} = {attr.pk} JOIN ClassificationSystemVersion AS version ON {assignment.systemVersion} = {version.pk} JOIN ClassificationSystem AS system ON {version.catalog} = {system.pk}} WHERE {class.code} = '<classificationClassCode>' ORDER BY {assignment.position}" --max-count 100`

Product feature query:

`./scripts/hac-flexquery.sh --content "SELECT {product.code}, {feature.qualifier}, {attr.code}, {feature.language}, {feature.valuePosition}, {feature.stringValue}, {feature.numberValue}, {feature.booleanValue}, {feature.unit} FROM {Product AS product JOIN ProductFeature AS feature ON {feature.product} = {product.pk} LEFT JOIN ClassAttributeAssignment AS assignment ON {feature.classificationAttributeAssignment} = {assignment.pk} LEFT JOIN ClassificationAttribute AS attr ON {assignment.classificationAttribute} = {attr.pk}} WHERE {product.code} = '<productCode>' ORDER BY {feature.qualifier}, {feature.valuePosition}" --max-count 100`

Product/classification class query:

`./scripts/hac-flexquery.sh --content "SELECT {product.code}, {class.code}, {system.id}, {version.version} FROM {Product AS product JOIN CategoryProductRelation AS rel ON {rel.target} = {product.pk} JOIN ClassificationClass AS class ON {rel.source} = {class.pk} JOIN ClassificationSystemVersion AS version ON {class.catalogVersion} = {version.pk} JOIN ClassificationSystem AS system ON {version.catalog} = {system.pk}} WHERE {product.code} = '<productCode>'" --max-count 100`

Interpretation:

- Classification system/version missing: classification catalog import or extension setup is incomplete.
- Classification class exists but assignment missing: the product class does not declare the expected attribute.
- Assignment exists but feature missing: the product has no value for the assigned classification attribute.
- Feature exists with only one value column populated: that is normal; `ProductFeature` stores typed values across `stringValue`, `numberValue`, `booleanValue`, raw/dynamic value, unit, language, and value position.
- Attribute exists but Solr facet/listing is missing: inspect `ClassAttributeAssignment.searchable/listable/comparable` and the Solr indexed property/provider separately.

## Warehouse and Inventory

Use when stock availability, inventory lookup, pickup/location stock, or warehouse wiring looks wrong.

Hypothesis: the product exists, but the site/store is not connected to the warehouse that owns the `StockLevel`, or the `StockLevel` is missing for that product code.

First query, from site to store to warehouse:

`./scripts/hac-flexquery.sh --content "SELECT {site.uid}, {store.uid}, {warehouse.code} FROM {CMSSite AS site JOIN StoresForCMSSite AS siteStore ON {siteStore.source} = {site.pk} JOIN BaseStore AS store ON {siteStore.target} = {store.pk} JOIN BaseStore2WarehouseRel AS storeWarehouse ON {storeWarehouse.source} = {store.pk} JOIN Warehouse AS warehouse ON {storeWarehouse.target} = {warehouse.pk}} WHERE {site.uid} = '<baseSiteUid>'" --max-count 50`

Stock row query:

`./scripts/hac-flexquery.sh --content "SELECT {sl.productCode}, {warehouse.code}, {sl.available}, {sl.reserved}, {sl.overSelling}, {sl.inStockStatus}, {sl.treatNegativeAsZero} FROM {StockLevel AS sl JOIN Warehouse AS warehouse ON {sl.warehouse} = {warehouse.pk}} WHERE {sl.productCode} = '<productCode>'" --max-count 50`

Product context query:

`./scripts/hac-flexquery.sh --content "SELECT {p.code}, {catalog.id}, {cv.version} FROM {Product AS p JOIN CatalogVersion AS cv ON {p.catalogVersion} = {cv.pk} JOIN Catalog AS catalog ON {cv.catalog} = {catalog.pk}} WHERE {p.code} = '<productCode>'" --max-count 20`

Interpretation:

- Product exists but no stock row: inventory has not been created for that product code and warehouse.
- Stock exists in an unrelated warehouse: the store/site cannot see that inventory through normal store warehouse resolution.
- Stock row exists but product context is wrong: the product code may belong to a different catalog/version than the storefront uses.
- Available stock is still not salable: stock services may apply reserved quantity, overselling, pre-order, in-stock status, date, threshold, or custom strategy rules.

## Products, Carts, and Orders

Use when a product is in a cart, should have become an order, or an order line needs to be traced back to product/catalog context.

Hypothesis: the cart/order header exists, but the product relationship lives on the entry row and must be checked through `CartEntry` or `OrderEntry`.

Cart entry query:

`./scripts/hac-flexquery.sh --content "SELECT {cart.code}, {cart.user}, {entry.entryNumber}, {product.code}, {entry.quantity}, {entry.basePrice}, {entry.totalPrice} FROM {Cart AS cart JOIN CartEntry AS entry ON {entry.order} = {cart.pk} JOIN Product AS product ON {entry.product} = {product.pk}} WHERE {cart.code} = '<cartCode>' ORDER BY {entry.entryNumber}" --max-count 100`

Order entry query:

`./scripts/hac-flexquery.sh --content "SELECT {order.code}, {order.status}, {order.user}, {entry.entryNumber}, {product.code}, {entry.quantity}, {entry.basePrice}, {entry.totalPrice} FROM {Order AS order JOIN OrderEntry AS entry ON {entry.order} = {order.pk} JOIN Product AS product ON {entry.product} = {product.pk}} WHERE {order.code} = '<orderCode>' ORDER BY {entry.entryNumber}" --max-count 100`

Product usage query:

`./scripts/hac-flexquery.sh --content "SELECT 'cart' AS kind, {cart.code}, {entry.entryNumber}, {entry.quantity} FROM {CartEntry AS entry JOIN Cart AS cart ON {entry.order} = {cart.pk} JOIN Product AS product ON {entry.product} = {product.pk}} WHERE {product.code} = '<productCode>' UNION ALL {{ SELECT 'order' AS kind, {order.code}, {entry.entryNumber}, {entry.quantity} FROM {OrderEntry AS entry JOIN Order AS order ON {entry.order} = {order.pk} JOIN Product AS product ON {entry.product} = {product.pk}} WHERE {product.code} = '<productCode>' }}" --max-count 100`

Interpretation:

- Cart/order exists but no entries: header exists without line items, or the wrong code/context is being queried.
- Entry exists with wrong product: add-to-cart or conversion used a different product PK/code than expected.
- Product exists but entry is missing: product visibility and cart/order persistence are separate problems.
- Order exists but cart still exists: this may be normal for saved/session carts or custom checkout behavior; do not assume one deletes the other without project evidence.

## Catalog Sync Jobs

Use when Staged data exists but Online data does not, or when sync behavior is suspect.

Hypothesis: the expected sync job is missing, disabled, or has not run successfully.

First query:

`./scripts/hac-flexquery.sh --content "SELECT {job.code}, {job.removeMissingItems}, {job.createNewItems} FROM {CatalogVersionSyncJob AS job} WHERE {job.code} LIKE '%<catalogId>%Staged%Online%'" --max-count 20`

Interpretation:

- Zero rows: sync job was not created for that catalog pair.
- Job present: check last CronJob only if runtime data is still absent.

Follow-up only when needed:

`./scripts/hac-flexquery.sh --content "SELECT {cj.code}, {cj.status}, {cj.result}, {cj.startTime}, {cj.endTime} FROM {CronJob AS cj} WHERE {cj.job} IN ({{ SELECT {job.pk} FROM {CatalogVersionSyncJob AS job} WHERE {job.code} = '<syncJobCode>' }}) ORDER BY {cj.startTime} DESC" --max-count 5`

## Product Catalogs, Categories, and Search

Use when product/category data exists in Commerce but search, listing, navigation, or facets do not show the expected results.

Hypothesis: the product and category may exist, but the search configuration, indexed type, indexed property, restriction context, or index freshness does not include that data.

Start with the Solr config by name when known:

`./scripts/hac-flexquery.sh --content "SELECT {cfg.name}, {cfg.indexNamePrefix}, {cfg.solrSearchConfig}, {cfg.solrIndexConfig}, {cfg.solrServerConfig} FROM {SolrFacetSearchConfig AS cfg} WHERE {cfg.name} = '<solrConfigName>'" --max-count 10`

List the catalog versions that feed the search config:

`./scripts/hac-flexquery.sh --content "SELECT {cfg.name}, {cat.id}, {cv.version}, {cv.active} FROM {SolrFacetSearchConfig AS cfg JOIN SolrFacetSearchConfig2CatalogVersionRelation AS rel ON {rel.source} = {cfg.pk} JOIN CatalogVersion AS cv ON {rel.target} = {cv.pk} JOIN Catalog AS cat ON {cv.catalog} = {cat.pk}} WHERE {cfg.name} = '<solrConfigName>' ORDER BY {cat.id}, {cv.version}" --max-count 50`

List indexed types and the Commerce composed type they index:

`./scripts/hac-flexquery.sh --content "SELECT {cfg.name}, {it.identifier}, {ct.code}, {it.variant}, {it.indexName}, {it.modelLoader}, {it.valuesProvider}, {it.defaultFieldValueProvider} FROM {SolrIndexedType AS it JOIN SolrFacetSearchConfig AS cfg ON {it.solrFacetSearchConfig} = {cfg.pk} JOIN ComposedType AS ct ON {it.type} = {ct.pk}} WHERE {cfg.name} = '<solrConfigName>' ORDER BY {it.identifier}" --max-count 50`

List category and facet-related indexed properties:

`./scripts/hac-flexquery.sh --content "SELECT {it.identifier}, {prop.name}, {prop.type}, {prop.facet}, {prop.facetType}, {prop.visible}, {prop.categoryField}, {prop.localized}, {prop.multiValue}, {prop.fieldValueProvider}, {prop.valueProviderParameter} FROM {SolrIndexedProperty AS prop JOIN SolrIndexedType AS it ON {prop.solrIndexedType} = {it.pk}} WHERE {it.identifier} = '<indexedType>' AND ({prop.name} LIKE '%category%' OR {prop.categoryField} = 1 OR {prop.facet} = 1) ORDER BY {prop.facet} DESC, {prop.categoryField} DESC, {prop.name}" --max-count 100`

Cross-check the product/category relation in the intended product catalog version:

`./scripts/hac-flexquery.sh --content "SELECT {cat.id}, {cv.version}, {category.code}, {product.code}, {product.approvalStatus} FROM {CategoryProductRelation AS rel JOIN Category AS category ON {rel.source} = {category.pk} JOIN Product AS product ON {rel.target} = {product.pk} JOIN CatalogVersion AS cv ON {product.catalogVersion} = {cv.pk} JOIN Catalog AS cat ON {cv.catalog} = {cat.pk}} WHERE {category.code} = '<categoryCode>' AND {product.code} = '<productCode>' AND {cat.id} = '<productCatalogId>' AND {cv.version} = '<version>'" --max-count 20`

Check index freshness for the Solr config:

`./scripts/hac-flexquery.sh --content "SELECT {cj.code}, {cj.status}, {cj.result}, {cj.startTime}, {cj.endTime} FROM {SolrIndexerCronJob AS cj JOIN SolrFacetSearchConfig AS cfg ON {cj.facetSearchConfig} = {cfg.pk}} WHERE {cfg.name} = '<solrConfigName>' ORDER BY {cj.startTime} DESC" --max-count 10`

Interpretation:

- Config zero rows: the Solr config import is missing or the site points to a different project-specific Solr attribute/config.
- Catalog version mismatch: search is indexing a different catalog/version than the product or category you validated.
- Indexed type mismatch: the indexed type points to a different composed type, variant mode, or index name than the storefront/OCC flow expects.
- Property missing: the data can exist in Commerce but will not appear as a searchable field, category filter, classification value, or facet until an indexed property/value provider emits it.
- Facet missing: check `facet`, `facetType`, `visible`, provider/range configuration, and whether the index has been rebuilt since the property changed.
- Product/category relation present but search empty: check Online sync, approval/status, active search restrictions, catalog-version read principals, category allowed principals, session catalog versions, and index freshness before assuming product data is missing.

## CronJobs

Use when asynchronous work, sync, import, indexing, or custom jobs appear stuck or stale.

Hypothesis: the job exists but the latest CronJob did not succeed.

First query:

`./scripts/hac-flexquery.sh --content "SELECT {cj.code}, {cj.status}, {cj.result}, {cj.startTime}, {cj.endTime} FROM {CronJob AS cj} WHERE {cj.code} LIKE '%<jobCodeOrPrefix>%' ORDER BY {cj.startTime} DESC" --max-count 10`

Interpretation:

- Zero rows: job never ran or code prefix is wrong.
- RUNNING/UNKNOWN: check logs or Backoffice; do not re-trigger blindly.
- ERROR/FAILURE: report status/result and ask before any corrective write.

## Media

Use when images/files are missing, URLs are wrong, or media references fail.

Hypothesis: the media item is missing, has no real data, or is in the wrong catalogVersion/folder.

First query:

`./scripts/hac-flexquery.sh --content "SELECT {m.code}, {m.realFileName}, {m.mime}, {m.URL}, {m.dataPK} FROM {Media AS m} WHERE {m.code} = '<mediaCode>'" --max-count 20`

Interpretation:

- Zero rows: media import missing or wrong code.
- dataPK blank: media model exists but binary data may be missing.
- URL unexpected: media folder/storage context may be wrong.

## Type System

Use when FlexibleSearch, ImpEx, Backoffice, or model access indicates missing types or attributes.

Hypothesis: the type system does not contain the expected item type or attribute.

Type first query:

`./scripts/hac-flexquery.sh --content "SELECT {code}, {extensionName}, {jaloclass} FROM {ComposedType} WHERE {code} = '<TypeCode>'" --max-count 10`

Attribute follow-up:

`./scripts/hac-flexquery.sh --content "SELECT {ad.qualifier}, {ad.attributeType}, {ad.optional}, {ad.unique} FROM {AttributeDescriptor AS ad JOIN ComposedType AS ct ON {ad.enclosingType} = {ct.pk}} WHERE {ct.code} = '<TypeCode>' AND {ad.qualifier} = '<attributeQualifier>'" --max-count 10`

Interpretation:

- Type zero rows: extension not loaded or type system update not applied.
- Attribute zero rows: qualifier wrong, extension dependency missing, or update not applied.
- Attribute present: the issue is likely query/import context rather than type existence.

## Reporting pattern

Use concise, evidence-first output:

- `Target`: HAC URL and Java context from the script output.
- `Hypothesis`: the one thing this query tested.
- `Result`: row count and key values.
- `Interpretation`: present, missing, ambiguous, or wrong context.
- `Next`: one follow-up query or stop condition.
