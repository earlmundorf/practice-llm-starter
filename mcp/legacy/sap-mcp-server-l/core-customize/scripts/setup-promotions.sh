#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GROOVY_FILE="$SCRIPT_DIR/../hybris/bin/custom/sampledatamcp/resources/sampledatamcp/promotions/setup-promotions.groovy"

if [ ! -f "$GROOVY_FILE" ]; then
    echo "ERROR: Groovy file not found: $GROOVY_FILE"
    echo "Is the sampledatamcp extension installed?"
    exit 1
fi

exec "$SCRIPT_DIR/../gradlew" groovy -Pfile="$GROOVY_FILE" -Pcommit=true
