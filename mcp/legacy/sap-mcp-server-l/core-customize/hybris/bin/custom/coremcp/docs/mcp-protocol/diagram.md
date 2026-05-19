# MCP Protocol — Diagrams

## Full Request Lifecycle

Traces a `tools/call` request from client through OAuth, controller, session validation, cart loading, dispatcher, tool handler, facade, and back.

```mermaid
sequenceDiagram
    participant Client
    participant OAuth as Spring Security<br/>(OAuth2 Filter)
    participant Ctrl as McpController
    participant SessS as McpSessionService
    participant CartL as CartLoaderStrategy
    participant Disp as McpDispatcherService
    participant Tool as McpToolHandler
    participant Facade as Commerce Facade

    Client->>OAuth: POST /occ/v2/{site}/mcp<br/>Authorization: Bearer {token}<br/>MCP-Session-Id: sess_abc123<br/>{"jsonrpc":"2.0","id":3,"method":"tools/call",<br/>"params":{"name":"product_search","arguments":{...}}}
    OAuth->>Ctrl: Authenticated request

    Ctrl->>Ctrl: Parse JSON body into JsonRpcRequest
    Ctrl->>Ctrl: Validate jsonrpc == "2.0"
    Note over Ctrl: method != "initialize",<br/>so session is required

    Ctrl->>SessS: getSession("sess_abc123")
    SessS->>SessS: touch() — update lastAccessedAt
    SessS-->>Ctrl: McpSession

    Ctrl->>Ctrl: Read session.getCartCode()
    alt Cart code exists
        Ctrl->>CartL: loadCart(cartCode)
        CartL-->>Ctrl: Cart loaded into thread-local
    end

    Ctrl->>Disp: dispatch(request, session)
    Disp->>Disp: switch(method) -> "tools/call"
    Disp->>Disp: Extract toolName from params.name
    Disp->>Disp: Look up handler in toolHandlerMap

    Disp->>Tool: execute(arguments)
    Tool->>Facade: e.g. productSearchFacade.textSearch(...)
    Facade-->>Tool: ProductSearchPageData
    Tool->>Tool: Serialize to JSON via ObjectMapper
    Tool-->>Disp: McpToolResult.success(json)

    Disp-->>Ctrl: JsonRpcResponse.toolResult(id, content, false)

    Ctrl->>Ctrl: Save cartService.getSessionCart().getCode()<br/>back to session.setCartCode()

    Ctrl-->>Client: 200 OK<br/>{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"..."}]}}
```

## Session State Machine

Shows the three states an MCP session passes through and the transitions between them.

```mermaid
stateDiagram-v2
    [*] --> Created: POST initialize<br/>createSession(clientInfo, protocolVersion)

    Created --> Active: POST notifications/initialized<br/>(or any subsequent request)

    Active --> Active: POST tools/list<br/>POST tools/call<br/>POST notifications/*<br/>(touch() updates lastAccessedAt)

    Active --> Terminated: DELETE /mcp<br/>removeSession(sessionId)

    Created --> Terminated: DELETE /mcp<br/>removeSession(sessionId)

    Terminated --> [*]

    note right of Created
        MCP-Session-Id returned in response header.
        Session stored in ConcurrentHashMap.
        cartCode is null.
    end note

    note right of Active
        Each request loads session cart via
        cartLoaderStrategy, saves cart code
        back after dispatch.
    end note

    note right of Terminated
        Removed from map. Further requests
        with this ID get -32600 error.
    end note
```

## Tool Registry Lookup

How `tools/call` resolves a tool name to a handler and executes it.

```mermaid
flowchart TD
    A["tools/call request<br/>params.name = 'product_search'"] --> B{params present?}
    B -->|No| E1["Error -32602<br/>Missing params"]
    B -->|Yes| C{name present?}
    C -->|No| E2["Error -32602<br/>Missing tool name"]
    C -->|Yes| D{toolHandlerMap.get(name)}
    D -->|null| E3["Error -32602<br/>Unknown tool: {name}"]
    D -->|handler found| F["handler.execute(arguments)"]
    F --> G{Exception?}
    G -->|Yes| H["JsonRpcResponse.toolResult(id,<br/>'Internal error: ...', isError=true)"]
    G -->|No| I["McpToolResult"]
    I --> J{result.isError()?}
    J -->|Yes| K["JsonRpcResponse.toolResult(id,<br/>content, isError=true)"]
    J -->|No| L["JsonRpcResponse.toolResult(id,<br/>content, isError=false)"]
```

## Spring Bean Dependency Graph

Shows how `coremcp-spring.xml` wires the MCP services and tool handlers, and how the controller discovers them.

