# UCP MCP binding — components

| File | Role |
|------|------|
| `src/com/ucpcommerce/controllers/UcpMcpController.java` | `POST /{baseSiteId}/ucp/mcp`: raw-string body, Jackson parse into JSON-RPC DTOs, `@Secured` roles — stateless (no session header, no cart preload) |
| `src/com/ucpcommerce/services/impl/DefaultUcpMcpDispatcherService.java` | Routes `initialize` / `tools/list` / `tools/call` / notifications; converts thrown exceptions to `isError` tool results; parses per-call meta into `UcpToolContext` |
| `src/com/ucpcommerce/tools/UcpTool.java` | Tool adapter interface (name, description, input schema, `execute(args, context)`) |
| `src/com/ucpcommerce/tools/UcpToolContext.java` | Per-call UCP metadata: `meta["ucp-agent"]`, `meta["idempotency-key"]` (accepts `meta` and `_meta` spellings) |
| `src/com/ucpcommerce/tools/impl/SearchCatalogTool.java`, `LookupCatalogTool.java`, `GetProductTool.java` | Catalog tools → `UcpCatalogService` |
| `src/com/ucpcommerce/tools/impl/CreateCheckoutTool.java`, `GetCheckoutTool.java`, `UpdateCheckoutTool.java`, `CompleteCheckoutTool.java`, `CancelCheckoutTool.java` | Checkout tools → `UcpCheckoutService` (see the ucp-checkout flow) |
| `src/com/ucpcommerce/tools/impl/GetOrderTool.java`, `ListOrdersTool.java` | Order tools → `UcpOrderService` |
| `src/com/ucpcommerce/tools/impl/GetPromotionsTool.java` | `com.thinkshop.promotions` → coremcp `PromotionQueryService` |
| `src/com/ucpcommerce/tools/impl/SearchKnowledgeTool.java`, `GetKnowledgeTool.java` | `com.thinkshop.knowledge` → coremcp `KnowledgeSearchService` (Solr `knowledgeIndex`) |
| `src/com/ucpcommerce/services/impl/DefaultUcpCatalogService.java` | `search`/`lookup`/`getProduct` over `ProductSearchFacade`/`ProductFacade`, UCP product marshalling |
| `src/com/ucpcommerce/services/impl/DefaultUcpOrderService.java` | `getOrder`/`history` over `OrderFacade` (scoped to the authenticated customer), UCP order marshalling |
| `src/com/ucpcommerce/services/impl/UcpMoneyConverter.java` | THE money boundary: `BigDecimal` major ⇄ integer minor units, currency-digit aware — every marshaller uses this single bean |
| `src/com/ucpcommerce/services/impl/UcpOrderMarshaller.java` | `OrderData`/`OrderHistoryData` → UCP order objects (minimal embedded block, full order, history summary) |
| `src/com/ucpcommerce/dto/JsonRpc{Request,Response,Error}.java` | Own JSON-RPC envelope DTOs (not imported from coremcp — design R2 keeps the surfaces decoupled) |
| `resources/ucpcommerce-spring.xml` | Services, marshallers, all 13 tool beans, dispatcher tool list |

Tests: `testsrc/com/ucpcommerce/services/impl/DefaultUcpMcpDispatcherServiceTest.java`
(routing/initialize/unknown-tool/bad-envelope), `DefaultUcpCatalogServiceTest`,
`DefaultUcpOrderServiceTest`, `UcpMoneyConverterTest`, `UcpOrderMarshallerTest`,
and `testsrc/com/ucpcommerce/tools/impl/ThinkshopToolsTest.java`
(promotions/knowledge tool passthrough over mocked coremcp services).
