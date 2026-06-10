# 0005 — LLM/tool payloads use plain Jackson DTOs, not *-beans.xml

**Status:** accepted (2026-06)

## Context

SAP Commerce convention generates Data/WsDTO classes from `*-beans.xml` for
payloads crossing the OCC data-mapping pipeline (converters/populators, field
projection). The coremcp payloads are different in kind: LLM chat-completion
responses, vision-analysis JSON, and MCP tool results are **internal wire
shapes** consumed by the agent loop or passed verbatim to/from LLM providers —
they never go through OCC converters, and their shape is dictated by external
APIs (OpenAI/Anthropic), not by our type system.

## Decision

Model them as hand-written Jackson classes in `com.coremcp.dto` /
`com.coremcp.dto.llm` (`LlmChatResponse`, `LlmToolCall`, `VisionAnalysisResult`,
JSON-RPC DTOs). `coremcp-beans.xml` stays intentionally empty, with a comment
saying so.

## Consequences

- Shape validation and casting live in one place per payload (`parse()` /
  annotated fields) with forward-compatibility via `@JsonAnySetter` where the
  external API may add fields.
- If any of these payloads ever need to be exposed through OCC's field-mapping
  pipeline (e.g. a mobile SDK consuming tool results), generate WsDTOs in
  beans.xml at that boundary — don't retrofit these internals.
