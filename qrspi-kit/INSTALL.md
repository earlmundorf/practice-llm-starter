# QRSPI Kit — Install

Drop-in QRSPI workflow + SAP Commerce / Angular Composable Storefront skills for Claude Code, config-driven the same way [`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi) works.

Two independent sets — install whichever the target project needs (or both):

```
qrspi-kit/
├── backend/   → an SAP Commerce (Hybris) CCv2 or on-prem backend
└── ui/        → an Angular SAP Composable Storefront (Spartacus)
```

Each set contains: `.claude/skills/` (the skills), `CLAUDE.md` (project guidance), and `working-docs/` (the `config.json` template + `profiles/`).

## Install (per target project)

From the kit, pick the set and copy its three pieces into your project root:

```bash
# --- SAP Commerce backend ---
cp -R qrspi-kit/backend/.claude       /path/to/your-commerce-repo/
cp    qrspi-kit/backend/CLAUDE.md      /path/to/your-commerce-repo/     # or merge into an existing CLAUDE.md
cp -R qrspi-kit/backend/working-docs   /path/to/your-commerce-repo/

# --- Angular Composable Storefront ---
cp -R qrspi-kit/ui/.claude             /path/to/your-storefront-repo/
cp    qrspi-kit/ui/CLAUDE.md           /path/to/your-storefront-repo/
cp -R qrspi-kit/ui/working-docs        /path/to/your-storefront-repo/
```

> If the project already has a `CLAUDE.md`, **merge** — don't overwrite. Keep the project's own conventions; add the "config-driven" + skills sections.

## Configure (once per project)

1. **Pick the profile** — copy the starter over the template:
   ```bash
   # backend
   cp working-docs/profiles/sap-commerce.json working-docs/config.json
   # storefront
   cp working-docs/profiles/composable-storefront.json working-docs/config.json   # Angular Spartacus
   # …or, for a React/Vite storefront:
   # cp working-docs/profiles/react-vite.json working-docs/config.json
   ```
2. **Adjust `working-docs/config.json`:**
   - Backend: set `<ext>` to your extension(s). Building with **ant** instead of gradle? Set `buildTool: "ant"` and swap the verbs per the `_notes` in the profile.
   - Storefront: pick the matching profile — **`composable-storefront`** (Angular Spartacus) or **`react-vite`** (React/Vite) — and confirm versions. React's gate is TYPECHECK + LINT + BUILD + Playwright e2e (no unit runner by default); Angular uses `ng test` (Karma) — set `UNIT_TEST` to `npx jest` if the app uses Jest.
   - Both: set `jira.mode` — `mcp` (Atlassian connector available), `manual` (paste tickets in), or `none`.
3. **Publish the `/cq:*` commands** (source of truth is the skill's `commands/`):
   ```bash
   .claude/skills/commerce-qrspi/sync-commands.sh      # backend
   .claude/skills/storefront-qrspi/sync-commands.sh    # storefront
   ```

## Use

```
/cq:go YOUR-TICKET            # recommends a tier, then runs the workflow
```
Tiers: `trivial` (fix + verify) · `simple` (brief → implement → validate) · `full` (all stages, gates at Design/Structure/Validate) · `comprehensive` (full + worktree + mandatory tests + team review).

## How it stays sharp

The workflow skills self-improve: each ticket can write a note to `.claude/skills/<skill>/findings/`, and stage 7 proposes promoting durable lessons into the stage commands, `CLAUDE.md`, or the config. The kit ships with an **empty findings ledger** (README + TEMPLATE only) so your project's learnings start clean.

## What's in each set

**backend** — skills: `commerce-qrspi`, `sap-commerce`, `sap-best-practices`, `java-best-practices`, `impex`, `sap-commerce-migrate-j21`.
**ui** — skills: `storefront-qrspi`, `spartacus-component`, `-state`, `-occ`, `-routing`, `-styling`, `-forms`, `-i18n`, `-testing`, `-upgrade`.

Single QRSPI workflow per stack (`commerce-qrspi` / `storefront-qrspi`) — the older RPI variants are intentionally not included.
