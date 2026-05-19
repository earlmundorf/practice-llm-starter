#!/bin/bash
# =============================================================================
# hac-flexquery.sh — Run FlexibleSearch queries against HAC from the command line
#
# Usage:
#   ./hac-flexquery.sh "SELECT {pk}, {code} FROM {Product}"
#   ./hac-flexquery.sh query.sql
#   ./hac-flexquery.sh /path/to/query.sql 50
#   echo "SELECT {pk} FROM {Product}" | ./hac-flexquery.sh -
#
# Arguments:
#   $1  FlexibleSearch query string, file path, or "-" for stdin (required)
#   $2  Max results (optional, default: 200)
#
# Environment variables (override defaults):
#   HAC_URL      Base URL (default: https://localhost:9002/hac)
#   HAC_USER     Username (default: admin)
#   HAC_PASS     Password (default: nimda)
#
# Requires: curl, python3
# =============================================================================

HAC="${HAC_URL:-https://localhost:9002/hac}"
AUTH_USER="${HAC_USER:-admin}"
AUTH_PASS="${HAC_PASS:-nimda}"
INPUT="$1"
MAX_COUNT="${2:-200}"

if [ -z "$INPUT" ]; then
  echo "Usage: $0 '<query>' | <file> | - [maxCount]" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 \"SELECT {pk}, {code} FROM {Product}\"" >&2
  echo "  $0 query.sql 50" >&2
  echo "  $0 /tmp/my-query.sql" >&2
  echo "  echo \"SELECT {pk} FROM {Product}\" | $0 -" >&2
  echo "" >&2
  echo "Environment:" >&2
  echo "  HAC_URL=https://localhost:9002/hac  HAC_USER=admin  HAC_PASS=nimda" >&2
  exit 1
fi

# Resolve query from string, file, or stdin
if [ "$INPUT" = "-" ]; then
  QUERY=$(cat)
elif [ -f "$INPUT" ]; then
  QUERY=$(cat "$INPUT")
else
  QUERY="$INPUT"
fi

if [ -z "$QUERY" ]; then
  echo "ERROR: Empty query" >&2
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

# 3. Get FlexibleSearch page for fresh CSRF token
curl -sk -b "$CJ" -c "$CJ" "$HAC/console/flexsearch/" -o "$TMP" 2>/dev/null
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

# 4. Execute query
curl -sk -b "$CJ" \
  -X POST "$HAC/console/flexsearch/execute" \
  -H "Accept: application/json" \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode "flexibleSearchQuery=$QUERY" \
  --data-urlencode "sqlQuery=" \
  --data-urlencode "maxCount=$MAX_COUNT" \
  --data-urlencode "user=$AUTH_USER" \
  --data-urlencode "locale=en" \
  --data-urlencode "commit=false" 2>/dev/null | python3 -c "
import sys, json

try:
    data = json.load(sys.stdin)
except json.JSONDecodeError:
    print('ERROR: Failed to parse HAC response — check server status', file=sys.stderr)
    sys.exit(1)

# Handle query errors
if data.get('exception'):
    msg = data['exception']
    if isinstance(msg, dict):
        msg = msg.get('message', str(msg))
    print('ERROR:', msg, file=sys.stderr)
    sys.exit(1)

headers = data.get('headers', [])
results = data.get('resultList', [])
count = data.get('resultCount', 0)

if not headers:
    print(f'(empty result set, {count} rows)')
    sys.exit(0)

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

print(fmt.format(*[str(h)[:60] for h in headers]))
print(sep)
for row in results:
    vals = [str(v or '') for v in row]
    while len(vals) < len(headers):
        vals.append('')
    print(fmt.format(*[v[:60] for v in vals[:len(headers)]]))

print(f'\n({count} rows)')
"
