# Spartacus Testing Patterns

Reference snippets for unit testing in Spartacus 6.x with Jasmine and Angular TestBed.

---

## Mocking CmsComponentData

Components receive CMS model data via `CmsComponentData<T>`. In tests, provide a mock with a typed observable.

```typescript
import { CmsComponentData } from '@spartacus/storefront';
import { CmsBannerComponent } from '@spartacus/core';
import { of } from 'rxjs';

const mockCmsBannerData: CmsBannerComponent = {
  uid: 'test-banner',
  typeCode: 'CmsBannerComponent',
  headline: 'Test Headline',
  content: '<p>Test content</p>',
  urlLink: '/test',
};

// In TestBed providers:
{ provide: CmsComponentData, useValue: { data$: of(mockCmsBannerData) } }
```

Always type the mock data to match the CMS model interface. This catches mismatches at compile time rather than at runtime.

---

## Testing Module Imports

Spartacus provides testing-specific modules that stub out heavy dependencies. Always use these instead of the real modules in unit tests.

```typescript
import { I18nTestingModule, UrlTestingModule } from '@spartacus/core';
import { RouterTestingModule } from '@angular/router/testing';

await TestBed.configureTestingModule({
  imports: [
    I18nTestingModule,                    // stubs cxTranslate pipe — returns raw key
    UrlTestingModule,                      // stubs cxUrl pipe — returns raw array
    RouterTestingModule.withRoutes([]),     // provides Router without real routes
  ],
}).compileComponents();
```

- `I18nTestingModule` replaces `I18nModule` — avoids loading translation files and HTTP calls
- `UrlTestingModule` replaces `UrlModule` — avoids needing a real Spartacus URL configuration
- `RouterTestingModule.withRoutes([])` provides a minimal router for components that inject `Router` or use `routerLink`

---

## Component Stub Pattern

Stub child components to avoid importing their full module trees. Declare stubs in the test file or a shared test utilities file.

```typescript
import { Component, Input } from '@angular/core';

@Component({ selector: 'cx-icon', template: '' })
class MockIconComponent {
  @Input() type: string;
}

@Component({ selector: 'cx-media', template: '' })
class MockMediaComponent {
  @Input() container: any;
}

// In TestBed declarations alongside the component under test:
declarations: [WishlistButtonComponent, MockIconComponent, MockMediaComponent],
```

Match the stub's `selector` and `@Input()` bindings to the real child component. The template can be empty — you only need the selector to satisfy Angular's template compiler.

---

## MockStore for Facade Tests

Facades typically inject `Store` and dispatch actions or select state. Use `MockStore` from `@ngrx/store/testing` to control state and spy on dispatches.

```typescript
import { TestBed } from '@angular/core/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { WishlistFacade } from './wishlist.facade';
import { WishlistSelectors } from '../store/selectors';
import { WishlistActions } from '../store/actions';

describe('WishlistFacade', () => {
  let facade: WishlistFacade;
  let store: MockStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        WishlistFacade,
        provideMockStore({ initialState: { wishlist: { items: [] } } }),
      ],
    });

    facade = TestBed.inject(WishlistFacade);
    store = TestBed.inject(MockStore);
    spyOn(store, 'dispatch').and.callThrough();
  });

  it('should dispatch LoadWishlist action', () => {
    facade.loadWishlist();
    expect(store.dispatch).toHaveBeenCalledWith(
      WishlistActions.loadWishlist()
    );
  });

  it('should return wishlist items from selector', (done) => {
    const mockItems = [{ productCode: '12345' }];
    store.overrideSelector(WishlistSelectors.getWishlistItems, mockItems);
    store.refreshState();

    facade.getWishlistItems().subscribe(items => {
      expect(items).toEqual(mockItems);
      done();
    });
  });
});
```

Key points: call `store.refreshState()` after `overrideSelector()` to push the new value, and spy on `dispatch` before triggering actions.

---

## provideMockActions for Effect Tests

Effects consume an `Actions` observable and output new actions. Use `provideMockActions` with a `ReplaySubject` to feed actions and verify outputs.

