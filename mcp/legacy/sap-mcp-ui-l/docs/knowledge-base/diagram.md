# Knowledge Base — Diagrams

## Search flow (`api.searchKnowledge`)

A caller (e.g. the future Help center) asks for KB entries; the api layer fetches the
public endpoint, maps each entry, and degrades to an empty result on any non-OK response.

```mermaid
sequenceDiagram
    participant C as Caller (page/component)
    participant A as api.searchKnowledge
    participant B as Backend /info/search
    C->>A: searchKnowledge({ q?, category?, pageSize? })
    A->>A: build URLSearchParams (only provided keys)
    A->>B: GET ${OCC_BASE}/info/search?<params> (no auth header)
    alt res.ok
        B-->>A: { results: [...], count }
        A->>A: results.map(mapKnowledgeEntry)
        A-->>C: { results: KnowledgeEntry[], count }
    else non-OK
        B-->>A: error status
        A-->>C: { results: [], count: 0 }
    end
```

## Get-by-uid flow (`api.getKnowledgeEntry`)

```mermaid
sequenceDiagram
    participant C as Caller
    participant A as api.getKnowledgeEntry
    participant B as Backend /info/{uid}
    C->>A: getKnowledgeEntry(uid)
    A->>B: GET ${OCC_BASE}/info/{encodeURIComponent(uid)} (no auth header)
    alt res.ok
        B-->>A: entry JSON
        A->>A: mapKnowledgeEntry(json)
        A-->>C: KnowledgeEntry
    else 404 / non-OK
        B-->>A: error status
        A-->>C: null
    end
```

The non-OK branches are deliberate: KB is non-critical chrome, so a backend error renders
as "no content" rather than a thrown exception that would break the host page.
