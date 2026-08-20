#!/usr/bin/env bash
# Reset the commerce-qrspi demo(s) back to the tagged baseline so they can be re-run cleanly.
#
#   bash demo/qrspi-demo/reset.sh          # full reset: both demos (142 backend + 143 UI)
#   bash demo/qrspi-demo/reset.sh all      # same as above
#   bash demo/qrspi-demo/reset.sh ui       # UI-ONLY: reset just the 143 storefront demo,
#                                          #          leaving the 142 backend run intact
#
# Full reset does what git alone can't: stop servers, `git reset --hard` the baseline (drops
# QRSPI slice commits), remove git-ignored run artifacts, rebuild the backend from clean source.
# UI reset is surgical: it restores only the sibling UI project to baseline (no backend touch,
# no rebuild) so you can re-run the short 143 demo against the same live 142 backend.
#
# Leaves UP: cockpit (ttyd :7681, serve.mjs :8090) and MySQL/colima.
set -uo pipefail

REPO=/Users/emundorf/development/mundo-dev/projects/practice-llm-starter
LEG="$REPO/mcp/legacy/sap-mcp-server-l"
UI="$REPO/mcp/legacy/sap-mcp-ui-l"
TAG=qrspi-demo-baseline
BRANCH=veritiv-ai-approach
JAVA17=/Users/emundorf/.sdkman/candidates/java/17.0.19-sapmchn   # legacy backend builds on Java 17 (NOT 21)
MODE="${1:-all}"

cd "$REPO" || { echo "repo not found"; exit 1; }

stop_backend() {
  if lsof -nP -iTCP:9002 -sTCP:LISTEN >/dev/null 2>&1; then
    ( cd "$LEG/core-customize/hybris/bin/platform" && source ./setantenv.sh >/dev/null 2>&1 && ./hybrisserver.sh stop >/dev/null 2>&1 ) &
    n=0; while lsof -nP -iTCP:9002 -sTCP:LISTEN >/dev/null 2>&1 && [ $n -lt 45 ]; do sleep 1; n=$((n+1)); done
    if lsof -nP -iTCP:9002 -sTCP:LISTEN >/dev/null 2>&1; then
      echo "   graceful stop slow — killing wrapper/JVM…"
      pkill -f "wrapper.*sap-mcp-server-l" 2>/dev/null || true
      lsof -nP -iTCP:9002 -sTCP:LISTEN -t 2>/dev/null | xargs -r kill -9 2>/dev/null || true
    fi
  fi
}
stop_storefront() { lsof -nP -iTCP:5173 -sTCP:LISTEN -t 2>/dev/null | xargs -r kill 2>/dev/null || true; }

if [ "$MODE" = "ui" ]; then
  echo "▸ UI-only reset (THINK-143) — 142 backend left running"
  echo "  1/3  stopping storefront (:5173)…"; stop_storefront
  echo "  2/3  restoring sibling UI project to baseline (src + ticket)…"
  git checkout "$TAG" -- "$UI" 2>/dev/null || true      # revert tracked UI files (src, tickets/…) to baseline
  git clean -fd -- "$UI/src" >/dev/null || true          # drop untracked UI files the run added
  echo "  3/3  removing UI run artifacts (working-docs/THINK-143/)…"; rm -rf "$UI/working-docs/THINK-143"
  echo "✅ UI demo reset. 142 backend untouched; storefront off. Re-run: /cq:0_go THINK-143 simple in sap-mcp-ui-l."
  exit 0
fi

echo "▸ 1/5  stopping app servers (backend :9002, storefront :5173)…"
stop_storefront
stop_backend
lsof -nP -iTCP:9002 -sTCP:LISTEN >/dev/null 2>&1 && echo "   ⚠ 9002 still up" || echo "   servers down (9002 free)."

echo "▸ 2/5  reverting code to '$TAG' (drops demo commits/edits)…"
git checkout -f "$BRANCH" >/dev/null 2>&1 || true
git branch -D qrspi-run >/dev/null 2>&1 || true
git reset --hard "$TAG"

echo "▸ 3/5  removing untracked files the runs added…"
git clean -fd -- "$LEG/core-customize/hybris/bin/custom" "$UI/src" >/dev/null || true

echo "▸ 4/5  removing QRSPI run artifacts (142 backend + 143 UI)…"
rm -rf "$LEG/working-docs/THINK-142" "$UI/working-docs/THINK-143"

echo "▸ 5/5  rebuilding backend from clean source (so the 'before' has no change)…"
if ( cd "$LEG/core-customize" && JAVA_HOME="$JAVA17" ./gradlew yall --console=plain ) >/tmp/qrspi-reset-build.log 2>&1; then
  echo "   build OK."
else
  echo "   build FAILED — see /tmp/qrspi-reset-build.log"
fi

echo
echo "✅ Full reset to baseline. App OFF & cold; cockpit + MySQL left up."
echo "   Re-run:  open http://localhost:8090  →  /cq:0_go THINK-142  (then THINK-143 in the UI tab)."
