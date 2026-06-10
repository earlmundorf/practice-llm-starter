# Commerce QRSPI — Developer Walkthrough

A day-in-the-life run, end to end: install, pick up a Jira ticket, add a new OCC
controller endpoint. Example ticket: **CMRC-1234 — "Expose express-checkout eligibility
on the cart via OCC"** (new endpoint `GET /users/{userId}/carts/{cartId}/express-eligibility`).

---

## 0. Install (once per repo, ~2 minutes)

```bash
cd ~/work/acme-commerce          # your Commerce repo
mkdir -p .claude/skills .claude/commands
cp -r /path/to/commerce-qrspi .claude/skills/commerce-qrspi
mkdir -p .claude/commands/cq
cp .claude/skills/commerce-qrspi/commands/*.md .claude/commands/cq/
git add .claude && git commit -m "chore: add commerce-qrspi workflow"
```

Verify: open `claude`, type `/cq` — autocomplete shows `1_ticket` … `7_validate`.
Prerequisite for Jira pull: Atlassian MCP connected (`claude mcp list`). **No MCP
(VPN-only Jira, no connector)?** Set `jira.mode=manual` in config: at stage 1 you paste
the ticket text when prompted, and stages 5 and 7 write every outbound update (plan
comment, PR link, status transition) paste-ready into
`working-docs/<TICKET-KEY>/jira-updates.md` — you copy them into Jira when convenient.
Everything else works identically.

---

## 1. Start — one command (~3 min, you skim)

```
claude
> /cq:go CMRC-1234
```

Claude pulls the ticket and recommends a tier:

> "New endpoint, touches three extensions and has design choices (eligibility reasons,
> endpoint shape) — I recommend **full**. (trivial / simple / full / comprehensive)"

You confirm `full`. Claude runs the ticket+questions stage and writes two files:

**`working-docs/CMRC-1234/ticket.md`** (excerpt)
> **Problem:** Frontend needs to know if a cart qualifies for express checkout
> (single shipment, no pre-order items, saved payment on file) without assembling
> it client-side from three calls.
> **ASSUMPTION-1:** Eligibility rules already exist server-side (ticket implies it).
> **ASSUMPTION-2:** Endpoint is for authenticated users only, not anonymous carts.
> **Draft success criteria (from AC):** returns `eligible: true|false` + reasons array;
> 401 for anonymous; responds < 300ms.

**`working-docs/CMRC-1234/questions.md`** (excerpt — note: neutral, no solutioning)
> Q1. How are OCC controllers registered in our custom webservices extension? (file:line)
> Q2. How are cart-scoped endpoints secured today (which security config, which roles)?
> Q3. Where does any express-checkout or checkout-eligibility logic live currently?
> Q4. How are response DTOs defined and mapped (beans.xml, populators, converters)?
> Q5. How do existing cart endpoints resolve {cartId} for the session user?
> Q6. What integration test patterns exist for OCC endpoints in this repo?

