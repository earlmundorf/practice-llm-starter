#!/usr/bin/env sh
# Build the three distributable kits into kits/dist/.
#
#   ./kits/make-kits.sh            build all three
#   ./kits/make-kits.sh qrspi      build one (qrspi | backend | frontend)
#
# The kits are BUILD ARTIFACTS. Skills are never duplicated in this repo just to be
# packaged — each kit is assembled from a designated source copy, listed below, so the
# provenance of every zip is auditable.

set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIST="$ROOT/kits/dist"
WORK="$DIST/.work"

die() { printf 'error: %s\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------------------------
# Source of truth for each kit's contents.
#
# qrspi     — qrspi-kit/ is itself the canonical source; zipped as-is.
# backend   — mcp/legacy/sap-mcp-server-l is the actively-maintained backend project and
#             carries the newest sap-commerce-migrate-j21. NOTE: sap-llm-template has
#             copies of all five that differ slightly; they have not been reconciled.
# frontend  — spartacus-* come from sap-llm-template (the Angular/Spartacus template);
#             the React skills come from sap-ui-template-react. NOTE: sap-mcp-ui-l has a
#             react-ecommerce that differs from the template's; the template wins here.
# ---------------------------------------------------------------------------------
BACKEND_SRC="$ROOT/mcp/legacy/sap-mcp-server-l/.claude/skills"
BACKEND_SKILLS="sap-commerce impex sap-best-practices java-best-practices sap-commerce-migrate-j21"

SPARTACUS_SRC="$ROOT/sap-llm-template/.claude/skills"
SPARTACUS_SKILLS="spartacus-component spartacus-forms spartacus-i18n spartacus-occ spartacus-routing spartacus-state spartacus-styling spartacus-testing spartacus-upgrade"

REACT_SRC="$ROOT/sap-ui-template-react/.claude/skills"
REACT_SKILLS="react-ecommerce react-typescript commerce-storefront spartacus-storefront"

have_zip() { command -v zip >/dev/null 2>&1 || die "zip not found on PATH"; }

stage_skill() {  # stage_skill <src-root> <name> <dest-skills-dir>
  [ -d "$1/$2" ] || die "missing source skill: $1/$2"
  cp -R "$1/$2" "$3/$2"
}

build_qrspi() {
  printf '\n== qrspi-kit.zip ==\n'
  [ -f "$ROOT/qrspi-kit/skills/qrspi/SKILL.md" ] || die "qrspi-kit looks incomplete"
  grep -q '{{TRIGGER_VOCABULARY}}' "$ROOT/qrspi-kit/skills/qrspi/SKILL.md" \
    || die "qrspi-kit/skills/qrspi/SKILL.md has no {{TRIGGER_VOCABULARY}} placeholder — did an install render it in place?"
  rm -rf "$WORK"; mkdir -p "$WORK"
  cp -R "$ROOT/qrspi-kit" "$WORK/qrspi-kit"
  rm -rf "$WORK/qrspi-kit/dist"
  ( cd "$WORK" && zip -qr "$DIST/qrspi-kit.zip" qrspi-kit )
  printf '   %s\n' "$DIST/qrspi-kit.zip"
}

build_backend() {
  printf '\n== backend-skills-kit.zip ==\n'
  rm -rf "$WORK"; mkdir -p "$WORK/backend-skills-kit/.claude/skills"
  for s in $BACKEND_SKILLS; do
    stage_skill "$BACKEND_SRC" "$s" "$WORK/backend-skills-kit/.claude/skills"
    printf '   + %s\n' "$s"
  done
  cp "$ROOT/kits/backend-INSTALL.md" "$WORK/backend-skills-kit/INSTALL.md"
  ( cd "$WORK" && zip -qr "$DIST/backend-skills-kit.zip" backend-skills-kit )
  printf '   %s\n' "$DIST/backend-skills-kit.zip"
}

build_frontend() {
  printf '\n== frontend-skills-kit.zip ==\n'
  rm -rf "$WORK"; mkdir -p "$WORK/frontend-skills-kit/.claude/skills"
  for s in $SPARTACUS_SKILLS; do
    stage_skill "$SPARTACUS_SRC" "$s" "$WORK/frontend-skills-kit/.claude/skills"
    printf '   + %s\n' "$s"
  done
  for s in $REACT_SKILLS; do
    stage_skill "$REACT_SRC" "$s" "$WORK/frontend-skills-kit/.claude/skills"
    printf '   + %s\n' "$s"
  done
  cp "$ROOT/kits/frontend-INSTALL.md" "$WORK/frontend-skills-kit/INSTALL.md"
  ( cd "$WORK" && zip -qr "$DIST/frontend-skills-kit.zip" frontend-skills-kit )
  printf '   %s\n' "$DIST/frontend-skills-kit.zip"
}

have_zip
mkdir -p "$DIST"
case "${1:-all}" in
  all)      rm -f "$DIST"/*.zip; build_qrspi; build_backend; build_frontend ;;
  qrspi)    rm -f "$DIST/qrspi-kit.zip"; build_qrspi ;;
  backend)  rm -f "$DIST/backend-skills-kit.zip"; build_backend ;;
  frontend) rm -f "$DIST/frontend-skills-kit.zip"; build_frontend ;;
  *)        die "unknown kit: $1 (expected: all | qrspi | backend | frontend)" ;;
esac
rm -rf "$WORK"

printf '\nBuilt into %s\n\n' "$DIST"