```mermaid
graph TB
    subgraph "Web Layer (coremcp-web-spring.xml)"
        MC["McpController<br/><i>@Controller, component-scanned</i>"]
    end

    subgraph "Service Layer (coremcp-spring.xml)"
        MDS["mcpDispatcherService<br/><i>DefaultMcpDispatcherService</i>"]
        MSS["mcpSessionService<br/><i>DefaultMcpSessionService</i>"]
        MCSS["mcpCartSessionService<br/><i>DefaultMcpCartSessionService</i>"]
    end

    subgraph "Tool Handlers (coremcp-spring.xml)"
        TH1["productSearchToolHandler"]
        TH2["productGetToolHandler"]
        TH3["cartGetToolHandler"]
        TH4["cartAddProductToolHandler"]
        TH5["cartUpdateEntryToolHandler"]
        TH6["cartRemoveEntryToolHandler"]
        TH7["orderGetToolHandler"]
        TH8["orderHistoryToolHandler"]
        TH9["customerGetToolHandler"]
        TH10["customerLookupToolHandler"]
        TH11["checkoutSetDeliveryAddressToolHandler"]
        TH12["checkoutSetDeliveryModeToolHandler"]
        TH13["checkoutSetPaymentToolHandler"]
        TH14["orderPlaceToolHandler"]
        TH15["promotionsGetToolHandler"]
    end

    subgraph "Platform Beans (existing)"
        PSF["productSearchFacade"]
        PF["productFacade"]
        CF["cartFacade"]
        OF["orderFacade"]
        CKF["checkoutFacade"]
        CUF["customerFacade"]
        PQS["promotionQueryService<br/><i>(custom, uses flexibleSearchService)</i>"]
        CLS["cartLoaderStrategy"]
        CS["cartService"]
    end

    MC -->|"@Resource"| MDS
    MC -->|"@Resource"| MSS
    MC -->|"@Resource"| CLS
    MC -->|"@Resource"| CS

    MDS -->|"property"| MSS
    MDS -->|"property: toolHandlers list"| TH1
    MDS -->|"property: toolHandlers list"| TH2
    MDS -->|"property: toolHandlers list"| TH3
    MDS -->|"property: toolHandlers list"| TH4
    MDS -->|"property: toolHandlers list"| TH5
    MDS -->|"property: toolHandlers list"| TH6
    MDS -->|"property: toolHandlers list"| TH7
    MDS -->|"property: toolHandlers list"| TH8
    MDS -->|"property: toolHandlers list"| TH9
    MDS -->|"property: toolHandlers list"| TH10
    MDS -->|"property: toolHandlers list"| TH11
    MDS -->|"property: toolHandlers list"| TH12
    MDS -->|"property: toolHandlers list"| TH13
    MDS -->|"property: toolHandlers list"| TH14
    MDS -->|"property: toolHandlers list"| TH15

    TH1 --> PSF
    TH2 --> PF
    TH3 --> CF
    TH4 --> CF
    TH5 --> CF
    TH6 --> CF
    TH7 --> OF
    TH8 --> OF
    TH9 --> CUF
    TH10 --> CUF
    TH11 --> CKF
    TH12 --> CKF
    TH13 --> CKF
    TH14 --> CKF
    TH15 --> PQS
```

## Initialize Handshake

The full MCP session initialization sequence showing both the protocol handshake and the internal session creation.

```mermaid
sequenceDiagram
    participant Client
    participant Ctrl as McpController
    participant Disp as McpDispatcherService
    participant Sess as McpSessionService

    Client->>Ctrl: POST /mcp<br/>MCP-Protocol-Version: 2025-11-25<br/>{"method":"initialize","params":{"clientInfo":{"name":"claude-code"}}}

    Ctrl->>Ctrl: Parse & validate jsonrpc == "2.0"
    Ctrl->>Disp: handleInitialize(request)

    Disp->>Sess: createSession(clientInfo, "2025-11-25")
    Sess->>Sess: Generate "sess_" + UUID[0:12]
    Sess->>Sess: Store McpSession in ConcurrentHashMap
    Sess-->>Disp: "sess_a1b2c3d4e5f6"

    Disp-->>Ctrl: InitializeResult(response, sessionId)

    Ctrl->>Ctrl: Set MCP-Session-Id header
    Ctrl-->>Client: 200 OK<br/>MCP-Session-Id: sess_a1b2c3d4e5f6<br/>{"result":{"protocolVersion":"2025-11-25",<br/>"capabilities":{"tools":{"listChanged":false},"logging":{}},<br/>"serverInfo":{"name":"coremcp","version":"1.0.0"},<br/>"instructions":"ThinkShop is an electronics..."}}

    Client->>Ctrl: POST /mcp<br/>MCP-Session-Id: sess_a1b2c3d4e5f6<br/>{"method":"notifications/initialized"}

    Ctrl->>Sess: getSession("sess_a1b2c3d4e5f6")
    Sess-->>Ctrl: McpSession (touch'd)
    Ctrl->>Disp: dispatch(request, session)
    Disp-->>Ctrl: null (notification)
    Ctrl-->>Client: 202 Accepted
```
