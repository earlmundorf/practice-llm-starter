# Spartacus Styling Examples

Focused code snippets showing correct and incorrect SCSS patterns for Spartacus theming.

---

## GOOD: Placeholder Selector Theme Override

Extends the Spartacus default styles cleanly. Only overrides what needs to change.

```scss
// src/styles/_mini-cart.scss
%cx-mini-cart {
  .cx-cart-count {
    background: var(--cx-color-primary);
    color: var(--cx-color-background);
    font-size: var(--cx-font-size-sm);
    font-weight: 600;
    min-width: 1.25rem;
    height: 1.25rem;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  cx-icon {
    font-size: 1.5rem;
    color: var(--cx-color-text);
    transition: color 0.2s;
  }

  &:hover cx-icon {
    color: var(--cx-color-primary);
  }
}
```

Why this is correct:
- Uses `%cx-mini-cart` placeholder selector — merges with Spartacus defaults
- Colors from CSS custom properties — changes with theme
- Transition for smooth hover effect
- No `!important` — works with Spartacus specificity

---

## GOOD: Responsive Component with Breakpoint Mixins

Mobile-first approach with Spartacus breakpoint mixins.

```scss
%cx-product-intro {
  display: flex;
  flex-direction: column;
  gap: var(--cx-spacing-md);
  padding: var(--cx-spacing-md);

  .cx-product-title {
    font-size: var(--cx-font-size-lg);
    font-weight: 700;
    color: var(--cx-color-text);
  }

  .cx-product-summary {
    display: none; // Hidden on mobile — save space
  }

  @include media-breakpoint-up(md) {
    flex-direction: row;
    gap: var(--cx-spacing-xl);

    .cx-product-summary {
      display: block;
      flex: 1;
      color: var(--cx-color-text-secondary);
    }
  }

  @include media-breakpoint-up(lg) {
    .cx-product-title {
      font-size: 1.5rem;
    }
  }
}
```

Why this is correct:
- Base styles target mobile
- Progressive enhancement with `breakpoint-up` for larger screens
- Design tokens for spacing and colors
- Selective content display per breakpoint

---

## GOOD: Dark Mode via CSS Custom Properties

```scss
// src/styles/_variables.scss
:root {
  --cx-color-primary: #1a73e8;
  --cx-color-background: #ffffff;
  --cx-color-surface: #f8f9fa;
  --cx-color-text: #202124;
  --cx-color-text-secondary: #5f6368;
  --cx-border-color: #dadce0;
}

@media (prefers-color-scheme: dark) {
  :root {
    --cx-color-primary: #8ab4f8;
    --cx-color-background: #202124;
    --cx-color-surface: #303134;
    --cx-color-text: #e8eaed;
    --cx-color-text-secondary: #9aa0a6;
    --cx-border-color: #5f6368;
  }
}
```

Why this is correct:
- All components automatically adapt — they reference `var(--cx-*)` tokens
- No per-component dark mode code needed
- Respects OS-level dark mode preference
- Single source of truth for color tokens

---

## BAD: !important Overrides

```scss
// DON'T do this
.cx-product-card {
  border: none !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1) !important;
  margin: 16px !important;
}

cx-header {
  background-color: navy !important;
  color: white !important;
}
```

**What's wrong:**
- `!important` creates specificity wars — impossible to override later
- Direct class targeting instead of placeholder selectors
- Hardcoded colors — won't adapt to theme changes or dark mode
- Hardcoded pixel values — not aligned with spacing scale
- Targeting `cx-header` element directly — brittle if Spartacus changes selectors

**Fix:** Use placeholder selectors and CSS custom properties:

```scss
%cx-product-card {
  border: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin: var(--cx-spacing-md);
}

%cx-header {
  background-color: var(--cx-color-primary);
  color: var(--cx-color-background);
}
```

---

## BAD: Hardcoded Values and Deep Nesting

```scss
.my-custom-component {
  .wrapper {
    .inner {
      .content {
        .title {
          font-size: 18px;        // magic number
          color: #333;             // hardcoded color
          margin-bottom: 12px;     // not on spacing scale
          margin-left: 20px;       // LTR-only, breaks RTL
        }
      }
    }
  }
}
```

**What's wrong:**
- 5 levels of nesting — specificity nightmare, hard to override
- Magic numbers (`18px`, `12px`, `20px`) not from design tokens
- Hardcoded color `#333` — won't adapt to theme or dark mode
- `margin-left` — doesn't flip for RTL languages
- Not using Spartacus placeholder selector

**Fix:**

```scss
%cx-my-custom-component {
  .cx-title {
    font-size: var(--cx-font-size-lg);
    color: var(--cx-color-text);
    margin-block-end: var(--cx-spacing-sm);
    margin-inline-start: var(--cx-spacing-lg);
  }
}
```

---

## BAD: Removing Focus Indicators

```scss
// DON'T do this
button:focus, a:focus, input:focus {
  outline: none;  // accessibility violation
}

*:focus {
  outline: 0;     // even worse — removes focus from everything
}
```

**What's wrong:**
- Removes keyboard focus indicators — WCAG 2.1 AA failure
- Keyboard-only users cannot see which element is focused
- Universal `*:focus` is especially destructive

**Fix:** Replace default outline with a custom focus-visible style:

```scss
button:focus-visible,
a:focus-visible {
  outline: 2px solid var(--cx-color-primary);
  outline-offset: 2px;
}
```

---

## GENERATE OUTPUT: /spartacus-styling generate ProductCard

Running `/spartacus-styling generate ProductCard` produces:

### src/styles/_product-card.scss

```scss
// Product Card style overrides
// Extends Spartacus %cx-product-card placeholder selector

%cx-product-card {
  border-radius: var(--cx-border-radius);
  border: 1px solid var(--cx-border-color);
  overflow: hidden;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    border-color: var(--cx-color-primary);
  }

  // Product image
  .cx-product-image {
    aspect-ratio: 1;
    background: var(--cx-color-surface);

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  // Product info
  .cx-product-name {
    font-weight: 600;
    color: var(--cx-color-text);
    margin-block-end: var(--cx-spacing-xs);
  }

  .cx-product-price {
    font-size: var(--cx-font-size-lg);
    font-weight: 700;
    color: var(--cx-color-primary);
  }

  .cx-product-rating {
    color: var(--cx-color-warning);
    font-size: var(--cx-font-size-sm);
  }

  // Stock status
  .cx-stock-status {
    font-size: var(--cx-font-size-sm);
    font-weight: 500;

    &.in-stock {
      color: var(--cx-color-success);
    }

    &.out-of-stock {
      color: var(--cx-color-danger);
    }
  }

  // Add to cart button
  .cx-add-to-cart {
    width: 100%;
    padding: var(--cx-spacing-sm) var(--cx-spacing-md);
    margin-block-start: var(--cx-spacing-md);

    &:focus-visible {
      outline: 2px solid var(--cx-color-primary);
      outline-offset: 2px;
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  // Responsive adjustments
  @include media-breakpoint-down(sm) {
    .cx-product-name {
      font-size: var(--cx-font-size-sm);
    }
  }

  // Reduced motion
  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
}
```

### Update src/styles.scss

```scss
// Add import for new override
@use './styles/product-card';
```
