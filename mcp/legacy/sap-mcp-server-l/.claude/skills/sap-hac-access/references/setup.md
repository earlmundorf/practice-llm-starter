# Project Setup

Follow this when the user asks to set up the skill, asks what it needs, asks about `hac.env`
or `project-knowledge/`, or when a task needs one of them and it does not exist yet.

Setup is idempotent and never destructive: create what is missing, leave what exists alone.

## What setup produces

At the **project root** (not inside the skill):

```
hac.env                                  # gitignored; credentials for named environments
project-knowledge/
├── README.md
├── sap-commerce/                        # this project's data model, catalogs, Solr, CMS
│   ├── README.md
│   ├── overview.md
│   ├── catalogs-and-sites.md
│   ├── types.md
│   └── solr-and-cms.md
└── hac/                                 # this project's HAC landscape + learnings
    ├── README.md
    ├── environments.md
    └── learnings.md
```

Sources live in this skill's `./templates/`. The skill itself stays portable — nothing
project-specific is ever written inside `.claude/skills/`.

Reproduce the template content with file-read/file-write tools rather than shell copies; this
skill's `allowed-tools` restricts Bash to the three HAC scripts.

## Choosing the project root

In a multi-root workspace, pick the root that owns the HAC work — usually the one holding
the Commerce platform, `core-customize/`, or the artifact being validated. If more than one
candidate exists, ask which root before writing anything. Do not treat the skill directory
as the project root.

## Steps

1. **Report before writing.** List what already exists and what you intend to create. If
   only a `hac.env` is needed for the immediate task, say so rather than creating everything.
2. **Create `project-knowledge/`** by copying `./templates/project-knowledge/` to the project
   root. Never overwrite an existing file: copy only the ones that are absent, and mention
   which were skipped.
3. **Create `hac.env`** by copying `./templates/hac.env.example` to `<project-root>/hac.env`.
   Leave the placeholders commented out — the user fills in real values themselves.
   - If `hac.env` already exists, do not touch it. Report which environment keys it defines
     (key names only, never values).
4. **Protect the secret.** Ensure `.gitignore` at the project root ignores `hac.env`. If the
   entry is missing, add it:

   ```
   # HAC credentials — never commit
   hac.env
   ```

   If there is no `.gitignore`, create one containing that entry. If `hac.env` is already
   tracked by git, say so plainly and recommend `git rm --cached hac.env`; do not run it
   without approval.
5. **Do not ask for passwords.** Never request, echo, or write a credential value. Tell the
   user to edit `hac.env` in their editor. Local HAC needs no credentials at all — the
   scripts default to `admin`/`nimda` at `https://localhost:9002`.
6. **Offer to seed, don't invent.** The templates are `TBD` placeholders on purpose. Offer to
   fill `project-knowledge/sap-commerce/` from verified sources — `items.xml`, `*.impex`,
   `localextensions.xml`, or read-only FlexibleSearch queries against local HAC. Do not
   populate a fact you have not confirmed; leave it `TBD` instead.
7. **Verify, if the user wants it.** With local Commerce running, a read-only check confirms
   the wiring end to end:

   ```
   ./scripts/hac-flexquery.sh --content "SELECT {pk}, {uid} FROM {BaseSite}" --max-count 10
   ```

   Zero rows or a login failure means setup is fine but the server is not — see
   [Troubleshooting](./troubleshooting.md).
8. **Summarize.** List files created, files skipped, whether `.gitignore` was updated, and the
   single next action for the user (usually: fill in `hac.env`, or nothing at all if only
   local HAC is in play).

## Partial setup

Do not insist on the full tree. Create the narrowest thing the situation needs:

| Situation | Create |
|---|---|
| User asks to "set up the HAC skill" | everything above |
| Task targets `dev`/`qa`/`uat`/`stage`, no `hac.env` | `hac.env` + `.gitignore` entry only |
| A project-specific fact is worth keeping | just the one `project-knowledge/` file it belongs in |
| Local-only HAC work | nothing; the defaults already work |

## Answering "what does this skill need?"

Answer directly, then offer to do it:

- **Local HAC:** nothing. `curl` and `python3` on `PATH`, local Commerce running on
  `https://localhost:9002`.
- **Named environments:** a `hac.env` at the project root with that environment's URL, user,
  and password.
- **Project-specific answers:** a `project-knowledge/` overlay. Optional — the skill works
  without it, it just answers generically.
