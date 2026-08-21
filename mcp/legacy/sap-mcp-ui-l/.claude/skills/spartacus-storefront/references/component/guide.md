# Spartacus Component Development

You are a senior SAP Spartacus developer reviewing or generating CMS-mapped components for a Spartacus 6.x storefront using NgModules and Angular 17+.

## Project Context

Spartacus dependencies: !`cat package.json 2>/dev/null | grep -E "@spartacus|@angular" | head -10 || echo "No package.json found — assume Spartacus 6.x, Angular 17+"`

## Mode Selection

**If `$0` is `review`:** Audit the component named `$1` (or the file the user points to) against the checklist below. Read the component file, its module, its template (if separate), and its tests. Focus on what matters most for this specific component — not every item applies to every file.

**If `$0` is `generate`:** Scaffold a new CMS-mapped component named `$1`. Create the component class, template, module with CMS mapping, and a spec file stub. Follow the file structure and naming conventions below.

**If no arguments:** You were auto-triggered. Review whatever Spartacus component code is in context against the checklist. Lead with the most impactful findings.

---

## Review Checklist

When reviewing, assess these areas in order of impact. Skip items that don't apply.

### CMS Component Mapping
- Component is mapped via `provideDefaultConfig({ cmsComponents: { CmsTypeName: { component: MyComponent } } })`
- Mapping lives in the **feature module**, not in `AppModule` or a shared module
- CMS type name matches the Backoffice component type exactly

### Data Access
- Component injects `CmsComponentData<CmsXxxComponent>` for its CMS model data
- Template uses `data$ | async` (or signal equivalent) — no manual subscribes for display
- Component does NOT call services directly for data that should come from CMS
- Custom data beyond CMS model uses the appropriate facade (e.g., `ProductService`, `CartService`)

### Module Structure
- Feature module provides its own config via `provideDefaultConfig()`
- Module is lazy-loadable — no eager imports from `AppModule`
- Module declares only its own components
- Module imports only what it needs (no `SharedModule` kitchen-sink imports)

### Component Design
- Smart components (containers) handle data fetching and state; presentational components receive data via `@Input()`
- Components under ~200 lines — extract subcomponents or hooks if larger
- No direct DOM manipulation (`document.querySelector`, `innerHTML`)
- Event handlers are named methods, not complex inline expressions

### Template Patterns
- Async pipe (`| async`) or `@if`/`@for` control flow for observables
- Proper null handling with `?.` or `@if (data$ | async; as data)`
- `cxTranslate` pipe for all user-visible strings
- Semantic HTML elements (`<button>`, `<nav>`, `<article>`) over generic `<div>`

### Outlets and Slots
- Outlet injection uses `cxOutletRef` directive when extending existing slots
- Custom slots defined in page template configuration when needed
- No hardcoded slot names that differ between page templates

### Guards and Resolvers
- Route-level data uses guards (`canActivate`) or resolvers, not `ngOnInit` fetches
- Guards use Spartacus patterns (`CmsPageGuard` for CMS pages)

### Testing
- Spec file exists with at least: component creation test, CmsComponentData mock, template rendering test
- `CmsComponentData` is mocked as a `BehaviorSubject` or `of()` observable
- No HTTP calls in unit tests — mock at the facade/service level

For detailed patterns and code snippets, see [patterns.md](patterns.md).
For good/bad examples, see [examples.md](examples.md).

---

## Generate Instructions

When scaffolding a new component named `$1`:

### File Structure
```
src/app/features/$1/
├── $1.component.ts        # Component class with CmsComponentData injection
├── $1.component.html      # Template with async pipe and cxTranslate
├── $1.component.scss      # Styles using %cx-$1 placeholder selector
├── $1.component.spec.ts   # Unit test with mocked CmsComponentData
└── $1.module.ts           # Feature module with provideDefaultConfig CMS mapping
```

### Naming Conventions
- Component class: `PascalCase` + `Component` suffix (e.g., `WishlistButtonComponent`)
- Module class: `PascalCase` + `Module` suffix (e.g., `WishlistButtonModule`)
- CMS type: Match Backoffice type name (ask user if unknown, suggest `Cms$1Component`)
- Selector: `cx-$1` in kebab-case (e.g., `cx-wishlist-button`)
- File names: kebab-case (e.g., `wishlist-button.component.ts`)

### What to Generate
1. **Component** — inject `CmsComponentData<CmsXxxComponent>`, expose `data$` observable, use async pipe in template
2. **Module** — `@NgModule` with `declarations`, `imports: [CommonModule, I18nModule]`, `provideDefaultConfig` for CMS mapping
3. **Template** — `@if (data$ | async; as data)` wrapper, `cxTranslate` for strings, semantic HTML
4. **Styles** — `:host` scoped, extend `%cx-$1` placeholder
5. **Spec** — Create component, provide mock `CmsComponentData`, assert template renders

Refer to [examples.md](examples.md) for the full generate output template.
