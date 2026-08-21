# qrspi — the skill

One stack-neutral implementation of the QRSPI workflow: **Q**uestions → **R**esearch →
**S**tructure/Design → **P**lan → **I**mplement, with validation to close. Seven stages,
each in a fresh context window, with developer gates at Design, Structure, and Validate.

Nothing in this directory is stack-specific. Every project detail — build verbs, research
layers, protected paths, Jira mode, what a slice looks like, where a human verifies —
comes from `working-docs/config.json`, written by the kit installer from a profile.

## Layout

```
SKILL.md            the workflow contract: stages, gates, ground rules, config schema
commands/           the 8 stage files; also published to .claude/commands/cq/ as /cq:*
findings-seed/      README + TEMPLATE copied into working-docs/findings/ on install
QUICKREF.md         one-page cheat sheet
WALKTHROUGH.md      a full ticket, start to finish
```

## The contract

- **Stages are generated.** This directory is installed by the QRSPI kit and replaced
  wholesale on update. Never hand-edit a stage here — edit
  `qrspi-kit/skills/qrspi/commands/` in the kit and re-install.
- **Config is yours.** `working-docs/config.json` is committed with your project and is
  the only place project specificity belongs.
- **Findings are yours.** `working-docs/findings/` accumulates what tickets taught; stage 1
  loads the relevant ones, stage 7 writes new ones and proposes promotions.

`.installed-from` in this directory records which profile and kit version produced this
copy.

## Adding support for a new stack

You don't edit this skill. Write a profile: a `config.json`-shaped JSON file naming that
stack's verbs, research layers, protected paths, boundary layer, slice shape, manual
verification surfaces, and trigger vocabulary. Drop it in `qrspi-kit/profiles/` and install
with it. If a stack genuinely needs a mechanic no config field can express, that's a change
to the canonical stages — route it through the findings-promotion loop so every project
gets it.
