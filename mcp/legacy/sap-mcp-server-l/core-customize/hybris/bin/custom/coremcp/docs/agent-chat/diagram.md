# Agent Chat — Diagrams

## Full Request Flow

End-to-end trace from the client sending a chat message to receiving the agent's response. Two endpoints are exposed: a JSON one (`/agent/chat`) and a Server-Sent Events one (`/agent/chat/stream`). The client tries streaming first and transparently falls back to JSON when the gateway strips SSE or `coremcp.agent.streaming.enabled=false`.

```mermaid
sequenceDiagram
    participant Client
    participant OAuth as Spring Security (OAuth2)
    participant Controller as AgentController
    participant Cart as CartLoaderStrategy
    participant Agent as DefaultAgentService
    participant LLM as Anthropic Messages API<br/>(claude-sonnet-4-6)
    participant Tools as McpToolHandler[]

    Client->>OAuth: POST /occ/v2/{site}/agent/chat[/stream]<br/>Authorization: Bearer {token}
    OAuth->>Controller: Authenticated request

    Controller->>Controller: Parse body: messages[], cartCode
    Controller->>Cart: loadCartOrCurrent(cartCode)
    Cart-->>Controller: Cart loaded into session (or no-op)

    alt Streaming endpoint AND coremcp.agent.streaming.enabled=true
        Controller->>Controller: Set Content-Type: text/event-stream
        Controller->>Agent: chatStream(messages, onText, onTool)
    else Non-streaming (or kill-switch off)
        Controller->>Agent: chat(messages)
    end

    Note over Agent: All 20 tool definitions sent every turn —<br/>no classifier, no per-intent filtering.<br/>Anthropic prompt caching makes this cheap.

    loop Tool loop (max 10 iterations)
        Agent->>LLM: chatCompletion[Stream](messages, tools)<br/>system = [persona (cached), state snapshot]<br/>tools[last] has cache_control: ephemeral
        LLM-->>Agent: response with finish_reason

        alt finish_reason = "tool_calls"
            loop Each tool_call in response
                Agent->>Controller: onTool(toolName) — streaming only
                Controller-->>Client: event: tool / data: {name}
                Agent->>Tools: handler.execute(args)
                Tools-->>Agent: McpToolResult
                Agent->>Agent: Collect entityRefs from tool result
                Agent->>Agent: Append tool result as role:tool message
            end
        else finish_reason = "stop" (or other)
            Note over Agent: Streaming mode: text deltas already<br/>flushed to client during this round
            Agent->>Agent: Extract reply text, break loop
        end
    end

    Agent-->>Controller: { reply, messages, action?, entityRefs? }
    Controller->>Controller: Attach cartCode from session cart

    alt Streaming
        Controller-->>Client: event: done / data: {full result JSON}
    else Non-streaming
        Controller-->>Client: 200 OK { reply, messages, cartCode?, action?, entityRefs? }
    end
```

## Tool Execution Loop

Detail of `DefaultAgentService.runChat()`. Same body services both `chat()` (no-op text consumer → behaves like the old non-streaming path) and `chatStream()` (consumers wired to the SSE writer).

```mermaid
flowchart TD
    Start([runChat called]) --> BuildState[Build state snapshot<br/>customer + cart + applied vouchers]
    BuildState --> Prepend[Prepend persona + state<br/>as two system messages]
    Prepend --> Call[Call llmClient.chatCompletion or<br/>chatCompletionStream<br/>with messages + ALL tools]

    Call --> Stream{Streaming?}
    Stream -->|Yes| Emit[Emit text deltas to onText<br/>as SSE 'text' events]
    Stream -->|No| Buffer[Provider buffers full response]
    Emit --> Check
    Buffer --> Check

    Check{finish_reason =<br/>"tool_calls"?}

    Check -->|No| Extract[Extract reply from<br/>assistant message content]
    Extract --> Build[Build result:<br/>reply, messages, cartCode,<br/>action?, entityRefs?]
    Build --> Return([Return result Map])

    Check -->|Yes| Iterate{iteration < 10?}
    Iterate -->|No| Fallback([Return apology message<br/>+ entityRefs collected so far])

    Iterate -->|Yes| Dedupe{Already-seen<br/>tool+args?}
    Dedupe -->|Yes| Skip[Use placeholder: 'reuse previous result']
    Dedupe -->|No| ToolEvent[onTool toolName<br/>SSE 'tool' event]
    ToolEvent --> Lookup[Look up handler by name]
    Lookup --> Execute[handler.execute args]
    Execute --> CollectRefs[Collect entityRefs<br/>product/order/orderHistory codes]
    CollectRefs --> Append[Append role:tool message<br/>with result content]
    Skip --> Append
    Append --> Call
```

