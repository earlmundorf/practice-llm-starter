# Stage 4 — Structure (DEV GATE 2)

**Input:** `design.md` (+ `research.md` for file references).
**Output:** `structure.md` (~2 pages max). Design = where we're going; this = how we get there.

## Instructions

1. Break the work into **vertical slices** — each slice delivers one testable capability
   end to end (e.g., Slice 1: api.ts method + types + typecheck; Slice 2: page/component wired to
   it with loading/error states; Slice 3: polish — empty states, dark mode, a11y). Never
   horizontal layers ("all types, then all components").
2. For each slice specify: goal (one sentence), new/changed signatures and types — like a
   C header, not implementations — files touched (paths), and a **checkpoint**: the
   verification VERBS (e.g., BUILD + UNIT_TEST) that prove the slice works, per the
   change-type mapping in `working-docs/config.json`. Verbs, not literal commands.
3. Order slices by dependency; flag any that could run in parallel worktrees.
4. Keep it skimmable: a developer should review this in under 10 minutes.
5. If structuring exposes a design flaw, stop and route back: `Re-run /cq:3_design.`
6. Present for approval; iterate until approved.
7. End by printing: `Next: /cq:5_plan working-docs/<TICKET-KEY>/ — run in a FRESH session.`

## Do not

- Write implementation code or full method bodies.
- Create a slice without an automated checkpoint.
- Exceed 2 pages — push detail down to stage 5.
