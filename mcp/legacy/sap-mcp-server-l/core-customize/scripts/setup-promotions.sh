#!/usr/bin/env bash
# Creates the 5 ThinkShop promotion rules + 2 coupons via the model-service
# (groovy through HAC). Idempotent — safe to re-run.
# Also imports the catalog→rule-engine context mapping the platform doesn't
# auto-create for our custom catalog.
# After this, publish to Drools with:
#   ./gradlew groovy -Pfile=scripts/publish-promotions.groovy -Pcommit=true
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GROOVY="$SCRIPT_DIR/../hybris/bin/custom/sampledatamcp/resources/sampledatamcp/promotions/setup-promotions.groovy"
IMPEX_CTX="$SCRIPT_DIR/../hybris/bin/custom/sampledatamcp/resources/sampledatamcp/promotions/catalog-rule-context.impex"

if [ ! -f "$GROOVY" ]; then
    echo "ERROR: groovy file not found: $GROOVY" >&2
    exit 1
fi

if [ -f "$IMPEX_CTX" ]; then
    "$SCRIPT_DIR/../gradlew" impex -Pfile="$IMPEX_CTX"
fi

exec "$SCRIPT_DIR/../gradlew" groovy -Pfile="$GROOVY" -Pcommit=true
