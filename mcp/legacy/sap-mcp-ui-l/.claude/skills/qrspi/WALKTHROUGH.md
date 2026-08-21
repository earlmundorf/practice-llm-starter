# A ticket, start to finish

A worked example of the `full` tier. Substitute your own stack — nothing below depends on
one, because every command and layer name comes from `working-docs/config.json`.

Ticket: **THINK-142 — "Shoppers can't tell which help articles are new."**

---

## 0. Setup (once per repo)

```bash
qrspi-kit/install.sh list                              # see the profiles
qrspi-kit/install.sh <profile> --target /path/to/repo  # skill + /cq:* + config seed
```

That writes `working-docs/config.json` from the profile — or, if one already exists, leaves
yours alone and drops the profile at `config.json.new` to diff. Then **edit the config so
its verbs are this repo's real commands**: that file is the only thing standing between the
seven stages and your toolchain.

Before working a ticket, confirm the verbs actually run — a checkpoint that can't execute is
discovered at stage 6 otherwise:

```bash
# whatever your config's build table names, e.g.
npx tsc --noEmit && npm run lint && npm run build
```

Open the repo in Claude Code. Typing `/cq` autocompletes `0_go … 7_validate`.

---

## `/cq:go THINK-142`

No tier given, so the skill recommends one:

> Three-ish files, a visible UI change, and a design choice (what counts as "new" — publish
> date? first-seen-per-user?). That's **full**, not simple. Confirm?

You confirm. It records the tier and chains stages 1 and 2.

## Stage 1 — ticket & questions

Reads `working-docs/findings/` first, and one matches on `area: api-types`:

> Loaded finding 2026-06-19-e2e-gate-runnability.md — e2e specs need the dev server up.

Writes `ticket.md` (problem in its own words, ASSUMPTION-1..3, draft success criteria,
out-of-scope) and `questions.md` — 11 neutral questions, covering every name in
`researchLayers`:

```
Q3  How are help articles fetched and typed today? (file:line)
Q7  Does any existing list surface a "new" or "updated" badge? How is it computed?
Q9  What does the API return for article dates, and in what timezone?
```

Note what's absent: no question asks "how should we add the badge?" Stage 2 must not learn
the intended change.

## Stage 2 — research (blind)

Fresh session. It reads `questions.md` and **not** `ticket.md`, then dispatches one
subagent per `researchLayers` entry. Output is facts with references:

```
Q7  ANSWERED. One badge exists: ProductCard.tsx:38 renders a "Sale" pill from
    product.priceRange (src/types/index.ts:112). Nothing computes recency anywhere.
Q9  ANSWERED. KnowledgeEntry.publishedAt is an ISO-8601 string, UTC
    (src/types/Knowledge.ts:14). No client-side date formatting helper exists.
```

Unanswerable questions get `UNANSWERED` plus what was searched. No opinions, no
recommendations — that's the next stage's job.

## Stage 3 — design ★ GATE

Presents questions **before** writing anything:

> Q1: "New" means — (A) `publishedAt` within 14 days, (B) unseen by this user
> (localStorage), (C) both. Recommend A: no storage, no auth coupling, matches how
> ProductCard already derives a badge from data it already has.

You pick A but say 7 days. It walks your three assumptions against research — ASSUMPTION-2
is contradicted at `src/types/Knowledge.ts:14` — then writes `design.md`, whose success
criteria split into Automated (verbs) and Manual (from
`manualVerificationSurfaces`: "dev-server route `/help`", "browser console").

## Stage 4 — structure ★ GATE

Vertical slices, shaped by `sliceExample`:

```
Slice 1  isNew() helper + unit-testable date logic     checkpoint: TYPECHECK, LINT
Slice 2  badge rendered in the help list               checkpoint: TYPECHECK, LINT, BUILD
Slice 3  e2e spec for the badge                        checkpoint: E2E_TEST
```

Checkpoints name **verbs**, never commands. Slice 3 is `E2E_TEST` because
`changeTypeVerbs` maps `tests|spec` to it.

## Stage 5 — plan

Each slice becomes ordered checkbox tasks with exact paths, exact signatures, and verbs
resolved to real commands from config. Final task is always Documentation. Then, per
`jira.mode: manual`, the plan summary lands in `jira-updates.md` paste-ready.

## Stage 6 — implement

`mode=claude`, one fresh subagent per slice, verify after every task:

> Slice 1 · task 2 · LINT → red. `isNew` shadows an import. Fixed, re-run → green.
> Slice 3 · E2E_TEST → 4 passed. (Not "0 passed" — a suite that ran nothing is red.)

One commit per slice. Checkboxes updated as it goes, so a fresh session can resume.

## Stage 7 — validate ★ GATE

Runs every Automated criterion verbatim and records the output. Lists Manual criteria with
concrete steps and **does not** claim them passed. Then the gate that matters:

> 4 files changed, +87 −3. Have you read this diff and do you own it?

Only after your yes does it open the PR. Then the retro, and the findings loop:

> New finding: `manualVerificationSurfaces` didn't mention the dark-mode variant, which
> the reviewer asked about. Promote to `working-docs/config.json`?

---

## The other tiers, in one line each

The walkthrough above is `full`. The same ticket at another tier:

- **trivial** — `/cq:go "fix the typo in the help empty-state" trivial` → edit, run the
  change type's verbs, show the diff, you own it. No workflow, no artifacts.
- **simple** — `/cq:go THINK-142 simple` → one-page `brief.md` (problem, assumptions,
  success criteria, task checklist; the assumptions *are* the gate) → implement → validate
  lite. A reasonable call here if the "what counts as new" question were already settled.
- **comprehensive** — `full` plus worktree isolation, every checkpoint verb per slice
  rather than the change-type subset, and `design.md` + `structure.md` shared for team
  review before stage 5 starts. For migrations, cross-cutting changes, unfamiliar areas.

Tier changes the ceremony, never the safety rails: verification verbs, the diff-ownership
gate, `protectedPaths`, and the boundary convention apply at all four. Promote mid-flight
when something turns out bigger than it looked — the artifacts are compatible, so the
skipped stages just run late.

## What made this work

- **Blind research.** Stage 2 never saw the intended change, so it documented what *is*.
- **Gates before code.** The 7-vs-14-day correction cost one sentence at stage 3. At stage
  6 it would have cost a slice.
- **Verbs, not commands.** Nothing above names a build tool. Point the config at a different
  stack and the same seven stages run unchanged.
- **Fresh context per stage.** Each stage reads only its declared inputs, so nothing drifts
  in from earlier speculation.
