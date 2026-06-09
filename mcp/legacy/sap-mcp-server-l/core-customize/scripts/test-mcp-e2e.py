#!/usr/bin/env python3
"""
End-to-end test for the MCP JSON-RPC endpoint.

Tests the full external-client flow: OAuth → initialize → tools/list → tools/call → DELETE.
Requires the hybris server to be running on localhost:9002 (HTTPS, self-signed cert).

Usage:
    python3 core-customize/scripts/test-mcp-e2e.py
    python3 core-customize/scripts/test-mcp-e2e.py --base-url https://localhost:9002
    python3 core-customize/scripts/test-mcp-e2e.py --verbose
"""

import argparse
import json
import ssl
import sys
import urllib.request
import urllib.error
import urllib.parse

# Skip certificate verification for self-signed dev cert
_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE

# ── Config ──────────────────────────────────────────────────────────────────

DEFAULT_BASE_URL = "https://localhost:9002"
BASE_SITE = "electronics"
MCP_PATH = f"/occ/v2/{BASE_SITE}/mcp"
OAUTH_PATH = "/authorizationserver/oauth/token"

# OAuth client with ROLE_TRUSTED_CLIENT (from commercewebservices essentialdata impex)
CLIENT_ID = "trusted_client"
CLIENT_SECRET = "secret"

# Default customer credentials (from thinkshop project data)
CUSTOMER_EMAIL = "john.doe@thinkshop.com"
CUSTOMER_PASSWORD = "1234"


# ── Helpers ─────────────────────────────────────────────────────────────────

class Colors:
    GREEN = "\033[92m"
    RED = "\033[91m"
    YELLOW = "\033[93m"
    CYAN = "\033[96m"
    DIM = "\033[2m"
    RESET = "\033[0m"


passed = 0
failed = 0
errors = []
verbose = False


def log(msg):
    print(msg)


def log_verbose(msg):
    if verbose:
        print(f"{Colors.DIM}{msg}{Colors.RESET}")


def check(name, condition, detail=""):
    global passed, failed
    if condition:
        passed += 1
        log(f"  {Colors.GREEN}PASS{Colors.RESET} {name}")
    else:
        failed += 1
        errors.append(f"{name}: {detail}")
        log(f"  {Colors.RED}FAIL{Colors.RESET} {name} — {detail}")


def http_request(url, data=None, headers=None, method=None):
    """Simple urllib wrapper that returns (status, headers, body_dict)."""
    headers = headers or {}
    if data is not None and isinstance(data, (dict, list)):
        data = json.dumps(data).encode("utf-8")
        headers.setdefault("Content-Type", "application/json")

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req, context=_SSL_CTX)
        body = resp.read().decode("utf-8")
        return resp.status, dict(resp.headers), json.loads(body) if body.strip() else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            parsed = json.loads(body) if body.strip() else {}
        except json.JSONDecodeError:
            parsed = {"_raw": body}
        return e.code, dict(e.headers), parsed


def jsonrpc(method, params=None, req_id="test-1"):
    msg = {"jsonrpc": "2.0", "id": req_id, "method": method}
    if params is not None:
        msg["params"] = params
    return msg


# ── OAuth ───────────────────────────────────────────────────────────────────

def get_trusted_client_token(base_url):
    """Get an OAuth token with ROLE_TRUSTED_CLIENT via client_credentials grant."""
    url = base_url + OAUTH_PATH
    data = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
    }).encode("utf-8")
    req = urllib.request.Request(url, data=data)
    try:
        resp = urllib.request.urlopen(req, context=_SSL_CTX)
        return json.loads(resp.read())["access_token"]
    except Exception as e:
        return None


def get_customer_token(base_url):
    """Get an OAuth token for a real customer (password grant). Cart/order tools work fully."""
    url = base_url + OAUTH_PATH
    data = urllib.parse.urlencode({
        "grant_type": "password",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "username": CUSTOMER_EMAIL,
        "password": CUSTOMER_PASSWORD,
    }).encode("utf-8")
    req = urllib.request.Request(url, data=data)
    try:
        resp = urllib.request.urlopen(req, context=_SSL_CTX)
        return json.loads(resp.read())["access_token"]
    except Exception as e:
        return None


# ── Test Sections ───────────────────────────────────────────────────────────

