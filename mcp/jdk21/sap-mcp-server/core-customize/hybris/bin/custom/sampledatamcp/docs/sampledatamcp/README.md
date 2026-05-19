# sampledatamcp

Data-only extension that bootstraps sample data for the MCP server. No custom types, no Spring beans, no Java code. Portable — drop into any SAP Commerce project with the required extensions.

For generic extension setup patterns, see the [project-level docs](../../../../docs/).

## Flows

Each subdirectory documents one data flow — what it creates, the files involved, and a diagram showing dependencies.

| Flow | Description |
|---|---|
| [store-infrastructure/](store-infrastructure/) | OAuth clients, catalog, store, delivery/payment modes, Solr faceted search config |
| [sample-data/](sample-data/) | 10 products with prices and stock, 3 customers with addresses, 3 orders |
| [promotions-setup/](promotions-setup/) | 5 promotion rules, 2 coupons, Drools publish (Groovy script, manual execution) |

## Load Order

SAP Commerce loads ImpEx files by filename pattern, then alphabetically within each pattern:

| Order | File | Pattern | When |
|---|---|---|---|
| 1 | `essentialdata-infrastructure.impex` | `essentialdata-*` | initialize + updatesystem |
| 2 | `essentialdata-solr.impex` | `essentialdata-*` | initialize + updatesystem |
| 3 | `projectdata-sampledatamcp.impex` | `projectdata-*` | initialize only |
| 4 | `setup-promotions.groovy` | (manual) | HAC Groovy console |

The alphabetical sort matters: `infrastructure` < `solr`, so catalog and currency exist before Solr config references them. Project data loads after all essential data, so the store, warehouse, and delivery modes are in place before products and orders are created.

## Adding a New Flow

When you add a new feature, create a new subdirectory here with three files:

```
docs/sampledatamcp/my-new-feature/
├── context.md       # What this flow does, when it's used, key decisions
├── components.md    # The files that implement it and what each one does
└── diagram.md       # How the pieces connect (Mermaid diagrams with context)
```
