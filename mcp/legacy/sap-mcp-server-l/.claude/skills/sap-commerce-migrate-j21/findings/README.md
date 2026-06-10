# findings/ — self-improvement log

This directory is where the skill writes what it didn't already know. The goal is that every project the skill runs on leaves it sharper than it started.

## What belongs here

- **Gaps:** references were silent on something the migration actually hit.
- **Surprises:** a step behaved differently than the SAP docs described.
- **New symptoms:** errors that aren't in `known-incidents.md` yet.
- **Practical fixes:** the exact code/config change that resolved something, beyond what the SAP page said.
- **Confirmations:** rare — only when a non-obvious decision (e.g., a judgement call between two SAP-sanctioned options) turned out to be right and is worth recording for next time.

## What does NOT belong here

- Routine progress / status notes (those go in the migration plan checklist).
- Duplicates of SAP content that's already mirrored in `references/sap-docs/`.
- Project-specific secrets or data.

## File naming

One finding per file. Format: `YYYY-MM-DD-{slug}.md`. Use `TEMPLATE.md` as the starting point.

## Promotion

At the end of a migration run, the user and skill review findings together. Items that generalize to other projects get "promoted" — their content is merged into the matching `references/` file (overview, decision tree, known incidents, or a new sap-docs supplement). Once promoted, the finding's file can be removed or marked with `status: promoted` in its frontmatter.

Unpromoted findings are still loaded by the skill on the NEXT run — so even pre-promotion, they improve future behavior.

## Loading order (by the skill)

At the start of every plan phase, the skill:
1. Lists `findings/*.md` (excluding TEMPLATE.md and README.md).
2. Reads each finding's frontmatter (`applies_to` + `status`).
3. Includes any finding whose `applies_to` matches the current detected state, with higher priority than the condensed overview but lower priority than authoritative `sap-docs/` mirrors.

This is documented in SKILL.md; the mechanism works as long as findings use the template.
