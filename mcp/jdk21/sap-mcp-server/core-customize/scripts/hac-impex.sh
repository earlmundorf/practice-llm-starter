#!/bin/bash
# =============================================================================
# hac-impex.sh — Run ImpEx imports against HAC from the command line
#
# Usage:
#   ./hac-impex.sh "INSERT_UPDATE Product; code[unique=true]; name[lang=en]"
#   ./hac-impex.sh /path/to/import.impex
#   cat data.impex | ./hac-impex.sh -
#
# Arguments:
#   $1  ImpEx content string, file path, or "-" for stdin (required)
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
INPUT="$1"

if [ -z "$INPUT" ]; then
  echo "Usage: $0 '<impex>' | <file> | -" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 import.impex" >&2
  echo "  $0 /tmp/my-data.impex" >&2
  echo "  $0 \"INSERT_UPDATE Title; code[unique=true]; name[lang=en]" >&2
  echo "  ; mr ; Mr\"" >&2
  echo "  cat data.impex | $0 -" >&2
  echo "" >&2
  echo "Environment:" >&2
  echo "  HAC_URL=https://localhost:9002/hac  HAC_USER=admin  HAC_PASS=nimda" >&2
  exit 1
fi

# Resolve ImpEx from string, file, or stdin
if [ "$INPUT" = "-" ]; then
  IMPEX=$(cat)
elif [ -f "$INPUT" ]; then
  IMPEX=$(cat "$INPUT")
else
  IMPEX="$INPUT"
fi

if [ -z "$IMPEX" ]; then
  echo "ERROR: Empty ImpEx content" >&2
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

# 3. Get ImpEx import page for fresh CSRF token
curl -sk -b "$CJ" -c "$CJ" "$HAC/console/impex/import" -o "$TMP" 2>/dev/null
CSRF=$(python3 -c "
import re, sys
try:
    html = open(sys.argv[1]).read()
    m = re.search(r'meta name=\"_csrf\" content=\"([^\"]+)\"', html)
    if not m:
        m = re.search(r'name=\"_csrf\"[^>]*value=\"([^\"]+)\"', html)
    print(m.group(1) if m else '')
except Exception: print('')
" "$TMP")

if [ -z "$CSRF" ]; then
  echo "ERROR: Login failed — check HAC_USER/HAC_PASS" >&2
  exit 1
fi

# 4. Execute ImpEx import
curl -sk -b "$CJ" \
  -X POST "$HAC/console/impex/import" \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode "scriptContent=$IMPEX" \
  --data-urlencode "encoding=UTF-8" \
  --data-urlencode "maxThreads=1" \
  -d "validationEnum=IMPORT_STRICT" \
  -d "enableCodeExecution=false" \
  -d "distributedMode=false" \
  -d "legacyMode=false" \
  -d "sldEnabled=false" \
  -o "$TMP" 2>/dev/null

# 5. Parse HTML response for result
python3 -c "
import re, sys, html as htmlmod

try:
    content = open(sys.argv[1]).read()
except Exception:
    print('ERROR: Could not read HAC response', file=sys.stderr)
    sys.exit(1)

# Check for success — HAC uses data-level='notice' (older) or data-result containing 'finished successfully' (2211-jdk21.x)
notice = re.search(r'data-level=\"notice\"[^>]*data-result=\"([^\"]*)\"', content)
error = re.search(r'data-level=\"error\"[^>]*data-result=\"([^\"]*)\"', content)
if notice:
    print(f'OK: {htmlmod.unescape(notice.group(1))}')
    m = re.search(r'<div class=\"box impexResult quiet\">\\s*<pre>\\s*(.*?)\\s*</pre>', content, re.DOTALL)
    if m:
        log = htmlmod.unescape(m.group(1)).strip()
        if log:
            print(log)
    sys.exit(0)

if error:
    print(f'ERROR: {htmlmod.unescape(error.group(1))}', file=sys.stderr)
    m = re.search(r'<div class=\"box impexResult quiet\">\\s*<pre>\\s*(.*?)\\s*</pre>', content, re.DOTALL)
    if m:
        detail = htmlmod.unescape(m.group(1)).strip()
        if detail:
            print(detail, file=sys.stderr)
    sys.exit(1)

# Check for Spring form validation errors
errors = re.findall(r'class=\"error\"[^>]*>(.*?)<', content)
if errors:
    print('ERROR: ' + '; '.join(e.strip() for e in errors if e.strip()), file=sys.stderr)
    sys.exit(1)

# Fallback — could not determine result
print('WARNING: Could not determine import result — check HAC manually', file=sys.stderr)
sys.exit(1)
" "$TMP"
