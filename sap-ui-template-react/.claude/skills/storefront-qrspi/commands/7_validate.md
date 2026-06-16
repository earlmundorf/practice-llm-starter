# Stage 7 — Validate & Ship (DEV GATE 3)

**Input:** `design.md` (success criteria), `plan.md` (checkboxes), the diff.
**Output:** `validation.md`, PR, Jira update.

## Instructions

1. Run every **Automated** success criterion from `design.md` verbatim. Record command,
   result, and evidence in `validation.md`. Any failure → stop, fix via stage 6, re-run.
2. List every **Manual** criterion with concrete steps (dev-server route, viewport/dark-mode,
   expected behavior) and who verifies. Do not claim manual criteria as passed.
3. Confirm all `plan.md` checkboxes are checked; explain any that aren't.
4. **Code ownership gate:** present the full diff summary (files + stats) and ask the
   developer to confirm: "Have you read this diff and do you own it?" Do not proceed to
   PR creation without an explicit yes. No exceptions.
5. Create the PR with a description grounded in `design.md`: problem, chosen design (and
   alternatives rejected), success criteria status, manual verification steps for the
   reviewer. Link the `working-docs/<TICKET-KEY>/` artifacts.
6. Jira per `jira.mode`: `mcp` → transition the ticket and comment with PR link +
   validation summary. `manual` → append the comment text and the target status
   (e.g., "move to In Review") to `working-docs/<TICKET-KEY>/jira-updates.md` and
   remind the developer to apply it. `none` → skip.
7. Close out: append a 5-line retrospective to `validation.md` — what went well, what went
   wrong, what to carry into the next ticket.
8. **Feed the findings loop.** If this ticket surfaced anything the workflow or a future
   ticket should know — a wrong/missing verb mapping, a research category the stages
   missed, a recurring codebase quirk, a tier mis-call — write a
   `findings/YYYY-MM-DD-{slug}.md` in the skill from `TEMPLATE.md`. Summarize new findings
   and propose which to **promote** into the stage commands / SKILL.md / config / repo
   CLAUDE.md (user-approved; mark promoted ones `status: promoted`). Nothing worth carrying
   forward? Say so and skip — don't manufacture findings.
9. **Retire the ticket file.** If this ticket came from `tickets/active/`, move that file to
   `tickets/completed/` so `active/` reflects the real remaining backlog.

## Do not

- Create a PR with failing automated criteria.
- Mark the ticket Done — that happens after human review/merge.
