# SAP Commerce Best Practices

**Single source of truth: the `sap-best-practices` skill**
(`.claude/skills/sap-best-practices/SKILL.md`). It carries the full review
guidance — layer separation, the alias pattern, type system rules,
FlexibleSearch, OCC conventions, ImpEx, performance — plus calibrated examples
from this codebase. This file used to duplicate ~95% of it; for anything beyond
the quick checklist below, read that skill (or invoke it for an actual review).

## Quick checklist

- **Layers:** Controller → Facade → Service → DAO. Never skip; never call
  upward. Models never leak past a facade.
- **Wiring:** interface + `impl/Default*`; bean `id="defaultX"` + `<alias>` to
  `x`; `@Required` setters for XML-wired beans; `@Resource(name=...)` in
  controllers.
- **Type system:** typecodes in the allocated range; business keys
  `unique="true"`; Relations over collection attributes; never touch `gensrc/`.
- **FlexibleSearch:** parameterize everything; select `{pk}` only; paginate
  large results; mind catalog-version filtering.
- **DTOs:** OCC `*Data`/`*WsDTO` are generated from `*-beans.xml`. Internal
  protocol/LLM payloads in this project are deliberate hand-written Jackson
  classes (ADR 0005).
- **ImpEx:** idempotent `INSERT_UPDATE`, business keys, macros; this project
  uses numeric load-order prefixes (`essentialdata-NN-*`).
- **Performance:** no N+1 loops; `saveAll()` for batches; never `save()` inside
  an interceptor.
- **Project rules:** the repo's CLAUDE.md Critical Rules are authoritative.