```typescript
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { ReplaySubject, of, throwError } from 'rxjs';
import { WishlistEffects } from './wishlist.effects';
import { WishlistActions } from './wishlist.actions';
import { WishlistConnector } from '../connectors/wishlist.connector';

describe('WishlistEffects', () => {
  let effects: WishlistEffects;
  let actions$: ReplaySubject<any>;
  let connector: jasmine.SpyObj<WishlistConnector>;

  beforeEach(() => {
    actions$ = new ReplaySubject(1);
    connector = jasmine.createSpyObj('WishlistConnector', ['loadWishlist']);

    TestBed.configureTestingModule({
      providers: [
        WishlistEffects,
        provideMockActions(() => actions$),
        { provide: WishlistConnector, useValue: connector },
      ],
    });

    effects = TestBed.inject(WishlistEffects);
  });

  it('should emit loadWishlistSuccess on success', (done) => {
    const mockItems = [{ productCode: '12345' }];
    connector.loadWishlist.and.returnValue(of(mockItems));
    actions$.next(WishlistActions.loadWishlist());

    effects.loadWishlist$.subscribe(action => {
      expect(action).toEqual(WishlistActions.loadWishlistSuccess({ items: mockItems }));
      done();
    });
  });

  it('should emit loadWishlistFail on error', (done) => {
    connector.loadWishlist.and.returnValue(throwError(() => new Error('fail')));
    actions$.next(WishlistActions.loadWishlist());

    effects.loadWishlist$.subscribe(action => {
      expect(action.type).toBe(WishlistActions.loadWishlistFail.type);
      done();
    });
  });
});
```

Use `ReplaySubject(1)` so the action is available when the effect subscribes. Never use a real `Actions` stream — it would require a full store setup.

---

## HttpClientTestingModule for Adapter Tests

OCC adapters make HTTP calls. Use `HttpClientTestingModule` to intercept requests and assert on URL, method, and body.

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { WishlistOccAdapter } from './wishlist-occ.adapter';
import { ConverterService, OccEndpointsService } from '@spartacus/core';

describe('WishlistOccAdapter', () => {
  let adapter: WishlistOccAdapter;
  let httpMock: HttpTestingController;
  let converter: jasmine.SpyObj<ConverterService>;
  let occEndpoints: jasmine.SpyObj<OccEndpointsService>;

  beforeEach(() => {
    converter = jasmine.createSpyObj('ConverterService', ['pipeable', 'convert']);
    occEndpoints = jasmine.createSpyObj('OccEndpointsService', ['buildUrl']);
    occEndpoints.buildUrl.and.returnValue('/occ/v2/electronics/users/current/wishlist');

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        WishlistOccAdapter,
        { provide: ConverterService, useValue: converter },
        { provide: OccEndpointsService, useValue: occEndpoints },
      ],
    });

    adapter = TestBed.inject(WishlistOccAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();  // ensure no outstanding requests
  });

  it('should GET wishlist from OCC endpoint', () => {
    const mockResponse = { entries: [{ product: { code: '12345' } }] };
    adapter.loadWishlist('current').subscribe();

    const req = httpMock.expectOne('/occ/v2/electronics/users/current/wishlist');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });
});
```

Always call `httpMock.verify()` in `afterEach` to catch unmatched or unexpected HTTP requests.

---

## ConverterService Mock

Spartacus uses `ConverterService` to normalize API responses. Mock it with `createSpyObj` and verify the correct normalizer token is used.

```typescript
const converter = jasmine.createSpyObj('ConverterService', ['pipeable', 'convert']);

// pipeable() returns an rxjs operator — mock it to pass through values
converter.pipeable.and.returnValue(x => x);

// In assertions, verify the correct token was passed:
expect(converter.pipeable).toHaveBeenCalledWith(WISHLIST_NORMALIZER);
```

For adapters that also serialize request bodies:

```typescript
converter.convert.and.callFake((source: any) => source);

// Verify serialization token:
expect(converter.convert).toHaveBeenCalledWith(payload, WISHLIST_SERIALIZER);
```

The `pipeable()` mock returns an identity operator (`x => x`) so the response flows through unchanged. This isolates the adapter logic from the normalizer logic.

---

## Testing Observables

Two patterns for asserting on observable values in Jasmine tests.

**Subscribe with `done` callback** — for async observables:

```typescript
it('should return items', (done) => {
  facade.getItems().subscribe(items => {
    expect(items.length).toBe(3);
    done();
  });
});
```

**Synchronous with `take(1)`** — when the observable emits synchronously (e.g., from `of()` or `BehaviorSubject`):

```typescript
import { take } from 'rxjs/operators';

it('should return items synchronously', () => {
  let result: Item[];
  facade.getItems().pipe(take(1)).subscribe(items => (result = items));
  expect(result!.length).toBe(3);
});
```

Prefer the `done` callback pattern for clarity. Use `take(1)` only when you know the source is synchronous and want to avoid the `done` boilerplate.
