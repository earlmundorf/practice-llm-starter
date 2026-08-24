#!/usr/bin/env sh
# QRSPI kit installer — macOS / Linux / WSL / Git Bash.
# Windows without a POSIX shell: use install.ps1 (identical behaviour).
#
#   ./install.sh list                      show available profiles
#   ./install.sh <profile>                 install into the kit's parent directory
#   ./install.sh <profile> --target <dir>  install into <dir> instead
#
# Nothing is ever deleted outside .claude/skills/qrspi and .claude/commands/cq.
# An existing working-docs/config.json is never overwritten.

set -eu

KIT_DIR=$(cd "$(dirname "$0")" && pwd)
PROFILES_DIR="$KIT_DIR/profiles"
KIT_VERSION=$(cat "$KIT_DIR/VERSION" 2>/dev/null || echo "unknown")

die() { printf 'error: %s\n' "$1" >&2; exit 1; }

# --- read one string field out of a profile, without assuming jq exists ----------
# Profiles are constrained to single-line string values (see profiles/README.md).
field() {
  grep -m1 "\"$2\"" "$1" 2>/dev/null \
    | sed -e "s/^.*\"$2\"[[:space:]]*:[[:space:]]*\"//" -e 's/",*[[:space:]]*$//'
}

summary() {  # first entry of _notes
  awk '/"_notes"/{getline; gsub(/^[[:space:]]*"/,""); gsub(/",?[[:space:]]*$/,""); print; exit}' "$1"
}

# --- keep the rendered description inside the Agent Skills 1024-char limit --------
# The description is what a skills-compatible agent matches a request against.
# Over-long frontmatter is rejected *silently* — the skill simply never loads — so
# trim the appended vocabulary to fit and say what was dropped, rather than shipping
# a skill that won't load. Echoes the (possibly trimmed) vocabulary on stdout.
fit_vocabulary() {  # fit_vocabulary <rendered-skill-md> <vocabulary>
  if ! command -v python3 >/dev/null 2>&1; then
    printf '%s' "$2"
    printf '  SKILL.md description             length UNCHECKED (no python3) — cap is 1024 chars\n' >&2
    return
  fi
  python3 - "$1" "$2" <<'PY'
import re, sys
LIMIT = 1024
skill, tv = sys.argv[1], sys.argv[2]
fm = open(skill).read().split('---')[1]
m = re.search(r'description:\s*>?\s*\n((?:  .*\n)+)', fm)
if not m:
    print(tv, end=''); sys.exit()
body = ' '.join(l.strip() for l in m.group(1).splitlines())
budget = LIMIT - len(body.replace('{{TRIGGER_VOCABULARY}}', ''))
if budget <= 0:
    print('', end='')
    print(f"  SKILL.md description             WARNING: base description alone is "
          f"{-budget + LIMIT} chars, over the {LIMIT} limit — shorten it in the kit", file=sys.stderr)
    sys.exit()
kept, dropped, used = [], [], 0
for term in (t.strip() for t in tv.split(',')):
    if not term:
        continue
    add = len(term) + (2 if kept else 0)
    if used + add <= budget:
        kept.append(term); used += add
    else:
        dropped.append(term)
if dropped:
    print(f"  SKILL.md description             trimmed to fit {LIMIT} chars — dropped: "
          f"{', '.join(dropped)}", file=sys.stderr)
print(', '.join(kept), end='')
PY
}

