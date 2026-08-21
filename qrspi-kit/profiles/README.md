# profiles/ — the flavor library

A profile is a `config.json` in waiting. Installing one copies it to
`working-docs/config.json` in the target project, and from that moment the project owns it.
Profile and config are the same shape, so there is only one schema to learn.

| Profile | Stack | Gate |
|---|---|---|
| `sap-commerce-ant` | SAP Commerce (Hybris) built with raw **ant** — the common on-prem setup | FULL_BUILD + TYPE_SYSTEM_UPDATE + yunitinit + ant unit/integration tests |
| `sap-commerce-gradle` | SAP Commerce CCv2 driven by the **gradle wrapper** | BUILD + TYPE_SYSTEM_UPDATE + ant unit/integration tests |
| `react-storefront` | React 19 + TypeScript + Vite | TYPECHECK + LINT + BUILD (+ e2e where specs exist) |
| `composable-storefront` | Angular + SAP Composable Storefront (Spartacus) | BUILD + LINT + UNIT_TEST (Karma or Jest) |
| `springboot` | Java + Spring Boot, Maven or Gradle | COMPILE + UNIT_TEST + INTEGRATION_TEST |
| `fastapi` | Python + FastAPI, uv (or pip/Poetry) | LINT + TYPECHECK + pytest |

**Picking an SAP Commerce profile:** does the repo have a `gradlew` wrapper with the SAP
y-task plugin? Use `sap-commerce-gradle`. Otherwise — `hybris/bin/platform/build.xml` and a
`setantenv.sh` — use `sap-commerce-ant`. The ant profile is not a stripped-down variant: it
carries the full target set (`clean`/`build`/`all`, `server`, `updatesystem`, `yunitinit`,
`allwebtests`, `hybrisserver.sh` start/stop/debug, `addoninstall`, `extgen`) and gates the
destructive `initialize` behind `MANUAL:`. Note that both profiles run *tests* through ant,
because the gradle passthrough drops `-D` flags.

## Fields

| Field | What it holds |
|---|---|
| `profile` / `profileVersion` | Name and version stamp; the installer copies both into the config |
| `stack` | Detected stack — drives example vocabulary only |
| `workingDir` | Directory build commands run from (`.`, or a subdir like `core-customize`) |
| `protectedPaths` | Paths the workflow must never modify |
| `apiBoundary` | The designated I/O boundary — a path, or the convention in prose |
| `build` | The verb table: `VERB → command`. A command may be `MANUAL: <steps>` |
| `changeTypeVerbs` | `glob → [VERBS]` — which checks a kind of change requires |
| `jira` | `{ mode: mcp \| manual \| none, project }` |
| `researchLayers` | `[{ name, targets }]` — one stage-2 subagent per layer |
| `questionCategories` | Stage-1 categories; `null` means "use the `researchLayers` names" |
| `manualVerificationSurfaces` | Where a human checks this stack — feeds stages 3 and 7 |
| `sliceExample` | What a vertical slice looks like here — feeds stage 4 |
| `verbNamespaces` | Multi-stack repos: verb prefix → command prefix (e.g. `{"FE_": "cd web && "}`) |
| `triggerVocabulary` | Stack jargon rendered into the skill's frontmatter at install |
| `_notes` | Hard-won rules worth carrying with the config |

## Constraints

- Every verb named in `changeTypeVerbs` must exist in `build`. The installer errors otherwise.
- `triggerVocabulary` must be a **single-line JSON string with no embedded quotes** — the
  bash installer extracts it without a JSON parser (`jq` is not guaranteed). The installer
  errors on a missing or multi-line value rather than substituting nothing.
- A verb whose command is `MANUAL: …` makes stages print the steps and wait for the
  developer instead of running anything. Use it for interactive commands (`ant extgen`),
  for steps with no scriptable path (ImpEx via the HAC console), and for anything
  destructive (`ant initialize` wipes the database) — a `MANUAL:` verb can never be run
  unattended by a slice checkpoint.
- `_notes[0]` is the profile's one-line summary: `install.sh list` prints it. Keep it in the
  form "Starter profile for …".
- Every command in `build` runs in a **fresh shell** from `workingDir`. Anything
  environmental (`. ./setantenv.sh`) must be repeated in each verb that needs it — it does
  not carry over.

## Adding a profile

Copy the closest existing one, change what differs, keep every field present (use `null`
rather than deleting). Stack-specific *prose* belongs in `_notes`, never in a stage file —
the stages are shared by every project and must stay stack-neutral.

If a stack needs a mechanic no field can express, that's a change to the canonical stages
in `../skills/qrspi/commands/`. Route it through the findings-promotion loop so every
project gets it at once.
