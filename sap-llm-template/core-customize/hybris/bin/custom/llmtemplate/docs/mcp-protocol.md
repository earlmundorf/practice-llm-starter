# MCP Protocol Reference

This document covers the MCP (Model Context Protocol) specification as
implemented by llmtemplate. Based on **MCP spec version 2025-11-25** using
**Streamable HTTP** transport.

## JSON-RPC 2.0

MCP uses JSON-RPC 2.0 as its message format. Every message has a `jsonrpc`
field set to `"2.0"`.

### Request

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": { ... }
}
```

- `id` — Unique request identifier (integer or string). Present on all
  requests that expect a response.
- `method` — The RPC method name.
- `params` — Method-specific parameters (object).

### Response (success)

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": { ... }
}
```

### Response (error)

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

### Notification

A request with no `id` field. The server does not send a response.

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

### Standard Error Codes

| Code   | Meaning              |
|--------|----------------------|
| -32700 | Parse error          |
| -32600 | Invalid request      |
| -32601 | Method not found     |
| -32602 | Invalid params       |
| -32603 | Internal error       |

## Session Lifecycle

```
  Client                                 Server
    |                                      |
    |  POST  initialize                    |
    |  MCP-Protocol-Version: 2025-11-25    |
    |------------------------------------->|
    |                                      |
    |  200 OK                              |
    |  MCP-Session-Id: abc123              |
    |  { result: { protocolVersion,        |
    |    capabilities, serverInfo } }      |
    |<-------------------------------------|
    |                                      |
    |  POST  notifications/initialized     |
    |  MCP-Session-Id: abc123              |
    |------------------------------------->|
    |                                      |
    |  202 Accepted (no body)              |
    |<-------------------------------------|
    |                                      |
    |  POST  tools/list                    |
    |  MCP-Session-Id: abc123              |
    |------------------------------------->|
    |                                      |
    |  200 OK                              |
    |  { result: { tools: [...] } }        |
    |<-------------------------------------|
    |                                      |
    |  POST  tools/call                    |
    |  MCP-Session-Id: abc123              |
    |  { params: { name: "product_search", |
    |    arguments: { query: "camera" } } }|
    |------------------------------------->|
    |                                      |
    |  200 OK                              |
    |  { result: { content: [...] } }      |
    |<-------------------------------------|
    |                                      |
    |  DELETE                              |
    |  MCP-Session-Id: abc123              |
    |------------------------------------->|
    |                                      |
    |  200 OK (session terminated)         |
    |<-------------------------------------|
```

## Capability Negotiation

During `initialize`, the client and server exchange capabilities.

### Server Capabilities (what llmtemplate advertises)

```json
{
  "capabilities": {
    "tools": {
      "listChanged": false
    },
    "logging": {}
  }
}
```

- **tools** — Server supports `tools/list` and `tools/call`.
  `listChanged: false` means the tool list is static (no
  `notifications/tools/list_changed` will be sent).
- **logging** — Server supports `logging/setLevel` and can emit
  `notifications/message` log entries.

### Client Capabilities

The server does not require specific client capabilities. Clients
typically advertise `roots` and `sampling` but llmtemplate ignores these.

## Required Headers

| Header                  | When             | Value                   |
|-------------------------|------------------|-------------------------|
| `Content-Type`          | All POST         | `application/json`      |
| `Authorization`         | All requests     | `Bearer <oauth2-token>` |
| `MCP-Protocol-Version`  | `initialize`     | `2025-11-25`            |
| `MCP-Session-Id`        | After initialize | Value from init response|
| `Accept`                | POST (optional)  | `application/json, text/event-stream` |

## Transport: Streamable HTTP

MCP 2025-11-25 uses Streamable HTTP transport (replaces the deprecated
stdio and SSE transports for HTTP servers).

- **POST** to the MCP endpoint for all client-to-server messages
- **GET** to the MCP endpoint opens an SSE stream for server-initiated
  messages (optional — llmtemplate may defer this)
- **DELETE** to the MCP endpoint terminates the session

The server MAY respond to POST with either:
1. A single JSON response (`Content-Type: application/json`)
2. An SSE stream (`Content-Type: text/event-stream`) for long-running ops

llmtemplate uses option 1 (single JSON response) for all tool calls since
commerce facade calls are synchronous.
