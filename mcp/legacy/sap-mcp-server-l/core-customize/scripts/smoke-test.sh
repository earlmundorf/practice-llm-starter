#!/bin/bash
# ThinkShop MCP server demo-readiness smoke test.
BASE="https://localhost:9002/occ/v2/electronics"
AUTH="https://localhost:9002/authorizationserver/oauth/token"
PASS=0; FAIL=0; WARN=0

ok()   { PASS=$((PASS+1)); echo "PASS  $1"; }
bad()  { FAIL=$((FAIL+1)); echo "FAIL  $1"; }
warn() { WARN=$((WARN+1)); echo "WARN  $1"; }

jqpy() { python3 -c "import sys,json;$1" 2>/dev/null; }

# --- 1. OAuth: trusted client + customer (john.doe) -------------------------
TOKEN=$(curl -sk -X POST "$AUTH" -d "client_id=trusted_client&client_secret=secret&grant_type=client_credentials" | jqpy "print(json.load(sys.stdin)['access_token'])")
[ -n "$TOKEN" ] && ok "OAuth client_credentials (trusted_client)" || bad "OAuth client_credentials"

CTOKEN=$(curl -sk -X POST "$AUTH" -d "client_id=mobile_android&client_secret=secret&grant_type=password&username=john.doe%40thinkshop.com&password=1234" | jqpy "print(json.load(sys.stdin)['access_token'])")
[ -n "$CTOKEN" ] && ok "OAuth password grant (john.doe customer)" || bad "OAuth password grant (john.doe)"

mcp() { # token session payload
  curl -sk -X POST "$BASE/mcp" -H "Authorization: Bearer $1" -H "Content-Type: application/json" ${2:+-H "MCP-Session-Id: $2"} -d "$3"
}
tool() { # token session name args
  mcp "$1" "$2" "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"$3\",\"arguments\":$4}}" \
    | jqpy "b=json.load(sys.stdin);print(b['result']['content'][0]['text'])"
}

