# QRSPI — going generic (roadmap)

**Goal:** collapse the two hand-authored, drifting QRSPI skills into **one stack-neutral
`qrspi` skill** whose personality lives entirely in `working-docs/config.json`. Same idea as
[rice-qrspi](https://github.com/earlmundorf/rice-qrspi): the stage commands hardcode nothing;
each project supplies a **profile** (a `config.json`) and the same skill serves any stack.

This is the **next task**, done on `main`. The `config.json` here has already been moved to the
rice-shaped profile schema (commit that introduced `profile`/`protectedPaths`/`apiBoundary`/
`researchLayers`/verb table) — the config half is in place; the skill half remains.

## Where we are

| | Backend (`sap-mcp-server-l`) | UI (`sap-mcp-ui-l`) |
|---|---|---|
| Skill | `commerce-qrspi` (stack-specialized) | `storefront-qrspi` (stack-specialized) |
| Config | `working-docs/config.json` — **already rice-shaped** ✅ | its own `config.json` |
| Problem | 8 command files + SKILL.md hardcode backend literals (gensrc/OOTB/items.xml/gradlew) | same, with frontend literals (node_modules/components/npm) |

Both skills share the 7-stage skeleton but every stage command differs, so they drift and both
expose colliding `/cq:*`.

## Target model (from rice-qrspi)

- **One** `.claude/skills/qrspi/` — commands reference config **fields**, never literals:
  `build` (verb table), `researchLayers`, `protectedPaths`, `apiBoundary`, `changeTypeVerbs`,
  `jira`, `_notes`.
- **Profiles** in `working-docs/profiles/` (e.g. `sap-commerce.json`, `storefront.json`); copy
  the closest to `config.json` and adjust.
- Proven in rice-qrspi on both a React and a FastAPI example with the same skill.

## Plan (steps)

1. Bring the generic `qrspi` skill into the repo (from rice-qrspi upstream, or the local
   `qrspi-kit/`), replacing `commerce-qrspi`; retire `storefront-qrspi` on the UI side.
2. Ensure the generic stage commands reference config fields only — port across any backend
   specifics that are still prose (e.g. `TYPE_SYSTEM_UPDATE`/`IMPEX_IMPORT` are already verbs;
   ground rules use `protectedPaths`/`apiBoundary`).
3. Keep `sap-commerce.json` as the backend profile; add a `storefront.json` profile for the UI.
   Each project's `config.json` is seeded from its profile.
4. **Distribution decision (open):** one copy at user scope `~/.claude/skills/qrspi/` (zero
   duplication) vs kit-as-source synced into each repo (versioned per-repo). Decide during this task.
5. Retire `commerce-qrspi` + `storefront-qrspi`; unify on `/cq:*` from the single skill (removes
   the name collision).
6. Re-test a full `/cq:go` run on each side; sync `/cq` commands (`sync-commands.sh`).

## Guardrails

- Writing standard applies (thorough but concise, human- + LLM-readable, complete, never
  fabricated).
- Config is versioned in git; set up once per repo.
- Don't regress the THINK-201 work (feature lives on branch `jm-think-201a`).
