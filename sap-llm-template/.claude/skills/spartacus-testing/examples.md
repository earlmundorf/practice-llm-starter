# Spartacus Testing Examples

Focused code snippets showing correct and incorrect patterns for unit testing in Spartacus 6.x.

---

## GOOD: CMS Component Test

Full spec with CmsComponentData mock, I18nTestingModule, child stubs, and template assertions.

```typescript
// wishlist-button.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { CmsComponentData } from '@spartacus/storefront';
import { I18nTestingModule } from '@spartacus/core';
import { of } from 'rxjs';
import {
  WishlistButtonComponent,
  CmsWishlistButtonComponent,
} from './wishlist-button.component';

@Component({ selector: 'cx-icon', template: '' })
class MockIconComponent {
  @Input() type: string;
}

describe('WishlistButtonComponent', () => {
  let component: WishlistButtonComponent;
  let fixture: ComponentFixture<WishlistButtonComponent>;

  const mockData: CmsWishlistButtonComponent = {
    uid: 'test-wishlist-btn',
    typeCode: 'CmsWishlistButtonComponent',
    productCode: '12345',
    label: 'Add to Wishlist',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [WishlistButtonComponent, MockIconComponent],
      imports: [I18nTestingModule],
      providers: [
        { provide: CmsComponentData, useValue: { data$: of(mockData) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WishlistButtonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render button with label from CMS data', () => {
    const button: HTMLButtonElement =
      fixture.nativeElement.querySelector('button');
    expect(button).toBeTruthy();
    expect(button.textContent).toContain('Add to Wishlist');
  });

  it('should render icon component', () => {
    const icon = fixture.nativeElement.querySelector('cx-icon');
    expect(icon).toBeTruthy();
  });

  it('should not render when data is missing', () => {
    component.data$ = of(undefined as any);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button');
    expect(button).toBeFalsy();
  });
});
```

Why this is correct:
- `CmsComponentData` is mocked with typed data matching the CMS model interface
- `I18nTestingModule` avoids loading real translation files
- `MockIconComponent` stubs the child component without importing `IconModule`
- Tests verify template rendering after `fixture.detectChanges()`
- No `NO_ERRORS_SCHEMA` — template binding errors surface immediately

---

## GOOD: Effect Test with Mock Connector

Effect test using `provideMockActions`, mocked connector, verifying both success and failure paths.

```typescript
// wishlist.effects.spec.ts
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { ReplaySubject, of, throwError } from 'rxjs';
import { WishlistEffects } from './wishlist.effects';
import { WishlistActions } from './wishlist.actions';
import { WishlistConnector } from '../connectors/wishlist.connector';
import { WishlistItem } from '../models/wishlist.model';
import { HttpErrorResponse } from '@angular/common/http';
import { normalizeHttpError, LoggerService } from '@spartacus/core';

describe('WishlistEffects', () => {
  let effects: WishlistEffects;
  let actions$: ReplaySubject<any>;
  let connector: jasmine.SpyObj<WishlistConnector>;
  let logger: jasmine.SpyObj<LoggerService>;

  const mockItems: WishlistItem[] = [
    { productCode: '12345', addedAt: '2024-01-01' },
    { productCode: '67890', addedAt: '2024-01-02' },
  ];

  beforeEach(() => {
    actions$ = new ReplaySubject(1);
    connector = jasmine.createSpyObj('WishlistConnector', [
      'loadWishlist',
      'addToWishlist',
    ]);
    logger = jasmine.createSpyObj('LoggerService', ['error']);

    TestBed.configureTestingModule({
      providers: [
        WishlistEffects,
        provideMockActions(() => actions$),
        { provide: WishlistConnector, useValue: connector },
        { provide: LoggerService, useValue: logger },
      ],
    });

    effects = TestBed.inject(WishlistEffects);
  });

  describe('loadWishlist$', () => {
    it('should emit loadWishlistSuccess with items on success', (done) => {
      connector.loadWishlist.and.returnValue(of(mockItems));
      actions$.next(WishlistActions.loadWishlist({ userId: 'current' }));

      effects.loadWishlist$.subscribe(action => {
        expect(action).toEqual(
          WishlistActions.loadWishlistSuccess({ items: mockItems })
        );
        done();
      });
    });

    it('should emit loadWishlistFail with normalized error on failure', (done) => {
      const errorResponse = new HttpErrorResponse({ status: 500 });
      connector.loadWishlist.and.returnValue(throwError(() => errorResponse));
      actions$.next(WishlistActions.loadWishlist({ userId: 'current' }));

      effects.loadWishlist$.subscribe(action => {
        expect(action.type).toBe(WishlistActions.loadWishlistFail.type);
        done();
      });
    });
  });

  describe('addToWishlist$', () => {
    it('should emit addToWishlistSuccess on success', (done) => {
      connector.addToWishlist.and.returnValue(of(undefined));
      actions$.next(
        WishlistActions.addToWishlist({ userId: 'current', productCode: '12345' })
      );

      effects.addToWishlist$.subscribe(action => {
        expect(action).toEqual(
          WishlistActions.addToWishlistSuccess({ productCode: '12345' })
        );
        done();
      });
    });
  });
});
```

