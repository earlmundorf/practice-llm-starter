# Spartacus State Management Examples

Focused code snippets showing correct and incorrect NgRx state patterns in Spartacus.

---

## GOOD: Facade with Proper Observable API

Facade exposes clean Observable API. Components never touch Store directly.

```typescript
@Injectable({ providedIn: 'root' })
export class WishlistService {
  constructor(protected store: Store<StateWithWishlist>) {}

  getItems(): Observable<WishlistItem[]> {
    return this.store.select(WishlistSelectors.getWishlistItems);
  }

  getLoading(): Observable<boolean> {
    return this.store.select(WishlistSelectors.getWishlistLoading);
  }

  loadItems(): void {
    this.store.dispatch(WishlistActions.loadWishlist());
  }

  addItem(productCode: string): void {
    this.store.dispatch(WishlistActions.addWishlistItem({ productCode }));
  }
}
```

Why this is correct:
- Returns `Observable<T>` for reads — components subscribe via async pipe
- Returns `void` for dispatches — fire and forget, state changes flow back via selectors
- No business logic — pure delegation to store
- Singleton via `providedIn: 'root'`

---

## GOOD: Effect with Proper Error Handling

```typescript
@Injectable()
export class WishlistEffects {
  loadWishlist$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WishlistActions.loadWishlist),
      switchMap(() =>
        this.connector.getItems().pipe(
          map(items => WishlistActions.loadWishlistSuccess({ items })),
          catchError(error =>
            of(WishlistActions.loadWishlistFail({ error: error.message }))
          )
        )
      )
    )
  );

  addItem$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WishlistActions.addWishlistItem),
      mergeMap(({ productCode }) =>
        this.connector.addItem(productCode).pipe(
          map(item => WishlistActions.addWishlistItemSuccess({ item })),
          catchError(error =>
            of(WishlistActions.addWishlistItemFail({ error: error.message }))
          )
        )
      )
    )
  );

  constructor(
    protected actions$: Actions,
    protected connector: WishlistConnector
  ) {}
}
```

Why this is correct:
- `switchMap` for load (cancels stale requests)
- `mergeMap` for add (allows concurrent adds)
- `catchError` inside the inner pipe — keeps the outer effect stream alive
- Dispatches fail action on error — UI can react via selectors
- Calls connector, not HttpClient

---

## BAD: Component Importing Store Directly

```typescript
@Component({ selector: 'cx-wishlist', template: '...' })
export class WishlistComponent implements OnInit {
  items$!: Observable<WishlistItem[]>;

  constructor(private store: Store<StateWithWishlist>) {}

  ngOnInit(): void {
    this.store.dispatch(WishlistActions.loadWishlist());
    this.items$ = this.store.select(WishlistSelectors.getWishlistItems);
  }
}
```

**What's wrong:**
- Component imports `Store` directly — bypasses the facade
- Tightly couples the component to the state shape and action names
- If the state structure changes, every component that imports Store must update
- Makes testing harder — must mock the entire Store instead of a simple facade

**Fix:** Inject `WishlistService` (facade) instead:

```typescript
constructor(protected wishlistService: WishlistService) {}

ngOnInit(): void {
  this.wishlistService.loadItems();
  this.items$ = this.wishlistService.getItems();
}
```

---

## BAD: Effect Calling HttpClient Directly

```typescript
@Injectable()
export class BadWishlistEffects {
  loadWishlist$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WishlistActions.loadWishlist),
      switchMap(() =>
        this.http.get<any>('/occ/v2/electronics/users/current/wishlist').pipe(
          map(res => WishlistActions.loadWishlistSuccess({ items: res.entries })),
          catchError(error => of(WishlistActions.loadWishlistFail({ error })))
        )
      )
    )
  );

  constructor(
    protected actions$: Actions,
    protected http: HttpClient  // WRONG — should be connector
  ) {}
}
```

**What's wrong:**
- Effect calls `HttpClient` directly — bypasses connector/adapter pipeline
- Hardcoded OCC URL — not configurable, breaks across environments
- No response normalization — raw OCC DTO leaks into state
- Cannot swap backend implementation (e.g., mock adapter for testing)

**Fix:** Inject the connector, which delegates to the adapter:

