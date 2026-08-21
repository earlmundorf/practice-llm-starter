# STRB Demo — 15 min — ThinkShop + QRSPI

**Audience:** Customer Senior Technical Review Board
**Frame:** ThinkShop is the reference architecture for what we'll build for them; QRSPI is how we'll build it
**Total:** 15 min — 1 intro / 3 QRSPI / 9 ThinkShop / 2 close

---

## Pre-demo checklist (T-30 min)

```bash
# 1. Server up + green
cd core-customize
./gradlew serverStatus                     # PID present
./scripts/smoke-test.sh                    # 22 passed, 0 failed — DEMO READY

# 2. Frontend running
cd ../../sap-mcp-ui-l && npm run dev       # http://localhost:5173

# 3. Tabs queued in order:
#    [1] Storefront chat:      http://localhost:5173
#    [2] Backoffice:           https://localhost:9002/backoffice  (admin/nimda — already logged in)
#    [3] VS Code:              .claude/skills/qrspi/WALKTHROUGH.md
#    [4] VS Code:              core-customize/dev-config/local.properties
#    [5] Terminal:             cd core-customize, ready to re-run smoke if asked
#    [6] Photo on phone:       laptop / mug / headphones — anything in the catalog
```

**Failure-mode dry run:** run the smoke test once, refresh the storefront, send one chat message ("hi"), ensure SSE streams. If any fails — fix before showtime, don't demo broken.

---

## Beat 1 — Intro (1 min) — [0:00 → 1:00]

**Say:** *"I want to show you two things in fifteen minutes: how we work, and what we deliver. The 'how' is QRSPI — a structured workflow that turns an LLM coding agent into a disciplined engineering tool. The 'what' is ThinkShop — a working AI commerce reference platform on SAP Commerce that we use as the starting point for engagements like yours. By the end you'll see the methodology, the product, and the operational rigor behind both."*

**Do:** Stay on the title slide / black screen. Don't switch to a tab yet.

---

## Beat 2 — QRSPI (3 min) — [1:00 → 4:00]

**Say:** *"QRSPI is seven stages: Ticket, Questions, Research, Design, Structure, Plan, Implement, Validate. Two short developer review gates — between Design and Structure, and between Structure and Plan. Every stage produces a markdown artifact. The point is to keep an LLM agent's work auditable: nothing touches code until a human has signed off on the design and the slice plan."*

**Do:** Switch to **[3] WALKTHROUGH.md**. Scroll fast through:

| Section | Time | Point at |
|---|---|---|
| Stage 1 — Ticket & Questions | ~15s | "These are the questions the agent generated to scope the ticket — before any code reading" |
| Stage 2 — Research (blind) | ~15s | "Layered research output. Agents work in parallel and don't see each other's output." |
| Stage 3 — Design | ~30s | **DEV GATE 1** — *"Developer reviews ~200 lines of design. If wrong, you redirect now, not after 500 lines of code."* |
| Stage 5 — Plan (vertical slices) | ~20s | *"Each slice is a green test or a working endpoint. Not an arbitrary file split."* |
| Stage 6 — Implement | ~20s | *"Implementation runs against the build verbs configured per repo — your gradle, your tests, your CI."* |
| Stage 7 — Validate | ~15s | *"Success criteria from Stage 3 are the gate. You don't ship until every checkbox is green."* |

**Say (closing):** *"Three things make this production-fit. Gates force human review at the highest-leverage moments. Tier scaling adapts ceremony to risk — a one-line fix doesn't need seven stages. And build verbs are repo-specific, so verification is your build, not a generic placeholder. We'd run this on your codebase from day one."*

---

## Beat 3 — ThinkShop intro + storefront (1 min) — [4:00 → 5:00]

**Say:** *"This is what QRSPI builds. ThinkShop is real SAP Commerce 2211 — your familiar stack, no replatforming — plus a custom MCP extension that gives any AI client structured access to the catalog, cart, orders, and a knowledge base. The frontend is React; the backend is hybris. Standard CCv2 deployment shape."*

**Do:** Switch to **[1] storefront**. Quickly scroll the product catalog. Don't dwell.

---

## Beat 4 — Agent chat with KB grounding (3 min) — [5:00 → 8:00]

**Say:** *"The chat widget runs against an MCP server we wrote — nineteen tools the agent can call. Watch how it decides when to use a tool versus when to answer from the knowledge base."*

**Do:** Click chat. Type:

> **What's your return policy?**

**Watch & narrate as it streams:**
- *"Notice the streaming SSE response — that's not a polling hack, it's true server-sent events with non-streaming fallback."*
- *"And the answer is grounded: thirty days, free return shipping, prepaid label. That language came from a Solr-indexed knowledge entry the merchant edits in Backoffice — no hallucination, no policy drift."*

**Do:** Type the second prompt:

> **Do you have laptops under $1500?**

**Watch & narrate:**
- *"Same agent, different tool. It's calling product_search with a category filter and price guard."*
- *"Inline product cards — those render from a structured contract between agent and frontend, not the LLM guessing markdown. The 'suggested follow-ups' below the answer are part of the same contract."*

