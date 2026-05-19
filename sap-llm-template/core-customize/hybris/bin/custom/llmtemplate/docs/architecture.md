# Architecture & Design

## Component Overview

```
  +-------------------+
  |   MCP Client      |  Claude Code, React app, curl, etc.
  +--------+----------+
           | HTTP (JSON-RPC 2.0)
           | Authorization: Bearer <token>
           |
  +--------v-----------------------------------------+
  |  Spring Security Filter Chain (OAuth2)           |
  |  (reused from commercewebservices)               |
  +--------+-----------------------------------------+
           |
  +--------v-----------------------------------------+
  |                  McpController                    |
  |  POST /{baseSiteId}/mcp                          |
  |  GET  /{baseSiteId}/mcp  (SSE, future)           |
  |  DELETE /{baseSiteId}/mcp                         |
  |                                                   |
  |  - Parses JSON-RPC envelope                       |
  |  - Validates MCP-Session-Id header                |
  |  - Delegates to McpDispatcherService              |
  |  - Returns JSON-RPC response                      |
  +--------+-----------------------------------------+
           |
  +--------v-----------------------------------------+
  |             McpDispatcherService                  |
  |                                                   |
  |  Routes by JSON-RPC method:                       |
  |    "initialize"    -> handleInitialize()          |
  |    "tools/list"    -> handleToolsList()           |
  |    "tools/call"    -> lookup McpToolHandler       |
  |    "notifications/*" -> handleNotification()      |
  +--------+-----------------------------------------+
           |
           |  tools/call dispatches to:
           |
  +--------v-----------------------------------------+
  |              McpToolHandler (interface)           |
  |                                                   |
  |  String getName()                                 |
  |  Map<String,Object> getDefinition()               |
  |  McpToolResult execute(Map<String,Object> args)   |
  +---------+---+---+---+---+---+---+---+------------+
            |   |   |   |   |   |   |   |
  +---------v-+ | +-v-+ | +-v-+ | +-v-+ | +-v-------+
  |product_   | | |ord| | |car| | |chk| | |customer_|
  |search/get | | |er_| | |t_ | | |out| | |get/     |
  |           | | |get/| | |get| | |set| | |lookup   |
  |           | | |hist| | |add| | |pay| | |         |
  +---------+-+ | +---+ | +---+ | +---+ | +----+----+
            |   |   |   |   |   |   |   |      |
            v   v   v   v   v   v   v   v      v
  +---------+---+---+---+---+---+---+----------+-----+
  |          Commerce Facade Layer (existing)         |
  |                                                   |
  |  ProductSearchFacade  OrderFacade  CartFacade     |
  |  ProductFacade        CheckoutFacade              |
  |                       CustomerFacade              |
  +--------------------------------------------------+
```

## Request Flow

Step-by-step trace of a `tools/call` request:

```
  1. Client POSTs to /occ/v2/electronics/mcp
     Headers: Authorization, MCP-Session-Id, Content-Type
     Body: {"jsonrpc":"2.0","id":3,"method":"tools/call",
            "params":{"name":"product_search","arguments":{"query":"camera"}}}

  2. Spring Security OAuth2 filter validates the Bearer token.
     If invalid -> 401 Unauthorized (never reaches controller).

  3. McpController.handlePost() receives the request.
     a. Parses JSON body into JsonRpcRequest object (Jackson)
     b. Reads MCP-Session-Id header
     c. Validates session via McpSessionService.getSession(sessionId)
        If invalid -> returns JSON-RPC error (-32600)
     d. Delegates to McpDispatcherService.dispatch(request, session)

  4. McpDispatcherService sees method = "tools/call"
     a. Extracts tool name "product_search" from params
     b. Looks up McpToolHandler by name in handler registry (Map)
        If not found -> returns JSON-RPC error (-32602)
     c. Calls handler.execute(arguments)

  5. ProductSearchToolHandler.execute({"query":"camera"})
     a. Extracts "query", "pageSize", "currentPage" from arguments
     b. Builds PageableData and SearchStateData
     c. Calls productSearchFacade.textSearch(query, searchState)
     d. Serializes ProductSearchPageData to JSON string
     e. Returns McpToolResult with content = [{type:"text", text: <json>}]

  6. Response bubbles back up:
     McpDispatcherService -> McpController -> HTTP Response

  7. Client receives:
     200 OK
     {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"..."}]}}
```

## Session Management

```
  +---------------------------------------------+
  |           McpSessionService                  |
  |                                              |
  |  ConcurrentHashMap<String, McpSession>       |
  |                                              |
  |  createSession(clientInfo)                   |
  |    -> generates UUID session ID              |
  |    -> stores McpSession in map               |
  |    -> returns session ID                     |
  |                                              |
  |  getSession(sessionId)                       |
  |    -> returns McpSession or null             |
  |                                              |
  |  removeSession(sessionId)                    |
  |    -> removes from map                       |
  |                                              |
  |  (Scheduled task purges sessions             |
  |   older than configurable TTL)               |
  +---------------------------------------------+

  McpSession:
  +---------------------------------------------+
  |  id: String (UUID)                           |
  |  clientInfo: { name, version }               |
  |  createdAt: Instant                          |
  |  lastAccessedAt: Instant                     |
  |  protocolVersion: String                     |
  +---------------------------------------------+
```

