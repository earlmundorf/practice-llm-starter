# 0004 — Filter KnowledgeEntry validity dates in Java, not Solr

**Status:** accepted

## Context

`KnowledgeEntry` has `validFrom`/`validTo` windows for time-bound content
(promos, events). Indexing them into the `knowledgeIndex` would allow Solr-side
range filtering — but the SpringEL value provider emits `Date.toString()`, which
is not ISO-8601 and fails Solr date-field conversion. A custom value provider
could fix the format.

## Decision

Keep the date attributes **out of the Solr index** and apply the validity-window
filter in `DefaultKnowledgeSearchService` after the Solr query returns. The
exclusion is documented with a comment at the site in the Solr config ImpEx.

## Consequences

- Simple and correct today at knowledge-base scale (dozens of entries): the
  post-filter cost is negligible.
- Pages can under-fill (Solr returns N, Java filters some out). If the knowledge
  base grows to thousands of time-bound entries, revisit with a custom
  ISO-8601 value provider and a Solr range filter.
