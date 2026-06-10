# Stage 2 — Research (blind)

**Input:** `questions.md` ONLY. **You must not read `ticket.md`.** You are a documentarian:
facts with `file:line` references, no opinions, no recommendations.
**Output:** `research.md` (~300 lines max).

## Instructions

1. Read `questions.md`. Group questions by Commerce layer.
2. If `working-docs/config.json` is missing, run build-adapter detection per SKILL.md
   (layout, build system, verb table) and confirm with the developer before proceeding.
   Note "limited research mode" if OOTB modules are absent.
3. Dispatch one subagent per layer, fresh context each, scoped to its questions:
   - Layer 0 Extensions: dependency graph from extensioninfo.xml
   - Layer 1 Type system: relevant `*-items.xml`, custom types/relations
   - Layer 2 Service layer: `*-spring.xml`, beans, overrides, strategies
   - Layer 3 Storefront/OCC: only if `js-storefront/` or `**/web/` controllers relevant
   - Layer 4 ImpEx/data: `resources/impex/`, naming/ordering behavior
   Each subagent returns answers with file:line; you keep only the answers, not raw scans.
4. Assemble `research.md`: each question, its factual answer, file:line evidence,
   existing patterns observed (as facts: "X is done via Y in three places: …").
5. Mark unanswerable questions UNANSWERED with what was searched — do not guess.
6. End by printing: `Next: /cq:3_design working-docs/<TICKET-KEY>/ — run in a FRESH session.`

## Do not

- Read `ticket.md`, suggest improvements, critique code, or propose approaches.
- Exceed ~300 lines — link to files instead of pasting them.
