#!/bin/bash
# =============================================================================
# hac-groovy.sh — Run Groovy scripts against HAC from the command line
#
# Usage:
#   ./hac-groovy.sh --content "return 'Hello World'"
#   ./hac-groovy.sh --file /path/to/script.groovy
#   echo "println 'hi'" | ./hac-groovy.sh --stdin
#   ./hac-groovy.sh --file script.groovy --commit     # Persist changes
#   ./hac-groovy.sh --file script.groovy --verbose    # Full stack trace on failure
#
# Arguments:
#   Groovy script string, file path, or "-" for stdin (required)
#   --commit  Persist changes (default: rollback mode)
#   --verbose Print full stack trace on failure
#
# Environment overrides, only when defaults do not match:
#   HAC_URL, HAC_JAVA_VERSION, HAC_CONTEXT_PATH, HAC_USER, HAC_PASS
#   Defaults assume local Java 21 HAC at https://localhost:9002 with admin/nimda.
#
# Requires: curl, python3
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/hac-common.sh
. "$SCRIPT_DIR/hac-common.sh"

usage() {
  echo "Usage: $0 [--file <path> | --stdin | --content <script> | '<script>' | <file> | -] [--commit] [--verbose]" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 --content \"return 'Hello World'\"" >&2
  echo "  $0 --file script.groovy" >&2
  echo "  $0 --file /tmp/my-script.groovy --commit" >&2
  echo "  echo \"println 'hi'\" | $0 --stdin" >&2
  echo "" >&2
  echo "Options:" >&2
  echo "  --file <path>       Read script from a file" >&2
  echo "  --stdin             Read script from standard input" >&2
  echo "  --content <script>  Use script text directly" >&2
  echo "  --commit   Persist changes (default: rollback mode)" >&2
  echo "  --verbose  Print full stack trace on failure" >&2
  echo "" >&2
  hac_usage_env
}

if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
  usage
  exit 0
fi

COMMIT="false"
VERBOSE="false"
INPUT=""
INPUT_MODE="auto"

# Parse args
while [ "$#" -gt 0 ]; do
  case "$1" in
  --file)
    hac_require_option_value "--file" "${2:-}" || exit 1
    hac_set_input "Groovy" "file" "$2" || exit 1
    shift 2
    ;;
  --stdin)
    hac_set_input "Groovy" "stdin" "-" || exit 1
    shift
    ;;
  --content)
    hac_require_option_value "--content" "${2:-}" || exit 1
    hac_set_input "Groovy" "content" "$2" || exit 1
    shift 2
    ;;
  --commit)
    COMMIT="true"
    shift
    ;;
  --verbose)
    VERBOSE="true"
    shift
    ;;
  --help|-h)
    usage
    exit 0
    ;;
  --*)
    echo "ERROR: Unknown option: $1" >&2
    exit 1
    ;;
  *)
    if [ -n "$INPUT" ]; then echo "ERROR: Unexpected argument: $1" >&2; exit 1; fi
    hac_set_input "Groovy" "auto" "$1" || exit 1
    shift
    ;;
  esac
done

if [ -z "$INPUT" ]; then
  usage
  exit 1
fi

hac_init || exit 1

SCRIPT_FILE=$(mktemp)
CJ=$(mktemp)
TMP=$(mktemp)
RESPONSE=$(mktemp)
cleanup() { rm -f "$SCRIPT_FILE" "$CJ" "$TMP" "$RESPONSE"; }
trap cleanup EXIT

hac_write_content_file "$INPUT_MODE" "$INPUT" "Groovy" "$SCRIPT_FILE" || exit 1

if [ ! -s "$SCRIPT_FILE" ]; then
  echo "ERROR: Empty script" >&2
  exit 1
fi

SCRIPTING_PAGE_URL=$(hac_url "/console/scripting") || exit 1
SCRIPTING_EXECUTE_URL=$(hac_url "/console/scripting/execute") || exit 1

hac_print_run_context "groovy" "mode=$([ "$COMMIT" = "true" ] && printf commit || printf rollback) inputMode=$INPUT_MODE input=$(hac_preview_file "$SCRIPT_FILE")"

hac_login "$CJ" "$TMP" || exit 1
CSRF=$(hac_fresh_csrf "load scripting page" "$CJ" "$TMP" "$SCRIPTING_PAGE_URL") || exit 1

# 4. Execute script
MODE="rollback"
[ "$COMMIT" = "true" ] && MODE="commit"
echo "[$MODE mode]"

hac_curl "execute Groovy script" /dev/stdout -b "$CJ" \
  -X POST "$SCRIPTING_EXECUTE_URL" \
  -H "Accept: application/json" \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode "script@$SCRIPT_FILE" \
  --data-urlencode "scriptType=groovy" \
  --data-urlencode "commit=$COMMIT" > "$RESPONSE" || exit 1

python3 -c "
import sys, json


def first_nonempty(lines):
  for line in lines:
    if line.strip():
      return line.strip()
  return ''


def first_relevant_frame(lines):
  app_frame = ''
  commerce_frame = ''
  for line in lines:
    stripped = line.strip()
    if not stripped.startswith('at '):
      continue
    if not commerce_frame and any(token in stripped for token in ['de.hybris.platform', 'com.sap', 'com.company', 'com.example']):
      commerce_frame = stripped
    if not app_frame and not any(token in stripped for token in ['java.', 'javax.', 'jakarta.', 'sun.', 'groovy.', 'org.codehaus.groovy']):
      app_frame = stripped
  return app_frame or commerce_frame

try:
    data = json.load(open(sys.argv[1]))
except json.JSONDecodeError:
  print('ERROR: Failed to parse HAC response - expected JSON from HAC scripting', file=sys.stderr)
  print('HINT: Check login/session state, Java HAC context, and SAP Commerce server logs before retrying.', file=sys.stderr)
  sys.exit(1)

result = data.get('executionResult', '')
output = data.get('outputText', '')
stacktrace = data.get('stacktraceText', '')
verbose = sys.argv[2] == 'true'

if stacktrace:
  lines = stacktrace.splitlines()
  summary = first_nonempty(lines)
  frame = first_relevant_frame(lines)
  print('ERROR: Groovy execution failed', file=sys.stderr)
  if summary:
    print('DETAIL:', summary, file=sys.stderr)
  if frame:
    print('DETAIL: first relevant frame: ' + frame, file=sys.stderr)
  if verbose:
    print(stacktrace, file=sys.stderr)
  else:
    print('HINT: Rerun with --verbose only if the summary and first frame are not enough.', file=sys.stderr)
    sys.exit(1)

if output:
    print(output.rstrip())

if result and result != 'null':
    print('=> ' + result)
" "$RESPONSE" "$VERBOSE"
