# project-knowledge/sap-commerce/

This project's SAP Commerce facts. The `sap-hac-access` skill checks here **first** when a
question is project-specific rather than generic platform behavior.

| File | Holds |
|---|---|
| `overview.md` | Version/runtime, custom extensions, where code lives, build + init commands |
| `catalogs-and-sites.md` | Catalog IDs, catalog versions, BaseSites, BaseStores, sync jobs |
| `types.md` | Custom item types, what they extend, key attributes, relations, business keys |
| `solr-and-cms.md` | Facet search configs, indexed types/properties, content catalog, page/slot conventions |

Add files as topics come up — `orders.md`, `integrations.md`, `pricing.md`. Keep one topic
per file so the skill can load only what it needs.

Every fact should be traceable: cite the `items.xml`, the ImpEx, the extension path, or the
FlexibleSearch query that proved it. Delete or correct entries when the project changes —
a stale overlay is worse than a missing one.
