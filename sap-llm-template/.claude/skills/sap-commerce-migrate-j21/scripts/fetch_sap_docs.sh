#!/usr/bin/env bash
# Mirror SAP Commerce Cloud help-portal pages as Markdown.
#
# SAP Help Portal is a Vue SPA — WebFetch/curl of the visible URL returns
# an empty 934-byte shell. The real content lives behind two JSON endpoints
# reverse-engineered from the SPA bundle's ContentService:
#
#   1. GET /http.svc/deliverableMetadata
#        params: product_url, deliverable_url, topic_url, version=LATEST,
#                language=en-US, state=PRODUCTION, deliverableInfo=1, toc=0
#        returns: data.deliverable.id (numeric), data.deliverable.buildNo
#
#   2. GET /http.svc/pagecontent
#        params: deliverable_id (numeric), file_path={LOIO}.html,
#                buildNo, loadlandingpageontopicnotfound=true
#        returns: data.body (HTML), data.currentPage.t (title)
#
# Both respond with gzip (curl --compressed) and JSON.
#
# This script idempotently mirrors each unique topic LOIO in the resource
# index into SKILL_DIR/references/sap-docs/{NN-slug}.md with frontmatter.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$SKILL_DIR/references/sap-docs"
SOURCE_URLS="${1:-$SCRIPT_DIR/upgrade_resources.md}"

mkdir -p "$OUT_DIR"

if ! command -v pandoc >/dev/null 2>&1; then
  echo "ERROR: pandoc is required (brew install pandoc)" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required (brew install jq)" >&2
  exit 1
fi

API="https://help.sap.com/http.svc"
UA="Mozilla/5.0"

# topic LOIO → output filename slug + display title
# Single source of truth; extend here when new pages are added to upgrade_resources.md.
declare -A LOIO_FILE=(
  [9efd1f6212134dec8236a146cac4c98a]="01-general-update-guide"
  [4f86494bd4574ae4b5d29f7dc9b8e5b6]="03-spring-6"
  [202acfa790dc41149af8d5a33374a795]="04-spring-model-changes"
  [b8a9332e0f6d455dab5f22d6e027497d]="05-tomcat"
  [53d796af83224e98bbd6d5dbffa1de89]="06-build-ant-gradle"
  [f21c6eb4c4014edeb113e22095b75ef6]="07-testing"
  [d079f886cab647e5a0555e2cae8e4416]="08-oauth-authorization-server"
  [d92f552a2fbc47c59950e3f134fb67c2]="09-resource-server"
  [9f8c9d455de44fd0a204fce82247b206]="10-resttemplate-removal"
  [a32fa4b86acf4c899ce3149f4679ac95]="11-smartedit"
  [52632c5ae11f430baf05fff9e0d483fe]="12-web-service-exception-handling"
  [236dcbe0ff5d4bd0bdf177b7f151cc66]="13-release-notes-2211-jdk21"
  [712a8128d12d49cb8752765c38e50d41]="14-update-release-2211.46"
)

# Cache metadata per deliverable_hash so we only hit it once.
declare -A DELIV_ID_CACHE=()
declare -A DELIV_BUILD_CACHE=()

get_metadata() {
  local product="$1" deliv_hash="$2" topic_loio="$3"
  local cache_key="${deliv_hash}"
  if [[ -n "${DELIV_ID_CACHE[$cache_key]:-}" ]]; then
    echo "${DELIV_ID_CACHE[$cache_key]} ${DELIV_BUILD_CACHE[$cache_key]}"
    return
  fi
  local url="$API/deliverableMetadata?product_url=${product}&deliverable_url=${deliv_hash}&topic_url=${topic_loio}&version=LATEST&language=en-US&state=PRODUCTION&deliverableInfo=1&toc=0"
  local resp
  resp=$(curl -s --compressed -A "$UA" -H "Accept: application/json" -H "Referer: https://help.sap.com/" "$url")
  local status
  status=$(echo "$resp" | jq -r '.status // "ERR"')
  if [[ "$status" != "OK" ]]; then
    echo "metadata failed for $deliv_hash / $topic_loio: $resp" >&2
    return 1
  fi
  local id build
  id=$(echo "$resp" | jq -r '.data.deliverable.id')
  build=$(echo "$resp" | jq -r '.data.deliverable.buildNo')
  DELIV_ID_CACHE[$cache_key]="$id"
  DELIV_BUILD_CACHE[$cache_key]="$build"
  echo "$id $build"
}

