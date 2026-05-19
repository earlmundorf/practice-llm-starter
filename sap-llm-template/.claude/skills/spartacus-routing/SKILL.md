---
name: spartacus-routing
description: |
  Review or generate SAP Spartacus 6.x CMS-driven routing, configurable routes, guards, and resolvers.
  Covers CmsPageGuard, SemanticPathService, cxUrl pipe, custom URL matchers, and PageMetaResolver.
  Auto-triggers on routing config, SemanticPathService, CmsPageGuard, UrlModule, URL matchers.
  Also trigger with: "spartacus routing", "CMS page route", "semantic path",
  "route guard", "URL matcher", "configurable routes".
argument-hint: "review|generate [RouteName]"
allowed-tools: [Read, Grep, Glob, Bash(ng *), Edit, Write]
effort: high
---

# Spartacus Routing Development

You are a senior SAP Spartacus developer reviewing or generating CMS-driven routing for a Spartacus 6.x storefront using NgModules and Angular 17+.

## Project Context

Spartacus dependencies: !`cat package.json 2>/dev/null | grep -E "@spartacus|@angular/router" | head -10 || echo "No package.json found — assume Spartacus 6.x, Angular 17+"`

## Mode Selection

**If `$0` is `review`:** Audit the routing for `$1` (or the file the user points to) against the checklist below. Read the routing module, guards, resolvers, and route config. Focus on what matters most for this specific route — not every item applies to every file.

**If `$0` is `generate`:** Scaffold CMS-driven routing for a feature named `$1`. Create the routing module with CmsPageGuard, optional custom guard, page meta resolver, and feature module. Follow the file structure and naming conventions below.

**If no arguments:** You were auto-triggered. Review whatever Spartacus routing code is in context against the checklist. Lead with the most impactful findings.

---

## Review Checklist

When reviewing, assess these areas in order of impact. Skip items that don't apply.

### CMS-Driven Routing
- Routes use `CmsPageGuard` and `PageLayoutComponent` as the component
- Route `data` specifies `pageLabel` or `cxRoute` to identify the CMS page
- CMS pages resolve their layout and slots automatically via `CmsPageGuard`

### Configurable Routes
- Routes defined via `provideDefaultConfig({ routing: { routes: { ... } } })`, not hardcoded in RouterModule.forChild()
- paths array supports multiple aliases for the same logical route
- Route params properly mapped in `paramsMapping` when API field names differ from URL params

### SemanticPathService and cxUrl
- Navigation uses `cxUrl` pipe in templates or `SemanticPathService.transform()` in TypeScript
- No hardcoded URL strings in routerLink or router.navigate()
- Route params passed as object to cxUrl pipe: `{ cxRoute: 'product', params: { code, name } } | cxUrl`

### URL Matchers
- Custom URL matchers used when route pattern is dynamic or ambiguous
- Matchers return `UrlMatchResult` or `null`
- Matchers registered via route config `matcher` property

### Route Guards
- Guards return `Observable<boolean | UrlTree>`
- CMS pages use `CmsPageGuard` (do not create custom guards for CMS page loading)
- Custom guards compose with Spartacus guards (`AuthGuard`, `NotAuthGuard`)
- Redirect via `router.parseUrl()`, not `router.navigate()` inside guards

### Page Meta / SEO
- Custom pages provide `PageMetaResolver` for title, description, and robots meta
- Resolvers extend Spartacus `PageMetaResolver` and implement resolver interfaces
- Canonical URLs handled via `PageMetaService`
- No hardcoded page titles in component templates

### Lazy Loading
- Feature modules lazy-loaded via `featureModules` config, not eager RouterModule.forChild in AppModule
- Route config lives in the feature module, not AppModule

For detailed patterns and code snippets, see [patterns.md](patterns.md).
For good/bad examples, see [examples.md](examples.md).

---

## Generate Instructions

When scaffolding routing for a feature named `$1`:

### File Structure
```
src/app/features/$1/
├── $1-routing.module.ts      # Route config with CmsPageGuard
├── $1.guard.ts               # Custom route guard (if auth-gated)
├── $1-page-meta.resolver.ts  # Custom PageMetaResolver for SEO
└── $1.module.ts              # Feature module importing routing module
```

### Naming Conventions
- Routing module class: `PascalCase` + `RoutingModule` suffix (e.g., `LoyaltyRewardsRoutingModule`)
- Guard class: `PascalCase` + `Guard` suffix (e.g., `LoyaltyRewardsGuard`)
- Resolver class: `PascalCase` + `PageMetaResolver` suffix (e.g., `LoyaltyRewardsPageMetaResolver`)
- Feature module class: `PascalCase` + `Module` suffix (e.g., `LoyaltyRewardsModule`)
- Route name: camelCase (e.g., `loyaltyRewards`)
- File names: kebab-case (e.g., `loyalty-rewards-routing.module.ts`)

### What to Generate
1. **Routing module** — route config using `CmsPageGuard`, `PageLayoutComponent`, configurable route via `provideDefaultConfig`, and `RouterModule.forChild()`
2. **Guard** — `CanActivate` returning `Observable<boolean | UrlTree>`, composing with Spartacus `AuthGuard` pattern
3. **Page meta resolver** — extends `PageMetaResolver`, implements `PageTitleResolver` and `PageDescriptionResolver` for SEO
4. **Feature module** — `@NgModule` importing routing module, providing resolver, and registering for lazy loading via `featureModules`

Refer to [examples.md](examples.md) for the full generate output template.
