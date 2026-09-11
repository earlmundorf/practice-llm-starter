#!/bin/bash
# =============================================================================
# hac-impex.sh — Run ImpEx imports against HAC from the command line
#
# Usage:
#   ./hac-impex.sh --file /path/to/import.impex
#   ./hac-impex.sh --content "INSERT_UPDATE Product; code[unique=true]; name[lang=en]"
#   cat data.impex | ./hac-impex.sh --stdin
#
# Arguments:
#   ImpEx content string, file path, or "-" for stdin (required)
#
# Environment overrides, only when defaults do not match:
#   HAC_URL, HAC_JAVA_VERSION, HAC_CONTEXT_PATH, HAC_USER, HAC_PASS
#   Defaults assume local Java 21 HAC at https://localhost:9002 with admin/nimda.
#
# Requires: curl, python3
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/hac-common.sh
. "$SCRIPT_DIR/hac-common.sh"

usage() {
  echo "Usage: $0 [--file <path> | --stdin | --content <impex> | '<impex>' | <file> | -]" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 --file import.impex" >&2
  echo "  $0 --file /tmp/my-data.impex" >&2
  echo "  $0 \"INSERT_UPDATE Title; code[unique=true]; name[lang=en]" >&2
  echo "  ; mr ; Mr\"" >&2
  echo "  cat data.impex | $0 --stdin" >&2
  echo "" >&2
  echo "Options:" >&2
  echo "  --file <path>       Read ImpEx from a file" >&2
  echo "  --stdin             Read ImpEx from standard input" >&2
  echo "  --content <impex>   Use ImpEx text directly" >&2
  echo "" >&2
  hac_usage_env
}

if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
  usage
  exit 0
fi

INPUT=""
INPUT_MODE="auto"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --file)
      hac_require_option_value "--file" "${2:-}" || exit 1
      hac_set_input "ImpEx" "file" "$2" || exit 1
      shift 2
      ;;
    --stdin)
      hac_set_input "ImpEx" "stdin" "-" || exit 1
      shift
      ;;
    --content)
      hac_require_option_value "--content" "${2:-}" || exit 1
      hac_set_input "ImpEx" "content" "$2" || exit 1
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --*)
      echo "ERROR: Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [ -n "$INPUT" ]; then echo "ERROR: Unexpected argument: $1" >&2; exit 1; fi
      hac_set_input "ImpEx" "auto" "$1" || exit 1
      shift
      ;;
  esac
done

if [ -z "$INPUT" ]; then
  usage
  exit 1
fi

hac_init || exit 1

IMPEX_FILE=$(mktemp)
CJ=$(mktemp)
TMP=$(mktemp)
cleanup() { rm -f "$IMPEX_FILE" "$CJ" "$TMP"; }
trap cleanup EXIT

hac_write_content_file "$INPUT_MODE" "$INPUT" "ImpEx" "$IMPEX_FILE" || exit 1

if [ ! -s "$IMPEX_FILE" ]; then
  echo "ERROR: Empty ImpEx content" >&2
  exit 1
fi

IMPEX_IMPORT_URL=$(hac_url "/console/impex/import") || exit 1

hac_print_run_context "impex" "inputMode=$INPUT_MODE input=$(hac_preview_file "$IMPEX_FILE")"

hac_login "$CJ" "$TMP" || exit 1
CSRF=$(hac_fresh_csrf "load ImpEx import page" "$CJ" "$TMP" "$IMPEX_IMPORT_URL") || exit 1

# 4. Execute ImpEx import
hac_curl "execute ImpEx import" "$TMP" -b "$CJ" \
  -X POST "$IMPEX_IMPORT_URL" \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode "scriptContent@$IMPEX_FILE" \
  --data-urlencode "encoding=UTF-8" \
  --data-urlencode "maxThreads=1" \
  -d "validationEnum=IMPORT_STRICT" \
  -d "enableCodeExecution=false" \
  -d "distributedMode=false" \
  -d "legacyMode=false" \
  -d "sldEnabled=false" || exit 1

# 5. Parse HTML response for result
python3 -c "
import re, sys, html as htmlmod


