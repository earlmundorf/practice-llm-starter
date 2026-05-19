# Visual Product Search — Implementation Steps

Reference implementations from the Cowork session are at:
`$CLAUDE_COWORK_HOME/Cap Gemini Corporate/` (VisualSearchService.java, DefaultVisualSearchService.java, VisualSearchController.java, coremcp-visual-search-spring.xml)

---

## How the AI Request Works

Visual search is a **single-shot vision call**, not an agentic tool loop:

1. Controller receives base64 image from the client
2. `DefaultVisualSearchService` sends image to GPT-4o Vision via `openAiClient.chatCompletion(messages, null, "gpt-4o")` — `tools` is **null**, no function calling
3. The prompt instructs GPT-4o to reason step-by-step about what it sees, explain its thinking, and return structured JSON: `{ productName, brand, category, color, material, searchTerms, reasoning, confidence }`
4. Java code parses that JSON and runs 3 deterministic Solr queries via `ProductSearchFacade`

The AI acts as a **vision classifier only**. Catalog search is plain Java — faster, cheaper, testable.

---

## Step 1: Create the Service Interface

**File:** `src/com/coremcp/services/VisualSearchService.java`

- Single method: `Map<String, Object> searchByImage(String base64Image, String mimeType)`
- Returns map with `visionAnalysis` (String) and `products` (List of match maps)
- Adapt from reference implementation — verify package and import alignment with existing services

## Step 2: Create the Service Implementation

**File:** `src/com/coremcp/services/impl/DefaultVisualSearchService.java`

- Implements `VisualSearchService`
- Dependencies: `OpenAiClient` (existing), `ProductSearchFacade` (platform) — injected via Spring setters
- GPT-4o Vision call: builds vision message format (content as List with image_url + text), calls `openAiClient.chatCompletion(messages, null, "gpt-4o")`
- Parses JSON response from GPT-4o into product attributes
- 3-tier catalog search via `productSearchFacade.textSearch()`:
  1. Exact: brand + product name (3 results, confidence 0.95)
  2. Similar: name + color + material + category (5 results, confidence 0.7, de-duped)
  3. Fallback: category only if no results (5 results, confidence 0.4)
- Builds response with product maps (name, code, price, description, thumbnailUrl, matchType, confidence)

**Verify before writing:**
- `OpenAiClient.chatCompletion()` signature — confirm the 3-arg overload exists (it does: `messages, tools, modelOverride`)
- `ProductSearchFacade` import path and `textSearch()` signature
- Match the code style of existing `Default*` services (LOG pattern, exception handling, etc.)

## Step 3: Register the Visual Search Service Bean

**File:** `resources/coremcp-spring.xml`

Add in the services section (after OpenAI Client, before Agent Service):

```xml
<!-- Visual Search Service -->
<alias name="defaultVisualSearchService" alias="visualSearchService"/>
<bean id="defaultVisualSearchService"
      class="com.coremcp.services.impl.DefaultVisualSearchService">
    <property name="openAiClient" ref="openAiClient"/>
    <property name="productSearchFacade" ref="productSearchFacade"/>
</bean>
```

Follows the project's alias pattern (`defaultXxx` bean + `xxx` alias).

**Note:** `openAiClient` and `productSearchFacade` beans already exist — no new dependencies.

## Step 4: Create the Controller

**File:** `src/com/coremcp/controllers/VisualSearchController.java`

- `@Controller` + `@RequestMapping("/{baseSiteId}")`
- Endpoint: `POST /agent/visual-search` (produces `application/json`)
- `@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })` — same as AgentController
- `@Resource(name = "visualSearchService")` injection
- Validation:
  - image field required and non-blank → 400
  - image size ≤ 10MB (base64 length check) → 413
  - mimeType in whitelist (jpeg, png, webp, gif) → 400
- Delegates to `visualSearchService.searchByImage(image, mimeType)`
- Error handling: catch-all returns 500 with JSON error body
- **No additional Spring config needed** — `coremcp-web-spring.xml` already scans `com.coremcp.controllers`

**Verify before writing:**
- Match the controller patterns in `AgentController` (body parsing, ObjectMapper usage, response style)

## Step 5: Build and Verify Compilation

```bash
cd core-customize && ./gradlew ybuild
```

- Fix any compilation errors
- Confirm the build output includes coremcp extension

## Step 6: Start Server and Smoke Test

```bash
cd core-customize && ./gradlew startServer
```

Once running:

1. **Get an OAuth token:**
   ```bash
   curl -sk -X POST http://localhost:9001/authorizationserver/oauth/token \
     -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234'
   ```

2. **Test the OCC endpoint directly** (small base64-encoded image):
   ```bash
   BASE64=$(base64 < test-image.jpg)

   curl -sk -X POST http://localhost:9001/occ/v2/electronics/agent/visual-search \
     -H "Authorization: Bearer {token}" \
     -H "Content-Type: application/json" \
     -d "{\"image\":\"$BASE64\",\"mimeType\":\"image/jpeg\"}"
   ```

3. **Verify response shape:**
   - `visionAnalysis` — string description from GPT-4o
   - `aiDetail` — AI reasoning details: `{ searchTerms, reasoning, confidence, productName, brand, category, color }`
   - `products` — array of `{ product, matchType, confidence }` objects (products in full OCC shape)

4. **Test error cases:**
   - Missing image → 400 (controller) / error result (MCP tool)
   - Invalid MIME type → 400
   - No auth token → 401

## Step 7: Update Documentation

- Update `docs/endpoints.md` with the visual-search endpoint spec
- Update `docs/README.md` to reference visual search

---

## Optional Future Steps

- **Rate Limiting:** Add per-user rate limiting for the vision endpoint (OpenAI costs)
- **MCP Tool:** If a programmatic agent-to-agent use case emerges where raw image bytes are passed, add a `VisualSearchToolHandler`. Not useful for AI chat clients (Claude Code, etc.) since they can't forward raw images through MCP tool arguments.
- **Frontend:** `ThinkshopImageSearch.jsx` component in the storefront repo (reference impl available in cowork directory)
