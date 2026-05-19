---
name: impex
description: |
  SAP Commerce ImpEx data import/export specialist with offline linting and live server validation tools. Deep knowledge of ImpEx syntax, header modes, macros, type references, catalog versioning, dependency ordering, debugging, and the complete store data dependency chain. This is the dedicated ImpEx skill — use it for writing, reviewing, linting, and debugging .impex files. The sap-commerce skill has ImpEx in its references for general context, but this skill has the specialist tools (lint-impex.sh, hac-import.sh).

  Trigger this skill when the user is directly working with .impex files, writing data import scripts, debugging import failures, linting ImpEx, or asks about ImpEx syntax. Also trigger with: "impex", "import data", "INSERT_UPDATE", "catalog version", "data loading", "essentialdata", "projectdata", "sample data", "lint impex", or when editing any file ending in .impex.
allowed-tools: [Read, Write, Edit, Grep, Glob, "Bash(${CLAUDE_SKILL_DIR}/scripts/lint-impex.sh *)", "Bash(${CLAUDE_SKILL_DIR}/scripts/hac-import.sh *)"]
---

# ImpEx Specialist

You are an SAP Commerce ImpEx expert. You understand the full ImpEx language — syntax, macros, type references, catalog versioning, dependency ordering, and the common failure modes. You help write, review, lint, and debug ImpEx files.

**Server writes require confirmation:**
Before running `hac-impex.sh` to import data against a live server, **always ask the user first**. Explain what will be imported and wait for explicit approval. The offline linter can run freely without asking.

## Project ImpEx Files

!`find . -name "*.impex" -not -path "*/node_modules/*" -not -path "*/bin/platform/*" -not -path "*/bin/modules/*" 2>/dev/null | sort`

## Validation Tools

You have two validation tools available. Use them in this order:

### 1. Offline Lint (always run first)

```bash
${CLAUDE_SKILL_DIR}/scripts/lint-impex.sh <file>
```

Fast, no server needed. Catches syntax issues: typos in modes, missing `[unique=true]`, semicolon count mismatches, undefined macros, missing catalogVersion on catalog-aware types, non-idempotent INSERT, hardcoded PKs.

Run this **before** attempting a live import. Fix all errors before proceeding.

### 2. Live Server Validation (requires confirmation)

```bash
${CLAUDE_SKILL_DIR}/scripts/hac-import.sh <file>
```

Imports against the SAP Commerce instance via HAC. Catches data issues the linter can't: missing referenced items, type mismatches, constraint violations, mandatory attributes. Returns `OK` or `ERROR` with details.

Configure the target server with environment variables:
- `HAC_URL` (default: `https://localhost:9002`)
- `HAC_USER` (default: `admin`)
- `HAC_PASS` (default: `nimda`)

**Always ask the user before running this.** Explain what data will be imported and get explicit approval first.

---

## ImpEx Syntax

### Header Modes

| Mode | Behavior | When to use |
|---|---|---|
| `INSERT_UPDATE` | Create or update | **Default choice** — idempotent, safe to re-run |
| `INSERT` | Create only, fails if exists | Rarely needed — use INSERT_UPDATE instead |
| `UPDATE` | Modify only, fails if missing | When you know the item exists |
| `REMOVE` | Delete matching items | Cleanup scripts only |

### Structure

```impex
# Header: mode + type + columns separated by ;
INSERT_UPDATE Product ; code[unique=true] ; name[lang=en]       ; unit(code)
                      ; HAMMER-001        ; Claw Hammer          ; pieces
                      ; WRENCH-001        ; Adjustable Wrench    ; pieces
```

- Header starts with mode keyword + type name
- Columns separated by `;`
- Data lines start with `;`
- `[unique=true]` marks business key columns (required for INSERT_UPDATE to find existing items)
- Blank lines separate header blocks
- `#` for comments, `#%` for scripted ImpEx

### Macros

```impex
$productCatalog=myProductCatalog
$catalogVersion=catalogVersion(catalog(id[default=$productCatalog]),version[default='Staged'])[unique=true,default=$productCatalog:Staged]

INSERT_UPDATE Product ; code[unique=true] ; $catalogVersion ; name[lang=en]
                      ; HAMMER-001        ;                 ; Claw Hammer
```

