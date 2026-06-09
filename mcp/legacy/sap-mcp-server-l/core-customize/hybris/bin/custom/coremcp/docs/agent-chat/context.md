# Agent Chat — Context

## What It Does

An AI-powered shopping assistant for ThinkShop. Users send natural-language messages and the agent responds conversationally, using SAP Commerce tools behind the scenes to search products, manage the cart, check promotions, and complete checkout. The agent runs through a provider-neutral abstraction (`LlmClient`) over Anthropic, OpenAI, or any OpenAI-compatible gateway, looping through tool invocations until it has enough information to reply. Replies stream to the client over Server-Sent Events when supported, or are returned as JSON otherwise.

## When It's Used

- React UI chat widget sends the full conversation history on each turn
- Any OAuth-authenticated client can POST to either endpoint for a single-turn or multi-turn conversation
- The agent is the primary interface for the SAP-MCP-UI frontend

## Endpoints

```
POST /occ/v2/{baseSiteId}/agent/chat          # JSON response (always works)
POST /occ/v2/{baseSiteId}/agent/chat/stream   # text/event-stream when enabled, else JSON

Authorization: Bearer {oauthToken}
Content-Type: application/json
Roles: ROLE_CUSTOMERGROUP, ROLE_TRUSTED_CLIENT
```

The streaming endpoint is gated by `coremcp.agent.streaming.enabled` (default `true`). When the flag is off, or when the upstream LLM gateway strips SSE, the endpoint transparently returns plain JSON in the same shape as `/agent/chat` — clients fall back without special-casing.

## Key Decisions

1. **No intent classifier — full tool set every turn.** An earlier design ran a Haiku call before the main agent to classify intent (browse/cart/checkout) and filter tools accordingly. Profiling showed that classifier added 1.3–2.3 s per turn AND caused the Anthropic prompt cache to thrash whenever the user shifted intents. We removed it. Sonnet picks tools fine from all 18 definitions; with prompt caching the marginal token cost of the extra tools is negligible.

2. **Anthropic prompt caching on persona + tools.** The system prompt is sent as an array of content blocks: block 0 (the stable persona) gets `cache_control: {type: "ephemeral"}`; block 1 (the per-turn state snapshot) is uncached. The last tool definition also gets `cache_control: ephemeral`. Two breakpoints out of Anthropic's four; cached prefix is ~4400 input tokens once warm. First turn writes to cache, subsequent turns within ~5 minutes read from it.

3. **Tool loop with max iterations.** The agent calls the LLM, checks `finish_reason`, and if it is `"tool_calls"`, executes each requested tool and feeds the results back as `role: tool` messages. This repeats until the model returns a final text response (`finish_reason` is not `"tool_calls"`) or the loop hits `MAX_TOOL_ITERATIONS` (10). On max iterations, the agent returns a fallback apology message. Within a single conversation, identical `(toolName, args)` invocations are short-circuited to keep the model from spinning.

4. **Streaming with graceful fallback.** `chatStream()` runs the same loop as `chat()` but threads a text-delta consumer through to the provider. When Anthropic returns the response as SSE, text deltas are forwarded to the SSE writer in real time; when the gateway strips SSE (non-`text/event-stream` content type) or returns an HTTP error, the provider's auto-fallback drains the response, calls `chatCompletion` non-streamed, and emits the full text as one chunk — the agent service sees no difference. The endpoint also emits `event: tool` whenever a tool call starts so the UI can render a transient "Looking up your orders…" status.

5. **Entity references for UI deep-linking.** During the tool loop, `collectEntityRefs` extracts product codes (from `product_search`, `product_get`), order codes (from `order_get`, `order_history`), and a top-level `orderHistory` marker. These are returned as `entityRefs: [{type, code}]` in the response and rendered as clickable chips below the assistant message in the chat UI. Each chip opens a focused modal (`ProductModal`, `OrderModal`, `OrderHistoryModal`).

6. **System prompt design.** A single persona prompt defines the agent's voice (knowledgeable friend with the ThinkShop catalog), explains the chips/modals system so the LLM doesn't try to fabricate URLs, specifies the checkout flow order (add to cart → set delivery address → set delivery mode → set payment → place order), requires `SUGGESTIONS:[...]` at the end of every response for the UI to render quick-action buttons, and mandates use of the `ui_action` tool for checkout navigation.

7. **Cart loading per request.** The controller loads the user's cart into the Hybris session before calling the agent. It prefers an explicit `cartCode` from the request body; falls back to `"current"` (most recently modified cart). After the agent finishes, the controller reads the session cart code and returns it in the response so the UI stays in sync across turns.

8. **UI action support.** The agent has a `ui_action` tool that does not call any commerce facade. When invoked, `DefaultAgentService` captures the `action` parameter (e.g., `"checkout"`) and includes it in the response as `"action"`. The UI uses this to trigger client-side navigation. Note: the UI also has a deterministic regex (`CHECKOUT_INTENT_REGEX`) that bypasses the LLM for explicit "proceed to checkout" phrasings.

9. **LLM provider routing.** `DefaultLlmClient` dispatches to one of three providers (`openai`, `anthropic`, `openai-compatible`) selected by the `coremcp.llm.provider` property (default: `openai`; production default: `anthropic`). The `LlmProvider` interface defines both `chatCompletion` and `chatCompletionStream`; non-streaming providers inherit a default streaming impl that emits the full reply as one chunk. See `coremcp/docs/llm/README.md` for the full env var reference.

10. **Model selection.** With `anthropic` (the production default), the main chat model is `coremcp.anthropic.model` (default `claude-sonnet-4-6`). Per-call model overrides go through `chatCompletion(messages, tools, modelOverride)` — used historically for the now-removed intent classifier.

11. **Tool result truncation on echo-back.** Tool results larger than 300 chars get truncated to 200 chars before being echoed back to the client (and back into the next turn's conversation history). Important `url` fields embedded in tool results are extracted and appended to the truncated summary so deep-link information survives.

12. **Conversation history management.** The client sends the full message history. The agent prepends the persona prompt + state snapshot, runs the tool loop, then returns the updated history (minus the two system messages) along with the final `reply` text. This keeps the prompt server-side while letting the client manage conversation state.

13. **Promotions and coupons.** The system prompt explicitly instructs the agent to call `promotions_get` for coupon eligibility questions and never guess from order history. The tool returns per-customer redemption counts for accurate eligibility checks.

## API Contract

```
Request (both endpoints):
{
  "messages": [
    { "role": "user", "content": "Show me cameras under $200" }
  ],
  "cartCode": "00001001"   // optional, falls back to "current"
}

Response from /agent/chat (always JSON):
{
  "reply": "Here are some cameras under $200: ...\nSUGGESTIONS:[...]",
  "messages": [ ... full conversation history without system prompt ... ],
  "cartCode": "00001001",         // present if a session cart exists
  "action": "checkout",           // present only if ui_action tool was called
  "entityRefs": [                 // present when tools touched products/orders
    { "type": "orderHistory" },
    { "type": "order", "code": "THINK-0001" }
  ]
}

Response from /agent/chat/stream:
  Content-Type: text/event-stream when streaming is engaged
    event: tool   data: {"name":"order_history"}     # zero or more, before/between rounds
    event: text   data: "Here "                       # zero or more text deltas
    event: text   data: "are your orders…"
    event: done   data: { same shape as /agent/chat response }
    event: error  data: {"error":"…"}                 # mid-stream errors only
  Content-Type: application/json when streaming is disabled or unsupported
    Body is the same shape as /agent/chat response

Errors: 400 (missing messages), 500 (provider/tool failure), 401 (auth)
```
