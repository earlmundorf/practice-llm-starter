# CLAUDE.md

This is the root configuration file for [Claude Code](https://claude.ai/code) in this SAP Commerce (Hybris) 22.11 project. It teaches Claude how to build, run, test, and extend the platform — so it can assist effectively from the first message.

## What This Project Is

An SAP Commerce 22.11 template project for LLM/AI integration, structured for CCv2 (Commerce Cloud v2) deployment:
- **Backend:** Custom OCC extension (`llmtemplate`) with sample data and configuration
- **Data:** Solr-indexed product catalog, OAuth2 auth, sample customers and orders
- **Layout:** CCv2-compliant repository structure with `core-customize/` root

## CCv2 Repository Structure

This project follows the SAP Commerce Cloud v2 mandatory layout:

```
SAP-LLM-Template/
├── core-customize/                    # CCv2 root — cloud builds start here
│   ├── manifest.json                  # Build/deployment configuration
│   └── hybris/
│       ├── bin/custom/llmtemplate/     # LLM template extension
│       └── config/
│           ├── localextensions.xml    # Active extension declarations
│           └── local.properties       # Runtime configuration
├── docs/                              # Development guides
├── local/scripts/                     # Server and HAC automation
├── .claude/skills/                    # Claude Code domain skills
└── CLAUDE.md                          # This file
```

The `manifest.json` declares the Commerce version and references config files. The cloud build system downloads the platform and modules automatically — they are not in the repository.

## How This Project Is Documented

| Layer | Location | Purpose |
|-------|----------|---------|
| **CLAUDE.md** (this file) | Project root | Commands, rules, paths — everything Claude needs to operate |
| **Skills** | `.claude/skills/` | Domain expertise that activates contextually (SAP Commerce dev, code review) |
| **Project docs** | `docs/` | Generic patterns: extension setup, new feature walkthrough, checklists |
| **Extension docs** | `core-customize/hybris/bin/custom/llmtemplate/docs/` | Sample reference docs from the SAP-MCP-Server project |
| **Local scripts** | `local/scripts/` | Automation for server lifecycle and HAC console operations |

## Commands

### Local Scripts (ALWAYS use these)

**MANDATORY: Always use these scripts instead of running hybrisserver.sh, ant, or HAC manually.** They handle stop/start sequencing, log monitoring, authentication, and CSRF tokens automatically.

#### Local Setup (first time only)

```bash
export HYBRIS_HOME=/path/to/hybris    # directory containing bin/platform/ and bin/modules/
./local/scripts/setup-local.sh        # symlinks platform and modules into core-customize/hybris/bin/
```

#### Server Lifecycle

| Script | When to Use |
|--------|-------------|
| `local/scripts/restart-server.sh` | Quick restart (no build) |
| `local/scripts/build-server.sh` | After Java source changes (ant build + restart) |
| `local/scripts/update-server.sh` | After `*-items.xml` or `*-beans.xml` changes (rebuild + updatesystem, preserves data) |
| `local/scripts/initialize-server.sh` | Full data reset (rebuild + initialize, **destroys all data**) |
| `local/scripts/index-solr.sh` | Full Solr reindex (required after initialize) |

All server scripts output `STARTED` or `ERROR: <reason>` as their final line.

#### HAC Console

All accept three input modes: inline string, file path, or stdin (`-`).

| Script | Purpose |
|--------|---------|
| `local/scripts/hac-flexquery.sh` | Run FlexibleSearch queries — returns tabular results |
| `local/scripts/hac-groovy.sh` | Execute Groovy scripts — rollback by default, `--commit` to persist |
| `local/scripts/hac-impex.sh` | Run ImpEx imports — outputs `OK` or `ERROR` with details |

**Tip:** For complex multi-line input, write to a temp file first (e.g., `/tmp/query.sql`) then pass the file path. This avoids shell escaping issues.

### Raw Commands (only when scripts aren't appropriate)

```bash
cd core-customize/hybris/bin/platform && . ./setantenv.sh   # Required before any ant command
ant build                                                     # Build all extensions
ant build && ant updatesystem                                  # After *-items.xml changes
ant clean all initialize                                       # Full reset (destroys data)
```

### Testing

```bash
ant yunitinit                                              # Initialize JUnit tenant (once)
ant unittests -Dtestclasses.extensions=llmtemplate             # Unit tests
ant integrationtests -Dtestclasses.extensions=llmtemplate      # Integration tests
```

## Key Paths

| Path | Purpose |
|------|---------|
| `core-customize/manifest.json` | CCv2 build configuration |
| `core-customize/hybris/bin/custom/llmtemplate/` | LLM template extension |
| `core-customize/hybris/config/localextensions.xml` | Active extension declarations |
| `core-customize/hybris/config/local.properties` | Local configuration overrides |
| `docs/` | Generic development guides and patterns |
| `local/scripts/` | Server and HAC automation scripts |
| `.claude/skills/` | Claude Code skills for SAP Commerce |

## Critical Rules

1. **Never modify `gensrc/`** — auto-generated from `*-items.xml` and `*-beans.xml`, overwritten on build
2. **Never modify platform or modules** — they are downloaded by CCv2; override behavior in custom extensions
3. **Use the alias pattern** for Spring beans: define `defaultMyBean`, alias to `myBean`
4. **Define interfaces** for services, facades, DAOs — implementations in `impl/` subpackage with `Default*` prefix
5. **DTOs are generated** from `*-beans.xml` — never hand-write these classes
6. **After `*-items.xml` changes**: run `local/scripts/update-server.sh`
7. **After `*-beans.xml` changes**: `ant build` then `local/scripts/restart-server.sh`
8. **After Java source changes**: run `local/scripts/build-server.sh`
9. **Register new extensions** in `core-customize/hybris/config/localextensions.xml` before building

## Extensions

### llmtemplate

The `llmtemplate` extension is an empty OCC extension scaffold ready for implementation. It provides:
- **Extension scaffold:** `extensioninfo.xml`, `project.properties`, Spring XML configs, empty items/beans XML
- **Web module:** Registered at `/occ/v2` with controller component scanning
- **Reference docs:** Architecture, endpoints, tools, and protocol documentation in `docs/`

See `core-customize/hybris/bin/custom/llmtemplate/docs/` for the MCP server reference documentation.

### sampledatamcp

The `sampledatamcp` extension provides sample data via ImpEx:
- **Store infrastructure:** OAuth clients, catalog, currency, base store/site, delivery/payment modes
- **Solr configuration:** `thinkshopIndex` with indexed properties, price range facets, sort definitions
- **Sample data:** 10 electronics products, 3 customers, 3 orders with order entries

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
| https://localhost:9002/occ/v2/ | OCC REST API | OAuth token |
| https://localhost:9002/occ/v2/swagger-ui.html | Swagger API Docs | — |
