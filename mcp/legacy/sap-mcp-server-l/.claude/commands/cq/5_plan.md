# Stage 5 — Plan (tactical; spot-check only)

**Input:** all artifacts in the task directory.
**Output:** `plan.md` with checkboxes. Alignment already happened in stages 3-4 — the
developer only spot-checks this document.

## Instructions

1. Expand each slice from `structure.md` into ordered tasks. Each task:
   - `- [ ]` checkbox, imperative title
   - Exact file paths to create/modify
   - Specific change description (signatures, bean wiring, impex headers — not prose essays)
   - Verification: the slice checkpoint's verbs resolved to concrete commands from
     `working-docs/config.json` (or `MANUAL:` instructions where so configured)
2. Respect the repo's CLAUDE.md Critical Rules — they are authoritative over this list:
   interfaces + `impl/Default*`, alias bean pattern, DTO conventions (including any
   sanctioned exceptions the repo documents, e.g. ADR-recorded hand-written internal
   DTOs), extension registration in the repo's tracked config source, never touch
   `gensrc/`/platform/OOTB.
3. Final task is always **Documentation**: distill research into permanent flow docs
   (`context.md`, `components.md`, `diagram.md`).
4. Jira handling per `jira.mode` in config — never create Epics, Stories, or sub-tasks;
   the ticket the developer started with is the only Jira artifact:
   - `mcp`: attach the plan summary (and design/structure links) as a ticket comment.
   - `manual`: append the same comment as paste-ready text to
     `working-docs/<TICKET-KEY>/jira-updates.md` and tell the developer it's there.
   - `none`: skip.
5. Checkboxes are the resume mechanism — a fresh session reads them to continue.
6. End by printing:
   `Next: /cq:6_implement working-docs/<TICKET-KEY>/ mode=dev|claude — run in a FRESH session.`

## Grounding — no speculation

Write for a human reader first; keep it useful to the tooling by keeping it true.
- **Only verified facts.** Every claim traces to something you actually read — the ticket text, the code, or a command's output. Anchor code facts with `file:line`.
- **Unknown stays unknown.** Can't confirm it? Write it as an open question or mark it `unconfirmed` and clarify with the developer — never fill the gap with a plausible guess, and never infer intent or motive.
- **No editorializing, no padding.** Don't add tangential detail "for completeness"; unverified extras are what mislead later stages and seed hallucinations. Comprehensive on what the work needs, silent on what it doesn't.

## Do not

- Introduce tasks not traceable to a slice.
- Re-litigate design decisions; if a fundamental problem appears, route back to stage 3/4.
