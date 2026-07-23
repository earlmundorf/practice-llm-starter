#!/usr/bin/env python3
"""
End-to-end harness for the UCP surface (ucpcommerce extension).

Drives the UCP flows against a running local server, transport-flagged from
day one (--transport mcp is the default; rest lands in Phase 7). Assertions
are written against UCP payload objects, not the wire, so they are reused
verbatim across transports.

Phase 1 scope: fetch the public profile anonymously, assert its JSON shape,
and (best-effort) schema-validate captured payloads with the `ucp-schema` CLI
when it is installed. Later phases append catalog / checkout / order /
promotions / knowledge sections.

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

    # Phase 1 serves a nearly-empty profile — the profile only advertises what
    # works. These assertions are updated as later phases add entries.
    check("capabilities is empty (Phase 1)", body.get("capabilities") == [],
          f"got {body.get('capabilities')!r}")
    check("services is empty (Phase 1)", body.get("services") == {},
          f"got {body.get('services')!r}")
    check("payment_handlers is empty (Phase 1)", body.get("payment_handlers") == [],
          f"got {body.get('payment_handlers')!r}")

    ucp_schema_validate(body, "profile")
    return body


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

    # Later phases: auth bootstrap (password grant) + catalog / checkout /
    # order / promotions / knowledge sections over the selected transport.
    if args.transport == "rest":
        log(f"\n{Colors.YELLOW}NOTE{Colors.RESET} --transport rest is not implemented until Phase 7")

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
