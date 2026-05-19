---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: A
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: gap
status: promoted
promoted_commit: 779e5e5
related_refs:
  - references/sap-docs/06-build-ant-gradle.md
  - scripts/detect_state.sh
  - scripts/plan-template.md (Phase A.1)
promotion_target: scripts/plan-template.md + scripts/detect_state.sh — reframe the "ant check" around setantenv.sh, not raw PATH
---

## What happened

The current skill treats ant presence as "is system ant on PATH?" — e.g. Phase 0's `no-ant-installed` finding and the `detect_state.sh` ant check both report on system ant. Phase A.1 of the plan says `ant -version; brew install ant if not installed`.

User correction during A.1: that's the wrong frame. **In SAP Commerce, the ant you care about is the platform-bundled one, activated by `source core-customize/hybris/bin/platform/setantenv.sh`.** Not raw system ant. Sourcing sets `ANT_HOME` to `hybris/bin/platform/apache-ant`, sets `ANT_OPTS` (including JDK 21 module exports — `--add-exports java.xml/...`), and puts the hybris classpath in scope. Without it, ant either fails outright or silently misses hybris tasks.

My first A.1 attempt invoked `sh core-customize/hybris/bin/platform/apache-ant/bin/ant -version` directly. That "works" for `-version` but is not what you'd actually use for any real target — no hybris classpath, no JDK 21 module exports. The correct command is:

```bash
cd core-customize/hybris/bin/platform
source ./setantenv.sh
ant -version    # now uses the platform's ant with the right environment
ant <target>    # runs hybris y-targets correctly
```

## Context

- SAP's `06-build-ant-gradle.md` describes Ant version requirements; it assumes the reader knows about setantenv.sh.
- `./gradlew y*` (what this project uses for everything) does NOT need setantenv.sh — the `sap.commerce.build` Gradle plugin handles ant env internally.
- Raw system ant install (e.g. `brew install ant`) is not needed for SAP Commerce work, period. It was suggested in Phase A.1 of the plan-template but is orthogonal to the migration.
- Minor secondary quirk: `apache-ant/bin/ant` can lose its exec bit through `git archive | tar -x`; sourcing setantenv.sh fixes access via Java invocation paths but not if you try to run the shell script directly. In practice this doesn't matter because after sourcing, you just type `ant`.

## The SAP-doc gap (if applicable)

SAP assumes readers know to source setantenv.sh. The skill shouldn't — a fresh reader following the plan-template will `brew install ant` (wasted work), never learn about setantenv.sh, and then be confused when their system ant doesn't find hybris targets. The skill should be the one that says "source setantenv.sh" explicitly.

## The fix that worked

For Phase A.1 verification on this project: `cd core-customize/hybris/bin/platform && source ./setantenv.sh && ant -version` — reports `Apache Ant 1.10.15`, confirms hybris classpath and JDK 21 module exports are wired.

## Why this generalizes

Every SAP Commerce migration hits this. "Verify ant" in the abstract is meaningless on Commerce; the only ant that matters is the platform's bundled one, and it must be activated via setantenv.sh. Telling a fresh reader to `brew install ant` actively leads them away from the right answer.

Also: if the skill is going to drive `./gradlew y*` (which it is — that's how this project's `gradlew bootstrapPlatform`, `yclean yall`, etc. are invoked), it should call out that **sourcing setantenv.sh is NOT needed in that path** — the Gradle plugin handles it. The rule is: source when invoking `ant` directly from a shell; don't bother when invoking via `./gradlew`.

## Promotion suggestion

1. **`scripts/plan-template.md`** Phase A.1 — rewrite the ant-version step:

   > - [ ] `cd core-customize/hybris/bin/platform && source ./setantenv.sh && ant -version` — expect ≥ 1.10.14. The platform ships a bundled ant at `hybris/bin/platform/apache-ant/`; `setantenv.sh` activates it with the correct `ANT_HOME`, JDK 21 module exports, and hybris classpath.
   > - [ ] **Do NOT `brew install ant` or use raw system ant** for SAP Commerce work — it lacks the hybris classpath and JDK 21 module exports. The only ant that matters for Commerce is the platform-bundled one.
   > - [ ] When running `./gradlew y*` targets, sourcing `setantenv.sh` is not needed — the `sap.commerce.build` Gradle plugin handles env setup internally.

2. **`scripts/detect_state.sh`** — reframe the ant check:

   ```bash
   # Old: "ant on PATH" — misleading for Commerce
   # New: check bundled platform ant
   if [[ -x core-customize/hybris/bin/platform/setantenv.sh ]]; then
     bundled_ver=$(sh -c "cd core-customize/hybris/bin/platform && . ./setantenv.sh >/dev/null 2>&1 && ant -version" 2>&1 | head -1)
     report "platform ant" "$bundled_ver (activate with: source core-customize/hybris/bin/platform/setantenv.sh)"
   else
     report "platform ant" "not yet bootstrapped — run ./gradlew bootstrapPlatform first"
   fi
   ```

   Remove the "is system ant on PATH" check entirely, or downgrade to "(optional for non-Commerce ant needs only)".

3. **`references/sap-docs/06-build-ant-gradle.md`** or `references/additional-changes.md` — add one paragraph up top:

   > **How to invoke ant in SAP Commerce:** `cd core-customize/hybris/bin/platform && source ./setantenv.sh`. This sets `ANT_HOME`, `ANT_OPTS` (JDK 21 module exports), and the hybris classpath. Raw system ant (e.g. `brew install ant`) is not a substitute — it lacks the hybris-specific environment. When running `./gradlew y*`, you don't need to source — the Gradle plugin handles it.

4. **Retire or rewrite `findings/2026-04-30-no-ant-installed.md`.** That finding was framed around "ant not on PATH" and proposed adding a PATH check to detect_state.sh. It's now superseded by this finding. Either mark it `status: superseded` and link to this one, or rewrite the "What happened" and "Promotion suggestion" sections to reflect the correct frame.
