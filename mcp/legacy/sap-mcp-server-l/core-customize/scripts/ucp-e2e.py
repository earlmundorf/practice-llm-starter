#!/usr/bin/env python3
"""
End-to-end harness for the UCP surface (ucpcommerce extension).

Drives the UCP flows against a running local server, transport-flagged from
day one (--transport mcp is the default; --transport rest drives the Phase 7
REST binding). Assertions are written against UCP payload objects, not the
wire, so they are reused verbatim across transports. Transport-specific bits:
MCP protocol checks (initialize/tools-list/isError) run only on mcp; on rest,
client protocol bugs are HTTP 400 (UCP error envelope), the Idempotency-Key
header replaces meta["idempotency-key"], and the com.thinkshop.* custom
capabilities are skipped (MCP-only — Phase 7 REST scope is catalog/checkout/
order; see docs/adr/0002).

Sections so far:
  1. Profile   — anonymous discovery document shape (Phase 1) + Phase 2 entries
  2. Auth      — password-grant bootstrap (design R8)
  3. Catalog   — tools/list, search_catalog / lookup_catalog / get_product with
                 integer minor-unit price assertions (Phase 2)
  4. Checkout  — create_checkout / get_checkout round-trip against the
                 persisted UcpCheckoutSessionEntry store (Phase 3)
  5. Update    — update_checkout: declarative line-item diffs, destination +
                 delivery mode, derived status transition to ready_for_complete,
                 and Drools promotion discounts (BOGO mouse) in totals (Phase 4;
                 requires setup-promotions.sh + publish-promotions.groovy)
  6. Complete  — complete_checkout (mock payment handler thinkshop_mock_card,
                 meta["idempotency-key"], idempotent replay returns the SAME
                 order with no second placeOrder) + cancel_checkout (idempotent,
                 terminal) (Phase 5)
  7. Orders    — get_order (full UCP order for the just-placed purchase) +
                 list_orders history incl. the Phase 5 fixture order 00005004
                 (Phase 6)
  8. Promotions— get_promotions: com.thinkshop.promotions custom capability,
                 known rule/coupon codes (Phase 6)
  9. Knowledge — search_knowledge / get_knowledge: com.thinkshop.knowledge
                 custom capability over the Solr knowledgeIndex (Phase 6)
Schema validation shells out to the `ucp-schema` CLI when installed (best
effort; SKIP otherwise).

Usage:
    python3 core-customize/scripts/ucp-e2e.py
    python3 core-customize/scripts/ucp-e2e.py --base-url https://localhost:9002
    python3 core-customize/scripts/ucp-e2e.py --transport mcp --verbose
"""

import argparse
import json
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
import urllib.request
import urllib.error
import urllib.parse
import uuid

# Skip certificate verification for self-signed dev cert (same as test-mcp-e2e.py)
_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE

# ── Config ──────────────────────────────────────────────────────────────────

DEFAULT_BASE_URL = "https://localhost:9002"
DEFAULT_BASE_SITE = "electronics"
OAUTH_PATH = "/authorizationserver/oauth/token"

# OAuth client with ROLE_TRUSTED_CLIENT (from commercewebservices essentialdata impex)
CLIENT_ID = "trusted_client"
CLIENT_SECRET = "secret"

# Demo customer (from thinkshop project data) — the only checkout path proven
# end-to-end (design R8); used by every authenticated section from Phase 2 on.
CUSTOMER_EMAIL = "john.doe@thinkshop.com"
CUSTOMER_PASSWORD = "1234"

# Pinned UCP versions are dated calver strings, e.g. 2026-04-08.
UCP_VERSION_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

# Known ThinkShop fixtures (sampledatamcp projectdata-10-products.impex).
# Prices are asserted in INTEGER MINOR UNITS — the storefront major-unit
# price × 100 for USD (the silent-100×-bug guard).
KNOWN_SKU = "LAPTOP_PRO_15"
KNOWN_SKU_PRICE_MINOR = 129999          # $1299.99
SECOND_SKU = "WIRELESS_GAMING_MOUSE"
SECOND_SKU_PRICE_MINOR = 7999           # $79.99

# UCP MCP binding tool names (pinned spec 2026-04-08). Grows phase by phase:
# catalog (Phase 2) + create/get checkout (Phase 3) + update (Phase 4)
# + complete/cancel (Phase 5) + order and custom capabilities (Phase 6).
EXPECTED_CATALOG_TOOLS = {"search_catalog", "lookup_catalog", "get_product"}
EXPECTED_CHECKOUT_TOOLS = {"create_checkout", "get_checkout", "update_checkout",
                           "complete_checkout", "cancel_checkout"}
EXPECTED_ORDER_TOOLS = {"get_order", "list_orders"}
EXPECTED_CUSTOM_TOOLS = {"get_promotions", "search_knowledge", "get_knowledge"}
EXPECTED_TOOLS = (EXPECTED_CATALOG_TOOLS | EXPECTED_CHECKOUT_TOOLS
                  | EXPECTED_ORDER_TOOLS | EXPECTED_CUSTOM_TOOLS)

# The single declared mock payment handler (design R9) — the only handler_id
# complete_checkout accepts; any credential token is accepted for it.
PAYMENT_HANDLER_ID = "thinkshop_mock_card"

# Destination fixtures (sampledatamcp: john.doe's deliverable US address,
# ZoneDeliveryMode thinkshop-standard $5.99 / thinkshop-express $14.99; the
# electronics BaseStore delivers to US only).
DESTINATION = {
    "first_name": "John", "last_name": "Doe",
    "line1": "100 Main St", "city": "New York",
    "postal_code": "10001", "country": "US",
}
DELIVERY_MODE_STANDARD = "thinkshop-standard"
SHIPPING_STANDARD_MINOR = 599            # $5.99
# Published Drools promotions (setup-promotions.groovy): bogo_mouse gives 100%
# off the cheapest of each pair of WIRELESS_GAMING_MOUSE; free_shipping_1000
# swaps the delivery mode to thinkshop-free-delivery on carts >= $1,000.
BOGO_DISCOUNT_MINOR = SECOND_SKU_PRICE_MINOR    # one mouse free
FREE_DELIVERY_MODE = "thinkshop-free-delivery"

# The UCP purchase placed during Phase 5 verification — a durable fixture in
# john.doe's order history alongside the THINK-000x impex orders.
UCP_FIXTURE_ORDER = "00005004"
# Known promotion rules/coupons (setup-promotions.groovy, published to Drools).
KNOWN_PROMO_RULES = {"bogo_mouse", "free_shipping_1000"}
KNOWN_COUPON = "LAPTOP10"
# Known knowledge-base entry (sampledatamcp projectdata-50-knowledge.impex).
KNOWN_KB_UID = "returns-policy"

HARNESS_AGENT_PROFILE = {"profile": "https://ucp-e2e.invalid/.well-known/ucp"}


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
skipped = 0
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


def skip(name, reason):
    global skipped
    skipped += 1
    log(f"  {Colors.YELLOW}SKIP{Colors.RESET} {name} — {reason}")


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
    except urllib.error.URLError as e:
        # Server unreachable — surface as a synthetic failure instead of a traceback.
        return 0, {}, {"_error": str(e.reason)}


# ── OAuth ───────────────────────────────────────────────────────────────────

def get_customer_token(base_url):
    """Password-grant token for the demo customer (design R8). Returns None on failure."""
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
    except Exception:
        return None


# ── Schema validation (best-effort) ─────────────────────────────────────────

def ucp_schema_validate(payload, label, schema=None, op=None):
    """
    Shell out to the official `ucp-schema` CLI when available; SKIP otherwise.
    (Fallback to bundled JSON Schemas from a cloned ucp-schema repo is noted
    as an open question in the structure outline.)
    """
    cli = shutil.which("ucp-schema")
    if cli is None:
        skip(f"ucp-schema validate ({label})", "ucp-schema CLI not on PATH")
        return

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(payload, f)
        path = f.name

    cmd = [cli, "validate", path]
    if schema:
        cmd += ["--schema", schema]
    if op:
        cmd += ["--op", op]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        log_verbose(f"  ucp-schema output: {result.stdout.strip()} {result.stderr.strip()}")
        check(f"ucp-schema validate ({label})", result.returncode == 0,
              f"rc={result.returncode}: {result.stderr.strip()[:300]}")
    except Exception as e:
        check(f"ucp-schema validate ({label})", False, str(e))


# ── Test Sections ───────────────────────────────────────────────────────────

