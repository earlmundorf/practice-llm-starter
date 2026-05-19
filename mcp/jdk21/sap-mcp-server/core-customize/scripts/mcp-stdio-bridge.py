#!/usr/bin/env python3
"""
Stdio-to-HTTP bridge for the coremcp MCP server.

Reads JSON-RPC messages from stdin, forwards them to the hybris MCP endpoint
over HTTPS (with self-signed cert support), and writes responses to stdout.

Configure in Claude Code:
    claude mcp add --transport stdio coremcp -- python3 /path/to/mcp-stdio-bridge.py

Or in ~/.claude.json:
    {
      "mcpServers": {
        "coremcp": {
          "type": "stdio",
          "command": "python3",
          "args": ["/path/to/mcp-stdio-bridge.py"]
        }
      }
    }
"""

import json
import os
import ssl
import sys
import urllib.request
import urllib.error
import urllib.parse

# ── Config ──────────────────────────────────────────────────────────────────

BASE_URL = os.environ.get("MCP_BASE_URL", "https://localhost:9002")
BASE_SITE = os.environ.get("MCP_BASE_SITE", "electronics")
MCP_PATH = f"/occ/v2/{BASE_SITE}/mcp"
OAUTH_PATH = "/authorizationserver/oauth/token"
CLIENT_ID = os.environ.get("MCP_CLIENT_ID", "trusted_client")
CLIENT_SECRET = os.environ.get("MCP_CLIENT_SECRET", "secret")

# For customer auth, set MCP_USERNAME and MCP_PASSWORD
CUSTOMER_USERNAME = os.environ.get("MCP_USERNAME", "")
CUSTOMER_PASSWORD = os.environ.get("MCP_PASSWORD", "")

# Skip certificate verification for self-signed dev cert
_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE

# ── State ───────────────────────────────────────────────────────────────────

access_token = None
session_id = None


# ── Helpers ─────────────────────────────────────────────────────────────────

def log(msg):
    """Log to stderr (stdout is reserved for MCP protocol)."""
    print(f"[mcp-bridge] {msg}", file=sys.stderr, flush=True)


def get_oauth_token():
    """Get an OAuth token, using customer grant if credentials are set."""
    global access_token
    url = BASE_URL + OAUTH_PATH

    if CUSTOMER_USERNAME and CUSTOMER_PASSWORD:
        params = {
            "grant_type": "password",
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
            "username": CUSTOMER_USERNAME,
            "password": CUSTOMER_PASSWORD,
        }
        log(f"Authenticating as customer: {CUSTOMER_USERNAME}")
    else:
        params = {
            "grant_type": "client_credentials",
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
        }
        log(f"Authenticating as trusted client: {CLIENT_ID}")

    data = urllib.parse.urlencode(params).encode("utf-8")
    req = urllib.request.Request(url, data=data)
    resp = urllib.request.urlopen(req, context=_SSL_CTX)
    result = json.loads(resp.read())
    access_token = result["access_token"]
    log(f"Got token: {access_token[:20]}...")


def reinitialize_session():
    """Send a fresh initialize request to get a new MCP session."""
    global session_id
    session_id = None
    log("Re-initializing MCP session...")
    init_msg = {
        "jsonrpc": "2.0",
        "id": "__reinit__",
        "method": "initialize",
        "params": {
            "protocolVersion": "2025-11-25",
            "clientInfo": {"name": "mcp-stdio-bridge", "version": "1.0.0"},
            "capabilities": {},
        },
    }
    result = forward_request(init_msg)
    if result and "result" in result:
        log(f"Re-initialized session: {session_id}")
        # Send notifications/initialized
        notify_msg = {
            "jsonrpc": "2.0",
            "method": "notifications/initialized",
        }
        forward_request(notify_msg)
        return True
    log("Re-initialization failed")
    return False


def forward_request(message, _retry=True):
    """Forward a JSON-RPC message to the hybris MCP endpoint."""
    global session_id

    if access_token is None:
        get_oauth_token()

    url = BASE_URL + MCP_PATH
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}",
    }
    if session_id:
        headers["MCP-Session-Id"] = session_id

    data = json.dumps(message).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers)

    try:
        resp = urllib.request.urlopen(req, context=_SSL_CTX)
        # Capture session ID from initialize response
        new_session = resp.headers.get("MCP-Session-Id")
        if new_session:
            session_id = new_session
            log(f"Session: {session_id}")

        body = resp.read().decode("utf-8")
        if body.strip():
            return json.loads(body)
        return None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        log(f"HTTP {e.code}: {body[:200]}")
        # If 401, try refreshing token and retry once
        if e.code == 401:
            log("Token expired, refreshing...")
            get_oauth_token()
            headers["Authorization"] = f"Bearer {access_token}"
            req2 = urllib.request.Request(url, data=data, headers=headers)
            try:
                resp2 = urllib.request.urlopen(req2, context=_SSL_CTX)
                new_session = resp2.headers.get("MCP-Session-Id")
                if new_session:
                    session_id = new_session
                body2 = resp2.read().decode("utf-8")
                if body2.strip():
                    return json.loads(body2)
                return None
            except Exception as e2:
                log(f"Retry failed: {e2}")

        # Check for invalid/expired session — re-initialize and retry
        if _retry:
            try:
                err = json.loads(body)
                err_msg = err.get("error", {}).get("message", "") if isinstance(err.get("error"), dict) else ""
            except Exception:
                err_msg = body
            if "session" in err_msg.lower() or "expired" in err_msg.lower():
                log("Session expired, re-initializing...")
                if reinitialize_session():
                    return forward_request(message, _retry=False)

        # Return JSON-RPC error
        return {
            "jsonrpc": "2.0",
            "id": message.get("id"),
            "error": {"code": -32603, "message": f"HTTP {e.code}: {body[:200]}"},
        }


# ── Main loop ───────────────────────────────────────────────────────────────

def main():
    log(f"Starting bridge → {BASE_URL}{MCP_PATH}")

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            message = json.loads(line)
        except json.JSONDecodeError as e:
            log(f"Bad JSON from stdin: {e}")
            continue

        log(f"→ {message.get('method', '?')} (id={message.get('id')})")

        response = forward_request(message)

        if response is not None:
            out = json.dumps(response)
            log(f"← {len(out)} bytes")
            print(out, flush=True)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log("Shutting down")
    except BrokenPipeError:
        pass
