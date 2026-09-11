# SAP Commerce Object Model Expert Map

Use this guidance before generating FlexibleSearch, HAC Groovy, or ImpEx that touches site, store, catalog, inventory, Solr, or CMS configuration.

## Expert stance

SAP Commerce has a stable commerce object graph even when each implementation extends it. Treat BaseSite, BaseStore, catalogs, catalog versions, warehouses, stock, Solr, and CMS as a connected system. Do not generate isolated queries or imports that only validate one object while ignoring the references that make it usable at runtime.

For general data-model questions, answer from the SAP Commerce type system first and the project implementation second. Core concepts such as `CronJob`, `Warehouse`, `BaseStore`, `CatalogVersion`, and `StockLevel` have platform-level storage and relation patterns. Project concepts such as hotels or custom site attributes should be understood by asking what composed type they extend and what attributes or relations they add.

The exact qualifiers and relation type names can vary by platform version and project extensions, but the business relationships are consistent:

```text
BaseSite / CMSSite
  -> BaseStore(s)
    -> ProductCatalog(s)
      -> CatalogVersion(s): usually Staged and Online
      -> Products, Categories, Prices, Stock lookups by product/catalog context
    -> Warehouse(s)
      -> StockLevel rows by productCode/SKU and warehouse
    -> Currency, Language, Delivery, Payment, Tax, Promotion configuration
  -> ContentCatalog(s)
    -> CatalogVersion(s): usually Staged and Online
    -> CMSItem rows keyed by uid + catalogVersion
      -> AbstractPage / ContentPage / CategoryPage / ProductPage
        -> PageTemplate through masterTemplate
        -> ContentSlotForPage rows by position
      -> PageTemplate
        -> ContentSlotForTemplate rows by position
      -> ContentSlot
        -> AbstractCMSComponent rows through ElementsForSlot
      -> CMSNavigationNode / CMSNavigationEntry
        -> ContentPage, Category, Product, or other Item targets depending on entry item
      -> AbstractRestriction rows linked to pages/components and to categories/products/users/groups/catalogs
  -> ClassificationCatalog(s)
    -> CatalogVersion(s): usually system version such as 1.0
    -> Classification classes, attributes, units, assignments
  -> SolrFacetSearchConfig / SolrIndexedType
    -> CatalogVersion(s), Language(s), Currency(s)
    -> SolrIndexedType(s)
      -> Commerce composed type being indexed, such as Product or a project domain subtype
      -> SolrIndexedProperty rows for fields, category fields, classification fields, and facets
    -> SolrIndexerCronJob rows that build or refresh index documents

ProductCatalog / CatalogVersion
  -> Category hierarchy through CategoryCategoryRelation
    -> CategoryProductRelation
      -> Product
  -> Product
    -> supercategories through CategoryProductRelation
    -> variants through Product2VariantRelation when the base has variantType
      -> VariantProduct / GenericVariantProduct
        -> baseProduct
    -> ClassificationClass through category/classification relations
      -> ClassAttributeAssignment
        -> ClassificationAttribute / ClassificationAttributeValue / Unit
      -> ProductFeature values on the product
    -> StockLevel by productCode + Warehouse
    -> CartEntry / OrderEntry through AbstractOrderEntry.product

Cart
  -> CartEntry rows through AbstractOrder2AbstractOrderEntry
    -> Product, quantity, unit, price snapshots
  -> checkout/place order process
    -> Order
      -> OrderEntry rows through AbstractOrder2AbstractOrderEntry
        -> Product, quantity, unit, price snapshots

Principal
  -> User / PrincipalGroup / UserGroup
  -> PrincipalGroupRelation for group membership and nested groups
  -> CatalogVersion readPrincipals/writePrincipals through catalog principal relations
  -> SearchRestriction through Principal2SearchRestrictionRelation

Product / Category / CMSItem
  -> CatalogVersion
    -> readPrincipals / writePrincipals controlling catalog-version access
  -> SearchRestriction can further filter by principal and restricted type
Category
  -> allowedPrincipals through Category2PrincipalRelation when category-level access is used
```

## Users, groups, and access model

SAP Commerce access is layered. Do not explain CMS or product visibility as a single permission check unless the project has a custom permission model that proves it. Start with the effective `Principal`, then walk each access layer that can admit or filter the item.

Core identity:

```text
Principal
  -> User
    -> Customer / Employee / project-specific user subtype
  -> PrincipalGroup
    -> UserGroup / project-specific group subtype
PrincipalGroupRelation
  -> Principal.members
  -> PrincipalGroup.groups
```

Access layers:

