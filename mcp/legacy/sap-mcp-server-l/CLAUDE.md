# CLAUDE.md

This is the root configuration file for [Claude Code](https://claude.ai/code) in this SAP Commerce (Hybris) project. It teaches Claude how to build, run, test, and extend the platform — so it can assist effectively from the first message.

## What This Project Is

An SAP Commerce MCP (Model Context Protocol) server extension, structured for CCv2 (Commerce Cloud v2) deployment. The Commerce Suite version is defined in `core-customize/manifest.json` (`commerceSuiteVersion`).
- **Backend:** Custom OCC extension (`coremcp`) providing an MCP server, LLM agent, and visual search for AI agent integration
- **Data:** Solr-indexed product catalog and knowledge base, OAuth2 auth, sample customers and orders (`sampledatamcp`)
- **Layout:** CCv2-compliant repository structure with `core-customize/` root

## CCv2 Repository Structure

This project follows the SAP Commerce Cloud v2 mandatory layout:

```
sap-mcp-server-l/
├── core-customize/                    # CCv2 root — cloud builds start here
│   ├── manifest.json                  # Build/deployment configuration
│   ├── dev-config/                    # Tracked config sources (overlaid onto hybris/config by setupConfig)
│   │   ├── local.properties           #   ← edit config HERE, not in hybris/config/
│   │   ├── local-{dev,stg,prod}.properties  # CCv2 persona overrides
│   │   └── localextensions.xml
│   ├── scripts/                       # HAC helpers, Solr indexing, promotions, smoke-test.sh
│   └── hybris/
│       ├── bin/custom/
│       │   ├── coremcp/               # MCP server extension
│       │   └── sampledatamcp/         # Sample data extension (excluded in production)
│       └── config/                    # GENERATED at setup — gitignored, do not edit
├── docs/                              # Development guides + adr/ + review/
├── .claude/skills/                    # Claude Code domain skills
├── CLAUDE.md                          # This file
├── SECURITY.md                        # Secrets policy + production deployment checklist
└── README.md
```

The `manifest.json` declares the Commerce version and references config files. The cloud build system downloads the platform and modules automatically — they are not in the repository.

## How This Project Is Documented

| Layer | Location | Purpose |
|-------|----------|---------|
| **CLAUDE.md** (this file) | Project root | Commands, rules, paths — everything Claude needs to operate |
| **Skills** | `.claude/skills/` | Domain expertise that activates contextually (SAP Commerce dev, code review) |
| **Project docs** | `docs/` | Setup + data guides, extension patterns, `extending/` checklists, `adr/` decisions, `review/` architecture review |
| **Extension docs** | `<extension>/docs/` in `coremcp` and `sampledatamcp` | Per-feature flows + `reference/` (see Documentation Convention below) |
| **Gradle tasks** | `core-customize/gradlew` | Server lifecycle, HAC console, and build operations (see Commands below) |

## Commands

All commands run from `core-customize/` using `./gradlew` (the one exception —
scoped integration tests via ant — is called out in Testing).

### First-Time Setup

See `docs/getting-started.md` for the full walkthrough. Summary:

1. **Install Java 17** (any distribution; e.g. SDKMAN: `sdk install java 17.0.19-sapmchn`)
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
   ./scripts/smoke-test.sh         # Verify end to end (21 checks)
   ```

### Server Lifecycle

| Command | When to Use |
|---------|-------------|
| `./gradlew stopServer startServer` | Quick restart (no build) |
| `./gradlew yclean ybuild stopServer startServer` | **After ANY Java source changes** — see note below |
| `./gradlew yclean ybuild stopServer yupdatesystem startServer` | After `*-items.xml` changes (preserves data). Run `yupdatesystem` with the server **stopped** — with it running, ant tries a JMX wrapper restart that fails (`restartWrapper: serviceURL is null`) before updating anything |
| `./gradlew yclean ybuild stopServer startServer` | After `*-beans.xml` changes |
| `./gradlew yclean yall yinitialize` | Full data reset (**destroys all data**) |

**Always `yclean` before `ybuild` for Java changes:** hybris's incremental Java
compilation is unreliable in this project — we've observed `ybuild` silently
skipping a recompile of edited sources, leaving stale `.class` files on disk
even though the build reports `BUILD SUCCESSFUL`. The symptom is "my code
change doesn't seem to take effect" with no error. `yclean` is safe (only
deletes build outputs, never data); the cost is ~30s extra build time and a
guarantee your edits actually deploy.

### HAC Console

| Command | Purpose |
|---------|---------|
| `./gradlew flexquery -Pfile=<path>` | Run FlexibleSearch queries |
| `./gradlew groovy -Pfile=<path> [-Pcommit=true]` | Execute Groovy scripts — rollback by default |
| `./gradlew impex -Pfile=<path>` | Run ImpEx imports |

**Tip:** For complex multi-line input, write to a temp file first (e.g., `/tmp/query.sql`) then pass the file path.

### Testing

```bash
./gradlew testCustomExtensions   # Unit tests for all custom extensions (preferred)

# Integration tests — run ant directly; see note below
cd hybris/bin/platform && . ./setantenv.sh && ant integrationtests -Dtestclasses.extensions=coremcp
```

**Test scope rule (important):** Always restrict test runs to **custom extensions** —
the platform's own tests are slow and not what we are verifying. `testCustomExtensions`
wraps `ant unittests -Dtestclasses.extensions=<all custom>`; to scope to specific
extensions, use the ant form directly with a comma-separated list
(e.g. `-Dtestclasses.extensions=coremcp,sampledatamcp`). **Do not use the gradle
passthrough tasks (`./gradlew yunittests` / `yintegrationtests`) with `-D` flags —
the plugin drops them and runs the entire platform suite.** Only run the full
platform suite when the user explicitly asks for it.

**Stop the server before CLI test runs:** The test framework boots a junit tenant that
binds Solr on the same port (8983) as the live server's Solr, and shuts that Solr
down at the end of the run. The DB is isolated (junit tenant has its own schema)
but Solr is not — running tests with the server up will leave the live server's
Solr **dead** until the next `stopServer/startServer`. Before any
`testCustomExtensions`/`yunittests`/`yintegrationtests` run from the command line:

```bash
./gradlew stopServer
./gradlew testCustomExtensions
./gradlew startServer
```

(IDE-driven JUnit runs for `@UnitTest` classes don't boot the platform and don't
have this problem.)

**After an `*-items.xml` change**, the junit tenant must be re-initialized once
(`ant yunitinit` from `hybris/bin/platform`) or integration tests fail with
`type code '...' invalid`.

**End-to-end verification:** `./scripts/smoke-test.sh` (from `core-customize/`,
server running) runs 21 live checks — OAuth, MCP handshake/tools, search,
cart flow, agent guards, and a real LLM round-trip.

## Key Paths

| Path | Purpose |
|------|---------|
| `core-customize/manifest.json` | CCv2 build configuration |
| `core-customize/hybris/bin/custom/coremcp/` | MCP server extension |
| `core-customize/hybris/bin/custom/coremcp/project.properties` | All `coremcp.*` tunables with commented defaults |
| `core-customize/dev-config/local.properties` | **Editable** config source — `setupConfig` copies it into the gitignored `hybris/config/` |
| `core-customize/dev-config/localextensions.xml` | Active extension declarations (source) |
| `core-customize/scripts/smoke-test.sh` | 21-check end-to-end suite against the live server |
| `docs/` | Generic development guides, `adr/` (decisions), `review/` (architecture review + plan) |
| `SECURITY.md` | Secrets policy + CCv2 production deployment checklist |
| `core-customize/gradlew` | Gradle wrapper — all build/server/HAC commands |
| `.claude/skills/` | Claude Code skills for SAP Commerce |

## Critical Rules

1. **Never modify `gensrc/`** — auto-generated from `*-items.xml` and `*-beans.xml`, overwritten on build
2. **Never modify platform or modules** — they are downloaded by CCv2; override behavior in custom extensions
3. **Use the alias pattern** for Spring beans: define `defaultMyBean`, alias to `myBean`
4. **Define interfaces** for services, facades, DAOs — implementations in `impl/` subpackage with `Default*` prefix
5. **OCC Data/WsDTO classes are generated** from `*-beans.xml` — never hand-write those. Internal protocol/LLM payload DTOs (`com.coremcp.dto.*`) are deliberately plain Jackson classes instead — see ADR 0005
6. **After `*-items.xml` changes**: `./gradlew yclean ybuild stopServer yupdatesystem startServer`
7. **After `*-beans.xml` changes**: `./gradlew yclean ybuild stopServer startServer`
8. **After Java source changes**: `./gradlew yclean ybuild stopServer startServer`
9. **Register new extensions** in `core-customize/dev-config/localextensions.xml` (the tracked source) before building — `setupConfig` copies it into the generated `hybris/config/`
10. **Keep reference docs in sync** — when changing tool schemas, endpoints, or configuration properties, update the matching reference doc under `coremcp/docs/reference/` (`tools.md`, `endpoints.md`, `llm-providers.md`, `solr.md`) in the same commit

## Extension: coremcp

The `coremcp` extension provides:
- **MCP server:** JSON-RPC 2.0 protocol with 19 tools and a DB-persisted, cluster-safe session store (`McpSessionEntry`; `coremcp.session.store=persistent|memory`)
- **Tool handlers:** Product search (keyword + categories), cart/vouchers, checkout, orders, customer lookup, promotions, knowledge base
- **Agent service:** Multi-provider LLM agent (OpenAI/Anthropic/OpenAI-compatible) with tool calling, SSE streaming, transient-failure retry, and per-user rate limiting (`coremcp.agent.rateLimit.perMinute`)
- **Visual search:** Vision-model image analysis with 3-tier catalog search (POST /{baseSiteId}/agent/visual-search)
- **Solr configuration:** `thinkshopIndex` (products: category/price/stock facets) and `knowledgeIndex` (knowledge base)

Operational tunables are defined with commented defaults in
`core-customize/hybris/bin/custom/coremcp/project.properties`.
Sample data lives entirely in `sampledatamcp` (excluded from production builds);
its ImpEx files carry numeric load-order prefixes (`essentialdata-NN-*`,
`projectdata-NN-*`) — keep that convention for new data files. Note that
`projectdata-*` only loads on initialize; on an existing DB import new files via
`./gradlew impex -Pfile=...`.

See `core-customize/hybris/bin/custom/coremcp/docs/` for architecture, endpoints, and protocol documentation.

## Documentation Convention

Each extension's `docs/` directory has exactly three kinds of content:

```
<extension>/docs/
├── README.md            # Index: lists every flow and reference doc
├── <feature-flow>/      # One directory per feature flow (three files, no more)
│   ├── context.md       # What the flow does, when it's used, key decisions
│   ├── components.md    # The files that implement it and what each one does
│   └── diagram.md       # Mermaid diagrams with descriptive context
└── reference/           # Cross-cutting reference docs (flat .md files)
```

Current flows — coremcp: `mcp-protocol/`, `agent-chat/`, `visual-search/`,
`knowledge-base/`; sampledatamcp: `sample-data/`, `store-infrastructure/`,
`promotions-setup/`. coremcp's `reference/` holds `tools.md`, `endpoints.md`,
`solr.md`, `llm-providers.md`.

**Before working on a feature**, read its flow directory. **When adding a new
feature**, create the flow directory first — docs before code. Flat markdown
files belong in `reference/`, never loose in `docs/` (README.md is the only
exception).

## Access Points

| URL | Purpose | Credentials |
|-----|---------|-------------|
| http://localhost:9001/hac | Admin Console | admin / nimda |
| http://localhost:9001/backoffice | Backoffice Admin UI | admin / nimda |
| https://localhost:9002/occ/v2/ | OCC REST API (incl. `/mcp`, `/agent/*`, `/info/*`) | OAuth token |
| https://localhost:9002/authorizationserver/oauth/token | OAuth2 token endpoint | demo clients — see SECURITY.md |
| https://localhost:9002/occ/v2/swagger-ui.html | Swagger API Docs | — |

OAuth and OCC calls must use HTTPS (9002, self-signed — use `curl -k`); the
token endpoint answers plain-HTTP requests on 9001 with a 302 redirect.
