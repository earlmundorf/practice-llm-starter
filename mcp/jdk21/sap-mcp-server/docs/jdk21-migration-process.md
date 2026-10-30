# The JDK 21 migration process

How the `sap-commerce-migrate-j21` skill works: what its two phases do, how the work
divides into nine gated phases, and exactly which files the process leaves behind.

Written against skill **v0.6.0**. This is a description of the process, not the process
itself — the authoritative files are in
[`.claude/skills/sap-commerce-migrate-j21/`](../.claude/skills/sap-commerce-migrate-j21/).

| | |
|---|---|
| Target line | `2211-jdk21.x` — target the latest patch at migration time |
| Validated on | `2211-jdk21.9` |
| JDK | SapMachine 21.0.11 |
| Build plugin | `sap.commerce.build` 5.0.2 |
| Gradle / Ant | 8.12 / 1.10.15 (platform-bundled) |

---

## 1. Done means five things

The opening premise is that compiling cleanly is necessary but not sufficient. Five
criteria define done, each with an operational test. Every phase gate verifies one of
them, and the migration isn't finished until all five pass.

| Criterion | Operational test |
|---|---|
| **System builds** | `./gradlew yall` succeeds, `startServer` reaches "Platform started", and the boot log has zero `SEVERE` entries |
| **Solr works** | Cores report healthy, indexed doc counts within ±0.5% of the pre-migration baseline per type, and the top-10 search queries return the same product IDs as legacy |
| **Code works** | Unit and integration tests pass, user journeys smoke-tested by hand (PDP, cart, checkout), and no `NoClassDefFoundError` / `ClassNotFoundException` at runtime |
| **Data matches** | Row counts for 5–10 critical types within tolerance, orphaned-type cleanup done without losing intended data, and a stale cart/order deserialization sample loads |
| **OCC testable** | Endpoint status codes as expected, response schemas diff cleanly against the Phase 0 baseline, Swagger hasn't drifted, and OAuth flows complete |

Relevance preservation and schema diffing are the two tests that catch what a green build
hides: search that still returns results but ranks them differently, and endpoints that
still return 200 with a quietly changed shape.

---

## 2. Two phases, with a stop between them

The process **defaults to planning**. Execution begins only when someone explicitly says
*execute*, *run*, or *apply* — and the plan phase ends by stopping and waiting for
approval rather than rolling on.

### Phase 1 — Plan (default)

1. Create `migration-docs/` — the skill's exclusive write target
2. Run `scripts/detect_state.sh <target-project>`
3. Capture intake (three modes, below)
4. Check blockers
5. Walk the decision tree
6. Consult the authoritative references
7. Write the plan from the phase guide, substituting every `{{PLACEHOLDER}}` from intake
8. Record what the skill wants to learn *from* this run
9. **Stop** and wait for approval

Produces `intake.md`, `migration-plan.md`, `supporting-skill-findings.md`.

### Phase 2 — Execute (on request)

1. Confirm the approved plan exists
2. Walk Phase B's residue catalog, sweep by sweep
3. Commit at each sweep boundary
4. Verify with a full build
5. Capture findings for the next project
6. Hand back at Phase C

Produces code changes, one commit per sweep, and new findings.

### Two hard stops

- **Production with no backup.** If intake section 4.1 selects that scenario, planning
  **halts** and returns "plan blocked". It will not continue until there is a verified
  backup mechanism.
- **Deprecated extensions.** These pause the plan until the project decides a replacement
  path — that decision has to precede migration, not follow it.

### Intake has three modes

`detect_state.sh` fills in what it can determine automatically, so nobody is asked for
facts a script already knows. For the rest:

| Mode | Behaviour |
|---|---|
| Q&A walkthrough | One question at a time, each answer approved before moving on. Slower, auditable |
| Batched | All remaining questions in one message, answered in one reply |
| Self-fill | You edit `migration-docs/intake.md` directly; the skill validates it |

Sections 1–4 are required and the sign-off must be checked before a plan can generate.

---

## 3. Nine phases, nine gates

The plan is a pipeline. Phases run 0 → H, and any that don't apply to the detected state
are dropped rather than carried as dead weight. Gates are numbered separately from phases
because they don't map one-to-one — Phase F alone clears three.

| Phase | Gate | What it covers |
|---|---|---|
| **0** | 0 | **Toolchain prep.** JDK, platform ZIPs, database preflight, and the OCC response baseline that Gate 7 later diffs against. Written in two variants — in-place and copy-to-new-repo — and only the one matching the chosen strategy is copied into the plan |
| **A** | 1 | **Build tooling.** Ant and Gradle changes, so the project builds under the new platform before any source is touched |
| **B** | 2 | **Spring 6 + Jakarta sweep.** Thirteen residue sweeps, grep-inventoried and edited to zero. The only phase the process drives itself |
| **C** | 3 | **Tomcat 10.** Servlet container move to 10.1 and the Jakarta EE 10 baseline it brings |
| **D** | 4 | **OAuth / Authorization Server.** The legacy `oauth2` extension is replaced by Spring Authorization Server. Historically the richest source of incidents — client validation, redirect URIs, PKCE, and the removed password grant all land here |
| **E** | *alongside D* | **Tests.** Test framework changes, including the Mockito `Matchers` removal and the junit tenant's type-system refresh |
| **F** | 5 · 6 · 7 | **Data, Solr and OCC.** The heaviest verification phase: data integrity, Solr counts and relevance, then OCC end-to-end against the Phase 0 baseline |
| **G** | 8 | **SmartEdit** — optional; skipped outright where SmartEdit isn't in play |
| **H** | *cross-cutting* | **JVM and language housekeeping.** Library bumps, JVM flags, language-level cleanup. Many items can run alongside Phase B |

