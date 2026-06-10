# 0006 — Demo data isolated in `sampledatamcp`; dual-import with a sync job for authoring

**Status:** accepted

## Context

The project needs rich demo data (catalog, customers, orders, knowledge base,
OAuth clients) without contaminating production deployments, and the catalog has
Staged + Online versions that must both be populated at bootstrap. A classic
staged→online sync during initialization is order-sensitive: demo orders
reference Online products in the same projectdata pass, so Online must be
populated before orders import — a sync step can't run mid-ImpEx.

## Decision

1. **Isolation:** every demo artifact — products, prices, stock, media, customers
   (password `1234`), orders, knowledge entries, demo OAuth clients — lives in the
   data-only `sampledatamcp` extension. Production excludes the extension from
   `localextensions.xml`, removing all demo data and credentials in one move.
2. **Bootstrap by dual-import:** product ImpEx writes both Staged and Online
   versions directly, keeping initialization deterministic and order-safe.
3. **Authoring by sync:** a standard `CatalogVersionSyncJob`
   (`sync-electronicsProductCatalog-Staged-Online`) is provided for ongoing
   changes — edit Staged, run the sync — so the demo also shows the proper
   catalog workflow.
4. **Explicit load order:** ImpEx files carry numeric prefixes
   (`essentialdata-00-…`, `projectdata-10-…`) because the platform loads them
   alphabetically and implicit ordering had a latent bug (product media imported
   before products on first initialization).

## Consequences

- One `localextensions.xml` line separates "demo" from "production-capable".
- Dual-import means demo data changes touch two blocks per file; the sync job
  covers incremental authoring without re-import.
- Numeric prefixes are now the convention for any new ImpEx in this repository.
