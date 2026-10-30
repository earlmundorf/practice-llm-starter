# kits/ — the distributables

Three zips, three jobs. They version independently, so a workflow change doesn't force a
domain-knowledge release and vice versa.

| Zip | What it is | Source of truth |
|---|---|---|
| `qrspi-kit.zip` | The QRSPI **workflow** — one generic skill + profiles + installer | `qrspi-kit/` (canonical; zipped as-is) |
| `backend-skills-kit.zip` | SAP Commerce **domain knowledge** — 6 skills | `mcp/legacy/sap-mcp-server-l/.claude/skills/` |
| `frontend-skills-kit.zip` | Storefront **domain knowledge** — 9 Spartacus + 4 React skills | `sap-llm-template/` (spartacus-*) and `sap-ui-template-react/` (react-*) |

## Build

```bash
./kits/make-kits.sh              # all three
./kits/make-kits.sh backend      # just one: qrspi | backend | frontend
```

Output lands in `kits/dist/` (gitignored — these are build artifacts, not source).

## Ad-hoc zips

`make-skill-zip.sh` bundles any set of skills into one zip, using the same designated-source
precedence as `make-kits.sh`. Use it when the three fixed kits are the wrong shape — a
single skill for one teammate, or a custom mix for one project.

```bash
./kits/make-skill-zip.sh list                                  # every skill and the copy that would be used
./kits/make-skill-zip.sh -o hac-skill-kit sap-hac-access       # one skill
./kits/make-skill-zip.sh -o my-kit impex sap-commerce qrspi    # a custom mix
./kits/make-skill-zip.sh --from sap-llm-template impex         # force a non-default copy
```

`-o` names the zip (default `custom-skills-kit`). The generated `INSTALL.md` records which
source each skill came from and the build date, and adds a `chmod +x` line only for the
skills that actually ship scripts.

## Why the skills kits have no canonical directory

The domain skills live in the projects that use them. `make-kits.sh` assembles each zip from
a **designated source project**, listed in the table above and in the script's header. That
keeps the repo free of copies-that-exist-only-to-be-packaged, which is the duplication that
made the old `sap-commerce-claude-kit.zip` drift from the live skills.

Consequence worth knowing: where two projects carry the same skill, the script picks one.
Known divergences at the time of writing —

- Five of the six backend skills exist in both `sap-mcp-server-l` and `sap-llm-template` and
  **differ slightly**. The script takes `sap-mcp-server-l` (actively maintained, newest
  `sap-commerce-migrate-j21`). The copies have not been reconciled. `sap-hac-access` exists
  only in `sap-mcp-server-l`, so there is nothing to reconcile for it.
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
