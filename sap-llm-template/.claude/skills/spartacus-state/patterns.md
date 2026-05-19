# Spartacus State Management Patterns

Reference snippets for NgRx state management in Spartacus 6.x following the facade/connector/adapter pipeline.

---

## Facade — Public API

The facade is the only interface components should use. It dispatches actions for writes and selects from the store for reads.

```typescript
import { Injectable } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { StateWithWishlist } from '../store/wishlist-state';
import { WishlistActions } from '../store/actions/wishlist.actions';
import { WishlistSelectors } from '../store/selectors/wishlist.selectors';
import { WishlistItem } from '../../models/wishlist.model';

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

---

## Actions — Typed Events

Actions describe what happened. Use a load/success/fail triplet for async operations.

```typescript
import { createAction, props } from '@ngrx/store';
import { StateUtils } from '@spartacus/core';
import { WISHLIST_FEATURE } from '../wishlist-state';
import { WishlistItem } from '../../models/wishlist.model';

export namespace WishlistActions {
  export const loadWishlist = createAction(
    '[Wishlist] Load Items',
    StateUtils.entityLoadMeta(WISHLIST_FEATURE)
  );

  export const loadWishlistSuccess = createAction(
    '[Wishlist] Load Items Success',
    props<{ items: WishlistItem[] }>(),
    StateUtils.entitySuccessMeta(WISHLIST_FEATURE)
  );

  export const loadWishlistFail = createAction(
    '[Wishlist] Load Items Fail',
    props<{ error: any }>(),
    StateUtils.entityFailMeta(WISHLIST_FEATURE)
  );

  export const addWishlistItem = createAction(
    '[Wishlist] Add Item',
    props<{ productCode: string }>()
  );
}
```

Action naming convention: `[Feature] Verb Noun` — e.g., `[Cart] Add Entry`, `[Cart] Add Entry Success`.

---

## Reducer — Pure State Transitions

Reducers handle state changes. Wrap in `StateUtils.loaderReducer()` for automatic loading/error state tracking.

```typescript
import { createReducer, on } from '@ngrx/store';
import { WishlistActions } from '../actions/wishlist.actions';
import { WishlistState } from '../wishlist-state';
import { WishlistItem } from '../../models/wishlist.model';

export const initialState: WishlistState = {
  items: [],
};

export const wishlistReducer = createReducer(
  initialState,
  on(WishlistActions.loadWishlistSuccess, (state, { items }) => ({
    ...state,
    items,
  })),
  on(WishlistActions.loadWishlistFail, (state) => ({
    ...state,
    items: [],
  }))
);
```

Reducer token for AOT compatibility:

```typescript
import { InjectionToken, Provider } from '@angular/core';
import { ActionReducerMap } from '@ngrx/store';
import { WishlistState } from '../wishlist-state';

export const wishlistReducerToken = new InjectionToken<ActionReducerMap<WishlistState>>(
  'WishlistReducers'
);

export const wishlistReducerProvider: Provider = {
  provide: wishlistReducerToken,
  useFactory: () => ({ wishlist: wishlistReducer }),
};
```

---

## Effects — Side Effects via Connector

Effects listen for actions, call the connector, and dispatch success/fail actions. Never call `HttpClient` directly.

```typescript
import { Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { switchMap, map, catchError } from 'rxjs/operators';
import { WishlistActions } from '../actions/wishlist.actions';
import { WishlistConnector } from '../../connectors/wishlist.connector';

@Injectable()
export class WishlistEffects {
  loadWishlist$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WishlistActions.loadWishlist),
      switchMap(() =>
        this.wishlistConnector.getItems().pipe(
          map(items => WishlistActions.loadWishlistSuccess({ items })),
          catchError(error => of(WishlistActions.loadWishlistFail({ error })))
        )
      )
    )
  );

  constructor(
    protected actions$: Actions,
    protected wishlistConnector: WishlistConnector
  ) {}
}
```

Operator choice matters:
- `switchMap` for loads — cancels previous request if a new one comes in
- `mergeMap` for writes — allows concurrent requests (e.g., add multiple items)
- `exhaustMap` for operations that must not overlap (e.g., place order)
- `concatMap` for sequential processing (e.g., ordered queue of updates)

---

## Selectors — Memoized State Queries

Selectors derive data from state. They're memoized — only recompute when input state changes.

```typescript
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { StateUtils } from '@spartacus/core';
import { StateWithWishlist, WishlistState, WISHLIST_FEATURE } from '../wishlist-state';

