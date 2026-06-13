# LLM providers — architecture and configuration

The agent and visual search run against a runtime-selectable LLM backend behind
the `LlmClient` abstraction. Four providers ship with the extension; all extend
`AbstractHttpLlmProvider`, which owns the shared HTTP client, timeouts, and the
transient-failure retry policy. Defaults for every property below live (with
comments) in [`project.properties`](../project.properties).

If you just want to switch providers, jump to
[Choosing and switching providers](#choosing-and-switching-providers).

## Architecture

```
DefaultAgentService          ──┐
DefaultVisualSearchService   ──┤───►  LlmClient  ──►  DefaultLlmClient  ──►  LlmProvider (selected by id)
                                                                              │
                                                                              ├──►  OpenAiLlmProvider
                                                                              ├──►  AnthropicLlmProvider
                                                                              ├──►  GeminiLlmProvider
                                                                              └──►  OpenAiCompatibleLlmProvider
                                                                                    (anything else speaking the OpenAI
                                                                                     protocol — Azure / OpenRouter /
                                                                                     Kong / vLLM / LocalAI / Together)
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
- `gemini` — generativelanguage.googleapis.com (or a gateway via `coremcp.gemini.baseurl`)
- `openai-compatible` — any host speaking the OpenAI chat-completions protocol
  (Azure OpenAI, OpenRouter, Kong-fronted gateways, vLLM, LocalAI, …)

## Secrets — environment variables ONLY

API keys are read exclusively via `System.getenv()`; a key placed in a
properties file is ignored by design (see SECURITY.md):

| Env var | Used by provider |
|---|---|
| `OPENAI_API_KEY` | `openai` |
| `ANTHROPIC_API_KEY` | `anthropic` |
| `GEMINI_API_KEY` | `gemini` |
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

## Direct Gemini (`gemini`)

| Property | Default | Purpose |
|---|---|---|
| `coremcp.gemini.model` | `gemini-2.5-flash` | Chat model |
| `coremcp.gemini.baseurl` | (blank → generativelanguage.googleapis.com) | Gateway override |

Gemini speaks the OpenAI chat-completions protocol on the
`/v1beta/openai/chat/completions` path, so the provider extends
`AbstractOpenAiCompatibleLlmProvider` and inherits all shared HTTP/JSON/retry
behavior. Vision works on `gemini-2.5-pro` / `gemini-2.5-flash` (set
`coremcp.visualsearch.model` to a Gemini vision model when using the
`/agent/visual-search` endpoint with this provider).

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

All four providers are registered with the SAP alias pattern
(`defaultOpenAiLlmProvider` → `openAiLlmProvider`, etc.), so a downstream
extension can substitute a custom provider by redefining the alias.
`DefaultLlmClient` (alias `llmClient`) routes by `coremcp.llm.provider`.

## Choosing and switching providers

Each recipe below has the same five steps. Pick the provider that matches the
key you have, follow the steps, and run `./scripts/smoke-test.sh` to confirm
22/22 green.

> **The apply step is the same for all four providers:**
> ```bash
> cd core-customize
> ./gradlew setupConfig stopServer startServer
> ```
> `setupConfig` re-copies `dev-config/` into the gitignored `hybris/config/` —
> without it, edits to `dev-config/local.properties` don't reach the running
> server. After Java source changes (rare for provider switches), use the
> longer `./gradlew yclean ybuild stopServer startServer` cycle instead.
>
> **CCv2:** set env vars in Cloud Portal → Environments → Configuration →
> Environment Variables; commit the `dev-config/local-*.properties` change for
> the persona that owns that environment (`local-dev`, `local-stg`, `local-prod`).

### Recipe: OpenAI

1. **Get an API key** — https://platform.openai.com/api-keys (create a
   project-scoped key with `model.request` permission).
2. **Set the env var:**
   ```bash
   export OPENAI_API_KEY=sk-...
   ```
3. **Edit `core-customize/dev-config/local.properties`:**
   ```properties
   coremcp.llm.provider=openai
   coremcp.openai.model=gpt-4o
   coremcp.openai.baseurl=
   ```
4. **Apply config + restart server** (see boxed command above).
5. **Verify:**
   ```bash
   curl -k https://localhost:9002/occ/v2/electronics/agent/capabilities \
     -H "Authorization: Bearer $TOKEN"
   ./scripts/smoke-test.sh
   ```
   The capabilities response should include `"vision":true`. The smoke test's
   live LLM round-trip (#21) and KB-grounded round-trip (#22) confirm
   end-to-end success.

### Recipe: Anthropic

1. **Get an API key** — https://console.anthropic.com/settings/keys, or use a
   Capgemini Generative Engine key for gateway routing.
2. **Set the env vars:**
   ```bash
   export ANTHROPIC_API_KEY=sk-ant-...
   # Optional — only when routing through a gateway (e.g. Capgemini Generative Engine):
   export ANTHROPIC_BASE_URL=https://anthropic.generative.engine.capgemini.com
   ```
   When `ANTHROPIC_BASE_URL` is set, the provider appends `/v1/messages`
   automatically if the URL doesn't end with it.
3. **Edit `core-customize/dev-config/local.properties`:**
   ```properties
   coremcp.llm.provider=anthropic
   coremcp.anthropic.model=claude-sonnet-4-6
   coremcp.anthropic.version=2023-06-01
   coremcp.anthropic.baseurl=
   ```
4. **Apply config + restart server** (see boxed command above).
5. **Verify:** same as OpenAI. Anthropic adds prompt caching (the persona
   system block + tool definitions are tagged `cache_control: ephemeral`), so
   you should see latency drop on the second smoke-test request.

### Recipe: Gemini

1. **Get an API key** — https://aistudio.google.com/app/apikey (free tier
   available; for higher volume use a Vertex AI key on the OpenAI-compatible
   provider with a Vertex gateway URL instead).
2. **Set the env var:**
   ```bash
   export GEMINI_API_KEY=...
   ```
3. **Edit `core-customize/dev-config/local.properties`:**
   ```properties
   coremcp.llm.provider=gemini
   coremcp.gemini.model=gemini-2.5-flash
   coremcp.gemini.baseurl=
   ```
4. **Apply config + restart server** (see boxed command above).
5. **Verify:** same as OpenAI. If the live round-trip (#21) fails with
   `model not found`, your Google API project may not have access to
   `gemini-2.5-flash` — try `gemini-2.0-flash` or `gemini-1.5-flash` instead.
   For visual search with Gemini, also set
   `coremcp.visualsearch.model=gemini-2.5-flash` (or `-pro`).

### Recipe: OpenAI-compatible (Azure / OpenRouter / Kong / vLLM / Together / …)

1. **Get an API key** — vendor-specific. Azure OpenAI keys come from the Azure
   portal; OpenRouter keys from openrouter.ai/keys; Kong/vLLM/LocalAI keys
   from your gateway config.
2. **Set the env var (always this name, regardless of vendor):**
   ```bash
   export OPENAI_COMPATIBLE_API_KEY=...
   ```
3. **Edit `core-customize/dev-config/local.properties`** — `baseurl` is
   **required** for this provider:
   ```properties
   coremcp.llm.provider=openai-compatible
   coremcp.openai-compatible.baseurl=https://your-gateway.example.com
   coremcp.openai-compatible.model=gpt-4o
   # Optional — for proxies that rewrite the path:
   # coremcp.openai-compatible.completions.path=/v1/chat/completions
   ```
4. **Apply config + restart server** (see boxed command above).
5. **Verify:** same as OpenAI. The capabilities response defaults
   `"vision":false` for this provider (self-hosted models are often text-only)
   — override with `coremcp.openai-compatible.vision.enabled=true` if your
   gateway serves a multimodal model.

## Adding a fifth provider

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
