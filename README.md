# cg-llm-starter

A monorepo containing starter projects for LLM-powered applications built on SAP Commerce. Each subdirectory is a self-contained starter you can use as a foundation for new projects.

## Projects

### SAP Commerce Backend

| Project | Purpose |
|---------|---------|
| `sap-mcp-server` | A fully working SAP Commerce extension with MCP server implementation, sample data, and Claude Code skills. This is the starting point for building LLM-integrated Commerce projects — it includes running Java code, ImpEx data, Solr configuration, and agent tooling. |
| `sap-llm-template` | A minimal SAP Commerce starter with no custom code — configuration only. Use this as the cleanest possible foundation when you want to add LLM capabilities to your existing project, with the project structure, skills, and local tooling already in place. |

### Decoupled React UI

| Project | Purpose |
|---------|---------|
| `sap-mcp-ui-react` | A fully working React storefront wired to the `sap-mcp-server` OCC API. Includes product browse, cart, checkout, order history, and an MCP chat interface. Use this as the starting point for a decoupled UI on top of an MCP-enabled Commerce backend. |
| `sap-ui-template-react` | A minimal React storefront scaffold — routing, auth context, cart context, and API service wired to SAP Commerce OCC. No feature pages beyond the shell. Use this when you want to build your own UI from a clean but fully connected foundation. |

## Getting Started

Each project has its own `docs/getting-started.md` with setup instructions. Start with the backend project first, then connect a UI project to it.
