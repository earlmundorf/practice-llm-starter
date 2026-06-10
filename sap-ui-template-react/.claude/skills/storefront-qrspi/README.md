# storefront-qrspi

QRSPI workflow (Ticket/Questions → Research → Design → Structure → Plan → Implement →
Validate) for storefront projects — the frontend sibling of `commerce-qrspi` in the
`sap-mcp-server-l` backend repo. Same architecture (small stages in fresh contexts,
blind research, two short review gates, tiered ceremony, verb-resolved verification).

**Framework-neutral by design:** the research layers AND the verification verbs live in
`working-docs/config.json`, so this exact skill folder serves a React/Vite storefront
(this repo's config) or an Angular SAP Composable Storefront (copy the folder, seed an
Angular config: `ng build`/`ng test` verbs; CMS-component/feature-module/facade layers).
One skill definition, one config per repo — no per-framework forks to keep in sync.

## Usage

```
/cq:go <TICKET-KEY or description> [trivial|simple|full|comprehensive]
```

One command; Claude recommends a tier from the ticket's scope and you confirm.
Artifacts live in `working-docs/<TICKET-KEY>/` (gitignored except the shared
`working-docs/config.json`, which is committed — it maps verification verbs like
TYPECHECK/LINT/BUILD to this repo's npm scripts).

| Tier | When |
|---|---|
| trivial | Typo, copy change, <3 files, no design choice |
| simple | Bug with known cause, small change in one component/page |
| full | Standard feature — multiple files/areas, real design choices |
| comprehensive | Cross-cutting (routing/auth/cart), risky, or team review wanted |

Safety rails at every tier: verification verbs, the diff-ownership gate before any PR,
all backend calls through `src/services/api.ts`, never touch `node_modules`/`dist`.

Note: `UNIT_TEST` currently resolves to `MANUAL:` because no test runner is configured —
update `working-docs/config.json` when Vitest lands.

Attribution: QRSPI per Horthy/Lavaee (2026); command mechanics adapted from
github.com/matanshavit/qrspi (MIT); Commerce variant authored in Cowork.
