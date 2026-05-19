# Verification checklist

The user's definition of migration-done: "the system builds, Solr works, code works, data matches, and new OCC access is testable." This checklist operationalizes that.

Use this between phases of the execute workflow and at the end. Each item has a `How to verify` line with a concrete command or observation. Skip anything that isn't relevant to the project (e.g., Solr items when the project doesn't use Solr).

## Phase gates

A migration plan is divided into phases (Spring, Tomcat, OAuth, Build, Tests, OCC). After each phase the user approves moving on — the gate questions below feed that decision.

### Gate 0 — Before starting

- [ ] **Working baseline captured.** Commit clean; tag `pre-migration`; `ant clean all` succeeds on the legacy stack.
  - How to verify: `git tag | grep pre-migration` + build log.
- [ ] **No deprecated extensions in manifest.** No `*cockpit*`, no `amazoncloud`.
  - How to verify: `grep -E "cockpit|amazoncloud" manifest.json localextensions.xml`.
- [ ] **Starting state pinned.** `scripts/detect_state.sh` output saved.

### Gate 1 — After build-tooling phase (Ant / Gradle)

- [ ] `ant --version` reports ≥ 1.10.14.
- [ ] `./gradlew --version` reports 8.5+ (if the project uses Gradle).
- [ ] `ant clean` succeeds (uses new Ant + task defs).
  - How to verify: exit code 0; no "Unsupported major.minor version" errors.
- [ ] Custom Ant task definitions still load.

### Gate 2 — After Spring 6 / Jakarta sweep

- [ ] Full build: `ant clean all`.
  - How to verify: exit 0, no compile errors. Expected residues: deprecation warnings (OK at this gate).
- [ ] No `javax.servlet`, `javax.persistence`, `javax.validation` imports remain in custom extensions:
  - `grep -rE "javax\.(servlet|persistence|validation|annotation)" custom/ bin/custom 2>/dev/null | grep -v target`
- [ ] No `@Required` remaining:
  - `grep -rE "@Required\b" custom/ bin/custom`
- [ ] No `SocketUtils` remaining.
- [ ] Spring XML files don't reference removed beans (use `04-spring-model-changes.md` as the diff).
- [ ] No Apache Commons Lang 2 imports:
  - `grep -rE "import org\.apache\.commons\.lang\b" custom/ bin/custom`
- [ ] No Apache Commons Math 3 imports (library removed):
  - `grep -rE "org\.apache\.commons\.math3" custom/ bin/custom`
- [ ] No `EhCacheRegion` references (replaced by `DefaultCacheRegion`):
  - `grep -rE "EhCacheRegion" custom/ bin/custom`
- [ ] No DdlUtils imports (library removed, integrated into platform):
  - `grep -rE "org\.apache\.ddlutils" custom/ bin/custom`

### Gate 3 — After Tomcat phase

- [ ] Tomcat starts: `ant initialize` / server start.
  - How to verify: `bin/platform/hybrisserver.sh start` reaches "Platform started" with no SEVERE filter errors.
- [ ] No `SEVERE: Exception starting filter [WebResourceOptimizer] java.lang.NoClassDefFoundError: javax/servlet/Filter`.
- [ ] SSL connector (if configured) serves HTTPS.
- [ ] No X-XSS-Protection warnings on boot.

### Gate 4 — After OAuth / Auth Server phase

- [ ] Login via admin UI works (browser).
- [ ] OAuth token request succeeds for a registered client:
  - `curl -s -X POST "https://HOST/authorizationserver/oauth/token" -d "grant_type=client_credentials&client_id=CLIENT&client_secret=SECRET"` → returns `access_token`.
