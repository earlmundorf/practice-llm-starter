# LLM provider configuration

The commerce MCP server now supports multiple runtime-selectable LLM backends behind the same client abstraction.

## Provider selection

Set [`coremcp.llm.provider`](sap-mcp-server-l/core-customize/hybris/bin/custom/coremcp/resources/coremcp-spring.xml) to one of:

- `openai`
- `anthropic`
- `kong-openai`

Optional environment fallback:

- `COREMCP_LLM_PROVIDER`

## Shared setting

- `coremcp.llm.timeout.seconds`

## Direct OpenAI

- `coremcp.openai.apikey`
- `coremcp.openai.baseurl` optional, defaults to OpenAI
- `coremcp.openai.model`
- `coremcp.openai.intent.model`
- env fallback: `OPENAI_API_KEY`

## Direct Anthropic / Claude

- `coremcp.anthropic.apikey`
- `coremcp.anthropic.baseurl` optional, defaults to Anthropic messages API
- `coremcp.anthropic.version` optional, defaults to `2023-06-01`
- `coremcp.anthropic.model`
- `coremcp.anthropic.intent.model`
- env fallback: `ANTHROPIC_API_KEY`

Anthropic responses are normalized back into the existing OpenAI-style `choices -> message -> content/tool_calls` shape so the agent loop in [`DefaultAgentService`](sap-mcp-server-l/core-customize/hybris/bin/custom/coremcp/src/com/coremcp/services/impl/DefaultAgentService.java) does not need major changes.

## Kong OpenAI-compatible route

Use this when Kong fronts an OpenAI-compatible API, similar to the Roo setup.

- `coremcp.kong.apikey`
- `coremcp.kong.baseurl`
- `coremcp.kong.chat.completions.path` optional, defaults to `/v1/chat/completions`
- `coremcp.kong.model`
- `coremcp.kong.intent.model`
- env fallback: `KONG_LLM_API_KEY`

## Wiring notes

- [`DefaultLlmClient`](sap-mcp-server-l/core-customize/hybris/bin/custom/coremcp/src/com/coremcp/services/impl/DefaultLlmClient.java) routes requests by config.
- The legacy bean alias [`openAiClient`](sap-mcp-server-l/core-customize/hybris/bin/custom/coremcp/resources/coremcp-spring.xml) is preserved for compatibility.
- [`OpenAiClient`](sap-mcp-server-l/core-customize/hybris/bin/custom/coremcp/src/com/coremcp/services/OpenAiClient.java) now extends [`LlmClient`](sap-mcp-server-l/core-customize/hybris/bin/custom/coremcp/src/com/coremcp/services/LlmClient.java) as a migration-safe alias.
