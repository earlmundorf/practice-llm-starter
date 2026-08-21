# Stage 6 — Implement

**Input:** `structure.md` + `plan.md`. **Mode is explicit:** `mode=dev` or `mode=claude`.
If not given, ask — never assume.

## mode=dev — developer writes the code

1. Act as pair: answer questions grounded in `research.md`/`design.md`, review diffs on
   request, run verification commands when asked.
2. After each slice the developer completes, update `plan.md` checkboxes and confirm the
   slice checkpoint passed.
3. Never write implementation code unless the developer asks for a specific task.

## mode=claude — Claude executes the plan

1. Work slice by slice, in order. One fresh subagent per slice; it reads only its slice's
   tasks, relevant research summary, and prior slice outputs.
2. For each task: implement → run the task's verification (verbs resolved via
   `working-docs/config.json`; `MANUAL:` verbs mean print instructions and wait for the
   developer to confirm) → if it fails, diagnose and fix, re-verify; **never skip or
   proceed on red**.
3. **Verify the check actually ran.** A pass with zero tests executed, zero files compiled,
   or a skipped suite is not a pass — read the output, confirm the real count, and treat
   "nothing ran" as red. Config `_notes` may record stack-specific traps here.
4. Commit once per completed slice (conventional message referencing the ticket key).
   Check off tasks in `plan.md` as completed.
5. If the plan can't be followed (expected vs. actual mismatch), STOP. Present the
   mismatch and ask. Fundamental issue → route back: `Re-run /cq:5_plan (or 3_design).`
6. Optional: offer `git worktree` isolation before starting if the branch is shared.

## Both modes

- Resume support: on start, read checkboxes; trust completed checkmarks, start at the
  first unchecked task.
- Never push to remote, deploy, or modify any path in `protectedPaths`
  (`working-docs/config.json`); never commit `.env` or secrets.
- End by printing: `Next: /cq:7_validate working-docs/<TICKET-KEY>/ — run in a FRESH session.`

## Grounding — no speculation

Write for a human reader first; keep it useful to the tooling by keeping it true.
- **Only verified facts.** Every claim traces to something you actually read — the ticket text, the code, or a command's output. Anchor code facts with `file:line`.
- **Unknown stays unknown.** Can't confirm it? Write it as an open question or mark it `unconfirmed` and clarify with the developer — never fill the gap with a plausible guess, and never infer intent or motive.
- **No editorializing, no padding.** Don't add tangential detail "for completeness"; unverified extras are what mislead later stages and seed hallucinations. Comprehensive on what the work needs, silent on what it doesn't.