- [ ] OAuth authorization code flow end-to-end (if the project has public clients): PKCE verifier round-trips, token issued, refresh works.
- [ ] Redirect URIs validate per new stricter rules (see known-incidents #3).

### Gate 5 — Data integrity

Operational test for the "data matches" criterion (SKILL.md Core principle).

- [ ] Initialize/update runs without the new `create-data` failures (or known-tolerated failures are explicitly allowlisted).
- [ ] **Production migrations: use `yupdatesystem`, never `yinitialize`** against a populated DB. `yinitialize` wipes data; only run it on dev/throwaway environments.
- [ ] Row counts for 5–10 critical types within tolerance (±0.1% unless documented). At minimum: Product, Customer, Order, Catalog, ContentPage.
  - How to verify: FlexibleSearch `SELECT count(*) FROM {Product}` before vs. after. Record both in `migration-docs/migration-log.md`.
- [ ] Orphaned types cleaned per SAP general update guide (run platform's orphaned-type cleanup tooling). Diff before/after to confirm intended-data wasn't removed.
- [ ] `LocalizedHybrisConstraintViolation` subclasses compile and validate correctly.
- [ ] **Stale cart/order deserialization sample passes** (catches known-incidents #6). Sample 100 random pre-migration carts/orders; load each via cart facade; zero `ClassCastException` / `ItemNotFoundException` / `InvalidClassException`.

### Gate 6 — Solr

Operational test for the "Solr works" criterion (SKILL.md Core principle).

- [ ] Solr reachable: `curl -s http://solr:8983/solr/admin/cores?action=STATUS` returns healthy.
- [ ] Search service responds from the storefront.
- [ ] **Indexed doc count matches pre-migration baseline within ±0.5% per type.** Compare against the Step 0.0b export.
- [ ] **Relevance preserved on top-10 search queries.** Pick the project's top-10 search queries (from analytics or product catalog top-sellers); verify that the same product IDs appear in the top-10 results, in roughly the same order.
- [ ] **Re-index time estimate documented if Solr major-version changed.** Solr 8 → 9 invalidates index format; full re-index of 1M+ docs is hours, not minutes. Estimate: ~10k docs/min/core. Plan maintenance window accordingly.

### Gate 7 — OCC end-to-end

Operational test for the "OCC testable" criterion (SKILL.md Core principle). Status-code parity is necessary; **schema parity is the actual test**.

- [ ] `/occ/v2/BASESITE/products/search?query=*` returns 200 with a result set.
- [ ] Authenticated OCC call succeeds (with token from Gate 4).
- [ ] Swagger JSON loads: `/occ/v2/api-docs` returns a valid OpenAPI doc.
- [ ] POST endpoint exercises binding path (catches known-incidents #4):
  - Pick one custom POST endpoint; send a representative payload; verify 2xx.
- [ ] **OCC contract diff against `migration-docs/occ-baseline-{{DATE}}/`** (captured in Step 0.0c). Replay each captured endpoint; JSON-diff response bodies. **Any non-trivial structural diff (field added/removed/renamed/reshaped/null-able-changed) is a finding.** Status code matching alone passes the previous bullet but does not pass this one.
  ```bash
  for f in migration-docs/occ-baseline-{{DATE}}/*.json; do
    ep=$(basename "$f" .json | tr '_' '/')
    diff <(jq -S . "$f") \
         <(curl -s "$NEW_OCC_BASE/v2/$BASESITE/$ep" -H "Authorization: Bearer $NEW_TOKEN" | jq -S .) \
         | head -50  # truncate; full diff written to migration-log
  done
  ```
- [ ] Swagger drift check: diff `/occ/v2/api-docs` legacy vs new. New paths/operations appearing is fine; existing paths changing schema is a finding.

### Gate 8 — Storefront (if applicable)

- [ ] Home page renders.
- [ ] Navigation + PDP + cart flow completes.
- [ ] No 404s on static resources (catches known-incidents #2 — PathPatternParser fallout).

## Post-migration observations to record

After the full migration passes all gates, write a `findings/YYYY-MM-DD-post-migration.md` entry with:
- Which gates required manual fixes beyond what OpenRewrite did
- Any new symptom patterns not already in `known-incidents.md`
- Time spent per phase (informs future-project estimation)
- Any OCC/data assertions the team established during verification (for inclusion in the next run's checklist)