def test_profile(base_url, base_site):
    """Phase 1: fetch the discovery profile anonymously and assert its shape."""
    log(f"\n{Colors.CYAN}── UCP profile (anonymous discovery) ──{Colors.RESET}")
    profile_url = f"{base_url}/occ/v2/{base_site}/.well-known/ucp"
    log_verbose(f"  GET {profile_url} (no Authorization header)")

    # Deliberately no Authorization header — the profile must be public (R6).
    status, headers, body = http_request(profile_url)
    log_verbose(f"  profile response: {json.dumps(body, indent=2)[:1500]}")

    if status == 0:
        check("profile endpoint reachable", False, body.get("_error", "connection failed"))
        return None

    check("profile returns 200 without auth", status == 200, f"got {status}")
    check("profile is a JSON object", isinstance(body, dict), f"body: {str(body)[:200]}")
    if not isinstance(body, dict):
        return None

    content_type = headers.get("Content-Type", "")
    check("profile Content-Type is JSON", "application/json" in content_type, f"got {content_type!r}")
    check("profile allows any origin (CORS)",
          headers.get("Access-Control-Allow-Origin") == "*",
          f"got {headers.get('Access-Control-Allow-Origin')!r}")

    # Shape corrected against the official discovery schema + sample server
    # (ADR 0003): everything lives INSIDE the top-level ucp object, and
    # services/capabilities/payment_handlers are REGISTRIES — maps keyed by
    # reverse-domain name whose values are LISTS of version entries. This is
    # exactly what the OOTB reference client parses
    # (ucp_data.get("payment_handlers", {}).values()).
    ucp = body.get("ucp")
    check("profile has ucp block", isinstance(ucp, dict), f"got {ucp!r}")
    ucp = ucp or {}
    version = ucp.get("version", "")
    check("ucp.version is a dated calver string",
          bool(UCP_VERSION_RE.match(version)), f"got {version!r}")

    check("no top-level capabilities outside the ucp object",
          "capabilities" not in body, f"got {sorted(body)!r}")
    check("ucp.capabilities is a registry object (dict of lists)",
          isinstance(ucp.get("capabilities"), dict)
          and all(isinstance(v, list) for v in (ucp.get("capabilities") or {}).values()),
          f"got {ucp.get('capabilities')!r}")
    check("ucp.services is a registry object",
          isinstance(ucp.get("services"), dict), f"got {ucp.get('services')!r}")
    check("ucp.payment_handlers is a registry object (dict of lists)",
          isinstance(ucp.get("payment_handlers"), dict)
          and all(isinstance(v, list) for v in (ucp.get("payment_handlers") or {}).values()),
          f"got {ucp.get('payment_handlers')!r}")

    caps = ucp.get("capabilities") or {}
    check("profile advertises dev.ucp.shopping.catalog",
          "dev.ucp.shopping.catalog" in caps, f"got {sorted(caps)!r}")
    catalog_cap = (caps.get("dev.ucp.shopping.catalog") or [{}])[0]
    check("catalog capability version is a dated calver string",
          bool(UCP_VERSION_RE.match(catalog_cap.get("version", ""))),
          f"got {catalog_cap.get('version')!r}")
    check("capability entries carry no name field (the registry key is the name)",
          "name" not in catalog_cap, f"got {catalog_cap!r}")
    check("profile advertises dev.ucp.shopping.checkout",
          "dev.ucp.shopping.checkout" in caps, f"got {sorted(caps)!r}")
    fulfillment_cap = (caps.get("dev.ucp.shopping.fulfillment") or [{}])[0]
    check("profile advertises dev.ucp.shopping.fulfillment extending checkout",
          fulfillment_cap.get("extends") == "dev.ucp.shopping.checkout",
          f"got {fulfillment_cap!r}")
    check("profile advertises dev.ucp.shopping.order",
          "dev.ucp.shopping.order" in caps, f"got {sorted(caps)!r}")
    check("profile advertises the custom com.thinkshop.* capabilities",
          {"com.thinkshop.promotions", "com.thinkshop.knowledge"} <= set(caps),
          f"got {sorted(caps)!r}")
    custom_caps = [e for name in ("com.thinkshop.promotions", "com.thinkshop.knowledge")
                   for e in caps.get(name) or []]
    check("custom capabilities carry dated calver versions",
          custom_caps and all(UCP_VERSION_RE.match(c.get("version", "")) for c in custom_caps),
          f"got {custom_caps!r}")

    # Service registry: dev.ucp.shopping → a LIST of transport entries.
    shopping = (ucp.get("services") or {}).get("dev.ucp.shopping") or []
    check("dev.ucp.shopping service entries are a list of transports",
          isinstance(shopping, list) and len(shopping) >= 2, f"got {shopping!r}")
    transports = {e.get("transport"): e for e in shopping if isinstance(e, dict)}
    mcp_endpoint = (transports.get("mcp") or {}).get("endpoint", "")
    check("profile advertises the mcp transport endpoint",
          mcp_endpoint.endswith(f"/occ/v2/{base_site}/ucp/mcp"), f"got {mcp_endpoint!r}")
    rest_endpoint = (transports.get("rest") or {}).get("endpoint", "")
    check("profile advertises the rest transport base endpoint",
          rest_endpoint.endswith(f"/occ/v2/{base_site}/ucp"), f"got {rest_endpoint!r}")

    # Payment-handler registry: flatten the values exactly like the OOTB
    # reference client's discovery step does, then match on id.
    flattened = [h for handlers in (ucp.get("payment_handlers") or {}).values()
                 for h in handlers if isinstance(h, dict)]
    handler_ids = [h.get("id") for h in flattened]
    check(f"profile declares exactly the mock payment handler {PAYMENT_HANDLER_ID}",
          handler_ids == [PAYMENT_HANDLER_ID], f"got {handler_ids!r}")
    check("the mock handler carries a human-readable name (the client logs it)",
          bool((flattened or [{}])[0].get("name")), f"got {flattened!r}")

    ucp_schema_validate(body, "profile")
    return body


# ── UCP MCP binding helpers ─────────────────────────────────────────────────

_rpc_id = 0


def mcp_rpc(base_url, base_site, token, method, params=None, notification=False):
    """POST one JSON-RPC request to the UCP MCP endpoint. Returns (status, headers, body)."""
    global _rpc_id
    payload = {"jsonrpc": "2.0", "method": method}
    if not notification:
        _rpc_id += 1
        payload["id"] = _rpc_id
    if params is not None:
        payload["params"] = params
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    url = f"{base_url}/occ/v2/{base_site}/ucp/mcp"
    log_verbose(f"  POST {url} {method}")
    return http_request(url, data=payload, headers=headers, method="POST")


def mcp_tool_call(base_url, base_site, token, tool, arguments, meta=None):
    """tools/call with UCP per-call meta. Returns (status, body, payload_dict_or_None)."""
    params = {"name": tool, "arguments": arguments,
              "meta": meta if meta is not None else {"ucp-agent": HARNESS_AGENT_PROFILE}}
    status, _, body = mcp_rpc(base_url, base_site, token, "tools/call", params)
    payload = None
    try:
        content = body["result"]["content"][0]["text"]
        payload = json.loads(content)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError):
        pass
    log_verbose(f"  {tool} payload: {json.dumps(payload, indent=2)[:1200] if payload else body}")
    return status, body, payload


# ── UCP REST binding helpers (Phase 7) ──────────────────────────────────────

# Which wire the capability sections drive; set from --transport in main().
TRANSPORT = "mcp"


