# Architecture Decision Records

Significant, hard-to-reverse decisions and their reasoning. One file per decision;
status is `accepted` unless superseded. New ADRs take the next number.

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-mcp-server-as-occ-extension.md) | Implement the MCP server as a native OCC v2 extension | accepted |
| [0002](0002-persisted-mcp-session-store.md) | MCP session state: in-memory → DB-persisted item type | accepted |
| [0003](0003-promotions-via-groovy-not-impex.md) | Define promotion rules in idempotent Groovy, not ImpEx | accepted |
| [0004](0004-knowledge-date-filtering-after-solr.md) | Filter KnowledgeEntry validity dates in Java, not Solr | accepted |
| [0005](0005-internal-jackson-dtos-not-beans-xml.md) | LLM/tool payloads use plain Jackson DTOs, not *-beans.xml | accepted |
| [0006](0006-demo-data-isolated-in-sampledatamcp.md) | All demo data isolated in `sampledatamcp`; dual-import + sync job | accepted |
