#!/usr/bin/env bash
# =============================================================================
# smoke-info.sh — Smoke tests for the knowledge-base and swag additions.
#
# Hits the live server's OCC endpoints (info/{uid}, info/search) and verifies
# product_search picks up the new swag category. Exits non-zero on any failure.
#
# Usage:
#   ./scripts/smoke-info.sh
#   BASE_URL=https://localhost:9002 ./scripts/smoke-info.sh
#
# Env overrides:
#   BASE_URL       default https://localhost:9002
#   BASE_SITE      default electronics
#   CLIENT_ID      default trusted_client
#   CLIENT_SECRET  default secret
#   USER_EMAIL     default john.doe@thinkshop.com
#   USER_PASSWORD  default 1234
#
# Requires: curl, python3
# =============================================================================

set -u
BASE_URL="${BASE_URL:-https://localhost:9002}"
BASE_SITE="${BASE_SITE:-electronics}"
CLIENT_ID="${CLIENT_ID:-trusted_client}"
CLIENT_SECRET="${CLIENT_SECRET:-secret}"
USER_EMAIL="${USER_EMAIL:-john.doe@thinkshop.com}"
USER_PASSWORD="${USER_PASSWORD:-1234}"

OCC="$BASE_URL/occ/v2/$BASE_SITE"

PASS=0
FAIL=0

# ── helpers ──────────────────────────────────────────────────────────────────

# Colors only when stdout is a TTY
if [ -t 1 ]; then
  G="\033[32m"; R="\033[31m"; Y="\033[33m"; C="\033[36m"; Z="\033[0m"
else
  G=""; R=""; Y=""; C=""; Z=""
fi

check() {
  local label="$1"; local cond="$2"; local detail="${3:-}"
  if [ "$cond" = "1" ]; then
    printf "  ${G}OK${Z}   %s\n" "$label"
    PASS=$((PASS+1))
  else
    printf "  ${R}FAIL${Z} %s%s\n" "$label" "${detail:+ — $detail}"
    FAIL=$((FAIL+1))
  fi
}

jq_get() {  # jq_get <json> <python expression on var d>
  python3 -c "import json,sys; d=json.loads(sys.stdin.read() or '{}'); print($2)" <<< "$1" 2>/dev/null
}

# ── auth ─────────────────────────────────────────────────────────────────────

printf "${C}── auth ──${Z}\n"
TOKEN_JSON=$(curl -sk -X POST "$BASE_URL/authorizationserver/oauth/token" \
  -d "grant_type=password&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&username=$USER_EMAIL&password=$USER_PASSWORD")
TOKEN=$(jq_get "$TOKEN_JSON" "d.get('access_token','')")
if [ -z "$TOKEN" ]; then
  printf "${R}FATAL${Z} could not get OAuth token. Is the server running? response: %s\n" "$(printf '%s' "$TOKEN_JSON" | head -c 200)"
  exit 2
fi
printf "  ${G}OK${Z}   token: %s...\n" "${TOKEN:0:16}"
H="Authorization: Bearer $TOKEN"

# ── info_get ─────────────────────────────────────────────────────────────────

printf "\n${C}── info_get ──${Z}\n"
STATUS=$(curl -sk -o /tmp/.smoke-info-get -w '%{http_code}' -H "$H" "$OCC/info/returns-policy")
[ "$STATUS" = "200" ] && check "GET /info/returns-policy returns 200" "1" || check "GET /info/returns-policy returns 200" "0" "got $STATUS"
BODY=$(cat /tmp/.smoke-info-get)
ENTRY_UID=$(jq_get "$BODY" "d.get('uid','')")
ENTRY_CAT=$(jq_get "$BODY" "d.get('category','')")
[ "$ENTRY_UID" = "returns-policy" ] && check "uid is 'returns-policy'" "1" || check "uid is 'returns-policy'" "0" "got '$ENTRY_UID'"
[ "$ENTRY_CAT" = "policy" ] && check "category is 'policy'" "1" || check "category is 'policy'" "0" "got '$ENTRY_CAT'"

STATUS=$(curl -sk -o /dev/null -w '%{http_code}' -H "$H" "$OCC/info/does-not-exist")
[ "$STATUS" = "404" ] && check "GET /info/{unknown} returns 404" "1" || check "GET /info/{unknown} returns 404" "0" "got $STATUS"

# ── info_search ──────────────────────────────────────────────────────────────

printf "\n${C}── info_search ──${Z}\n"
BODY=$(curl -sk -H "$H" "$OCC/info/search?q=shipping&pageSize=5")
COUNT=$(jq_get "$BODY" "len(d.get('results', []))")
[ -n "$COUNT" ] && [ "$COUNT" -ge 1 ] && check "q=shipping returns >= 1 result" "1" || check "q=shipping returns >= 1 result" "0" "got count=$COUNT"

BODY=$(curl -sk -H "$H" "$OCC/info/search?q=&category=event&pageSize=5")
NON_EVENT=$(jq_get "$BODY" "sum(1 for r in d.get('results', []) if r.get('category') != 'event')")
[ "$NON_EVENT" = "0" ] && check "category=event filter only returns events" "1" || check "category=event filter only returns events" "0" "got $NON_EVENT non-event rows"

BODY=$(curl -sk -H "$H" "$OCC/info/search?q=returns&pageSize=3")
HAS=$(jq_get "$BODY" "1 if any(r.get('uid') == 'returns-policy' for r in d.get('results', [])) else 0")
[ "$HAS" = "1" ] && check "q='returns' surfaces returns-policy" "1" || check "q='returns' surfaces returns-policy" "0"

# ── swag in product_search ───────────────────────────────────────────────────

printf "\n${C}── swag products ──${Z}\n"
BODY=$(curl -sk -H "$H" "$OCC/products/search?query=hoodie&pageSize=5&fields=DEFAULT")
HAS=$(jq_get "$BODY" "1 if any(p.get('code') == 'TS_HOODIE_ZIP' for p in d.get('products', [])) else 0")
[ "$HAS" = "1" ] && check "product_search('hoodie') finds TS_HOODIE_ZIP" "1" || check "product_search('hoodie') finds TS_HOODIE_ZIP" "0"

BODY=$(curl -sk -H "$H" "$OCC/products/search?query=mug&pageSize=5&fields=DEFAULT")
HAS=$(jq_get "$BODY" "1 if any((p.get('code') or '').startswith('TS_MUG') for p in d.get('products', [])) else 0")
[ "$HAS" = "1" ] && check "product_search('mug') finds a TS_MUG_*" "1" || check "product_search('mug') finds a TS_MUG_*" "0"

# ── summary ──────────────────────────────────────────────────────────────────

printf "\n%s\n" "============================================================"
TOTAL=$((PASS+FAIL))
if [ "$FAIL" -eq 0 ]; then
  printf "${G}All %d checks passed.${Z}\n" "$TOTAL"
  exit 0
else
  printf "${R}%d/%d checks failed.${Z}\n" "$FAIL" "$TOTAL"
  exit 1
fi
