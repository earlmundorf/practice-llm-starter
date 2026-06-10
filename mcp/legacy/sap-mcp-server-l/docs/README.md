# SAP Commerce OCC Development Guide

Generic patterns and conventions for building OCC extensions on SAP Commerce (Hybris) 22.11.

## Topics

| Topic | Description |
|---|---|
| [getting-started.md](getting-started.md) | Local development setup with Gradle, MySQL, and platform bootstrap |
| [data.md](data.md) | Database configuration and data directory layout |
| [extension-setup/](extension-setup/) | How OCC extensions are configured, loaded, and wired by the platform |
| [extending/](extending/) | Step-by-step guide and checklist for adding new features |
| [adr/](adr/) | Architecture Decision Records — significant decisions and their reasoning |
| [review/](review/) | Full project review (June 2026): architecture, code, configuration, data, strategic alignment, and the phased improvement plan |

## Extension-Specific Docs

Each custom extension has its own `docs/` directory: a README index, one
directory per feature flow (context/components/diagram), and `reference/` for
cross-cutting flat docs (see the Documentation Convention in the root CLAUDE.md):

| Extension | Path | Flows |
|---|---|---|
| coremcp | `bin/custom/coremcp/docs/` | MCP protocol, agent chat, visual search, knowledge base (+ `reference/`: tools, endpoints, solr, llm-providers) |
| sampledatamcp | `bin/custom/sampledatamcp/docs/` | Store infrastructure, sample data, promotions |
