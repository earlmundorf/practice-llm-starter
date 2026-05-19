---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: F (surfaced at F.2; should be caught at Phase 0)
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: new-incident
status: open
related_refs:
  - core-customize/hybris/bin/platform/project.properties (line 1061 in 2211-jdk21.9)
  - scripts/plan-template.md (Phase 0)
  - references/known-incidents.md
promotion_target: references/known-incidents.md (new entry); scripts/plan-template.md Phase 0 (prereq); scripts/detect_state.sh (advisory check)
---

## What happened

At Phase F.2, after a successful `yinitialize`, every admin login attempt to HAC returned a 302 redirect back to the login page — classic "bad credentials." Tried `nimda`, `admin`, `administrator`, `Admin1234` — none worked. Tomcat was up cleanly, no SEVERE errors; the issue was purely credential-side.

Cause: in 2211-jdk21.x (verified on 2211-jdk21.9), the platform's default value for `initialpassword.admin` in `hybris/bin/platform/project.properties:1061` is **EMPTY**:

```properties
# Determines default password for given userID (default password is not provided)
#initialpassword.userid=password
initialpassword.admin=
```

Previous Commerce versions shipped with `initialpassword.admin=nimda` as the default. The 2211-jdk21.x hardening removed the baked-in default — if no override is set in `local.properties`, `yinitialize` generates a random password no one knows, and local-dev admin access is locked.

## Context

- Our `dev-config/local.properties` had never set `initialpassword.admin` — the project relied on the old platform default.
- After yinitialize, the admin user existed (Phase F.6 row count showed User=17 including admin) but with an unknown password.
- Fix: explicitly set `initialpassword.admin=nimda` in `dev-config/local.properties`, sync to `hybris/config/local.properties`, re-run yinitialize. One line of config + a 3-minute re-init.
- The platform log lines around password init are completely silent about what the password ended up being:
  ```
  INFO [main] [Initialization] ################ Initialize password for the 'admin' user.
  INFO [main] [Initialization] ################ Password for the 'admin' user has been successfully initialized.
  ```
  No "password set to X", no "generated random password printed to audit log". Opaque by design — which is correct for a production hardening, but makes the dev UX painful when the override is missing.
- CCv2 cloud environments set this via secret manager / Cloud Portal, so they aren't affected. Local dev and CI are where this bites.

## The SAP-doc gap (if applicable)

SAP's general update guide doesn't mention this change. The release notes for 2211-jdk21.x (`sap-docs/13-release-notes-2211-jdk21.md`) don't call it out either. It's visible only by diffing `bin/platform/project.properties` between the legacy and new platform releases — not a realistic thing for a migration project to do routinely.

The skill should:
- Add it as a Phase 0 prereq in `plan-template.md` — "before first yinitialize, ensure `initialpassword.admin` is set explicitly."
- Add an advisory check in `detect_state.sh` — grep `local.properties` for the property; warn if absent.
- Add a known-incidents entry so teams know the symptom signature.

## The fix that worked

Add to `dev-config/local.properties` (and sync to the generated `hybris/config/local.properties`):

```properties
###############################################################################
# Initial admin password (dev only)
###############################################################################
# 2211-jdk21.x hardened the platform default: initialpassword.admin= is now EMPTY
# in platform/project.properties (was "nimda" in earlier releases). Without this
# override, yinitialize generates a random admin password and you lose local-dev
# access via the documented admin/nimda credential pair.
# Do NOT use this in CCv2 cloud — cloud envs rely on secret manager integration.
initialpassword.admin=nimda
```

Re-ran `yinitialize` → clean admin/nimda login worked.

## Why this generalizes

Every SAP Commerce project migrating to 2211-jdk21.x that relied on the platform default for local dev will hit this silently at the first post-migration `yinitialize`. Most projects will set `initialpassword.admin=nimda` somewhere in their local config today, but a meaningful fraction relied on the platform default — ours did.

The fix is trivial (one line of config), but the discovery cost is high because:
1. Login failures don't produce an obvious error message — just 302s to the login page.
2. The console log doesn't print the generated password.
3. The symptom looks identical to "I typed the wrong password" — easy to waste 30 min trying variations.

Setting expectations in the plan-template converts this from a 30-minute discovery to a 5-second config line.

## Promotion suggestion

1. **`references/known-incidents.md`** — add a new incident:

   > **N. `admin` login fails silently after yinitialize on 2211-jdk21.x**
   > - **Symptom:** HAC login POST to `/j_spring_security_check` returns 302 → `/login` (back to login page). `admin/nimda` doesn't work. No obvious error in the Tomcat console log. The admin user exists (confirmed via FlexibleSearch on `User`) but the password is unknown.
   > - **Cause:** the platform's default `initialpassword.admin=` is EMPTY in 2211-jdk21.x (was `nimda` in earlier releases). Without an explicit override, `yinitialize` generates a random password no one knows.
   > - **Fix:** add `initialpassword.admin=nimda` (or your chosen dev credential) to `core-customize/dev-config/local.properties`. Sync to `hybris/config/local.properties`. Re-run `./gradlew yinitialize`. Login works.
   > - **Applies to:** local dev and CI only. CCv2 cloud envs use secret manager and are not affected.

2. **`scripts/plan-template.md`** — add a Phase 0 prereq bullet (Step 0.5 area, before the platform bump / toolchain steps):

   ```markdown
   - [ ] **Before first `yinitialize`**, ensure `initialpassword.admin` is set in `dev-config/local.properties`. 2211-jdk21.x's platform default is EMPTY — missing override means yinitialize generates a random admin password. One-line fix; 30-min gotcha if you forget.
   ```

3. **`scripts/detect_state.sh`** — new advisory in the pre-flight section:

   ```bash
   hdr "Bootstrap prereqs"
   if grep -qE '^[^#]*initialpassword\.admin=' core-customize/dev-config/local.properties 2>/dev/null; then
     report "initialpassword.admin" "set in dev-config/local.properties ✓"
   else
     report "initialpassword.admin" "⚠ NOT SET — 2211-jdk21.x default is empty; yinitialize will generate a random admin password"
   fi
   ```

## Downstream follow-ups

- None. The fix is permanent once added to the project's checked-in `local.properties`.
- Consider adding a note in the project's own `CLAUDE.md` / onboarding docs that new team members should not remove this line — it's load-bearing for local dev, not legacy cruft.
