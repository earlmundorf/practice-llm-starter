# Spartacus i18n Patterns

Reference snippets for internationalization in Spartacus 6.x with NgModules.

---

## Translation Chunk Registration

Register translation chunks via `provideDefaultConfig` so Spartacus can lazy-load translations per feature. The `chunks` map tells Spartacus which keys belong to which chunk, and `backend.loadPath` defines where to fetch them.

```typescript
import { provideDefaultConfig } from '@spartacus/core';

provideDefaultConfig({
  i18n: {
    backend: {
      loadPath: 'assets/i18n/{{lng}}/{{ns}}.json',
    },
    chunks: {
      orderHistory: ['orderHistory'],
    },
    fallbackLang: 'en',
  },
});
```

The chunk name `orderHistory` maps to a file like `assets/i18n/en/orderHistory.json`. When a template first uses a key from this chunk, Spartacus fetches the file on demand.

---

## Translation Object Structure

Translation files export a typed object with nested keys that mirror the dot-notation paths used in templates. The top-level key matches the chunk name.

```typescript
// i18n/en/order-history.ts
export const orderHistory = {
  orderHistory: {
    title: 'Order History',
    noOrders: 'You have no orders yet.',
    orderId: 'Order #{{ id }}',
    status: {
      pending: 'Pending',
      shipped: 'Shipped',
      delivered: 'Delivered',
      cancelled: 'Cancelled',
    },
    table: {
      orderId: 'Order ID',
      date: 'Date',
      status: 'Status',
      total: 'Total',
    },
  },
};
```

The key `'orderHistory.status.shipped'` resolves by traversing `orderHistory.status.shipped` in this object.

---

## cxTranslate Pipe with Parameters

Use the `cxTranslate` pipe in templates for all user-visible strings. Pass interpolation parameters as a second argument.

```html
<!-- Simple key -->
<h1>{{ 'orderHistory.title' | cxTranslate }}</h1>

<!-- Parameterized key -->
<span>{{ 'orderHistory.orderId' | cxTranslate: { id: order.code } }}</span>

<!-- Nested key -->
<span class="status">{{ 'orderHistory.status.' + order.status | cxTranslate }}</span>

<!-- Inside attribute binding -->
<button [attr.aria-label]="'orderHistory.viewDetails' | cxTranslate: { id: order.code }">
  {{ 'orderHistory.view' | cxTranslate }}
</button>
```

The pipe automatically subscribes to the translation observable and handles chunk loading. Always use single quotes around the key string inside the template expression.

---

## ICU Pluralization

Use ICU message format for plural, select, and nested expressions. Spartacus passes the translation string through an ICU parser before rendering.

```typescript
// In translation file
export const cart = {
  cart: {
    itemCount: '{count, plural, =0 {No items} one {1 item} other {{{count}} items}}',
    shipping: '{method, select, standard {Standard shipping} express {Express shipping} other {Shipping}}',
    summary: '{count, plural, =0 {Your cart is empty.} one {You have 1 item totaling {{total}}.} other {You have {count} items totaling {{total}}.}}',
  },
};
```

Template usage:

```html
<span>{{ 'cart.itemCount' | cxTranslate: { count: cart.totalItems } }}</span>
<span>{{ 'cart.summary' | cxTranslate: { count: cart.totalItems, total: cart.totalPrice?.formattedValue } }}</span>
```

Note: Inside ICU expressions, literal curly braces around interpolation variables use double braces `{{count}}`, while the ICU syntax itself uses single braces `{count, plural, ...}`.

---

## Programmatic Translation via TranslationService

When you need translated strings in TypeScript (e.g., for toast messages, dynamic titles, or SEO), use `TranslationService` instead of the pipe.

```typescript
import { TranslationService } from '@spartacus/core';

@Component({ /* ... */ })
export class OrderConfirmationComponent {
  constructor(
    protected translation: TranslationService,
    protected globalMessageService: GlobalMessageService
  ) {}

  showConfirmation(orderId: string): void {
    this.translation
      .translate('orderConfirmation.success', { id: orderId })
      .pipe(take(1))
      .subscribe(msg => {
        this.globalMessageService.add(msg, GlobalMessageType.MSG_TYPE_CONFIRMATION);
      });
  }

  // For page title (observable-based)
  title$ = this.translation.translate('orderHistory.title');
}
```

Always use `take(1)` for one-shot translations in imperative code. For template-bound observables, let the async pipe handle unsubscription.

---

## Overriding Spartacus Default Translations

To customize translations from `@spartacus/assets`, provide your overrides after the Spartacus defaults. Only override the keys you need to change.

```typescript
import { translations, translationChunksConfig } from '@spartacus/assets';

// Custom overrides — only the keys that differ
const customTranslations = {
  en: {
    cart: {
      cartItems: {
        itemRemoved: 'Product removed from your bag.',  // override default
      },
    },
  },
};

@NgModule({
  providers: [
    provideDefaultConfig({
      i18n: {
        resources: translations,          // Spartacus defaults first
        chunks: translationChunksConfig,
        fallbackLang: 'en',
      },
    }),
    provideDefaultConfig({
      i18n: {
        resources: customTranslations,    // Custom overrides second — takes precedence
      },
    }),
  ],
})
export class AppI18nModule {}
```

The second `provideDefaultConfig` call merges into the first. Keys in `customTranslations` override matching keys in `translations`; all other keys remain unchanged.

---

## Locale-Aware Formatting

Use Angular built-in pipes with the active Spartacus language for consistent locale formatting. Derive the locale from `LanguageService`, never hardcode it.

```typescript
import { LanguageService } from '@spartacus/core';

@Component({
  selector: 'cx-order-total',
  template: `
    @if (order$ | async; as order) {
      <span class="date">
        {{ order.created | date:'mediumDate':undefined:(language$ | async) }}
      </span>
      <span class="total">
        {{ order.totalPrice?.value | currency:order.totalPrice?.currencyIso:'symbol':undefined:(language$ | async) }}
      </span>
      <span class="quantity">
        {{ order.totalItems | number:undefined:(language$ | async) }}
      </span>
    }
  `,
})
export class OrderTotalComponent {
  order$ = this.orderService.getOrderDetails();
  language$ = this.languageService.getActive();

  constructor(
    protected orderService: OrderService,
    protected languageService: LanguageService
  ) {}
}
```

Angular's `DatePipe`, `DecimalPipe`, and `CurrencyPipe` all accept a locale parameter. Piping `language$` through async ensures the display updates when the user switches languages.
