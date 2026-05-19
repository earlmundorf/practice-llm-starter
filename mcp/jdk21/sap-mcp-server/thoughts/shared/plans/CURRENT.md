# Current Plans

Last updated: 2026-03-25

## Active Skill Build Plans

### 1. Cowork Skill: `commerce-rpi-cowork`
- **Plan:** [2026-03-25-commerce-rpi-cowork-skill-plan.md](2026-03-25-commerce-rpi-cowork-skill-plan.md)
- **Status:** Approved — ready to build
- **Persona:** Team leads (functional or technical), no terminal
- **Scope:** Research + Plan + Jira/Confluence publishing, handoff to Claude Code for implementation

### 2. Claude Code Skill: `commerce-rpi-code`
- **Plan:** [2026-03-25-commerce-rpi-claude-code-addendum.md](2026-03-25-commerce-rpi-claude-code-addendum.md)
- **Status:** Approved — ready to build (depends on shared references from Cowork skill)
- **Persona:** Developers in Claude Code terminal
- **Scope:** Strict superset of Cowork skill — supports R, R+P, or R+P+I with optional Jira/Confluence

## Shared Architecture

Both skills share `references/`, `assets/`, and `scripts/` directories. The Cowork plan is the primary document defining all shared components. The Claude Code addendum defines only what differs.

## Build Order

1. Build shared references and assets (defined in Cowork plan, Section 9)
2. Build `commerce-rpi-cowork` SKILL.md
3. Build `commerce-rpi-code` SKILL.md (addendum steps CC-1 through CC-6)
4. Test both against mounted CCv2 repo
