---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: F (surfaced at F.2)
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: new-incident
status: open
related_refs:
  - references/sap-docs/05-tomcat.md (context-path changes)
  - references/known-incidents.md (incident #4 — Spring 6 trailing-slash matching is related)
  - scripts/plan-template.md (Phase F)
promotion_target: references/known-incidents.md (new entry); scripts/plan-template.md Phase F (audit step for project-owned HAC scripts)
---

## What happened

At Phase F.2, the project's checked-in HAC automation scripts (`scripts/hac-groovy.sh`, `scripts/hac-flexquery.sh`, `scripts/hac-impex.sh`) all failed authentication after a successful `yinitialize` + `startServer`. Error from `hac-groovy.sh`:

```
ERROR: Login failed — check HAC_USER/HAC_PASS
```

The scripts worked on the legacy platform (pre-2211-jdk21.x) and hadn't been touched during the migration. The HAC web app was confirmed healthy in the browser (admin/nimda login worked at `https://localhost:9002/`). So the issue was script-side.

Two discovered changes in 2211-jdk21.x's HAC webapp:

1. **HAC's servlet context path moved from `/hac/*` to ROOT `/*`.** All HAC URLs that used to live at `https://host/hac/login`, `https://host/hac/j_spring_security_check`, `https://host/hac/console/scripting/` now live at the equivalent root paths: `https://host/login`, `https://host/j_spring_security_check`, `https://host/console/scripting`. Hitting the old `/hac/*` paths returns 302 redirect chains that don't complete authentication (the POST to `/hac/j_spring_security_check` returns `Location: /hac/login?continue`, an infinite loop).

2. **Spring 6's PathPatternParser rejects trailing slashes** on the HAC console sub-routes. `/console/scripting` returns 200; `/console/scripting/` returns 404. Our scripts were written with trailing slashes (matching the older Spring 5 behavior where `useSuffixPatternMatch` and trailing-slash-matching were defaults). Spring 6 removed both defaults.

Combined effect: the scripts request `/hac/console/scripting/` — wrong prefix AND wrong slash semantics — and get a 404 with no meta CSRF token, which the script interprets as "login failed" (because CSRF extraction from a non-200 page yields empty).

## Context

- The HAC nav bar at `https://localhost:9002/` (root) shows the canonical paths:
  ```
  /tenants, /platform/config, /platform/system, /platform/log4j,
  /console/scripting, /console/flexsearch, /console/impex/import, ...
  ```
  All at root, no `/hac` prefix, no trailing slashes.
- Browser-side HAC works fine because the webapp's own links use correct paths.
- Script-side breakage is silent because the scripts follow redirects (`-L`) and end up on the login page, which has its own `_csrf` but not the "post-authenticated meta CSRF" the script expects — so the script concludes "login failed" even though credential verification actually may have worked.

## The SAP-doc gap (if applicable)

- `sap-docs/05-tomcat.md` (in the skill) doesn't call out the HAC context-path move. Neither does the general update guide.
- Spring 6's strict path matching IS documented — `references/known-incidents.md` incident #4 already covers the trailing-slash 404 in the context of OCC custom POST endpoints — but not specifically for HAC automation scripts.
- The intersection (context-path move + trailing slash) is project-affecting tooling breakage, not a source-code migration issue. Worth a known-incidents entry because the symptom ("login failed, credentials correct") is actively misleading.

## The fix that worked

Two-line fix per script:
1. Remove `/hac/` prefix from all URL paths: `$HAC/hac/login` → `$HAC/login`, `$HAC/hac/j_spring_security_check` → `$HAC/j_spring_security_check`, `$HAC/hac/console/*` → `$HAC/console/*`.
2. Remove trailing slashes on all HAC console sub-paths: `/console/scripting/` → `/console/scripting`, `/console/flexsearch/` → `/console/flexsearch`.

Both `hac-groovy.sh` and `hac-flexquery.sh` patched; `hac-impex.sh` has the same pattern and needs the same patch (our project's Phase F smoke didn't exercise it, so the patch is deferred to when it's first needed — but it's the same mechanical fix).

Verified via `./scripts/hac-groovy.sh "return 2+2"` → `[rollback mode] => 4`. Full Solr indexing + promotion publish run succeeded afterward.

## Why this generalizes

Any SAP Commerce project that has automation wrapping HAC's web UI — either via `curl` (our case) or via Selenium / Playwright for acceptance tests — will hit one or both of these. The context-path move is unambiguous (it's a platform change, not an extension change). The trailing-slash strictness is a Spring 6 migration tax that applies uniformly to every Spring-registered route under the new PathPatternParser.

Projects that call HAC only through the browser won't notice. Projects with CI/CD pipelines that invoke `gradlew groovy -Pfile=...` or `gradlew impex -Pfile=...` (which go through hac-*.sh wrappers) will see their CI break silently with "login failed" messages that look like credential issues.

## Promotion suggestion

1. **`references/known-incidents.md`** — add a new incident:

   > **N. HAC automation scripts fail with "login failed" after 2211-jdk21.x migration**
   > - **Symptom:** Project-owned `hac-*.sh` wrapper scripts (or equivalent Selenium/curl scripts) that call HAC's web UI fail with "login failed" or "CSRF token not found" errors, even though admin/nimda works in the browser.
   > - **Two root causes (both commonly present):**
   >   1. **Context path move:** HAC moved from `/hac/*` to root `/*`. Replace `/hac/login` → `/login`, `/hac/j_spring_security_check` → `/j_spring_security_check`, `/hac/console/*` → `/console/*` in all scripts.
   >   2. **Trailing-slash strictness:** Spring 6 rejects `/console/scripting/` — use `/console/scripting` (no trailing slash). Same for `/console/flexsearch`, `/console/impex/import`.
   > - **Verification:** `curl -sk https://localhost:9002/console/scripting` should return 200 with HTML title `hybris administration console | Scripting`. 404 means one of the two fixes above is still missing.
   > - **Related:** same Spring 6 trailing-slash issue affects custom OCC POST endpoints — see incident #4.

2. **`scripts/plan-template.md` Phase F** — add an audit step:

   ```markdown
   - [ ] Audit project-owned HAC automation scripts (`scripts/hac-*.sh`, Selenium tests, CI steps calling `gradlew groovy/impex/flexquery`):
         `grep -rnE '/hac/(login|j_spring|console)|console/[a-z]+/\"' core-customize/scripts` → expected: zero matches. Any hit is a broken HAC URL pattern. Fix per known-incidents entry N (context-path move + trailing-slash strictness).
   ```

3. **`references/sap-docs/05-tomcat.md`** — add a section "HAC context path change" documenting the move and the trailing-slash implications.

## Downstream follow-ups

- `scripts/hac-impex.sh` needs the same URL patches when the project next uses it (likely Phase F.7 in future projects, not exercised on this one).
- If any CCv2 cloud pipeline calls HAC via scripts, it may already be patched upstream by SAP — but worth checking.
- The CLAUDE.md access-points table in this project shows `http://localhost:9001/hac` — that redirect may actually work in the browser because HAC's 302 chain ends at the correct root URL. Worth re-checking and updating CLAUDE.md if needed: the canonical URL is now `https://localhost:9002/` (root, not `/hac/`).
