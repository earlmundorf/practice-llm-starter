# LLM providers — architecture and configuration

The agent and visual search run against a runtime-selectable LLM backend behind
the `LlmClient` abstraction. Three providers ship with the extension; all extend
`AbstractHttpLlmProvider`, which owns the shared HTTP client, timeouts, and the
transient-failure retry policy. Defaults for every property below live (with
comments) in [`project.properties`](../project.properties).

## Architecture

```
DefaultAgentService          ──┐
DefaultVisualSearchService   ──┤───►  LlmClient  ──►  DefaultLlmClient  ──►  LlmProvider (selected by id)
                                                                              │
                                                                              ├──►  OpenAiLlmProvider
                                                                              ├──►  AnthropicLlmProvider
                                                                              └──►  OpenAiCompatibleLlmProvider
                                                                                    (Azure / OpenRouter / Kong /
                                                                                     vLLM / LocalAI / Together / ...)
```

- `LlmClient` (interface): the provider-neutral API consumed by agent + visual search services.
- `DefaultLlmClient`: reads `coremcp.llm.provider` and dispatches to the matching `LlmProvider`.
- `LlmProvider` (interface): one implementation per vendor; the default `chatCompletionStream` falls back to non-streaming and emits one chunk, so vendors that can't stream still satisfy the contract.
- `AbstractHttpLlmProvider`: base for all providers — shared HttpClient, request timeouts, and bounded retry with exponential backoff for transient failures (429/5xx, connection errors). Streaming is never retried after the first delta.
- `AbstractOpenAiCompatibleLlmProvider`: shared JSON plumbing for any provider speaking the OpenAI `/v1/chat/completions` protocol. Extended by `OpenAiLlmProvider` and `OpenAiCompatibleLlmProvider`.
- `AnthropicLlmProvider`: adapts Anthropic's content-block format to the OpenAI-style `choices[].message` shape the agent loop expects; true SSE streaming with non-streaming fallback; tags the persona system block and tool definitions with `cache_control: ephemeral` for Anthropic's prompt cache.

## Provider selection

Set `coremcp.llm.provider` to one of:

- `openai` — api.openai.com (or a gateway via `coremcp.openai.baseurl`)
- `anthropic` — api.anthropic.com (or a gateway via `ANTHROPIC_BASE_URL` / `coremcp.anthropic.baseurl`)
- `openai-compatible` — any host speaking the OpenAI chat-completions protocol
  (Azure OpenAI, OpenRouter, Kong-fronted gateways, vLLM, LocalAI, …)

## Secrets — environment variables ONLY

API keys are read exclusively via `System.getenv()`; a key placed in a
properties file is ignored by design (see SECURITY.md):

| Env var | Used by provider |
|---|---|
| `OPENAI_API_KEY` | `openai` |
| `ANTHROPIC_API_KEY` | `anthropic` |
| `OPENAI_COMPATIBLE_API_KEY` | `openai-compatible` |

On CCv2, set these as Cloud Portal environment variables.

## Shared HTTP behavior (AbstractHttpLlmProvider)

| Property | Default | Purpose |
|---|---|---|
| `coremcp.llm.timeout.seconds` | 60 | Per-request timeout, non-streaming calls |
| `coremcp.llm.stream.timeout.seconds` | 120 | Per-request timeout, streaming calls (connect + headers) |
| `coremcp.llm.retry.maxAttempts` | 3 | Total attempts for 429/500/502/503 and connection errors |
| `coremcp.llm.retry.baseDelayMillis` | 500 | Exponential backoff base (plus jitter) |

Streaming responses are **never retried** once the first delta has been emitted —
a failed stream falls back to the (retried) non-streaming path instead.

## Direct OpenAI (`openai`)

| Property | Default | Purpose |
|---|---|---|
| `coremcp.openai.model` | `gpt-4o` | Chat model |
| `coremcp.openai.baseurl` | (blank → api.openai.com) | Gateway override |

## Direct Anthropic (`anthropic`)

| Property / env | Default | Purpose |
|---|---|---|
| `coremcp.anthropic.model` | `claude-3-5-sonnet-latest` | Chat model |
| `coremcp.anthropic.maxTokens` | 1024 | `max_tokens` per response |
| `coremcp.anthropic.version` | `2023-06-01` | API version header |
| `ANTHROPIC_BASE_URL` (env) / `coremcp.anthropic.baseurl` | (api.anthropic.com) | Gateway override (`/v1/messages` appended when missing) |

Anthropic responses are normalized into the OpenAI-style
`choices → message → content/tool_calls` shape (see `AnthropicLlmProvider`), so
the agent loop is provider-agnostic. The provider implements true SSE streaming
with automatic non-streaming fallback, and tags the persona system block and
tool definitions with `cache_control: ephemeral` for Anthropic's prompt cache.

## OpenAI-compatible (`openai-compatible`)

| Property | Default | Purpose |
|---|---|---|
| `coremcp.openai-compatible.baseurl` | **required** | Host base URL |
| `coremcp.openai-compatible.model` | `gpt-4o` | Chat model |
| `coremcp.openai-compatible.completions.path` | `/v1/chat/completions` | Path override for proxies that rewrite it |

## Vision

| Property | Default | Purpose |
|---|---|---|
| `coremcp.visualsearch.model` | `gpt-4o` | Model used by visual search (`modelOverride` per call) |
| `coremcp.<provider>.vision.enabled` | provider-specific | Whether the agent accepts image content (surfaced via `/agent/capabilities`) |

## Wiring and overriding

All three providers are registered with the SAP alias pattern
(`defaultOpenAiLlmProvider` → `openAiLlmProvider`, etc.), so a downstream
extension can substitute a custom provider by redefining the alias.
`DefaultLlmClient` (alias `llmClient`) routes by `coremcp.llm.provider`.

## Adding a fourth provider

1. Create `MyVendorLlmProvider.java` in `src/com/coremcp/services/impl/`. Either:
   - extend `AbstractOpenAiCompatibleLlmProvider` if the vendor speaks the OpenAI
     protocol (override the abstract getters — `Config.getString(...)` for
     non-secrets, `System.getenv(...)` for the API key), or
   - extend `AbstractHttpLlmProvider` directly for a different wire format, and
     adapt the response to `{ choices: [{ message: { content, tool_calls? },
     finish_reason }] }` — use `AnthropicLlmProvider` as the reference.
2. Pick a unique `providerId` (e.g. `mistral`) — the value users set in
   `coremcp.llm.provider`.
3. Register it in `coremcp-spring.xml` with the alias pattern and the shared
   parent: `<bean id="defaultMyVendorLlmProvider" parent="abstractHttpLlmProvider"
   class="..."/>` + `<alias .../>`, and add a `<ref/>` to the
   `defaultLlmClient.providers` list.
4. Document the env var + properties at the top of the provider class and in
   this file.

## Notes

- Secrets are read via `System.getenv(...)` only. Non-secret reads use static
  `Config.getString(...)` / `Config.getInt(...)` at request time, except values
  wired through `${...}` placeholders in `coremcp-spring.xml` (retry policy,
  tunables) — those resolve from platform properties at boot.
- The `openai-compatible` provider throws `IllegalStateException` on first use
  if `coremcp.openai-compatible.baseurl` is unset.
- All non-secret defaults are also baked into the Java code as fallbacks, so the
  providers work in tests with no properties file wired.
