# UCP REST binding — diagram

Both transports are thin adapters over the same capability services — the
REST binding adds the left-hand path without touching anything below the
controller layer:

```mermaid
flowchart TD
    subgraph client [UCP client]
        H[ucp-e2e.py --transport rest / mcp]
    end
    subgraph occ [OCC v2 web context]
        P["UcpProfileController<br/>GET /.well-known/ucp (anonymous)"]
        R["UcpCatalogRestController<br/>UcpCheckoutRestController<br/>UcpOrderRestController<br/>(Phase 7 — REST routes)"]
        M["UcpMcpController<br/>POST /ucp/mcp (JSON-RPC tools)"]
    end
    subgraph services [ucpcommerce capability services — binding-agnostic, R12]
        C[UcpCatalogService]
        K[UcpCheckoutService + session store]
        O[UcpOrderService]
    end
    F[hybris facades<br/>ProductSearch / Cart / Checkout / Order]
    H -->|discover| P
    H -->|REST| R
    H -->|MCP| M
    R --> C & K & O
    M --> C & K & O
    C --> F
    K --> F
    O --> F
```

Request/response mapping on the checkout complete route (the one operation
with a header mapping):

```mermaid
sequenceDiagram
    participant U as UCP client
    participant R as UcpCheckoutRestController
    participant S as UcpCheckoutService (unchanged)
    U->>R: POST /checkout-sessions/{id}/complete<br/>Idempotency-Key: uuid<br/>{payment: {instruments: [...]}}
    R->>R: parse body, reject payload id (400 on violation)
    R->>S: complete(id, payload, header value)
    Note over S: identical call the MCP CompleteCheckoutTool makes<br/>with meta["idempotency-key"]
    S-->>R: UcpCheckout (completed / error-envelope payload)
    R-->>U: HTTP 200 + JSON (business errors included)<br/>HTTP 400 only for client protocol bugs
```
