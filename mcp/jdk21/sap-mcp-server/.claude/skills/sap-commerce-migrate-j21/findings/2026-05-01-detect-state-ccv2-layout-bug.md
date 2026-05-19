---
date: 2026-05-01
project: SAP-JDK21-Migration (testbed re-run)
phase: 0-prep / detect_state
applies_to:
  java_from: any
  spring_from: any
  commerce_from: any
  layout: CCv2 (any project where SAP files are under core-customize/)
kind: new-incident
status: unpromoted
related_refs:
  - scripts/detect_state.sh
promotion_target: scripts/detect_state.sh (already fixed 2026-05-01) + a one-liner in 00-overview.md noting CCv2 layout assumption
---

## What happened

`detect_state.sh` failed silently with exit code 1 against a real CCv2 project on first invocation post-v0.5.0. The Java/toolchain section returned no values; the Spring version section terminated the script before reaching subsequent sections.

Two distinct bugs in the script:

1. **CCv2 layout assumption was missing.** The script looked for `gradle.properties`, `manifest.json`, `build.gradle`, `localextensions.xml`, etc. at `$PROJECT/$f`, but in any CCv2 (SAP Commerce Cloud v2) project these live under `$PROJECT/core-customize/`. The script's `[[ -f "$PROJECT/$f" ]]` checks all returned false; the loop iterated and produced nothing.

2. **`while read` + `set -e` interaction.** In the Spring version section, an empty pipe (because the for-loop above produced no output due to bug #1) reached `read -r l`, which returned 1 on EOF. Combined with `set -euo pipefail`, this terminated the script. The trace showed:
   ```
   + IFS=
   + read -r l
   ```
   ...and then exited.

## Context

- Skill version: 0.5.0 (intake template + scenario-conditional safety net)
- Run command: `bash scripts/detect_state.sh /Users/emundorf/development/mundo/cap-gemini/projects/SAP-JDK21-Migration`
- Project layout: standard CCv2 — manifest.json + gradle.properties + build.gradle + dependencies/ all under `core-customize/`
- This is the layout EVERY real SAP Commerce Cloud v2 project uses. The bug means the script never worked correctly against any real Commerce project. The 2026-04-30 validation must have either run with files in unusual places OR the script's silent failure was papered over with manual entries — either way, it wasn't caught.

## The SAP-doc gap (if applicable)

Not a SAP doc issue; pure script bug.

## The fix that worked

Rewrote `scripts/detect_state.sh` (2026-05-01):

1. **CCv2 root resolution at top of script:**
   ```bash
   CC_DIR="$PROJECT/core-customize"
   [[ -d "$CC_DIR" ]] || CC_DIR="$PROJECT"   # graceful fallback for non-CCv2
   ```
   All SAP Commerce file lookups (gradle.properties, manifest.json, build.gradle, dev-config/localextensions.xml, dependencies/, hybris/bin/...) now use `$CC_DIR/...`.

2. **Toolchain hint files stay at project root** (`.sdkmanrc`, `.tool-versions`) — those are user-level toolchain configs, not CCv2 files.

3. **Custom-extension scans scoped to `core-customize/hybris/bin/custom/`** — previously OAuth and javax scans hit the entire PROJECT, including the skill itself, docs/, etc. Inflated counts and false positives.

4. **`set -euo pipefail` → `set -uo pipefail`** (no `-e`). detect_state should never silently fail; it reports what it can find and continues.

5. **Replaced `for ... | while read` pattern with `done < <(...)` redirection** in Spring version section, which doesn't suffer from the empty-pipe-read-EOF problem.

6. **Added `Custom extensions` section** as a peer to existing reports — counts declared extensions in localextensions.xml AND lists custom dirs under `bin/custom/`. Useful intake input.

7. **Added legacy `oauth2` extension declaration check** — surfaces known-incidents #0 directly in the report.

After fix, full clean run against the testbed produced the expected output: target Commerce version, JDK 21 pinned, custom extensions enumerated, no blockers detected.

## Why this generalizes

CCv2 is the canonical SAP Commerce Cloud layout. Any project using `manifest.json`-driven cloud builds will have files under `core-customize/`. The bug would have hit every customer project the skill is dropped into. Fix is universal.

The `while read` + `set -e` interaction is a generic bash gotcha. Worth remembering for any future scripts in this skill: prefer process substitution (`done < <(cmd)`) over pipe-into-while when `set -e` is active, because the read-on-EOF return code is interpreted as command failure.

## Promotion suggestion

- `detect_state.sh` is already fixed in-tree. No reference doc change needed.
- Add a one-line note to `references/00-overview.md` (Toolchain compatibility section): "Skill assumes CCv2 layout — SAP files under `core-customize/`. Non-CCv2 projects fall back to PROJECT root with degraded detection."
- `findings/README.md` could mention this as a worked example: "Bugs in skill-internal tooling are valid finding subjects, not just bugs in SAP's docs."

After promotion, mark this finding `status: promoted`.