fetch_page() {
  local deliv_id="$1" build="$2" loio="$3"
  local url="$API/pagecontent?deliverable_id=${deliv_id}&file_path=${loio}.html&buildNo=${build}&loadlandingpageontopicnotfound=true"
  curl -s --compressed -A "$UA" -H "Accept: application/json" -H "Referer: https://help.sap.com/" "$url"
}

# Parse TOPIC: URL pairs; dedupe by topic LOIO.
declare -A SEEN_LOIO=()
declare -a QUEUE=()

while IFS= read -r line; do
  [[ -z "$line" || "$line" =~ ^# ]] && continue
  [[ ! "$line" =~ :.*https:// ]] && continue

  topic="${line%%:*}"
  url="${line#*: }"
  url="${url%%#*}"   # strip anchor — body contains all sections

  # Expected: https://help.sap.com/docs/{PRODUCT}/{DELIV_HASH}/{LOIO}.html
  if [[ ! "$url" =~ help\.sap\.com/docs/([^/]+)/([0-9a-f]{32})/([0-9a-f]{32})\.html ]]; then
    echo "skip (no match): $line" >&2
    continue
  fi
  product="${BASH_REMATCH[1]}"
  deliv_hash="${BASH_REMATCH[2]}"
  loio="${BASH_REMATCH[3]}"

  if [[ -n "${SEEN_LOIO[$loio]:-}" ]]; then
    continue
  fi
  SEEN_LOIO[$loio]=1
  QUEUE+=("$topic|$product|$deliv_hash|$loio|$url")
done < "$SOURCE_URLS"

echo "Queue: ${#QUEUE[@]} unique topics"

today=$(date +%Y-%m-%d)
success=0
failure=0

for row in "${QUEUE[@]}"; do
  IFS='|' read -r topic product deliv_hash loio url <<<"$row"
  slug="${LOIO_FILE[$loio]:-99-$topic}"
  out="$OUT_DIR/${slug}.md"

  echo "→ $topic  ($loio)  → $(basename "$out")"

  if ! read -r deliv_id build <<<"$(get_metadata "$product" "$deliv_hash" "$loio")"; then
    failure=$((failure+1))
    continue
  fi

  resp=$(fetch_page "$deliv_id" "$build" "$loio")
  status=$(echo "$resp" | jq -r '.status // "ERR"')
  if [[ "$status" != "OK" ]]; then
    echo "   FAIL: $(echo "$resp" | head -c 200)" >&2
    failure=$((failure+1))
    continue
  fi

  title=$(echo "$resp" | jq -r '.data.currentPage.t // "Untitled"')
  body_html=$(echo "$resp" | jq -r '.data.body // ""')
  if [[ -z "$body_html" || "$body_html" == "null" ]]; then
    echo "   FAIL: empty body" >&2
    failure=$((failure+1))
    continue
  fi

  tmpf=$(mktemp)
  printf '%s' "$body_html" > "$tmpf"
  md_body=$(pandoc -f html -t gfm --wrap=none "$tmpf")
  rm -f "$tmpf"

  {
    echo "---"
    echo "source_topic: $topic"
    echo "source_url: $url"
    echo "sap_product: $product"
    echo "deliverable_hash: $deliv_hash"
    echo "topic_loio: $loio"
    echo "sap_version: v2211"
    echo "fetched: $today"
    echo "title: \"$title\""
    echo "---"
    echo
    echo "> Mirror of [$title]($url) — fetched $today via reverse-engineered SAP Help Portal JSON API."
    echo "> Authoritative source is the URL above; re-run \`scripts/fetch_sap_docs.sh\` to refresh."
    echo
    echo "$md_body"
  } > "$out"
  success=$((success+1))
done

echo
echo "Done: $success ok, $failure failed"
[[ $failure -eq 0 ]]
