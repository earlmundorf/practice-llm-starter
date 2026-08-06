#!/usr/bin/env bash
#
# Run Google's OUT-OF-THE-BOX UCP reference client against the local ThinkShop
# server — the acceptance test for the UCP surface.
#
# What this script does:
#   1. Derives a copy of the official happy-path client
#      (working-docs/ucp-client/samples/rest/python/client/flower_shop/
#       simple_happy_path_client.py — gitignored clone) with EXACTLY three
#      documented substitutions. The client hardcodes flower-shop demo data;
#      we do not pollute the ThinkShop catalog with flower SKUs or rename our
#      payment handler, so the demo constants are swapped instead:
#          bouquet_roses        → WIRELESS_GAMING_MOUSE   (demo SKU 1)
#          pot_ceramic          → LAPTOP_PRO_15           (demo SKU 2)
#          mock_payment_handler → thinkshop_mock_card     (our declared handler)
#      Nothing else changes — flow, paths, headers, payloads are all OOTB.
#      (The 10OFF discount code stays as-is: ThinkShop has no such code, the
#      client logs "No discounts applied!" as a warning and continues.)
#   2. Ensures the local UCP proxy (scripts/ucp-local-proxy.py, port 8182) is
#      up — the documented dev stand-in for the production edge rewrite (R6)
#      and agent-gateway auth (R8). Started here if needed, stopped on exit.
#   3. Runs the derived client via uv with --server_url=http://localhost:8182
#      and verifies it prints "Happy Path completed successfully."
#
# Prereqs: server running (./gradlew startServer), Solr indexed, uv installed.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLIENT_DIR="$ROOT_DIR/working-docs/ucp-client/samples/rest/python/client/flower_shop"
OOTB_CLIENT="$CLIENT_DIR/simple_happy_path_client.py"
DERIVED_CLIENT="$CLIENT_DIR/thinkshop_happy_path_client.py"
PROXY_PORT="${UCP_PROXY_PORT:-8182}"
EXPORT_LOG="${UCP_CLIENT_EXPORT:-/tmp/ucp-reference-client-run.md}"
RUN_LOG="$(mktemp /tmp/ucp-reference-client.XXXXXX)"

UV_BIN="$(command -v uv || echo /opt/homebrew/bin/uv)"

if [[ ! -f "$OOTB_CLIENT" ]]; then
    echo "FAIL: OOTB reference client not found at $OOTB_CLIENT"
    echo "      (clone the Universal-Commerce-Protocol samples + python-sdk repos"
    echo "       into working-docs/ucp-client/ first)"
    exit 1
fi

# ── 1. Derive the client copy (three documented substitutions, see header) ──
sed -e 's/bouquet_roses/WIRELESS_GAMING_MOUSE/g' \
    -e 's/pot_ceramic/LAPTOP_PRO_15/g' \
    -e 's/mock_payment_handler/thinkshop_mock_card/g' \
    "$OOTB_CLIENT" > "$DERIVED_CLIENT"
echo "Derived client written: $DERIVED_CLIENT"

# ── 2. Ensure the local proxy is up (start it if not) ───────────────────────
STARTED_PROXY=""
cleanup() {
    if [[ -n "$STARTED_PROXY" ]]; then
        kill "$STARTED_PROXY" 2>/dev/null || true
    fi
}
trap cleanup EXIT

if curl -s -m 3 "http://localhost:$PROXY_PORT/.well-known/ucp" | grep -q '"ucp"'; then
    echo "UCP proxy already serving on port $PROXY_PORT"
else
    echo "Starting UCP proxy on port $PROXY_PORT ..."
    nohup python3 "$SCRIPT_DIR/ucp-local-proxy.py" --port "$PROXY_PORT" \
        > /tmp/ucp-local-proxy.log 2>&1 &
    STARTED_PROXY=$!
    for _ in $(seq 1 20); do
        sleep 0.5
        if curl -s -m 3 "http://localhost:$PROXY_PORT/.well-known/ucp" | grep -q '"ucp"'; then
            break
        fi
    done
    if ! curl -s -m 3 "http://localhost:$PROXY_PORT/.well-known/ucp" | grep -q '"ucp"'; then
        echo "FAIL: proxy did not become ready (is the hybris server running on 9002?)"
        echo "      see /tmp/ucp-local-proxy.log"
        exit 1
    fi
    echo "Proxy ready (log: /tmp/ucp-local-proxy.log)"
fi

# ── 3. Run the derived OOTB client via uv ───────────────────────────────────
echo "Running the reference client (interaction log → $EXPORT_LOG) ..."
set +e
(cd "$CLIENT_DIR" && "$UV_BIN" run "$DERIVED_CLIENT" \
    --server_url="http://localhost:$PROXY_PORT" \
    --export_requests_to="$EXPORT_LOG") 2>&1 | tee "$RUN_LOG"
CLIENT_RC=${PIPESTATUS[0]}
set -e

echo
if [[ $CLIENT_RC -eq 0 ]] && grep -q "Happy Path completed successfully." "$RUN_LOG"; then
    ORDER_ID=$(grep -oE "Order ID: .*" "$RUN_LOG" | tail -1 | sed 's/Order ID: //')
    echo "PASS: OOTB reference client completed the full happy path."
    echo "      order placed: ${ORDER_ID:-unknown}"
    echo "      interaction log: $EXPORT_LOG"
    exit 0
else
    echo "FAIL: reference client did not complete the happy path (rc=$CLIENT_RC)."
    echo "      run log: $RUN_LOG · proxy log: /tmp/ucp-local-proxy.log"
    exit 1
fi