```text
Effective principal context
  -> direct and inherited groups through PrincipalGroupRelation / allGroups
  -> CatalogVersion.readPrincipals / writePrincipals
  -> Category.allowedPrincipals when category-level access is used
  -> SearchRestriction rows for the principal and restricted type
  -> CMS restrictions on pages and components
  -> Solr indexed snapshot and storefront/OCC/facade business logic
```

Catalog and category access:

```text
CatalogVersion
  -> readPrincipals through Principal2ReadableCatalogVersionRelation
  -> writePrincipals through Principal2WriteableCatalogVersionRelation
Category
  -> allowedPrincipals through Category2PrincipalRelation, deployed to Cat2PrincRel
Product
  -> CatalogVersion
  -> CategoryProductRelation to Category
```

CMS access:

```text
CMSSite
  -> ContentCatalog
    -> CatalogVersion
      -> CMSItem / AbstractPage / AbstractCMSComponent
        -> RestrictionsForPages or RestrictionsForComponents
          -> AbstractRestriction
            -> UsersForRestriction / UserGroupsForRestriction
            -> CategoriesForRestriction / ProductsForRestriction / CatalogsForRestriction
```

Interpretation rules:

- `Principal` is the shared security identity. A relation to `Principal` may point at one user, one group, or a project-specific principal subtype.
- `User.groups`/`Principal.groups` shows direct membership. Dynamic `allGroups` can include inherited or nested groups; use it conceptually, but validate direct persistence through `PrincipalGroupRelation` when writing FlexibleSearch.
- `anonymous` storefront access is often indirect: the `anonymous` user can belong to `customergroup`, and catalog/category/CMS data may be allowed for `customergroup` rather than for `anonymous` directly.
- `CatalogVersion.readPrincipals` and `writePrincipals` answer whether a principal can read or write that catalog version. They do not prove that the storefront should render a specific product, category, page, or component.
- `Category.allowedPrincipals` is category-level access metadata. It is useful for category navigation/listing access, but it is not a general per-product permission model.
- Core `Product` and `CMSItem` do not normally carry direct per-row user-group ACLs. Product and content visibility usually comes from catalog version access, category access, search restrictions, CMS restrictions, sync/approval state, Solr index state, and storefront logic.
- CMS restrictions are independent of product catalog access. A CMS component can be hidden by `CMSUserGroupRestriction` even when the linked category or product is readable, and a category/product can be inaccessible even when a navigation entry points to it.
- Search restrictions are type-level query filters tied to a principal and `ComposedType`. They can filter `Product`, `Category`, `CMSItem`, or project-specific types in service-layer, Backoffice, storefront, or OCC contexts, even when HAC/admin existence queries return rows.
- Solr is an indexed snapshot. Product/category rows and permissions can be correct while storefront search still misses data because the active Solr config, indexed type, indexed properties, catalog version, language/currency/session context, or index freshness is wrong.
- Diagnose visibility by proving each layer in order: effective principal groups, catalog-version principals, category allowed principals, CMS restrictions, search restrictions, item catalog/version and approval status, category/product relation, sync, Solr configuration/index freshness, then storefront/facade behavior.

## Type system investigation model

SAP Commerce storage is defined through composed types, attributes, and relations, not by hand-authored SQL tables alone.

- `ComposedType` tells you the type code, owning extension, and parent type. Use it to answer “what does this type extend?” before assuming a project-specific object is a product, CMS item, order entry, or custom root type.
- `AttributeDescriptor` tells you which qualifiers are valid for FlexibleSearch, ImpEx, and model access. Qualifiers are the source of truth for query generation; physical column names are secondary implementation detail.
- `RelationDescriptor` and `*-items.xml` relation definitions tell you relation names, source and target qualifiers, cardinality, collection type, ordering, and deployment table when one is declared.
- A relation is not always a direct business relationship. For example, `Warehouse` is commonly reached from `CMSSite` through `CMSSite/BaseSite -> BaseStore -> Warehouse`, not by a direct `CMSSite -> Warehouse` relation.
- Subtypes inherit parent attributes and storage semantics. When a specific type exists, first identify its parent chain, then inspect only the additional attributes/relations introduced by the subtype.

Reusable discovery queries:

```sql
SELECT {ct.code}, {ct.extensionName}, {parent.code}, {ct.jaloclass}
FROM {ComposedType AS ct LEFT JOIN ComposedType AS parent ON {ct.superType} = {parent.pk}}
WHERE {ct.code} = '<TypeCode>'
```

