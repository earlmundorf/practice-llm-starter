# Spartacus i18n Examples

Focused code snippets showing correct and incorrect patterns for internationalization in Spartacus 6.x.

---

## GOOD: Feature Translation Chunk with Namespace

Translation file uses a feature-scoped namespace, chunk config registers it properly, and the template consumes keys via `cxTranslate`.

```typescript
// i18n/en/wish-list.ts
export const wishList = {
  wishList: {
    title: 'My Wish List',
    empty: 'Your wish list is empty.',
    itemCount: '{count, plural, =0 {No items} one {1 item} other {{{count}} items}}',
    addedSuccess: '{{ name }} added to your wish list.',
    remove: 'Remove',
    removeConfirm: 'Remove {{ name }} from wish list?',
    moveToCart: 'Move to Cart',
    table: {
      product: 'Product',
      price: 'Price',
      availability: 'Availability',
      actions: 'Actions',
    },
  },
};
```

```typescript
// i18n/wish-list-translations.ts
import { wishList } from './en/wish-list';

export const wishListTranslations = {
  en: { wishList },
};

export const wishListTranslationChunksConfig = {
  wishList: ['wishList'],
};
```

```html
<!-- wish-list.component.html -->
@if (items$ | async; as items) {
  <h2>{{ 'wishList.title' | cxTranslate }}</h2>
  <p class="item-count">{{ 'wishList.itemCount' | cxTranslate: { count: items.length } }}</p>
  @if (items.length === 0) {
    <p>{{ 'wishList.empty' | cxTranslate }}</p>
  } @else {
    @for (item of items; track item.product?.code) {
      <div class="wish-list-item">
        <span>{{ item.product?.name }}</span>
        <button (click)="remove(item)" [attr.aria-label]="'wishList.removeConfirm' | cxTranslate: { name: item.product?.name }">
          {{ 'wishList.remove' | cxTranslate }}
        </button>
      </div>
    }
  }
}
```

Why this is correct:
- Translation keys are namespaced under `wishList` — no collision with other features
- ICU pluralization handles 0, 1, and many cases
- Parameterized translations interpolate product names dynamically
- Chunk is self-contained and lazy-loadable

---

## GOOD: Pluralization with ICU Format

ICU plural syntax handles all count cases correctly and is rendered via `cxTranslate` with a count parameter.

```typescript
// i18n/en/search-results.ts
export const searchResults = {
  searchResults: {
    resultsCount: '{count, plural, =0 {No results found} one {1 result found} other {{{count}} results found}}',
    filterCount: '{count, plural, =0 {No active filters} one {1 active filter} other {{{count}} active filters}}',
    pageInfo: 'Showing {{start}} - {{end}} of {total, plural, one {1 result} other {{{total}} results}}',
  },
};
```

```html
<p class="results-count">
  {{ 'searchResults.resultsCount' | cxTranslate: { count: pagination.totalResults } }}
</p>
<p class="page-info">
  {{ 'searchResults.pageInfo' | cxTranslate: {
    start: pagination.currentPage * pagination.pageSize + 1,
    end: endIndex,
    total: pagination.totalResults
  } }}
</p>
```

Why this is correct:
- Each plural case (=0, one, other) provides natural-sounding text
- `{{count}}` inside ICU uses double braces for interpolation
- Multiple ICU expressions can coexist with regular interpolation params in the same string

---

## BAD: Hardcoded Strings in Templates

```html
<!-- bad-product-list.component.html -->
@if (products$ | async; as products) {
  <h2>Product List</h2>
  <p>Showing {{ products.length }} products</p>
  @for (product of products; track product.code) {
    <div class="product-card">
      <span>{{ product.name }}</span>
      <span>Price: {{ product.price?.formattedValue }}</span>
      <button>Add to Cart</button>
      <button>Remove</button>
    </div>
  }
  @empty {
    <p>No products found. Try a different search.</p>
  }
}
```

**What's wrong:**
- "Product List", "Add to Cart", "Remove", and "No products found" are hardcoded English strings
- "Showing X products" doesn't use ICU pluralization — "Showing 1 products" is grammatically wrong
- "Price:" label is hardcoded and won't translate
- No way for translators to localize this component without code changes

