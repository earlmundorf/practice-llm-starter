# project-knowledge/

Project-specific facts that the portable `sap-hac-access` skill reads but does not own.

The skill carries generic SAP Commerce and HAC knowledge. Anything true only of *this*
client, repo, or landscape belongs here instead, so the skill stays shareable and this
folder stays yours.

| Folder | Holds |
|---|---|
| `sap-commerce/` | This project's data model, catalogs, sites, Solr, CMS, extensions, business keys |
| `hac/` | This project's HAC landscape facts and hard-won operational learnings |

Rules of thumb:

- **No secrets.** URLs and usernames are fine; passwords and tokens live in `hac.env`,
  which is gitignored. Never paste a password into these files.
- **Facts, not guesses.** Write down what was verified, and how it was verified — a
  FlexibleSearch query, a file path, a HAC screen. Mark anything unconfirmed as such.
- **Commit this folder.** It is durable project documentation; the whole team benefits.
- **Keep it out of the skill.** Do not copy these files back into `.claude/skills/`, and do
  not include them when packaging or sharing the skill.
