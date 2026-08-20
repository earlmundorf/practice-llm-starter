#!/usr/bin/env bash
# Demo environment control for the commerce-qrspi demo.
#
#   bash demo/qrspi-demo/demo.sh up        # start everything (MySQL → backend → storefront → console → ttyd)
#   bash demo/qrspi-demo/demo.sh down      # stop app services (MySQL left up)
#   bash demo/qrspi-demo/demo.sh down --db # also stop the MySQL container + colima
#   bash demo/qrspi-demo/demo.sh status    # show what's up
#   bash demo/qrspi-demo/demo.sh restart   # down (app services) then up
#
# Assumes the backend is already built + initialized (see RESTORE.md / reset.sh for a cold build).
set -uo pipefail

REPO=/Users/emundorf/development/mundo-dev/projects/practice-llm-starter
LEG="$REPO/mcp/legacy/sap-mcp-server-l"
UI="$REPO/mcp/legacy/sap-mcp-ui-l"
CONSOLE="$REPO/demo/qrspi-demo"
JAVA17=/Users/emundorf/.sdkman/candidates/java/17.0.19-sapmchn
NODE_BIN=/Users/emundorf/.nvm/versions/node/v24.14.0/bin
TTYD=/opt/homebrew/bin/ttyd
MYSQL_CTR=hybris-mysql
LOGDIR=/tmp

# resolve docker/colima (not always on the non-interactive PATH)
DOCKER="$(command -v docker || ls /opt/homebrew/Cellar/docker/*/bin/docker 2>/dev/null | head -1 || true)"
COLIMA="$(command -v colima || echo /opt/homebrew/bin/colima)"
export PATH="/opt/homebrew/bin:$NODE_BIN:$PATH"

c_grn=$'\e[32m'; c_red=$'\e[31m'; c_dim=$'\e[2m'; c_off=$'\e[0m'
up()   { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }
say()  { printf '  %s\n' "$*"; }
ok()   { printf "  ${c_grn}✓${c_off} %s\n" "$*"; }
warn() { printf "  ${c_red}!${c_off} %s\n" "$*"; }
wait_up()   { local p=$1 n=0; until up "$p"; do sleep 1; n=$((n+1)); [ $n -ge "${2:-60}" ] && return 1; done; }
wait_down() { local p=$1 n=0; while up "$p"; do sleep 1; n=$((n+1)); [ $n -ge "${2:-45}" ] && return 1; done; }
kill_port() { local pids; pids=$(lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null); [ -n "$pids" ] && { echo "$pids"|xargs kill 2>/dev/null; sleep 1; echo "$pids"|xargs kill -9 2>/dev/null; }; return 0; }

