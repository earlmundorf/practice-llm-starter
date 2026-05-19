# SAP Commerce Best Practices

## Table of Contents
1. [Layer Separation](#layer-separation)
2. [Coding Standards](#coding-standards)
3. [Performance](#performance)
4. [Data Modeling](#data-modeling)
5. [ImpEx Best Practices](#impex-best-practices)
6. [Testing](#testing)
7. [Extension Design](#extension-design)
8. [Security](#security)
9. [Common Anti-Patterns](#common-anti-patterns)

---

## Layer Separation

### The golden rule: respect the layers

```
Controller → Facade → Service → DAO
```

Each layer has exactly one job:

- **DAO**: Data access only. All FlexibleSearch queries live here. Returns Models.
- **Service**: Business logic. Validation, calculations, orchestration of other services. Operates on Models.
- **Facade**: Converts Models to DTOs using Converters/Populators. Orchestrates service calls for a use case. Never exposes Models.
- **Controller**: HTTP handling. Calls facades, maps DTOs to response objects. Zero business logic.

### Why this matters

If your storefront is web-based today but you add a mobile app tomorrow, you reuse the same facades and services. If you have both B2C and B2B channels, they share the service layer but may have different facades. Skipping layers destroys this reusability.

## Coding Standards

### Naming

- Interfaces for all Services, Facades, DAOs
- Implementations prefixed with `Default` (e.g., `DefaultProductService`)
- Spring bean IDs match the interface name in camelCase
- Consistent package structure: `.services`, `.services.impl`, `.facades`, `.facades.impl`, `.daos`, `.daos.impl`

### Dependency injection

Prefer **setter injection** with `@Required` or constructor injection for mandatory dependencies. SAP Commerce's Spring XML config traditionally uses setter injection:

```xml
<bean id="defaultMyService" class="...">
  <property name="modelService" ref="modelService"/>
  <property name="myDao" ref="myDao"/>
</bean>
```

For newer code, constructor injection is acceptable and more testable.

### Annotations vs XML

SAP Commerce traditionally uses XML bean definitions because it enables the alias/override pattern. If you use annotations (`@Service`, `@Autowired`), you lose the ability for other extensions to override your bean via aliasing. Recommendation: **use XML for beans that others might override** (services, facades, DAOs) and annotations for internal-only components.

## Performance

### FlexibleSearch

- **Use pagination** — Never return unbounded result sets
- **Select {pk} only** when you'll access the full model — avoids double-loading
- **Add indexes** for frequently queried custom attributes
- **Avoid N+1 queries** — If you need related data for a list of items, write a single joined query
- **Cache query results** where appropriate using `FlexibleSearchQuery.setCacheable(true)`

### ModelService

- **Batch saves** — Call `modelService.saveAll(collection)` rather than saving one at a time in a loop
- **Don't save in interceptors** — Interceptors that call `modelService.save()` on other models can cause infinite loops
- **Use `modelService.detach()`** — When you need a Model for read-only comparison without triggering dirty-checking

### ImpEx

- **Batch imports** — Break large imports into chunks (10,000 lines per file)
- **Disable search indexes during import** — Re-index after
- **Use `INSERT` instead of `INSERT_UPDATE`** for initial loads — avoids the lookup overhead

## Data Modeling

### When to create a new type vs extend an existing one

- **Extend** when your entity IS-A version of the parent (e.g., `ToolProduct extends Product`)
- **Create new** when it's a genuinely different entity (e.g., `WarrantyClaim`)
- **Prefer relations** over collection attributes for references between items
- **Use dynamic enums** (`dynamic="true"`) unless you need compile-time safety

### Catalog versioning

- Products, Categories, Media, CMS content — all catalog-versioned (Staged→Online)
- Custom types that extend Product inherit catalog versioning automatically
- Non-catalog-versioned types: Users, Orders, Addresses, Stock

### Localization

- Use `localized:java.lang.String` for any user-facing text
- Store locale-independent data (codes, SKUs, booleans) as regular attributes
- Test with multiple locales from the start

## ImpEx Best Practices

- **essentialdata vs projectdata** — Essential data runs on every update; project data runs on initialize only
- **Use macros** for repetitive values (catalog versions, currencies)
- **Order imports by dependency** — Catalogs before products, categories before assignments
- **Use `INSERT_UPDATE`** for idempotent imports (safe to re-run)
- **Never hardcode PKs** — Use business keys (code, uid) with `[unique=true]`

## Testing

### Unit tests

```java
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultToolProductServiceTest {

  @InjectMocks
  private DefaultToolProductService service;

  @Mock
  private ToolProductDao toolProductDao;

  @Test
  public void shouldFindToolsByCategory() {
    when(toolProductDao.findByCategory(eq(ToolCategoryEnum.HAND_TOOL), any()))
        .thenReturn(Arrays.asList(createToolProduct("HAMMER-001")));

    List<ToolProductModel> results = service.findByCategory(ToolCategoryEnum.HAND_TOOL, mockCv);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getCode()).isEqualTo("HAMMER-001");
  }
}
```

### Integration tests

```java
@IntegrationTest
public class ToolProductDaoIntegrationTest extends ServicelayerTransactionalTest {

  @Resource
  private ToolProductDao toolProductDao;

  @Before
  public void setUp() throws Exception {
    importCsv("/test/testdata-toolproducts.impex", "utf-8");
  }

  @Test
  public void shouldFindToolsByCategory() {
    List<ToolProductModel> results = toolProductDao.findByCategory(ToolCategoryEnum.HAND_TOOL, cv);
    assertThat(results).isNotEmpty();
  }
}
```

### Test tips

- Use `@UnitTest` and `@IntegrationTest` annotations for proper test categorization
- Mock external dependencies in unit tests
- Use ImpEx to set up test data in integration tests
- Test each layer independently

## Extension Design

- **One extension per concern** — `myproject-core` (type system + services), `myproject-facades` (facades + DTOs), `myproject-storefront` (web UI), `myproject-occ` (REST API)
- **Depend on OOTB extensions** rather than copying their code
- **Override via Spring aliasing** — Never modify OOTB extension files
- **Use AddOns** for storefront customizations that overlay the accelerator
- **Keep `buildcallbacks.xml` minimal** — Complex build logic is fragile

## Security

- **Never trust client input** — Validate in the Service layer
- **Use `@Secured` annotations** on OCC controllers to enforce role-based access
- **Don't expose Model attributes directly** — The Facade/DTO layer filters what's visible
- **Sanitize ImpEx input** — Especially in scripted ImpEx blocks
- **Use OAuth2** for API authentication (the `oauth2` extension)

## Common Anti-Patterns

| Anti-Pattern | Why It's Bad | Do This Instead |
|---|---|---|
| Controller calls DAO directly | Skips business logic and DTO conversion | Controller → Facade → Service → DAO |
| Business logic in Controller | Not reusable across channels | Move to Service layer |
| Exposing Models in REST API | Tight coupling, exposes internal structure | Use DTOs via Facade |
| Hardcoded catalog version | Breaks in multi-catalog setups | Use `CatalogVersionService` or parameterize |
| FlexibleSearch in Service | Violates layer separation | Move queries to DAO |
| Editing gensrc/ files | Overwritten on next build | Extend generated classes properly |
| Using Jalo layer | Deprecated since many versions ago | Use ServiceLayer (Models, Services) |
| Giant ImpEx files | Slow, hard to debug, timeout-prone | Split into smaller domain-specific files |
| Saving in interceptors | Can cause infinite loops | Use events or business process |
| String concatenation in queries | SQL injection risk, no plan caching | Use parameterized FlexibleSearch |
