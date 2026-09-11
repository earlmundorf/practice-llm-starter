#!/bin/bash
# =============================================================================
# hac-flexquery.sh — Run FlexibleSearch queries against HAC from the command line
#
# Usage:
#   ./hac-flexquery.sh "SELECT {pk}, {code} FROM {Product}"
#   ./hac-flexquery.sh --file query.sql --max-count 50
#   ./hac-flexquery.sh --content "SELECT {pk} FROM {Product}" --json
#   echo "SELECT {pk} FROM {Product}" | ./hac-flexquery.sh --stdin
#
# Arguments:
#   FlexibleSearch query string, file path, or "-" for stdin (required)
#   Optional max results positional value, or --max-count <n>
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
  echo "Usage: $0 [--file <path> | --stdin | --content <query> | '<query>' | <file> | -] [maxCount] [--json]" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 \"SELECT {pk}, {code} FROM {Product}\"" >&2
  echo "  $0 --file query.sql --max-count 50" >&2
  echo "  $0 --content \"SELECT {pk} FROM {Product}\" --json" >&2
  echo "  echo \"SELECT {pk} FROM {Product}\" | $0 --stdin" >&2
  echo "" >&2
  echo "Options:" >&2
  echo "  --file <path>      Read query from a file" >&2
  echo "  --stdin            Read query from standard input" >&2
  echo "  --content <query>  Use query text directly" >&2
  echo "  --max-count <n>    Max results (default: 200)" >&2
  echo "  --json             Print raw HAC JSON response" >&2
  echo "" >&2
  hac_usage_env
}

if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
  usage
  exit 0
fi

INPUT=""
INPUT_MODE="auto"
MAX_COUNT="200"
JSON_OUTPUT="false"
POSITIONAL_COUNT=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --file)
      hac_require_option_value "--file" "${2:-}" || exit 1
      hac_set_input "query" "file" "$2" || exit 1
      shift 2
      ;;
    --stdin)
      hac_set_input "query" "stdin" "-" || exit 1
      shift
      ;;
    --content)
      hac_require_option_value "--content" "${2:-}" || exit 1
      hac_set_input "query" "content" "$2" || exit 1
      shift 2
      ;;
    --max-count)
      hac_require_option_value "--max-count" "${2:-}" || exit 1
      MAX_COUNT="$2"
      shift 2
      ;;
    --json)
      JSON_OUTPUT="true"
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
      POSITIONAL_COUNT=$((POSITIONAL_COUNT + 1))
      if [ "$POSITIONAL_COUNT" -eq 1 ]; then
        hac_set_input "query" "auto" "$1" || exit 1
      elif [ "$POSITIONAL_COUNT" -eq 2 ]; then
        MAX_COUNT="$1"
      else
        echo "ERROR: Unexpected argument: $1" >&2
        exit 1
      fi
      shift
      ;;
  esac
done

if [ -z "$INPUT" ]; then
  usage
  exit 1
fi

if ! [[ "$MAX_COUNT" =~ ^[0-9]+$ ]]; then
  echo "ERROR: max count must be a non-negative integer" >&2
  exit 1
fi

hac_init || exit 1

QUERY_FILE=$(mktemp)
CJ=$(mktemp)
TMP=$(mktemp)
RESPONSE=$(mktemp)
cleanup() { rm -f "$QUERY_FILE" "$CJ" "$TMP" "$RESPONSE"; }
trap cleanup EXIT

hac_write_content_file "$INPUT_MODE" "$INPUT" "query" "$QUERY_FILE" || exit 1

if [ ! -s "$QUERY_FILE" ]; then
  echo "ERROR: Empty query" >&2
  exit 1
fi

FLEXSEARCH_PAGE_URL=$(hac_url "/console/flexsearch") || exit 1
FLEXSEARCH_EXECUTE_URL=$(hac_url "/console/flexsearch/execute") || exit 1

hac_print_run_context "flexquery" "maxCount=$MAX_COUNT"

hac_login "$CJ" "$TMP" || exit 1
CSRF=$(hac_fresh_csrf "load FlexibleSearch page" "$CJ" "$TMP" "$FLEXSEARCH_PAGE_URL") || exit 1

