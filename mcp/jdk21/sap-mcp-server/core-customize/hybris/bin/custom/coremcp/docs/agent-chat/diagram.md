# Agent Chat — Diagrams

## Full Request Flow

End-to-end trace from the client sending a chat message to receiving the agent's response.

```mermaid
sequenceDiagram
    participant Client
    participant OAuth as Spring Security (OAuth2)
    participant Controller as AgentController
    participant Cart as CartLoaderStrategy
    participant Agent as DefaultAgentService
    participant Intent as OpenAI (gpt-4o-mini)
    participant LLM as OpenAI (gpt-4o)
    participant Tools as McpToolHandler[]

    Client->>OAuth: POST /occ/v2/{site}/agent/chat<br/>Authorization: Bearer {token}
    OAuth->>Controller: Authenticated request

    Controller->>Controller: Parse body: messages[], cartCode
    Controller->>Cart: loadCart(cartCode or "current")
    Cart-->>Controller: Cart loaded into session (or no-op)

    Controller->>Agent: chat(messages)

    Agent->>Intent: classifyIntent(lastUserMessage)
    Intent-->>Agent: "browse" | "cart" | "checkout"
    Agent->>Agent: Select filtered tool definitions for intent

    Agent->>Agent: Prepend system prompt to messages

    loop Tool loop (max 10 iterations)
        Agent->>LLM: chatCompletion(messages, filteredTools)
        LLM-->>Agent: response with finish_reason

        alt finish_reason = "tool_calls"
            loop Each tool_call in response
                Agent->>Tools: handler.execute(args)
                Tools-->>Agent: McpToolResult
                Agent->>Agent: Append tool result as role:tool message
            end
        else finish_reason = "stop" (or other)
            Agent->>Agent: Extract reply text, break loop
        end
    end

    Agent-->>Controller: { reply, messages, action? }
    Controller->>Controller: Attach cartCode from session cart
    Controller-->>Client: 200 OK { reply, messages, cartCode?, action? }
```

## Intent Classification and Tool Filtering

The intent classifier determines which tools are visible to the main model. This reduces token usage and prevents out-of-context tool calls.

```mermaid
graph TD
    UM[Last user message] --> IC[Intent Classifier<br/>gpt-4o-mini]

    IC -->|"browse"| BT[Browse Tools — 8]
    IC -->|"cart"| CT[Cart Tools — 11]
    IC -->|"checkout"| AT[All Tools — 16]

    subgraph "Browse Tools"
        BT --> B1[product_search]
        BT --> B2[product_get]
        BT --> B3[cart_add_product]
        BT --> B4[customer_get]
        BT --> B5[customer_lookup]
        BT --> B6[order_history]
        BT --> B7[order_get]
        BT --> B8[promotions_get]
    end

    subgraph "Cart Tools (adds 3)"
        CT --> C1[cart_get]
        CT --> C2[cart_update_entry]
        CT --> C3[cart_remove_entry]
        CT -.->|plus all browse tools| BT
    end

    subgraph "Checkout Tools (adds 5)"
        AT --> A1[checkout_set_delivery_address]
        AT --> A2[checkout_set_delivery_mode]
        AT --> A3[checkout_set_payment]
        AT --> A4[order_place]
        AT --> A5[ui_action]
        AT -.->|plus all cart tools| CT
    end
```

## Tool Execution Loop

Detail of the iterative tool-calling loop inside `DefaultAgentService.chat()`.

```mermaid
flowchart TD
    Start([chat called]) --> Classify[Classify intent via gpt-4o-mini]
    Classify --> Select[Select tool definitions for intent]
    Select --> Prepend[Prepend system prompt to messages]
    Prepend --> Call[Call OpenAI chatCompletion<br/>with messages + tools]

    Call --> Check{finish_reason =<br/>"tool_calls"?}

    Check -->|No| Extract[Extract reply from<br/>assistant message content]
    Extract --> Return([Return reply + messages + action])

    Check -->|Yes| Iterate{iteration < 10?}

    Iterate -->|No| Fallback([Return apology message])

    Iterate -->|Yes| ExecTools[For each tool_call:]
    ExecTools --> Lookup[Look up handler by name]
    Lookup --> Execute[handler.execute args]
    Execute --> UICheck{tool = ui_action?}
    UICheck -->|Yes| Capture[Capture action value]
    UICheck -->|No| Skip[ ]
    Capture --> Append[Append role:tool message<br/>with result content]
    Skip --> Append
    Append --> Call
```

## Cart Sync Flow

The controller manages cart state around the agent call so tool handlers that modify the cart (add, update, remove) operate on the correct cart.

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
    Controller->>Loader: loadCart("00001001")
    Loader-->>Controller: Cart set in Hybris session

    Controller->>Agent: chat(messages)

    Note over Agent: During tool loop, cart tools<br/>operate on the session cart
    Agent->>Handlers: cart_add_product({ productCode: "1234", qty: 2 })
    Handlers-->>Agent: Cart updated

    Agent-->>Controller: { reply, messages }

    Note over Controller: Read cart code after agent finishes
    Controller->>CartSvc: hasSessionCart()?
    CartSvc-->>Controller: true
    Controller->>CartSvc: getSessionCart().getCode()
    CartSvc-->>Controller: "00001001"

    Controller-->>Client: { reply, messages, cartCode: "00001001" }

    Note over Client: UI stores cartCode and sends<br/>it back on the next turn
```
