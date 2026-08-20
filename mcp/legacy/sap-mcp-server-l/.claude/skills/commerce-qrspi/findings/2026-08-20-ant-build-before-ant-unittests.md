---
date: 2026-08-20
ticket: THINK-201
tier: full
stage: 6-implement
applies_to:
  area: build
  ticket_type: feature
kind: project-knowledge
status: promoted
promotion_target: promoted to commands/6_implement.md (verify real Tests-run count; ant build before ant unittests). Config.json verb hardening still PENDING — deferred because config.json on main is mid-rewrite to the rice-shaped profile.
---

## What happened

After adding a new `@UnitTest` class and running `./gradlew yclean ybuild` then
`ant unittests -Dtestclasses.extensions=coremcp`, the ant runner reported
`[testClassesUtil] No test class file 'coremcp/coremcp-testclasses.xml' in class loader` →
`No tests found!` and exited "BUILD SUCCESSFUL in 1 second" (a false green — nothing ran).
`coremcp-testclasses.xml` on disk *did* list the new test, but it wasn't on the ant runner's
classpath. Running `ant build` first (which regenerates/registers testclasses for the ant
runner) fixed it; the tests then ran 5/5.

## Context

- Stage 6, THINK-201, this repo (CCv2, gradle wrapper). Verb `UNIT_TEST` in config.json is the
  ant form (`ant unittests -Dtestclasses.extensions=<ext>`), but the platform was last built via
  gradle (`yclean ybuild`).
- Symptom is a **false green**: exit 0, "BUILD SUCCESSFUL", `No tests found!` — easy to mistake
  for a pass. Watch for "Total time: 1 second" and a `Tests run:` count of 0 for your class.
- The same seam bit the integration run on the prior branch (a bare `ant integrationtests`
  didn't register the new `@IntegrationTest`).

## The fix / the knowledge

When a new test class was added and the platform was built with gradle, run **`ant build`
before `ant unittests`/`ant integrationtests`** so the ant runner's testclasses registration +
classpath include the new class. Always confirm the actual `Tests run:`/`... succeeded` lines for
your class — never trust "BUILD SUCCESSFUL" alone for a test run.

## Why this generalizes

Every QRSPI ticket that adds a test on this repo and verifies via the ant test verbs after a
gradle build hits this. A false-green test run is worse than a red one — it can pass a slice
checkpoint with zero coverage.

## Promotion suggestion

Make the `UNIT_TEST` and `INTEGRATION_TEST` verbs in `working-docs/config.json` robust by
prepending an ant build, e.g. `cd hybris/bin/platform && . ./setantenv.sh && ant build && ant
unittests -Dtestclasses.extensions=<ext>`. Or add a one-line repo CLAUDE.md note. Also worth a
line in `commands/6_implement.md`: verify the `Tests run:` count for the new class, not just
"BUILD SUCCESSFUL".
