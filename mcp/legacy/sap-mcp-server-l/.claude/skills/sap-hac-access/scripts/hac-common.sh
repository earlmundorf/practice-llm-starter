#!/bin/bash
# Shared helpers for HAC command-line tools.

HAC_DEFAULT_URL="https://localhost:9002"
HAC_DEFAULT_JAVA_VERSION="21"
HAC_DEFAULT_CONTEXT_PATH=""
HAC_DEFAULT_USER="admin"
HAC_DEFAULT_PASS="nimda"
HAC_CONNECT_TIMEOUT="${HAC_CONNECT_TIMEOUT:-10}"
HAC_MAX_TIME="${HAC_MAX_TIME:-120}"

hac_usage_env() {
  echo "Environment overrides, only when defaults do not match:" >&2
  echo "  HAC_URL  HAC_JAVA_VERSION  HAC_CONTEXT_PATH  HAC_USER  HAC_PASS" >&2
  echo "  Defaults assume local Java 21 HAC at https://localhost:9002 with admin/nimda." >&2
  echo "  Example override: HAC_CONTEXT_PATH=/hac when HAC is mounted under /hac." >&2
}

hac_require_commands() {
  local missing=""
  local command_name

  for command_name in curl python3; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
      missing="$missing $command_name"
    fi
  done

  if [ -n "$missing" ]; then
    echo "ERROR: Missing required command(s):$missing" >&2
    return 1
  fi
}

hac_normalize_url() {
  local url="${1:-$HAC_DEFAULT_URL}"
  local java_version="${2:-$HAC_DEFAULT_JAVA_VERSION}"
  local context_path="${3:-$HAC_DEFAULT_CONTEXT_PATH}"
  local base_url
  local input_had_hac="false"

  while [ "${url%/}" != "$url" ]; do
    url="${url%/}"
  done

  if [[ "$url" == */login ]]; then
    url="${url%/login}"
  fi

  if [[ "$url" == */console/* ]]; then
    url="${url%%/console/*}"
  fi

  while [ "${url%/}" != "$url" ]; do
    url="${url%/}"
  done

  base_url="$url"
  if [[ "$base_url" == */hac ]]; then
    input_had_hac="true"
    base_url="${base_url%/hac}"
  fi

  if ! [[ "$base_url" =~ ^[[:alpha:]][[:alnum:]+.-]*://[^/]+$ ]]; then
    echo "ERROR: HAC_URL must be a HAC root, /hac, /login, or /console/... URL; got: $url" >&2
    return 1
  fi

  case "$java_version" in
    17|21)
      ;;
    *)
      echo "ERROR: HAC_JAVA_VERSION must be 17 or 21; got: $java_version" >&2
      return 1
      ;;
  esac

  if [ -z "$context_path" ]; then
    if [ "$input_had_hac" = "true" ]; then
      context_path="/hac"
    elif [ "$java_version" = "17" ]; then
      context_path="/hac"
    fi
  fi

  if [ -n "$context_path" ]; then
    if [ "$context_path" != "/hac" ]; then
      echo "ERROR: HAC_CONTEXT_PATH must be empty or /hac; got: $context_path" >&2
      return 1
    fi
    url="$base_url$context_path"
  else
    url="$base_url"
  fi

  printf '%s\n' "$url"
}

