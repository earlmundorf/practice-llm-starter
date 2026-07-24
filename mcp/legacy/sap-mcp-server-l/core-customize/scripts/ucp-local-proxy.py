#!/usr/bin/env python3
"""
Local UCP reverse proxy — the dev stand-in for the production edge tier.

WHY THIS EXISTS
  UCP requires the discovery profile at the HOST ROOT (/.well-known/ucp) and
  the REST binding at the base the profile advertises, while SAP Commerce
  serves both under the OCC context path. In production that gap is closed by
  an edge rewrite (design R6 / runbook §3.1: ProxyPass at the web tier) and
  merchant auth is handled by an agent gateway in front of the store (R8).
  Locally, Google's out-of-the-box reference client
  (samples/rest/python/client/flower_shop/simple_happy_path_client.py) takes
  ONE --server_url and calls fixed paths on it:

      GET  {base}/.well-known/ucp
      POST {base}/checkout-sessions
      PUT  {base}/checkout-sessions/{id}
      POST {base}/checkout-sessions/{id}/complete

  No single hybris base satisfies both, and the client sends no OAuth token.
  This proxy is the documented local stand-in for BOTH production concerns:

      /.well-known/ucp  →  https://localhost:9002/occ/v2/{site}/.well-known/ucp   (anonymous)
      /{anything-else}  →  https://localhost:9002/occ/v2/{site}/ucp/{anything-else}
                           + Authorization: Bearer <password-grant token>         (R8)

  Upstream TLS verification is disabled (the local server uses a self-signed
  cert). The token is fetched with the demo customer's password grant and
  refreshed once on a 401. Plain HTTP on the proxy side — local use only.

USAGE
  python3 scripts/ucp-local-proxy.py [--port 8182] [--upstream https://localhost:9002]
                                     [--site electronics]
  then: uv run simple_happy_path_client.py --server_url=http://localhost:8182
"""

import argparse
import http.server
import json
import ssl
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request

_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE

# Hop-by-hop / transport headers we never forward in either direction.
_SKIP_REQUEST_HEADERS = {"host", "connection", "content-length", "accept-encoding",
                         "authorization", "transfer-encoding"}
_SKIP_RESPONSE_HEADERS = {"connection", "transfer-encoding", "content-length", "keep-alive"}


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


def make_handler(upstream, site, tokens):
    profile_path = "/.well-known/ucp"
    profile_target = f"{upstream}/occ/v2/{site}{profile_path}"
    rest_base = f"{upstream}/occ/v2/{site}/ucp"

    class UcpProxyHandler(http.server.BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):  # noqa: N802
            sys.stderr.write("[ucp-proxy] %s\n" % (fmt % args))

        def _forward(self):
            path = self.path
            if path.split("?")[0] == profile_path:
                target = profile_target
                inject_auth = False   # the profile is public by spec (R6)
            else:
                target = rest_base + path
                inject_auth = True    # agent-gateway stand-in (R8)

            body = None
            length = int(self.headers.get("Content-Length") or 0)
            if length:
                body = self.rfile.read(length)

            headers = {k: v for k, v in self.headers.items()
                       if k.lower() not in _SKIP_REQUEST_HEADERS}

            def call(token):
                if inject_auth and token:
                    headers["Authorization"] = "Bearer " + token
                req = urllib.request.Request(target, data=body, headers=headers,
                                             method=self.command)
                return urllib.request.urlopen(req, context=_SSL_CTX, timeout=120)

            try:
                token = tokens.get() if inject_auth else None
                try:
                    resp = call(token)
                except urllib.error.HTTPError as e:
                    if e.code == 401 and inject_auth:
                        # Token expired underneath us — refresh once and retry.
                        resp = call(tokens.get(force_refresh=True))
                    else:
                        resp = e  # HTTPError is a valid response object
                payload = resp.read()
                self.send_response(resp.getcode())
                for k, v in resp.headers.items():
                    if k.lower() not in _SKIP_RESPONSE_HEADERS:
                        self.send_header(k, v)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except Exception as e:  # noqa: BLE001 — surface as a 502, never crash the proxy
                msg = json.dumps({"error": "upstream request failed", "detail": str(e)}).encode()
                self.send_response(502)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(msg)))
                self.end_headers()
                self.wfile.write(msg)

        do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = _forward

    return UcpProxyHandler


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=8182)
    parser.add_argument("--upstream", default="https://localhost:9002")
    parser.add_argument("--site", default="electronics")
    parser.add_argument("--client-id", default="mobile_android")
    parser.add_argument("--client-secret", default="secret")
    parser.add_argument("--username", default="john.doe@thinkshop.com")
    parser.add_argument("--password", default="1234")
    args = parser.parse_args()

    upstream = args.upstream.rstrip("/")
    tokens = TokenSource(upstream, args.client_id, args.client_secret,
                         args.username, args.password)
    handler = make_handler(upstream, args.site, tokens)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", args.port), handler)
    print(f"[ucp-proxy] listening on http://127.0.0.1:{args.port}")
    print(f"[ucp-proxy]   /.well-known/ucp → {upstream}/occ/v2/{args.site}/.well-known/ucp (anonymous)")
    print(f"[ucp-proxy]   /*               → {upstream}/occ/v2/{args.site}/ucp/* (+ bearer token)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
