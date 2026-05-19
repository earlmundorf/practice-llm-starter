#!/usr/bin/env bash
# =============================================================================
# test-integration.sh — Run integration tests for custom extensions via direct ant
#
# Prerequisites (run ONCE):
#   - MySQL running (port 3306)
#   - junit tenant initialized:  ./scripts/junit-tenant-init.sh
#
# Why direct ant, not `./gradlew yintegrationtests`:
# Same reason as test-unit.sh — Gradle wrapper ignores the
# -Dtestclasses.extensions filter and runs the full platform suite.
#
# Usage:
#   ./scripts/test-integration.sh                    # default: coremcp,sampledatamcp
#   ./scripts/test-integration.sh coremcp            # one extension
#   ./scripts/test-integration.sh coremcp,otherext   # explicit list
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

echo "Running integration tests for extensions: $EXTENSIONS"
ant integrationtests \
  -Dtestclasses.extensions="$EXTENSIONS" \
  -Dtestclasses.annotations=integrationtests
