# UCP profile — diagram

Discovery and auth bootstrap (design S1). The profile is the only anonymous
endpoint; every capability operation needs the password-grant bearer token
(design R8 — a documented local-testing concession).

```mermaid
sequenceDiagram
    participant U as UCP client / harness
    participant P as UcpProfileController (ROLE_ANONYMOUS)
    participant S as DefaultUcpProfileService
    participant O as OAuth2 token endpoint (platform)
    U->>P: GET /occ/v2/{site}/.well-known/ucp
    P->>S: buildProfile(baseSiteId)
    S-->>P: UcpProfile (version, capabilities, services, payment_handlers)
    P-->>U: 200 JSON + CORS headers
    U->>O: POST /oauth/token (password grant, john.doe@thinkshop.com)
    O-->>U: access_token (ROLE_CUSTOMERGROUP)
    Note over U: client now calls the advertised mcp endpoint with the bearer token
```

Advertised capability set after Phase 6 (all versioned with the pinned
`2026-04-08` string):

```mermaid
flowchart LR
    PR[/.well-known/ucp profile/]
    PR --> C1[dev.ucp.shopping.catalog]
    PR --> C2[dev.ucp.shopping.checkout]
    PR --> C3[dev.ucp.shopping.order]
    PR --> C4[com.thinkshop.promotions]
    PR --> C5[com.thinkshop.knowledge]
    PR --> T1[services.dev.ucp.shopping.mcp.endpoint<br/>POST /occ/v2/site/ucp/mcp]
    PR --> H1[payment_handlers: thinkshop_mock_card]
```
