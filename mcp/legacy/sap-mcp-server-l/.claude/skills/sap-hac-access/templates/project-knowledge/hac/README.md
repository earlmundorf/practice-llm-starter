# project-knowledge/hac/

This project's HAC landscape and operational learnings. The `sap-hac-access` skill reads
here for HAC facts that are true of this project only.

| File | Holds |
|---|---|
| `environments.md` | Which environments exist, their HAC context path and Java line, who owns them |
| `learnings.md` | Operational gotchas discovered while running HAC against this project |

Credentials do **not** go here. URLs and usernames belong in `hac.env`; passwords belong
only in `hac.env`, which is gitignored. This folder is committed.
