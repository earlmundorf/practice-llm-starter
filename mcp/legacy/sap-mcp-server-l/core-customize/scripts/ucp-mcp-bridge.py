#!/usr/bin/env python3
"""
Auth-injecting MCP bridge — the dev stand-in for the agent gateway (R8),
for GENERIC MCP chat clients (e.g. a claude.ai custom connector).

WHY THIS EXISTS
  The UCP MCP binding requires two things a generic MCP chat client cannot
  provide:

  1. An OAuth2 bearer (password grant) on every call — claude.ai custom
     connectors only speak unauthenticated or OAuth-with-DCR, neither of
     which the platform auth server offers.
  2. meta["idempotency-key"] on complete/cancel_checkout, carried in the
     JSON-RPC request's params.meta — OUTSIDE params.arguments, which is
     the only part of the request an LLM controls. Without the key the
     server fails fast (no order is placed) — see docs/reference/tools.md.

  Both are agent-platform/gateway responsibilities in production. This
  bridge plays that role locally: it accepts unauthenticated MCP POSTs,
  injects the bearer, and injects a DETERMINISTIC idempotency key
  (sha256 of tool name + arguments) into params.meta when none is present,
  so a client retrying an identical call replays the stored completion
  instead of placing a duplicate order.

USAGE
  python3 scripts/ucp-mcp-bridge.py [--port 8183] [--upstream https://localhost:9002]
                                    [--site electronics]
  then expose it (e.g. `cloudflared tunnel --url http://localhost:8183`) and
  point the MCP client at  https://<public-host>/mcp

  Plain HTTP on the bridge side and demo credentials upstream — local
  demo use only; take the tunnel down when finished.
"""

import argparse
import hashlib
import http.server
import json
import os
import ssl
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request

_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE


class TokenSource:
    """Password-grant token cache (the smoke-test.sh credentials), thread-safe."""

    def __init__(self, upstream, client_id, client_secret, username, password):
        self.token_url = upstream + "/authorizationserver/oauth/token"
        self.form = urllib.parse.urlencode({
            "grant_type": "password",
            "client_id": client_id,
            "client_secret": client_secret,
            "username": username,
            "password": password,
        }).encode("utf-8")
        self._lock = threading.Lock()
        self._token = None

    def get(self, force_refresh=False):
        with self._lock:
            if self._token is None or force_refresh:
                req = urllib.request.Request(self.token_url, data=self.form)
                with urllib.request.urlopen(req, context=_SSL_CTX, timeout=30) as resp:
                    self._token = json.loads(resp.read())["access_token"]
            return self._token


# Only the tools whose binding REQUIRES the key get one injected — a blanket
# key on every call would make unrelated retries replay each other.
_IDEMPOTENT_TOOLS = {"complete_checkout", "cancel_checkout"}


def inject_idempotency_key(body):
    """Add a deterministic params.meta["idempotency-key"] to complete/cancel calls."""
    try:
        rpc = json.loads(body)
        if rpc.get("method") != "tools/call":
            return body
        params = rpc.setdefault("params", {})
        if params.get("name") not in _IDEMPOTENT_TOOLS:
            return body
        meta = params.get("meta") or params.get("_meta") or {}
        if not meta.get("idempotency-key"):
            seed = json.dumps([params.get("name"), params.get("arguments")], sort_keys=True)
            meta["idempotency-key"] = hashlib.sha256(seed.encode()).hexdigest()[:36]
            params["meta"] = meta
        return json.dumps(rpc).encode("utf-8")
    except (ValueError, AttributeError):
        return body  # not JSON / not a call — forward untouched


def main():
    parser = argparse.ArgumentParser(description="Auth-injecting MCP bridge for generic MCP chat clients")
    parser.add_argument("--port", type=int, default=int(os.getenv("UCP_BRIDGE_PORT", "8183")))
    parser.add_argument("--upstream", default=os.getenv("UCP_UPSTREAM", "https://localhost:9002"))
    parser.add_argument("--site", default=os.getenv("UCP_SITE", "electronics"))
    # Prefer a least-privilege OAuth client over trusted_client where one is
    # configured; the checked-in values are the LOCAL DEMO defaults (repo
    # rule: real credentials come from the environment).
    parser.add_argument("--client-id", default=os.getenv("UCP_CLIENT_ID", "mobile_android"))
    parser.add_argument("--client-secret", default=os.getenv("UCP_CLIENT_SECRET", "secret"))
    parser.add_argument("--username", default=os.getenv("UCP_USERNAME", "john.doe@thinkshop.com"))
    parser.add_argument("--password", default=os.getenv("UCP_PASSWORD", "1234"))
    parser.add_argument("--auth-token", default=os.getenv("UCP_BRIDGE_AUTH_TOKEN"),
                        help="Optional shared secret; when set, callers must send "
                             "Authorization: Bearer <token> to the bridge")
    args = parser.parse_args()

    if not args.auth_token:
        print("=" * 72, file=sys.stderr)
        print("WARNING: this bridge accepts UNAUTHENTICATED requests and injects a", file=sys.stderr)
        print("         merchant bearer for the demo customer. Anyone who can reach", file=sys.stderr)
        print("         it (e.g. through a tunnel) can browse AND PLACE ORDERS as", file=sys.stderr)
        print("         that customer. Local demo use only — pass --auth-token (or", file=sys.stderr)
        print("         UCP_BRIDGE_AUTH_TOKEN) to require a shared secret, and take", file=sys.stderr)
        print("         any tunnel down when finished.", file=sys.stderr)
        print("=" * 72, file=sys.stderr)

    mcp_url = f"{args.upstream}/occ/v2/{args.site}/ucp/mcp"
    tokens = TokenSource(args.upstream, args.client_id, args.client_secret,
                         args.username, args.password)

    class Handler(http.server.BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def _reply(self, status, body=b"", ctype="application/json"):
            self.send_response(status)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            # No OAuth discovery metadata -> the connector treats us as no-auth;
            # no SSE stream (the binding answers plain JSON, spec-allowed).
            self._reply(404 if self.path.startswith("/.well-known") else 405)

        def do_DELETE(self):
            self._reply(200)  # stateless binding: session termination is a no-op

        def do_POST(self):
            if args.auth_token:
                supplied = (self.headers.get("Authorization") or "").removeprefix("Bearer ").strip()
                if supplied != args.auth_token:
                    return self._reply(401, json.dumps({"error": "bridge auth token required"}).encode())
            body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            body = inject_idempotency_key(body)
            for attempt in (1, 2):
                req = urllib.request.Request(mcp_url, data=body, method="POST", headers={
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                    "Authorization": "Bearer " + tokens.get(force_refresh=attempt == 2)})
                try:
                    with urllib.request.urlopen(req, context=_SSL_CTX, timeout=60) as resp:
                        return self._reply(resp.status, resp.read(),
                                           resp.headers.get("Content-Type", "application/json"))
                except urllib.error.HTTPError as e:
                    if e.code == 401 and attempt == 1:
                        continue  # expired token -> refresh once and retry
                    return self._reply(e.code, e.read(),
                                       e.headers.get("Content-Type", "application/json"))
                except OSError as e:
                    return self._reply(502, json.dumps({"error": str(e)}).encode())

        def log_message(self, fmt, *args_):
            print(fmt % args_, flush=True)

    print(f"MCP bridge on :{args.port} -> {mcp_url}", flush=True)
    http.server.ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