Claude asks: *"Any questions to add or remove?"* You add one ("Q7: do we version OCC
endpoints — v2 only?") and move on. It prints:

```
Next: /cq:2_research working-docs/CMRC-1234/ — run in a FRESH session.
```

## 2. Stage 2 — Research (~10 min, agent works, you get coffee)

New session (`/clear` or new terminal):

```
> /cq:2_research working-docs/CMRC-1234/
```

First run in this repo, so the **build adapter** kicks in first:

> "I detected a CCv2 layout (`core-customize/manifest.json`, Commerce 2211.45), gradle
> wrapper with y-tasks, custom extensions: `acmecore`, `acmefacades`, `acmewebservices`,
> Spartacus storefront present. Verb table: BUILD=`./gradlew ybuild`, UNIT_TEST=… — confirm?"

You confirm; it writes `working-docs/config.json` (commit it — teammates skip this step).

Then layered subagents answer the questions **without ever seeing ticket.md**, producing
**`research.md`** (excerpt):

> **Q1:** Controllers live in `acmewebservices/web/src/.../v2/`. Registered via
> component-scan in `v2-web-spring.xml:38`. Pattern: extend nothing, annotate
> `@RestController @RequestMapping("/{baseSiteId}/users/{userId}/carts")` —
> see `AcmeCartsController.java:41`.
> **Q3:** `ExpressCheckoutEvaluationStrategy` exists at `acmecore/.../impl/DefaultExpressCheckoutEvaluationStrategy.java:27`,
> used only by `AcmeCheckoutFacade`. Returns boolean, no reasons.
> **Q4:** DTOs in `acmewebservices-beans.xml`; converters follow populator pattern,
> e.g. `CartEligibilityPopulator` does not exist — nearest example `CartVoucherPopulator.java:19`.
> **Q6:** OCC integration tests use `@NeedsEmbeddedServer` + Spock, see
> `AcmeCartsControllerIntegrationTest.groovy:15`.

## 3. Stage 3 — Design (★ GATE 1, ~10 min of your attention)

Fresh session:

```
> /cq:3_design working-docs/CMRC-1234/
```

Claude **must ask before writing anything**:

> **Q1 — Eligibility reasons:** strategy returns boolean only. (A) Extend the strategy
> interface to return a result object with reasons *(recommended — single source of
> truth)*, (B) compute reasons in a new facade method, (C) boolean only, drop reasons
> from AC.
> **Q2 — Endpoint shape:** (A) new sub-resource `/express-eligibility` on carts
> *(recommended, matches Q5 pattern)*, (B) field on existing cart DTO via `fields=FULL`.
> **Q3 — ASSUMPTION-2 (auth-only) holds per security config (`v2-web-spring.xml:74`),
> but anonymous carts hit this controller today. Reject with 401 or evaluate anyway?**

You answer A, A, "401 — confirmed." Claude writes **`design.md`** (~150 lines): current
state, end state, the three decisions with rationale, confirmed assumptions, and:

> **Success criteria — Automated:** UNIT_TEST on `acmecore` + `acmewebservices` green;
> new integration test: 200 + reasons for eligible cart, 200 + `eligible:false` for
> pre-order cart, 401 anonymous.
> **Manual:** Postman call against local server; Spartacus unaffected (no consumer yet).

You approve. *This 10 minutes is the highest-leverage review you'll do on this ticket.*

## 4. Stage 4 — Structure (★ GATE 2, ~5 min)

```
> /cq:4_structure working-docs/CMRC-1234/
```

**`structure.md`** — vertical slices, signatures only:

> **Slice 1 — Domain result:** `ExpressEligibilityResult` bean (`acmecore-beans.xml`),
> widen `ExpressCheckoutEvaluationStrategy#evaluate(CartModel): ExpressEligibilityResult`,
> update sole caller. Checkpoint: BUILD + UNIT_TEST(acmecore).
> **Slice 2 — Facade + DTO:** `AcmeCartFacade#getExpressEligibility(String cartId)`,
> `ExpressEligibilityWsDTO` + populator. Checkpoint: BUILD + UNIT_TEST(acmefacades).
> **Slice 3 — Controller + security + test:** `@GetMapping("/{cartId}/express-eligibility")`
> in `AcmeCartsController`, security rule, Spock integration test (3 cases from design).
> Checkpoint: BUILD + INTEGRATION_TEST(acmewebservices).

You skim, approve. Each slice is independently shippable and testable — no "all DTOs
first" layering.

## 5. Stage 5 — Plan (spot-check, ~2 min)

```
> /cq:5_plan working-docs/CMRC-1234/
```

`plan.md`: ~12 checkboxed tasks with exact paths and resolved commands
(`./gradlew yunittests -Dtestclasses.extensions=acmecore` etc.), final task =
flow documentation. Plan is also posted as a comment on CMRC-1234 — no sub-tasks created.
You spot-check paths, done.

## 6. Stage 6 — Implement (your choice)

```
> /cq:6_implement working-docs/CMRC-1234/ mode=claude
```

`mode=claude`: one subagent per slice; implement → run checkpoint → fix on red → commit:

```
✓ Slice 1  feat(CMRC-1234): eligibility result on evaluation strategy   [yunittests green]
✓ Slice 2  feat(CMRC-1234): cart facade express-eligibility + WsDTO     [yunittests green]
✓ Slice 3  feat(CMRC-1234): OCC express-eligibility endpoint + tests    [yintegrationtests green]
```

Prefer to write Slice 3 yourself? `mode=dev` — Claude answers questions from research.md
and runs verification when you ask; checkboxes track either way. Laptop dies mid-run?
Re-invoke; it resumes from the first unchecked box.

## 7. Stage 7 — Validate & Ship (★ GATE 3, ~10 min)

```
> /cq:7_validate working-docs/CMRC-1234/
```

Claude re-runs every automated criterion from design.md verbatim, records evidence in
`validation.md`, lists the manual Postman check for you, then:

> Diff: 9 files changed, +412 −18 (acmecore 3, acmefacades 3, acmewebservices 3).
> **Have you read this diff and do you own it?**

You read it (you reviewed design and structure, so nothing surprises you), say yes.
Claude opens the PR — description grounded in design.md with rejected alternatives and
reviewer verification steps — comments on CMRC-1234 with the PR link, transitions the
ticket to In Review, and appends a 5-line retrospective to validation.md.

---

## Total developer attention

| Stage | Your time |
|---|---|
| 1 Ticket | 3 min skim |
| 3 Design | 10 min Q&A + review ★ |
| 4 Structure | 5 min review ★ |
| 5 Plan | 2 min spot-check |
| 7 Validate | 10 min diff read ★ |
| **Total** | **~30 min** on a ticket that previously cost an hour of bad-PR review or agent babysitting |

## If this ticket had been different

Same command, different tier — that's all a developer has to remember:

- One-line typo in a populator → `/cq:go "fix typo in CartVoucherPopulator" trivial`
  — direct fix, verification, diff. No artifacts.
- Known NPE in the strategy → `/cq:go CMRC-1301 simple` — 1-page brief.md (problem,
  assumptions, success criteria, checklist), you confirm it, implement, validate-lite.
- Cross-extension pricing migration → `/cq:go CMRC-1400 comprehensive` — everything
  above plus worktree isolation, mandatory integration tests per slice, and design.md/
  structure.md go to the team for review before planning starts.

Mis-tiered? Say "this is bigger than we thought" — Claude promotes the tier and runs
the skipped stages with the artifacts already produced.
