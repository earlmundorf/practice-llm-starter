# Visual Product Search — Components

## New Files

| File | Location | Purpose |
|------|----------|---------|
| `VisualSearchService.java` | `src/com/coremcp/services/` | Interface: `searchByImage(base64Image, mimeType)` returns analysis + product matches |
| `DefaultVisualSearchService.java` | `src/com/coremcp/services/impl/` | Orchestrates: GPT-4o Vision analysis → JSON parse → 3-tier Solr catalog search |
| `VisualSearchController.java` | `src/com/coremcp/controllers/` | OCC endpoint: `POST /{baseSiteId}/agent/visual-search` with validation and auth |

## Modified Files

| File | Change |
|------|--------|
| `resources/coremcp-spring.xml` | Add `defaultVisualSearchService` bean + `visualSearchService` alias, wired to existing `llmClient` and `productSearchFacade` |
| `docs/endpoints.md` | Document the new visual-search endpoint |
| `docs/tools.md` | (Optional) Add visual-search as an MCP tool if we expose it through MCP later |

## Existing Files Used (no changes)

| File | Role |
|------|------|
| `DefaultLlmClient.java` | Sends vision request through the active provider — same `chatCompletion()` method, vision format serializes correctly |
| `coremcp-web-spring.xml` | Already component-scans `com.coremcp.controllers` — picks up `VisualSearchController` automatically |
| `ProductSearchFacade` (platform) | Solr text search — called by `DefaultVisualSearchService` for catalog queries |

## Dependency Chain

```
VisualSearchController
  └─ @Resource visualSearchService
       ├─ llmClient (existing DefaultLlmClient → selected LlmProvider)
       │    └─ GPT-4o Vision via /chat/completions
       └─ productSearchFacade (platform bean)
            └─ Solr index (thinkshopIndex)
```