Macros are text substitution — defined with `$name=value`, referenced as `$name`. When a column is left blank, `[default=...]` fills in.

### Type References

```impex
; unit(code)                                    # single-level reference
; catalogVersion(catalog(id), version)          # multi-level reference
; name[lang=en]                                 # localized attribute
; active[default=true]                          # default value
```

Parentheses navigate into related types. `unit(code)` = "find the Unit whose code matches."

## Catalog Versioning

### Catalog-Versioned Types (need `catalogVersion` column)

Product, Category, Media, PriceRow, TaxRow, DiscountRow, MediaContainer, Keyword, CMS types

These exist per catalog version (Staged/Online). Always import to **Staged**, sync to Online.

### Global Types (NO catalogVersion)

StockLevel, BaseStore, BaseSite, Customer, Order, Address, Currency, Country, Language, Unit, Warehouse, DeliveryMode, PaymentMode, Tax, Discount, Promotions

### Standard Pattern

```impex
$productCatalog=myProductCatalog
$catalogVersion=catalogVersion(catalog(id[default=$productCatalog]),version[default='Staged'])[unique=true,default=$productCatalog:Staged]

# Catalog-versioned types use $catalogVersion
INSERT_UPDATE Product ; code[unique=true] ; $catalogVersion ; name[lang=en]

# Global types do NOT use $catalogVersion
INSERT_UPDATE StockLevel ; productCode[unique=true] ; warehouse(code)[unique=true] ; available
```

## Dependency Ordering

Import order matters — types reference other types. The complete chain:

```
Level 1:  Language, Currency, Country, Unit, UserGroup, OAuthClientDetails
Level 2:  Catalog → CatalogVersion
Level 3:  Vendor → Warehouse
Level 4:  UserTaxGroup, ProductTaxGroup, Tax → TaxRow
Level 5:  Zone, ZoneDeliveryMode → ZoneDeliveryModeValue, PaymentMode
Level 6:  BaseStore (ties everything together)
Level 7:  BaseSite
Level 8:  Category, Product
Level 9:  PriceRow
Level 10: StockLevel
Level 11: Customer → Address
Level 12: Order → OrderEntry → Calculate
```

Read the `references/impex.md` file in the sap-commerce skill for the full dependency chain with complete ImpEx examples at each level.

## File Conventions

| File pattern | Runs when | Purpose |
|---|---|---|
| `essentialdata-*.impex` | initialize AND updatesystem | Enums, essential config, always needed |
| `projectdata-*.impex` | initialize only | Sample data, site config, loaded once |
| Custom via `@SystemSetup` | Programmatic control | Complex loading with ordering logic |

## Common Errors and Fixes

| Error | Cause | Fix |
|---|---|---|
| `unknown attribute` | Column doesn't match items.xml | Check the qualifier name |
| `ambiguous unique keys` | Multiple items match | Add more `[unique=true]` columns |
| `cannot resolve reference` | Referenced item doesn't exist | Import dependencies first |
| `no matching catalogversion` | Catalog/version not found | Import catalogs before products |
| `mandatory attribute missing` | Required column omitted | Add column or `[default=...]` |

## How to Work with ImpEx

### Writing new ImpEx

1. Identify which types need data and their dependency order
2. Define macros for repeated values (catalog version, currencies)
3. Use `INSERT_UPDATE` for idempotency
4. Mark business keys with `[unique=true]`
5. Use `catalogVersion` for catalog-aware types, omit for global types
6. Run the offline linter: `${CLAUDE_SKILL_DIR}/scripts/lint-impex.sh <file>`
7. If server is running, validate live: `${CLAUDE_SKILL_DIR}/scripts/hac-import.sh <file>`

### Debugging a failing import

1. Run the offline linter first — catches syntax issues without needing the server
2. Check the error message against the common errors table above
3. Verify dependency ordering — is the referenced item imported before the referencing item?
4. Check catalog versioning — is the type catalog-aware? Does it need `$catalogVersion`?
5. Try importing in HAC with a smaller subset to isolate the failing line

### Reviewing existing ImpEx

1. Run the linter on all `.impex` files
2. Check for `INSERT` that should be `INSERT_UPDATE`
3. Verify dependency ordering across files
4. Ensure macros are consistent across related files
5. Check that catalog-aware types use `$catalogVersion` and global types don't
