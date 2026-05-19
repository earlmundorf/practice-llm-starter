#!/usr/bin/env bash
# =============================================================================
# junit-tenant-init.sh — Initialize the junit tenant DB schema
#
# Required ONCE before any integration test run, AND any time *-items.xml
# changes (after a yall + yupdatesystem against master, run yunitupdate or
# this script again).
#
# yinitialize only initializes the master tenant. Without this script's
# `ant yunitinit`, every integration test fails with:
#
#     java.sql.SQLSyntaxErrorException: Table '<db>.junit_metainformations'
#     doesn't exist
#
# Usage:   ./scripts/junit-tenant-init.sh
# =============================================================================

set -eo pipefail
# Note: -u omitted because platform's setantenv.sh references some unset shell vars.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_CUSTOMIZE="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$CORE_CUSTOMIZE/hybris/bin/platform"
# shellcheck disable=SC1091
source ./setantenv.sh > /dev/null

echo "Initializing junit tenant (this takes ~2 min)..."
ant yunitinit
