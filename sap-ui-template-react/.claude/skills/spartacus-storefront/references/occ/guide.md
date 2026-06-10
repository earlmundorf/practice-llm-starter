# Spartacus OCC API Customization

You are a senior SAP Spartacus developer reviewing or generating OCC adapter customizations for a Spartacus 6.x storefront using NgModules and Angular 17+.

## Project Context

Spartacus dependencies: !`cat package.json 2>/dev/null | grep -E "@spartacus|@angular" | head -10 || echo "No package.json found — assume Spartacus 6.x, Angular 17+"`

## Mode Selection

**If `$0` is `review`:** Audit the OCC adapter or converter named `$1` (or the code the user points to). Check endpoint config, adapter implementation, normalizer/serializer correctness, and converter registration.

**If `$0` is `generate`:** Scaffold a custom OCC adapter named `$1` with endpoint config, converter tokens, normalizer, and module registration. Follow the file structure and naming below.

**If no arguments:** Auto-triggered. Review whatever OCC integration code is in context. Lead with the most impactful findings.

---

## The OCC Integration Pipeline

Data flows through these layers between the Spartacus frontend and SAP Commerce:

```
Connector → Adapter → HttpClient → SAP Commerce OCC API
                ↓ response
           ConverterService.pipeable(NORMALIZER) → Spartacus Model

Spartacus Model → ConverterService.convert(data, SERIALIZER) → OCC DTO
                                                                  ↓
                                          Adapter → HttpClient → SAP Commerce
```

Each layer has one job:
- **Adapter**: Makes HTTP calls using `HttpClient` and `OccEndpointsService`. Returns raw response piped through converters.
- **Endpoint config**: Defines URL patterns with path/query parameters.
- **Normalizer**: Transforms OCC DTO → Spartacus model (reads).
- **Serializer**: Transforms Spartacus model → OCC DTO (writes).
- **ConverterService**: Applies normalizers/serializers via injection tokens.
- **Interceptor**: Cross-cutting concerns (auth, errors, site context).

---

## Review Checklist

### Endpoint Configuration
- Endpoints defined via `provideDefaultConfig({ backend: { occ: { endpoints: { ... } } } })`
- URL patterns use Spartacus placeholder syntax: `${param}` for path params
- `fields` parameter specified to control response verbosity (`FULL`, `DEFAULT`, `BASIC`)
- No hardcoded base URLs — `OccEndpointsService` handles base path
- Endpoint key matches existing Spartacus endpoint names when extending (check `default-occ-config.ts`)

### Adapter Implementation
- Extends the OCC adapter base class (e.g., `OccProductAdapter`) or implements the abstract adapter
- Uses `OccEndpointsService.buildUrl()` for URL construction — never string concatenation
- Response piped through `ConverterService.pipeable(NORMALIZER_TOKEN)`
- Request data converted via `ConverterService.convert(data, SERIALIZER_TOKEN)` before sending
- Returns `Observable<T>` — no `.toPromise()` or manual subscribes
- Error handling delegated to interceptors — adapter should NOT catch/swallow errors

### Converter Registration
- Normalizer/serializer provided as multi-providers: `{ provide: TOKEN, useExisting: MyNormalizer, multi: true }`
- Converter class is `@Injectable({ providedIn: 'root' })` or provided in feature module
- Multiple normalizers on the same token chain correctly (each processes the result of the previous)
- Token imported from Spartacus core (e.g., `PRODUCT_NORMALIZER`) or custom token declared

### Normalizer
- Implements `Converter<Occ.Source, TargetModel>`
- `convert(source: Occ.Source, target?: TargetModel): TargetModel`
- Handles null/undefined source fields gracefully (nullish coalescing, optional chaining)
- Maps OCC field names to Spartacus model field names
- Does NOT call services or make HTTP calls — pure transformation
- Preserves existing `target` fields when merging (spread `...target` first)

### Serializer
- Implements `Converter<SourceModel, Occ.Target>`
- Reverse direction of normalizer — model → OCC DTO
- Only includes fields the OCC endpoint expects
- Handles optional fields — omits undefined rather than sending `null`

### Interceptors
- Implements `HttpInterceptor`
- Checks if request is an OCC request before modifying (use `OccEndpointsService.isOccUrl()`)
- Does not swallow errors — rethrows after handling
- Auth interceptor adds `Authorization: Bearer` header
- Error interceptor maps OCC error responses to Spartacus `HttpErrorModel`

For detailed patterns and code snippets, see [patterns.md](patterns.md).
For good/bad examples, see [examples.md](examples.md).

---

## Generate Instructions

When scaffolding a custom OCC adapter named `$1`:

### File Structure
```
src/app/features/$1/occ/
├── occ-$1.adapter.ts              # OCC adapter implementation
├── $1.converter.ts                # Normalizer (OCC DTO → model)
├── $1.serializer.ts               # Serializer (model → OCC DTO) — if writes needed
├── $1.config.ts                   # Endpoint configuration
└── $1-occ.module.ts               # Module providing adapter + converters

src/app/features/$1/connectors/
├── $1.adapter.ts                  # Abstract adapter interface
└── converters.ts                  # Converter token declarations
```

### Naming Conventions
- OCC adapter: `Occ$1Adapter` (e.g., `OccWishlistAdapter`)
- Abstract adapter: `$1Adapter` (e.g., `WishlistAdapter`)
- Normalizer: `$1Normalizer` or `Occ$1Normalizer`
- Serializer: `$1Serializer` or `Occ$1Serializer`
- Converter token: `$1_NORMALIZER`, `$1_SERIALIZER` (SCREAMING_SNAKE_CASE)
- Endpoint key: camelCase matching domain (e.g., `wishlist`, `wishlistItem`)

### What to Generate
1. **Abstract adapter** — interface with abstract methods
2. **Converter tokens** — `InjectionToken` for normalizer and serializer
3. **OCC adapter** — implements abstract adapter, uses `OccEndpointsService` and `ConverterService`
4. **Endpoint config** — URL patterns with path params and fields
5. **Normalizer** — `Converter<Occ.Source, Model>` with null-safe transforms
6. **OCC module** — provides adapter binding, converter multi-providers, and endpoint config

Refer to [examples.md](examples.md) for the full generate output template.
