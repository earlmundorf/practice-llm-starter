# Installing `sap-commerce-migrate-j21`

A Claude Code skill for migrating SAP Commerce Cloud projects to **Update Release 2211-jdk21.1** (JDK 21 + Spring 6 + Tomcat 10.1 / Jakarta EE 10).

## Where to install

Two options. Pick one.

### Option A — User-level (recommended)

Available in every Claude Code session, regardless of cwd. Best when migrating multiple projects.

```bash
mkdir -p ~/.claude/skills
cp -r path/to/sap-commerce-migrate-j21 ~/.claude/skills/
```

### Option B — Project-level

Travels with the project repo (committed alongside the migration plan/log). Best when you want the skill version to be reproducible per-project.

```bash
mkdir -p <your-sap-commerce-project>/.claude/skills
cp -r path/to/sap-commerce-migrate-j21 <your-sap-commerce-project>/.claude/skills/
```

Per-project shadows user-level — if both exist, the project copy wins for that project.

## Prerequisites on the target machine

Before invoking the skill, the project's host machine needs:

| Requirement | How to install / verify |
|---|---|
| **JDK 21 SapMachine** | `sdk install java 21.0.11-sapmchn` (or whatever 21.x is current). Pin in `core-customize/gradle.properties`: `org.gradle.java.home=/path/to/21.x.y-sapmchn` |
| **Target Commerce Suite ZIP** | `core-customize/dependencies/hybris-commerce-suite-2211-jdk21.<x>.zip` — obtained from SAP Software Download Center (customer login). The skill never bundles this. |
| **Working tree clean on `main`** | `git status` reports clean. Required for the in-place strategy's pre-migration tag + branch step. |
| **Claude Code installed and opened in the project root** | So cwd is the SAP Commerce repo when you talk to the skill. |

The integration extension pack (`hybris-commerce-integrations-*.zip`) is **optional** — leave it out if SAP hasn't shipped a matching `2211-jdk21.x` pack yet (see `findings/2026-04-30-missing-integrations-pack-2211-jdk21.md`).

## Invoking the skill

Just describe the migration. The skill's description triggers on:

- "migrate", "upgrade" + ("JDK 21" or "Spring 6" or "Jakarta EE" or "Tomcat 10")
- "javax to jakarta", "Spring 5 to 6"
- "OpenRewrite" + "Commerce"
- "September 2025 framework update", "2211-jdk21"

Examples that work:

> "Migrate this project to JDK 21 and Spring 6"

> "Upgrade SAP Commerce to the September 2025 framework update"

> "I need to do the javax → jakarta sweep"

The skill loads, reads `findings/`, asks you in-place vs. copy, runs `scripts/detect_state.sh`, and writes a plan to `migration-docs/migration-plan.md`. It stops at the plan and waits for your approval before executing.

## What the skill produces

In the **target project** (not the skill folder):

- `migration-docs/migration-plan.md` — phased plan with checkboxes and SAP-doc links
- `migration-docs/migration-log.md` — per-step execution log
- `migration-docs/supporting-skill-findings.md` — what the skill wants to learn this run

The skill creates `migration-docs/` on first run if absent. It never writes to the project's own `docs/` (if any) — that belongs to the project.

## Updating the skill

The skill is designed to get sharper every project. After a migration:

1. **Review findings.** Anything in `findings/YYYY-MM-DD-*.md` that wasn't in the references is a candidate for promotion.
2. **Promote useful findings** into `references/` (typically `known-incidents.md` or one of the `sap-docs/` files). Mark the finding `status: promoted` in its frontmatter.
3. **Update validation history** in `SKILL.md` frontmatter with the new `validation_history` entry.
4. **Refresh SAP doc mirrors** if SAP updated them: `bash scripts/fetch_sap_docs.sh`. Edit `scripts/upgrade_resources.md` first if new pages need mirroring.
5. **Update the source `.docx`** at `references/upstream/SAPCommerceUpgradeJDK21Changes.docx` if SAP publishes a new framework-update changelog. Re-derive `additional-changes.md` and the relevant parts of `00-overview.md`.

## Scope reminder

This skill covers the migration from Java 17/21 + Spring 5 to **2211-jdk21.1** (or any 2211-jdk21.x release). Out of scope:

- General SAP Commerce development → use the `sap-commerce` skill
- ImpEx work → use the `impex` skill
- Code review against best practices → use `sap-best-practices` or `java-best-practices`
- Migrations to non-Commerce Spring 6 codebases → not what this skill is for