```typescript
constructor(
  protected actions$: Actions,
  protected connector: WishlistConnector
) {}
```

---

## BAD: Missing Loading/Error State

```typescript
// State with no loading tracking
export interface BadWishlistState {
  items: WishlistItem[];  // no LoaderState wrapper
}

// Reducer manually tracking loading (error-prone)
export const badReducer = createReducer(
  { items: [], loading: false, error: null },
  on(loadWishlist, state => ({ ...state, loading: true })),
  on(loadSuccess, (state, { items }) => ({ ...state, items, loading: false })),
  on(loadFail, (state, { error }) => ({ ...state, error, loading: false }))
);
```

**What's wrong:**
- Manually tracking `loading`/`error` booleans is repetitive and error-prone
- Easy to forget to reset `loading` on failure, or `error` on success
- Every feature reimplements the same pattern

**Fix:** Use `StateUtils.LoaderState<T>` and the corresponding reducer/selector utilities:

```typescript
export interface WishlistState {
  items: StateUtils.LoaderState<WishlistItem[]>;
}

// Use StateUtils.loaderReducer to auto-track loading/success/error
```

---

## GENERATE OUTPUT: /spartacus-state generate Wishlist

Running `/spartacus-state generate Wishlist` produces these files:

### File structure
```
src/app/features/wishlist/
├── store/
│   ├── wishlist-state.ts
│   ├── actions/wishlist.actions.ts
│   ├── reducers/wishlist.reducer.ts
│   ├── reducers/index.ts
│   ├── effects/wishlist.effects.ts
│   ├── selectors/wishlist.selectors.ts
│   └── wishlist-store.module.ts
├── facade/wishlist.service.ts
├── connectors/wishlist.connector.ts
├── connectors/wishlist.adapter.ts
└── models/wishlist.model.ts
```

### Key files (abbreviated)

**wishlist-state.ts:**
```typescript
import { StateUtils } from '@spartacus/core';
import { WishlistItem } from '../models/wishlist.model';

export const WISHLIST_FEATURE = 'wishlist';

export interface WishlistState {
  items: StateUtils.LoaderState<WishlistItem[]>;
}

export interface StateWithWishlist {
  [WISHLIST_FEATURE]: WishlistState;
}
```

**wishlist.actions.ts:**
```typescript
export namespace WishlistActions {
  export const loadWishlist = createAction('[Wishlist] Load Items');
  export const loadWishlistSuccess = createAction('[Wishlist] Load Items Success', props<{ items: WishlistItem[] }>());
  export const loadWishlistFail = createAction('[Wishlist] Load Items Fail', props<{ error: any }>());
  export const addWishlistItem = createAction('[Wishlist] Add Item', props<{ productCode: string }>());
  export const addWishlistItemSuccess = createAction('[Wishlist] Add Item Success', props<{ item: WishlistItem }>());
  export const addWishlistItemFail = createAction('[Wishlist] Add Item Fail', props<{ error: any }>());
  export const removeWishlistItem = createAction('[Wishlist] Remove Item', props<{ itemId: string }>());
}
```

**wishlist-store.module.ts:**
```typescript
@NgModule({
  imports: [
    StoreModule.forFeature(WISHLIST_FEATURE, wishlistReducerToken),
    EffectsModule.forFeature([WishlistEffects]),
  ],
  providers: [wishlistReducerProvider],
})
export class WishlistStoreModule {}
```

**wishlist.service.ts (facade):**
```typescript
@Injectable({ providedIn: 'root' })
export class WishlistService {
  constructor(protected store: Store<StateWithWishlist>) {}

  getItems(): Observable<WishlistItem[]> { return this.store.select(WishlistSelectors.getWishlistItems); }
  getLoading(): Observable<boolean> { return this.store.select(WishlistSelectors.getWishlistLoading); }
  loadItems(): void { this.store.dispatch(WishlistActions.loadWishlist()); }
  addItem(productCode: string): void { this.store.dispatch(WishlistActions.addWishlistItem({ productCode })); }
  removeItem(itemId: string): void { this.store.dispatch(WishlistActions.removeWishlistItem({ itemId })); }
}
```

Each generated file includes necessary imports, proper typing, and follows the patterns documented in [patterns.md](patterns.md).
