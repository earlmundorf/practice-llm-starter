#!/bin/bash
# =============================================================================
# hac-groovy.sh — Run Groovy scripts against HAC from the command line
#
# Usage:
#   ./hac-groovy.sh "return 'Hello World'"
#   ./hac-groovy.sh /path/to/script.groovy
#   echo "println 'hi'" | ./hac-groovy.sh -
#   ./hac-groovy.sh script.groovy --commit     # Persist changes
#
# Arguments:
#   $1  Groovy script string, file path, or "-" for stdin (required)
#   --commit  Persist changes (default: rollback mode)
#
# Environment variables (override defaults):
#   HAC_URL      Base URL (default: https://localhost:9002/hac)
#   HAC_USER     Username (default: admin)
#   HAC_PASS     Password (default: nimda)
#
# Requires: curl, python3
# =============================================================================

HAC="${HAC_URL:-https://localhost:9002}"
AUTH_USER="${HAC_USER:-admin}"
AUTH_PASS="${HAC_PASS:-nimda}"
COMMIT="false"

# Parse args
INPUT=""
for arg in "$@"; do
  if [ "$arg" = "--commit" ]; then
    COMMIT="true"
  elif [ -z "$INPUT" ]; then
    INPUT="$arg"
  fi
done

if [ -z "$INPUT" ]; then
  echo "Usage: $0 '<script>' | <file> | - [--commit]" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 \"return 'Hello World'\"" >&2
  echo "  $0 script.groovy" >&2
  echo "  $0 /tmp/my-script.groovy --commit" >&2
  echo "  echo \"println 'hi'\" | $0 -" >&2
  echo "" >&2
  echo "Options:" >&2
  echo "  --commit   Persist changes (default: rollback mode)" >&2
  echo "" >&2
  echo "Environment:" >&2
  echo "  HAC_URL=https://localhost:9002/hac  HAC_USER=admin  HAC_PASS=nimda" >&2
  exit 1
fi

# Resolve script from string, file, or stdin
if [ "$INPUT" = "-" ]; then
  SCRIPT=$(cat)
elif [ -f "$INPUT" ]; then
  SCRIPT=$(cat "$INPUT")
else
  SCRIPT="$INPUT"
fi

if [ -z "$SCRIPT" ]; then
  echo "ERROR: Empty script" >&2
  exit 1
fi

CJ=$(mktemp)
TMP=$(mktemp)
cleanup() { rm -f "$CJ" "$TMP"; }
trap cleanup EXIT

# 1. Get login page + CSRF token
curl -sk -L -c "$CJ" "$HAC/login" -o "$TMP" 2>/dev/null
CSRF=$(python3 -c "
import re, sys
try:
    html = open(sys.argv[1]).read()
    m = re.search(r'name=\"_csrf\"[^>]*value=\"([^\"]+)\"', html)
    print(m.group(1) if m else '')
except Exception: print('')
" "$TMP")

if [ -z "$CSRF" ]; then
  echo "ERROR: Could not reach HAC at $HAC — is the server running?" >&2
  exit 1
fi

# 2. Authenticate
curl -sk -b "$CJ" -c "$CJ" -X POST "$HAC/j_spring_security_check" \
  -d "j_username=$AUTH_USER&j_password=$AUTH_PASS&_csrf=$CSRF" -o /dev/null 2>/dev/null

# 3. Get scripting page for fresh CSRF token
curl -sk -b "$CJ" -c "$CJ" "$HAC/console/scripting/" -o "$TMP" 2>/dev/null
CSRF=$(python3 -c "
import re, sys
try:
    html = open(sys.argv[1]).read()
    m = re.search(r'meta name=\"_csrf\" content=\"([^\"]+)\"', html)
    print(m.group(1) if m else '')
except Exception: print('')
" "$TMP")

if [ -z "$CSRF" ]; then
  echo "ERROR: Login failed — check HAC_USER/HAC_PASS" >&2
  exit 1
fi

# 4. Execute script
MODE="rollback"
[ "$COMMIT" = "true" ] && MODE="commit"
echo "[$MODE mode]"

curl -sk -b "$CJ" \
  -X POST "$HAC/console/scripting/execute" \
  -H "Accept: application/json" \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode "script=$SCRIPT" \
  --data-urlencode "scriptType=groovy" \
  --data-urlencode "commit=$COMMIT" 2>/dev/null | python3 -c "
import sys, json

try:
    data = json.load(sys.stdin)
except json.JSONDecodeError:
    print('ERROR: Failed to parse HAC response — check server status', file=sys.stderr)
    sys.exit(1)

result = data.get('executionResult', '')
output = data.get('outputText', '')
stacktrace = data.get('stacktraceText', '')

if stacktrace:
    print('ERROR:', file=sys.stderr)
    print(stacktrace, file=sys.stderr)
    sys.exit(1)

if output:
    print(output.rstrip())

if result and result != 'null':
    print('=> ' + result)
"