def test_protocol(base_url, auth_header):
    """Test MCP protocol-level behavior: initialize, session, errors."""
    log(f"\n{Colors.CYAN}── Protocol Tests ──{Colors.RESET}")
    mcp_url = base_url + MCP_PATH

    # 1. Initialize
    status, headers, body = http_request(
        mcp_url,
        data=jsonrpc("initialize", {
            "clientInfo": {"name": "e2e-test", "version": "1.0"},
            "protocolVersion": "2025-11-25",
        }, "init-1"),
        headers={"Authorization": auth_header},
    )
    log_verbose(f"  initialize response: {json.dumps(body, indent=2)}")

    check("initialize returns 200", status == 200, f"got {status}")
    check("initialize has result", "result" in body, f"body: {body}")

    session_id = headers.get("MCP-Session-Id") or headers.get("mcp-session-id")
    check("initialize returns MCP-Session-Id header", session_id is not None, f"headers: {list(headers.keys())}")

    if body.get("result"):
        result = body["result"]
        check("result has serverInfo", "serverInfo" in result, f"keys: {list(result.keys())}")
        check("result has capabilities.tools", "tools" in result.get("capabilities", {}))

    # 2. Request without session → error
    status2, _, body2 = http_request(
        mcp_url,
        data=jsonrpc("tools/list", req_id="nosess-1"),
        headers={"Authorization": auth_header},
    )
    log_verbose(f"  no-session response: {json.dumps(body2, indent=2)}")
    check("tools/list without session returns error",
          "error" in body2, f"body: {body2}")

    # 3. Request with bad session → error
    status3, _, body3 = http_request(
        mcp_url,
        data=jsonrpc("tools/list", req_id="badsess-1"),
        headers={"Authorization": auth_header, "MCP-Session-Id": "sess_invalid"},
    )
    check("tools/list with bad session returns error",
          "error" in body3, f"body: {body3}")

    # 4. Bad JSON-RPC version
    status4, _, body4 = http_request(
        mcp_url,
        data={"jsonrpc": "1.0", "id": "bad-1", "method": "tools/list"},
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id or ""},
    )
    check("bad jsonrpc version returns INVALID_REQUEST",
          body4.get("error", {}).get("code") == -32600,
          f"body: {body4}")

    # 5. Unknown method
    status5, _, body5 = http_request(
        mcp_url,
        data=jsonrpc("unknown/method", req_id="unk-1"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id or ""},
    )
    log_verbose(f"  unknown method response: {json.dumps(body5, indent=2)}")
    check("unknown method returns METHOD_NOT_FOUND",
          body5.get("error", {}).get("code") == -32601,
          f"body: {body5}")

    # 6. Notification (id=null) → 202
    notif_data = json.dumps({"jsonrpc": "2.0", "id": None, "method": "notifications/initialized"}).encode("utf-8")
    req = urllib.request.Request(mcp_url, data=notif_data, headers={
        "Authorization": auth_header,
        "MCP-Session-Id": session_id or "",
        "Content-Type": "application/json",
    })
    try:
        resp = urllib.request.urlopen(req, context=_SSL_CTX)
        check("notification returns 202", resp.status == 202, f"got {resp.status}")
    except urllib.error.HTTPError as e:
        check("notification returns 202", e.code == 202, f"got {e.code}")

    return session_id


