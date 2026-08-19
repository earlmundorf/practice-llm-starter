# CLAUDE.md — Angular SAP Composable Storefront

Root config for [Claude Code](https://claude.com/claude-code) in an **Angular SAP Composable Storefront (Spartacus)** project. Teaches Claude how to build, test, and extend the storefront, and points to the skills that carry the domain expertise.

## Configuration is driven by `working-docs/config.json`

This kit is **config-driven**, the same way `rice-qrspi` works. The QRSPI stages never hardcode commands — they resolve **verbs** (`INSTALL`, `BUILD`, `LINT`, `UNIT_TEST`, `DEV_SERVER`, …) and **research layers** from `working-docs/config.json`.

**On first setup:** copy the starter profile over the template, then adjust:
```bash
cp working-docs/profiles/composable-storefront.json working-docs/config.json
# set Angular/Spartacus versions; if the app uses Jest, set UNIT_TEST to 'npx jest'
```
The profile targets Angular Composable Storefront: `ng build` / `ng test` (Karma default) / `ng lint`, with research layers for the Spartacus pipeline (CMS components → facades/connectors → NgRx state → OCC config → build).

## Critical rules

1. Outbound I/O goes through the **OCC layer** — custom adapters/connectors with converters/normalizers and `OccEndpointsService`; **never inline `HttpClient`** in components or feature services.
2. **Feature modules + lazy loading**; map CMS components via `CmsComponentData`; split smart/presentational components.
3. i18n via **`cxTranslate`** + translation chunks; SCSS via **placeholder-selector overrides** and Spartacus tokens.
4. Never edit `node_modules/`, `dist/`, or `.angular/`; never commit `.env`.
5. Handle **loading and error states** on every async operation.

## Skills (`.claude/skills/`)

| Skill | Use it for |
|---|---|
| **`storefront-qrspi`** | The governed 7-stage workflow (Ticket → Research → Design → Structure → Plan → Implement → Validate), framework-neutral via `config.json`. Entry point: `/cq:go <TICKET> [tier]`. |
| **`spartacus-component`** | CMS components, feature modules, component mapping, outlets/slots. |
| **`spartacus-state`** | NgRx + the facade/connector/adapter/normalizer pipeline. |
| **`spartacus-occ`** | Custom OCC adapters, endpoints, converters, normalizers. |
| **`spartacus-routing`** | CMS-driven routing, guards, `SemanticPathService`, URL matchers. |
| **`spartacus-styling`** | SCSS theming, CSS custom properties, breakpoints. |
| **`spartacus-forms`** | Reactive forms, `CustomFormValidators`, `cx-form-errors`. |
| **`spartacus-i18n`** | Translation chunks, `cxTranslate`, ICU pluralization. |
| **`spartacus-testing`** | Unit tests — `CmsComponentData` mocks, `I18nTestingModule`, `MockStore`. |
| **`spartacus-upgrade`** | Step-wise Spartacus/Angular version upgrades. |

**QRSPI grounding rule (every stage):** documents state only *verified* facts (with `file:line`); unknowns are flagged as open questions or `unconfirmed` and clarified, **not** guessed. No editorializing, no tangential padding — comprehensive on what the work needs, and readable. Unverified detail seeds hallucinations.

**Stage commands sync:** the source of truth is `storefront-qrspi/commands/*.md`; run `storefront-qrspi/sync-commands.sh` to publish them into `.claude/commands/cq/` (what `/cq:*` reads).

## Documentation convention

Each feature flow gets a directory under `docs/` with `context.md`, `components.md`, `diagram.md`. Read the flow before working on it; create it before adding a feature — docs before code.
