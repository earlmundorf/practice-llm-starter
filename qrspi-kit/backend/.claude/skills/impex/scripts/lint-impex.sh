#!/usr/bin/env bash
# lint-impex.sh — Offline syntax linter for SAP Commerce ImpEx files.
# Usage: lint-impex.sh <file>   or   lint-impex.sh -   (reads stdin)
set -euo pipefail

# ── Input handling ──────────────────────────────────────────────────────────
if [[ $# -lt 1 ]]; then
  echo "Usage: lint-impex.sh <file | ->" >&2; exit 2
fi
if [[ "$1" == "-" ]]; then
  input=$(cat)
else
  [[ -f "$1" ]] || { echo "ERROR: File not found: $1" >&2; exit 2; }
  input=$(cat "$1")
fi
# Normalise Windows line endings
input="${input//$'\r'/}"

errors=0; warnings=0
declare -A macros         # tracks defined macros ($name -> value)
declare -a used_macros    # tracks macro references for later check

# Common typos — map typo -> suggestion
declare -rA typos=(
  [INSERT_UDPATE]="INSERT_UPDATE" [INSER_UPDATE]="INSERT_UPDATE"
  [INSRT_UPDATE]="INSERT_UPDATE"  [INSERT_UPDAT]="INSERT_UPDATE"
  [INSERT_UDATE]="INSERT_UPDATE"  [UDPATE]="UPDATE"
  [REMOV]="REMOVE"                [REOMVE]="REMOVE"
  [INSRET]="INSERT"               [INSRT]="INSERT"
  [INSER]="INSERT"                [UPATE]="UPDATE"
  [INSERT_UPDTE]="INSERT_UPDATE"  [INSERTT_UPDATE]="INSERT_UPDATE"
)

# Types that typically require catalogVersion
readonly catalog_types="Product|Category|Media|PriceRow|TaxRow|DiscountRow|MediaContainer|Keyword"

emit_error() { echo "ERROR: line $1: $2"; errors=$((errors + 1)); }
emit_warn()  { echo "WARN: line $1: $2";  warnings=$((warnings + 1)); }

header_line=0; header_mode=""; header_type=""; header_semicolons=0
reset_header() { header_line=0; header_mode=""; header_type=""; header_semicolons=0; }

lineno=0
while IFS= read -r line || [[ -n "$line" ]]; do
  lineno=$((lineno + 1))
  # Skip empty lines (they also reset the current header block)
  if [[ -z "${line// /}" ]]; then
    reset_header; continue
  fi
  # Skip comments and scripted impex (#% lines)
  if [[ "$line" =~ ^[[:space:]]*#% ]]; then continue; fi
  if [[ "$line" =~ ^[[:space:]]*# ]]; then continue; fi

  # ── Macro definition ────────────────────────────────────────────────────
  if [[ "$line" =~ ^\$([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*= ]]; then
    macro_val="${line#*=}"
    macro_val="${macro_val#"${macro_val%%[![:space:]]*}"}"  # trim leading spaces
    macros["${BASH_REMATCH[1]}"]="$macro_val"
    # Also collect any macro references on the RHS
    rhs="${line#*=}"
    while [[ "$rhs" =~ \$([A-Za-z_][A-Za-z0-9_]*) ]]; do
      used_macros+=("${BASH_REMATCH[1]}:$lineno")
      rhs="${rhs#*"${BASH_REMATCH[0]}"}"
    done
    continue
  fi

  # ── Collect macro references on any non-definition line ─────────────────
  scan="$line"
  while [[ "$scan" =~ \$([A-Za-z_][A-Za-z0-9_]*) ]]; do
    used_macros+=("${BASH_REMATCH[1]}:$lineno")
    scan="${scan#*"${BASH_REMATCH[0]}"}"
  done

  # ── Header line detection ───────────────────────────────────────────────
  # A header starts with a mode keyword (possibly with leading spaces)
  trimmed="${line#"${line%%[![:space:]]*}"}"
  first_word="${trimmed%% *}"
  first_word="${first_word%%;*}"  # strip trailing semicolon if glued

  # Check for typos via direct lookup (O(1) instead of iterating all keys)
  is_typo=false
  upper="${first_word^^}"
  # Guard: only attempt lookup if upper looks like a mode keyword (letters/underscores)
  if [[ "$upper" =~ ^[A-Z_]+$ ]] && [[ -n "${typos[$upper]+x}" ]]; then
    emit_error "$lineno" "Typo in mode — \"$first_word\" (did you mean ${typos[$upper]}?)"
    is_typo=true
  fi

  if [[ "$first_word" =~ ^(INSERT_UPDATE|INSERT|UPDATE|REMOVE)$ ]] || $is_typo; then
    # This is a header line
    if $is_typo; then
      # Still parse it as a header for semicolon counting
      mode="${typos[$upper]:-INSERT_UPDATE}"
    else
      mode="$first_word"
    fi

    # Extract type name (word after mode)
    rest="${trimmed#"$first_word"}"
    rest="${rest#"${rest%%[![:space:]]*}"}"
    type_name="${rest%%;*}"
    type_name="${type_name%% *}"
    type_name="${type_name%%[*}"  # strip modifier bracket if glued

    header_line=$lineno
    header_mode="$mode"
    header_type="$type_name"

    # Count semicolons in header
    tmp="${line//[^;]/}"
    header_semicolons=${#tmp}

    # ── WARN: INSERT instead of INSERT_UPDATE ─────────────────────────
    if [[ "$mode" == "INSERT" ]]; then
      emit_warn "$lineno" "Using INSERT instead of INSERT_UPDATE — not idempotent"
    fi

    # ── ERROR: No [unique=true] (except REMOVE) ──────────────────────
    if [[ "$mode" != "REMOVE" ]]; then
      # Check for [unique=true] or [unique = true] anywhere on the line
      if ! [[ "$line" =~ \[.*unique[[:space:]]*=[[:space:]]*true.*\] ]]; then
        emit_error "$lineno" "No [unique=true] column in header for $type_name"
      fi
    fi

    # ── WARN: Missing catalogVersion on catalog-aware types ───────────
    if [[ "$type_name" =~ ^($catalog_types)$ ]]; then
      has_cv=false
      if [[ "${line,,}" =~ catalogversion ]]; then
        has_cv=true
      else
        # Check if any macro referenced on this line expands to contain catalogVersion
        check_scan="$line"
        while [[ "$check_scan" =~ \$([A-Za-z_][A-Za-z0-9_]*) ]]; do
          ref_name="${BASH_REMATCH[1]}"
          ref_match="${BASH_REMATCH[0]}"
          if [[ -n "${macros[$ref_name]+x}" ]]; then
            if [[ "${macros[$ref_name],,}" == *catalogversion* ]]; then
              has_cv=true; break
            fi
          fi
          check_scan="${check_scan#*"$ref_match"}"
        done
      fi
      if ! $has_cv; then
        emit_warn "$lineno" "Missing catalogVersion column on $type_name (catalog-aware type)"
      fi
    fi

    continue
  fi

  # ── Data line detection ─────────────────────────────────────────────────
  # Data lines start with ; (possibly with leading whitespace)
  if [[ "$trimmed" == ";"* ]] && [[ $header_line -gt 0 ]]; then
    # Count semicolons
    tmp="${line//[^;]/}"
    data_semicolons=${#tmp}

    if [[ $data_semicolons -gt $header_semicolons ]]; then
      emit_error "$lineno" "Too many columns: header (line $header_line) has $header_semicolons semicolons, this data line has $data_semicolons"
    fi

    # ── WARN: Hardcoded PKs (8+ digit numbers) ───────────────────────
    if [[ "$line" =~ (^|;)[[:space:]]*([0-9]{8,})[[:space:]]*(;|$) ]]; then
      emit_warn "$lineno" "Possible hardcoded PK: \"${BASH_REMATCH[2]}\""
    fi
  fi

done <<< "$input"

# ── Check undefined macro references ──────────────────────────────────────
for entry in "${used_macros[@]+"${used_macros[@]}"}"; do
  [[ -z "$entry" ]] && continue
  name="${entry%%:*}"
  ref_line="${entry##*:}"
  if [[ -z "${macros[$name]+x}" ]]; then
    emit_error "$ref_line" "Undefined macro reference: \$$name"
  fi
done

# ── Summary ───────────────────────────────────────────────────────────────
if [[ $errors -eq 0 && $warnings -eq 0 ]]; then
  echo "OK: No issues found"
  exit 0
fi

echo ""
echo "Summary: $errors errors, $warnings warnings"
[[ $errors -gt 0 ]] && exit 1
exit 0