```sql
SELECT {child.code}, {child.extensionName}, {parent.code}
FROM {ComposedType AS child JOIN ComposedType AS parent ON {child.superType} = {parent.pk}}
WHERE {parent.code} = '<ParentTypeCode>'
  OR {child.code} LIKE '%<NamePart>%'
ORDER BY {parent.code}, {child.code}
```

```sql
SELECT {ad.qualifier}, {ad.attributeType}, {ad.unique}, {ad.partOf}, {ad.collection}
FROM {AttributeDescriptor AS ad JOIN ComposedType AS ct ON {ad.enclosingType} = {ct.pk}}
WHERE {ct.code} = '<TypeCode>'
ORDER BY {ad.qualifier}
```

If a qualifier such as `{ct.superType}` or `{ad.collection}` is not available in a given runtime, simplify the query to core fields and inspect `items.xml` for the richer definition.

## Stable core responsibilities

- `BaseSite` is the storefront/API site identifier used by site resolution, OCC, search, carts, cron jobs, and session context. In accelerator-style systems it is often backed by `CMSSite`, which adds CMS catalog and preview configuration.
- `CMSSite` commonly owns `stores(uid)`, `contentCatalogs(id)`, `productCatalogs(id)` or `defaultCatalog(id)`, `classificationCatalogs(id)`, `defaultLanguage`, `urlPatterns`, `active`, `startingPage`, and preview settings. Confirm the actual qualifiers because some projects expose these through `BaseSite`, `CMSSite`, or custom extensions.
- `BaseStore` groups commerce behavior for one or more sites: product catalogs, supported/default currency, supported/default language, warehouses, payment/delivery/tax configuration, order process settings, and stock behavior.
- `Warehouse` is commonly associated to one or more `BaseStore` rows through `BaseStore2WarehouseRel`; `CMSSite`/`BaseSite` reaches warehouses through its related base stores. `PointOfService` can also relate to warehouses for location-specific stock flows.
- `StockLevel` is the inventory fact row. It extends `GenericItem`, is deployed to `StockLevels`, and stores `productCode`, `warehouse`, `available`, `reserved`, `overSelling`, `preOrder`, `maxPreOrder`, `inStockStatus`, and related timing/status fields. Stock is keyed by product code plus warehouse context, not by catalog version.
- `CronJob` is a persisted composed type, not just an in-memory scheduler concept. In the platform processing extension it extends `GenericItem` and is deployed to the `CronJobs` table; concrete jobs such as Solr indexer cron jobs, sync cron jobs, import jobs, and custom jobs extend `CronJob`.
- `Catalog` is the container. `ProductCatalog`, `ContentCatalog`, and `ClassificationSystem` are specialized catalog families with different runtime roles.
- `CatalogVersion` is the real scope for catalog-aware data. Products, categories, CMS items, media, sync jobs, and classification system data must be validated with catalog and version together, never catalog ID alone.
- `ContentCatalog` extends `Catalog`. CMS content is not owned directly by `BaseStore`; a `CMSSite` ties storefront site context to one or more content catalogs through `CatalogsForCMSSite`, while BaseStore supplies commerce context such as product catalogs, currencies, languages, warehouses, and checkout behavior.
- `CMSItem` is the catalog-versioned base type for CMS data. Its stable key is `uid + catalogVersion`; concrete CMS pages, slots, components, navigation nodes, navigation entries, and restrictions inherit this catalog-version scope.
- `AbstractPage` extends `CMSItem` and carries page state such as `approvalStatus`, `pageStatus`, `masterTemplate`, `defaultPage`, and restriction behavior. `ContentPage` adds page routing identifiers such as `label` and `homepage`; `CategoryPage`, `ProductPage`, and `CatalogPage` are page specializations used for catalog-driven page types.
- `PageTemplate` extends `CMSItem`. Template-level slots are not stored as a direct list on the page template table; they are `ContentSlotForTemplate` CMS relation rows with `pageTemplate`, `contentSlot`, `position`, and `allowOverwrite`.
- Page-level slot overrides are `ContentSlotForPage` CMS relation rows with `page`, `contentSlot`, and `position`. This is the data-model bridge from a page to the slots it explicitly owns or overrides.
- `ContentSlot` extends `CMSItem` and holds active/date metadata. Slot contents are connected through the `ElementsForSlot` relation, where `ContentSlot.slots` relates to ordered `AbstractCMSComponent.cmsComponents`.
- `AbstractCMSComponent` extends `CMSItem`; concrete components such as link, image, paragraph, banner, container, navigation, and project-specific components inherit catalog-versioned CMS behavior. Components can have their own visibility and restrictions independent of the containing page or slot.
- `CMSNavigationNode` extends `CMSItem` and forms a tree through `CMSNavigationNodeChildren` (`parent` to ordered `children`). `CMSNavigationEntry` extends `CMSItem` and points at a generic `Item`, so an entry can target a content page, category, product, link-like CMS item, or other supported item depending on project data and rendering logic.
- CMS category/product relationships are usually navigation or restriction relationships, not ownership of the category/product itself. Categories and products remain product-catalog data; CMS entries/components/restrictions reference them from the content catalog context.
- CMS restrictions are CMS catalog-versioned items. Pages use `RestrictionsForPages`; components use `RestrictionsForComponents`; concrete restriction relations include `CategoriesForRestriction`, `ProductsForRestriction`, `UsersForRestriction`, `UserGroupsForRestriction`, `CatalogsForRestriction`, and `StoreTimeRestriction2BaseStore` for BaseStore-aware time restrictions.
- `Category` is catalog-versioned grouping data used for merchandising, navigation, classification inheritance, and product organization. Category hierarchy uses `CategoryCategoryRelation`, deployed to `Cat2CatRel`, with `supercategories` on the parent side and `categories` on the child side.
- `Category2PrincipalRelation` connects `Category.accessibleCategories` to `Principal.allowedPrincipals` and is deployed to `Cat2PrincRel`. This is category-level access metadata, not a general product permission model.
- `CategoryProductRelation` connects categories to products and is deployed to `Cat2ProdRel`. The category-side qualifier is `supercategories`; the product-side qualifier is `products`. In practice this means `Product.supercategories` and `Category.products` are relation-backed, catalog-version-sensitive relationships.
- Product visibility in a category/listing depends on more than `Product` existence. Validate that the product exists in the expected catalog version, the category exists in the expected catalog version, the relation exists in the expected version, the data has been synced to Online when storefronts need Online, and search/navigation layers have been refreshed when applicable.
- `ClassificationSystem` extends `Catalog`; `ClassificationSystemVersion` extends `CatalogVersion`. A classification catalog is therefore catalog-versioned, but it describes attribute metadata rather than sellable products or CMS content.
- `ClassificationClass` extends `Category` and lives in a `ClassificationSystemVersion`. It groups the classification attributes that can apply to products assigned to that class.
- `ClassAttributeAssignment` is the central definition row for a classified attribute on a class. It links `classificationClass` to `classificationAttribute`, belongs to a `systemVersion`, and carries behavior flags such as `mandatory`, `localized`, `multiValued`, `range`, `searchable`, `listable`, `comparable`, `visibility`, `attributeType`, `unit`, and allowed attribute values.
- `ClassificationAttribute` is the attribute definition, keyed by `code` within a `ClassificationSystemVersion`. `ClassificationAttributeValue` is an allowed value, also keyed by `code` within the system version. `AttributeValueAssignment` can constrain allowed values for an assignment.
- `ProductFeature` is the actual product classification value row. It is attached to `Product` by `Product2FeatureRelation`, points to `classificationAttributeAssignment`, and stores typed values in fields such as `stringValue`, `numberValue`, `booleanValue`, raw/dynamic `value`, `unit`, `language`, `valuePosition`, and `featurePosition`.
- `Product` is catalog-versioned master data. It becomes a cart line or order line only through an order entry; do not look for the cart/order relationship directly on the product when diagnosing checkout data.
- `Product.variantType` marks a product as the base for a variant structure and identifies the variant product type expected below it. A base product may be the search/listing/PDP parent, but whether it is purchasable is project and facade dependent.
- `VariantProduct` extends `Product`. It is still catalog-versioned product data with its own code and attributes, and it links back to the base product through `baseProduct`.
- `Product2VariantRelation` is the base-to-variant relation. The source qualifier is `baseProduct` on the `Product` side, and the target qualifier is `variants` on the `VariantProduct` side. The target side is `partof`, so variants are owned by the base product relation in the platform model.
- `GenericVariantProduct` extends `VariantProduct` and adds no descriptive variant dimension attributes by itself. Project or accelerator-specific variant types can extend `VariantProduct` to add dimensions such as style, size, package option, room option, or other domain-specific selection attributes.
- Do not infer variant structure from product code naming. Query `Product.variantType`, `VariantProduct.baseProduct`, the concrete item type, and the relevant catalog version before explaining PDP, add-to-cart, stock, or search behavior.
- `AbstractOrder` is the shared header type for carts and orders. Core `Cart` extends `AbstractOrder` and deploys to `Carts`; core `Order` extends `AbstractOrder` and deploys to `Orders`.
- `AbstractOrderEntry` is the shared entry type for cart and order lines. Core `CartEntry` extends `AbstractOrderEntry` and deploys to `CartEntries`; core `OrderEntry` extends `AbstractOrderEntry` and deploys to `OrderEntries`.
- `AbstractOrder2AbstractOrderEntry` connects order headers to entries. The header sees an `entries` list, and each entry points back through `order`. Entries carry the `product`, `quantity`, `unit`, `basePrice`, and `totalPrice` snapshot used for cart/order diagnosis.
- Product and content catalogs usually have `Staged` and `Online`; authoring and imports usually target `Staged`, then sync to `Online`. Local validation may intentionally query both.
- Classification systems usually use a version such as `1.0`; products refer to classification classes and features through classification assignments and system versions.
- `Warehouse` is inventory location/configuration. `StockLevel` is not catalog-versioned; it references product code and warehouse, so a stock query must tie the product code back to the intended product catalog version separately.
- `PointOfService` can connect store-facing locations to warehouses and addresses. Validate it when pickup, store locator, or location-based inventory is involved.
- `SolrFacetSearchConfig` and `SolrIndexedType` determine what a site can search. Site-level Solr attributes may be custom, such as separate product and hotel Solr configurations.
- `CatalogVersionSyncJob` connects source and target catalog versions. Do not assume Staged-to-Online sync exists just because both versions exist.
- `Principal` is the core security identity. `User` extends `Principal`; `PrincipalGroup` extends `Principal`; `UserGroup` extends `PrincipalGroup`. Groups can represent storefront/customer grouping, Backoffice/cockpit access, B2B roles, catalog permissions, and restriction context depending on the project.
- `PrincipalGroupRelation` connects `Principal.members` to `PrincipalGroup.groups`. Use it for direct membership and remember that dynamic attributes such as `allGroups` can include inherited/nested group effects.
- `CatalogVersion.readPrincipals` and `CatalogVersion.writePrincipals` are relation-backed catalog-version access lists through `Principal2ReadableCatalogVersionRelation` and `Principal2WriteableCatalogVersionRelation`. They answer who can read or write that catalog version, not whether a storefront chooses to display an item.
- `SearchRestriction` is a persisted type-level restriction with `code`, `active`, `principal`, `restrictedType`, and `query`. It is connected to principals through `Principal2SearchRestrictionRelation`; service-layer and FlexibleSearch execution may inject these filters depending on session context and restriction state.
- Products and CMS items usually do not carry direct per-row user-group permissions in the core model. Product, category, and content visibility is layered: catalog-version principal access, category allowed principals when used, search restrictions, approval/status, catalog sync, Solr/index state, CMS restrictions/personalization, and storefront/facade business logic.
- HAC/admin queries prove data existence, not customer or storefront visibility. To diagnose visibility for a user or group, query principal groups, catalog-version principals, search restrictions, item catalog version, category/product relations, approval/sync/index state, and the storefront session context.
- Search is catalog-version scoped configuration plus indexed snapshots. `SolrFacetSearchConfig` extends `GenericItem` and relates to `CatalogVersion`, `Currency`, `Language`, `SolrIndexedType`, and `SolrIndexerCronJob`. The config tells the platform which catalog versions and indexed types belong together for a search domain.
- `SolrIndexedType` extends `GenericItem` and points to the Commerce `ComposedType` being indexed through its `type` attribute. It also carries index behavior such as `identifier`, `variant`, `identityProvider`, `modelLoader`, `defaultFieldValueProvider`, `valuesProvider`, `indexName`, result converter, grouping, and query builder configuration.
- `SolrIndexedProperty` extends `GenericItem` and belongs to a `SolrIndexedType` through `solrIndexedType`. It defines a field in the index, not a live database join. Important data-model flags include `name`, `type`, `localized`, `currency`, `multiValue`, `fieldValueProvider`, `valueProviderParameter`, `classAttributeAssignment`, `categoryField`, `facet`, `facetType`, `visible`, response/autocomplete/spellcheck flags, priority, range sets, and facet display/sort providers.
- Category search works by indexing product/category data into fields. Categories contribute through `CategoryProductRelation`, category hierarchy, category metadata, and category-related `SolrIndexedProperty` rows such as properties with `categoryField=true` or category field value providers. Solr result filtering/navigation then reads indexed fields; it does not dynamically join `CategoryProductRelation` at request time.
- Classification attributes become searchable or facetable only when the classification model is present and the indexed property or field value provider is configured to emit those values. A `ClassAttributeAssignment.searchable=true` flag alone does not prove the value is in the active index.
- Basic facet behavior belongs to `SolrIndexedProperty`: `facet=true` makes a property participate as a facet, `facetType` controls selection behavior, and `visible` controls whether the facet is intended for frontend users. Core facet type values are `Refine`, `MultiSelectAnd`, and `MultiSelectOr`.
- Search visibility is layered. Even when a product is indexed, the storefront/OCC result can still be filtered or shaped by catalog version access, category allowed principals, active `SearchRestriction` rows, approval/status, sync state, stale index data, language/currency/session catalog context, adaptive search, and facade/business logic.

