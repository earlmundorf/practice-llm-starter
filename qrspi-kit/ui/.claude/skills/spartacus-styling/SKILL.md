---
name: spartacus-styling
description: |
  Review or generate SAP Spartacus 6.x SCSS theming and styling.
  Covers CSS custom properties, placeholder selector overrides, responsive
  breakpoints, component-scoped styles, and the Spartacus design system.
  Auto-triggers on Spartacus SCSS, component styles, theme overrides, or cx- classes.
  Also trigger with: "spartacus styles", "spartacus theme", "cx- styles",
  "placeholder selector", "scss override", "breakpoint mixin".
argument-hint: "review|generate [ComponentName]"
allowed-tools: [Read, Grep, Glob, Edit, Write]
effort: medium
---

# Spartacus Theming & Styling

You are a senior SAP Spartacus developer reviewing or generating SCSS styles for a Spartacus 6.x storefront.

## Project Context

Spartacus dependencies: !`cat package.json 2>/dev/null | grep -E "@spartacus" | head -5 || echo "No package.json found — assume Spartacus 6.x"`

Style entry point: !`head -20 src/styles.scss 2>/dev/null || echo "No src/styles.scss found"`

## Mode Selection

**If `$0` is `review`:** Audit the styles for component `$1` (or the SCSS the user points to). Check for proper override techniques, accessibility, responsiveness, and Spartacus design system alignment.

**If `$0` is `generate`:** Scaffold style overrides for a Spartacus component named `$1`. Create SCSS using placeholder selectors, CSS custom properties, and responsive breakpoints.

**If no arguments:** Auto-triggered. Review whatever Spartacus SCSS is in context. Lead with the most impactful findings.

---

## Review Checklist

### Override Technique
- Overrides use `%cx-component-name` placeholder selectors — not direct class targeting
- No modifications to `@spartacus/styles` source files
- Custom styles extend the theme layer, not fight it
- No `!important` unless overriding third-party styles with no alternative (document why)
- Overrides are in a dedicated `styles/` directory or component-scoped SCSS

### CSS Custom Properties
- Theme tokens use `--cx-*` namespace: `--cx-color-primary`, `--cx-font-size-base`, etc.
- Custom properties override Spartacus defaults at `:root` or component scope
- No hardcoded color values in component styles — use `var(--cx-color-*)` tokens
- Font sizes, spacing, and border-radius use Spartacus tokens where available

### Responsive Design
- Uses Spartacus breakpoint mixins: `@include media-breakpoint-down(md)`, `@include media-breakpoint-up(lg)`
- Mobile-first approach — base styles for mobile, `breakpoint-up` for larger screens
- Touch targets at least 44x44px on mobile
- No hardcoded pixel widths that break on small screens
- Content readable without horizontal scroll at 320px

### Component-Scoped Styles
- Component SCSS uses `:host` for host element styling
- `ViewEncapsulation` is default (Emulated) — not `None` unless justified
- Scoped styles don't leak to children via deep selectors (`::ng-deep` is deprecated)
- Styles don't conflict with Spartacus default component styles

### Accessibility
- Color contrast meets WCAG 2.1 AA (4.5:1 for text, 3:1 for large text)
- Focus indicators visible on all interactive elements
- Focus styles not removed (`outline: none`) without replacement
- Sufficient contrast in both light and dark mode (if applicable)
- Reduced motion respected: `@media (prefers-reduced-motion: reduce)`

### SCSS Quality
- No magic numbers — use variables or design tokens
- Nesting depth max 3 levels
- No redundant selectors or unused styles
- `@use` instead of `@import` (Sass module system)
- Logical properties preferred for RTL support: `margin-inline-start` over `margin-left`

### Z-Index Scale
- Uses a defined z-index scale, not arbitrary values
- Spartacus z-index tokens: `$cx-z-index-modal`, `$cx-z-index-dropdown`, etc.
- No z-index arms race (values like `9999`)

For detailed patterns and code snippets, see [patterns.md](patterns.md).
For good/bad examples, see [examples.md](examples.md).

---

## Generate Instructions

When scaffolding style overrides for component `$1`:

### File Structure

For theme-level overrides (affects all instances):
```
src/styles/
├── _variables.scss           # CSS custom property overrides
├── _$1.scss                  # Placeholder selector overrides for $1
└── styles.scss               # Imports all partials
```

For component-scoped overrides:
```
src/app/features/$1/
└── $1.component.scss         # :host scoped styles
```

### What to Generate
1. **Placeholder selector override** — `%cx-$1 { ... }` extending the Spartacus default
2. **CSS custom property overrides** — `--cx-*` tokens for colors, spacing, typography
3. **Responsive breakpoints** — mobile-first with `@include media-breakpoint-up(md/lg)`
4. **Focus styles** — visible focus indicators for interactive elements
5. **Dark mode** — `@media (prefers-color-scheme: dark)` or `--cx-*` dark tokens

Refer to [examples.md](examples.md) for the full generate output template.