cmd_list() {
  printf '\nQRSPI kit %s — available profiles:\n\n' "$KIT_VERSION"
  for p in "$PROFILES_DIR"/*.json; do
    [ -f "$p" ] || continue
    name=$(basename "$p" .json)
    printf '  %-24s %s\n' "$name" "$(field "$p" stack)"
    printf '  %-24s %s\n\n' "" "$(summary "$p")"
  done
  printf 'Install with:  %s <profile>\n\n' "$0"
}

cmd_install() {
  profile=$1
  target=$2
  src="$PROFILES_DIR/$profile.json"

  # ---- validate everything BEFORE touching the target ---------------------------
  [ -f "$src" ] || { printf 'error: no such profile: %s\n\n' "$profile" >&2; cmd_list >&2; exit 1; }
  [ -d "$target" ] || die "target directory does not exist: $target"
  [ -w "$target" ] || die "target directory is not writable: $target"
  [ -f "$KIT_DIR/skills/qrspi/SKILL.md" ] || die "kit looks incomplete: skills/qrspi/SKILL.md not found"

  tv=$(field "$src" triggerVocabulary)
  [ -n "$tv" ] || die "$profile.json has no single-line triggerVocabulary field"
  case $tv in
    *'|'*|*'&'*) die "triggerVocabulary in $profile.json must not contain '|' or '&'" ;;
  esac

  printf 'Installing profile %s (kit %s)\n  into %s\n\n' "$profile" "$KIT_VERSION" "$target"

  # ---- 1. the skill (generated: replaced wholesale) ------------------------------
  rm -rf "$target/.claude/skills/qrspi"
  mkdir -p "$target/.claude/skills/qrspi"
  cp -R "$KIT_DIR/skills/qrspi/." "$target/.claude/skills/qrspi/"
  printf '  .claude/skills/qrspi/            installed\n'

  # ---- 2. render the one placeholder in the installed copy -----------------------
  skill="$target/.claude/skills/qrspi/SKILL.md"
  tv=$(fit_vocabulary "$skill" "$tv")
  sed "s|{{TRIGGER_VOCABULARY}}|$tv|" "$skill" > "$skill.tmp" && mv "$skill.tmp" "$skill"
  grep -q '{{TRIGGER_VOCABULARY}}' "$skill" && die "placeholder substitution failed in SKILL.md"
  printf '  SKILL.md frontmatter             rendered from triggerVocabulary\n'

  # ---- 3. publish the /cq: commands ---------------------------------------------
  mkdir -p "$target/.claude/commands/cq"
  cp "$KIT_DIR/skills/qrspi/commands/"*.md "$target/.claude/commands/cq/"
  printf '  .claude/commands/cq/             published (/cq:go … /cq:7_validate)\n'

  # ---- 4. the config: never overwrite -------------------------------------------
  mkdir -p "$target/working-docs"
  cfg="$target/working-docs/config.json"
  if [ -f "$cfg" ]; then
    cp "$src" "$cfg.new"
    printf '  working-docs/config.json         KEPT (yours) — profile written to config.json.new\n'
    config_note="existing config kept; review config.json.new and merge, then delete it"
  else
    cp "$src" "$cfg"
    printf '  working-docs/config.json         written from %s\n' "$profile"
    config_note="config written; fill in any <placeholders> it names"
  fi

  # ---- 5. seed the findings log --------------------------------------------------
  mkdir -p "$target/working-docs/findings"
  for f in README.md TEMPLATE.md; do
    if [ ! -f "$target/working-docs/findings/$f" ]; then
      cp "$KIT_DIR/skills/qrspi/findings-seed/$f" "$target/working-docs/findings/$f"
    fi
  done
  printf '  working-docs/findings/           seeded (existing findings untouched)\n'

  # ---- 6. stamp what produced this install --------------------------------------
  cat > "$target/.claude/skills/qrspi/.installed-from" <<EOF
profile: $profile
profileVersion: $(field "$src" profileVersion)
kitVersion: $KIT_VERSION
installedAt: $(date +%Y-%m-%d)
source: qrspi-kit/skills/qrspi
note: generated directory — edit the kit and re-install, never edit here
EOF
  printf '  .installed-from                  stamped\n'

  # ---- 7. ignore the kit, when it lives inside the target ------------------------
  kit_inside_target=no
  if [ "$(dirname "$KIT_DIR")" = "$(cd "$target" && pwd)" ]; then
    kit_inside_target=yes
  fi
  if [ "$kit_inside_target" = yes ]; then
    gi="$target/.gitignore"
    if ! { [ -f "$gi" ] && grep -qxF '/qrspi-kit/' "$gi"; }; then
      { [ -f "$gi" ] && [ -n "$(tail -c1 "$gi" 2>/dev/null)" ] && printf '\n'; } >> "$gi" || true
      printf '# QRSPI kit — local tool copy, not part of the project\n/qrspi-kit/\n' >> "$gi"
      printf '  .gitignore                       added /qrspi-kit/\n'
    else
      printf '  .gitignore                       already ignores /qrspi-kit/\n'
    fi
  fi

  # ---- schema check (best effort: needs python3; the kit lints profiles in CI) ---
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$cfg" <<'PY' || true
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception as e:
    print(f"  config check                     WARNING: unreadable JSON ({e})"); sys.exit()
known = {"profile","profileVersion","stack","workingDir","buildTool","packageManager","appModule",
         "framework","protectedPaths","apiBoundary","build","changeTypeVerbs","jira","researchLayers",
         "questionCategories","manualVerificationSurfaces","sliceExample","verbNamespaces",
         "triggerVocabulary","_notes","project"}
bad = [v for vs in d.get("changeTypeVerbs", {}).values() for v in vs if v not in d.get("build", {})]
unknown = sorted(set(d) - known)
if bad:     print(f"  config check                     ERROR: changeTypeVerbs names verbs missing from build: {sorted(set(bad))}")
if unknown: print(f"  config check                     warning: unknown keys (typo?): {unknown}")
if not bad and not unknown: print("  config check                     ok")
PY
  else
    printf '  config check                     skipped (no python3)\n'
  fi

  printf '\nDone. %s\n' "$config_note"
  printf 'Next:  /cq:go <TICKET-KEY>          (tiers: trivial | simple | full | comprehensive)\n'
  if [ "$kit_inside_target" = yes ]; then
    printf 'The kit was left in place; it is gitignored and safe to delete by hand.\n\n'
  else
    printf 'Installed from %s (left untouched).\n\n' "$KIT_DIR"
  fi
}

main() {
  [ $# -ge 1 ] || { cmd_list; exit 0; }
  case $1 in
    list|--list|-l)   cmd_list; exit 0 ;;
    -h|--help|help)   cmd_list; exit 0 ;;
  esac

  profile=$1; shift
  target=$(dirname "$KIT_DIR")
  while [ $# -gt 0 ]; do
    case $1 in
      --target) [ $# -ge 2 ] || die "--target needs a directory"; target=$2; shift 2 ;;
      *)        die "unknown argument: $1" ;;
    esac
  done
  cmd_install "$profile" "$target"
}

main "$@"
