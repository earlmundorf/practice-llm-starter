# Endpoint Specification

## Single Endpoint

```
/occ/v2/{baseSiteId}/mcp
```

All MCP communication flows through this one endpoint. The HTTP method
determines the operation type:

| Method | Purpose                              |
|--------|--------------------------------------|
| POST   | Client sends JSON-RPC requests       |
| GET    | SSE stream for server notifications  |
| DELETE | Terminate session                    |

## Authentication Flow

```
  Client                  Auth Server              MCP Endpoint
    |                         |                         |
    |  POST /oauth/token      |                         |
    |  grant_type=password    |                         |
    |  &username=user         |                         |
    |  &password=pass         |                         |
    |  &client_id=mobile      |                         |
    |  &client_secret=secret  |                         |
    |------------------------>|                         |
    |                         |                         |
    |  { access_token: "xyz" }|                         |
    |<------------------------|                         |
    |                                                   |
    |  POST /occ/v2/electronics/mcp                     |
    |  Authorization: Bearer xyz                        |
    |  { method: "initialize" ... }                     |
    |-------------------------------------------------->|
    |                                                   |
    |  200 OK + MCP-Session-Id                          |
    |<--------------------------------------------------|
    |                                                   |
    |  POST /occ/v2/electronics/mcp                     |
    |  Authorization: Bearer xyz                        |
    |  MCP-Session-Id: <session>                        |
    |  { method: "tools/call" ... }                     |
    |-------------------------------------------------->|
    |                                                   |
    |  200 OK { result: { content: [...] } }            |
    |<--------------------------------------------------|
```

OAuth2 is handled by Spring Security's filter chain before the MCP
controller is reached. The controller only sees authenticated requests.

Supported grant types:
- `password` — For user-context operations (cart, customer, order history)
- `client_credentials` — For anonymous/trusted-client operations (product search)

---

## POST — Client Requests

All client-to-server messages are JSON-RPC 2.0 POSTed to the endpoint.

### `initialize`

Starts a new MCP session. Must be the first message.

**Request:**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-11-25",
    "capabilities": {},
    "clientInfo": {
      "name": "claude-code",
      "version": "1.0.0"
    }
  }
}
```

**Required headers:**

```
Content-Type: application/json
Authorization: Bearer <token>
MCP-Protocol-Version: 2025-11-25
```

**Response (200 OK):**

```
MCP-Session-Id: sess_a1b2c3d4e5f6
```

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-11-25",
    "capabilities": {
      "tools": { "listChanged": false },
      "logging": {}
    },
    "serverInfo": {
      "name": "llmtemplate",
      "version": "1.0.0"
    }
  }
}
```

---

### `notifications/initialized`

Client confirms initialization is complete. This is a notification (no `id`),
so the server returns 202 with no body.

**Request:**

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

**Required headers:**

```
Content-Type: application/json
Authorization: Bearer <token>
MCP-Session-Id: sess_a1b2c3d4e5f6
```

**Response:** `202 Accepted` (no body)

---

### `tools/list`

Returns all available tools with their input schemas.

**Request:**

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list"
}
```

**Response (200 OK):**

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "product_search",
        "description": "Search products by keyword, with optional category and pagination",
        "inputSchema": {
          "type": "object",
          "properties": {
            "query": { "type": "string", "description": "Search keyword" },
            "currentPage": { "type": "integer", "description": "Page number (0-based)", "default": 0 },
            "pageSize": { "type": "integer", "description": "Results per page", "default": 20 }
          },
          "required": ["query"]
        }
      }
    ]
  }
}
```

(See [tools.md](tools.md) for the complete tools array.)

---

### `tools/call`

Invokes a specific tool and returns the result.

**Request:**

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "product_search",
    "arguments": {
      "query": "camera",
      "pageSize": 5
    }
  }
}
```

**Response (200 OK):**

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"products\":[{\"code\":\"1934793\",\"name\":\"PowerShot A480\",\"price\":{\"value\":99.85,\"currencyIso\":\"USD\"}}],\"pagination\":{\"currentPage\":0,\"pageSize\":5,\"totalResults\":23}}"
      }
    ]
  }
}
```

MCP tool results are always returned as an array of content blocks.
llmtemplate returns a single `text` content block containing JSON-serialized
facade data.

---

### Error Responses

**Unknown method:**

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "error": {
    "code": -32601,
    "message": "Method not found: ping"
  }
}
```

**Unknown tool:**

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "error": {
    "code": -32602,
    "message": "Unknown tool: invalid_tool_name"
  }
}
```

**Missing/expired session:**

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "error": {
    "code": -32600,
    "message": "Invalid or expired MCP-Session-Id"
  }
}
```

**Tool execution error:**

```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Product not found: INVALID_CODE"
      }
    ],
    "isError": true
  }
}
```

Note: Per MCP spec, tool execution failures use `result` with `isError: true`,
not JSON-RPC `error`. The `error` field is reserved for protocol-level errors.

---

## GET — Server-Sent Events (SSE)

Opens a persistent connection for server-initiated messages.

```
GET /occ/v2/electronics/mcp
Authorization: Bearer <token>
MCP-Session-Id: sess_a1b2c3d4e5f6
Accept: text/event-stream
```

**Response:**

```
HTTP/1.1 200 OK
Content-Type: text/event-stream

event: message
data: {"jsonrpc":"2.0","method":"notifications/message","params":{"level":"info","data":"Session initialized"}}
```

Implementation note: SSE support may be deferred to a later phase.
All current tools are synchronous and return results directly in the
POST response. SSE would be useful for long-running operations or
server-pushed notifications.

---

## DELETE — Session Termination

Terminates an MCP session and cleans up server-side state.

```
DELETE /occ/v2/electronics/mcp
Authorization: Bearer <token>
MCP-Session-Id: sess_a1b2c3d4e5f6
```

**Response:** `200 OK` (no body)

After deletion, any requests with the terminated session ID will receive
an "Invalid or expired MCP-Session-Id" error.
