# UCP MCP binding — diagram

One `tools/call` through the stateless binding (read path — catalog, orders,
custom capabilities; the checkout flow's stateful bracket is in the
ucp-checkout diagram):

```mermaid
sequenceDiagram
    participant U as UCP client
    participant C as UcpMcpController
    participant D as UcpMcpDispatcherService
    participant T as UcpTool adapter
    participant S as capability service
    participant F as hybris facade / coremcp service
    U->>C: POST /ucp/mcp {tools/call, name, arguments, meta}
    C->>D: dispatch(JsonRpcRequest)
    D->>D: UcpToolContext.fromParams (ucp-agent, idempotency-key)
    D->>T: execute(arguments, context)
    T->>S: one typed operation
    S->>F: ProductSearchFacade / OrderFacade / PromotionQueryService / KnowledgeSearchService
    F-->>S: platform data
    S-->>T: UCP payload DTO (ucp envelope, minor-unit money, messages[])
    T-->>D: serialized JSON payload
    D-->>C: JSON-RPC tool result (isError only for unexpected exceptions)
    C-->>U: 200 JSON (202 for notifications)
```

Tool → backing service map (13 tools after Phase 6):

```mermaid
flowchart LR
    subgraph tools [MCP tools]
        A[search_catalog / lookup_catalog / get_product]
        B[create/get/update/complete/cancel_checkout]
        O[get_order / list_orders]
        P[get_promotions]
        K[search_knowledge / get_knowledge]
    end
    A --> CS[UcpCatalogService] --> F1[ProductSearchFacade / ProductFacade]
    B --> CK[UcpCheckoutService] --> F2[CartFacade / CheckoutFacade / UserFacade]
    O --> OS[UcpOrderService] --> F3[OrderFacade]
    P --> PQ[coremcp PromotionQueryService]
    K --> KS[coremcp KnowledgeSearchService - Solr knowledgeIndex]
```