## Implementation evidence patterns

Look for these ImpEx headers when reconstructing a site implementation:

```impex
INSERT_UPDATE BaseStore; uid[unique = true]
INSERT_UPDATE CMSSite; uid[unique = true]; stores(uid); contentCatalogs(id); defaultCatalog(id); defaultLanguage(isoCode); active
INSERT_UPDATE ContentCatalog; id[unique = true]
INSERT_UPDATE ProductCatalog; id[unique = true]
INSERT_UPDATE ClassificationSystem; id[unique = true]
INSERT_UPDATE CatalogVersion; catalog(id)[unique = true]; version[unique = true]; active; languages(isoCode)
INSERT_UPDATE Category; code[unique = true]; catalogVersion(catalog(id),version)[unique = true]; supercategories(code,catalogVersion(catalog(id),version))
INSERT_UPDATE Product; code[unique = true]; catalogVersion(catalog(id),version)[unique = true]; supercategories(code,catalogVersion(catalog(id),version)); variantType(code)
INSERT_UPDATE GenericVariantProduct; code[unique = true]; catalogVersion(catalog(id),version)[unique = true]; baseProduct(code,catalogVersion(catalog(id),version))
INSERT_UPDATE ClassificationSystemVersion; catalog(id)[unique = true]; version[unique = true]; active; languages(isoCode)
INSERT_UPDATE ClassificationClass; code[unique = true]; catalogVersion(catalog(id),version)[unique = true]
INSERT_UPDATE ClassificationAttribute; code[unique = true]; systemVersion(catalog(id),version)[unique = true]
INSERT_UPDATE ClassificationAttributeValue; code[unique = true]; systemVersion(catalog(id),version)[unique = true]
INSERT_UPDATE ClassAttributeAssignment; classificationClass(code,catalogVersion(catalog(id),version))[unique = true]; classificationAttribute(code,systemVersion(catalog(id),version))[unique = true]; systemVersion(catalog(id),version)[unique = true]; attributeType(code); localized; multiValued; searchable; listable; comparable
INSERT_UPDATE ProductFeature; product(code,catalogVersion(catalog(id),version))[unique = true]; qualifier[unique = true]; classificationAttributeAssignment(classificationClass(code,catalogVersion(catalog(id),version)),classificationAttribute(code,systemVersion(catalog(id),version)),systemVersion(catalog(id),version)); valuePosition[unique = true]; stringValue; numberValue; booleanValue; unit(code,systemVersion(catalog(id),version))
INSERT_UPDATE UserGroup; uid[unique = true]
INSERT_UPDATE User; uid[unique = true]; groups(uid)
INSERT_UPDATE CatalogVersion; catalog(id)[unique = true]; version[unique = true]; readPrincipals(uid); writePrincipals(uid)
INSERT_UPDATE Category; code[unique = true]; catalogVersion(catalog(id),version)[unique = true]; allowedPrincipals(uid)
INSERT_UPDATE SearchRestriction; code[unique = true]; principal(uid); restrictedType(code); active; query
INSERT_UPDATE Warehouse; code[unique = true]
INSERT_UPDATE StockLevel; productCode[unique = true]; warehouse(code)[unique = true]
INSERT_UPDATE Cart; code[unique = true]; user(uid); currency(isocode); entries(product(code),quantity,unit(code))
INSERT_UPDATE CartEntry; order(code)[unique = true]; entryNumber[unique = true]; product(code); quantity; unit(code)
INSERT_UPDATE Order; code[unique = true]; user(uid); currency(isocode); status(code); entries(product(code),quantity,unit(code))
INSERT_UPDATE OrderEntry; order(code)[unique = true]; entryNumber[unique = true]; product(code); quantity; unit(code)
INSERT_UPDATE SolrFacetSearchConfig; name[unique = true]; solrIndexedTypes(identifier); catalogVersions(catalog(id),version)
INSERT_UPDATE SolrIndexedType; identifier[unique = true]; type(code); solrFacetSearchConfig(name); indexName; defaultFieldValueProvider
INSERT_UPDATE SolrIndexedProperty; name[unique = true]; solrIndexedType(identifier)[unique = true]; type(code); facet; facetType(code); visible; categoryField; fieldValueProvider; valueProviderParameter; localized; multiValue
INSERT_UPDATE ContentCatalog; id[unique = true]
INSERT_UPDATE CMSItem; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]
INSERT_UPDATE ContentPage; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; label; masterTemplate(uid,catalogVersion(catalog(id),version)); defaultPage; homepage; approvalStatus; pageStatus
INSERT_UPDATE PageTemplate; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; active; frontendTemplateName
INSERT_UPDATE ContentSlot; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; active; cmsComponents(uid,catalogVersion(catalog(id),version))
INSERT_UPDATE ContentSlotForTemplate; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; pageTemplate(uid,catalogVersion(catalog(id),version)); contentSlot(uid,catalogVersion(catalog(id),version)); position; allowOverwrite
INSERT_UPDATE ContentSlotForPage; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; page(uid,catalogVersion(catalog(id),version)); contentSlot(uid,catalogVersion(catalog(id),version)); position
INSERT_UPDATE CMSNavigationNode; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; parent(uid,catalogVersion(catalog(id),version)); children(uid,catalogVersion(catalog(id),version)); entries(uid,catalogVersion(catalog(id),version)); visible
INSERT_UPDATE CMSNavigationEntry; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; item(&ItemRef)
INSERT_UPDATE CMSCategoryRestriction; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; categories(code,catalogVersion(catalog(id),version)); recursive
INSERT_UPDATE CMSProductRestriction; uid[unique = true]; catalogVersion(catalog(id),version)[unique = true]; products(code,catalogVersion(catalog(id),version))
INSERT_UPDATE CatalogVersionSyncJob; code[unique = true]; sourceVersion(catalog(id),version); targetVersion(catalog(id),version)
```

