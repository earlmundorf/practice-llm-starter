---
date: 2026-05-01
project: SAP-JDK21-Migration (testbed re-run)
phase: 0-prep
applies_to:
  java_from: any
  spring_from: any
  commerce_from: any
  scenario: B / C / D / E (any scenario where DB persistence is in play)
kind: gap
status: unpromoted
related_refs:
  - references/phase-guide.md (Phase 0-prep Step 0.0d)
  - scripts/detect_state.sh
promotion_target: scripts/detect_state.sh (add DB-port probe) + references/phase-guide.md (expand 0.0d)
---

## What happened

Phase 0 ran cleanly through bootstrapPlatform. Phase F (`./gradlew yclean yall yinitialize`) cannot proceed because MySQL is not running locally — port 3306 connection refused. This was discovered only when I tried to invoke Phase F, AFTER 38 seconds of platform bootstrap and full extraction had already succeeded.

The skill should catch DB-unavailability earlier — ideally in Phase 0-prep Step 0.0d (sanity) — so the user knows to start the DB before any heavy work.

## Context

- Project: testbed at `/Users/emundorf/development/mundo/cap-gemini/projects/SAP-JDK21-Migration`
- Persistence intake answer (4.1): Scenario B (dev MySQL via Docker per `docs/data.md`)
- Phase 0 outputs: bin/platform/, bin/modules/, hybris/config/ all extracted and configured
- Block surfaced at: about to invoke `./gradlew yclean yall yinitialize` for Phase F
- Diagnostic that revealed it: `nc -zv localhost 3306` → "Connection refused"

## The skill-doc gap

`detect_state.sh` doesn't check whether the DB the project needs is reachable. It doesn't check Solr either. Both are required for Phase F to succeed. Phase 0-prep Step 0.0d is described as "Build sanity check" but only runs `./gradlew tasks` — that doesn't exercise DB or Solr.

There's no doc instruction telling the user "start your DB before Phase F" — it's implicit and discovered the hard way.

## The fix that worked (preventive guidance for next run)

Two complementary changes:

### 1. Extend `detect_state.sh` with a connectivity probe

Add a section that, if `core-customize/hybris/config/local.properties` exists (post-bootstrap) or `core-customize/dev-config/local.properties` (pre-bootstrap), parses `db.url=jdbc:mysql://<host>:<port>/...` and probes the host:port. Non-fatal report — just surface "DB reachable: yes/no". Equivalent for Solr (`solr.url`).

Sketch:
```bash
hdr "External services (informational)"
LP="$CC_DIR/dev-config/local.properties"
[[ -f "$LP" ]] || LP="$CC_DIR/hybris/config/local.properties"
if [[ -f "$LP" ]]; then
  db_url=$(grep -E '^db\.url=' "$LP" | head -1 | cut -d= -f2-)
  if [[ "$db_url" =~ jdbc:mysql://([^:/]+):([0-9]+) ]]; then
    db_host="${BASH_REMATCH[1]}"
    db_port="${BASH_REMATCH[2]}"
    if nc -z "$db_host" "$db_port" 2>/dev/null; then
      report "db reachable" "yes ($db_host:$db_port)"
    else
      report "⚠ db reachable" "NO ($db_host:$db_port) — start the DB before Phase F"
    fi
  fi
fi
```

### 2. Expand Phase 0-prep Step 0.0d in `phase-guide.md`

Currently 0.0d is `./gradlew tasks` only. Add bullet:
```
- [ ] Confirm external services reachable per intake 4.1:
  - Scenario A (HSQLDB): N/A — embedded.
  - Scenario B/C/D: DB host:port probe must succeed (`nc -z $DB_HOST $DB_PORT`).
  - Solr (any scenario except A): host:port probe must succeed.
- [ ] If any service is down, stop here. Phase F will fail at yinitialize/index time.
  This is a per-environment prerequisite the migration won't fix; bring services
  up first.
```

## Why this generalizes

Every project the skill runs against will need DB and (usually) Solr available before Phase F. Pre-flighting the connection is a 5-line check that saves 38+ seconds of bootstrap + the user's confusion about why yinitialize errored. The check is universally applicable: HSQLDB skips it, every other scenario needs it.

The "external services reachable" check is also a natural place to surface the docker invocation hint for projects using Docker MySQL/Solr — point users at their project's `docs/data.md` or equivalent for how to start the services.

## Promotion suggestion

When promoting:

1. Edit `scripts/detect_state.sh` to add the connectivity probe section (above sketch, generalized to also handle Solr URL parsing).
2. Edit `references/phase-guide.md` Phase 0-prep Step 0.0d to expand from "build sanity" to "build sanity + external services pre-flight." Document the scenario-conditional behavior.
3. Edit `references/intake-template.md` if intake should also capture the DB host:port if non-default.
4. Cross-link to this finding from `references/known-incidents.md` — though it's not really an incident, more a workflow gap.
5. Mark this finding `status: promoted`.

After promotion, the next time the skill runs, the user gets "⚠ db reachable: NO" in the detect_state output BEFORE any bootstrap work happens. The plan generation can then surface a "start MySQL/Solr first" message in the plan.
