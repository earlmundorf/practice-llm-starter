# Reference profiles (generic starters)

These are **stack-neutral config starters imported verbatim from
[`rice-qrspi`](https://github.com/earlmundorf/rice-qrspi)** — the upstream, generic QRSPI. They
show the shape of a `working-docs/config.json` for common stacks and are here for **reference /
reuse**, not as part of the runnable SAP Commerce kit.

| Profile | Stack |
|---|---|
| `springboot.json` | Java Spring Boot (Maven default; Gradle swap in its `_notes`) |
| `fastapi.json` | Python FastAPI |
| `react-storefront-generic.json` | Generic React + Vite (rice's `storefront.json`, renamed to avoid clashing with this kit's tailored `ui/react-vite.json`) |

## Important — no matching skill ships here

This kit's skills are **stack-specialized**: `commerce-qrspi` (SAP Commerce backend) and
`storefront-qrspi` (storefront). These generic profiles pair with rice-qrspi's **single generic
`qrspi` skill**. So to actually *run* QRSPI on a Spring Boot / FastAPI / plain-React project, use
**[rice-qrspi](https://github.com/earlmundorf/rice-qrspi)** and drop the matching profile onto its
`working-docs/config.json`. Here they serve as templates and examples.

## This kit's own (runnable) profiles live with their sets

- Backend (SAP Commerce): `backend/working-docs/profiles/sap-commerce.json`
- Storefront: `ui/working-docs/profiles/composable-storefront.json` (Angular Spartacus) ·
  `ui/working-docs/profiles/react-vite.json` (React/Vite over OCC — tailored)

## Using any profile

Same mechanism everywhere: copy the closest profile over your project's `working-docs/config.json`,
then adjust (verbs, `<placeholders>`, `jira.mode`). Every VERB a stage references must exist in
`build`; a verb may be `MANUAL: <steps>`.
