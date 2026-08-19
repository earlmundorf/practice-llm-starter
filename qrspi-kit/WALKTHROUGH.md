# QRSPI Walkthrough — one real ticket, stage by stage

This walks the **backend** kit end to end: install it, configure it, tour what landed, then
run a real ticket through all seven `/cq:*` stages. The worked example is **THINK-201 —
surface product reviews to the MCP agent**, run against the `coremcp` extension. Every
citation below is real code in this repo, so you can verify as you go.

> Prereq: Claude Code, and a SAP Commerce repo. This example runs against the `sap-mcp-server-l`
> backend (it has `coremcp` and the ticket already staged), but the steps are the same for any
> Commerce project.

---

## 0 · Install the kit

From `qrspi-kit/`, copy the backend set into your Commerce repo root:

```bash
cp -R qrspi-kit/backend/.claude       /path/to/commerce-repo/
cp    qrspi-kit/backend/CLAUDE.md      /path/to/commerce-repo/    # or merge into an existing one
cp -R qrspi-kit/backend/working-docs   /path/to/commerce-repo/
```

Publish the stage commands so `/cq:*` resolves:

```bash
/path/to/commerce-repo/.claude/skills/commerce-qrspi/sync-commands.sh
```

## 1 · Configure (`working-docs/config.json`)

Copy the SAP Commerce profile over the template, then adjust:

```bash
cd /path/to/commerce-repo
cp working-docs/profiles/sap-commerce.json working-docs/config.json
```

Edit `working-docs/config.json`:
- set `<ext>` (in `UNIT_TEST`/`INTEGRATION_TEST`) to your extension — here **`coremcp`**;
- **gradle** is the default; if you build with ant, set `buildTool: "ant"` and swap the verbs
  per the `_notes` in the file.

That's the whole portability layer — the stages read their build **verbs** and **research
layers** from this file, so nothing is hardcoded.

## 2 · What landed (directory tour)

```
commerce-repo/
├── CLAUDE.md                         # rules + skills map + "configure the rice way"
├── .claude/
│   ├── commands/cq/*.md              # /cq:0_go … 7_validate  (published by sync-commands.sh)
│   └── skills/
│       ├── commerce-qrspi/           # the 7-stage workflow (commands/, findings/, sync-commands.sh)
│       ├── sap-commerce/  sap-best-practices/  java-best-practices/  impex/
│       └── sap-commerce-migrate-j21/
└── working-docs/
    ├── config.json                   # ← active config (copied from a profile)
    └── profiles/sap-commerce.json    # the starter you copied
```

Per-ticket artifacts will appear under `working-docs/<TICKET>/` as you run the stages.

## 3 · The ticket

`tickets/active/THINK-201.md` (already in `sap-mcp-server-l`): *the `product_get` tool advertises
a `REVIEW` option but callers get no review content back — return real reviews (rating, headline,
comment), reusing OOTB, with sample data.* Stage 1 auto-reads it from `tickets/active/`.

---

## 4 · Run it — one `/cq:*` command per stage

Type each command in Claude Code. Each stage runs in a fresh context, reads only its declared
inputs, writes its artifact under `working-docs/THINK-201/`, and prints the next command. **★ = a
human gate — you review and approve before it proceeds.**

### `/cq:0_go THINK-201`
Recommends a **tier** and confirms the build config. This ticket spans data + a tool handler +
docs, so expect a recommendation of **`full`** (all stages, gates at Design/Structure/Validate).
Confirm it.

### `/cq:1_ticket`
**Writes** `ticket.md` (problem restated, draft assumptions, draft success criteria) and
`questions.md` (8–15 *neutral* research questions). Good questions describe what to find, never
what to build — e.g. *"How are product reviews modeled and exposed in the platform today, and does
any custom code touch them? (file:line)"*

### `/cq:2_research`   *(blind — reads `questions.md` only, never the ticket)*
**Writes** `research.md` — facts with `file:line`, no solutioning. On a correct run it should
surface the real picture (and overturn the naive assumption that "reviews aren't wired"):
- `product_get` **does** map `REVIEW` → `ProductOption.valueOf` and pass it to the facade —
  `ProductGetToolHandler.java:72` and `:80`.
- `ProductOption.REVIEW` exists OOTB — `…/commercefacades/product/ProductOption.java:42`.
- The OOTB read/write API already exists: `ProductFacade.getReviews(code)` (`ProductFacade.java:81`),
  `postReview(...)` (`:69`), converted via `CustomerReviewPopulator` (`DefaultProductFacade.java:58`).
- The `customerreview` extension is present (`hybris/bin/modules/base-commerce/customerreview`).
- **Gap:** no custom code calls `getReviews`, and there is **no sample review data** — so callers
  get nothing because there's nothing to return / no dedicated read path, not because the option is unwired.

### `/cq:3_design`   ★ **gate**
Presents 2–5 design decisions as options and **waits for your answers**, then writes `design.md`
(~200 lines) with current state, desired end state, decisions, success criteria (split
Automated / Manual), and out-of-scope. Expected decisions for THINK-201:
- **Reuse OOTB** `ProductFacade.getReviews(code)` — no new model/service.
- Expose it as a **dedicated `product_reviews` tool** (clean, paginated) vs. populating the
  `REVIEW` field on `product_get`.
- **Seed sample `CustomerReview`s** so behavior is observable.
- Success criteria: `BUILD` green; an MCP `product_reviews` call returns rating/headline/comment.

### `/cq:4_structure`   ★ **gate**
**Writes** `structure.md` — the work cut into **vertical slices**, each with a verification
checkpoint. Expected slices: (1) sample review data (impex/groovy creating `CustomerReview`s),
(2) `ProductReviewsToolHandler` calling `productFacade.getReviews(code)` + Spring registration,
(3) update `coremcp/docs/reference/tools.md`.

### `/cq:5_plan`
**Writes** `plan.md` — a checkboxed tactical plan, commands resolved from your `config.json`
verbs (e.g. `BUILD` → `./gradlew yclean ybuild`, `SERVER_RESTART`).

### `/cq:6_implement`
Implements **slice by slice, one commit each**, running the change-type verbs between slices.
Touches: a sample-data file (`CustomerReview` rows), `ProductReviewsToolHandler.java` +
`coremcp-spring.xml` registration, and the tools reference doc. No new review model — it calls
the OOTB facade.

### `/cq:7_validate`   ★ **gate**
Re-runs the success criteria, applies the diff-ownership check, and opens the PR. Expected: build
green, and a `product_reviews` MCP call returns real review content for the seeded product.

---

## 5 · What this run demonstrates

- **Grounding rule in action.** Research states only verified facts (`file:line`) and *corrected*
  the tempting-but-wrong "reviews are unwired" assumption — the exact failure the grounding rule
  exists to prevent.
- **Reuse OOTB, override in custom.** The design reuses `customerreview` + `ProductFacade.getReviews`
  rather than rebuilding reviews — the core SAP Commerce best practice.
- **Human gates.** You steer at Design, Structure, and Validate; everything else runs on rails.
- **Config-driven.** Every command the stages ran came from `working-docs/config.json` — swap the
  profile (or `buildTool: ant`) and the same workflow serves a different Commerce project unchanged.
