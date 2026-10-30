# Backend skills kit — install

SAP Commerce (Hybris) domain-knowledge skills for Claude Code. These are **reference and
review** skills — they carry platform expertise. They are independent of the QRSPI workflow
kit and version on their own clock; install either, or both.

| Skill | Use it for |
|---|---|
| `sap-commerce` | Core dev assistant — type system, ImpEx, FlexibleSearch, Spring, OCC, layered architecture, build/deploy |
| `impex` | ImpEx specialist — syntax, header modes, catalog versioning, ordering; includes lint + HAC-import scripts |
| `sap-hac-access` | HAC automation — run FlexibleSearch, ImpEx imports and Groovy against a live server; data-model investigation |
| `sap-best-practices` | Commerce code review — layer separation, Spring wiring, OCC conventions, performance |
| `java-best-practices` | General Java review (Effective Java / Pragmatic Programmer) |
| `sap-commerce-migrate-j21` | Migration to Java 21 + Spring 6 + Tomcat 10.1 / Jakarta EE 10 (2211-jdk21.x) |

## Install

Unzip anywhere, then copy the skills into your project:

**macOS / Linux / WSL / Git Bash**

```bash
unzip backend-skills-kit.zip
mkdir -p /path/to/your-repo/.claude/skills
cp -R backend-skills-kit/.claude/skills/* /path/to/your-repo/.claude/skills/
```

**Windows PowerShell**

```powershell
Expand-Archive backend-skills-kit.zip -DestinationPath .
New-Item -ItemType Directory -Force -Path C:\path\to\your-repo\.claude\skills | Out-Null
Copy-Item backend-skills-kit\.claude\skills\* C:\path\to\your-repo\.claude\skills\ -Recurse -Force
```

That's all — no configuration. Claude Code discovers them on the next session.

## Notes

- Copying **merges** into `.claude/skills/`: any skills already there are untouched unless
  they share a name, in which case they're replaced. Nothing else in the project is modified.
- `impex` and `sap-hac-access` ship shell scripts. Zip archives don't reliably preserve the
  executable bit, so restore it after copying:
  `chmod +x .claude/skills/impex/scripts/*.sh .claude/skills/sap-hac-access/scripts/*.sh`.
- `sap-hac-access` defaults to local HAC (`https://localhost:9002`, `admin`/`nimda`, Java 21),
  so local work needs no configuration. For `dev`/`qa`/`uat`/`stage`, and for project-specific
  answers, ask it to *"set up the HAC skill"* — it scaffolds a gitignored `hac.env` and a
  `project-knowledge/{sap-commerce,hac}/` overlay at your project root, creating only what's
  missing. You fill in the credentials yourself; the skill never asks for or prints a secret.
- `sap-commerce-migrate-j21` keeps its own `findings/` directory and accumulates knowledge
  across migrations — that content is part of the skill, not per-project state.

## Relationship to the QRSPI kit

QRSPI (`qrspi-kit.zip`) is the *workflow*: seven stages, gates, verbs resolved from
`working-docs/config.json`. These skills are the *domain knowledge* it draws on. Install the
QRSPI kit with the `sap-commerce-ant` or `sap-commerce-gradle` profile and these skills
alongside it, and the workflow's research and review stages have platform expertise to
consult.
