# Frontend skills kit — install

Storefront domain-knowledge skills for Claude Code, covering both storefront families.
Independent of the QRSPI workflow kit — install either, or both.

**Angular SAP Composable Storefront (Spartacus)**

| Skill | Use it for |
|---|---|
| `spartacus-component` | CMS components, feature modules, component mapping, outlets/slots |
| `spartacus-state` | NgRx + the facade/connector/adapter/normalizer pipeline |
| `spartacus-occ` | Custom OCC adapters, endpoints, converters, normalizers |
| `spartacus-routing` | CMS-driven routing, guards, `SemanticPathService`, URL matchers |
| `spartacus-styling` | SCSS theming, CSS custom properties, breakpoints |
| `spartacus-forms` | Reactive forms, `CustomFormValidators`, `cx-form-errors` |
| `spartacus-i18n` | Translation chunks, `cxTranslate`, ICU pluralization |
| `spartacus-testing` | Unit tests — `CmsComponentData` mocks, `I18nTestingModule`, `MockStore` |
| `spartacus-upgrade` | Step-wise Spartacus/Angular version upgrades |

**React storefront**

| Skill | Use it for |
|---|---|
| `react-ecommerce` | Code review against React storefront conventions |
| `react-typescript` | React + TypeScript patterns |
| `commerce-storefront` | Commerce storefront concerns in a React app |
| `spartacus-storefront` | Spartacus concepts as they map onto a React storefront |

Install the whole kit and ignore the family you don't use, or copy only the skills you want —
they're independent directories.

## Install

**macOS / Linux / WSL / Git Bash**

```bash
unzip frontend-skills-kit.zip
mkdir -p /path/to/your-repo/.claude/skills
cp -R frontend-skills-kit/.claude/skills/* /path/to/your-repo/.claude/skills/
```

**Windows PowerShell**

```powershell
Expand-Archive frontend-skills-kit.zip -DestinationPath .
New-Item -ItemType Directory -Force -Path C:\path\to\your-repo\.claude\skills | Out-Null
Copy-Item frontend-skills-kit\.claude\skills\* C:\path\to\your-repo\.claude\skills\ -Recurse -Force
```

No configuration. Claude Code discovers them on the next session.

## Notes

- Copying **merges** into `.claude/skills/`: existing skills are untouched unless they share
  a name, in which case they're replaced. Nothing else in the project is modified.
- Only want Angular? Copy `spartacus-*` but not the React four (note `spartacus-storefront`
  is a React-side skill despite the name). Only want React? Copy the four React skills.

## Relationship to the QRSPI kit

QRSPI (`qrspi-kit.zip`) is the *workflow*: seven stages, gates, verbs resolved from
`working-docs/config.json`. These skills are the *domain knowledge* it draws on. Pair the
`composable-storefront` profile with the `spartacus-*` skills, or the `react-storefront`
profile with the React skills. An example project `CLAUDE.md` for an Angular Spartacus app
ships in the QRSPI kit under `reference/`.
