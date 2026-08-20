# QRSPI Kit

A drop-in [Claude Code](https://claude.com/claude-code) kit that takes a ticket from
*"here's a Jira issue"* to *"here's a reviewed PR"* through seven governed stages —
**Q**uestion · **R**esearch · **D**esign · **S**tructure · **P**lan · **I**mplement · **V**alidate —
with human gates at Design, Structure, and Validate. Everything project-specific lives in one
config file, so the same workflow serves a SAP Commerce backend or an Angular Composable
Storefront. Config-driven the same way [`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi) works.

## What's in the box

| Set | Skills |
|---|---|
| **`backend/`** — SAP Commerce (Hybris) | `commerce-qrspi` · `sap-commerce` · `sap-best-practices` · `java-best-practices` · `impex` · `sap-commerce-migrate-j21` |
| **`ui/`** — Angular SAP Composable Storefront | `storefront-qrspi` · `spartacus-component` · `-state` · `-occ` · `-routing` · `-styling` · `-forms` · `-i18n` · `-testing` · `-upgrade` |

Each set ships `.claude/skills/`, a `CLAUDE.md`, and `working-docs/` (a `config.json` template +
`profiles/`). One QRSPI workflow per stack — no older RPI variants.

## 60-second quickstart

```bash
# 1. copy the set you need into your repo root
cp -R qrspi-kit/backend/.claude qrspi-kit/backend/CLAUDE.md qrspi-kit/backend/working-docs  /path/to/repo/

# 2. pick the profile → becomes your active config
cd /path/to/repo
cp working-docs/profiles/sap-commerce.json working-docs/config.json     # (or composable-storefront.json)

# 3. publish the /cq commands
.claude/skills/commerce-qrspi/sync-commands.sh

# 4. run it
/cq:0_go YOUR-TICKET
```

Tiers scale the ceremony to the work: `trivial` · `simple` · `full` · `comprehensive`.

## The config (the portability layer)

Stages never hardcode commands — they resolve **verbs** and **research layers** from
`working-docs/config.json`. Ship-ready profiles:
- **`sap-commerce.json`** — verbs default to **gradle**, with a documented **ant** swap; layers for
  extensions / type-system / service / OCC / impex.
- **`composable-storefront.json`** — **Angular Spartacus** verbs (`ng build`/`ng test`/`ng lint`) and
  the CMS → facades → NgRx → OCC research layers.
- **`react-vite.json`** — **React + Vite + TypeScript** storefront over OCC (`npm run build` / `tsc` /
  Playwright e2e), with the `src/services/api.ts` boundary. Pick this or `composable-storefront` per your stack.

Generic starters for other stacks (Spring Boot, FastAPI, plain React), imported from
[`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi) for reference, live in [`profiles/`](profiles/) —
they pair with rice's generic `qrspi` skill, not this kit's specialized ones.

## Learn it

- **[WALKTHROUGH.md](WALKTHROUGH.md)** — one real ticket (surface OOTB product reviews to the MCP
  agent) run through all seven `/cq:*` stages, grounded in real code. Start here.
- **[INSTALL.md](INSTALL.md)** — full install + configure reference, per stack.

## How it stays sharp

The workflow skills self-improve: each ticket can drop a note in `.claude/skills/<skill>/findings/`,
and stage 7 proposes promoting durable lessons into the stage commands, `CLAUDE.md`, or the config.
The kit ships an empty findings ledger so your project's learnings start clean.

**Grounding rule (every stage):** documents state only *verified* facts (with `file:line`); unknowns
are flagged and clarified, never guessed. No editorializing, no padding — comprehensive on what the
work needs. Unverified detail seeds hallucinations.