export namespace WishlistSelectors {
  export const getWishlistState = createFeatureSelector<WishlistState>(WISHLIST_FEATURE);

  export const getWishlistLoaderState = createSelector(
    getWishlistState,
    state => state.items
  );

  export const getWishlistItems = createSelector(
    getWishlistLoaderState,
    StateUtils.loaderValueSelector
  );

  export const getWishlistLoading = createSelector(
    getWishlistLoaderState,
    StateUtils.loaderLoadingSelector
  );

  export const getWishlistError = createSelector(
    getWishlistLoaderState,
    StateUtils.loaderErrorSelector
  );

  // Composed selector — derives count from items
  export const getWishlistCount = createSelector(
    getWishlistItems,
    items => items?.length ?? 0
  );
}
```

---

## Connector — Abstraction Boundary

The connector is an abstract class. It delegates to the adapter (which handles the actual HTTP transport).

```typescript
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { WishlistAdapter } from './wishlist.adapter';
import { WishlistItem } from '../models/wishlist.model';

@Injectable({ providedIn: 'root' })
export class WishlistConnector {
  constructor(protected adapter: WishlistAdapter) {}

  getItems(): Observable<WishlistItem[]> {
    return this.adapter.getItems();
  }

  addItem(productCode: string): Observable<WishlistItem> {
    return this.adapter.addItem(productCode);
  }

  removeItem(itemId: string): Observable<void> {
    return this.adapter.removeItem(itemId);
  }
}
```

---

## Adapter Interface

The adapter is abstract — the OCC implementation lives separately (see `spartacus-occ` skill).

```typescript
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { WishlistItem } from '../models/wishlist.model';

@Injectable()
export abstract class WishlistAdapter {
  abstract getItems(): Observable<WishlistItem[]>;
  abstract addItem(productCode: string): Observable<WishlistItem>;
  abstract removeItem(itemId: string): Observable<void>;
}
```

---

## State Shape Interfaces

Define the shape of your feature state and how it integrates into the root state.

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

`StateUtils.LoaderState<T>` wraps your data with `loading`, `error`, `success`, and `value` properties — no need to manage these flags manually.

---

## State Module Registration

Register the feature state and effects in a dedicated store module.

```typescript
import { NgModule } from '@angular/core';
import { StoreModule } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { WISHLIST_FEATURE } from './wishlist-state';
import { wishlistReducerToken, wishlistReducerProvider } from './reducers';
import { WishlistEffects } from './effects/wishlist.effects';

@NgModule({
  imports: [
    StoreModule.forFeature(WISHLIST_FEATURE, wishlistReducerToken),
    EffectsModule.forFeature([WishlistEffects]),
  ],
  providers: [wishlistReducerProvider],
})
export class WishlistStoreModule {}
```

---

## LoaderState / EntityState / ProcessesLoaderState

Spartacus provides state wrappers for common async patterns:

**LoaderState** — single async value with loading/error tracking:

```typescript
interface WishlistState {
  items: StateUtils.LoaderState<WishlistItem[]>;
}
```

**EntityState** — keyed collection (e.g., products by code):

```typescript
interface ProductState {
  details: StateUtils.EntityState<Product>;
  // Access: state.details.entities['12345']
}
```

**ProcessesLoaderState** — tracks multiple concurrent processes:

```typescript
interface CartState {
  cart: StateUtils.ProcessesLoaderState<Cart>;
  // Tracks: overall loading + individual process IDs
}
```

Use the matching `StateUtils.*Reducer()` and `StateUtils.*Selector()` for each wrapper type.
