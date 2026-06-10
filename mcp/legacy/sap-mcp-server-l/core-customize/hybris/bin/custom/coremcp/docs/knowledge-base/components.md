# Knowledge Base — Components

## Type system (coremcp)

| File | Purpose |
|------|---------|
| `resources/coremcp-items.xml` | `KnowledgeEntry` (typecode 14001): `uid` (unique business key), `category` (enum `KnowledgeCategory`: policy/event/promo/guide/brand/howto/contact), localized `title`/`summary`/`body` (LONG_STRING), `tags` (comma-separated), `validFrom`/`validTo`, `priority` (int, ranking tie-break), `imageUrl`, `status` (enum `KnowledgeStatus`: draft/published). |

## Service layer (coremcp)

| File | Purpose |
|------|---------|
| `src/com/coremcp/services/KnowledgeSearchService.java` | Interface: `getByUid(uid)`, `search(query, category, pageSize)`, `toJson(document)`. Returns Solr `Document`s, not Models. |
| `src/com/coremcp/services/impl/DefaultKnowledgeSearchService.java` | Queries `knowledgeIndex` via the platform `FacetSearchService` using the `DEFAULT` free-text template. `getByUid` = filter query on `uid`, pageSize 1. `search` = free-text query, optional `category` filter query, pageSize clamped 1–50. Failures log a warning and return empty (the agent degrades to a generic answer instead of erroring the turn). `toJson` maps the indexed fields (uid, category, title, summary, body, tags, priority, imageUrl). |

## Web layer (coremcp)

| File | Purpose |
|------|---------|
| `src/com/coremcp/controllers/KnowledgeController.java` | `GET /{baseSiteId}/info/{uid}` (404 + error JSON when missing) and `GET /{baseSiteId}/info/search?q=&category=&pageSize=` (returns `{results, count}`). `@Secured` includes `ROLE_ANONYMOUS` — public content. `pageSize` clamped to `coremcp.knowledge.maxPageSize` (default 50). |

## MCP tools (coremcp)

| File | Purpose |
|------|---------|
| `src/com/coremcp/tools/impl/InfoSearchToolHandler.java` | `info_search` — natural-language query + optional category enum + pageSize (default 5). The description steers the agent: use for any question that is NOT about a specific product or order. |
| `src/com/coremcp/tools/impl/InfoGetToolHandler.java` | `info_get` — fetch one entry by `uid`; error result when not found. |

Both are wired into the MCP dispatcher and agent tool lists in `coremcp-spring.xml`
(alias pattern: `defaultInfoSearchToolHandler` → `infoSearchToolHandler`, …), with
`knowledgeSearchService` injected.

## Index configuration and content (sampledatamcp)

| File | Purpose |
|------|---------|
| `resources/impex/essentialdata-20-solr-knowledge.impex` | `knowledgeIndex` facet-search config: indexed type `KnowledgeEntry`; fields uid/title/summary/body/tags/searchableText with weighted DEFAULT free-text template (uid 200, title 120/150-phrase, tags 80, summary 60, body 25; fuzzy on title/summary/tags); `category` facet; sorts `relevance` (score + priority desc) and `priority-desc`. `validFrom`/`validTo` intentionally NOT indexed (see ADR 0004). Full-reindex only (no update query). |
| `resources/impex/projectdata-50-knowledge.impex` | 15 core entries (policies, brand, contact, how-tos, loyalty). |
| `resources/impex/projectdata-55-knowledge-extras.impex` | 11 promo/event entries with validity windows and priorities. |

After content changes, re-run `./scripts/index-solr.sh` (full reindex of all configs).

## Tests

| File | Coverage |
|------|----------|
| `testsrc/com/coremcp/tools/impl/InfoToolHandlersTest.java` | `info_get` happy path / missing uid / service exception; `info_search` schema shape and results array. |
| `core-customize/scripts/smoke-test.sh` | Live checks: `/info/search`, `/info/{uid}`, and the `info_search` MCP tool. |