Project ImpEx often sets a minimal `BaseStore` first, then wires the richer site through `CMSSite; stores(uid); contentCatalogs(id); defaultCatalog(id)`. Updates to `BaseSite` may follow for project attributes such as visitor info, reference order IDs, Solr configuration, or hotel-specific configuration.

## Diagnostic order

When asked for a site or store configuration, gather facts in this order:

1. Resolve exact `BaseSite` or `CMSSite` by `uid`; include channel, language, active flag if available, URL patterns, and custom project flags.
2. Resolve related `BaseStore` rows; include default/supported currency and language, warehouses, and product catalogs.
3. Resolve content catalogs and catalog versions; include active flags and sync jobs.
4. Resolve product catalogs and catalog versions; include active flags, sync jobs, and whether products/categories exist in the expected version.
5. Resolve classification catalogs/system versions; include classification class/assignment presence when product facets or attributes matter.
6. Resolve warehouse and stock wiring; query `StockLevel` by product code plus warehouse, then separately prove that product code exists in the intended product catalog version.
7. Resolve access context when visibility differs by user or channel: user groups, catalog-version read/write principals, category allowed principals when used, and active search restrictions for the relevant principal/group/type.
8. Resolve Solr configuration, indexed types, indexed properties, and index CronJobs for the site/domain.
9. Only then diagnose storefront/OCC behavior, because failures often come from a missing link rather than a missing row.