**Fix:** Replace every user-visible string with `{{ 'key' | cxTranslate }}` and add a translation chunk with ICU pluralization for counts.

---

## BAD: Flat Global Translation Keys

```typescript
// i18n/en/common.ts — DON'T do this
export const common = {
  save: 'Save',
  cancel: 'Cancel',
  delete: 'Delete',
  edit: 'Edit',
  loading: 'Loading...',
  error: 'An error occurred',
  success: 'Success',
  submit: 'Submit',
  back: 'Back',
  next: 'Next',
};
```

```html
<!-- Used across many features -->
<button>{{ 'save' | cxTranslate }}</button>
<button>{{ 'cancel' | cxTranslate }}</button>
```

**What's wrong:**
- Flat keys like `'save'` and `'cancel'` collide across features — one feature's "Save" may need different text than another's
- No namespace means all keys land in one chunk — defeats lazy loading
- Impossible to override translations for a single feature without affecting every usage
- Adding context-specific text (e.g., "Save Address" vs "Save Payment") requires renaming keys everywhere

**Fix:** Namespace under the feature: `'addressForm.save'`, `'paymentForm.save'`. Each feature owns its own chunk with its own keys, even for common verbs.

---

## GENERATE OUTPUT: /spartacus-i18n generate order-history

Running `/spartacus-i18n generate order-history` produces these files:

### i18n/en/order-history.ts

```typescript
export const orderHistory = {
  orderHistory: {
    title: 'Order History',
    noOrders: 'You have no orders yet.',
    orderCount: '{count, plural, =0 {No orders} one {1 order} other {{{count}} orders}}',
    orderId: 'Order #{{ id }}',
    viewDetails: 'View details for order #{{ id }}',
    table: {
      orderId: 'Order ID',
      date: 'Date',
      status: 'Status',
      total: 'Total',
    },
    status: {
      pending: 'Pending',
      processing: 'Processing',
      shipped: 'Shipped',
      delivered: 'Delivered',
      cancelled: 'Cancelled',
    },
    filters: {
      allOrders: 'All Orders',
      dateRange: 'Date Range',
      statusFilter: 'Filter by Status',
    },
  },
};
```

### i18n/order-history-translations.ts

```typescript
import { orderHistory } from './en/order-history';

export const orderHistoryTranslations = {
  en: { orderHistory },
};

export const orderHistoryTranslationChunksConfig = {
  orderHistory: ['orderHistory'],
};
```

### i18n/order-history-i18n.module.ts

```typescript
import { NgModule } from '@angular/core';
import { provideDefaultConfig, I18nConfig } from '@spartacus/core';
import {
  orderHistoryTranslations,
  orderHistoryTranslationChunksConfig,
} from './order-history-translations';

@NgModule({
  providers: [
    provideDefaultConfig(<I18nConfig>{
      i18n: {
        resources: orderHistoryTranslations,
        chunks: orderHistoryTranslationChunksConfig,
        fallbackLang: 'en',
      },
    }),
  ],
})
export class OrderHistoryI18nModule {}
```

### Sample template usage

```html
<!-- order-history.component.html -->
@if (orders$ | async; as orders) {
  <h1>{{ 'orderHistory.title' | cxTranslate }}</h1>
  <p>{{ 'orderHistory.orderCount' | cxTranslate: { count: orders.length } }}</p>

  @if (orders.length === 0) {
    <p class="empty-state">{{ 'orderHistory.noOrders' | cxTranslate }}</p>
  } @else {
    <table>
      <thead>
        <tr>
          <th>{{ 'orderHistory.table.orderId' | cxTranslate }}</th>
          <th>{{ 'orderHistory.table.date' | cxTranslate }}</th>
          <th>{{ 'orderHistory.table.status' | cxTranslate }}</th>
          <th>{{ 'orderHistory.table.total' | cxTranslate }}</th>
        </tr>
      </thead>
      <tbody>
        @for (order of orders; track order.code) {
          <tr>
            <td>{{ 'orderHistory.orderId' | cxTranslate: { id: order.code } }}</td>
            <td>{{ order.placed | date:'mediumDate' }}</td>
            <td>{{ 'orderHistory.status.' + order.status | cxTranslate }}</td>
            <td>{{ order.total?.formattedValue }}</td>
          </tr>
        }
      </tbody>
    </table>
  }
}
```
