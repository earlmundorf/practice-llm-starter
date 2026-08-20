# Stage 1 — Ticket & Questions

**Input:** Jira key (fetch via Jira MCP), ticket text, or a one-line task description.
If `jira.mode=manual` (or the MCP fetch fails): say so plainly and ask the developer to
paste the ticket — title, description, acceptance criteria, and any comments worth
keeping. Record `source: manual paste` in `ticket.md`. A failed MCP call must degrade
to paste, never block the workflow.
**Local ticket store:** before MCP/paste, check `tickets/active/` — if a file matches the
key/slug (e.g. `tickets/active/<KEY>*.md`), read it as the ticket source (it wins over MCP
and paste) and remember its path for stage 7. If `tickets/` doesn't exist, ignore this.
**Output:** `working-docs/<TICKET-KEY>/ticket.md` and `questions.md`.

## Instructions

0. **Load prior findings.** List the skill's `findings/*.md` (skip README/TEMPLATE). Read
   any whose `applies_to.area` or `ticket_type` plausibly matches this ticket — they carry
   what earlier tickets learned about this codebase and these stages. Let them inform the
   questions in step 3.
1. Create the task directory `working-docs/<TICKET-KEY>/` (e.g., `working-docs/CMRC-1234/`).
   No ticket number? Use a short kebab slug: `working-docs/cart-rate-limit/`.
2. Write `ticket.md`: problem statement in your own words, business context, draft
   assumptions (label each ASSUMPTION-n), draft success criteria taken from the ticket's
   acceptance criteria, explicit out-of-scope list.
3. Write `questions.md`: 8-15 **neutral** research questions that force coverage of every
   codebase area the ticket could touch. Questions describe what to find, never what to
   build. Bad: "How should we add the new discount type?" Good: "How are discount types
   defined, registered, and evaluated today? (file:line)"
4. Cover the Commerce layers explicitly where relevant: extensions/dependencies, type
   system (items.xml), service layer/Spring, OCC/storefront, impex/data, configuration.
5. If the ticket is trivial (<3 files, one sentence), say so and recommend skipping QRSPI.
6. Show the developer both files. Ask only: "Any questions to add or remove?"
7. End by printing: `Next: /cq:2_research working-docs/<TICKET-KEY>/ — run in a FRESH session.`

## Grounding — no speculation

Write for a human reader first; keep it useful to the tooling by keeping it true.
- **Only verified facts.** Every claim traces to something you actually read — the ticket text, the code, or a command's output. Anchor code facts with `file:line`.
- **Unknown stays unknown.** Can't confirm it? Write it as an open question or mark it `unconfirmed` and clarify with the developer — never fill the gap with a plausible guess, and never infer intent or motive.
- **No editorializing, no padding.** Don't add tangential detail "for completeness"; unverified extras are what mislead later stages and seed hallucinations. Comprehensive on what the work needs, silent on what it doesn't.

## Do not

- Propose solutions or designs.
- Let `questions.md` leak the intended change — stage 2 must stay unbiased.