**In-memory limitations and upgrade path:**

- In-memory sessions are lost on server restart
- Not suitable for clustered deployments (sessions not shared)
- Upgrade path: Define a `McpSession` type in `llmtemplate-items.xml`,
  store sessions in the database via ModelService, enabling cluster-wide
  session persistence without code changes to McpSessionService consumers

## Spring Bean Wiring Plan

> **Note:** These are planned configurations for Phase 2. The XML files exist as empty scaffolds — add beans here when implementing.

The service-layer beans go in `llmtemplate-spring.xml`. Each bean follows the alias pattern (`defaultXxx` aliased to `xxx`) so other extensions can override. Tool handlers are registered as a list in the dispatcher bean. Example pattern:

```xml
<!-- Alias pattern: defaultXxx → xxx -->
<alias name="defaultMyService" alias="myService"/>
<bean id="defaultMyService" class="com.llmtemplate.services.impl.DefaultMyService"/>

<!-- Dispatcher with handler list -->
<alias name="defaultMcpDispatcherService" alias="mcpDispatcherService"/>
<bean id="defaultMcpDispatcherService"
      class="com.llmtemplate.services.impl.DefaultMcpDispatcherService">
    <property name="mcpSessionService" ref="mcpSessionService"/>
    <property name="toolHandlers">
        <list>
            <ref bean="productSearchToolHandler"/>
            <!-- ... one ref per tool handler ... -->
        </list>
    </property>
</bean>
```

The web-layer config in `llmtemplate-web-spring.xml` scans for `@Controller` classes:

```xml
<context:component-scan base-package="com.llmtemplate.controllers"/>
```

The controller is discovered by component-scan and injects
`mcpDispatcherService` via `@Resource`.

## Planned Package Structure (Phase 2)

> **Note:** These packages do not exist yet. This is the target structure for implementation.

The extension follows standard SAP Commerce layering:

- **`controllers/`** — Single `McpController` handling POST (JSON-RPC requests) and DELETE (session termination). Thin HTTP adapter, no business logic.
- **`services/`** — `McpDispatcherService` (routes JSON-RPC methods to tool handlers) and `McpSessionService` (in-memory session management via ConcurrentHashMap). Each has an interface and `Default*` implementation in `impl/`.
- **`tools/`** — `McpToolHandler` strategy interface with one implementation per tool (product_search, cart_get, order_place, etc.). Each handler delegates to an existing commerce facade.
- **`dto/`** — Jackson-annotated POJOs for JSON-RPC protocol messages (request, response, error). These are NOT generated from beans.xml — they need Jackson annotations that the generator doesn't support.
- **`jalo/`** — `LlmtemplateManager` (platform-generated, required by the extension framework).

## Design Decisions

### 1. Single Controller

The MCP spec mandates a single endpoint. All JSON-RPC methods go to one
URL. The controller acts as a thin HTTP adapter — it parses the JSON-RPC
envelope, validates the session, and delegates to McpDispatcherService.

### 2. Strategy Pattern for Tool Handlers

Each tool is a separate `McpToolHandler` implementation. Benefits:
- Tools are independently testable
- New tools added by implementing the interface + registering a bean
- Dispatcher has no knowledge of individual tool logic
- Tools can be overridden via Spring alias pattern

### 3. Delegates to Existing Commerce Facades

llmtemplate adds NO new DAO or service layer for domain logic. Every tool
handler calls an existing commerce facade (ProductSearchFacade, OrderFacade,
etc.). This avoids duplicating business logic and ensures consistency with
OCC REST endpoints.

```
  +------------------------------------------+
  |  llmtemplate tool handlers (NEW)            |
  +----+-----+-----+-----+-----+-----+------+
       |     |     |     |     |     |
  +----v-----v-----v-----v-----v-----v------+
  |  Commerce Facades (EXISTING, shared)     |
  |  ProductSearchFacade, OrderFacade,       |
  |  CartFacade, CheckoutFacade,             |
  |  CustomerFacade                          |
  +------------------------------------------+
  |  Commerce Services (EXISTING)            |
  +------------------------------------------+
  |  DAOs / FlexibleSearch (EXISTING)        |
  +------------------------------------------+
```

### 4. In-Memory Sessions (Initially)

ConcurrentHashMap for session storage. Simple, fast, no DB overhead.
Trade-offs documented above (not cluster-safe, lost on restart).

### 5. Jackson for JSON-RPC (Not beans.xml)

MCP protocol messages (JsonRpcRequest, JsonRpcResponse) are internal
DTOs parsed by Jackson, NOT generated from beans.xml. Rationale:
- beans.xml generates simple POJOs without annotations
- JSON-RPC parsing needs Jackson annotations (@JsonProperty, etc.)
- Protocol DTOs are not exposed to other extensions
- Commerce data objects returned BY tools still use existing facade DTOs

### 6. OAuth2 via Spring Security

No auth code in llmtemplate. The OCC web module's Spring Security filter
chain handles OAuth2 token validation before any request reaches
McpController. Tool handlers access the authenticated user via
`userService.getCurrentUser()` (injected from the security context).
