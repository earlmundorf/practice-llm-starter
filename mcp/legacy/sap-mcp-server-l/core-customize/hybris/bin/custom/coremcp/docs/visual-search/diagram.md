# Visual Product Search — Diagrams

## Request Flow

The visual search request follows the standard OCC pattern through Spring Security, then into custom code.

```mermaid
sequenceDiagram
    participant Client
    participant OAuth as Spring Security (OAuth2)
    participant Controller as VisualSearchController
    participant Service as DefaultVisualSearchService
    participant OpenAI as OpenAiClient (GPT-4o Vision)
    participant Solr as ProductSearchFacade (Solr)

    Client->>OAuth: POST /occ/v2/{site}/agent/visual-search<br/>Authorization: Bearer {token}
    OAuth->>Controller: Authenticated request

    Controller->>Controller: Validate image (size, MIME type)
    Controller->>Service: searchByImage(base64, mimeType)

    Service->>OpenAI: chatCompletion(visionMessages, null, "gpt-4o")
    OpenAI-->>Service: { productName, brand, category, color, material, searchTerms, reasoning, confidence }

    Service->>Solr: Tier 1: "{brand} {productName}" (limit 3)
    Solr-->>Service: bestMatch matches (confidence 0.95 or 0.9 with searchTerms fallback)

    Service->>Solr: Tier 2: "{name} {color} {material} {category}" (limit 5)
    Solr-->>Service: similar matches (confidence 0.7, de-duped)

    alt No results yet
        Service->>Solr: Tier 3: "{category}" (limit 5)
        Solr-->>Service: explore matches (confidence 0.4)
    end

    Service-->>Controller: { visionAnalysis, products[] }
    Controller-->>Client: 200 OK + JSON response
```

## Component Architecture

Where visual search fits within the existing coremcp extension.

```mermaid
graph TB
    subgraph "OCC Web Layer (component-scanned)"
        AC[AgentController<br/>/agent/chat]
        MC[McpController<br/>/mcp]
        VC[VisualSearchController<br/>/agent/visual-search]
    end

    subgraph "Service Layer (coremcp-spring.xml)"
        AS[AgentService]
        MDS[McpDispatcherService]
        VS[VisualSearchService]
        OAI[OpenAiClient]
    end

    subgraph "Platform Facades"
        PSF[ProductSearchFacade]
        CF[CartFacade]
        OF[OrderFacade]
    end

    AC --> AS
    MC --> MDS
    VC --> VS

    AS --> OAI
    VS --> OAI
    VS --> PSF

    AS --> TH[Tool Handlers]
    MDS --> TH
    TH --> PSF
    TH --> CF
    TH --> OF
```