def rest_call(base_url, base_site, token, tool, arguments, meta=None):
    """
    One logical UCP operation over the REST binding (thin adapters over the
    same capability services — design R12). Resource naming: /checkout-sessions
    per ADR 0002 (runbook §9.1 ambiguity resolved to the researched Google
    Native-checkout shape). Returns (status, body, payload) exactly like
    mcp_tool_call so section assertions are reused verbatim.
    """
    base = f"{base_url}/occ/v2/{base_site}/ucp"
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    # REST spelling of the per-call agent metadata (meta["ucp-agent"] on MCP).
    headers["UCP-Agent"] = f'profile="{HARNESS_AGENT_PROFILE["profile"]}"'
    key = (meta or {}).get("idempotency-key")
    if key:
        headers["Idempotency-Key"] = key

    def q(params):
        cleaned = {k: v for k, v in params.items() if v is not None}
        return ("?" + urllib.parse.urlencode(cleaned)) if cleaned else ""

    def path_id(value):
        return urllib.parse.quote(str(value if value is not None else ""), safe="")

    a = arguments or {}
    if tool == "search_catalog":
        method, url, body = "GET", f"{base}/catalog/search" + q(
            {"query": a.get("query"), "page": a.get("page"), "page_size": a.get("page_size")}), None
    elif tool == "lookup_catalog":
        method, url, body = "GET", f"{base}/catalog/lookup" + q(
            {"ids": ",".join(a.get("ids") or [])}), None
    elif tool == "get_product":
        method, url, body = "GET", f"{base}/products/{path_id(a.get('id'))}", None
    elif tool == "create_checkout":
        method, url, body = "POST", f"{base}/checkout-sessions", a.get("checkout")
    elif tool == "get_checkout":
        method, url, body = "GET", f"{base}/checkout-sessions/{path_id(a.get('id'))}", None
    elif tool == "update_checkout":
        method, url, body = "PUT", f"{base}/checkout-sessions/{path_id(a.get('id'))}", a.get("checkout")
    elif tool == "complete_checkout":
        method, url, body = "POST", f"{base}/checkout-sessions/{path_id(a.get('id'))}/complete", a.get("checkout")
    elif tool == "cancel_checkout":
        # Cancel carries no checkout payload on either binding.
        method, url, body = "POST", f"{base}/checkout-sessions/{path_id(a.get('id'))}/cancel", {}
    elif tool == "get_order":
        method, url, body = "GET", f"{base}/orders/{path_id(a.get('id'))}", None
    elif tool == "list_orders":
        statuses = a.get("statuses")
        method, url, body = "GET", f"{base}/orders" + q(
            {"page": a.get("page"), "page_size": a.get("page_size"),
             "statuses": ",".join(statuses) if statuses else None}), None
    else:
        raise ValueError(f"{tool} has no REST route (com.thinkshop.* capabilities are MCP-only)")

    log_verbose(f"  {method} {url}")
    status, _, parsed = http_request(url, data=body, headers=headers, method=method)
    payload = parsed if isinstance(parsed, dict) and "_raw" not in parsed and "_error" not in parsed else None
    log_verbose(f"  {tool} [{status}] payload: "
                f"{json.dumps(payload, indent=2)[:1200] if payload else parsed}")
    return status, parsed, payload


def ucp_call(base_url, base_site, token, tool, arguments, meta=None):
    """Transport-agnostic capability call: same (status, body, payload) contract."""
    if TRANSPORT == "rest":
        return rest_call(base_url, base_site, token, tool, arguments, meta)
    return mcp_tool_call(base_url, base_site, token, tool, arguments, meta)


def protocol_rejected(status, body):
    """
    True when the transport rejected the call as a CLIENT PROTOCOL BUG
    (schema-shape violations, missing idempotency key): an MCP isError tool
    result, or HTTP 400 with the UCP error envelope on REST. UCP business
    errors are never protocol rejections — they are 200/non-isError payloads
    with ucp.status="error" + messages[].
    """
    if TRANSPORT == "rest":
        return status == 400
    return isinstance(body, dict) and (body.get("result") or {}).get("isError") is True


def assert_ucp_envelope(payload, label, expected_status="success"):
    """Every UCP capability payload leads with a ucp envelope."""
    ucp = (payload or {}).get("ucp") or {}
    check(f"{label}: ucp envelope has dated version",
          bool(UCP_VERSION_RE.match(ucp.get("version", ""))), f"got {ucp.get('version')!r}")
    check(f"{label}: ucp.status is {expected_status}",
          ucp.get("status") == expected_status, f"got {ucp.get('status')!r}")


def test_auth(base_url):
    """Password-grant auth bootstrap (design R8 / diagram S1)."""
    log(f"\n{Colors.CYAN}── Auth bootstrap (password grant, {CUSTOMER_EMAIL}) ──{Colors.RESET}")
    token = get_customer_token(base_url)
    check("password-grant token obtained", token is not None,
          "OAuth token request failed — is the server initialized?")
    return token


