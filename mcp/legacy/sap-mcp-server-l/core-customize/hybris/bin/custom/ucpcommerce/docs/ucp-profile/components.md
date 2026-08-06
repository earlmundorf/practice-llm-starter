# UCP profile — components

| File | Role |
|------|------|
| `src/com/ucpcommerce/controllers/UcpProfileController.java` | `GET /{baseSiteId}/.well-known/ucp`, `@Secured` incl. `ROLE_ANONYMOUS` (the `KnowledgeController` pattern), permissive CORS headers, JSON body from the profile service |
| `src/com/ucpcommerce/services/UcpProfileService.java` | Service interface: `buildProfile(baseSiteId)` |
| `src/com/ucpcommerce/services/impl/DefaultUcpProfileService.java` | Builds the profile: pinned `ucp.version`, capability entries (standard `dev.ucp.shopping.{catalog,checkout,order}` + custom `com.thinkshop.{promotions,knowledge}`), `services.dev.ucp.shopping.mcp.endpoint`, the single `thinkshop_mock_card` payment handler |
| `src/com/ucpcommerce/dto/UcpProfile.java` | Top-level profile DTO: `ucp` + `capabilities[]` + `services{}` + `payment_handlers[]` (hand-written Jackson, ADR 0005 convention) |
| `src/com/ucpcommerce/dto/UcpCapability.java`, `UcpServiceEntry.java`, `UcpTransportEndpoint.java`, `UcpPaymentHandler.java`, `UcpEnvelope.java` | Profile building blocks |
| `project.properties` | `ucpcommerce.ucp.version`, `ucpcommerce.public.base.url` defaults |
| `testsrc/com/ucpcommerce/services/impl/DefaultUcpProfileServiceTest.java` | Profile shape/version/capability-set assertions, serialized wire-shape pin |

Spring wiring: `resources/ucpcommerce-spring.xml` (`defaultUcpProfileService`
+ `ucpProfileService` alias). The controller joins the OCC v2 servlet via
`resources/commercewebservices/v2/additional-web-spring-context.xml`
(component scan of `com.ucpcommerce.controllers`).