## Query generation rules

- Start from the object the user named, but report the connected chain that controls runtime behavior.
- Confirm qualifiers with `AttributeDescriptor` before using project-specific attributes such as `hotelSolrFacetSearchConfiguration`, `productIndexType`, or `requiresReferenceOrderId`.
- Prefer exact keys after discovery: site `uid`, store `uid`, catalog `id`, catalog version, warehouse `code`, Solr config `name`, indexed type `identifier`.
- Do not infer stock availability from product existence. Query `StockLevel` and warehouse wiring.
- Do not infer content availability from CMS item existence. Query the site content catalog, catalog version, and active/sync state.
- Do not infer CMS page availability from a `ContentPage` row alone. Query `CMSSite.contentCatalogs`, content catalog version, page status/approval/template, page/template slots, slot components, component visibility/restrictions, and sync state.
- Do not infer that a category or product is part of CMS because navigation points to it. Query the CMS navigation entry/component/restriction in the content catalog and separately query the category/product in the product catalog version.
- Do not infer product listing visibility from product existence. Query `CategoryProductRelation`, category/product catalog versions, sync state, and search/index state when relevant.
- Do not infer product search/listing visibility from product existence or category assignment alone. Query the `SolrFacetSearchConfig` catalog versions, `SolrIndexedType`, `SolrIndexedProperty` rows, index CronJob history, category/product relations, restrictions, sync, and the storefront session context.
- Do not infer variant structure from product code naming. Query `Product.variantType`, `VariantProduct.baseProduct`, concrete variant type, and catalog version.
- Do not infer classification availability from product catalog existence. Query the classification system version and classification assignments/features.
- Do not treat classification attributes as direct `Product` attributes. Query `ClassAttributeAssignment` for the definition and `ProductFeature` for actual product values.
- Do not infer customer/storefront visibility from HAC/admin row existence. Query principal group membership, catalog-version `readPrincipals`, category `allowedPrincipals` when present, active `SearchRestriction` rows, approval/status, sync, Solr, and session context.
- Do not assume direct product allowed-groups unless project source or runtime metadata proves a custom attribute/relation. In core Commerce, product access is usually controlled indirectly through catalog version, category, search restrictions, and storefront logic.

