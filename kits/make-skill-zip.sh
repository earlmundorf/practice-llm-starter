#!/usr/bin/env sh
# Bundle any set of skills from this repo into a single zip.
#
#   ./kits/make-skill-zip.sh list                          what's available, and from where
#   ./kits/make-skill-zip.sh sap-hac-access impex          pick any skills
#   ./kits/make-skill-zip.sh -o hac-only sap-hac-access    name the zip
#   ./kits/make-skill-zip.sh --from sap-llm-template impex take a specific copy
#
# make-kits.sh builds the three FIXED distributables. This builds an AD-HOC one — same
# no-duplication rule: skills are read from the project that owns them, never from a copy
# kept around just for packaging.

set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIST="$ROOT/kits/dist"
WORK="$DIST/.work-adhoc"
OUT_NAME="custom-skills-kit"
FROM=""

die() { printf 'error: %s\n' "$1" >&2; exit 1; }

usage() {
  sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

# Where skills live. Order matters: when a skill exists in more than one project, the first
# match wins. Same precedence make-kits.sh uses, so ad-hoc zips agree with the fixed kits.
PREFERENCE="mcp/legacy/sap-mcp-server-l/.claude/skills
sap-llm-template/.claude/skills
sap-ui-template-react/.claude/skills
qrspi-kit/skills
mcp/jdk21/sap-mcp-server/.claude/skills
mcp/legacy/sap-mcp-ui-l/.claude/skills
mcp/jdk21/sap-mcp-ui/.claude/skills"

# Every skills directory in the repo, preferred roots first, then anything else discovered.
skill_roots() {
  printf '%s\n' "$PREFERENCE" | while IFS= read -r rel; do
    [ -d "$ROOT/$rel" ] && printf '%s\n' "$rel"
  done
  find "$ROOT" \
    -type d \( -name node_modules -o -name .git -o -name dist -o -name target \) -prune -o \
    -type d -path '*/.claude/skills' -print 2>/dev/null |
    sed "s|^$ROOT/||" |
    while IFS= read -r rel; do
      printf '%s\n' "$PREFERENCE" | grep -qxF "$rel" || printf '%s\n' "$rel"
    done
}

skills_in_root() {  # skills_in_root <rel-root>
  for d in "$ROOT/$1"/*/; do
    [ -f "$d/SKILL.md" ] && basename "$d"
  done
}

resolve_skill() {  # resolve_skill <name> -> rel-root of the copy to use
  if [ -n "$FROM" ]; then
    for rel in "$FROM" "$FROM/.claude/skills"; do
      [ -f "$ROOT/$rel/$1/SKILL.md" ] && { printf '%s\n' "$rel"; return 0; }
    done
    return 1
  fi
  skill_roots | while IFS= read -r rel; do
    [ -f "$ROOT/$rel/$1/SKILL.md" ] && { printf '%s\n' "$rel"; break; }
  done | head -1 | grep . || return 1
}

copies_of() {  # copies_of <name> -> every rel-root holding it
  skill_roots | while IFS= read -r rel; do
    [ -f "$ROOT/$rel/$1/SKILL.md" ] && printf '%s\n' "$rel"
  done
}

do_list() {
  names=$(skill_roots | while IFS= read -r rel; do skills_in_root "$rel"; done | sort -u)
  [ -n "$names" ] || die "no skills found under $ROOT"
  printf '\n%-30s %s\n' 'SKILL' 'SOURCE USED'
  printf '%-30s %s\n' '-----' '-----------'
  for n in $names; do
    src=$(resolve_skill "$n") || continue
    n_copies=$(copies_of "$n" | wc -l | tr -d ' ')
    if [ "$n_copies" -gt 1 ]; then
      printf '%-30s %s  (%s copies)\n' "$n" "$src" "$n_copies"
    else
      printf '%-30s %s\n' "$n" "$src"
    fi
  done
  printf '\nWhere a skill has several copies they may differ; the source shown is the one used.\n'
  printf 'Override per build with --from <project-or-skills-dir>.\n\n'
}

write_install() {  # write_install <dest-file> <skill-names...>
  dest=$1; shift
  {
    printf '# %s — install\n\n' "$OUT_NAME"
    printf 'Claude Code skills, bundled on %s.\n\n' "$(date +%Y-%m-%d)"
    printf '| Skill | Source |\n|---|---|\n'
    for n in "$@"; do
      printf '| `%s` | `%s` |\n' "$n" "$(resolve_skill "$n")"
    done
    cat <<EOF

## Install

Unzip anywhere, then copy the skills into your project.

**macOS / Linux / WSL / Git Bash**

\`\`\`bash
unzip $OUT_NAME.zip
mkdir -p /path/to/your-repo/.claude/skills
cp -R $OUT_NAME/.claude/skills/* /path/to/your-repo/.claude/skills/
\`\`\`

**Windows PowerShell**

\`\`\`powershell
Expand-Archive $OUT_NAME.zip -DestinationPath .
New-Item -ItemType Directory -Force -Path C:\\path\\to\\your-repo\\.claude\\skills | Out-Null
Copy-Item $OUT_NAME\\.claude\\skills\\* C:\\path\\to\\your-repo\\.claude\\skills\\ -Recurse -Force
\`\`\`

Copying **merges** into \`.claude/skills/\`: skills already there are untouched unless they
share a name, in which case they're replaced. Nothing else in the project is modified.
Claude Code discovers the skills on the next session — there is no configuration step.
EOF
    scripted=""
    for n in "$@"; do
      ls "$WORK/$OUT_NAME/.claude/skills/$n"/scripts/*.sh >/dev/null 2>&1 && scripted="$scripted $n"
    done
    if [ -n "$scripted" ]; then
      printf '\n## Shell scripts\n\n'
      printf 'These skills ship scripts, and zip does not reliably carry the executable bit:\n\n'
      printf '```bash\nchmod +x'
      for n in $scripted; do printf ' .claude/skills/%s/scripts/*.sh' "$n"; done
      printf '\n```\n'
    fi
  } > "$dest"
}

# --- args -----------------------------------------------------------------------------
[ "$#" -gt 0 ] || usage 1
SKILLS=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    list|--list)   ;;
    -h|--help)     usage 0 ;;
    -o|--out)      [ "${2:-}" ] || die "-o needs a name"; OUT_NAME=$(basename "$2" .zip); shift ;;
    --from)        [ "${2:-}" ] || die "--from needs a path"; FROM=${2#"$ROOT"/}; FROM=${FROM%/}; shift ;;
    -*)            die "unknown option: $1" ;;
    *)             SKILLS="$SKILLS $1" ;;
  esac
  case "$1" in list|--list) DO_LIST=1 ;; esac
  shift
done

command -v zip >/dev/null 2>&1 || die "zip not found on PATH"
mkdir -p "$DIST"

if [ "${DO_LIST:-0}" = 1 ]; then
  do_list
  exit 0
fi

[ -n "$SKILLS" ] || die "no skills named — try: $(basename "$0") list"
[ -z "$FROM" ] || [ -d "$ROOT/$FROM" ] || die "--from path not in repo: $FROM"

# --- build ----------------------------------------------------------------------------
printf '\n== %s.zip ==\n' "$OUT_NAME"
rm -rf "$WORK"; mkdir -p "$WORK/$OUT_NAME/.claude/skills"
for s in $SKILLS; do
  src=$(resolve_skill "$s") || die "skill not found: $s (try: $(basename "$0") list)"
  cp -R "$ROOT/$src/$s" "$WORK/$OUT_NAME/.claude/skills/$s"
  if grep -rqlF '{{TRIGGER_VOCABULARY}}' "$WORK/$OUT_NAME/.claude/skills/$s" 2>/dev/null; then
    printf '   ! %s has unrendered {{TRIGGER_VOCABULARY}} — it expects an installer\n' "$s"
  fi
  printf '   + %-28s %s\n' "$s" "$src"
done

# shellcheck disable=SC2086
write_install "$WORK/$OUT_NAME/INSTALL.md" $SKILLS

rm -f "$DIST/$OUT_NAME.zip"
( cd "$WORK" && zip -qr "$DIST/$OUT_NAME.zip" "$OUT_NAME" )
rm -rf "$WORK"
printf '   %s\n\n' "$DIST/$OUT_NAME.zip"