hac_url() {
  local path="$1"

  if [[ "$path" != /* ]]; then
    echo "ERROR: HAC endpoint path must start with /; got: $path" >&2
    return 1
  fi

  printf '%s%s\n' "$HAC" "$path"
}

hac_require_option_value() {
  local option_name="$1"
  local option_value="${2:-}"

  if [ -z "$option_value" ]; then
    echo "ERROR: $option_name requires a value" >&2
    return 1
  fi
}

hac_set_input() {
  local label="$1"
  local mode="$2"
  local value="$3"

  if [ -n "$INPUT" ]; then
    echo "ERROR: Only one $label input may be provided" >&2
    return 1
  fi

  # shellcheck disable=SC2034 # Assigned for the calling script after sourcing this helper.
  INPUT_MODE="$mode"
  INPUT="$value"
}

hac_init() {
  hac_require_commands || return 1
  HAC_JAVA_VERSION="${HAC_JAVA_VERSION:-$HAC_DEFAULT_JAVA_VERSION}"
  HAC_CONTEXT_PATH="${HAC_CONTEXT_PATH:-$HAC_DEFAULT_CONTEXT_PATH}"
  HAC="$(hac_normalize_url "${HAC_URL:-$HAC_DEFAULT_URL}" "$HAC_JAVA_VERSION" "$HAC_CONTEXT_PATH")" || return 1
  AUTH_USER="${HAC_USER:-$HAC_DEFAULT_USER}"
  AUTH_PASS="${HAC_PASS:-$HAC_DEFAULT_PASS}"
}

hac_print_context() {
  echo "CONTEXT: HAC=$HAC HAC_JAVA_VERSION=$HAC_JAVA_VERSION HAC_CONTEXT_PATH=${HAC_CONTEXT_PATH:-}" >&2
}

hac_print_run_context() {
  local tool_name="$1"
  local detail="${2:-}"

  if [ "${HAC_SHOW_CONTEXT:-true}" = "true" ]; then
    if [ -n "$detail" ]; then
      echo "CONTEXT: tool=$tool_name HAC=$HAC HAC_JAVA_VERSION=$HAC_JAVA_VERSION HAC_CONTEXT_PATH=${HAC_CONTEXT_PATH:-} $detail" >&2
    else
      echo "CONTEXT: tool=$tool_name HAC=$HAC HAC_JAVA_VERSION=$HAC_JAVA_VERSION HAC_CONTEXT_PATH=${HAC_CONTEXT_PATH:-}" >&2
    fi
  fi
}

hac_preview_file() {
  local file_path="$1"
  local max_length="${2:-80}"

  python3 - "$file_path" "$max_length" <<'PY'
import os
import sys

path = sys.argv[1]
max_length = int(sys.argv[2])
name = os.path.basename(path)
if len(name) > max_length:
    name = name[: max_length - 3] + '...'
print(name)
PY
}

hac_hint_curl_failure() {
  local exit_code="$1"

  case "$exit_code" in
    6)
      echo "HINT: DNS could not resolve the HAC host. Check HAC_URL or the selected hac.env entry." >&2
      ;;
    7)
      echo "HINT: Could not connect to HAC. Confirm SAP Commerce is running and the HAC port is correct." >&2
      ;;
    28)
      echo "HINT: HAC request timed out. The server may still be starting, blocked, or running a long operation." >&2
      echo "HINT: Increase HAC_MAX_TIME only after confirming the request should take longer." >&2
      ;;
    35|60)
      echo "HINT: TLS handshake/certificate failed. Local HAC usually uses self-signed HTTPS; verify the URL scheme and port." >&2
      ;;
    *)
      echo "HINT: Curl failed before HAC returned a usable response. Check server availability and the normalized HAC target." >&2
      ;;
  esac
}

hac_hint_http_failure() {
  local label="$1"
  local http_status="$2"
  local body_file="$3"

  case "$http_status" in
    301|302|303|307|308)
      echo "HINT: HAC redirected during $label. This often means the session expired, login was not accepted, or the endpoint context is wrong." >&2
      ;;
    400)
      echo "HINT: HAC rejected the request payload. Check form fields, CSRF token, and the submitted script/query content." >&2
      ;;
    401|403)
      echo "HINT: HAC rejected authentication or CSRF. Confirm credentials and that the request loaded the matching HAC page first." >&2
      ;;
    404)
      echo "HINT: HAC endpoint not found. Check HAC_CONTEXT_PATH: use /hac when HAC is mounted under /hac, even on Java 21." >&2
      ;;
    500|502|503|504)
      echo "HINT: HAC/server error. Check SAP Commerce console logs before retrying the same request." >&2
      ;;
  esac

  if [ -s "$body_file" ] && grep -Eiq 'j_spring_security_check|name="j_username"|hac.*login|login' "$body_file"; then
    echo "HINT: Response looks like a login page. The HAC session may not be authenticated or the target URL may be wrong." >&2
  fi

  if [ -s "$body_file" ] && grep -Eiq 'backoffice|occ|commercewebservices|storefront' "$body_file"; then
    echo "HINT: Response looks like a non-HAC application. Check that HAC_URL points to HAC, not Backoffice, OCC, or the storefront." >&2
  fi
}

hac_hint_csrf_failure() {
  local phase="$1"
  local url="$2"
  local body_file="$3"

  hac_print_context
  echo "HINT: Could not find a HAC CSRF token while trying to $phase at $url." >&2
  echo "HINT: Check HAC context first: set HAC_CONTEXT_PATH=/hac when HAC is mounted under /hac." >&2

  if [ -s "$body_file" ] && grep -Eiq 'j_spring_security_check|name="j_username"|login' "$body_file"; then
    echo "HINT: Response is still a login page; credentials may have failed or the session was not established." >&2
  elif [ -s "$body_file" ] && grep -Eiq 'backoffice|occ|commercewebservices|storefront' "$body_file"; then
    echo "HINT: Response looks like a non-HAC page; correct HAC_URL or the selected hac.env entry." >&2
  else
    echo "HINT: If SAP Commerce just started, HAC may not be fully initialized yet. Check the server log before retrying." >&2
  fi
}

hac_curl() {
  local label="$1"
  local output_file="$2"
  shift 2

  local body_file="$output_file"
  local stderr_file
  local http_status
  local exit_code
  local stream_output="false"

  if [ "$output_file" = "/dev/stdout" ]; then
    body_file=$(mktemp)
    stream_output="true"
  fi

  stderr_file=$(mktemp)

  http_status=$(curl -sk --connect-timeout "$HAC_CONNECT_TIMEOUT" --max-time "$HAC_MAX_TIME" -w '%{http_code}' "$@" -o "$body_file" 2>"$stderr_file")
  exit_code=$?

  if [ "$exit_code" -ne 0 ]; then
    echo "ERROR: HAC request failed during $label (curl exit $exit_code)" >&2
    hac_print_context
    hac_hint_curl_failure "$exit_code"
    if [ -s "$stderr_file" ]; then
      sed 's/^/DETAIL: /' "$stderr_file" >&2
    fi
    rm -f "$stderr_file"
    if [ "$stream_output" = "true" ]; then
      rm -f "$body_file"
    fi
    return 1
  fi

  rm -f "$stderr_file"

  if ! [[ "$http_status" =~ ^[0-9][0-9][0-9]$ ]]; then
    echo "ERROR: HAC request failed during $label (missing HTTP status)" >&2
    hac_print_context
    if [ "$stream_output" = "true" ]; then
      rm -f "$body_file"
    fi
    return 1
  fi

  if [ "$http_status" -ge 300 ]; then
    echo "ERROR: HAC request failed during $label (HTTP $http_status)" >&2
    hac_print_context
    hac_hint_http_failure "$label" "$http_status" "$body_file"
    if [ -s "$body_file" ]; then
      sed -n '1,20p' "$body_file" | sed 's/^/DETAIL: /' >&2
    fi
    if [ "$stream_output" = "true" ]; then
      rm -f "$body_file"
    fi
    return 1
  fi

  if [ "$stream_output" = "true" ]; then
    cat "$body_file"
    rm -f "$body_file"
  fi
}

hac_read_content() {
  local mode="$1"
  local value="$2"
  local label="$3"

  case "$mode" in
    stdin)
      cat
      ;;
    file)
      if [ ! -f "$value" ]; then
        echo "ERROR: $label file not found: $value" >&2
        return 1
      fi
      cat "$value"
      ;;
    content)
      printf '%s\n' "$value"
      ;;
    auto)
      if [ "$value" = "-" ]; then
        cat
      elif [ -f "$value" ]; then
        cat "$value"
      else
        printf '%s\n' "$value"
      fi
      ;;
    *)
      echo "ERROR: Unsupported input mode: $mode" >&2
      return 1
      ;;
  esac
}

hac_write_content_file() {
  local mode="$1"
  local value="$2"
  local label="$3"
  local output_file="$4"

  case "$mode" in
    stdin)
      cat > "$output_file"
      ;;
    file)
      if [ ! -f "$value" ]; then
        echo "ERROR: $label file not found: $value" >&2
        return 1
      fi
      cp "$value" "$output_file"
      ;;
    content)
      printf '%s\n' "$value" > "$output_file"
      ;;
    auto)
      if [ "$value" = "-" ]; then
        cat > "$output_file"
      elif [ -f "$value" ]; then
        cp "$value" "$output_file"
      else
        printf '%s\n' "$value" > "$output_file"
      fi
      ;;
    *)
      echo "ERROR: Unsupported input mode: $mode" >&2
      return 1
      ;;
  esac
}

hac_extract_csrf() {
  python3 - "$1" <<'PY'
import sys
from html.parser import HTMLParser


class CsrfParser(HTMLParser):
  def __init__(self):
    super().__init__()
    self.token = ''

  def handle_starttag(self, tag, attrs):
    if self.token:
      return

    attr = dict(attrs)
    if attr.get('name') == '_csrf':
      self.token = attr.get('content') or attr.get('value') or ''

try:
  parser = CsrfParser()
  with open(sys.argv[1], encoding='utf-8', errors='replace') as handle:
    parser.feed(handle.read())
  print(parser.token)
except Exception:
    print('')
PY
}

hac_login() {
  local cookie_jar="$1"
  local tmp_file="$2"
  local login_url
  local security_check_url
  local csrf

  login_url=$(hac_url "/login") || return 1
  security_check_url=$(hac_url "/j_spring_security_check") || return 1

  hac_curl "load HAC login page" "$tmp_file" -L -c "$cookie_jar" "$login_url" || return 1
  csrf=$(hac_extract_csrf "$tmp_file")

  if [ -z "$csrf" ]; then
    echo "ERROR: Could not extract CSRF token from HAC login page at $login_url" >&2
    hac_hint_csrf_failure "load HAC login page" "$login_url" "$tmp_file"
    return 1
  fi

  hac_curl "submit HAC login" /dev/null -L -b "$cookie_jar" -c "$cookie_jar" "$security_check_url" \
    --data-urlencode "j_username=$AUTH_USER" \
    --data-urlencode "j_password=$AUTH_PASS" \
    --data-urlencode "_csrf=$csrf" || return 1
}

hac_fresh_csrf() {
  local label="$1"
  local cookie_jar="$2"
  local tmp_file="$3"
  local url="$4"
  local csrf

  hac_curl "$label" "$tmp_file" -b "$cookie_jar" -c "$cookie_jar" "$url" || return 1
  csrf=$(hac_extract_csrf "$tmp_file")

  if [ -z "$csrf" ]; then
    echo "ERROR: Login failed or CSRF token missing for $label" >&2
    hac_hint_csrf_failure "$label" "$url" "$tmp_file"
    return 1
  fi

  printf '%s\n' "$csrf"
}