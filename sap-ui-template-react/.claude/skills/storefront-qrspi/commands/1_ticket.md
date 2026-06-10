# Stage 1 — Ticket & Questions

**Input:** Jira key (fetch via Jira MCP), ticket text, or a one-line task description.
If `jira.mode=manual` (or the MCP fetch fails): say so plainly and ask the developer to
paste the ticket — title, description, acceptance criteria, and any comments worth
keeping. Record `source: manual paste` in `ticket.md`. A failed MCP call must degrade
to paste, never block the workflow.
**Output:** `working-docs/<TICKET-KEY>/ticket.md` and `questions.md`.

## Instructions

1. Create the task directory `working-docs/<TICKET-KEY>/` (e.g., `working-docs/CMRC-1234/`).
   No ticket number? Use a short kebab slug: `working-docs/cart-rate-limit/`.
2. Write `ticket.md`: problem statement in your own words, business context, draft
   assumptions (label each ASSUMPTION-n), draft success criteria taken from the ticket's
   acceptance criteria, explicit out-of-scope list.
3. Write `questions.md`: 8-15 **neutral** research questions that force coverage of every
   codebase area the ticket could touch. Questions describe what to find, never what to
   build. Bad: "How should we add the new discount type?" Good: "How are discount types
   defined, registered, and evaluated today? (file:line)"
4. Cover every storefront area in `researchLayers` (from `working-docs/config.json`)
   explicitly where relevant, plus styling and build/env configuration.
5. If the ticket is trivial (<3 files, one sentence), say so and recommend skipping QRSPI.
6. Show the developer both files. Ask only: "Any questions to add or remove?"
7. End by printing: `Next: /cq:2_research working-docs/<TICKET-KEY>/ — run in a FRESH session.`

## Do not

- Propose solutions or designs.
- Let `questions.md` leak the intended change — stage 2 must stay unbiased.
