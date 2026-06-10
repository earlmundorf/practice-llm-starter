# Knowledge Base — Context

## What it does

A shopper-facing content store — return/shipping/warranty/privacy policies,
brand and contact info, marketing events, current promos, how-tos, and buying
guides — modeled as `KnowledgeEntry` items, indexed in a dedicated Solr index
(`knowledgeIndex`), and surfaced three ways:

1. **Public OCC endpoints** — `GET /{baseSiteId}/info/{uid}` and
   `GET /{baseSiteId}/info/search` (anonymous access allowed; informational
   content, no PII).
2. **MCP tools** — `info_get` and `info_search`, available to any MCP client.
3. **The agent** — the system prompt instructs the agent to call `info_search`
   for any question that isn't about a specific product, order, or cart action,
   and to answer from the entry's summary/body rather than guessing.

This is what lets the agent answer "what's your return policy?" or "any events
coming up?" from governed content instead of hallucinating.

## When it's used

Every non-transactional question in a chat conversation routes here. The demo
data ships 26 published entries across eight categories (`policy`, `event`,
`promo`, `guide`, `brand`, `howto`, `contact`, plus loyalty content) — loaded by
`sampledatamcp` (`projectdata-50-knowledge.impex`, `-55-knowledge-extras.impex`).

## Key decisions

1. **Solr-only reads.** `DefaultKnowledgeSearchService` reads fields straight
   from the indexed documents — no Model loading, no FlexibleSearch on the read
   path. Same shape as `ProductSearchFacade.textSearch`, minus product
   conversion. Content changes require a `knowledgeIndex` reindex to become
   visible.
2. **Relevance is tuned, not defaulted.** The free-text template boosts
   uid (200) > title (120/150 phrase) > tags (80) > summary (60) > body (25),
   with fuzzy matching on title/summary/tags, and the relevance sort is
   score + `priority` descending — editors control tie-breaks via the
   `priority` attribute.
3. **Anonymous access is deliberate.** `/info/**` carries `ROLE_ANONYMOUS` in
   addition to the usual roles: policies and events are public content.
   `pageSize` is clamped (`coremcp.knowledge.maxPageSize`, default 50) so the
   open endpoint can't be used to bulk-dump the index in one call.
4. **Validity dates are stored but not enforced.** `validFrom`/`validTo` exist
   on the type for editorial bookkeeping, but they are neither Solr-indexed
   (the SpringEL provider emits non-ISO-8601 dates) nor filtered at query time
   — expired promo entries remain findable until unpublished or removed.
   Acceptable for a curated demo corpus; the upgrade path is a post-query
   filter in `DefaultKnowledgeSearchService` or a custom ISO-8601 value
   provider + Solr range filter. See ADR 0004.
5. **`status` exists but the demo ships everything `published`.** Draft
   filtering is likewise an upgrade path, not a current behavior.
