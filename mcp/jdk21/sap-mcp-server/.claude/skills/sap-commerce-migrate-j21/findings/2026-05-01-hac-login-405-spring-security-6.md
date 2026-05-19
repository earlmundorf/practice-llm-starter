---
date: 2026-05-01
project: sap-mcp-server-g
phase: F (surfaces post-yinitialize)
applies_to:
  java_from: any
  spring_from: 5.x
  commerce_from: any
  target: 2211-jdk21.x
kind: false-alarm-resolved
status: closed-not-an-incident
related_refs:
  - findings/2026-04-30-hac-context-path-root.md (the actual root cause — already promoted)
  - references/known-incidents.md (incident #4 mentions Spring 6 trailing-slash strictness)
promotion_target: NONE — superseded by hac-context-path-root finding which already covers the actual cause
---

## What I initially thought

That HAC web-form login was returning 405 on POST `/j_spring_security_check` due to a Spring Security 6 / `request-matcher="mvc"` interaction in `hac/web/webroot/WEB-INF/config/spring-security-config.xml`. Wrote up the hypothesis with a long rationale about Spring MVC's PathPatternParser and matcher semantics.

I was wrong. The user pushed back: "we shouldn't have a bug in platform code." Correct call.

## What was actually happening

Two artifacts piled up:

1. **Stale curl cookies + CSRF** across server restarts. I had `/tmp/hac.cookies` and `/tmp/hac.html` cached from a previous server lifecycle. After Tomcat restarted, the JSESSIONID was invalid and the cached `_csrf` token was bound to a session the new server had no record of. POSTing with that combination produced an unhelpful 405 (the actual response when CSRF state is corrupted in the way I created it; not a generic "wrong CSRF" 403).

2. **Project-side scripts using stale URL paths.** `scripts/hac-{groovy,impex,flexquery}.sh` defaulted to `https://localhost:9002/hac` and used trailing slashes on `/console/scripting/`, `/console/flexsearch/`. In 2211-jdk21.x:
   - HAC moved to root context (`/login`, `/j_spring_security_check`, `/console/*`), no `/hac/` prefix.
   - Spring 6's PathPatternParser rejects trailing slashes by default, so `/console/scripting/` 404s while `/console/scripting` 200s.

The script's logic is: GET `$HAC/console/scripting/` post-login to extract a fresh `_csrf` meta tag from the authenticated page. With `/hac/console/scripting/` the request 404s, returns no CSRF, and the script reports the misleading message "Login failed — check HAC_USER/HAC_PASS." Login was actually fine; the post-login fetch was wrong.

This is **fully covered by `findings/2026-04-30-hac-context-path-root.md` (already promoted)**. That finding documented the cause AND the fix; this incident is the same thing recurring on a different project. The finding's promotion to a Phase F audit step in `plan-template.md` would have caught it before I started chasing the false hypothesis.

## What actually fixed it

Per `2026-04-30-hac-context-path-root.md`:

1. `scripts/hac-{groovy,impex,flexquery}.sh`: change `HAC="${HAC_URL:-https://localhost:9002/hac}"` → `HAC="${HAC_URL:-https://localhost:9002}"`.
2. `scripts/hac-groovy.sh` and `scripts/hac-flexquery.sh`: drop trailing slashes on `/console/scripting/` → `/console/scripting` and `/console/flexsearch/` → `/console/flexsearch`.

(`hac-impex.sh` already used `/console/impex/import` without trailing slash — no path edit needed beyond the `$HAC` default.)

Verified: `./scripts/hac-groovy.sh "return 2+2"` → `=> 4`. `./scripts/index-solr.sh` → 3 indexes initialized. OCC `products/search` returns 10 real products from the catalog.

The platform's `request-matcher="mvc"` is fine. SAP knows what they're shipping.

## Why the false hypothesis was tempting

The 405 with `Allow: GET` looked like Spring MVC was claiming the URL was registered as GET-only. With `request-matcher="mvc"` visible in the security config, the chain to "Spring MVC's path matcher rejects POST" was easy to construct. But the diagnostic that should have nailed it earlier was: **does login work in a browser with a fresh session?** That answer was yes (per the user's screenshot of HAC homepage with `Uptime: 4 minutes`), which immediately falsifies the platform-bug hypothesis.

## Lessons for the skill

1. **When debugging form-login failures, always test in a fresh browser session before blaming server-side config.** Browser-vs-curl divergence almost always means the curl flow has corrupt session/CSRF state, not a server bug.
2. **`hac-context-path-root` finding deserves to be promoted into the plan-template's Phase F more visibly.** It's already promoted, but it should be a Phase F precondition check, not a sub-bullet — otherwise people (me) will rediscover it from scratch and waste time chasing wrong hypotheses.
3. **Resist the pull to modify platform code** as a "test." It pollutes the diagnosis (the platform edit appeared to fix it; that turned out to be a coincidence with the fresh session that came with the restart). The user correctly insisted on reverting before drawing conclusions.

## Promotion suggestion

None for this finding — it's a false alarm resolved by an existing finding's fix. But:

- Consider adding a note in `references/phase-guide.md` Phase F preflight: "If you see HTTP 405 on HAC form-login from curl/scripts, suspect stale cookies/CSRF first; verify in a fresh browser session before suspecting server config."
- Strengthen the Phase F audit checklist to explicitly run the `grep -nE '/hac/(login|j_spring|console)|console/[a-z]+/\"' core-customize/scripts` audit step from `2026-04-30-hac-context-path-root.md` BEFORE running any HAC-dependent step.