mysql_up() {
  [ -x "$DOCKER" ] || { warn "docker not found"; return 1; }
  "$COLIMA" status >/dev/null 2>&1 || { say "starting colima…"; "$COLIMA" start >/dev/null 2>&1; }
  if "$DOCKER" ps --format '{{.Names}}' 2>/dev/null | grep -qx "$MYSQL_CTR"; then ok "MySQL ($MYSQL_CTR) already running"
  else "$DOCKER" start "$MYSQL_CTR" >/dev/null 2>&1 && ok "MySQL ($MYSQL_CTR) started" || warn "could not start $MYSQL_CTR"; fi
}
backend_up() {
  if up 9002; then ok "backend already up (9002)"; return; fi
  for p in 9001 9002 8983; do up "$p" && { warn "port $p already taken by another process — run 'down' first"; return 1; }; done
  say "starting backend (Java 17)… this can take a minute"
  ( cd "$LEG/core-customize" && JAVA_HOME="$JAVA17" ./gradlew startServer --console=plain >"$LOGDIR/demo-backend.log" 2>&1 )
  wait_up 9002 120 && ok "backend up (HAC :9001 / OCC :9002)" || warn "backend didn't come up — see $LOGDIR/demo-backend.log"
}
backend_down() {
  if up 9002; then
    say "stopping backend…"
    ( cd "$LEG/core-customize" && JAVA_HOME="$JAVA17" ./gradlew stopServer --console=plain >"$LOGDIR/demo-backend-stop.log" 2>&1 ) &
    wait_down 9002 40 || { warn "graceful slow — hard-killing"; pkill -f 'wrapper.*sap-mcp-server' 2>/dev/null; kill_port 9002; kill_port 9001; }
  fi
  kill_port 8983   # Solr
  ok "backend + Solr stopped"
}
storefront_up() {
  if up 5173; then ok "storefront already up (5173)"; return; fi
  [ -d "$UI/node_modules" ] || { say "installing storefront deps…"; ( cd "$UI" && npm install >"$LOGDIR/demo-ui-install.log" 2>&1 ); }
  ( cd "$UI" && nohup npm run dev >"$LOGDIR/demo-ui.log" 2>&1 & )
  wait_up 5173 40 && ok "storefront up (:5173)" || warn "storefront didn't come up — see $LOGDIR/demo-ui.log"
}
console_up() {
  if up 8090; then ok "console already up (8090)"; return; fi
  ( cd "$CONSOLE" && nohup node serve.mjs >"$LOGDIR/demo-console.log" 2>&1 & )
  wait_up 8090 15 && ok "console up (:8090)" || warn "console didn't come up — see $LOGDIR/demo-console.log"
}
ttyd_up() {
  if up 7681; then ok "ttyd already up (7681)"; return; fi
  [ -x "$TTYD" ] || { warn "ttyd not found at $TTYD"; return; }
  nohup "$TTYD" -W -t fontSize=15 -t 'theme={"background":"#0b0b0f"}' zsh -l >"$LOGDIR/demo-ttyd.log" 2>&1 &
  wait_up 7681 10 && ok "ttyd up (:7681)" || warn "ttyd didn't come up"
}

do_status() {
  echo "Demo environment:"
  for row in "9001 HAC" "9002 OCC REST" "8983 Solr" "5173 storefront" "8090 console" "7681 ttyd"; do
    set -- $row; p=$1; shift; name="$*"
    up "$p" && printf "  ${c_grn}● up  ${c_off} %-5s %s\n" "$p" "$name" || printf "  ${c_dim}○ down${c_off} %-5s %s\n" "$p" "$name"
  done
  if [ -x "$DOCKER" ] && "$DOCKER" ps --format '{{.Names}}' 2>/dev/null | grep -qx "$MYSQL_CTR"; then
    printf "  ${c_grn}● up  ${c_off} %-5s %s\n" "-" "MySQL ($MYSQL_CTR)"
  else
    printf "  ${c_dim}○ down${c_off} %-5s %s\n" "-" "MySQL ($MYSQL_CTR)"
  fi
}

case "${1:-status}" in
  up)
    echo "▸ starting demo environment…"
    mysql_up; backend_up; storefront_up; console_up; ttyd_up
    echo; do_status; echo; echo "  open http://localhost:8090"
    ;;
  down)
    echo "▸ stopping app services…"
    backend_down
    kill_port 5173 && ok "storefront stopped"
    kill_port 8090 && ok "console stopped"
    kill_port 7681 && ok "ttyd stopped"
    if [ "${2:-}" = "--db" ]; then
      [ -x "$DOCKER" ] && "$DOCKER" stop "$MYSQL_CTR" >/dev/null 2>&1 && ok "MySQL stopped"
      "$COLIMA" stop >/dev/null 2>&1 && ok "colima stopped"
    else
      say "MySQL left up (use 'down --db' to stop it too)"
    fi
    ;;
  restart) "$0" down; echo; "$0" up ;;
  status)  do_status ;;
  *) echo "usage: $0 {up|down [--db]|status|restart}"; exit 2 ;;
esac
