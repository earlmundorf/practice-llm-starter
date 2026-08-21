# tickets/

The local ticket store for this repo. One Markdown file per ticket. This is the easy,
source-agnostic way to feed work into the QRSPI workflow (`/cq:go`).

```
tickets/
├── active/      # not-yet-done tickets — /cq:go reads from here
└── completed/   # moved here when the ticket ships (stage 7)
```

## How to populate it (any of these)

- **Generate** — ask Claude: "generate tickets for <goal> in tickets/active". Each lands
  as a file using `TICKET-TEMPLATE.md`.
- **Paste** — drop a Jira/ADO/GitHub ticket's text into a new
  `active/<KEY>-<slug>.md`. Title + description is enough; acceptance criteria help.
- **Pull dynamically** — if the Atlassian or GitHub MCP connector is available, ask
  Claude to fetch issue `<KEY>`/`#<n>` into `active/`. (When no connector is available,
  paste — same result.)

## How a developer uses one

```
/cq:go CHAT-01            # the skill finds tickets/active/CHAT-01*.md and works it
/cq:go "one-line task"    # or just describe it — no file needed for quick work
```

`/cq:go` resolves its input in this order: a matching file in `tickets/active/` →
the Jira MCP (if `jira.mode=mcp`) → paste. So the local file always wins when present.

## Lifecycle

1. Ticket file sits in `active/` (any source above).
2. `/cq:go <KEY>` runs the workflow; QRSPI scratch lands in `working-docs/<KEY>/`
   (gitignored), the code change ships as a PR.
3. On validation/merge (stage 7), the ticket file moves to `completed/` so `active/`
   always shows the real backlog.

## Naming

`<KEY>-<short-slug>.md` where KEY is **`THINK-UI-###`** — e.g.
`THINK-UI-004-footer-team-attribution.md`. Every ticket in this repo uses that prefix with
a zero-padded three-digit number, incrementing across `active/` and `completed/` together.
Keep slugs short and specific.

(Tickets predating this convention use `KB-##`: `KB-01` and `KB-02` shipped and sit in
`completed/`. `KB-03` — footer policy links + `/about` — was dropped without being built,
so number 3 is retired. New tickets continue as `THINK-UI-004` onward.)

Tracked in git (this is the shared backlog); per-ticket QRSPI scratch in `working-docs/`
is not. Both `active/` and `completed/` keep a `.gitkeep` so the empty dirs persist.