def test_tools_list(base_url, auth_header, session_id):
    """Test tools/list returns all expected tools."""
    log(f"\n{Colors.CYAN}── tools/list ──{Colors.RESET}")
    mcp_url = base_url + MCP_PATH

    status, _, body = http_request(
        mcp_url,
        data=jsonrpc("tools/list", req_id="list-1"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
    )
    log_verbose(f"  tools/list response: {json.dumps(body, indent=2)[:2000]}")

    check("tools/list returns 200", status == 200, f"got {status}")
    check("tools/list has result", "result" in body, f"body keys: {list(body.keys())}")

    tools = body.get("result", {}).get("tools", [])
    tool_names = [t["name"] for t in tools]
    log_verbose(f"  found tools: {tool_names}")

    expected_tools = [
        "product_search", "product_get",
        "cart_get", "cart_add_product", "cart_update_entry", "cart_remove_entry",
        "cart_apply_voucher", "cart_remove_voucher",
        "order_get", "order_history",
        "customer_get", "customer_lookup",
        "checkout_set_delivery_address", "checkout_set_delivery_mode",
        "checkout_set_payment", "order_place","promotions_get",
        "info_get", "info_search",
    ]

    check(f"tools/list returns {len(expected_tools)} tools",
          len(tools) == len(expected_tools),
          f"got {len(tools)}: {tool_names}")

    for name in expected_tools:
        check(f"tool '{name}' registered",
              name in tool_names,
              f"missing from {tool_names}")

    # Each tool should have name, description, inputSchema
    for tool in tools:
        has_fields = all(k in tool for k in ("name", "description", "inputSchema"))
        if not has_fields:
            check(f"tool '{tool.get('name', '?')}' has required fields", False,
                  f"keys: {list(tool.keys())}")

    return tool_names


def test_tool_calls(base_url, auth_header, session_id):
    """Call each tool with sample args and verify JSON-RPC response structure."""
    log(f"\n{Colors.CYAN}── tools/call (read-only tools) ──{Colors.RESET}")
    mcp_url = base_url + MCP_PATH

    # Tools with sample args.
    # These are split into "safe" (read-only / no side effects) and "mutating" groups.
    # Read-only tools should return success with data from sample electronics catalog.
    read_tools = [
        ("product_search", {"query": "camera", "pageSize": 3}),
        ("product_get", {"code": "SMARTPHONE_X"}),  # Canon EOS 450D from electronics
        ("cart_get", {}),
        ("customer_get", {}),
        ("order_history", {"pageSize": 5}),
        ("checkout_set_delivery_mode", {}),  # no code = list modes
    ]

    for tool_name, args in read_tools:
        status, _, body = http_request(
            mcp_url,
            data=jsonrpc("tools/call", {"name": tool_name, "arguments": args}, f"call-{tool_name}"),
            headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
        )
        log_verbose(f"  {tool_name} → status={status}, body={json.dumps(body)[:300]}")

        check(f"{tool_name}: returns 200", status == 200, f"got {status}")
        check(f"{tool_name}: has result with content",
              "result" in body and "content" in body.get("result", {}),
              f"body keys: {list(body.keys())}, result keys: {list(body.get('result', {}).keys())}")

        result = body.get("result", {})
        content = result.get("content", [])
        if content:
            check(f"{tool_name}: content[0] has type=text",
                  content[0].get("type") == "text",
                  f"got: {content[0]}")
            # Tool may return isError=true if facade fails (no user session, etc.)
            # That's OK — we're testing the protocol layer, not business logic
            is_error = result.get("isError", False)
            if is_error:
                log(f"    {Colors.YELLOW}NOTE{Colors.RESET} tool returned isError=true (expected for tools needing user context)")

    # Unknown tool → error
    log(f"\n{Colors.CYAN}── tools/call (error cases) ──{Colors.RESET}")
    status, _, body = http_request(
        mcp_url,
        data=jsonrpc("tools/call", {"name": "nonexistent_tool", "arguments": {}}, "call-bad"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
    )
    check("unknown tool returns JSON-RPC error",
          "error" in body,
          f"body: {body}")
    check("unknown tool error code is INVALID_PARAMS (-32602)",
          body.get("error", {}).get("code") == -32602,
          f"got code: {body.get('error', {}).get('code')}")


def test_mutating_tools(base_url, auth_header, session_id):
    """Test cart-mutating tools (add, update, remove) in sequence."""
    log(f"\n{Colors.CYAN}── tools/call (mutating: cart workflow) ──{Colors.RESET}")
    mcp_url = base_url + MCP_PATH

    def call_tool(name, args):
        status, _, body = http_request(
            mcp_url,
            data=jsonrpc("tools/call", {"name": name, "arguments": args}, f"mut-{name}"),
            headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
        )
        result = body.get("result", {})
        content_text = ""
        if result.get("content"):
            content_text = result["content"][0].get("text", "")
        is_error = result.get("isError", False)
        log_verbose(f"  {name} → error={is_error}, text={content_text[:200]}")
        return status, body, result, is_error, content_text

    # Add product to cart
    status, body, result, is_error, text = call_tool("cart_add_product", {"productCode": "SMARTPHONE_X", "quantity": 2})
    check("cart_add_product: returns 200", status == 200, f"got {status}")
    check("cart_add_product: has result.content",
          "content" in result, f"result: {result}")
    if is_error:
        log(f"    {Colors.YELLOW}NOTE{Colors.RESET} cart_add_product returned error (may need user session): {text[:100]}")

    # Update cart entry quantity
    status, body, result, is_error, text = call_tool("cart_update_entry", {"entryNumber": 0, "quantity": 5})
    check("cart_update_entry: returns 200", status == 200, f"got {status}")
    check("cart_update_entry: has result.content", "content" in result)

    # Remove cart entry
    status, body, result, is_error, text = call_tool("cart_remove_entry", {"entryNumber": 0})
    check("cart_remove_entry: returns 200", status == 200, f"got {status}")
    check("cart_remove_entry: has result.content", "content" in result)

    # Checkout tools (will likely error without cart, but protocol should work)
    checkout_tools = [
        ("checkout_set_delivery_address", {
            "firstName": "Test", "lastName": "User",
            "line1": "123 Main St", "town": "New York",
            "postalCode": "10001", "country": "US",
        }),
        ("checkout_set_delivery_mode", {"deliveryModeCode": "standard-gross"}),
        ("checkout_set_payment", {
            "cardNumber": "4111111111111111", "cardType": "visa",
            "expiryMonth": "12", "expiryYear": "2028",
        }),
        ("order_place", {"securityCode": "123"}),
        ("customer_lookup", {"uid": "aaron.customer@hybris.com"}),
        ("order_get", {"code": "00000001"}),
    ]

    for tool_name, args in checkout_tools:
        status, body, result, is_error, text = call_tool(tool_name, args)
        check(f"{tool_name}: returns 200", status == 200, f"got {status}")
        check(f"{tool_name}: has result.content", "content" in result)
        if is_error:
            log(f"    {Colors.YELLOW}NOTE{Colors.RESET} {tool_name} returned tool error (expected without full session)")


def test_knowledge_and_swag(base_url, auth_header, session_id):
    """Smoke tests for the knowledge base + swag additions."""
    log(f"\n{Colors.CYAN}── knowledge base (OCC + MCP) ──{Colors.RESET}")
    mcp_url = base_url + MCP_PATH
    occ_base = f"{base_url}/occ/v2/electronics"

    # 1. info_get OCC — known uid returns 200 with expected fields
    status, _, body = http_request(
        f"{occ_base}/info/returns-policy",
        headers={"Authorization": auth_header},
    )
    check("OCC info/returns-policy returns 200", status == 200, f"got {status}")
    check("OCC info/returns-policy has uid",
          isinstance(body, dict) and body.get("uid") == "returns-policy",
          f"body: {str(body)[:200]}")
    check("OCC info/returns-policy category is 'policy'",
          isinstance(body, dict) and body.get("category") == "policy",
          f"got: {body.get('category') if isinstance(body, dict) else body}")

    # 2. info_get OCC — unknown uid returns 404
    status, _, _ = http_request(
        f"{occ_base}/info/does-not-exist",
        headers={"Authorization": auth_header},
    )
    check("OCC info/{unknown} returns 404", status == 404, f"got {status}")

    # 3. info_search OCC — free-text query returns results
    status, _, body = http_request(
        f"{occ_base}/info/search?q=shipping&pageSize=5",
        headers={"Authorization": auth_header},
    )
    check("OCC info/search returns 200", status == 200, f"got {status}")
    results = body.get("results", []) if isinstance(body, dict) else []
    check("OCC info/search for 'shipping' returns at least one hit",
          len(results) >= 1, f"got {len(results)} results")
    uids = [r.get("uid") for r in results]
    log_verbose(f"  info/search 'shipping' uids: {uids}")

    # 4. info_search with category filter narrows results
    status, _, body = http_request(
        f"{occ_base}/info/search?q=&category=event&pageSize=5",
        headers={"Authorization": auth_header},
    )
    check("OCC info/search?category=event returns 200", status == 200, f"got {status}")
    cats = {r.get("category") for r in body.get("results", [])} if isinstance(body, dict) else set()
    check("OCC info/search?category=event only returns event rows",
          cats == set() or cats == {"event"},
          f"got categories: {cats}")

    # 5. MCP tools/call info_get
    status, _, body = http_request(
        mcp_url,
        data=jsonrpc("tools/call",
                     {"name": "info_get", "arguments": {"uid": "shipping-info"}},
                     "call-info-get"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
    )
    check("MCP info_get returns 200", status == 200, f"got {status}")
    content = body.get("result", {}).get("content", [])
    check("MCP info_get content includes 'shipping-info'",
          any("shipping-info" in (c.get("text", "")) for c in content),
          f"content: {str(content)[:200]}")

    # 6. MCP tools/call info_search — single term so Solr's default-AND across
    #    multi-term queries doesn't bite (see smoke-info.sh for the same note).
    status, _, body = http_request(
        mcp_url,
        data=jsonrpc("tools/call",
                     {"name": "info_search", "arguments": {"query": "returns", "pageSize": 3}},
                     "call-info-search"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
    )
    check("MCP info_search returns 200", status == 200, f"got {status}")
    content = body.get("result", {}).get("content", [])
    text = content[0].get("text", "") if content else ""
    check("MCP info_search includes 'returns-policy' for 'returns'",
          "returns-policy" in text,
          f"text: {text[:300]}")

    # 7. Swag products are reachable via product_search
    log(f"\n{Colors.CYAN}── swag products (via product_search) ──{Colors.RESET}")
    status, _, body = http_request(
        mcp_url,
        data=jsonrpc("tools/call",
                     {"name": "product_search", "arguments": {"query": "hoodie", "pageSize": 5}},
                     "call-swag-hoodie"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
    )
    check("MCP product_search('hoodie') returns 200", status == 200, f"got {status}")
    text = body.get("result", {}).get("content", [{}])[0].get("text", "")
    check("product_search('hoodie') finds TS_HOODIE_ZIP",
          "TS_HOODIE_ZIP" in text,
          f"text (first 300): {text[:300]}")

    status, _, body = http_request(
        mcp_url,
        data=jsonrpc("tools/call",
                     {"name": "product_search", "arguments": {"query": "mug", "pageSize": 5}},
                     "call-swag-mug"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
    )
    text = body.get("result", {}).get("content", [{}])[0].get("text", "")
    check("product_search('mug') finds at least one TS_MUG_*",
          "TS_MUG" in text,
          f"text (first 300): {text[:300]}")


def test_session_delete(base_url, auth_header, session_id):
    """Test session deletion via DELETE."""
    log(f"\n{Colors.CYAN}── Session Cleanup ──{Colors.RESET}")
    mcp_url = base_url + MCP_PATH

    status, _, _ = http_request(
        mcp_url,
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
        method="DELETE",
    )
    check("DELETE session returns 200", status == 200, f"got {status}")

    # Verify session is gone
    status2, _, body2 = http_request(
        mcp_url,
        data=jsonrpc("tools/list", req_id="post-delete"),
        headers={"Authorization": auth_header, "MCP-Session-Id": session_id},
    )
    check("tools/list after DELETE returns session error",
          "error" in body2, f"body: {body2}")


# ── Main ────────────────────────────────────────────────────────────────────

def main():
    global verbose

    parser = argparse.ArgumentParser(description="E2E test for MCP JSON-RPC endpoint")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"Server base URL (default: {DEFAULT_BASE_URL})")
    parser.add_argument("--verbose", "-v", action="store_true", help="Print response bodies")
    parser.add_argument("--customer", action="store_true",
                        help="Use customer token (john.doe) for full cart/order testing")
    args = parser.parse_args()

    verbose = args.verbose
    base_url = args.base_url.rstrip("/")

    log(f"\n{'='*60}")
    log(f"MCP E2E Test — {base_url}")
    log(f"{'='*60}")

    # Get OAuth token
    log(f"\n{Colors.CYAN}── Authentication ──{Colors.RESET}")
    if args.customer:
        log(f"  Authenticating as customer: {CUSTOMER_EMAIL}")
        token = get_customer_token(base_url)
        if not token:
            log(f"  {Colors.YELLOW}Customer auth failed, falling back to trusted client{Colors.RESET}")
            token = get_trusted_client_token(base_url)
    else:
        log(f"  Authenticating as trusted client: {CLIENT_ID}")
        token = get_trusted_client_token(base_url)

    if not token:
        log(f"  {Colors.RED}FATAL: Could not get OAuth token. Is the server running?{Colors.RESET}")
        sys.exit(1)

    log(f"  {Colors.GREEN}OK{Colors.RESET} got token: {token[:20]}...")
    auth_header = f"Bearer {token}"

    # Run tests
    session_id = test_protocol(base_url, auth_header)
    if not session_id:
        log(f"\n{Colors.RED}FATAL: No session ID — cannot continue.{Colors.RESET}")
        sys.exit(1)

    test_tools_list(base_url, auth_header, session_id)
    test_tool_calls(base_url, auth_header, session_id)
    test_mutating_tools(base_url, auth_header, session_id)
    test_knowledge_and_swag(base_url, auth_header, session_id)
    test_session_delete(base_url, auth_header, session_id)

    # Summary
    log(f"\n{'='*60}")
    total = passed + failed
    if failed == 0:
        log(f"{Colors.GREEN}ALL {total} TESTS PASSED{Colors.RESET}")
    else:
        log(f"{Colors.GREEN}{passed} passed{Colors.RESET}, {Colors.RED}{failed} failed{Colors.RESET} out of {total}")
        log(f"\nFailures:")
        for e in errors:
            log(f"  {Colors.RED}•{Colors.RESET} {e}")
    log(f"{'='*60}\n")

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
