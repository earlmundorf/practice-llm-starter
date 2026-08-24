---
name: spartacus-storefront
description: |
  SAP Composable Storefront (Spartacus 6.x, Angular) knowledge base — an explicitly
  chosen path, NOT for this repo's React code. Use ONLY when the user is working on or
  asking about a Spartacus / SAP Composable Storefront / Angular storefront: CMS
  component mapping, NgModules and feature modules, reactive forms, i18n
  (cxTranslate), OCC adapters/converters, CMS-driven routing, NgRx state
  (facade/connector/adapter), SCSS theming, unit testing, and version upgrades.

  Trigger this skill ONLY when the user explicitly mentions: Spartacus, Composable
  Storefront, Angular storefront, NgModule, CmsComponentData, cxTranslate, NgRx,
  OccEndpointsService, CmsPageGuard, or asks to take "the Spartacus path" / "the
  Angular path". Do NOT trigger for this repo's React/Vite code — generic words like
  component, form, routing, or state in a React context belong to react-typescript /
  react-ecommerce. When a dedicated Composable Storefront repo exists, copy this skill
  there.
allowed-tools: [Read, Grep, Glob, Bash(find *), Bash(ng *), Bash(npx ng *)]
---

# SAP Composable Storefront (Spartacus) Knowledge Base

You are an expert Spartacus 6.x / SAP Composable Storefront developer. This skill is
the **Angular path** — chosen explicitly, never applied to this repo's React code. It
bundles nine topic references, each with a guide, patterns, and worked examples.

## Topic references — read before writing code for that topic

| Topic | Read | Covers |
|---|---|---|
| CMS components | [guide](./references/component/guide.md) · [patterns](./references/component/patterns.md) · [examples](./references/component/examples.md) | CMS component mapping, CmsComponentData injection, feature modules, lazy loading, outlets/slots, smart vs presentational splits |
| Forms | [guide](./references/forms/guide.md) · [patterns](./references/forms/patterns.md) · [examples](./references/forms/examples.md) | Reactive forms, CustomFormValidators, cx-form-errors, checkout form customization, accessibility |
| i18n | [guide](./references/i18n/guide.md) · [patterns](./references/i18n/patterns.md) · [examples](./references/i18n/examples.md) | Translation chunks, cxTranslate pipe, key namespacing, ICU pluralization, locale formatting |
| OCC integration | [guide](./references/occ/guide.md) · [patterns](./references/occ/patterns.md) · [examples](./references/occ/examples.md) | Custom OCC adapters, endpoint configuration, converters/normalizers/serializers, interceptors, ConverterService |
| Routing | [guide](./references/routing/guide.md) · [patterns](./references/routing/patterns.md) · [examples](./references/routing/examples.md) | CMS-driven routing, configurable routes, CmsPageGuard, SemanticPathService, cxUrl, custom URL matchers, PageMetaResolver |
| State | [guide](./references/state/guide.md) · [patterns](./references/state/patterns.md) · [examples](./references/state/examples.md) | NgRx via the facade → connector → adapter → normalizer pipeline; actions, reducers, effects, selectors, StateUtils |
| Styling | [guide](./references/styling/guide.md) · [patterns](./references/styling/patterns.md) · [examples](./references/styling/examples.md) | SCSS theming, CSS custom properties, placeholder selector overrides, breakpoints, the cx- design system |
| Testing | [guide](./references/testing/guide.md) · [patterns](./references/testing/patterns.md) · [examples](./references/testing/examples.md) | Unit tests for components/facades/effects/adapters: CmsComponentData mocks, I18nTestingModule, MockStore, provideMockActions |
| Upgrades | [guide](./references/upgrade/guide.md) · [patterns](./references/upgrade/patterns.md) · [examples](./references/upgrade/examples.md) | Stepwise major-version upgrades (2.x → 2211.x), Angular alignment, schematics, common migration fixes |

Each topic directory has three files: `guide.md` (the how and why), `patterns.md`
(canonical code shapes), `examples.md` (worked examples). Read the guide first; pull
patterns/examples as needed.

## How this fits the project family

- This repo (`sap-ui-template-react`) is **React** — its skills are react-typescript,
  react-ecommerce, commerce-storefront, qrspi. This skill exists here as the
  prepared Angular path for the planned SAP Composable Storefront sibling; when that
  repo is created, copy this skill folder there (and seed its `working-docs/config.json`
  with `ng` verbs + Spartacus research layers — the QRSPI kit's `composable-storefront`
  profile already provides exactly that).
- The backend contracts are identical either way: SAP Commerce OCC v2 + the coremcp
  agent/knowledge/visual-search endpoints — see the `commerce-storefront` skill for
  those; Spartacus consumes them through its OCC adapter layer ([guide](./references/occ/guide.md) · [patterns](./references/occ/patterns.md) · [examples](./references/occ/examples.md))
  instead of a hand-rolled api.ts.

## Ground rules when on the Spartacus path

- Spartacus customization is configuration-first: prefer `provideConfig` (component
  mapping, routes, OCC endpoints, i18n) over forking library code; never patch
  `node_modules/@spartacus/*`.
- Override behavior via Angular DI (providers, outlets, component mapping), the same
  spirit as the backend's Spring alias pattern.
- Match Spartacus' own naming and module layout for custom feature libs; keep features
  lazy-loaded.
- Versions matter: this material targets Spartacus 6.x — check the target project's
  version before applying patterns, and use [guide](./references/upgrade/guide.md) · [patterns](./references/upgrade/patterns.md) · [examples](./references/upgrade/examples.md) when they differ.
