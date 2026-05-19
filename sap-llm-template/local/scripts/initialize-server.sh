#!/bin/bash
# initialize-server.sh — DESTROYS ALL DATA: stop, clean all, initialize, start, monitor.
# This runs "ant clean all initialize" which wipes the database and reloads from ImpEx.
# Outputs status lines for each step and a final "STARTED" or "ERROR: <reason>".

set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HYBRIS_DIR="$SCRIPT_DIR/../../core-customize/hybris/bin/platform"
HYBRIS_CONFIG_DIR="$SCRIPT_DIR/../../core-customize/hybris/config"

if [ ! -d "$HYBRIS_DIR" ]; then
    echo "ERROR: $HYBRIS_DIR does not exist — run setup-local.sh first" >&2
    exit 1
fi

HYBRIS_DIR="$(cd "$HYBRIS_DIR" && pwd)"
HYBRIS_CONFIG_DIR="$(cd "$HYBRIS_CONFIG_DIR" && pwd)"
export HYBRIS_CONFIG_DIR

# Resolve LOG_DIR from wrapper.conf (HYBRIS_LOG_DIR) so it works with shared core installations
LOG_DIR=$(sed -n 's/.*HYBRIS_LOG_DIR="\([^"]*\)".*/\1/p' "$HYBRIS_DIR/tomcat/conf/wrapper.conf" 2>/dev/null)
if [ -z "$LOG_DIR" ]; then
    LOG_DIR="$SCRIPT_DIR/../../core-customize/hybris/log"
fi
LOG_DIR="$(cd "$LOG_DIR" 2>/dev/null && pwd || echo "$LOG_DIR")/tomcat"
mkdir -p "$LOG_DIR"
TIMEOUT_SECONDS=180

cd "$HYBRIS_DIR" || { echo "ERROR: Cannot cd to $HYBRIS_DIR" >&2; exit 1; }
. ./setantenv.sh > /dev/null 2>&1

# --- Confirmation prompt (skip with --force) ---
if [ "$1" != "--force" ]; then
    echo "WARNING: This will DESTROY ALL DATA (ant clean all initialize)."
    echo "   All database content, carts, orders, and users will be wiped."
    echo ""
    read -r -p "Type 'initialize' to confirm: " CONFIRM
    if [ "$CONFIRM" != "initialize" ]; then
        echo "Aborted."
        exit 1
    fi
fi

# --- Stop ---
echo "=== STOPPING SERVER ==="
./hybrisserver.sh stop 2>&1 | grep -v "^$"
if ./hybrisserver.sh status 2>&1 | grep -q "running"; then
    echo "WARNING: Server may still be running" >&2
else
    echo "Server stopped."
fi

# --- Clean All + Initialize ---
echo ""
echo "=== ANT CLEAN ALL ==="
ant clean all 2>&1 | grep -E "^\s*(BUILD|build|clean|Cleaning|all:|\[|Total time)" | grep -v "^\s*\[echo\]"
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "ERROR: ant clean all failed" >&2
    exit 1
fi
echo "Clean all complete."

echo ""
echo "=== ANT INITIALIZE ==="
INIT_OUTPUT=$(ant initialize -Dinput.template=develop 2>&1)
INIT_EXIT=$?
echo "$INIT_OUTPUT" | grep -iE "(BUILD|initializing|importing|creating|extension|essential|project|sample|solr|cronjob|Total time|FAILED|ERROR)" | grep -v "^\s*\[echo\]"
if [ "$INIT_EXIT" -ne 0 ]; then
    # ant initialize calls JmxClient.restartWrapper at the end to signal a running server.
    # When the server is stopped (our normal flow), this always fails with a NullPointerException.
    # Detect this specific non-fatal case and continue; any other failure is a real error.
    if echo "$INIT_OUTPUT" | grep -q "JmxClient.restartWrapper"; then
        echo "WARNING: JMX wrapper restart skipped (server not running — expected)."
    else
        echo "ERROR: ant initialize failed" >&2
        exit 1
    fi
fi
echo "Initialize complete."

# --- Start ---
echo ""
echo "=== STARTING SERVER ==="

TODAY=$(date +%Y%m%d)
LOG_FILE="$LOG_DIR/console-${TODAY}.log"

if [ -f "$LOG_FILE" ]; then
    START_LINE=$(wc -l < "$LOG_FILE")
else
    START_LINE=0
fi

./hybrisserver.sh start 2>&1 | grep -v "^$"

# --- Monitor logs for startup result ---
ELAPSED=0
while [ "$ELAPSED" -lt "$TIMEOUT_SECONDS" ]; do
    sleep 2
    ELAPSED=$((ELAPSED + 2))

    [ -f "$LOG_FILE" ] || continue

    NEW_CONTENT=$(tail -n +"$((START_LINE + 1))" "$LOG_FILE" 2>/dev/null)

    if echo "$NEW_CONTENT" | grep -qE "Server startup in|<-- Wrapper Stopped"; then
        if echo "$NEW_CONTENT" | grep -q "Server startup in"; then
            STARTUP_LINE=$(echo "$NEW_CONTENT" | grep "Server startup in" | tail -1)
            echo "$STARTUP_LINE"
            echo "STARTED"
            exit 0
        fi

        if echo "$NEW_CONTENT" | grep -q "<-- Wrapper Stopped"; then
            echo "ERROR: Server wrapper stopped unexpectedly — check $LOG_FILE" >&2
            exit 1
        fi
    fi
done

echo "ERROR: Server did not start within ${TIMEOUT_SECONDS}s — check $LOG_FILE" >&2
exit 1
