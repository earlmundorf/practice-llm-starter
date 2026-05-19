# Supporting skill findings — sap-mcp-server-g run (2026-05-01)

> What the `sap-commerce-migrate-j21` skill wants to LEARN from this migration run. Each item below is a question or gap the skill is currently uncertain about; this run is the validation event that will produce concrete findings.

## 1. In-place bootstrap behavior on a pre-bootstrap project

**Source:** `findings/2026-05-01-inplace-bootstrap-platform-behavior-unknown.md`

This project starts pre-bootstrap (`bin/platform` not extracted). The unresolved sub-case in the finding — "what happens when `bootstrapPlatform` finds an existing extraction at a different version" — **will not be answered by this run** because there is no prior extraction to upgrade. Document this clearly in `migration-log.md` after Phase 0 completes.

To answer the unresolved sub-case, a future run needs a project that is mid-stream (e.g., already on `2211.50` with `bin/platform` extracted) being upgraded to `2211-jdk21.x` in place.

## 2. JDK-17 → JDK-21 toolchain swap on an existing project

This project has `org.gradle.java.home` pinned to JDK 17 in `gradle.properties` and is not yet bootstrapped. Whether changing that pin BEFORE first bootstrap behaves identically to changing it AFTER an existing bootstrap is open. Capture timing and any anomalies.

## 3. OAuth client ImpEx surface in a custom OCC extension

The project's `coremcp` extension defines OAuth clients via ImpEx. Phase D will surface concretely:
- Which redirect URIs the new validator demands for the existing client types
- Whether the `password` grant is in active use and what the `authorization_code + PKCE` replacement looks like for an MCP-server consumer
- Whether `oauth2commons` + `authorizationserver` + `resourceserver` is the right subset for an OCC-only deployment with custom JSON-RPC endpoints

If new patterns emerge (especially around how the MCP server authenticates to OCC), promote them into `references/sap-docs/08-oauth-authorization-server.md` or a new finding.

## 4. Visual-search / GPT-4o Vision endpoint behavior under Spring 6

The project exposes `POST /{baseSiteId}/agent/visual-search` for image analysis. POST + multipart + Spring 6 is the exact combination that surfaces incident #4 (POST mappings 404/405, multipart resolver, `@ModelAttribute`). This endpoint is a strong test case for the watchlist; capture any breakage as a finding.

## 5. Solr `thinkshopIndex` reindex parity

Custom Solr config (`thinkshopIndex` with price range facets and sort definitions) under `core-customize/hybris/config/solr/`. Reindex via `./scripts/index-solr.sh` post-migration; compare doc counts and a sample of facet/sort outputs against legacy expectations. New finding if anything diverges silently.

## 6. Sample-data extension as a reproducibility anchor

`sampledatamcp` is the project's ImpEx-seeded data layer. For scenario B, this extension's git tag (Step 0.0a) is the data-rollback anchor. If any ImpEx fails under the new `create-data` fail-by-default behavior, capture which directives needed loosening and whether they should be promoted into the skill's known-incidents list.
