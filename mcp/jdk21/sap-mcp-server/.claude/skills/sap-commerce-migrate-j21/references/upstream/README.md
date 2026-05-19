# upstream/ — bundled source materials

Vendor-supplied reference material the skill's curated content (`additional-changes.md`, `00-overview.md`, `sap-docs/`) was derived from or that supplements them. **Authoritative for source-of-truth lookups; not loaded by default.**

## What's here

| File | Source | When to consult |
|---|---|---|
| `SAPCommerceUpgradeJDK21Changes.docx` | SAP framework-update changelog (September 2025, 2211-jdk21.1) | When `additional-changes.md` or `00-overview.md` seems incomplete or contradictory; when verifying SAP's exact wording on a behavior |
| `SPRING_UPGRADES_60.md` | Spring Framework 6.0 upstream release notes (spring.io) | Spring 6.0 baseline behavior, removed APIs, Joda-Time removal, EhCache removal |
| `SPRING_UPGRADES_61.md` | Spring Framework 6.1 upstream release notes | Spring 6.1 incremental changes |
| `SPRING_UPGRADES_62.md` | Spring Framework 6.2 upstream release notes (current target) | PathPatternParser nuances, deprecation removals, behavior changes the SAP digest doesn't carry |

## Authority

Treat as **peer-authoritative** with `sap-docs/` and `additional-changes.md`. Use the skill's normal hierarchy when there's conflict (`decision-tree.md` "When two references disagree"):

1. `sap-docs/` (SAP Help mirrors) — if it speaks on the topic, trust it.
2. `additional-changes.md` — if it covers the topic, trust it.
3. `upstream/` — if the two above are silent, this is the next source. The `.docx` is SAP-authoritative; the `SPRING_UPGRADES_*.md` files are Spring-team-authoritative for Spring-internal questions.

`upstream/` should NEVER be cited as a SAP-specific position when `sap-docs/` covers the same question — SAP's mirror reflects how SAP packaged Spring 6 specifically, which sometimes diverges from upstream defaults.

## Loading

Not auto-loaded by SKILL.md. Read on-demand when:

- A migration step's behavior isn't documented in `sap-docs/` or `additional-changes.md`.
- A finding raises "did SAP actually say this?" and you want to check the verbatim source.
- You're verifying the digest in `additional-changes.md` against the source `.docx`.

## Refreshing

The `.docx` is updated by SAP when they publish a new framework-update release. When that happens:
1. Replace the `.docx` here with the new version.
2. Re-derive the relevant sections of `additional-changes.md` and `00-overview.md` from the updated source.
3. Capture deltas as findings under `findings/YYYY-MM-DD-changelog-update.md`.

The Spring upgrade notes (`SPRING_UPGRADES_*.md`) are static once captured — Spring releases new minor versions on its own cadence, not SAP's.