def clean_html(raw):
  text = re.sub(r'<script.*?</script>', ' ', raw, flags=re.DOTALL | re.IGNORECASE)
  text = re.sub(r'<style.*?</style>', ' ', text, flags=re.DOTALL | re.IGNORECASE)
  text = re.sub(r'<[^>]+>', ' ', text)
  text = htmlmod.unescape(text)
  lines = [re.sub(r'\s+', ' ', line).strip() for line in text.splitlines()]
  return [line for line in lines if line]


def extract_result(raw):
  match = re.search(r'<div class=\"box impexResult quiet\">\s*<pre>\s*(.*?)\s*</pre>', raw, re.DOTALL)
  if match:
    return htmlmod.unescape(match.group(1)).strip()
  return ''


def impex_hints(text):
  lower = text.lower()
  hints = []
  if any(token in lower for token in ['cannot resolve value', 'unresolved', 'could not resolve item']):
    hints.append('Unresolved reference: validate the referenced item exists and the unique key/catalogVersion matches.')
  if any(token in lower for token in ['unknown type', 'no composed type', 'cannot find type', 'invalid type']):
    hints.append('Unknown item type: check extension loading and whether yupdate/type system changes are applied.')
  if any(token in lower for token in ['unknown attribute', 'cannot find attribute', 'no attribute descriptor']):
    hints.append('Unknown attribute: check qualifier spelling, extension dependency, and type system update state.')
  if any(token in lower for token in ['more than one item', 'ambiguous unique keys', 'multiple items']):
    hints.append('Ambiguous unique key: add enough unique columns, often catalogVersion or parent context.')
  if any(token in lower for token in ['catalogversion', 'catalog version']):
    hints.append('CatalogVersion issue: verify catalog id and Staged/Online version before re-importing.')
  if any(token in lower for token in ['macro', 'missing key', 'unresolved mandatory']):
    hints.append('Macro/mandatory value issue: check macro definitions and required columns before retrying.')
  if any(token in lower for token in ['code execution', 'scripting is disabled', 'beanshell']):
    hints.append('Code execution is disabled by this tool; remove code execution or request an explicitly approved path.')
  if any(token in lower for token in ['invalid line', 'unexpected', 'syntax', 'translator']):
    hints.append('ImpEx syntax/translator issue: check header mode, separators, translators, and escaped semicolons.')
  return hints[:3]

try:
    content = open(sys.argv[1]).read()
except Exception:
    print('ERROR: Could not read HAC response', file=sys.stderr)
    sys.exit(1)

lower_content = content.lower()

# Check for success - HAC uses data-level='notice' for success
if re.search(r'data-level=[\"\\\']notice[\"\\\']', content, re.I) and 'successfull' in lower_content:
    print('OK: ImpEx import successful')
  log = extract_result(content)
  if log:
    print(log)
    sys.exit(0)

# Check for error - HAC uses data-level='error' for failure
if re.search(r'data-level=[\"\\\']error[\"\\\']', content, re.I) or 'unsuccessfull' in lower_content:
    print('ERROR: ImpEx import failed', file=sys.stderr)
  detail = extract_result(content)
  if detail:
    print(detail, file=sys.stderr)
  for hint in impex_hints(detail or content):
    print('HINT:', hint, file=sys.stderr)
    sys.exit(1)

# Check for Spring form validation errors
errors = re.findall(r'class=\"error\"[^>]*>(.*?)<', content)
if errors:
    print('ERROR: ' + '; '.join(e.strip() for e in errors if e.strip()), file=sys.stderr)
    sys.exit(1)

# Fallback — could not determine result
print('WARNING: Could not determine import result - check HAC manually', file=sys.stderr)
title = re.search(r'<title[^>]*>(.*?)</title>', content, re.DOTALL | re.IGNORECASE)
if title:
    print('DETAIL: page title: ' + htmlmod.unescape(re.sub(r'\s+', ' ', title.group(1))).strip(), file=sys.stderr)
lines = clean_html(content)
for line in lines[:20]:
  print(line, file=sys.stderr)
for hint in impex_hints('\n'.join(lines[:50])):
  print('HINT:', hint, file=sys.stderr)
sys.exit(1)
" "$TMP"
