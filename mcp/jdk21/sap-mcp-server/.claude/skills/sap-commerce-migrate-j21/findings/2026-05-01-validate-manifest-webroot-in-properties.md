---
date: 2026-05-01
project: sap-mcp-server-g
phase: 0-inplace
applies_to:
  java_from: any
  spring_from: any
  commerce_from: any
  build_plugin: sap.commerce.build 5.0.2
  deployment: CCv2
kind: new-incident
status: unpromoted-and-unfixed
related_refs:
  - references/phase-guide.md (Phase 0 Step 0.4)
  - references/known-incidents.md
promotion_target: references/known-incidents.md (new entry "validateManifest E-017")
---

## What happened

After fixing E-012 (extension dir → name), `validateManifest` still reports:

```
ERROR E-017 @ useConfig.properties[0].location (hybris/config/local.properties)
Do not configure webroots in properties.
```

Offending lines in `dev-config/local.properties`:
```
webservices.webroot=/occ
oauth2.webroot=/authorizationserver
```

CCv2 expects webroots to be declared in `manifest.json` under `aspects.webapps[]` instead of in `*.properties`. The properties form works at runtime, but CCv2 deployment validation rejects it.

## Status

**Fix not yet applied.** This is a CCv2 deployment-validation issue, not a local-dev blocker — `bootstrapPlatform` succeeded and local builds will run. Phase 0 gate continues with E-017 outstanding; capture as a follow-up before any cloud deploy.

## The fix (proposed, not yet applied)

1. Remove `webservices.webroot=/occ` and `oauth2.webroot=/authorizationserver` from `dev-config/local.properties`.
2. Add `aspects` block to `core-customize/manifest.json`. Approximate shape (verify against current SAP `manifest.json` schema):

   ```json
   "aspects": [
     {
       "name": "backoffice",
       "webapps": [
         { "name": "hac", "contextPath": "/hac" }
       ]
     },
     {
       "name": "accstorefront",
       "webapps": [
         { "name": "ycommercewebservices", "contextPath": "/occ" },
         { "name": "oauth2", "contextPath": "/authorizationserver" }
       ]
     }
   ]
   ```

3. Re-run `setupConfig` + `validateManifest`.

## Context

- Local-dev impact: zero (runtime properties still apply).
- CCv2 deployment impact: `validateManifest` is the gate before pushing to cloud. Must fix before deployment.
- Project's CLAUDE.md documents OAuth2 + OCC paths but doesn't include the manifest-aspects shape.

## Why this generalizes

Same pattern as E-012 — older Commerce projects used properties for webroots; newer plugin validation rejects this. Will surface on any pre-CCv2-aspects project migrating to recent plugin versions.

## Promotion suggestion

1. Add to `references/known-incidents.md` as "validateManifest E-017: webroots in properties" with the move-to-aspects fix recipe.
2. Optionally add a Phase 0 step for "manifest aspects modernization" alongside the extension-dir fix.
