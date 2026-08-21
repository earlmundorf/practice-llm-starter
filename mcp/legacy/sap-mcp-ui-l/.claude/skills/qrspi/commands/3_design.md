# Stage 3 — Design (interactive — DEV GATE 1)

**Input:** `ticket.md` + `research.md`.
**Output:** `design.md` (~200 lines max).

## Instructions

1. Read both inputs fully.
2. **You MUST present questions and wait for answers BEFORE writing any design document.**
   This is structural, not optional. Present 2-5 design decisions as options:
   `Q1: Discount evaluation — (A) a new pluggable strategy, (B) extend the existing default implementation, (C) intercept at the boundary. Recommend A because …`
3. Walk through the draft assumptions from `ticket.md` one by one: confirmed by research /
   contradicted (cite file:line) / still open. Open assumptions must be resolved by the
   developer or explicitly accepted as risks.
4. After answers, write `design.md` with exactly these sections:
   - **Current state** (facts from research, file:line)
   - **Desired end state** (observable behavior, not implementation)
   - **Design decisions** (each Q with chosen option and why)
   - **Confirmed assumptions & accepted risks**
   - **Success criteria** — split into *Automated* (verification verbs/commands, tests
     that must pass) and *Manual*. Manual checks come from
     `manualVerificationSurfaces` in `working-docs/config.json` — the places a human
     verifies this stack (admin console path, UI route/viewport, API endpoint call) — and
     each names who verifies.
   - **Out of scope**
5. If the developer's answers reveal missing research, stop and route back:
   `Re-run /cq:1_ticket to add questions, then /cq:2_research.`
6. Present `design.md` for approval; iterate until approved.
7. End by printing: `Next: /cq:4_structure working-docs/<TICKET-KEY>/ — run in a FRESH session.`

## Grounding — no speculation

Write for a human reader first; keep it useful to the tooling by keeping it true.
- **Only verified facts.** Every claim traces to something you actually read — the ticket text, the code, or a command's output. Anchor code facts with `file:line`.
- **Unknown stays unknown.** Can't confirm it? Write it as an open question or mark it `unconfirmed` and clarify with the developer — never fill the gap with a plausible guess, and never infer intent or motive.
- **No editorializing, no padding.** Don't add tangential detail "for completeness"; unverified extras are what mislead later stages and seed hallucinations. Comprehensive on what the work needs, silent on what it doesn't.

## Do not

- Write design.md before the Q&A exchange has happened.
- Include code, file-by-file changes, or task lists — that is stages 4-5.
- Pick a legacy pattern when research shows the team has a newer one; surface both and ask.
