# Visual Product Search — Context

## What It Does

Users upload, paste, or photograph a product image. The system sends it to GPT-4o Vision for identification, then searches the SAP Commerce Solr catalog for matching products using a 3-tier strategy (bestMatch, similar, explore).

## When It's Used

- Mobile shoppers snap a photo of a product they want to find in the catalog
- Desktop users paste/drag-drop product images
- AI agent integration — the visual search endpoint can be called by MCP tool handlers

## Key Decisions

1. **Reuses the shared `LlmClient`** — GPT-4o Vision uses the same `/chat/completions` endpoint. The vision message format uses a `List` for the `content` field instead of a `String`, which Jackson serializes correctly. Visual search runs through the same `DefaultLlmClient` → provider pipeline used by agent chat (see `coremcp/docs/llm/README.md`).

2. **3-tier search strategy** — maximizes recall while ranking by confidence:
   - **Tier 1 (Best Match):** brand + product name from vision → Solr free-text (up to 3 results, confidence 0.95; falls back to searchTerms at 0.9)
   - **Tier 2 (Similar):** product name + color + material + category (up to 5, confidence 0.7)
   - **Tier 3 (Explore / "You Might Like"):** category only (up to 5, confidence 0.4)
   - De-duplicated across tiers by product code

3. **Standard OCC pattern** — same auth, same `/{baseSiteId}` prefix, same `@Secured` roles as existing agent endpoints

4. **No new dependencies** — uses existing `LlmClient` bean and platform `ProductSearchFacade`

5. **Validation at the boundary** — image size (10MB), MIME type whitelist, auth check — all in the controller

6. **AI reasons step-by-step** — the prompt instructs GPT-4o to reason about what it sees, explain its thinking, and suggest `searchTerms` for catalog lookup

## API Contract

```
POST /{baseSiteId}/agent/visual-search
Authorization: Bearer {oauthToken}
Content-Type: application/json

Request:  { "image": "<base64>", "mimeType": "image/jpeg" }
Response: {
  "visionAnalysis": "...",
  "aiDetail": { "searchTerms": "...", "reasoning": "...", "confidence": 0.9, "productName": "...", "brand": "...", "category": "...", "color": "..." },
  "products": [
    { "product": { "name": "...", "code": "...", "price": { "value": 99.85 }, "stock": {...}, "images": [...], "categories": [...] },
      "matchType": "bestMatch|similar|explore", "confidence": 0.95 }
  ]
}

Products are returned in full OCC shape (price.value, stock, images array, categories).

Errors: 400 (bad input), 413 (too large), 429 (rate limit), 503 (OpenAI down)
```
