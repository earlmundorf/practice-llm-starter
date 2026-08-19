# CLAUDE.md — SAP Commerce backend

Root config for [Claude Code](https://claude.com/claude-code) in an **SAP Commerce Cloud (Hybris)** project (CCv2 layout). Teaches Claude how to build, run, test, and extend the platform, and points to the skills that carry the domain expertise.

## Configuration is driven by `working-docs/config.json`

This kit is **config-driven**, the same way `rice-qrspi` works. The QRSPI stages never hardcode build commands — they resolve **verbs** (`BUILD`, `TYPE_SYSTEM_UPDATE`, `UNIT_TEST`, `IMPEX_IMPORT`, `SERVER_RESTART`, …) from `working-docs/config.json`.

**On first setup:** copy the starter profile over the template, then adjust:
```bash
cp working-docs/profiles/sap-commerce.json working-docs/config.json
# then set <ext> to your extension(s); if you build with ant, set buildTool=ant and swap the verbs (documented in the profile)
```
The profile ships with **both build tools**: `gradle` (default, `./gradlew …`) and an `ant` swap (`setantenv.sh` + `ant …`), so it fits CCv2 gradle-wrapper repos **and** raw-ant on-prem repos.

## Critical rules

1. Never modify `gensrc/`, `platform/`, or OOTB modules — override in custom extensions.
2. Use the Spring **alias pattern** (`defaultMyBean` → `myBean`); interfaces + `Default*` impls in `impl/`.
3. OCC **Data/WsDTO** classes are generated from `*-beans.xml` — never hand-write them.
4. Always **`yclean` before `ybuild`** — incremental Java compile is unreliable and leaves stale `.class` on green builds.
5. Run **`yupdatesystem` and all tests with the server stopped** (JMX restart fails; junit tenant shares Solr :8983).
6. **Scope tests to custom extensions**; use the ant form (`-Dtestclasses.extensions=<ext>`), not the gradle passthrough (it drops `-D`).
7. Store secrets in env vars, never in code.

## Skills (`.claude/skills/`)

| Skill | Use it for |
|---|---|
| **`commerce-qrspi`** | The governed 7-stage workflow (Ticket → Research → Design → Structure → Plan → Implement → Validate) with human gates at Design/Structure/Validate. Entry point: `/cq:go <TICKET> [tier]`. |
| **`sap-commerce`** | Core dev assistant — type system, ImpEx, FlexibleSearch, Spring, OCC, layered architecture, build/deploy. |
| **`sap-best-practices`** | Commerce code review — layer separation, Spring wiring, OCC conventions, performance. |
| **`java-best-practices`** | General Java review (Effective Java / Pragmatic Programmer). |
| **`impex`** | ImpEx specialist — syntax, header modes, catalog versioning, ordering; includes lint + HAC-import scripts. |
| **`sap-commerce-migrate-j21`** | Migration to Java 21 + Spring 6 + Tomcat 10.1 / Jakarta EE 10 (2211-jdk21.x). |

**QRSPI grounding rule (every stage):** documents state only *verified* facts (with `file:line`); unknowns are flagged as open questions or `unconfirmed` and clarified, **not** guessed. No editorializing, no tangential padding — comprehensive on what the work needs, and readable. Unverified detail seeds hallucinations.

**Stage commands sync:** the source of truth is `commerce-qrspi/commands/*.md`; run `commerce-qrspi/sync-commands.sh` to publish them into `.claude/commands/cq/` (what `/cq:*` reads).

## Documentation convention

Each feature flow gets a directory under `docs/` with `context.md`, `components.md`, `diagram.md`. Read the flow before working on it; create it before adding a feature — docs before code.
