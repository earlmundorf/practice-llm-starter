#!/bin/bash
# build-server.sh — Build all extensions, then stop and restart the server.
# Outputs a single final line: "STARTED" or "ERROR: <reason>" for easy programmatic parsing.

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
WAIT_SECONDS=5
TIMEOUT_SECONDS=180

cd "$HYBRIS_DIR" || { echo "ERROR: Cannot cd to $HYBRIS_DIR" >&2; exit 1; }
. ./setantenv.sh > /dev/null 2>&1

# --- Build ---
echo "Building..."
ant build 2>&1 | tail -3
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "ERROR: ant build failed" >&2
    exit 1
fi

# --- Stop ---
echo "Stopping server..."
./hybrisserver.sh stop 2>&1 | grep -v "^$"

echo "Waiting ${WAIT_SECONDS}s before restart..."
sleep "$WAIT_SECONDS"

# --- Determine log file (today's date) ---
TODAY=$(date +%Y%m%d)
LOG_FILE="$LOG_DIR/console-${TODAY}.log"

# Record current log size so we only tail new lines
if [ -f "$LOG_FILE" ]; then
    START_LINE=$(wc -l < "$LOG_FILE")
else
    START_LINE=0
fi

# --- Start ---
echo "Starting server..."
./hybrisserver.sh start 2>&1 | grep -v "^$"

# --- Monitor logs for startup result ---
ELAPSED=0
while [ "$ELAPSED" -lt "$TIMEOUT_SECONDS" ]; do
    sleep 2
    ELAPSED=$((ELAPSED + 2))

    # Log file may not exist yet
    [ -f "$LOG_FILE" ] || continue

    # Check new lines only
    NEW_CONTENT=$(tail -n +"$((START_LINE + 1))" "$LOG_FILE" 2>/dev/null)

    if echo "$NEW_CONTENT" | grep -qE "Server startup in|<-- Wrapper Stopped|LifecycleException"; then
        # Success: Tomcat reports startup time
        if echo "$NEW_CONTENT" | grep -q "Server startup in"; then
            STARTUP_LINE=$(echo "$NEW_CONTENT" | grep "Server startup in" | tail -1)
            echo "$STARTUP_LINE"
            echo "STARTED"
            exit 0
        fi

        # Fatal: wrapper stopped unexpectedly
        if echo "$NEW_CONTENT" | grep -q "<-- Wrapper Stopped"; then
            echo "ERROR: Server wrapper stopped unexpectedly — check $LOG_FILE" >&2
            exit 1
        fi

        # Fatal: Tomcat lifecycle failure
        if echo "$NEW_CONTENT" | grep -q "LifecycleException"; then
            ERROR_LINE=$(echo "$NEW_CONTENT" | grep "LifecycleException" | head -1)
            echo "ERROR: $ERROR_LINE" >&2
            exit 1
        fi
    fi
done

echo "ERROR: Server did not start within ${TIMEOUT_SECONDS}s — check $LOG_FILE" >&2
exit 1
