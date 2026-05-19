#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/../gradlew" groovy -Pfile="$SCRIPT_DIR/index-solr.groovy" -Pcommit=true
