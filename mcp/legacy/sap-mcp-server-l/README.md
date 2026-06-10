# SAP Commerce MCP Server

An SAP Commerce (Hybris) 22.11 extension that implements a [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server, enabling AI agents to interact with SAP Commerce through a standardized JSON-RPC interface. Structured for CCv2 (Commerce Cloud v2) deployment.

## Features

- **MCP Protocol:** Full JSON-RPC 2.0 implementation — 19 commerce tools, DB-persisted cluster-safe sessions (survive restarts and multi-node routing), tool registration
- **Commerce Tools:** Product search (keyword + category facets), cart and voucher management, checkout flow, order history, customer lookup, promotions, knowledge base
- **Agent Service:** Multi-provider LLM agent (OpenAI / Anthropic / OpenAI-compatible) with tool calling, SSE streaming, transient-failure retry, and per-user rate limiting
- **Visual Search:** Vision-model image analysis with 3-tier catalog matching
- **Sample Data:** Electronics + merch catalog (21 products incl. an out-of-stock edge case), categories, pricing, stock, images, customers, orders, knowledge base, promotions
- **Solr Search:** Full-text product + knowledge search with category/price/stock facets and sort options
- **Gradle Build:** Full lifecycle management via the SAP Commerce Gradle Plugin (no `ant` or `setantenv.sh` needed)
- **Governance:** [SECURITY.md](SECURITY.md) (secrets policy + production checklist), [docs/adr/](docs/adr/) (architecture decisions), [docs/review/](docs/review/) (architecture review + improvement plan), end-to-end smoke test (`scripts/smoke-test.sh`)

## Prerequisites

- **Java 17** (SAP Commerce 22.11 requirement) — managed via `gradle.properties`
- **MySQL 8.0** running locally (or Docker)
- **SAP Commerce 2211 ZIP** + Integration Extension Pack placed in `core-customize/dependencies/`
  - Rename to Maven convention: `hybris-commerce-suite-<version>.zip` and `hybris-commerce-integrations-<version>.zip`
  - Versions must match `commerceSuiteVersion` in `manifest.json` and `intExtPackVersion` in `build.gradle`
- Ports available: 9001 (HTTP), 9002 (HTTPS), 8983 (Solr)

## Quick Start

```bash
cd core-customize

# 1. Bootstrap platform (unpack zips, set up config)
./gradlew bootstrapPlatform

# 2. Build all extensions
./gradlew yclean yall

# 3. Initialize database and load sample data
./gradlew yinitialize

# 4. Start the server
./gradlew startServer

# 5. Index Solr (wait ~30s for server to fully start)
./scripts/index-solr.sh

# 6. Set up promotions
./scripts/setup-promotions.sh
./gradlew groovy -Pfile=scripts/publish-promotions.groovy -Pcommit=true

# 7. Verify everything end to end (21 checks: OAuth, MCP, search, cart, agent)
./scripts/smoke-test.sh
```

## Repository Structure

```
sap-mcp-server-l/
├── core-customize/
│   ├── build.gradle                  # Gradle build config (all tasks defined here)
│   ├── settings.gradle
│   ├── gradle.properties             # Java 17 path (SDKMAN)
│   ├── gradlew / gradlew.bat        # Gradle wrapper
│   ├── manifest.json                 # CCv2 build configuration
│   ├── dev-config/                   # Project config (overlaid onto hybris/config)
│   │   ├── local.properties
│   │   ├── local-dev.properties
│   │   ├── local-stg.properties
│   │   ├── local-prod.properties
│   │   └── localextensions.xml
│   ├── dependencies/                 # SAP Commerce ZIP files (not checked in)
│   │   ├── hybris-commerce-suite-<version>.zip
│   │   └── hybris-commerce-integrations-<version>.zip
│   ├── scripts/                      # Automation scripts
│   │   ├── hac-groovy.sh            # Run Groovy via HAC
│   │   ├── hac-impex.sh             # Run ImpEx via HAC
│   │   ├── hac-flexquery.sh         # Run FlexibleSearch via HAC
│   │   ├── index-solr.sh            # Solr reindex
│   │   ├── index-solr.groovy
│   │   ├── setup-promotions.sh      # Create promotions
│   │   ├── publish-promotions.groovy # Publish promotions to Drools
│   │   ├── mcp-stdio-bridge.py      # Stdio↔HTTP bridge for MCP clients (.mcp.json)
│   │   ├── smoke-test.sh            # 21-check end-to-end suite vs live server
│   │   └── test-mcp-e2e.py          # Protocol-level MCP e2e (incl. session DELETE)
│   └── hybris/
│       ├── bin/custom/               # Custom extensions (checked in)
│       └── config/                   # Generated — do not check in
├── docs/
├── .claude/skills/
├── CLAUDE.md
└── README.md
```

## Gradle Tasks

All commands run from `core-customize/`.

### Bootstrap & Build

| Command | Purpose |
|---------|---------|
| `./gradlew bootstrapPlatform` | Unpack commerce ZIPs, install DB driver, set up config |
| `./gradlew setupConfig` | Regenerate config (platform defaults + dev-config overlay) |
| `./gradlew cleanAll` | Wipe everything except `hybris/bin/custom`, `dev-config`, `dependencies`, `scripts` |
| `./gradlew yclean yall` | Clean build (ant clean all) |
| `./gradlew yinitialize` | Initialize database (destroys existing data) |
| `./gradlew yupdatesystem` | Update system (preserves data, applies schema changes) |

### Server

| Command | Purpose |
|---------|---------|
| `./gradlew startServer` | Start in background |
| `./gradlew stopServer` | Stop |
| `./gradlew serverStatus` | Check if running |
| `./gradlew serverLog` | Tail console log |
| `./gradlew solrLog` | Tail Solr log |
| `./gradlew allLogs` | Tail both |

### HAC Console

| Command | Purpose |
|---------|---------|
| `./gradlew groovy -Pfile=<path> [-Pcommit=true]` | Run Groovy script via HAC |
| `./gradlew impex -Pfile=<path>` | Run ImpEx import via HAC |
| `./gradlew flexquery -Pfile=<path\|query> [-PmaxResults=50]` | Run FlexibleSearch query via HAC |

## MCP Server Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/occ/v2/{baseSiteId}/mcp` | POST | MCP JSON-RPC endpoint |
| `/occ/v2/{baseSiteId}/agent` | POST | AI agent endpoint |

## Available Tools

| Tool | Description |
|------|-------------|
| `product_search` | Search products with full-text queries, facets, and sorting |
| `product_get` | Get detailed product information by code |
| `cart_get` | Get current cart contents |
| `cart_add_product` | Add a product to the cart |
| `cart_update_entry` | Update cart entry quantity |
| `cart_remove_entry` | Remove an entry from the cart |
| `checkout_set_delivery_address` | Set delivery address for checkout |
| `checkout_set_delivery_mode` | Set delivery mode |
| `checkout_set_payment` | Set payment method |
| `order_place` | Place the order |
| `order_get` | Get order details |
| `order_history` | Get order history |
| `customer_get` | Get customer profile |
| `customer_lookup` | Look up customer by email |

## Sample Data

### Test Users

| Email | Password |
|-------|----------|
| john.doe@thinkshop.com | 1234 |
| jane.smith@thinkshop.com | 1234 |
| bob.wilson@thinkshop.com | 1234 |

### OAuth Clients

| Client ID | Client Secret |
|-----------|--------------|
| trusted_client | secret |
| mobile_android | secret |

## Access Points

| URL | Purpose | Credentials |
|-----|---------|-------------|
| https://localhost:9002/hac | Admin Console | admin / nimda |
| https://localhost:9002/backoffice | Backoffice Admin UI | admin / nimda |
| https://localhost:9002/occ/v2/ | OCC REST API | OAuth token |
| https://localhost:9002/occ/v2/swagger-ui.html | Swagger API Docs | — |

## Day-to-Day Development

| What Changed | Command |
|-------------|---------|
| Java source code | `./gradlew yclean yall` then restart |
| `*-items.xml` or `*-beans.xml` | `./gradlew yclean yall` then `./gradlew yupdatesystem` |
| Quick restart (no code changes) | `./gradlew stopServer startServer` |
| Full data reset | `./gradlew cleanAll bootstrapPlatform yclean yall yinitialize` |
| ImpEx data (manual import) | `./gradlew impex -Pfile=path/to/data.impex` |
| FlexibleSearch query | `./gradlew flexquery -Pfile="SELECT {pk} FROM {Product}"` |
| Groovy script | `./gradlew groovy -Pfile=path/to/script.groovy -Pcommit=true` |
