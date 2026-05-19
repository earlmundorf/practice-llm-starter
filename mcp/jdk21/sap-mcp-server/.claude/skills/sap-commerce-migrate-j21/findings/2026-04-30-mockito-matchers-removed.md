---
date: 2026-04-30
project: upgrade-21-mcp-server-g
phase: B (surfaced at B.2b)
applies_to:
  java_from: 17
  spring_from: 5.x
  commerce_from: 2211.50
kind: gap
status: promoted
promoted_commit: 4f4f247
related_refs:
  - scripts/plan-template.md (Phase B.2)
  - references/decision-tree.md (Branch 2 — OpenRewrite scope)
  - references/sap-docs/02-openrewrite-recipes.md ("What OpenRewrite does NOT cover")
promotion_target: scripts/plan-template.md Phase B.2 — add a grep line item for `org.mockito.Matchers`; references/decision-tree.md Branch 2 — explicitly note Mockito is outside OpenRewrite's recipe scope
---

## What happened

During Phase B.2 on the manual-sweep path, after `javax.*` → `jakarta.*` and `@Required` cleanup, the final compile residue was:

```
CartToolHandlersTest.java:7: error: cannot find symbol
  import static org.mockito.Matchers.anyLong;
CartToolHandlersTest.java:7: error: static import only from classes and interfaces
CartToolHandlersTest.java:8: error: cannot find symbol
  import static org.mockito.Matchers.anyString;
CheckoutToolHandlersTest.java:5: error: cannot find symbol
  import static org.mockito.Matchers.eq;
```

`org.mockito.Matchers` was deprecated in Mockito 2.x (renamed to `org.mockito.ArgumentMatchers`) and **removed entirely in Mockito 3.x**. SAP Commerce 2211-jdk21.x ships with a Mockito dependency that no longer provides the old class. Any test code written against the older Mockito API will fail to compile.

Fix is trivial: rename the import package from `org.mockito.Matchers` → `org.mockito.ArgumentMatchers`. All method signatures (`anyLong()`, `anyString()`, `eq()`, etc.) are unchanged — it's a pure package move.

## Context

- This is **not in the OpenRewrite recipe scope**. SAP Note 3618495's recipe `com.sap.cx.rewrite.java.SelectiveCommerceCloudFrameworkUpdate` covers SAP-specific changes (javax, `@Required`, Commons, `HttpPutFormContentFilter`, Spring 5→6 mechanicals). Third-party library migrations — including Mockito — are explicitly out of scope (see the skill's `references/sap-docs/02-openrewrite-recipes.md` "What OpenRewrite does NOT cover" list).
- So projects using Mockito 1.x/2.x APIs will hit this residue regardless of whether they run OpenRewrite or do a manual sweep.
- The residue surfaces late — it's usually the last compile error to disappear on the manual-sweep path, because most codebases have far more `javax` and `@Required` usage than Mockito usage.
- Likely pattern for next project: 1–5 test files affected, 1–3 imports each. Mechanical fix, total fix time <2 min once spotted.

## The SAP-doc gap (if applicable)

The skill's `references/additional-changes.md` doesn't list third-party test-library migrations. The "what breaks at 2211-jdk21.x" list focuses on SAP-provided API changes. Mockito is a transitive test dependency whose version bump is implicit in the platform upgrade, so it's easy to overlook.

## The fix that worked

Per-file replacement: `import static org.mockito.Matchers.` → `import static org.mockito.ArgumentMatchers.` (replace_all). Zero method-signature changes required. Verified by `./gradlew yall` BUILD SUCCESSFUL after the edit.

## Why this generalizes

Every SAP Commerce project with Mockito-based JUnit tests that were written against Mockito <3.0 will hit this same residue on the 2211-jdk21.x bump. The fix is well-defined and mechanical — it belongs in Phase B.2's grep list so the next project finds it proactively instead of discovering it via a compile error.

## Promotion suggestion

1. **`scripts/plan-template.md`** Phase B.2 — add a grep line item to the "Patterns to search for" sub-bullets:

   ```markdown
   - [ ] `grep -rn "org\.mockito\.Matchers" core-customize/hybris/bin/custom --include="*.java"` → rename to `org.mockito.ArgumentMatchers` (Mockito 3.x removed the old class; method signatures identical).
   ```

2. **`references/decision-tree.md`** Branch 2 — at the end of "What OpenRewrite does NOT cover":

   > - **Mockito `Matchers` class** — `org.mockito.Matchers` was removed in Mockito 3.x. Rename imports to `org.mockito.ArgumentMatchers` in test code. Not covered by the SAP OpenRewrite recipes because it's a third-party library migration, not a platform API change.

3. **`references/additional-changes.md`** — add a new sub-section "Third-party test libraries" with the Mockito entry as the first item. Frame it as "platform bump carries transitive dependency bumps — watch for these" rather than as a platform-level change.

## Downstream follow-ups

- When we eventually run `./gradlew yunittests -Dtestclasses.extensions=coremcp` (Phase E), check whether any test bodies use old Mockito stubbing APIs (`when(...).thenReturn(...)` is unchanged; older `when(...).thenCallRealMethod().anyMore()`-style chaining may need adjustment). Deferred until we're at E.
