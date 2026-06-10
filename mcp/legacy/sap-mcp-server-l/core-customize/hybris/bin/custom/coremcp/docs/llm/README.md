# LLM Integration

The `coremcp` extension routes chat completion requests through a pluggable provider strategy.
The active provider is selected at runtime by the `coremcp.llm.provider` hybris property.

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
- `LlmProvider` (interface): one implementation per vendor.
- `AbstractHttpLlmProvider`: base for all providers — shared HttpClient, request timeouts, and bounded retry with exponential backoff for transient failures (429/5xx, connection errors). Streaming is never retried after the first delta.
- `AbstractOpenAiCompatibleLlmProvider`: shared JSON plumbing for any provider that speaks the OpenAI `/v1/chat/completions` protocol. Extended by `OpenAiLlmProvider` and `OpenAiCompatibleLlmProvider`.
- `AnthropicLlmProvider`: adapts Anthropic's content-block format to the OpenAI-style `choices[].message` shape that the rest of the agent expects.
- All providers registered with the alias pattern (`defaultOpenAiLlmProvider` → `openAiLlmProvider`, …) for downstream override.

## Configuration split

**Secrets (env vars only — never in properties files):**

| Env var | Required for provider |
|---|---|
| `OPENAI_API_KEY` | `openai` |
| `ANTHROPIC_API_KEY` | `anthropic` |
| `OPENAI_COMPATIBLE_API_KEY` | `openai-compatible` |

Set them in your shell, a `.env` file (loaded via `direnv` / `dotenv-cli`), or in CCv2 Cloud Portal → Environments → Configuration → Environment Variables.

**Everything else (plain text in `local.properties` with sensible defaults):**

### Routing

| Property | Default | Purpose |
|---|---|---|
| `coremcp.llm.provider` | `openai` | Provider id: `openai`, `anthropic`, or `openai-compatible` |
| `coremcp.llm.timeout.seconds` | `60` | Per-request timeout, non-streaming |
| `coremcp.llm.stream.timeout.seconds` | `120` | Per-request timeout, streaming |
| `coremcp.llm.retry.maxAttempts` | `3` | Total attempts for transient failures (429/5xx, connection errors) |
| `coremcp.llm.retry.baseDelayMillis` | `500` | Exponential backoff base (plus jitter) |

### `openai` — direct OpenAI

| Property | Default | Purpose |
|---|---|---|
| `coremcp.openai.model` | `gpt-4o` | Main chat model |
| `coremcp.openai.baseurl` | _(blank → api.openai.com)_ | Override the endpoint host |

### `anthropic` — direct Anthropic

| Property | Default | Purpose |
|---|---|---|
| `coremcp.anthropic.model` | `claude-3-5-sonnet-latest` | Main chat model |
| `coremcp.anthropic.maxTokens` | `1024` | `max_tokens` per response |
| `coremcp.anthropic.version` | `2023-06-01` | `anthropic-version` header |
| `coremcp.anthropic.baseurl` | _(blank → api.anthropic.com)_ | Override the endpoint host |

### `openai-compatible` — any OpenAI-protocol host

Use this for Azure OpenAI, OpenRouter, Kong-routed OpenAI, LocalAI, vLLM, llama.cpp server, Together AI, Anyscale — anything that speaks the OpenAI `/v1/chat/completions` protocol on a non-OpenAI host.

| Property | Default | Purpose |
|---|---|---|
| `coremcp.openai-compatible.baseurl` | _(required — no default)_ | Host base URL — provider is unusable without it |
| `coremcp.openai-compatible.model` | `gpt-4o` | Main chat model |
| `coremcp.openai-compatible.completions.path` | `/v1/chat/completions` | Override if the proxy rewrites the path |

## Adding a fourth provider

1. Create `MyVendorLlmProvider.java` in `src/com/coremcp/services/impl/`. Either:
   - Extend `AbstractOpenAiCompatibleLlmProvider` if the vendor speaks the OpenAI protocol (override the abstract getters using `Config.getString(...)` for non-secrets and `System.getenv(...)` for the api key).
   - Extend `AbstractHttpLlmProvider` directly if you need a different request/response shape, and adapt the response to `{ choices: [{ message: { content, tool_calls? }, finish_reason }] }`. Use `AnthropicLlmProvider` as the reference.
2. Pick a unique `providerId` (e.g. `mistral`). That's the value users will set in `coremcp.llm.provider`.
3. Register it in `coremcp-spring.xml` with the alias pattern and the shared parent: `<bean id="defaultMyVendorLlmProvider" parent="abstractHttpLlmProvider" class="..."/>` + `<alias name="defaultMyVendorLlmProvider" alias="myVendorLlmProvider"/>`, and add a `<ref bean="myVendorLlmProvider"/>` to the `defaultLlmClient.providers` list.
4. Document the env vars + properties at the top of the provider class and in this README.

## Notes

- Secrets are read via `System.getenv(...)` only. Non-secret reads use static `Config.getString(...)` / `Config.getInt(...)` at request time, except values wired through `${...}` placeholders in `coremcp-spring.xml` (retry policy, tunables) — those resolve from platform properties at boot.
- The `openai-compatible` provider throws `IllegalStateException` on first use if `coremcp.openai-compatible.baseurl` is unset.
- All non-secret defaults are also baked into the Java code as fallbacks. If `local.properties` is missing or a key is deleted, the providers still work with the hardcoded default — useful for tests where no properties file is wired.
