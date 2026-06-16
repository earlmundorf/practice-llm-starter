# Knowledge Base — Components

The KB data layer lives entirely in two existing files. No new files were added (KB-01).

## `src/types/index.ts`

- **`KnowledgeEntry`** — one KB entry. Fields: `uid`, `category` (plain `string`), `title`,
  `summary`, `body` (long-form prose), `tags: string[]`, `priority: number`,
  `imageUrl?` (optional). Mirrors the public `/info/*` contract field-for-field.
- **`KnowledgeSearchResult`** — `{ results: KnowledgeEntry[]; count: number }`, returned by
  `searchKnowledge`.

## `src/services/api.ts`

- **`mapKnowledgeEntry(raw): KnowledgeEntry`** — internal mapper (lives in the `mapOcc*`
  block under the shared `eslint-disable @typescript-eslint/no-explicit-any`). Maps raw
  JSON 1:1; defaults `tags` to `[]` when absent. The single place raw KB JSON becomes typed.
- **`api.searchKnowledge(opts?)`** — `opts: { q?, category?, pageSize? }` (all optional;
  empty = browse). Builds query params with `URLSearchParams`, appending only the keys that
  are provided. Public `fetch` against `${OCC_BASE}/info/search`. Returns
  `{ results, count }`; on non-OK returns `{ results: [], count: 0 }` (never throws).
- **`api.getKnowledgeEntry(uid)`** — public `fetch` against `${OCC_BASE}/info/{uid}` (uid
  URL-encoded). Returns the mapped `KnowledgeEntry`, or `null` on 404/non-OK (never throws).

## Not touched

No routes, pages, components, contexts, hooks, Vite proxy, or env changes — those belong to
KB-02/KB-03. `/info` rides the same `OCC_BASE` as `products/search`, so no proxy entry was
needed.
