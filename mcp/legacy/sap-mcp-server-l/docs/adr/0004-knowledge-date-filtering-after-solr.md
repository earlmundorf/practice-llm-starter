# 0004 — Filter KnowledgeEntry validity dates in Java, not Solr

**Status:** accepted

## Context

`KnowledgeEntry` has `validFrom`/`validTo` windows for time-bound content
(promos, events). Indexing them into the `knowledgeIndex` would allow Solr-side
range filtering — but the SpringEL value provider emits `Date.toString()`, which
is not ISO-8601 and fails Solr date-field conversion. A custom value provider
could fix the format.

## Decision

Keep the date attributes **out of the Solr index**. The exclusion is documented
with a comment at the site in the Solr config ImpEx.

**Honest current state (corrected 2026-06-10):** the validity window is also
not enforced at query time — `DefaultKnowledgeSearchService` performs no date
filtering, so `validFrom`/`validTo` are editorial bookkeeping only. Expired
promo/event entries remain findable until unpublished or removed. This is
acceptable for a small curated demo corpus.

## Consequences

- Simple today at knowledge-base scale (dozens of curated entries).
- Upgrade paths, in order of effort: (a) a post-query date filter in
  `DefaultKnowledgeSearchService` (pages can then under-fill), or (b) a custom
  ISO-8601 value provider plus a Solr range filter for index-side enforcement —
  the right answer if the knowledge base grows to thousands of time-bound
  entries.