# 4. Execute query
hac_curl "execute FlexibleSearch query" "$RESPONSE" -b "$CJ" \
  -X POST "$FLEXSEARCH_EXECUTE_URL" \
  -H "Accept: application/json" \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode "flexibleSearchQuery@$QUERY_FILE" \
  --data-urlencode "sqlQuery=" \
  --data-urlencode "maxCount=$MAX_COUNT" \
  --data-urlencode "user=$AUTH_USER" \
  --data-urlencode "locale=en" \
  --data-urlencode "commit=false" || exit 1

if [ "$JSON_OUTPUT" = "true" ]; then
  cat "$RESPONSE"
  exit 0
fi

python3 -c "
import sys, json, re


def hint_for_exception(message):
  lower = message.lower()
  hints = []
  if any(token in lower for token in ['cannot find type', 'unknown type', 'invalid type', 'typecode']):
    hints.append('Check the item type name and whether the extension/type system update has been applied.')
  if any(token in lower for token in ['cannot search unknown field', 'unknown field', 'cannot find attribute', 'no attribute']):
    hints.append('Check attribute qualifiers and whether the query needs a join, localized qualifier, or updated type system.')
  if any(token in lower for token in ['missing values for', 'invalid pks', 'catalogversion', 'catalog version']):
    hints.append('Check Staged vs Online catalogVersion and whether the referenced catalog/version exists.')
  if any(token in lower for token in ['ambiguous', 'alias', 'cannot find (visible) type']):
    hints.append('Check FlexibleSearch aliases and relation joins; every selected attribute must resolve through a visible alias.')
  if any(token in lower for token in ['syntax', 'unexpected token', 'parse', 'query is not legal']):
    hints.append('Check FlexibleSearch syntax, not SQL syntax; use {Type} and {alias.attribute} notation.')
  return hints[:2]


raw = sys.stdin.read()

try:
  data = json.loads(raw)
except json.JSONDecodeError:
  print('ERROR: Failed to parse HAC response - expected JSON from FlexibleSearch', file=sys.stderr)
  if re.search(r'j_spring_security_check|j_username|login', raw, re.I):
    print('HINT: HAC returned a login page; check credentials, CSRF/session handling, and Java 17 vs Java 21 HAC context.', file=sys.stderr)
  elif re.search(r'<html|<!doctype', raw, re.I):
    print('HINT: HAC returned HTML instead of JSON; check endpoint context and server-side HAC errors.', file=sys.stderr)
  else:
    print('HINT: Check SAP Commerce server logs before retrying the same query.', file=sys.stderr)
    sys.exit(1)

# Handle query errors
if data.get('exception'):
    msg = data['exception']
    if isinstance(msg, dict):
        msg = msg.get('message', str(msg))
    msg = str(msg)
    print('ERROR:', msg, file=sys.stderr)
    for hint in hint_for_exception(msg):
      print('HINT:', hint, file=sys.stderr)
    sys.exit(1)

headers = data.get('headers', [])
results = data.get('resultList', [])
count = data.get('resultCount', 0)

if not headers:
    print(f'(empty result set, {count} rows)')
    print('HINT: Zero rows usually means wrong identifier, Staged vs Online catalogVersion, missing import, missing sync, or wrong BaseSite/BaseStore context.', file=sys.stderr)
    sys.exit(0)

truncated = [False]


def clip(value, width=60):
  text = str(value or '')
  if len(text) > width:
    truncated[0] = True
    return text[:width - 3] + '...'
  return text


# Calculate column widths
widths = [max(len(str(h)), 4) for h in headers]
for row in results:
    for i, val in enumerate(row):
        if i < len(widths):
            widths[i] = max(widths[i], len(str(val or '')))

# Cap column width at 60 chars for readability
widths = [min(w, 60) for w in widths]

fmt = '  '.join('{:<' + str(w) + '}' for w in widths)
sep = '  '.join('-' * w for w in widths)

print(fmt.format(*[clip(h) for h in headers]))
print(sep)
for row in results:
    vals = [str(v or '') for v in row]
    while len(vals) < len(headers):
        vals.append('')
    print(fmt.format(*[clip(v) for v in vals[:len(headers)]]))

print(f'\n({count} rows)')
if truncated[0]:
  print('NOTE: Some values were truncated to 60 characters; rerun with --json for full values.', file=sys.stderr)
" < "$RESPONSE"
