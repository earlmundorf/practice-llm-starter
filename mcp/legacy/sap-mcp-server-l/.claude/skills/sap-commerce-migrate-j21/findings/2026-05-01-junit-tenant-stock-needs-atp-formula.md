---
date: 2026-05-01
project: sap-mcp-server-g
phase: E (integration tests)
applies_to:
  java_from: any
  spring_from: any
  commerce_from: any
  target: 2211-jdk21.x
  test_type: integration tests using warehousing extension's CommerceStockService
kind: gotcha
status: promoted-with-phase-guide
related_refs:
  - references/phase-guide.md (Phase E.3 — integration tests gotcha note)
  - findings/2026-04-30-yunittests-ignores-extension-filter.md (parent — Phase E canonicalization)
promotion_target: references/phase-guide.md (already added an inline note in E.3)
---

## What happened

After fixing the Phase E flow to use direct ant + `yunitinit` (per `findings/2026-04-30-yunittests-ignores-extension-filter.md`'s promotion), the `coremcp` integration test suite ran 21 tests with exactly one failure:

```
testStockLevelsMatchDemoData FAILED
  java.lang.AssertionError: Stock mismatch for LAPTOP_PRO_15 expected:<25> but was:<0>
```

20 other tests passed. Master-tenant catalog data (orders, customers, prices, base sites, warehouses) all loaded correctly. The single failure was specifically `commerceStockService.getStockLevelForProductAndBaseStore(product, store)` returning 0 even though `StockLevel` records existed with `available=25` and were linked to the product.

## Root cause

The integration test's fixture file (`testdata-thinkshop.impex`) was hand-derived from the master-tenant `essentialdata-infrastructure.impex` but DROPPED the `AtpFormula` block and the `defaultAtpFormula(code)` column on the `BaseStore` row. The original master-tenant version had:

```impex
INSERT_UPDATE AtpFormula ; code[unique = true] ; availability ; allocation ; reserved ; ...
                         ; thinkshop-atp-formula ; true ; true ; true ; ...

INSERT_UPDATE BaseStore ; uid ; ... ; defaultAtpFormula(code)
                        ; electronics ; ... ; thinkshop-atp-formula
```

The test fixture had only the BaseStore row, without `defaultAtpFormula`.

The warehousing extension's `CommerceStockService.getStockLevelForProductAndBaseStore` resolves stock through the BaseStore's configured AtpFormula. Without one, the calculation returns 0 (NOT null — the product exists, the warehouse exists, the StockLevel exists, but the formula needed to AGGREGATE them is missing).

## The fix

Two-part fix in the test fixture (`testdata-thinkshop.impex` or equivalent):

1. Add the `AtpFormula` row, mirroring whatever your master tenant uses. Minimal example:

   ```impex
   INSERT_UPDATE AtpFormula ; code[unique = true]    ; availability ; allocation ; cancellation ; increase ; reserved ; shrinkage ; wastage ; returned ; external
                            ; thinkshop-atp-formula  ; true         ; true       ; false        ; false    ; true     ; false     ; false   ; false    ; false
   ```

2. Add `defaultAtpFormula(code)` to the `BaseStore` INSERT_UPDATE columns:

   ```impex
   INSERT_UPDATE BaseStore ; uid ; ... ; warehouses(code) ; defaultAtpFormula(code)
                           ; electronics ; ... ; electronics-warehouse ; thinkshop-atp-formula
   ```

After this fix, `testStockLevelsMatchDemoData` passes (and all 21/21 tests green).

## Why this generalizes

ANY integration test that uses `commerceStockService.getStockLevelForProductAndBaseStore(...)` (or anything else from the warehousing extension that resolves stock by store) needs the AtpFormula chain in its test fixture. Projects often miss this because:

1. Master-tenant `essentialdata-*.impex` declares it once, and developers forget the junit tenant fixture is a separate file with no inheritance.
2. The error message says "Stock mismatch ... expected:<25> but was:<0>" — looks like a stock-data problem, not an AtpFormula problem. Easy to assume the `StockLevel` row didn't load.
3. Other 20 tests pass, so "the data is loading" — only this one test exercises the formula chain.

This is a Phase E gotcha, not a migration-introduced bug — a project that ran integration tests on the legacy stack would hit the same failure if the fixture was missing the formula. But Phase E is when most projects FIRST run integration tests, so the gotcha surfaces during the migration.

## Promotion (already done in skill)

`references/phase-guide.md` Phase E.3 has an inline note documenting the gotcha:

> **Common gotcha:** if your test ImpEx fixture sets up a `BaseStore` for warehousing/stock-related tests, it must include `defaultAtpFormula(code)` and an accompanying `AtpFormula` row. Without it, `commerceStockService.getStockLevelForProductAndBaseStore(product, store)` returns 0 even when `StockLevel` records exist.

No additional doc edit needed. Closing this finding as `status: promoted-with-phase-guide`.
