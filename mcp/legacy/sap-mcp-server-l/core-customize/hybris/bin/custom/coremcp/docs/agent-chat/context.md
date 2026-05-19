# Agent Chat — Context

## What It Does

An AI-powered shopping assistant for ThinkShop. Users send natural-language messages and the agent responds conversationally, using SAP Commerce tools behind the scenes to search products, manage the cart, check promotions, and complete checkout. The agent runs on OpenAI's Chat Completions API with function calling, looping through tool invocations until it has enough information to reply.

## When It's Used

- React UI chat widget sends the full conversation history on each turn
- Any OAuth-authenticated client can POST to the endpoint for a single-turn or multi-turn conversation
- The agent is the primary interface for the SAP-MCP-UI frontend

## Endpoint

```
POST /occ/v2/{baseSiteId}/agent/chat
Authorization: Bearer {oauthToken}
Content-Type: application/json
Roles: ROLE_CUSTOMERGROUP, ROLE_TRUSTED_CLIENT
```

## Key Decisions

1. **Intent classification before tool selection** — Before calling the main model, a lightweight call to `gpt-4o-mini` classifies the user's last message as `browse`, `cart`, or `checkout`. This determines which subset of tools the main model sees, reducing token cost and preventing the model from calling irrelevant tools (e.g., checkout tools when the user is just browsing). The intent model and prompt are separate from the main conversation.

2. **Tool loop with max iterations** — The agent calls OpenAI, checks `finish_reason`, and if it is `"tool_calls"`, executes each requested tool and feeds the results back as `role: tool` messages. This repeats until the model returns a final text response (`finish_reason` is not `"tool_calls"`) or the loop hits `MAX_TOOL_ITERATIONS` (10). On max iterations, the agent returns a fallback apology message.

3. **System prompt design** — A single system prompt defines the agent's persona (ThinkShop shopping assistant), instructs it to use tools for data access, specifies the checkout flow order (add to cart, set delivery address, set delivery mode, set payment, place order), requires `SUGGESTIONS:[...]` at the end of every response for the UI to render quick-action buttons, and mandates use of the `ui_action` tool for checkout navigation.

4. **Cart loading per request** — The controller loads the user's cart into the Hybris session before calling the agent. It prefers an explicit `cartCode` from the request body; falls back to `"current"` (most recently modified cart). After the agent finishes, the controller reads the session cart code and returns it in the response so the UI stays in sync across turns.

5. **UI action support** — The agent has a `ui_action` tool that does not call any commerce facade. When invoked, `DefaultAgentService` captures the `action` parameter (e.g., `"checkout"`) and includes it in the response as `"action"`. The UI uses this to trigger client-side navigation (e.g., redirect to checkout page).

6. **OpenAI API key resolution** — `DefaultOpenAiClient` first checks `coremcp.openai.apikey` in Hybris config (`local.properties`). If missing, blank, or an unresolved placeholder (`${...}`), it falls back to the `OPENAI_API_KEY` environment variable. Fails with `IllegalStateException` if neither is set.

7. **Model configuration** — The main chat model defaults to `gpt-4o` (overridable via `coremcp.openai.model` in `local.properties`). The intent classification model defaults to `gpt-4o-mini` (overridable via `coremcp.openai.intent.model`). The intent call uses the 3-argument `chatCompletion(messages, tools, modelOverride)` method to specify the lighter model.

8. **Conversation history management** — The client sends the full message history. The agent prepends the system prompt, runs the tool loop, then returns the updated history (minus the system prompt) along with the final `reply` text. This keeps the system prompt server-side while letting the client manage conversation state.

9. **Promotions and coupons** — The system prompt explicitly instructs the agent to call `promotions_get` for coupon eligibility questions and never guess from order history. The tool returns per-customer redemption counts for accurate eligibility checks.

## API Contract

```
Request:
{
  "messages": [
    { "role": "user", "content": "Show me cameras under $200" }
  ],
  "cartCode": "00001001"   // optional, falls back to "current"
}

Response:
{
  "reply": "Here are some cameras under $200: ...\nSUGGESTIONS:[\"Add to cart\",\"View details\",\"Search again\"]",
  "messages": [ ... full conversation history without system prompt ... ],
  "cartCode": "00001001",  // present if a session cart exists
  "action": "checkout"     // present only if ui_action tool was called
}

Errors: 400 (missing messages), 500 (OpenAI/tool failure)
```