**Commit cadence:** per step, not per phase. Each gate is tagged (`phase-0-complete`,
`phase-A-complete`, …). That keeps `git bisect` useful and keeps log entries aligned 1:1
with commits.

---

## 4. Inside Phase B

Every sweep is the same three-beat loop, and the exit condition is a number rather than a
judgement — the same grep that found the residue has to come back empty:

```
grep to inventory  →  edit to transform  →  grep again  →  zero matches = done
```

The sweeps run on the **already-upgraded platform state**, which is what makes the approach
cheap: no toolchain rollback, no 16 GB JVM, no login-gated jar downloads, no git-rename
dance. SAP's OpenRewrite recipe catalog is used as the *specification* for what residues
exist, and each recipe's scope becomes one grep-then-edit pass.

| Sweep | Residue |
|---|---|
| B.0 | Decision: sweeps or OpenRewrite |
| B.1 | Legacy `oauth2` extension replaced |
| B.2 | `javax.*` → `jakarta.*` imports |
| B.3 | `@Required` annotation removed |
| B.4 | Mockito `Matchers` removed |
| B.5 | `CommonsMultipartResolver` → `StandardServletMultipartResolver` |
| B.6 | `HttpPutFormContentFilter` → `FormContentFilter` |
| B.7 | `SocketUtils` removed |
| B.8 | `SequenceSizeReleaseStrategy` → `SimpleSequenceSizeReleaseStrategy` |
| B.9 | Apache Commons Lang 2 / Commons Math 3 |
| B.10 | `RestTemplate` / `OAuth2RestTemplate` call sites |
| B.11 | Library version bumps in custom-declared dependencies |
| B.12 | Apache libraries + Ehcache residues |
| B.gate | Verification |

**The fork at B.0.** Five custom extensions is the dividing line. At or below it, hand
sweeps are faster to set up and easier to review. Above it, per-pattern grep volume stops
being manageable and OpenRewrite is the better tool — it stays fully documented as the
alternative rather than being treated as legacy.

---

## 5. What gets created

Four Markdown files, all inside `migration-docs/`, which the process treats as its
**exclusive write target** — a project's existing `docs/` is never touched. Line counts
below are from the completed run against this repository.

| File | Written | What it holds | Lines |
|---|---|---|---|
| `intake.md` | Plan step 3 | Auto-detected state plus the answers a plan can't be generated without — scope, strategy, source paths, data-persistence scenario, production characteristics, sign-off | 208 |
| `migration-plan.md` | Plan step 7 | The pipeline: starting state, blocking decisions, phases 0→H with per-step SAP citations and Go/No-Go gates, cross-cutting watchlist, sign-off | 362 |
| `migration-log.md` | During execution | The running record, one entry per commit | 293 |
| `supporting-skill-findings.md` | Plan step 8 | What the process wants to learn *from* this run — open questions to resolve during execution and feed back into the references | 36 |

Beyond the four files: a commit per step, a git tag per phase gate, and — in the skill's
own directory rather than the project's — a dated finding file for anything the references
didn't cover.

### What it reads from

The plan cites sources rather than asserting.

- **`references/sap-docs/`** — 14 Markdown mirrors of SAP Help pages, refreshable via
  `scripts/fetch_sap_docs.sh` against SAP's JSON API
- **`references/additional-changes.md`** — peer-authoritative; items taken verbatim from
  SAP's framework-update document that have no dedicated Help page
- **`references/oauth-migration-guide.md`** — curated companion: the ordered make-it-work
  path for the new Authorization Server, its 8 client-validation preconditions, an
  `OAuthClientDetails` cookbook, and SAML SSO impact
- **`references/upstream/`** — Spring 6.0/6.1/6.2 notes, for when a mirror is ambiguous and
  you need primary Spring wording. Flagged as Spring's words, not SAP guidance
- **`references/known-incidents.md`** — ten numbered field failures that plan steps link to
  as watchlist items
- **`references/decision-tree.md`**, **`phase-guide.md`**, **`verification-checklist.md`**,
  **`intake-template.md`** — the machinery the plan is generated from

---

## 6. What is automated, and what is not

Exactly one phase runs itself. Phase B's sweeps are driven end to end; **Phases C through G
stay human-driven** in this version, and the instruction is explicit — do not attempt to
drive Phase C onward automatically. At the end of Phase B the process surfaces the next
phase's first step and waits.

That boundary is a deliberate scope limit, not an unfinished edge. Also out of scope: Jira
and Confluence integration, and writing the extension-code rewrites that sweeps don't
handle — the process advises on those, someone else drives them.

### The self-improvement loop

Findings are the mechanism:

- **Start of a run** — all findings are read into context alongside the overview
- **During** — anything the references don't cover becomes
  `findings/YYYY-MM-DD-{slug}.md` with frontmatter, however small
- **End** — findings are summarized with a proposal for which should be **promoted** into
  `references/`. Promotion is always user-approved, so references never mutate silently; a
  promoted finding is marked `status: promoted` with its target recorded

Twenty-two findings have accumulated, nine of them promoted. **Unpromoted findings still
apply** — each carries an `applies_to:` block, and any whose conditions match the target
project get loaded even before anyone has decided where they belong.