# --- 2. MCP protocol: initialize + tools/list --------------------------------
SESSION=$(curl -sk -D - -o /dev/null -X POST "$BASE/mcp" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","clientInfo":{"name":"smoke"}}}' \
  | grep -i "MCP-Session-Id" | tr -d '\r' | awk '{print $2}')
[ -n "$SESSION" ] && ok "MCP initialize → session $SESSION" || bad "MCP initialize"

NTOOLS=$(mcp "$TOKEN" "$SESSION" '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | jqpy "print(len(json.load(sys.stdin)['result']['tools']))")
[ "$NTOOLS" = "19" ] && ok "tools/list returns 19 tools" || bad "tools/list returned '$NTOOLS' (expected 19)"

# --- 3. Product search: keyword, out-of-stock, category ----------------------
R=$(tool "$TOKEN" "$SESSION" product_search '{"query":"keyboard","pageSize":10}')
echo "$R" | grep -q "KEYBOARD_LTD_ALUMINUM" && echo "$R" | grep -q "outOfStock" \
  && ok "product_search: out-of-stock edge product surfaced" || bad "product_search keyword/out-of-stock"

R=$(tool "$TOKEN" "$SESSION" product_search '{"query":"","categoryCode":"computing","pageSize":10}')
echo "$R" | grep -q "LAPTOP_PRO_15" && echo "$R" | grep -q "MONITOR_4K_27" \
  && ok "product_search: category filter (computing)" || bad "product_search category filter"

R=$(tool "$TOKEN" "$SESSION" product_search '{"query":"","categoryCode":"swag-drinkware","pageSize":10}')
echo "$R" | grep -q "TS_MUG_CLASSIC" && ok "product_search: swag category still works" || bad "product_search swag category"

# --- 4. Session persistence (DB-backed) --------------------------------------
R=$(mcp "$TOKEN" "$SESSION" '{"jsonrpc":"2.0","id":3,"method":"tools/list"}')
echo "$R" | grep -q '"tools"' && ok "MCP session reused across requests (persistent store)" || bad "MCP session reuse"

R=$(mcp "$TOKEN" "sess_doesnotexist00" '{"jsonrpc":"2.0","id":4,"method":"tools/list"}')
echo "$R" | grep -q "Invalid or expired" && ok "Invalid session id rejected" || bad "Invalid session not rejected"

# --- 5. Knowledge base --------------------------------------------------------
R=$(curl -sk "$BASE/info/search?q=returns&pageSize=3")
echo "$R" | grep -qi "return" && ok "knowledge /info/search (anonymous)" || bad "knowledge /info/search: $R"

R=$(curl -sk "$BASE/info/returns-policy")
echo "$R" | grep -qi "returns" && ok "knowledge /info/{uid}" || bad "knowledge /info/{uid}: $R"

R=$(tool "$TOKEN" "$SESSION" info_search '{"query":"shipping"}')
echo "$R" | grep -qi "shipping" && ok "MCP info_search tool" || bad "MCP info_search tool"

# --- 6. Promotions ------------------------------------------------------------
R=$(tool "$TOKEN" "$SESSION" promotions_get '{}')
echo "$R" | grep -q "LAPTOP10\|free_shipping" && ok "MCP promotions_get (rules + coupons)" || warn "promotions_get returned no known rules (publish step run?): ${R:0:120}"

# --- 7. Customer cart flow (add → verify → clean up) --------------------------
CSESSION=$(curl -sk -D - -o /dev/null -X POST "$BASE/mcp" -H "Authorization: Bearer $CTOKEN" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","clientInfo":{"name":"smoke-customer"}}}' \
  | grep -i "MCP-Session-Id" | tr -d '\r' | awk '{print $2}')
R=$(tool "$CTOKEN" "$CSESSION" cart_add_product '{"productCode":"BLUETOOTH_SPEAKER","quantity":1}')
echo "$R" | grep -qi "BLUETOOTH_SPEAKER\|success\|added" && ok "MCP cart_add_product (customer session)" || bad "cart_add_product: ${R:0:160}"

R=$(tool "$CTOKEN" "$CSESSION" cart_get '{}')
echo "$R" | grep -q "BLUETOOTH_SPEAKER" && ok "MCP cart_get shows added item" || bad "cart_get: ${R:0:160}"

ENTRY=$(echo "$R" | jqpy "c=json.load(sys.stdin);print([e for e in c.get('entries',[]) if 'BLUETOOTH' in str(e)][0].get('entryNumber',0))")
R=$(tool "$CTOKEN" "$CSESSION" cart_remove_entry "{\"entryNumber\":${ENTRY:-0}}")
echo "$R" | grep -qi "error" && warn "cart_remove_entry cleanup: ${R:0:120}" || ok "MCP cart_remove_entry (demo cart restored)"

# --- 8. Out-of-stock add is refused -------------------------------------------
R=$(tool "$CTOKEN" "$CSESSION" cart_add_product '{"productCode":"KEYBOARD_LTD_ALUMINUM","quantity":1}')
echo "$R" | grep -qi "noStock\|out of stock\|quantityAdded.*0\|error" && ok "out-of-stock product cannot be added" || warn "out-of-stock add result: ${R:0:160}"

# --- 9. Agent endpoints --------------------------------------------------------
R=$(curl -sk "$BASE/agent/capabilities" -H "Authorization: Bearer $CTOKEN")
echo "$R" | grep -q "vision" && ok "agent /capabilities" || bad "agent /capabilities: $R"

R=$(curl -sk -X POST "$BASE/agent/visual-search" -H "Authorization: Bearer $CTOKEN" -H "Content-Type: application/json" -d '{"image":"!!!not-base64!!!","mimeType":"image/png"}')
echo "$R" | grep -q "not valid base64" && ok "visual-search rejects invalid base64 (guard live)" || bad "visual-search base64 guard: $R"

R=$(curl -sk -X POST "$BASE/agent/chat" -H "Authorization: Bearer $CTOKEN" -H "Content-Type: application/json" -d '{"messages":[]}')
echo "$R" | grep -q "messages array is required" && ok "agent /chat input validation (400 path)" || bad "agent /chat validation: $R"

R=$(curl -sk --max-time 90 -X POST "$BASE/agent/chat" -H "Authorization: Bearer $CTOKEN" -H "Content-Type: application/json" -d '{"messages":[{"role":"user","content":"In one short sentence: do you sell laptops?"}]}')
REPLY=$(echo "$R" | jqpy "print(json.load(sys.stdin).get('reply',''))")
if [ -n "$REPLY" ]; then ok "agent /chat LIVE LLM round-trip — reply: ${REPLY:0:90}"; else warn "agent /chat returned no reply (LLM key set on server?): ${R:0:160}"; fi

# --- 10. KB-grounded LLM round-trip (info_search invocation) ------------------
R=$(curl -sk --max-time 90 -X POST "$BASE/agent/chat" -H "Authorization: Bearer $CTOKEN" -H "Content-Type: application/json" -d '{"messages":[{"role":"user","content":"In one short sentence, what is your return policy?"}]}')
REPLY=$(echo "$R" | jqpy "print(json.load(sys.stdin).get('reply',''))")
if echo "$REPLY" | grep -qiE "30[- ]?day|5-7|prepaid|free return"; then
  ok "agent /chat KB-grounded round-trip (info_search) — reply: ${REPLY:0:90}"
elif [ -n "$REPLY" ]; then
  warn "agent /chat KB reply lacks policy-specific tokens (info_search not invoked?): ${REPLY:0:160}"
else
  warn "agent /chat KB-grounded returned no reply: ${R:0:160}"
fi

echo
echo "================================================="
echo "SMOKE TEST: $PASS passed, $FAIL failed, $WARN warnings"
[ $FAIL -eq 0 ] && echo "DEMO READY" || echo "NOT READY — fix failures above"