Why this is correct:
- `ReplaySubject(1)` ensures the action is buffered for the effect subscription
- Connector is mocked — no real HTTP calls
- Both success and error paths are tested
- Error response uses `HttpErrorResponse` matching real error shape
- Each effect gets its own `describe` block for clarity

---

## GOOD: OCC Adapter Test

Adapter test with `HttpClientTestingModule`, URL assertion, method verification, and converter mock.

```typescript
// wishlist-occ.adapter.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { WishlistOccAdapter } from './wishlist-occ.adapter';
import {
  ConverterService,
  OccEndpointsService,
} from '@spartacus/core';
import { WISHLIST_NORMALIZER } from '../connectors/converters';

describe('WishlistOccAdapter', () => {
  let adapter: WishlistOccAdapter;
  let httpMock: HttpTestingController;
  let converter: jasmine.SpyObj<ConverterService>;
  let occEndpoints: jasmine.SpyObj<OccEndpointsService>;

  const mockEndpointUrl = '/occ/v2/electronics/users/current/wishlists';

  beforeEach(() => {
    converter = jasmine.createSpyObj('ConverterService', ['pipeable', 'convert']);
    converter.pipeable.and.returnValue((x: any) => x);

    occEndpoints = jasmine.createSpyObj('OccEndpointsService', ['buildUrl']);
    occEndpoints.buildUrl.and.returnValue(mockEndpointUrl);

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
    httpMock.verify();
  });

  it('should GET wishlist and use normalizer', () => {
    const mockResponse = {
      entries: [{ product: { code: '12345' } }],
    };

    adapter.loadWishlist('current').subscribe();

    const req = httpMock.expectOne(mockEndpointUrl);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(converter.pipeable).toHaveBeenCalledWith(WISHLIST_NORMALIZER);
  });

  it('should POST to add item to wishlist', () => {
    adapter.addItem('current', '12345').subscribe();

    const req = httpMock.expectOne(mockEndpointUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ product: { code: '12345' } });
    req.flush({});
  });

  it('should DELETE to remove item from wishlist', () => {
    occEndpoints.buildUrl.and.returnValue(`${mockEndpointUrl}/entry/0`);
    adapter.removeItem('current', 0).subscribe();

    const req = httpMock.expectOne(`${mockEndpointUrl}/entry/0`);
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });
});
```

Why this is correct:
- `HttpClientTestingModule` intercepts all HTTP calls — no real network traffic
- `httpMock.verify()` in `afterEach` catches unexpected or unmatched requests
- `ConverterService` is mocked with `pipeable` returning an identity operator
- Tests verify the correct normalizer token is passed to `pipeable()`
- Each HTTP method (GET, POST, DELETE) is tested separately

---

## BAD: NO_ERRORS_SCHEMA

Test that compiles but hides broken template bindings.

```typescript
// bad-component.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { WishlistButtonComponent } from './wishlist-button.component';
import { CmsComponentData } from '@spartacus/storefront';
import { of } from 'rxjs';

describe('WishlistButtonComponent', () => {
  let fixture: ComponentFixture<WishlistButtonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [WishlistButtonComponent],
      schemas: [NO_ERRORS_SCHEMA],  // PROBLEM: hides all template errors
      providers: [
        { provide: CmsComponentData, useValue: { data$: of({}) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WishlistButtonComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
```

**What's wrong:**
- `NO_ERRORS_SCHEMA` suppresses all unknown element and property binding errors
- Typos in template bindings (e.g., `[typo]="value"`) pass silently
- Missing child component declarations go unnoticed
- Missing pipe imports (like `cxTranslate`) are hidden
- The test gives false confidence — it passes but the component may be broken at runtime