## SSE Event Stream

Wire format produced by `/agent/chat/stream`. The provider's auto-fallback to non-streaming and the controller's kill-switch both produce a plain `application/json` body instead — the client detects this via `Content-Type` and parses it directly.

```mermaid
sequenceDiagram
    participant Client
    participant Controller as AgentController
    participant Agent as DefaultAgentService
    participant Provider as AnthropicLlmProvider

    Client->>Controller: POST /agent/chat/stream
    Controller->>Controller: Set headers:<br/>Content-Type: text/event-stream<br/>Cache-Control: no-cache<br/>X-Accel-Buffering: no

    Controller->>Agent: chatStream(messages, onText, onTool)

    Note over Agent,Provider: Iteration 1 — tool-call round
    Agent->>Provider: chatCompletionStream(messages, tools, onText)
    Provider->>Provider: POST /v1/messages with stream:true
    Provider-->>Agent: content_block_start (tool_use)
    Provider-->>Agent: content_block_delta (input_json)*
    Provider-->>Agent: message_stop with usage
    Agent->>Agent: collectEntityRefs(...)
    Agent-->>Controller: onTool("order_history")
    Controller-->>Client: event: tool / data: {"name":"order_history"}

    Note over Agent,Provider: Iteration 2 — final reply round
    Agent->>Provider: chatCompletionStream(messages, tools, onText)
    Provider-->>Agent: content_block_delta (text_delta)
    Agent-->>Controller: onText("Here are ")
    Controller-->>Client: event: text / data: "Here are "
    Provider-->>Agent: content_block_delta (text_delta)
    Agent-->>Controller: onText("your orders…")
    Controller-->>Client: event: text / data: "your orders…"
    Provider-->>Agent: message_stop

    Agent-->>Controller: { reply, messages, cartCode?, action?, entityRefs? }
    Controller-->>Client: event: done / data: {full result JSON}

    Note over Client: If anything errored mid-stream:<br/>event: error / data: {"error":...}<br/>Client retries against /agent/chat
```

## Anthropic Prompt Caching

The persona prompt and tool definitions are stable across turns; the per-turn state snapshot is not. Two `cache_control: ephemeral` breakpoints — one on the persona system block, one on the last tool — let Anthropic cache the stable prefix for ~5 minutes between turns.

```mermaid
flowchart LR
    subgraph "Anthropic request body"
        direction TB
        S[system: array of blocks]
        T[tools: array]
        M[messages: conversation]
    end

    subgraph "Cached prefix (cache_control: ephemeral)"
        S1["#0 persona — STABLE<br/>cache_control: ephemeral"]
        S2["#1 state snapshot — per-turn<br/>(not cached)"]
        TN["tools[N-1] — last tool<br/>cache_control: ephemeral"]
    end

    S --> S1
    S --> S2
    T --> TN

    Note["First turn → cacheCreate=N tokens<br/>Subsequent turns within 5 min → cacheRead=N tokens"]
    S1 -.-> Note
    TN -.-> Note
```

## Cart Sync Flow

Unchanged. The controller manages cart state around the agent call so tool handlers that modify the cart operate on the correct cart.

```mermaid
sequenceDiagram
    participant Client
    participant Controller as AgentController
    participant Loader as CartLoaderStrategy
    participant Agent as AgentService
    participant CartSvc as CartService
    participant Handlers as Cart Tool Handlers

    Client->>Controller: { messages, cartCode: "00001001" }

    Note over Controller: Load cart before agent runs
    Controller->>Loader: loadCartOrCurrent("00001001")
    Loader-->>Controller: Cart set in Hybris session

    Controller->>Agent: chat / chatStream(messages)

    Note over Agent: During tool loop, cart tools<br/>operate on the session cart
    Agent->>Handlers: cart_add_product({ productCode, quantity })
    Handlers-->>Agent: Cart updated

    Agent-->>Controller: { reply, messages, ... }

    Note over Controller: Read cart code after agent finishes
    Controller->>CartSvc: hasSessionCart()?
    CartSvc-->>Controller: true
    Controller->>CartSvc: getSessionCart().getCode()
    CartSvc-->>Controller: "00001001"

    Controller-->>Client: { ..., cartCode: "00001001" }

    Note over Client: UI stores cartCode and sends<br/>it back on the next turn
```