## ImpEx generation rules

- Build core implementation data in dependency order: languages/currencies/enums, catalogs, catalog versions, base stores, sites, warehouses, stock, Solr, sync/index jobs, products/features/content, then carts/orders only when the task explicitly needs transactional data.
- Use macros for `$siteUid`, `$storeUid`, `$productCatalog`, `$contentCatalog`, `$classificationCatalog`, `$productCV`, `$contentCV`, `$warehouse`, and `$defaultLanguage`.
- For category/product imports, create the category and product in the intended catalog version, then assign the relation through `Product.supercategories` or a confirmed relation header. Keep Staged and Online explicit.
- For variant imports, create the base product with `variantType(code)`, then create concrete `VariantProduct` rows such as `GenericVariantProduct` with `baseProduct(code,catalogVersion(...))` in the same intended product catalog version unless the project model proves otherwise.
- For classification imports, create the classification system/version before classes, attributes, units, allowed values, class assignments, product-to-class/category relations, and product features.
- Keep `BaseStore` creation separate from `CMSSite`/`BaseSite` enrichment when following project patterns.
- Use relation columns or relation headers only after confirming the owning qualifier from items.xml or the runtime type system.
- Validate every reference with FlexibleSearch before live import when the file assumes pre-existing catalogs, stores, warehouses, Solr configs, jobs, or enum codes.
- Treat cart and order imports as exceptional. Carts and orders are transactional artifacts with calculated totals, payment/delivery/user context, and process side effects; prefer ServiceLayer or approved test-data patterns over hand-authored ImpEx unless the purpose is controlled local fixture data.