**Fix:** Remove `NO_ERRORS_SCHEMA`. Declare stub components for child selectors and import `I18nTestingModule` for the `cxTranslate` pipe.

---

## BAD: Untyped Mocks

Test that uses `any` everywhere, losing type safety and making refactoring fragile.

```typescript
// bad-facade.spec.ts
import { TestBed } from '@angular/core/testing';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { WishlistFacade } from './wishlist.facade';

describe('WishlistFacade', () => {
  let facade: WishlistFacade;
  let store: any;  // PROBLEM: untyped

  beforeEach(() => {
    store = {
      dispatch: jasmine.createSpy('dispatch'),
      pipe: jasmine.createSpy('pipe').and.returnValue(of([])),
    } as any;  // PROBLEM: no type checking on mock shape

    TestBed.configureTestingModule({
      providers: [
        WishlistFacade,
        { provide: Store, useValue: store },
      ],
    });

    facade = TestBed.inject(WishlistFacade);
  });

  it('should load wishlist', () => {
    facade.loadWishlist();
    expect(store.dispatch).toHaveBeenCalledWith(jasmine.objectContaining({
      type: '[Wishlist] Load',  // PROBLEM: hardcoded string, not action reference
    }));
  });

  it('should return items', (done) => {
    facade.getItems().subscribe((items: any) => {  // PROBLEM: any in assertion
      expect(items).toBeTruthy();  // PROBLEM: weak assertion
      done();
    });
  });
});
```

**What's wrong:**
- `store: any` bypasses TypeScript — mock can have wrong shape without compile errors
- Hardcoded action type string `'[Wishlist] Load'` breaks silently if the action type changes
- `jasmine.objectContaining` with a string type is fragile — use the action creator directly
- `items: any` in the subscribe loses type safety
- `expect(items).toBeTruthy()` is a weak assertion — does not verify the actual data

**Fix:** Use `MockStore` from `@ngrx/store/testing`, reference action creators directly (e.g., `WishlistActions.loadWishlist()`), and type all mock data.

---

## GENERATE OUTPUT: /spartacus-testing generate wishlist-button

Running `/spartacus-testing generate wishlist-button` for a component file produces:

### wishlist-button.component.spec.ts

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { CmsComponentData } from '@spartacus/storefront';
import { I18nTestingModule } from '@spartacus/core';
import { of } from 'rxjs';
import {
  WishlistButtonComponent,
  CmsWishlistButtonComponent,
} from './wishlist-button.component';

// --- Child component stubs ---

@Component({ selector: 'cx-icon', template: '' })
class MockIconComponent {
  @Input() type: string;
}

// --- Test suite ---

describe('WishlistButtonComponent', () => {
  let component: WishlistButtonComponent;
  let fixture: ComponentFixture<WishlistButtonComponent>;

  // --- Mock data ---

  const mockData: CmsWishlistButtonComponent = {
    uid: 'test-wishlist-btn',
    typeCode: 'CmsWishlistButtonComponent',
    productCode: '12345',
    label: 'Add to Wishlist',
  };

  const mockCmsComponentData: CmsComponentData<CmsWishlistButtonComponent> = {
    data$: of(mockData),
    uid: 'test-wishlist-btn',
  } as CmsComponentData<CmsWishlistButtonComponent>;

  // --- TestBed setup ---

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [WishlistButtonComponent, MockIconComponent],
      imports: [I18nTestingModule],
      providers: [
        { provide: CmsComponentData, useValue: mockCmsComponentData },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WishlistButtonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // --- Creation ---

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // --- Template rendering ---

  it('should render button with label from CMS data', () => {
    const button: HTMLButtonElement =
      fixture.nativeElement.querySelector('button');
    expect(button).toBeTruthy();
    expect(button.textContent).toContain('Add to Wishlist');
  });

  it('should render icon component', () => {
    const icon = fixture.nativeElement.querySelector('cx-icon');
    expect(icon).toBeTruthy();
  });

  // --- Behavior ---

  it('should expose data$ observable with CMS data', (done) => {
    component.data$.subscribe(data => {
      expect(data.productCode).toBe('12345');
      expect(data.label).toBe('Add to Wishlist');
      done();
    });
  });

  // --- Edge cases ---

  it('should handle missing CMS data gracefully', () => {
    component.data$ = of(undefined as any);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button');
    expect(button).toBeFalsy();
  });
});
```
