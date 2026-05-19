# Spartacus Styling Patterns

Reference snippets for SCSS theming and styling in Spartacus 6.x.

---

## Placeholder Selector Overrides

Spartacus exposes `%cx-component-name` placeholder selectors for each component. Extend these to override styles without fighting specificity.

```scss
// src/styles/_product-card.scss
%cx-product-card {
  border-radius: var(--cx-border-radius, 8px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: box-shadow 0.2s ease;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  }

  .cx-product-name {
    font-weight: 600;
    color: var(--cx-color-text);
  }

  .cx-product-price {
    font-size: var(--cx-font-size-lg, 1.125rem);
    color: var(--cx-color-primary);
  }
}
```

Import the partial in your main styles entry:

```scss
// src/styles.scss
@use '@spartacus/styles' as *;
@use './styles/product-card';
```

The placeholder selector merges with the Spartacus default — you only need to declare the properties you're changing.

---

## CSS Custom Properties — Theme Tokens

Override Spartacus design tokens at `:root` for global changes or at component scope for targeted changes.

```scss
// src/styles/_variables.scss
:root {
  // Brand colors
  --cx-color-primary: #1a73e8;
  --cx-color-secondary: #5f6368;
  --cx-color-background: #ffffff;
  --cx-color-surface: #f8f9fa;
  --cx-color-text: #202124;
  --cx-color-text-secondary: #5f6368;
  --cx-color-success: #34a853;
  --cx-color-warning: #fbbc04;
  --cx-color-danger: #ea4335;

  // Typography
  --cx-font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  --cx-font-size-base: 1rem;
  --cx-font-size-sm: 0.875rem;
  --cx-font-size-lg: 1.125rem;

  // Spacing
  --cx-spacing-xs: 0.25rem;
  --cx-spacing-sm: 0.5rem;
  --cx-spacing-md: 1rem;
  --cx-spacing-lg: 1.5rem;
  --cx-spacing-xl: 2rem;

  // Borders
  --cx-border-radius: 8px;
  --cx-border-color: #dadce0;
}
```

---

## Responsive Design with Breakpoint Mixins

Spartacus provides breakpoint mixins that align with its layout system. Use mobile-first.

```scss
%cx-product-list {
  // Mobile base: single column
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--cx-spacing-md);
  padding: var(--cx-spacing-md);

  // Tablet: two columns
  @include media-breakpoint-up(md) {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--cx-spacing-lg);
  }

  // Desktop: three columns
  @include media-breakpoint-up(lg) {
    grid-template-columns: repeat(3, 1fr);
    padding: var(--cx-spacing-xl);
  }

  // Large desktop: four columns
  @include media-breakpoint-up(xl) {
    grid-template-columns: repeat(4, 1fr);
  }
}
```

Spartacus breakpoints: `sm` (576px), `md` (768px), `lg` (992px), `xl` (1200px).

---

## Component-Scoped Styles via :host

For styles that only apply to a specific custom component, use `:host` in the component SCSS.

```scss
// wishlist-button.component.scss
:host {
  display: inline-block;
}

.cx-wishlist-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--cx-spacing-sm);
  padding: var(--cx-spacing-sm) var(--cx-spacing-md);
  border: 1px solid var(--cx-border-color);
  border-radius: var(--cx-border-radius);
  background: var(--cx-color-background);
  color: var(--cx-color-text);
  cursor: pointer;
  transition: background-color 0.2s, border-color 0.2s;

  &:hover {
    background: var(--cx-color-surface);
    border-color: var(--cx-color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--cx-color-primary);
    outline-offset: 2px;
  }

  &.is-active {
    color: var(--cx-color-danger);
  }
}
```

---

## Focus Indicators

Every interactive element must have a visible focus indicator. Never remove outline without providing a replacement.

```scss
// Global focus style
%cx-focus-visible {
  &:focus-visible {
    outline: 2px solid var(--cx-color-primary);
    outline-offset: 2px;
  }

  // Remove default outline only when custom focus is applied
  &:focus:not(:focus-visible) {
    outline: none;
  }
}

// Apply to buttons, links, inputs
%cx-btn {
  @extend %cx-focus-visible;
}
```

---

## Z-Index Scale

Define a predictable z-index scale instead of arbitrary values.

```scss
// src/styles/_variables.scss
:root {
  --cx-z-index-dropdown: 100;
  --cx-z-index-sticky: 200;
  --cx-z-index-overlay: 300;
  --cx-z-index-modal-backdrop: 400;
  --cx-z-index-modal: 500;
  --cx-z-index-popover: 600;
  --cx-z-index-tooltip: 700;
}
```

Use in components:

```scss
.cx-modal {
  z-index: var(--cx-z-index-modal);
}
```

---

## RTL Support with Logical Properties

Use CSS logical properties for RTL language support. They flip automatically based on text direction.

```scss
// Instead of:
margin-left: var(--cx-spacing-md);
padding-right: var(--cx-spacing-sm);
text-align: left;
border-left: 2px solid var(--cx-border-color);

// Use:
margin-inline-start: var(--cx-spacing-md);
padding-inline-end: var(--cx-spacing-sm);
text-align: start;
border-inline-start: 2px solid var(--cx-border-color);
```

---

## Reduced Motion

Respect user preferences for reduced motion.

```scss
.cx-animated-element {
  transition: transform 0.3s ease, opacity 0.3s ease;

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
}
```

---

## Theme Layer Structure

Organize overrides in a `styles/` directory with partials.

```scss
// src/styles.scss — main entry point
@use '@spartacus/styles' as *;

// Theme tokens
@use './styles/variables';

// Component overrides
@use './styles/header';
@use './styles/product-card';
@use './styles/product-list';
@use './styles/cart';
@use './styles/checkout';
```

Each partial overrides one component's placeholder selector. This makes it easy to find and modify overrides.
