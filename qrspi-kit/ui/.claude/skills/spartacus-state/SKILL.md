---
name: spartacus-state
description: |
  Review or generate SAP Spartacus 6.x NgRx state management following the
  facade/connector/adapter/normalizer pipeline. Covers actions, reducers,
  effects, selectors, facades, connectors, and StateUtils patterns.
  Auto-triggers on Spartacus facade, effect, reducer, connector, or adapter code.
  Also trigger with: "spartacus state", "facade", "ngrx spartacus", "connector",
  "adapter", "normalizer", "reducer", "effect", "selector".
argument-hint: "review|generate [FeatureName]"
allowed-tools: [Read, Grep, Glob, Edit, Write]
effort: high
---

# Spartacus NgRx State Management

You are a senior SAP Spartacus developer reviewing or generating NgRx state management code following the Spartacus facade/connector/adapter/normalizer pipeline for Spartacus 6.x with NgModules.

## Project Context

Spartacus dependencies: !`cat package.json 2>/dev/null | grep -E "@spartacus|@ngrx|@angular" | head -15 || echo "No package.json found — assume Spartacus 6.x, Angular 17+, NgRx 17+"`

## Mode Selection

**If `$0` is `review`:** Audit the state feature named `$1` (or the code the user points to). Read across the full pipeline: actions, reducer, effects, selectors, facade, connector, adapter. Focus on the layers that exist — not every feature has all layers.

**If `$0` is `generate`:** Scaffold a complete state feature named `$1`. Create actions, reducer, effects, selectors, facade, connector interface, and state module registration. Follow the file structure and naming below.

**If no arguments:** Auto-triggered. Review whatever Spartacus state code is in context. Lead with the most impactful findings.

---

## The Spartacus State Pipeline

Understanding how the layers connect is critical. Data flows like this:

```
Component → Facade → Store (dispatch action)
                        ↓
                      Effect → Connector → Adapter → HTTP/OCC
                        ↓                    ↑
                      Reducer ← Success/Fail action
                        ↓
                      Selector → Facade → Component (via Observable)

Adapter response → Normalizer → Spartacus model
Spartacus model → Serializer → OCC DTO (for writes)
```

Each layer has one job:
- **Facade**: Public API. Components inject this, never Store.
- **Actions**: Describe what happened. Typed payloads, no logic.
- **Reducer**: Pure state transitions. No side effects.
- **Effects**: Orchestrate side effects via connectors.
- **Selectors**: Derive data from state. Memoized.
- **Connector**: Delegates to adapter. Abstraction boundary.
- **Adapter**: HTTP calls. OCC-specific implementation.
- **Normalizer/Serializer**: Transform between OCC DTOs and Spartacus models.

---

## Review Checklist

### Facade
- Facade is `@Injectable({ providedIn: 'root' })` — singleton
- All public methods return `Observable<T>` (reads) or `void` (dispatches)
- Components NEVER import `Store` directly — always go through facade
- Facade dispatches actions for writes, selects from store for reads
- No business logic in facade — it's a thin delegation layer

### Actions
- Actions use `createAction()` with descriptive `[Feature] Verb Noun` naming
- Load/success/fail triplet for async operations
- Typed payloads via `props<{ ... }>()`
- `StateUtils.entityLoadMeta` / `StateUtils.entitySuccessMeta` for loader state tracking

### Reducer
- Pure function — no side effects, no service injection
- Uses `createReducer()` with `on()` handlers
- Initial state defined as a typed constant
- Wrapped in `StateUtils.loaderReducer()` or `StateUtils.entityReducer()` for loading/error tracking
- Handles success by storing data, fail by clearing or preserving last good state

### Effects
- `@Injectable()` class with `createEffect()` calls
- Side effects go through **connector**, never direct `HttpClient`
- Proper error handling: `catchError` dispatches fail action, does NOT rethrow
- Uses `switchMap` for loads (cancel previous), `mergeMap` for writes (concurrent)
- `exhaustMap` for operations that should not overlap (e.g., place order)

### Selectors
- `createFeatureSelector<StateWithXxx>(FEATURE_KEY)` for the feature state
- `createSelector()` for derived data — memoized automatically
- `StateUtils.loaderValueSelector` / `StateUtils.loaderLoadingSelector` for loader state
- Selectors compose — complex selectors built from simpler ones
- No side effects in selectors

### Connector
- Abstract class with abstract methods returning `Observable<T>`
- Concrete implementation injects the adapter
- One connector per domain concept (e.g., `WishlistConnector`)
- Provided at root level

### State Module Registration
- `StoreModule.forFeature(FEATURE_KEY, reducerToken)` in feature module
- `EffectsModule.forFeature([XxxEffects])` in same module
- Reducer provided via `InjectionToken` for AOT compatibility
- Feature key constant exported for selector access

### State Shape
- `StateWithXxx` interface extends root state declaration
- `XxxState` interface defines the feature's state shape
- Uses `StateUtils.LoaderState<T>` for async data with loading/error/success tracking
- Uses `StateUtils.EntityState<T>` for entity collections

For detailed patterns and code snippets, see [patterns.md](patterns.md).
For good/bad examples, see [examples.md](examples.md).

---

## Generate Instructions

When scaffolding a new state feature named `$1`:

### File Structure
```
src/app/features/$1/store/
├── actions/
│   └── $1.actions.ts           # Load/Success/Fail actions
├── reducers/
│   ├── $1.reducer.ts           # State transitions
│   └── index.ts                # Reducer token + provider
├── effects/
│   └── $1.effects.ts           # Side effects via connector
├── selectors/
│   └── $1.selectors.ts         # Memoized state queries
├── $1-state.ts                 # State shape interfaces
└── $1-store.module.ts          # StoreModule + EffectsModule registration

src/app/features/$1/facade/
└── $1.service.ts               # Facade — public API

src/app/features/$1/connectors/
├── $1.connector.ts             # Abstract connector
└── $1.adapter.ts               # Abstract adapter interface
```

### Naming Conventions
- Feature key: `SCREAMING_SNAKE_CASE` (e.g., `WISHLIST_FEATURE`)
- State interface: `WishlistState`, `StateWithWishlist`
- Actions: `[Wishlist] Load Items`, `[Wishlist] Load Items Success`
- Facade: `WishlistService` (the `Service` suffix is Spartacus convention for facades)
- Connector: `WishlistConnector`
- Adapter: `WishlistAdapter` (abstract), `OccWishlistAdapter` (OCC implementation)

### What to Generate
1. **State interfaces** — `WishlistState` with `LoaderState<WishlistItem[]>`, `StateWithWishlist`
2. **Actions** — Load/Success/Fail triplet with typed payloads
3. **Reducer** — Handle success/fail, wrap in `loaderReducer`
4. **Effects** — Listen for load action, call connector, dispatch success/fail
5. **Selectors** — Feature selector, value selector, loading selector, error selector
6. **Facade** — `WishlistService` dispatching actions and selecting from store
7. **Connector** — Abstract class with `getItems(): Observable<WishlistItem[]>`
8. **Store module** — Register feature state and effects

Refer to [examples.md](examples.md) for the full generate output template.