def test_catalog(base_url, base_site, token):
    """Phase 2: dev.ucp.shopping.catalog (Phase 7: same assertions over REST)."""
    log(f"\n{Colors.CYAN}── Catalog capability ({TRANSPORT.upper()} binding) ──{Colors.RESET}")

    # The endpoints are @Secured — no token must mean 401, not data.
    if TRANSPORT == "mcp":
        status, _, _ = mcp_rpc(base_url, base_site, None, "tools/list")
    else:
        status, _, _ = rest_call(base_url, base_site, None, "search_catalog", {"query": "laptop"})
    check("unauthenticated call is rejected (401)", status == 401, f"got {status}")

    if TRANSPORT == "mcp":
        # A generic MCP client's initialize is tolerated harmlessly (stateless).
        status, headers, body = mcp_rpc(base_url, base_site, token, "initialize",
                                        {"protocolVersion": "2025-11-25",
                                         "clientInfo": {"name": "ucp-e2e", "version": "1.0"}})
        check("initialize tolerated (200, no error)",
              status == 200 and body.get("error") is None, f"status {status}, body {str(body)[:200]}")
        check("initialize returns serverInfo",
              isinstance(body.get("result", {}).get("serverInfo"), dict), f"got {body.get('result')!r}")
        check("initialize issues no session header",
              not any(h.lower() == "mcp-session-id" for h in headers), f"headers {list(headers)}")

        # notifications/initialized → 202, empty body.
        status, _, _ = mcp_rpc(base_url, base_site, token, "notifications/initialized", notification=True)
        check("notification answered with 202", status == 202, f"got {status}")

        # tools/list — exactly the tools shipped so far (catalog + checkout +
        # order + custom com.thinkshop.* capabilities).
        status, _, body = mcp_rpc(base_url, base_site, token, "tools/list")
        tools = body.get("result", {}).get("tools", [])
        tool_names = {t.get("name") for t in tools if isinstance(t, dict)}
        check("tools/list returns exactly the catalog + checkout + order + custom tools",
              tool_names == EXPECTED_TOOLS, f"got {sorted(tool_names)!r}")

        # Unknown tool → JSON-RPC invalid-params error.
        status, body, _ = mcp_tool_call(base_url, base_site, token, "definitely_not_a_tool", {})
        check("unknown tool returns -32602",
              body.get("error", {}).get("code") == -32602, f"got {body.get('error')!r}")

    # search_catalog for a known SKU keyword.
    status, body, payload = ucp_call(base_url, base_site, token,
                                     "search_catalog", {"query": "laptop"})
    check("search_catalog returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is None:
        return
    assert_ucp_envelope(payload, "search_catalog")
    products = payload.get("products") or []
    check("search_catalog returns products", len(products) > 0, "no products — is Solr indexed?")
    check("every product price is integer minor units",
          all(isinstance(p.get("price"), int) for p in products if p.get("price") is not None)
          and any(isinstance(p.get("price"), int) for p in products),
          f"prices: {[p.get('price') for p in products]!r}")
    known = next((p for p in products if p.get("id") == KNOWN_SKU), None)
    check(f"search_catalog finds {KNOWN_SKU}", known is not None,
          f"got ids {[p.get('id') for p in products]!r}")
    if known:
        check(f"{KNOWN_SKU} search price is {KNOWN_SKU_PRICE_MINOR} minor units",
              known.get("price") == KNOWN_SKU_PRICE_MINOR, f"got {known.get('price')!r}")
        check(f"{KNOWN_SKU} currency is USD", known.get("currency") == "USD",
              f"got {known.get('currency')!r}")
    check("search_catalog includes pagination",
          isinstance(payload.get("pagination"), dict), f"got {payload.get('pagination')!r}")
    ucp_schema_validate(payload, "search_catalog response")

    # get_product for the known SKU — the ×100 spot check on the detail path.
    status, body, payload = ucp_call(base_url, base_site, token,
                                     "get_product", {"id": KNOWN_SKU})
    check("get_product returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is not None:
        assert_ucp_envelope(payload, "get_product")
        product = payload.get("product") or {}
        check(f"get_product returns {KNOWN_SKU}", product.get("id") == KNOWN_SKU,
              f"got {product.get('id')!r}")
        check(f"get_product price is {KNOWN_SKU_PRICE_MINOR} minor units (storefront $1299.99)",
              product.get("price") == KNOWN_SKU_PRICE_MINOR, f"got {product.get('price')!r}")
        check("get_product has availability", bool(product.get("availability")),
              f"got {product.get('availability')!r}")
        ucp_schema_validate(payload, "get_product response")

    # lookup_catalog batch — known ids resolve, price integrity on a second SKU.
    status, body, payload = ucp_call(base_url, base_site, token,
                                     "lookup_catalog", {"ids": [KNOWN_SKU, SECOND_SKU]})
    check("lookup_catalog returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is not None:
        assert_ucp_envelope(payload, "lookup_catalog")
        by_id = {p.get("id"): p for p in payload.get("products") or []}
        check("lookup_catalog resolves both known ids",
              set(by_id) == {KNOWN_SKU, SECOND_SKU}, f"got {sorted(by_id)!r}")
        check(f"{SECOND_SKU} lookup price is {SECOND_SKU_PRICE_MINOR} minor units",
              by_id.get(SECOND_SKU, {}).get("price") == SECOND_SKU_PRICE_MINOR,
              f"got {by_id.get(SECOND_SKU, {}).get('price')!r}")

    # lookup with one unknown id → partial success + recoverable message.
    status, body, payload = ucp_call(base_url, base_site, token,
                                     "lookup_catalog", {"ids": [KNOWN_SKU, "NO_SUCH_SKU"]})
    if payload is not None:
        msgs = payload.get("messages") or []
        check("lookup_catalog reports unknown id in messages[]",
              any(m.get("code") == "not_found" and m.get("severity") == "recoverable"
                  for m in msgs), f"got {msgs!r}")
        check("lookup_catalog still resolves the known id",
              any(p.get("id") == KNOWN_SKU for p in payload.get("products") or []),
              f"got {payload.get('products')!r}")

    # get_product for an unknown id — UCP business error, not a transport error.
    status, body, payload = ucp_call(base_url, base_site, token,
                                     "get_product", {"id": "NO_SUCH_SKU"})
    check("get_product unknown id still returns 200", status == 200, f"got {status}")
    if payload is not None:
        assert_ucp_envelope(payload, "get_product (unknown id)", expected_status="error")
        msgs = payload.get("messages") or []
        check("unknown id yields unrecoverable not_found message",
              any(m.get("code") == "not_found" and m.get("severity") == "unrecoverable"
                  for m in msgs), f"got {msgs!r}")


def test_checkout_create_get(base_url, base_site, token):
    """Phase 3: create_checkout / get_checkout (R5 store; Phase 7: over REST too)."""
    log(f"\n{Colors.CYAN}── Checkout capability: create/get ({TRANSPORT.upper()} binding) ──{Colors.RESET}")

    buyer = {"first_name": "John", "last_name": "Doe", "email": CUSTOMER_EMAIL}
    checkout_req = {
        "line_items": [{"item": {"id": SECOND_SKU}, "quantity": 1}],
        "buyer": buyer,
    }

    # create_checkout — the payload must NOT contain an id; the response mints one.
    status, body, payload = ucp_call(base_url, base_site, token,
                                     "create_checkout", {"checkout": checkout_req})
    check("create_checkout returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is None:
        return
    check("create_checkout is not rejected as a protocol error",
          not protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")
    assert_ucp_envelope(payload, "create_checkout")

    checkout_id = payload.get("id") or ""
    check("create_checkout mints an opaque ucp_chk_ id",
          checkout_id.startswith("ucp_chk_"), f"got {checkout_id!r}")
    check("new checkout status is incomplete",
          payload.get("status") == "incomplete", f"got {payload.get('status')!r}")
    check("checkout currency is USD", payload.get("currency") == "USD",
          f"got {payload.get('currency')!r}")

    line_items = payload.get("line_items") or []
    check("checkout has exactly one line item", len(line_items) == 1, f"got {len(line_items)}")
    li = line_items[0] if line_items else {}
    check(f"line item is {SECOND_SKU} qty 1",
          li.get("item", {}).get("id") == SECOND_SKU and li.get("quantity") == 1,
          f"got {li!r}")
    check(f"line item unit price is {SECOND_SKU_PRICE_MINOR} minor units (storefront $79.99)",
          li.get("item", {}).get("price") == SECOND_SKU_PRICE_MINOR,
          f"got {li.get('item', {}).get('price')!r}")

    totals = {t.get("type"): t.get("amount") for t in payload.get("totals") or []}
    check(f"totals.subtotal is {SECOND_SKU_PRICE_MINOR} minor units",
          totals.get("subtotal") == SECOND_SKU_PRICE_MINOR, f"got {totals!r}")
    check("totals.total is an integer amount",
          isinstance(totals.get("total"), int), f"got {totals!r}")
    check("buyer is echoed back",
          (payload.get("buyer") or {}).get("email") == CUSTOMER_EMAIL,
          f"got {payload.get('buyer')!r}")
    ucp_schema_validate(payload, "create_checkout response")

    # get_checkout round-trip — separate stateless call, id addresses the resource.
    status, body, got = ucp_call(base_url, base_site, token,
                                 "get_checkout", {"id": checkout_id})
    check("get_checkout returns a parseable UCP payload", got is not None,
          f"status {status}, body {str(body)[:300]}")
    if got is not None:
        assert_ucp_envelope(got, "get_checkout")
        check("get_checkout echoes the same id", got.get("id") == checkout_id,
              f"got {got.get('id')!r}")
        check("get_checkout status is still incomplete",
              got.get("status") == "incomplete", f"got {got.get('status')!r}")
        got_totals = {t.get("type"): t.get("amount") for t in got.get("totals") or []}
        check("get_checkout totals match the created checkout",
              got_totals == totals, f"created {totals!r} vs got {got_totals!r}")
        got_li = (got.get("line_items") or [{}])[0]
        check("get_checkout line item round-trips",
              got_li.get("item", {}).get("id") == SECOND_SKU and got_li.get("quantity") == 1,
              f"got {got_li!r}")
        check("get_checkout buyer is persisted on the entry",
              (got.get("buyer") or {}).get("email") == CUSTOMER_EMAIL,
              f"got {got.get('buyer')!r}")
        ucp_schema_validate(got, "get_checkout response")

    # Unknown checkout id → UCP business error payload, never a transport error.
    status, body, missing = ucp_call(base_url, base_site, token,
                                     "get_checkout", {"id": "ucp_chk_doesnotexist"})
    check("get_checkout unknown id still returns 200", status == 200, f"got {status}")
    check("get_checkout unknown id is not rejected as a protocol error",
          not protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")
    if missing is not None:
        assert_ucp_envelope(missing, "get_checkout (unknown id)", expected_status="error")
        msgs = missing.get("messages") or []
        check("unknown checkout id yields unrecoverable not_found message",
              any(m.get("code") == "not_found" and m.get("severity") == "unrecoverable"
                  for m in msgs), f"got {msgs!r}")

    # create_checkout with only an unknown SKU → error payload, no id minted.
    status, body, bad = ucp_call(base_url, base_site, token, "create_checkout",
                                 {"checkout": {"line_items": [{"item": {"id": "NO_SUCH_SKU"},
                                                               "quantity": 1}]}})
    if bad is not None:
        assert_ucp_envelope(bad, "create_checkout (unknown SKU)", expected_status="error")
        check("failed create mints no checkout id", bad.get("id") is None,
              f"got {bad.get('id')!r}")
        msgs = bad.get("messages") or []
        check("failed create reports the unknown item in messages[]",
              any(m.get("code") in ("not_found", "invalid_request") for m in msgs), f"got {msgs!r}")

    return checkout_id


def test_checkout_update(base_url, base_site, token):
    """Phase 4: update_checkout — diffs, destination, derived status, promotions."""
    log(f"\n{Colors.CYAN}── Checkout capability: update ({TRANSPORT.upper()} binding) ──{Colors.RESET}")

    def totals_of(payload):
        return {t.get("type"): t.get("amount") for t in payload.get("totals") or []}

    def items_of(payload):
        return {li.get("item", {}).get("id"): li for li in payload.get("line_items") or []}

    # Fresh checkout: one mouse, buyer attached.
    _, body, payload = ucp_call(base_url, base_site, token, "create_checkout",
                                {"checkout": {"line_items": [{"item": {"id": SECOND_SKU},
                                                              "quantity": 1}],
                                              "buyer": {"first_name": "John", "last_name": "Doe",
                                                        "email": CUSTOMER_EMAIL}}})
    checkout_id = (payload or {}).get("id")
    check("update section: fresh checkout created", bool(checkout_id),
          f"body {str(body)[:300]}")
    if not checkout_id:
        return

    # 1. Quantity change (1 → 2 mice) — the BOGO promotion fires during
    #    recalculation: one mouse free, discount visible in totals.
    status, body, upd = ucp_call(base_url, base_site, token, "update_checkout",
                                 {"id": checkout_id,
                                  "checkout": {"line_items": [{"item": {"id": SECOND_SKU},
                                                               "quantity": 2}]}})
    check("update_checkout returns a parseable UCP payload", upd is not None,
          f"status {status}, body {str(body)[:300]}")
    if upd is None:
        return
    assert_ucp_envelope(upd, "update_checkout (quantity)")
    check("update echoes the same checkout id", upd.get("id") == checkout_id,
          f"got {upd.get('id')!r}")
    check("mouse quantity is now 2",
          items_of(upd).get(SECOND_SKU, {}).get("quantity") == 2,
          f"got {upd.get('line_items')!r}")
    totals = totals_of(upd)
    # Discounts are NEGATIVE on the wire (official total.json — ADR 0003).
    check(f"BOGO discount of {-BOGO_DISCOUNT_MINOR} minor units appears in totals",
          totals.get("discount") == -BOGO_DISCOUNT_MINOR, f"got {totals!r}")
    check(f"total is {SECOND_SKU_PRICE_MINOR} (2 mice, one free, no shipping yet)",
          totals.get("total") == SECOND_SKU_PRICE_MINOR, f"got {totals!r}")
    check("total is the LAST totals entry (clients read totals[-1])",
          (upd.get("totals") or [{}])[-1].get("type") == "total",
          f"got {upd.get('totals')!r}")
    check("status is still incomplete before a destination is set",
          upd.get("status") == "incomplete", f"got {upd.get('status')!r}")
    ucp_schema_validate(upd, "update_checkout (quantity) response")

    # 2. Destination (address + delivery mode) → derived ready_for_complete.
    status, body, upd = ucp_call(base_url, base_site, token, "update_checkout",
                                 {"id": checkout_id,
                                  "checkout": {"fulfillment": {
                                      "destination": DESTINATION,
                                      "delivery_mode": DELIVERY_MODE_STANDARD}}})
    check("destination update returns a parseable UCP payload", upd is not None,
          f"status {status}, body {str(body)[:300]}")
    if upd is None:
        return
    assert_ucp_envelope(upd, "update_checkout (destination)")
    check("status transitions to ready_for_complete",
          upd.get("status") == "ready_for_complete", f"got {upd.get('status')!r}")
    fulfillment = upd.get("fulfillment") or {}
    check("fulfillment echoes the applied destination",
          (fulfillment.get("destination") or {}).get("city") == DESTINATION["city"],
          f"got {fulfillment!r}")
    check("fulfillment echoes the applied delivery mode",
          fulfillment.get("delivery_mode") == DELIVERY_MODE_STANDARD, f"got {fulfillment!r}")
    totals = totals_of(upd)
    # Delivery cost travels under the well-known type "fulfillment" (ADR 0003).
    check(f"fulfillment cost of {SHIPPING_STANDARD_MINOR} minor units appears in totals",
          totals.get("fulfillment") == SHIPPING_STANDARD_MINOR, f"got {totals!r}")
    check("BOGO discount survives the destination update",
          totals.get("discount") == -BOGO_DISCOUNT_MINOR, f"got {totals!r}")
    expected_total = SECOND_SKU_PRICE_MINOR + SHIPPING_STANDARD_MINOR
    check(f"total is {expected_total} (discounted mice + standard shipping)",
          totals.get("total") == expected_total, f"got {totals!r}")
    ucp_schema_validate(upd, "update_checkout (destination) response")

    # 3. The derived status is persisted on the entry (stateless re-read).
    _, _, got = ucp_call(base_url, base_site, token, "get_checkout", {"id": checkout_id})
    check("get_checkout sees the persisted ready_for_complete status",
          (got or {}).get("status") == "ready_for_complete",
          f"got {(got or {}).get('status')!r}")

    # 4. Declarative diff: desired state [mouse×2, laptop×1] adds the laptop.
    #    The cart now tops $1,000, so the free_shipping_1000 promotion swaps
    #    the delivery mode to free delivery — a second promotion visible via UCP.
    status, body, upd = ucp_call(base_url, base_site, token, "update_checkout",
                                 {"id": checkout_id,
                                  "checkout": {"line_items": [
                                      {"item": {"id": SECOND_SKU}, "quantity": 2},
                                      {"item": {"id": KNOWN_SKU}, "quantity": 1}]}})
    check("add-item update returns a parseable UCP payload", upd is not None,
          f"status {status}, body {str(body)[:300]}")
    if upd is not None:
        items = items_of(upd)
        check("laptop was added alongside the mice",
              set(items) == {SECOND_SKU, KNOWN_SKU}, f"got {sorted(items)!r}")
        totals = totals_of(upd)
        check("BOGO discount still applies with the laptop in the cart",
              totals.get("discount") == -BOGO_DISCOUNT_MINOR, f"got {totals!r}")
        check("free-shipping promotion swapped the delivery mode (cart >= $1,000)",
              (upd.get("fulfillment") or {}).get("delivery_mode") == FREE_DELIVERY_MODE,
              f"got {(upd.get('fulfillment') or {}).get('delivery_mode')!r}")
        expected_total = KNOWN_SKU_PRICE_MINOR + SECOND_SKU_PRICE_MINOR  # mice pair BOGO'd, shipping free
        check(f"total is {expected_total} (laptop + discounted mice, free shipping)",
              totals.get("total") == expected_total, f"got {totals!r}")
        check("status stays ready_for_complete",
              upd.get("status") == "ready_for_complete", f"got {upd.get('status')!r}")

    # 5. Declarative diff: dropping the laptop from the desired state removes it.
    status, body, upd = ucp_call(base_url, base_site, token, "update_checkout",
                                 {"id": checkout_id,
                                  "checkout": {"line_items": [
                                      {"item": {"id": SECOND_SKU}, "quantity": 2}]}})
    if upd is not None:
        items = items_of(upd)
        check("laptop was removed by its absence from the desired line_items",
              set(items) == {SECOND_SKU}, f"got {sorted(items)!r}")
        check("mouse discount still present after the removal",
              totals_of(upd).get("discount") == -BOGO_DISCOUNT_MINOR,
              f"got {totals_of(upd)!r}")
        check("status stays ready_for_complete after the removal",
              upd.get("status") == "ready_for_complete", f"got {upd.get('status')!r}")

    # 6. Unknown checkout id → UCP business error payload, never a transport error.
    status, body, missing = ucp_call(base_url, base_site, token, "update_checkout",
                                     {"id": "ucp_chk_doesnotexist",
                                      "checkout": {"line_items": [{"item": {"id": SECOND_SKU},
                                                                   "quantity": 1}]}})
    check("update_checkout unknown id still returns 200", status == 200, f"got {status}")
    check("update_checkout unknown id is not rejected as a protocol error",
          not protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")
    if missing is not None:
        assert_ucp_envelope(missing, "update_checkout (unknown id)", expected_status="error")
        msgs = missing.get("messages") or []
        check("unknown checkout id yields unrecoverable not_found message",
              any(m.get("code") == "not_found" and m.get("severity") == "unrecoverable"
                  for m in msgs), f"got {msgs!r}")

    # 7. Payload-id rule (corrected in ADR 0003): the SDK update-request shape
    #    carries an id and the OOTB reference client sends it — an id MATCHING
    #    the addressed checkout is accepted; a MISMATCH is still a protocol bug.
    status, body, echo = ucp_call(base_url, base_site, token, "update_checkout",
                                  {"id": checkout_id,
                                   "checkout": {"id": checkout_id,
                                                "line_items": [{"item": {"id": SECOND_SKU},
                                                                "quantity": 2}]}})
    check("checkout payload echoing its own id is ACCEPTED (SDK request shape)",
          not protocol_rejected(status, body) and (echo or {}).get("id") == checkout_id,
          f"status {status}, body {str(body)[:200]}")
    status, body, _ = ucp_call(base_url, base_site, token, "update_checkout",
                               {"id": checkout_id,
                                "checkout": {"id": "ucp_chk_other",
                                             "line_items": [{"item": {"id": SECOND_SKU},
                                                             "quantity": 2}]}})
    check("checkout payload with a MISMATCHED id is rejected as a protocol error",
          protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")

    # 8. Spec fulfillment negotiation (ADR 0003) — the OOTB reference client's
    #    steps 4–6 verbatim: trigger methods → offered destinations → select
    #    destination → offered options → select option.
    status, body, neg = ucp_call(base_url, base_site, token, "update_checkout",
                                 {"id": checkout_id,
                                  "checkout": {"fulfillment": {"methods": [
                                      {"id": "method_1", "type": "shipping",
                                       "line_item_ids": ["li_0"]}]}}})
    check("negotiation trigger returns a parseable UCP payload", neg is not None,
          f"status {status}, body {str(body)[:300]}")
    dest_id = None
    if neg is not None:
        methods = (neg.get("fulfillment") or {}).get("methods") or []
        check("negotiation response echoes the method", len(methods) == 1
              and methods[0].get("id") == "method_1"
              and methods[0].get("type") == "shipping", f"got {methods!r}")
        destinations = (methods[0].get("destinations") if methods else None) or []
        check("negotiation offers saved destinations (address book / applied address)",
              len(destinations) >= 1 and all(d.get("id") for d in destinations),
              f"got {destinations!r}")
        check("destinations use the spec PostalAddress field names",
              all("street_address" in d or "postal_code" in d for d in destinations),
              f"got {destinations!r}")
        dest_id = destinations[0].get("id") if destinations else None
    if dest_id:
        status, body, sel = ucp_call(base_url, base_site, token, "update_checkout",
                                     {"id": checkout_id,
                                      "checkout": {"fulfillment": {"methods": [
                                          {"id": "method_1", "type": "shipping",
                                           "line_item_ids": ["li_0"],
                                           "selected_destination_id": dest_id}]}}})
        method = ((sel or {}).get("fulfillment") or {}).get("methods", [{}])[0]
        check("selected destination is echoed",
              method.get("selected_destination_id") is not None, f"got {method!r}")
        groups = method.get("groups") or []
        options = (groups[0].get("options") if groups else None) or []
        check("destination selection yields groups[].options[] (delivery modes)",
              len(options) >= 1 and all(o.get("id") and o.get("title") for o in options),
              f"got {groups!r}")
        check("every option carries a minor-unit totals breakdown",
              all(isinstance(t.get("amount"), int)
                  for o in options for t in o.get("totals") or []),
              f"got {options!r}")
        if options:
            option_id = options[0]["id"]
            status, body, chosen = ucp_call(base_url, base_site, token, "update_checkout",
                                            {"id": checkout_id,
                                             "checkout": {"fulfillment": {"methods": [
                                                 {"id": "method_1", "type": "shipping",
                                                  "line_item_ids": ["li_0"],
                                                  "selected_destination_id": dest_id,
                                                  "groups": [{"id": "group_1",
                                                              "line_item_ids": ["li_0"],
                                                              "selected_option_id": option_id}]}]}}})
            chosen_method = ((chosen or {}).get("fulfillment") or {}).get("methods", [{}])[0]
            chosen_groups = chosen_method.get("groups") or [{}]
            check("selected option is applied and echoed",
                  chosen_groups[0].get("selected_option_id") == option_id,
                  f"got {chosen_groups!r}")
            check("negotiated checkout reaches ready_for_complete",
                  (chosen or {}).get("status") == "ready_for_complete",
                  f"got {(chosen or {}).get('status')!r}")
    else:
        skip("fulfillment negotiation destination/option selection",
             "no destinations offered (empty address book?)")


def test_checkout_complete(base_url, base_site, token):
    """Phase 5: complete_checkout (mock payment + idempotency) and cancel_checkout."""
    log(f"\n{Colors.CYAN}── Checkout capability: complete/cancel ({TRANSPORT.upper()} binding) ──{Colors.RESET}")

    def totals_of(payload):
        return {t.get("type"): t.get("amount") for t in payload.get("totals") or []}

    def payment_checkout(handler_id=PAYMENT_HANDLER_ID):
        # Any credential token is accepted for the declared mock handler (R9).
        return {"payment": {"instruments": [
            {"handler_id": handler_id, "type": "card",
             "credential": {"token": "tok_ucp_e2e_demo"}}]}}

    def meta_with_key(key):
        return {"ucp-agent": HARNESS_AGENT_PROFILE, "idempotency-key": key}

    # Build a purchasable checkout: two mice (BOGO fires) + destination.
    _, body, payload = ucp_call(base_url, base_site, token, "create_checkout",
                                {"checkout": {"line_items": [{"item": {"id": SECOND_SKU},
                                                              "quantity": 2}],
                                              "buyer": {"first_name": "John", "last_name": "Doe",
                                                        "email": CUSTOMER_EMAIL}}})
    checkout_id = (payload or {}).get("id")
    check("complete section: purchase checkout created", bool(checkout_id),
          f"body {str(body)[:300]}")
    if not checkout_id:
        return None
    _, body, upd = ucp_call(base_url, base_site, token, "update_checkout",
                            {"id": checkout_id,
                             "checkout": {"fulfillment": {
                                 "destination": DESTINATION,
                                 "delivery_mode": DELIVERY_MODE_STANDARD}}})
    check("purchase checkout is ready_for_complete",
          (upd or {}).get("status") == "ready_for_complete",
          f"got {(upd or {}).get('status')!r}")
    if (upd or {}).get("status") != "ready_for_complete":
        return None

    # 1. Missing idempotency key (meta on MCP / Idempotency-Key header on
    #    REST) → client protocol bug → MCP isError / HTTP 400.
    status, body, _ = ucp_call(base_url, base_site, token, "complete_checkout",
                               {"id": checkout_id, "checkout": payment_checkout()})
    check("complete without idempotency-key is rejected as a protocol error",
          protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")

    # 2. Unknown handler_id → unrecoverable UCP message, checkout untouched.
    status, body, bad = ucp_call(base_url, base_site, token, "complete_checkout",
                                 {"id": checkout_id,
                                  "checkout": payment_checkout("acme_real_card")},
                                 meta=meta_with_key(str(uuid.uuid4())))
    check("unknown payment handler still returns 200", status == 200, f"got {status}")
    if bad is not None:
        assert_ucp_envelope(bad, "complete_checkout (unknown handler)", expected_status="error")
        msgs = bad.get("messages") or []
        check("unknown handler yields an unrecoverable message naming the declared handler",
              any(m.get("severity") == "unrecoverable" and PAYMENT_HANDLER_ID in (m.get("content") or "")
                  for m in msgs), f"got {msgs!r}")

    # 3. The real complete: mock Visa path → placeOrder → status completed + order.id.
    idempotency_key = str(uuid.uuid4())
    status, body, done = ucp_call(base_url, base_site, token, "complete_checkout",
                                  {"id": checkout_id, "checkout": payment_checkout()},
                                  meta=meta_with_key(idempotency_key))
    check("complete_checkout returns a parseable UCP payload", done is not None,
          f"status {status}, body {str(body)[:300]}")
    if done is None:
        return None
    check("complete_checkout is not rejected as a protocol error",
          not protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")
    assert_ucp_envelope(done, "complete_checkout")
    check("completed checkout echoes the id", done.get("id") == checkout_id,
          f"got {done.get('id')!r}")
    check("status is completed", done.get("status") == "completed",
          f"got {done.get('status')!r}")
    order_id = (done.get("order") or {}).get("id")
    check("completed checkout embeds order.id", bool(order_id), f"got {done.get('order')!r}")
    # OrderConfirmation requires permalink_url — the OOTB client reads it
    # right after a successful complete (ADR 0003).
    permalink = (done.get("order") or {}).get("permalink_url") or ""
    check("completed order block carries permalink_url",
          permalink.startswith("http") and (order_id or "") in permalink,
          f"got {done.get('order')!r}")
    totals = totals_of(done)
    check(f"completed BOGO discount of {-BOGO_DISCOUNT_MINOR} survives onto the order",
          totals.get("discount") == -BOGO_DISCOUNT_MINOR, f"got {totals!r}")
    check(f"completed fulfillment cost is {SHIPPING_STANDARD_MINOR}",
          totals.get("fulfillment") == SHIPPING_STANDARD_MINOR, f"got {totals!r}")
    expected_total = SECOND_SKU_PRICE_MINOR + SHIPPING_STANDARD_MINOR
    check(f"completed total is {expected_total} (discounted mice + standard shipping)",
          totals.get("total") == expected_total, f"got {totals!r}")
    ucp_schema_validate(done, "complete_checkout response")

    # 4. Idempotent replay: the SAME key returns the SAME order — never a second
    #    placeOrder (verified in the DB as exactly one order for this key).
    status, body, replay = ucp_call(base_url, base_site, token, "complete_checkout",
                                    {"id": checkout_id, "checkout": payment_checkout()},
                                    meta=meta_with_key(idempotency_key))
    check("idempotent replay returns a parseable UCP payload", replay is not None,
          f"status {status}, body {str(body)[:300]}")
    if replay is not None:
        check("replay status is completed", replay.get("status") == "completed",
              f"got {replay.get('status')!r}")
        check("replay returns the SAME order.id (no second order)",
              (replay.get("order") or {}).get("id") == order_id,
              f"first {order_id!r} vs replay {(replay.get('order') or {}).get('id')!r}")

    # 5. A DIFFERENT key on the completed checkout → unrecoverable, no new order.
    status, body, again = ucp_call(base_url, base_site, token, "complete_checkout",
                                   {"id": checkout_id, "checkout": payment_checkout()},
                                   meta=meta_with_key(str(uuid.uuid4())))
    if again is not None:
        assert_ucp_envelope(again, "complete_checkout (different key, already completed)",
                            expected_status="error")
        msgs = again.get("messages") or []
        check("different-key complete on a completed checkout is unrecoverable",
              any(m.get("severity") == "unrecoverable" for m in msgs), f"got {msgs!r}")

    # 6. get_checkout reflects the terminal completed state (cart was consumed).
    _, _, got = ucp_call(base_url, base_site, token, "get_checkout", {"id": checkout_id})
    check("get_checkout after completion shows status completed",
          (got or {}).get("status") == "completed", f"got {(got or {}).get('status')!r}")
    check("get_checkout after completion carries the order id",
          ((got or {}).get("order") or {}).get("id") == order_id,
          f"got {(got or {}).get('order')!r}")

    # 7. Terminal guard: a completed checkout can no longer be updated.
    _, body, blocked = ucp_call(base_url, base_site, token, "update_checkout",
                                {"id": checkout_id,
                                 "checkout": {"line_items": [{"item": {"id": SECOND_SKU},
                                                              "quantity": 1}]}})
    if blocked is not None:
        assert_ucp_envelope(blocked, "update_checkout (completed)", expected_status="error")
        check("update after completion is unrecoverable",
              any(m.get("severity") == "unrecoverable" for m in blocked.get("messages") or []),
              f"got {blocked.get('messages')!r}")

    # ── cancel_checkout: idempotent + terminal ──────────────────────────────
    _, body, payload = ucp_call(base_url, base_site, token, "create_checkout",
                                {"checkout": {"line_items": [{"item": {"id": SECOND_SKU},
                                                              "quantity": 1}]}})
    cancel_id = (payload or {}).get("id")
    check("cancel section: fresh checkout created", bool(cancel_id), f"body {str(body)[:300]}")
    if not cancel_id:
        return order_id

    # Missing idempotency key → protocol error (the binding requires it on cancel too).
    status, body, _ = ucp_call(base_url, base_site, token, "cancel_checkout",
                               {"id": cancel_id})
    check("cancel without idempotency-key is rejected as a protocol error",
          protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")

    cancel_key = str(uuid.uuid4())
    status, body, canceled = ucp_call(base_url, base_site, token, "cancel_checkout",
                                      {"id": cancel_id}, meta=meta_with_key(cancel_key))
    check("cancel_checkout returns a parseable UCP payload", canceled is not None,
          f"status {status}, body {str(body)[:300]}")
    if canceled is not None:
        assert_ucp_envelope(canceled, "cancel_checkout")
        check("canceled checkout has status canceled",
              canceled.get("status") == "canceled", f"got {canceled.get('status')!r}")
        ucp_schema_validate(canceled, "cancel_checkout response")

    # Replayed cancel is idempotent — same terminal state, no error.
    status, body, canceled2 = ucp_call(base_url, base_site, token, "cancel_checkout",
                                       {"id": cancel_id}, meta=meta_with_key(cancel_key))
    check("replayed cancel is not rejected as a protocol error",
          not protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")
    check("replayed cancel re-returns status canceled",
          (canceled2 or {}).get("status") == "canceled",
          f"got {(canceled2 or {}).get('status')!r}")

    # Canceled is terminal: neither update nor complete may run afterwards.
    _, _, after = ucp_call(base_url, base_site, token, "update_checkout",
                           {"id": cancel_id,
                            "checkout": {"line_items": [{"item": {"id": SECOND_SKU},
                                                         "quantity": 2}]}})
    check("update after cancel is an unrecoverable UCP error",
          (after or {}).get("ucp", {}).get("status") == "error"
          and any(m.get("severity") == "unrecoverable" for m in (after or {}).get("messages") or []),
          f"got {after!r}")
    status, body, comp = ucp_call(base_url, base_site, token, "complete_checkout",
                                  {"id": cancel_id, "checkout": payment_checkout()},
                                  meta=meta_with_key(str(uuid.uuid4())))
    check("complete after cancel is an unrecoverable UCP error",
          (comp or {}).get("ucp", {}).get("status") == "error"
          and any(m.get("severity") == "unrecoverable" for m in (comp or {}).get("messages") or []),
          f"got {comp!r}")

    log(f"  {Colors.DIM}placed order: {order_id} (idempotency key {idempotency_key}){Colors.RESET}")
    return order_id


def test_orders(base_url, base_site, token, order_id):
    """Phase 6: dev.ucp.shopping.order — get_order + list_orders (OrderFacade,
    scoped to the authenticated customer)."""
    log(f"\n{Colors.CYAN}── Order capability ({TRANSPORT.upper()} binding) ──{Colors.RESET}")

    # get_order for the purchase the complete section just placed:
    # 2 mice (BOGO fires) + standard shipping.
    if order_id:
        status, body, payload = ucp_call(base_url, base_site, token,
                                         "get_order", {"id": order_id})
        check("get_order returns a parseable UCP payload", payload is not None,
              f"status {status}, body {str(body)[:300]}")
        if payload is not None:
            assert_ucp_envelope(payload, "get_order")
            order = payload.get("order") or {}
            check("get_order echoes the order id", order.get("id") == order_id,
                  f"got {order.get('id')!r}")
            check("order has an ISO created_at timestamp",
                  isinstance(order.get("created_at"), str) and "T" in order.get("created_at", ""),
                  f"got {order.get('created_at')!r}")
            # Mock placeOrder leaves no hybris status → UCP wire default "created".
            check("just-placed order status is created",
                  order.get("status") == "created", f"got {order.get('status')!r}")
            check("order currency is USD", order.get("currency") == "USD",
                  f"got {order.get('currency')!r}")
            line_items = order.get("line_items") or []
            check("order line items carry the purchased mice",
                  len(line_items) == 1
                  and line_items[0].get("item", {}).get("id") == SECOND_SKU
                  and line_items[0].get("quantity") == 2,
                  f"got {line_items!r}")
            totals = {t.get("type"): t.get("amount") for t in order.get("totals") or []}
            check(f"order BOGO discount of {-BOGO_DISCOUNT_MINOR} survives onto the order detail",
                  totals.get("discount") == -BOGO_DISCOUNT_MINOR, f"got {totals!r}")
            expected_total = SECOND_SKU_PRICE_MINOR + SHIPPING_STANDARD_MINOR
            check(f"order total is {expected_total} (discounted mice + standard shipping)",
                  totals.get("total") == expected_total, f"got {totals!r}")
            check("order fulfillment echoes the delivery destination",
                  ((order.get("fulfillment") or {}).get("destination") or {}).get("city")
                  == DESTINATION["city"], f"got {order.get('fulfillment')!r}")
            ucp_schema_validate(payload, "get_order response")
    else:
        skip("get_order for the placed order", "complete section did not yield an order id")

    # Unknown order id → UCP business error payload, never a transport error.
    status, body, missing = ucp_call(base_url, base_site, token,
                                     "get_order", {"id": "NO_SUCH_ORDER"})
    check("get_order unknown id still returns 200", status == 200, f"got {status}")
    check("get_order unknown id is not rejected as a protocol error",
          not protocol_rejected(status, body), f"status {status}, body {str(body)[:200]}")
    if missing is not None:
        assert_ucp_envelope(missing, "get_order (unknown id)", expected_status="error")
        msgs = missing.get("messages") or []
        check("unknown order id yields unrecoverable not_found message",
              any(m.get("code") == "not_found" and m.get("severity") == "unrecoverable"
                  for m in msgs), f"got {msgs!r}")

    # list_orders — the full history: the just-placed order plus the durable
    # fixtures (Phase 5's UCP purchase 00005004 and the THINK-000x impex orders).
    status, body, payload = ucp_call(base_url, base_site, token,
                                     "list_orders", {"page_size": 50})
    check("list_orders returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is not None:
        assert_ucp_envelope(payload, "list_orders")
        orders = payload.get("orders") or []
        order_ids = [o.get("id") for o in orders]
        check("list_orders returns the customer's history", len(orders) >= 3,
              f"got {len(orders)} orders")
        if order_id:
            check("list_orders includes the just-placed order",
                  order_id in order_ids, f"got {order_ids!r}")
        check(f"list_orders includes the Phase 5 UCP purchase {UCP_FIXTURE_ORDER}",
              UCP_FIXTURE_ORDER in order_ids, f"got {order_ids!r}")
        check("list_orders includes the THINK-000x impex fixtures",
              {"THINK-0001", "THINK-0003"} <= set(order_ids), f"got {order_ids!r}")
        check("every history entry total is integer minor units",
              all(isinstance(t.get("amount"), int)
                  for o in orders for t in o.get("totals") or []),
              f"got {[o.get('totals') for o in orders]!r}")
        check("every history entry carries a status",
              all(o.get("status") for o in orders), f"got {orders!r}")
        check("list_orders includes pagination",
              isinstance(payload.get("pagination"), dict), f"got {payload.get('pagination')!r}")
        ucp_schema_validate(payload, "list_orders response")

    # Pagination is honored: page_size 1 → exactly one summary.
    status, body, page1 = ucp_call(base_url, base_site, token,
                                   "list_orders", {"page_size": 1})
    check("list_orders honors page_size",
          page1 is not None and len(page1.get("orders") or []) == 1,
          f"got {page1 and page1.get('orders')!r}")


def test_promotions_mcp(base_url, base_site, token):
    """Phase 6: com.thinkshop.promotions — rule/coupon metadata via coremcp's
    PromotionQueryService. MCP-only: the custom com.thinkshop.* capabilities
    have no REST routes (Phase 7 REST scope is catalog/checkout/order)."""
    log(f"\n{Colors.CYAN}── Promotions capability (com.thinkshop.promotions) ──{Colors.RESET}")

    if TRANSPORT == "rest":
        skip("promotions capability over REST",
             "com.thinkshop.promotions is MCP-only (no REST routes; see docs/adr/0002)")
        return

    status, body, payload = mcp_tool_call(base_url, base_site, token, "get_promotions", {})
    check("get_promotions returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is None:
        return
    assert_ucp_envelope(payload, "get_promotions")
    promos = payload.get("promotions") or []
    promo_codes = {p.get("code") for p in promos if isinstance(p, dict)}
    check("get_promotions returns promotion rules", len(promos) > 0,
          "no rules — was setup-promotions.sh run?")
    check(f"known rules {sorted(KNOWN_PROMO_RULES)} are listed",
          KNOWN_PROMO_RULES <= promo_codes, f"got {sorted(promo_codes)!r}")
    coupons = payload.get("coupons") or []
    coupon_ids = {c.get("couponId") for c in coupons if isinstance(c, dict)}
    check(f"known coupon {KNOWN_COUPON} is listed",
          KNOWN_COUPON in coupon_ids, f"got {sorted(coupon_ids)!r}")
    ucp_schema_validate(payload, "get_promotions response")

    # include_coupons=false omits the coupons block entirely.
    status, body, no_coupons = mcp_tool_call(base_url, base_site, token,
                                             "get_promotions", {"include_coupons": False})
    check("include_coupons=false omits the coupons block",
          no_coupons is not None and "coupons" not in no_coupons,
          f"got {no_coupons and sorted(no_coupons)!r}")


def test_knowledge_mcp(base_url, base_site, token):
    """Phase 6: com.thinkshop.knowledge — search/get over the Solr
    knowledgeIndex via coremcp's KnowledgeSearchService. MCP-only, like
    promotions (Phase 7 REST scope is catalog/checkout/order)."""
    log(f"\n{Colors.CYAN}── Knowledge capability (com.thinkshop.knowledge) ──{Colors.RESET}")

    if TRANSPORT == "rest":
        skip("knowledge capability over REST",
             "com.thinkshop.knowledge is MCP-only (no REST routes; see docs/adr/0002)")
        return

    # search_knowledge for the returns policy — a known KB fixture.
    status, body, payload = mcp_tool_call(base_url, base_site, token,
                                          "search_knowledge", {"query": "return policy"})
    check("search_knowledge returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is not None:
        assert_ucp_envelope(payload, "search_knowledge")
        results = payload.get("results") or []
        check("search_knowledge returns results", len(results) > 0,
              "no results — is the knowledgeIndex indexed?")
        check("count matches the results array", payload.get("count") == len(results),
              f"count {payload.get('count')!r} vs {len(results)} results")
        uids = [r.get("uid") for r in results]
        check(f"search_knowledge surfaces {KNOWN_KB_UID}", KNOWN_KB_UID in uids,
              f"got {uids!r}")

    # Category filter narrows to the requested category.
    status, body, filtered = mcp_tool_call(base_url, base_site, token, "search_knowledge",
                                           {"query": "policy", "category": "policy", "page_size": 10})
    if filtered is not None:
        results = filtered.get("results") or []
        check("category filter returns only policy entries",
              len(results) > 0 and all(r.get("category") == "policy" for r in results),
              f"got {[(r.get('uid'), r.get('category')) for r in results]!r}")

    # get_knowledge round-trip by uid.
    status, body, entry_payload = mcp_tool_call(base_url, base_site, token,
                                                "get_knowledge", {"uid": KNOWN_KB_UID})
    check("get_knowledge returns a parseable UCP payload", entry_payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if entry_payload is not None:
        assert_ucp_envelope(entry_payload, "get_knowledge")
        entry = entry_payload.get("entry") or {}
        check(f"get_knowledge returns the {KNOWN_KB_UID} entry",
              entry.get("uid") == KNOWN_KB_UID, f"got {entry.get('uid')!r}")
        check("entry carries title and body",
              bool(entry.get("title")) and bool(entry.get("body")), f"got {sorted(entry)!r}")

    # Unknown uid → UCP business error payload, never a transport error.
    status, body, missing = mcp_tool_call(base_url, base_site, token,
                                          "get_knowledge", {"uid": "no-such-entry"})
    check("get_knowledge unknown uid still returns 200", status == 200, f"got {status}")
    check("get_knowledge unknown uid is not an MCP isError result",
          body.get("result", {}).get("isError") is not True, f"got {body.get('result')!r}")
    if missing is not None:
        assert_ucp_envelope(missing, "get_knowledge (unknown uid)", expected_status="error")
        msgs = missing.get("messages") or []
        check("unknown uid yields unrecoverable not_found message",
              any(m.get("code") == "not_found" and m.get("severity") == "unrecoverable"
                  for m in msgs), f"got {msgs!r}")

    # Missing query is a client protocol bug → MCP isError.
    status, body, _ = mcp_tool_call(base_url, base_site, token, "search_knowledge", {})
    check("search_knowledge without a query is an isError tool result",
          body.get("result", {}).get("isError") is True, f"got {body.get('result')!r}")


# ── Main ────────────────────────────────────────────────────────────────────

def main():
    global verbose, TRANSPORT

    parser = argparse.ArgumentParser(description="E2E harness for the UCP surface")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL,
                        help=f"Server base URL (default: {DEFAULT_BASE_URL})")
    parser.add_argument("--base-site", default=DEFAULT_BASE_SITE,
                        help=f"OCC base site id (default: {DEFAULT_BASE_SITE})")
    parser.add_argument("--transport", choices=["mcp", "rest"], default="mcp",
                        help="UCP transport binding to drive (default: mcp)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Print response bodies")
    args = parser.parse_args()

    verbose = args.verbose
    TRANSPORT = args.transport
    base_url = args.base_url.rstrip("/")

    log(f"\n{'='*60}")
    log(f"UCP E2E Harness — {base_url} (transport: {args.transport})")
    log(f"{'='*60}")

    # Section 1: anonymous profile discovery (transport-independent).
    profile = test_profile(base_url, args.base_site)
    if profile is None:
        log(f"\n{Colors.RED}FATAL: could not fetch the UCP profile — is the server running?{Colors.RESET}")
        sys.exit(1)

    # Section 2: auth bootstrap (password grant) — required by every
    # capability section from here on.
    token = test_auth(base_url)

    # Section 3+: capability sections over the selected transport (the
    # assertions are transport-agnostic — the wire is selected in ucp_call).
    if token:
        test_catalog(base_url, args.base_site, token)
        test_checkout_create_get(base_url, args.base_site, token)
        test_checkout_update(base_url, args.base_site, token)
        order_id = test_checkout_complete(base_url, args.base_site, token)
        test_orders(base_url, args.base_site, token, order_id)
        test_promotions_mcp(base_url, args.base_site, token)
        test_knowledge_mcp(base_url, args.base_site, token)
    else:
        log(f"\n{Colors.RED}FATAL: no auth token — skipping capability sections{Colors.RESET}")

    # Summary
    log(f"\n{'='*60}")
    total = passed + failed
    summary = f"{Colors.GREEN}{passed} passed{Colors.RESET}"
    if failed:
        summary += f", {Colors.RED}{failed} failed{Colors.RESET}"
    if skipped:
        summary += f", {Colors.YELLOW}{skipped} skipped{Colors.RESET}"
    log(f"{summary} out of {total} checks")
    if failed:
        log("\nFailures:")
        for e in errors:
            log(f"  {Colors.RED}•{Colors.RESET} {e}")
    log(f"{'='*60}\n")

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
