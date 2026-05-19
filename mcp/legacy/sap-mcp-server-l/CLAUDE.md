# CLAUDE.md

This is the root configuration file for [Claude Code](https://claude.ai/code) in this SAP Commerce (Hybris) project. It teaches Claude how to build, run, test, and extend the platform — so it can assist effectively from the first message.

## What This Project Is

An SAP Commerce MCP (Model Context Protocol) server extension, structured for CCv2 (Commerce Cloud v2) deployment. The Commerce Suite version is defined in `core-customize/manifest.json` (`commerceSuiteVersion`).
- **Backend:** Custom OCC extension (`coremcp`) providing an MCP server for AI agent integration
- **Data:** Solr-indexed product catalog, OAuth2 auth, sample customers and orders
- **Layout:** CCv2-compliant repository structure with `core-customize/` root

## CCv2 Repository Structure

This project follows the SAP Commerce Cloud v2 mandatory layout:

```
sap-mcp-server-g/
├── core-customize/                    # CCv2 root — cloud builds start here
│   ├── manifest.json                  # Build/deployment configuration
│   └── hybris/
│       ├── bin/custom/
│       │   ├── coremcp/               # MCP server extension
│       │   └── sampledatamcp/         # Sample data extension
│       └── config/
│           ├── localextensions.xml    # Active extension declarations
│           ├── local.properties       # Runtime configuration
│           └── solr/                  # Solr server configuration
├── docs/                              # Development guides
├── .claude/skills/                    # Claude Code domain skills
├── CLAUDE.md                          # This file
└── README.md
```

The `manifest.json` declares the Commerce version and references config files. The cloud build system downloads the platform and modules automatically — they are not in the repository.

## How This Project Is Documented

| Layer | Location | Purpose |
|-------|----------|---------|
| **CLAUDE.md** (this file) | Project root | Commands, rules, paths — everything Claude needs to operate |
| **Skills** | `.claude/skills/` | Domain expertise that activates contextually (SAP Commerce dev, code review) |
| **Project docs** | `docs/` | Generic patterns: extension setup, new feature walkthrough, checklists |
| **Extension docs** | `core-customize/hybris/bin/custom/coremcp/docs/` | MCP server architecture, endpoints, tools, protocol |
| **Gradle tasks** | `core-customize/gradlew` | Server lifecycle, HAC console, and build operations (see Commands below) |

## Commands

All commands run from `core-customize/` using `./gradlew`.

### First-Time Setup

See `docs/getting-started.md` for the full walkthrough. Summary:

1. **Install Java 17** (SDKMAN: `sdk install java 17.0.12-oracle`)
2. **Start MySQL** (Docker or native — see getting-started.md)
3. **Download SAP Commerce ZIPs** from SAP Software Download Center and place in `core-customize/dependencies/`:
   - `hybris-commerce-suite-<version>.zip` (SAP Commerce Suite)
   - `hybris-commerce-integrations-<version>.zip` (Integration Extension Pack)

   The version should match `commerceSuiteVersion` in `core-customize/manifest.json`.
4. **Bootstrap, build, initialize, start:**
   ```bash
   cd core-customize
   ./gradlew bootstrapPlatform    # Unpack ZIPs, install MySQL driver, generate config
   ./gradlew yclean yall           # Build all extensions
   ./gradlew yinitialize           # Initialize database + load sample data
   ./gradlew startServer           # Start the server
   ./scripts/index-solr.sh         # Index Solr (required for product search)
   ./scripts/setup-promotions.sh   # Create promotions
   ./gradlew groovy -Pfile=scripts/publish-promotions.groovy -Pcommit=true  # Publish to Drools
   ```

### Server Lifecycle

| Command | When to Use |
|---------|-------------|
| `./gradlew stopServer startServer` | Quick restart (no build) |
| `./gradlew ybuild stopServer startServer` | After Java source changes |
| `./gradlew ybuild stopServer startServer yupdatesystem` | After `*-items.xml` changes (preserves data) |
| `./gradlew ybuild stopServer startServer` | After `*-beans.xml` changes |
| `./gradlew yclean yall yinitialize` | Full data reset (**destroys all data**) |

### HAC Console

| Command | Purpose |
|---------|---------|
| `./gradlew flexquery -Pfile=<path>` | Run FlexibleSearch queries |
| `./gradlew groovy -Pfile=<path> [-Pcommit=true]` | Execute Groovy scripts — rollback by default |
| `./gradlew impex -Pfile=<path>` | Run ImpEx imports |

**Tip:** For complex multi-line input, write to a temp file first (e.g., `/tmp/query.sql`) then pass the file path.

### Testing

```bash
./gradlew yunittests -Dtestclasses.extensions=coremcp        # Unit tests
./gradlew yintegrationtests -Dtestclasses.extensions=coremcp  # Integration tests
```

## Key Paths

| Path | Purpose |
|------|---------|
| `core-customize/manifest.json` | CCv2 build configuration |
| `core-customize/hybris/bin/custom/coremcp/` | MCP server extension |
| `core-customize/hybris/config/localextensions.xml` | Active extension declarations |
| `core-customize/hybris/config/local.properties` | Local configuration overrides |
| `docs/` | Generic development guides and patterns |
| `core-customize/gradlew` | Gradle wrapper — all build/server/HAC commands |
| `.claude/skills/` | Claude Code skills for SAP Commerce |

## Critical Rules

1. **Never modify `gensrc/`** — auto-generated from `*-items.xml` and `*-beans.xml`, overwritten on build
2. **Never modify platform or modules** — they are downloaded by CCv2; override behavior in custom extensions
3. **Use the alias pattern** for Spring beans: define `defaultMyBean`, alias to `myBean`
4. **Define interfaces** for services, facades, DAOs — implementations in `impl/` subpackage with `Default*` prefix
5. **DTOs are generated** from `*-beans.xml` — never hand-write these classes
6. **After `*-items.xml` changes**: `./gradlew ybuild stopServer startServer yupdatesystem`
7. **After `*-beans.xml` changes**: `./gradlew ybuild stopServer startServer`
8. **After Java source changes**: `./gradlew ybuild stopServer startServer`
9. **Register new extensions** in `core-customize/hybris/config/localextensions.xml` before building

## Extension: coremcp

The `coremcp` extension provides:
- **MCP server:** JSON-RPC protocol implementation for AI agent integration
- **Tool handlers:** Product search, cart management, checkout, order history, customer lookup
- **Agent service:** OpenAI-powered agent with MCP tool access
- **ImpEx data:** OAuth clients, catalog, products, pricing, stock, customers, orders, delivery/payment modes, base store/site
- **Visual search:** GPT-4o Vision image analysis with 3-tier catalog search (POST /{baseSiteId}/agent/visual-search)
- **Solr configuration:** `thinkshopIndex` with indexed properties, price range facets, sort definitions

See `core-customize/hybris/bin/custom/coremcp/docs/` for architecture, endpoints, and protocol documentation.

## Documentation Convention

Each feature flow has a dedicated directory with three files:

| File | Purpose |
|------|---------|
| `context.md` | What the flow does, when it's used, key decisions |
| `components.md` | The files that implement it and what each one does |
| `diagram.md` | Mermaid diagrams with descriptive context |

**Before working on a feature**, read its flow directory. **When adding a new feature**, create the flow directory first — docs before code.

## Access Points

| URL | Purpose | Credentials |
|-----|---------|-------------|
| http://localhost:9001/hac | Admin Console | admin / nimda |
| http://localhost:9001/backoffice | Backoffice Admin UI | admin / nimda |
| http://localhost:9001/occ/v2/ | OCC REST API | OAuth token |
| http://localhost:9001/occ/v2/swagger-ui.html | Swagger API Docs | — |