**Say (transition):** *"This isn't generic ChatGPT plumbing. The tool catalog is closed-set — the agent can't invent an endpoint. Every tool is rate-limited, input-validated, and OAuth-gated."*

---

## Beat 5 — Visual search (2 min) — [8:00 → 10:00]

**Say:** *"Multimodal. Customer takes a picture of a product on the table; agent finds matches in the catalog."*

**Do:** Click the camera / paperclip icon in chat. Upload **[6] phone photo**. Send.

**Watch & narrate as it returns:**
- *"Vision model analyzes the image, then runs a three-tier search — exact match, similar items, category fallback. The same agent loop, just with an image content block."*
- *"Vision capability is per-provider. Today we're on Anthropic Claude Sonnet 4.6 routed through Capgemini's Generative Engine gateway. We can swap providers without code changes — I'll show you that next."*

---

## Beat 6 — Backoffice + provider switch (2 min) — [10:00 → 12:00]

**Say:** *"Two things customer architects always ask about — and most LLM demos skip — are where merchants edit content and how you control which model is running."*

**Do:** Switch to **[2] Backoffice**. Search bar → type `KnowledgeEntry` → Show all instances.

**Say:** *"Standard Commerce Backoffice. Your merchants don't learn anything new — they edit a KnowledgeEntry exactly the way they edit a Product today. Twenty-six seed entries here cover policies, brand, events, how-tos. Reindex is one click in the Solr Indexer Cronjobs panel."*

**Do:** Switch to **[4] local.properties**. Scroll to the LLM provider section.

**Say:** *"Four providers wired in: OpenAI, Anthropic, Gemini, OpenAI-compatible. Switching is a one-line config change plus a server restart — we documented the exact recipe per provider in the reference docs. That matters for procurement: you're not locked to one model vendor on day one, and you can route through a corporate gateway like the Capgemini Generative Engine for compliance."*

---

## Beat 7 — Close + Q&A (3 min) — [12:00 → 15:00]

**Say:** *"Three takeaways. First, the architecture is your existing Commerce stack — we've added a custom MCP extension that's standalone; nothing in the platform or modules is forked. Second, every feature you just saw is covered by a twenty-two-check smoke test that runs end-to-end against the live server — the same script that would run in your CCv2 environments before every deploy. Third, the methodology that built this — QRSPI — is what we'd use on your engagement, with auditable artifacts for every change."*

**Do (optional, ~10 sec wow):** Switch to **[5] terminal**, run `./scripts/smoke-test.sh | tail -3`, point at `22 passed, 0 failed — DEMO READY`.

**Q&A — anticipated questions + crisp answers:**

| Question | One-liner |
|---|---|
| "How do you keep the LLM from going off-script?" | Closed-set tool catalog — agent can't invent endpoints. Plus per-user rate limit (20 req/min default) and server-side input validation at every tool boundary. |
| "What about prompt injection?" | Two-layer defense: tool inputs are server-validated regardless of agent intent; sensitive operations require customer-token auth, not the trusted-client token. The agent has no way to escalate scope. |
| "Production readiness?" | 80 unit tests, 22-check smoke suite, DB-persisted sessions (cluster-safe across CCv2 nodes), six ADRs documenting decisions, SECURITY.md with the deployment checklist. |
| "Cost control?" | Anthropic prompt caching on system prompt + tool definitions, bounded retry with exponential backoff (never retries after first stream delta), per-user rate limit, configurable max tool iterations. |
| "Customizable for our domain?" | Yes — the knowledge base is type-system data (KnowledgeEntry items.xml type), so you add entries via Backoffice or ImpEx the same way you add products. The agent's system prompt is config-driven. |
| "How long to deploy this for us?" | The reference platform stands up in days; the customer-specific work is your catalog, your KB content, and your branding. We'd scope that in the engagement using QRSPI. |

---

## Failure-mode fallbacks (don't panic on stage)

| If this breaks... | Recover by... |
|---|---|
| **LLM round-trip slow or hangs (>15s)** | Switch to terminal, run `./scripts/smoke-test.sh \| tail -3` — proof the system works end-to-end. Skip live chat, narrate the architecture instead. |
| **Visual search fails or times out** | Skip the upload, say *"vision is per-provider configurable — happy to show that in a follow-up"*. Move to Beat 6. |
| **Frontend dev server crashed** | Use OCC directly: `curl -k https://localhost:9002/occ/v2/electronics/info/returns-policy \| jq` — shows the KB content the chat would have surfaced. |
| **Server unreachable entirely** | Walk the audience through `WALKTHROUGH.md` and `llm-providers.md` recipes section — the artifacts speak for themselves. Reschedule the live demo. |
| **Backoffice login times out** | Skip Beat 6's first half. Open `coremcp-items.xml` in VS Code and point at the `KnowledgeEntry` type definition — same point made through the source instead of the UI. |

---

## What NOT to demo (cut for time)

- Cart → checkout flow (covered by smoke #14–17 — mention only if asked)
- Promotions / Drools rules (covered by smoke #13 — mention only if asked)
- 80 unit tests / test report (offer as follow-up)
- The full QRSPI live workflow (15 min is too tight — the WALKTHROUGH artifact is enough)
- Architecture deep-dive into the agent loop decomposition (offer as follow-up)
