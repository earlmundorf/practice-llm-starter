#!/bin/bash
# =============================================================================
# hac-import.sh — Portable ImpEx import via HAC (no project-specific deps)
#
# Usage:
#   ./hac-import.sh "INSERT_UPDATE Product; code[unique=true]; name[lang=en]"
#   ./hac-import.sh /path/to/import.impex
#   cat data.impex | ./hac-import.sh -
#
# Environment variables (override defaults):
#   HAC_URL      Base URL (default: https://localhost:9002)
#   HAC_USER     Username (default: admin)
#   HAC_PASS     Password (default: nimda)
#
# Requires: curl
# =============================================================================
set -euo pipefail

readonly HAC_BASE="${HAC_URL:-https://localhost:9002}"
readonly HAC_USERNAME="${HAC_USER:-admin}"
readonly HAC_PASSWORD="${HAC_PASS:-nimda}"

# --- Functions ----------------------------------------------------------------

usage() {
  cat >&2 <<EOF
Usage: $0 '<impex>' | <file> | -

Examples:
  $0 import.impex
  $0 /tmp/my-data.impex
  $0 "INSERT_UPDATE Title; code[unique=true]; name[lang=en]
  ; mr ; Mr"
  cat data.impex | $0 -

Environment:
  HAC_URL=https://localhost:9002  HAC_USER=admin  HAC_PASS=nimda
EOF
  exit 1
}

# Extract CSRF token from an HTML page.
# Tries meta tag first (import page), then hidden form field (login page).
# Args: $1 — path to HTML file
extract_csrf() {
  local file="$1"
  local token

  token=$(grep -o 'meta name="_csrf" content="[^"]*"' "$file" | sed 's/.*content="//;s/"//' | head -1)
  if [ -z "$token" ]; then
    token=$(grep -o 'name="_csrf"[^>]*value="[^"]*"' "$file" | sed 's/.*value="//;s/"//' | head -1)
  fi
  echo "$token"
}

# Extract text from the <pre> block inside the impexResult div.
# Unescapes common HTML entities and strips leading whitespace.
# Args: $1 — path to HTML file
extract_pre_block() {
  local file="$1"

  awk '/impexResult/,/<\/div>/' "$file" \
    | awk '/<pre>/,/<\/pre>/' \
    | sed "s/.*<pre>//;s/<\/pre>.*//;s/&amp;/\&/g;s/&lt;/</g;s/&gt;/>/g;s/&quot;/\"/g;s/&#039;/'/g;s/&#39;/'/g;s/^[[:space:]]*//" \
    | grep -v '^$' || true
}

# --- Input --------------------------------------------------------------------

[ $# -eq 0 ] && usage

INPUT="$1"

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

# --- Temp files ---------------------------------------------------------------

COOKIE_JAR=$(mktemp)
RESPONSE_FILE=$(mktemp)
trap 'rm -f "$COOKIE_JAR" "$RESPONSE_FILE"' EXIT

# --- 1. Get login page + CSRF token (doubles as reachability check) -----------

HTTP_CODE=$(curl -sk -L -c "$COOKIE_JAR" \
  -w '%{http_code}' --connect-timeout 5 \
  -o "$RESPONSE_FILE" "$HAC_BASE/login" 2>/dev/null) || true

if [ "$HTTP_CODE" = "000" ] || [ -z "$HTTP_CODE" ]; then
  echo "ERROR: Server not reachable at $HAC_BASE — is it running?" >&2
  exit 1
fi

CSRF=$(extract_csrf "$RESPONSE_FILE")
if [ -z "$CSRF" ]; then
  echo "ERROR: Could not extract CSRF token from $HAC_BASE/login" >&2
  exit 1
fi

# --- 2. Authenticate ---------------------------------------------------------

curl -sk -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST \
  "$HAC_BASE/j_spring_security_check" \
  --data-urlencode "j_username=$HAC_USERNAME" \
  --data-urlencode "j_password=$HAC_PASSWORD" \
  --data-urlencode "_csrf=$CSRF" \
  -o /dev/null 2>/dev/null

# --- 3. Get fresh CSRF token from import page --------------------------------

curl -sk -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  "$HAC_BASE/console/impex/import" -o "$RESPONSE_FILE" 2>/dev/null

CSRF=$(extract_csrf "$RESPONSE_FILE")
if [ -z "$CSRF" ]; then
  echo "ERROR: Login failed — check HAC_USER/HAC_PASS" >&2
  exit 1
fi

# --- 4. Execute ImpEx import --------------------------------------------------

curl -sk -b "$COOKIE_JAR" \
  -X POST "$HAC_BASE/console/impex/import" \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode "scriptContent=$IMPEX" \
  --data-urlencode "encoding=UTF-8" \
  --data-urlencode "maxThreads=1" \
  -d "validationEnum=IMPORT_STRICT" \
  -d "enableCodeExecution=false" \
  -d "distributedMode=false" \
  -d "legacyMode=false" \
  -d "sldEnabled=false" \
  -o "$RESPONSE_FILE" 2>/dev/null

# --- 5. Parse result ---------------------------------------------------------

# Success: data-level="notice"
if grep -q 'data-level="notice"' "$RESPONSE_FILE"; then
  echo "OK: ImpEx import successful"
  LOG=$(extract_pre_block "$RESPONSE_FILE")
  [ -n "$LOG" ] && echo "$LOG"
  exit 0
fi

# Error: data-level="error" (with or without "unsuccessfull")
if grep -q 'data-level="error"' "$RESPONSE_FILE"; then
  RESULT_MSG=$(grep -o 'data-result="[^"]*"' "$RESPONSE_FILE" | sed 's/data-result="//;s/"$//' | head -1)
  echo "ERROR: ${RESULT_MSG:-ImpEx import failed}"
  DETAIL=$(extract_pre_block "$RESPONSE_FILE")
  [ -n "$DETAIL" ] && echo "$DETAIL"
  exit 1
fi

# Spring form validation errors
ERRORS=$(grep -o 'class="error"[^>]*>[^<]*<' "$RESPONSE_FILE" 2>/dev/null | sed 's/class="error"[^>]*>//;s/<$//' | grep -v '^$' || true)
if [ -n "$ERRORS" ]; then
  echo "ERROR: $ERRORS" >&2
  exit 1
fi

# Fallback
echo "WARNING: Could not determine import result — check HAC manually" >&2
exit 1
