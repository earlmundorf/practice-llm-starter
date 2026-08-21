# kits/ — the distributables

Three zips, three jobs. They version independently, so a workflow change doesn't force a
domain-knowledge release and vice versa.

| Zip | What it is | Source of truth |
|---|---|---|
| `qrspi-kit.zip` | The QRSPI **workflow** — one generic skill + profiles + installer | `qrspi-kit/` (canonical; zipped as-is) |
| `backend-skills-kit.zip` | SAP Commerce **domain knowledge** — 5 skills | `mcp/legacy/sap-mcp-server-l/.claude/skills/` |
| `frontend-skills-kit.zip` | Storefront **domain knowledge** — 9 Spartacus + 4 React skills | `sap-llm-template/` (spartacus-*) and `sap-ui-template-react/` (react-*) |

## Build

```bash
./kits/make-kits.sh              # all three
./kits/make-kits.sh backend      # just one: qrspi | backend | frontend
```

Output lands in `kits/dist/` (gitignored — these are build artifacts, not source).

## Why the skills kits have no canonical directory

The domain skills live in the projects that use them. `make-kits.sh` assembles each zip from
a **designated source project**, listed in the table above and in the script's header. That
keeps the repo free of copies-that-exist-only-to-be-packaged, which is the duplication that
made the old `sap-commerce-claude-kit.zip` drift from the live skills.

Consequence worth knowing: where two projects carry the same skill, the script picks one.
Known divergences at the time of writing —

- All five backend skills exist in both `sap-mcp-server-l` and `sap-llm-template` and
  **differ slightly**. The script takes `sap-mcp-server-l` (actively maintained, newest
  `sap-commerce-migrate-j21`). The copies have not been reconciled.
- `react-ecommerce` exists in both `sap-ui-template-react` and `sap-mcp-ui-l` and **differs**.
  The script takes the template's copy.

Reconciling those is a content decision, not a packaging one. Until it happens, the zips
carry the designated copy and nothing silently wins.

## Installing

Each zip carries its own `INSTALL.md`:

- QRSPI: unzip at the project root, `./qrspi-kit/install.sh list`, then install a profile.
  Windows: `install.ps1`. The installer writes only `.claude/skills/qrspi/`,
  `.claude/commands/cq/`, and `working-docs/` — every other skill in the project is
  left alone.
- Skills kits: unzip and copy `\.claude/skills/*` into the target project. No configuration.
