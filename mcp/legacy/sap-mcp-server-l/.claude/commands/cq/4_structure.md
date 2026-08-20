# Stage 4 — Structure (DEV GATE 2)

**Input:** `design.md` (+ `research.md` for file references).
**Output:** `structure.md` (~2 pages max). Design = where we're going; this = how we get there.

## Instructions

1. Break the work into **vertical slices** — each slice delivers one testable capability
   end to end (e.g., Slice 1: items.xml type + service stub + unit test; Slice 2: business
   logic + impex; Slice 3: OCC endpoint + integration test). Never horizontal layers
   ("all type system, then all services").
2. For each slice specify: goal (one sentence), new/changed signatures and types — like a
   C header, not implementations — files touched (paths), and a **checkpoint**: the
   verification VERBS (e.g., BUILD + UNIT_TEST) that prove the slice works, per the
   change-type mapping in `working-docs/config.json`. Verbs, not literal commands.
3. Order slices by dependency; flag any that could run in parallel worktrees.
4. Keep it skimmable: a developer should review this in under 10 minutes.
5. If structuring exposes a design flaw, stop and route back: `Re-run /cq:3_design.`
6. Present for approval; iterate until approved.
7. End by printing: `Next: /cq:5_plan working-docs/<TICKET-KEY>/ — run in a FRESH session.`

## Grounding — no speculation

Write for a human reader first; keep it useful to the tooling by keeping it true.
- **Only verified facts.** Every claim traces to something you actually read — the ticket text, the code, or a command's output. Anchor code facts with `file:line`.
- **Unknown stays unknown.** Can't confirm it? Write it as an open question or mark it `unconfirmed` and clarify with the developer — never fill the gap with a plausible guess, and never infer intent or motive.
- **No editorializing, no padding.** Don't add tangential detail "for completeness"; unverified extras are what mislead later stages and seed hallucinations. Comprehensive on what the work needs, silent on what it doesn't.

## Do not

- Write implementation code or full method bodies.
- Create a slice without an automated checkpoint.
- Exceed 2 pages — push detail down to stage 5.
