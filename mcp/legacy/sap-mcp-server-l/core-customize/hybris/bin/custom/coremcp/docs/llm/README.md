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
- `AbstractOpenAiCompatibleLlmProvider`: shared HTTP + JSON plumbing for any provider that speaks the OpenAI `/v1/chat/completions` protocol. Extended by `OpenAiLlmProvider` and `OpenAiCompatibleLlmProvider`.
- `AnthropicLlmProvider`: standalone — adapts Anthropic's content-block format to the OpenAI-style `choices[].message` shape that the rest of the agent expects.

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
| `coremcp.llm.timeout.seconds` | `60` | HTTP read timeout for chat completions |

### `openai` — direct OpenAI

| Property | Default | Purpose |
|---|---|---|
| `coremcp.openai.model` | `gpt-4o` | Main chat model |
| `coremcp.openai.intent.model` | `gpt-4o-mini` | Lighter model used for one-word intent classification |
| `coremcp.openai.baseurl` | _(blank → api.openai.com)_ | Override the endpoint host |

### `anthropic` — direct Anthropic

| Property | Default | Purpose |
|---|---|---|
| `coremcp.anthropic.model` | `claude-3-5-sonnet-latest` | Main chat model |
| `coremcp.anthropic.intent.model` | `claude-3-5-haiku-latest` | Intent classification model |
| `coremcp.anthropic.version` | `2023-06-01` | `anthropic-version` header |
| `coremcp.anthropic.baseurl` | _(blank → api.anthropic.com)_ | Override the endpoint host |

### `openai-compatible` — any OpenAI-protocol host

Use this for Azure OpenAI, OpenRouter, Kong-routed OpenAI, LocalAI, vLLM, llama.cpp server, Together AI, Anyscale — anything that speaks the OpenAI `/v1/chat/completions` protocol on a non-OpenAI host.

| Property | Default | Purpose |
|---|---|---|
| `coremcp.openai-compatible.baseurl` | _(required — no default)_ | Host base URL — provider is unusable without it |
| `coremcp.openai-compatible.model` | `gpt-4o` | Main chat model |
| `coremcp.openai-compatible.intent.model` | `gpt-4o-mini` | Intent classification model |
| `coremcp.openai-compatible.completions.path` | `/v1/chat/completions` | Override if the proxy rewrites the path |

## Adding a fourth provider

1. Create `MyVendorLlmProvider.java` in `src/com/coremcp/services/impl/`. Either:
   - Extend `AbstractOpenAiCompatibleLlmProvider` if the vendor speaks the OpenAI protocol (override the abstract getters using `Config.getString(...)` for non-secrets and `System.getenv(...)` for the api key).
   - Implement `LlmProvider` directly if you need a different request/response shape, and adapt the response to `{ choices: [{ message: { content, tool_calls? }, finish_reason }] }`. Use `AnthropicLlmProvider` as the reference.
2. Pick a unique `providerId` (e.g. `mistral`). That's the value users will set in `coremcp.llm.provider`.
3. Add a `<bean id="myVendorLlmProvider" class="..."/>` to `coremcp-spring.xml` and add a `<ref bean="myVendorLlmProvider"/>` to the `defaultLlmClient.providers` list.
4. Add a `case "mistral":` arm to `DefaultAgentService.resolveIntentModel()` if the vendor uses a different intent model.
5. Document the env vars + properties at the top of the provider class and in this README.

## Notes

- The hybris-extension Spring context does **not** register a `<context:property-placeholder/>`. All property reads go through static `Config.getString(...)` / `Config.getInt(...)` calls — the same pattern used elsewhere in the platform — and the three API keys are read directly via `System.getenv(...)`.
- The `openai-compatible` provider throws `IllegalStateException` on first use if `coremcp.openai-compatible.baseurl` is unset.
- All non-secret defaults are also baked into the Java code as fallbacks. If `local.properties` is missing or a key is deleted, the providers still work with the hardcoded default — useful for tests where no properties file is wired.
