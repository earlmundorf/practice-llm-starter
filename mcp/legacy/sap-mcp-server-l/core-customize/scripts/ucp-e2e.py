#!/usr/bin/env python3
"""
End-to-end harness for the UCP surface (ucpcommerce extension).

Drives the UCP flows against a running local server, transport-flagged from
day one (--transport mcp is the default; rest lands in Phase 7). Assertions
are written against UCP payload objects, not the wire, so they are reused
verbatim across transports.

Sections so far:
  1. Profile   — anonymous discovery document shape (Phase 1) + Phase 2 entries
  2. Auth      — password-grant bootstrap (design R8)
  3. Catalog   — tools/list, search_catalog / lookup_catalog / get_product with
                 integer minor-unit price assertions (Phase 2)
  4. Checkout  — create_checkout / get_checkout round-trip against the
                 persisted UcpCheckoutSessionEntry store (Phase 3)
Later phases append update/complete/cancel, order, promotions and knowledge
sections.
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
# catalog (Phase 2) + create/get checkout (Phase 3).
EXPECTED_CATALOG_TOOLS = {"search_catalog", "lookup_catalog", "get_product"}
EXPECTED_CHECKOUT_TOOLS = {"create_checkout", "get_checkout"}
EXPECTED_TOOLS = EXPECTED_CATALOG_TOOLS | EXPECTED_CHECKOUT_TOOLS

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

    ucp = body.get("ucp")
    check("profile has ucp block", isinstance(ucp, dict), f"got {ucp!r}")
    version = (ucp or {}).get("version", "")
    check("ucp.version is a dated calver string",
          bool(UCP_VERSION_RE.match(version)), f"got {version!r}")

    check("profile has capabilities array",
          isinstance(body.get("capabilities"), list), f"got {body.get('capabilities')!r}")
    check("profile has services object",
          isinstance(body.get("services"), dict), f"got {body.get('services')!r}")
    check("profile has payment_handlers array",
          isinstance(body.get("payment_handlers"), list), f"got {body.get('payment_handlers')!r}")

    # The profile only advertises what works. Phase 2: the catalog capability
    # + the mcp transport; checkout/payment_handlers stay absent until Phase 5.
    caps = body.get("capabilities") or []
    cap_names = [c.get("name") for c in caps if isinstance(c, dict)]
    check("profile advertises dev.ucp.shopping.catalog",
          "dev.ucp.shopping.catalog" in cap_names, f"got {cap_names!r}")
    catalog_cap = next((c for c in caps if isinstance(c, dict)
                        and c.get("name") == "dev.ucp.shopping.catalog"), {})
    check("catalog capability version is a dated calver string",
          bool(UCP_VERSION_RE.match(catalog_cap.get("version", ""))),
          f"got {catalog_cap.get('version')!r}")

    services = body.get("services") or {}
    mcp_endpoint = (services.get("dev.ucp.shopping") or {}).get("mcp", {}).get("endpoint", "")
    check("profile advertises the mcp transport endpoint",
          mcp_endpoint.endswith(f"/occ/v2/{base_site}/ucp/mcp"), f"got {mcp_endpoint!r}")
    check("rest transport not advertised yet (pre-Phase 7)",
          "rest" not in (services.get("dev.ucp.shopping") or {}),
          f"got {services.get('dev.ucp.shopping')!r}")
    check("payment_handlers is empty (pre-Phase 5)", body.get("payment_handlers") == [],
          f"got {body.get('payment_handlers')!r}")

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


def test_catalog_mcp(base_url, base_site, token):
    """Phase 2: dev.ucp.shopping.catalog over the MCP binding."""
    log(f"\n{Colors.CYAN}── Catalog capability (MCP binding) ──{Colors.RESET}")

    # The endpoint is @Secured — no token must mean 401, not data.
    status, _, _ = mcp_rpc(base_url, base_site, None, "tools/list")
    check("unauthenticated call is rejected (401)", status == 401, f"got {status}")

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

    # tools/list — exactly the tools shipped so far (catalog + checkout create/get).
    status, _, body = mcp_rpc(base_url, base_site, token, "tools/list")
    tools = body.get("result", {}).get("tools", [])
    tool_names = {t.get("name") for t in tools if isinstance(t, dict)}
    check("tools/list returns exactly the catalog + checkout tools",
          tool_names == EXPECTED_TOOLS, f"got {sorted(tool_names)!r}")

    # Unknown tool → JSON-RPC invalid-params error.
    status, body, _ = mcp_tool_call(base_url, base_site, token, "definitely_not_a_tool", {})
    check("unknown tool returns -32602",
          body.get("error", {}).get("code") == -32602, f"got {body.get('error')!r}")

    # search_catalog for a known SKU keyword.
    status, body, payload = mcp_tool_call(base_url, base_site, token,
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
    status, body, payload = mcp_tool_call(base_url, base_site, token,
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
    status, body, payload = mcp_tool_call(base_url, base_site, token,
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
    status, body, payload = mcp_tool_call(base_url, base_site, token,
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
    status, body, payload = mcp_tool_call(base_url, base_site, token,
                                          "get_product", {"id": "NO_SUCH_SKU"})
    check("get_product unknown id still returns 200", status == 200, f"got {status}")
    if payload is not None:
        assert_ucp_envelope(payload, "get_product (unknown id)", expected_status="error")
        msgs = payload.get("messages") or []
        check("unknown id yields unrecoverable not_found message",
              any(m.get("code") == "not_found" and m.get("severity") == "unrecoverable"
                  for m in msgs), f"got {msgs!r}")


def test_checkout_create_get_mcp(base_url, base_site, token):
    """Phase 3: create_checkout / get_checkout over the MCP binding (R5 store)."""
    log(f"\n{Colors.CYAN}── Checkout capability: create/get (MCP binding) ──{Colors.RESET}")

    buyer = {"first_name": "John", "last_name": "Doe", "email": CUSTOMER_EMAIL}
    checkout_req = {
        "line_items": [{"item": {"id": SECOND_SKU}, "quantity": 1}],
        "buyer": buyer,
    }

    # create_checkout — the payload must NOT contain an id; the response mints one.
    status, body, payload = mcp_tool_call(base_url, base_site, token,
                                          "create_checkout", {"checkout": checkout_req})
    check("create_checkout returns a parseable UCP payload", payload is not None,
          f"status {status}, body {str(body)[:300]}")
    if payload is None:
        return
    check("create_checkout is not an MCP isError result",
          body.get("result", {}).get("isError") is not True, f"got {body.get('result')!r}")
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
    status, body, got = mcp_tool_call(base_url, base_site, token,
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
    status, body, missing = mcp_tool_call(base_url, base_site, token,
                                          "get_checkout", {"id": "ucp_chk_doesnotexist"})
    check("get_checkout unknown id still returns 200", status == 200, f"got {status}")
    check("get_checkout unknown id is not an MCP isError result",
          body.get("result", {}).get("isError") is not True, f"got {body.get('result')!r}")
    if missing is not None:
        assert_ucp_envelope(missing, "get_checkout (unknown id)", expected_status="error")
        msgs = missing.get("messages") or []
        check("unknown checkout id yields unrecoverable not_found message",
              any(m.get("code") == "not_found" and m.get("severity") == "unrecoverable"
                  for m in msgs), f"got {msgs!r}")

    # create_checkout with only an unknown SKU → error payload, no id minted.
    status, body, bad = mcp_tool_call(base_url, base_site, token, "create_checkout",
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


# ── Main ────────────────────────────────────────────────────────────────────

def main():
    global verbose

    parser = argparse.ArgumentParser(description="E2E harness for the UCP surface")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL,
                        help=f"Server base URL (default: {DEFAULT_BASE_URL})")
    parser.add_argument("--base-site", default=DEFAULT_BASE_SITE,
                        help=f"OCC base site id (default: {DEFAULT_BASE_SITE})")
    parser.add_argument("--transport", choices=["mcp", "rest"], default="mcp",
                        help="UCP transport binding to drive (default: mcp; rest lands in Phase 7)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Print response bodies")
    args = parser.parse_args()

    verbose = args.verbose
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

    # Section 3+: capability sections over the selected transport.
    if args.transport == "rest":
        log(f"\n{Colors.YELLOW}NOTE{Colors.RESET} --transport rest is not implemented until Phase 7")
    elif token:
        test_catalog_mcp(base_url, args.base_site, token)
        test_checkout_create_get_mcp(base_url, args.base_site, token)
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