## Groovy generation rules

- Use services that understand the object graph: `baseSiteService`, `baseStoreService` when present, `catalogVersionService`, classification services where available, `commerceStockService` or stock services where available, `commerceCartService`, `commerceCheckoutService`, `flexibleSearchService`, `modelService`, and `cronJobService`.
- Treat site/store/catalog model getters as read-only graph traversal. Copy returned collections before sorting or reshaping them; do not mutate model relations during diagnostics unless the script is an approved repair.
- Set site, catalog version, language, and currency session context deliberately when a script depends on storefront behavior.
- For stock or Solr actions, validate site-store-catalog-warehouse/index wiring first, then trigger or mutate the narrowest target.
- For repairs, print the full chain being changed before saving: site, store, catalog version, warehouse, Solr config, or sync/index job code.

## Common failure signatures

- Site exists but storefront/OCC fails: missing store relation, inactive CMS site, URL pattern mismatch, missing default language/currency, or missing session catalog versions.
- Products exist but are not visible: wrong catalog version, unsynced Staged-to-Online data, category relation in the wrong version, missing approval/status, or Solr index not current.
- Products/categories exist for admin but not for a user: missing group membership, missing catalog-version read principal, category `allowedPrincipals` mismatch, active `SearchRestriction`, wrong session catalog versions, missing approval/sync, or stale Solr index.
- CMS items exist but are not visible: wrong content catalog/version, inactive page/component restrictions, missing starting page, or unsynced content catalog.
- Product facets/attributes missing: classification system version not wired, assignments missing, indexed properties missing, or Solr index not current.
- Stock unavailable: no `StockLevel` for product code and warehouse, site store not linked to the warehouse, product code mismatch, or stock service threshold/status settings.
- Search empty: site points to wrong Solr config/indexed type, Solr config uses different catalog versions, indexed type points to the wrong composed type, category/product data is missing from the indexed catalog version, active restrictions filter the type, CronJob never ran, or index is stale.
- Facets missing: the property is absent from the indexed type, `facet=false`, `visible=false`, wrong `facetType`, missing field value provider, classification assignment not populated, category field not configured, or the index has not been rebuilt since the configuration/data changed.
