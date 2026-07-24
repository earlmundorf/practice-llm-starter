#!/usr/bin/env bash
#
# Run the OFFICIAL UCP conformance suite (Universal-Commerce-Protocol/
# conformance, cloned under working-docs/ucp-client/) against the local
# ThinkShop server — the external validation issue #5 calls for.
#
# Topology: the suite is discovery-driven (it reads the REST shopping
# endpoint from /.well-known/ucp and sends NO OAuth bearer), so it runs
# through scripts/ucp-local-proxy.py — the documented dev stand-in for the
# production edge rewrite (R6) + agent-gateway auth (R8). The proxy rewrites
# the advertised endpoints to itself and injects the merchant bearer.
#
# ThinkShop-specific expectations live in scripts/conformance/
# (thinkshop-conformance-input.json + thinkshop-test-fixtures.json).
#
# Prereqs: server running (./gradlew startServer), Solr indexed, promotions
# published, demo coupon created, uv installed.
#
# Usage:
#   ./scripts/run-ucp-conformance.sh                # whole suite
#   ./scripts/run-ucp-conformance.sh checkout_lifecycle_test.py  # one file
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFORMANCE_DIR="$ROOT_DIR/working-docs/ucp-client/conformance"
PROXY_PORT="${UCP_PROXY_PORT:-8182}"
RUN_LOG="${UCP_CONFORMANCE_LOG:-/tmp/ucp-conformance-run.log}"

UV_BIN="$(command -v uv || echo /opt/homebrew/bin/uv)"

if [[ ! -d "$CONFORMANCE_DIR" ]]; then
    echo "FAIL: conformance suite not found at $CONFORMANCE_DIR"
    echo "      git clone https://github.com/Universal-Commerce-Protocol/conformance \\"
    echo "          $ROOT_DIR/working-docs/ucp-client/conformance"
    exit 1
fi

# ── Ensure the local proxy is up (start it if not) ──────────────────────────
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

# The proxy must advertise ITSELF (discovery-driven suite) — verify the rewrite.
if ! curl -s -m 3 "http://localhost:$PROXY_PORT/.well-known/ucp" \
        | grep -q "\"endpoint\": *\"http://localhost:$PROXY_PORT\""; then
    echo "WARN: the proxy's profile does not advertise http://localhost:$PROXY_PORT —"
    echo "      is an old ucp-local-proxy.py (without the endpoint rewrite) running?"
fi

# ── Run the suite ───────────────────────────────────────────────────────────
# Per-file absltest invocation: the suite's conftest.py initializes absl
# FLAGS without forwarding pytest CLI args, so the documented per-file form
# is the one that accepts --conformance_input/--fixture_config overrides.
if [[ $# -gt 0 ]]; then
    TEST_FILES=("$@")
else
    TEST_FILES=()
    while IFS= read -r f; do
        TEST_FILES+=("$(basename "$f")")
    done < <(command ls "$CONFORMANCE_DIR"/*_test.py)
fi

echo "Running the official conformance suite (${#TEST_FILES[@]} files, log → $RUN_LOG) ..."
: > "$RUN_LOG"
PASS_FILES=()
FAIL_FILES=()
for test_file in "${TEST_FILES[@]}"; do
    echo "── $test_file ──" | tee -a "$RUN_LOG"
    set +e
    (cd "$CONFORMANCE_DIR" && \
        SERVER_URL="http://localhost:$PROXY_PORT" \
        "$UV_BIN" run "$test_file" \
            --server_url="http://localhost:$PROXY_PORT" \
            --conformance_input="$SCRIPT_DIR/conformance/thinkshop-conformance-input.json" \
            --fixture_config="$SCRIPT_DIR/conformance/thinkshop-test-fixtures.json" \
            --test_data_dir="$SCRIPT_DIR/conformance/test_data") \
        >> "$RUN_LOG" 2>&1
    RC=$?
    set -e
    if [[ $RC -eq 0 ]]; then
        echo "  PASS $test_file"
        PASS_FILES+=("$test_file")
    else
        echo "  FAIL $test_file (rc=$RC)"
        FAIL_FILES+=("$test_file")
    fi
done

echo
echo "Conformance summary: ${#PASS_FILES[@]} file(s) passed, ${#FAIL_FILES[@]} failed — full log: $RUN_LOG"
if [[ ${#FAIL_FILES[@]} -gt 0 ]]; then
    printf '  failed: %s\n' "${FAIL_FILES[@]}"
fi
exit $(( ${#FAIL_FILES[@]} > 0 ))
