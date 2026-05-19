#!/usr/bin/env bash
# =============================================================================
# test-unit.sh — Run unit tests for custom extensions via direct ant
#
# Why direct ant, not `./gradlew yunittests`:
# The sap.commerce.build Gradle plugin (5.0.2) silently IGNORES the
# -Dtestclasses.extensions filter — `./gradlew yunittests -Dtestclasses.extensions=...`
# runs the full ~11000-test platform suite regardless. Direct ant honors
# the filter and runs only the requested extensions' tests.
#
# Usage:
#   ./scripts/test-unit.sh                    # default: coremcp,sampledatamcp
#   ./scripts/test-unit.sh coremcp            # one extension
#   ./scripts/test-unit.sh coremcp,otherext   # explicit list
#
# Reports:
#   core-customize/hybris/temp/hybris/junit/TEST-*.xml  (per-class)
#   core-customize/hybris/log/junit/index.html          (html summary)
# =============================================================================

set -eo pipefail
# Note: -u omitted because platform's setantenv.sh references some unset shell vars.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_CUSTOMIZE="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS="${1:-coremcp,sampledatamcp}"

cd "$CORE_CUSTOMIZE/hybris/bin/platform"
# shellcheck disable=SC1091
source ./setantenv.sh > /dev/null

echo "Running unit tests for extensions: $EXTENSIONS"
ant unittests \
  -Dtestclasses.extensions="$EXTENSIONS" \
  -Dtestclasses.annotations=unittests
