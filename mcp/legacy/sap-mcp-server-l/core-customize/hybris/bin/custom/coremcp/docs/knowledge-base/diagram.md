# Knowledge Base — Diagrams

## Read path — agent question to governed answer

A chat turn like "what's your return policy?" never touches the database on the
read path: the agent calls `info_search`, the service queries Solr, and the
answer is composed from indexed content.

```mermaid
sequenceDiagram
    participant User
    participant Agent as DefaultAgentService
    participant Tool as InfoSearchToolHandler
    participant KSS as DefaultKnowledgeSearchService
    participant Solr as Solr (knowledgeIndex)

    User->>Agent: "what's your return policy?"
    Agent->>Agent: LLM picks info_search (per system prompt)
    Agent->>Tool: execute({query: "return policy"})
    Tool->>KSS: search("return policy", null, 5)
    KSS->>Solr: DEFAULT free-text template<br/>(uid 200 > title 120 > tags 80 > summary 60 > body 25)
    Solr-->>KSS: documents (score + priority ranking)
    KSS-->>Tool: List<Document>
    Tool-->>Agent: {results: [{uid, title, summary, body, ...}], count}
    Agent->>User: answer composed from entry body,<br/>citing the entry title
```

The public REST path is the same minus the agent:
`GET /info/search` → `KnowledgeController` → `KnowledgeSearchService` → Solr.
On any Solr failure the service returns empty results (warning logged) — the
endpoint answers `{results: [], count: 0}` and the agent falls back to a
generic answer rather than failing the turn.

## Content path — ImpEx to searchable

```mermaid
flowchart LR
    A[projectdata-50/55-knowledge.impex<br/>26 KnowledgeEntry items] -->|yinitialize /<br/>gradlew impex| B[(DB: KnowledgeEntries<br/>typecode 14001)]
    C[essentialdata-20-solr-knowledge.impex<br/>knowledgeIndex config] -->|yinitialize /<br/>yupdatesystem| D[SolrFacetSearchConfig]
    B --> E[scripts/index-solr.sh<br/>full reindex]
    D --> E
    E --> F[(Solr knowledgeIndex)]
    F --> G[/info/** endpoints/]
    F --> H[info_get / info_search MCP tools]
```

Content is invisible to search until the reindex runs — `index-solr.sh` is part
of the documented setup flow and required after any knowledge ImpEx change.
