#!/bin/bash
# The whole system on this machine (card #1478). Usage: tools/ops/local/local.sh up | down | wipe | status   (README.md next to this file)
#  up      database container, migrations (profile migrate,local), backend on :8080 (profile local), web client on :5173; waits until each answers
#  down    stops what `up` started (only the processes it recorded); the data stays
#  wipe    down, then deletes the database volume and the generated local settings: the next `up` starts from nothing
# The database password is generated on the first `up` into ~/.forcitis-local/env (mode 0600); it is never printed and never leaves this machine.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
STATE="${FORCITIS_LOCAL_STATE:-$HOME/.forcitis-local}"
ENV_FILE="$STATE/env"
COMPOSE=(docker compose -f "$HERE/compose.yaml" --env-file "$ENV_FILE")
BACKEND_URL="http://localhost:8080/actuator/health"
WEB_URL="http://localhost:5173/"

say() { printf '%s\n' "$*"; }
die() { printf 'local.sh: %s\n' "$*" >&2; exit 1; }

load_env() {
  mkdir -p "$STATE"; chmod 700 "$STATE"
  if [ ! -f "$ENV_FILE" ]; then
    ( umask 077
      { echo "LOCAL_DB_USER=forcitis_local"
        echo "LOCAL_DB_PORT=55432"
        echo "LOCAL_DB_PASSWORD=$(openssl rand -hex 24)"; } > "$ENV_FILE" )
  fi
  [ "$(stat -f '%Lp' "$ENV_FILE" 2>/dev/null || stat -c '%a' "$ENV_FILE")" = "600" ] || die "$ENV_FILE must have mode 600"
  set -a; . "$ENV_FILE"; set +a
}

need() { command -v "$1" >/dev/null 2>&1 || die "missing prerequisite: $1"; }

pid_alive() { [ -f "$1" ] && kill -0 "$(cat "$1")" 2>/dev/null; }

stop_pid() { # only the pid this script recorded, and only while that pid still runs the command it recorded
  local file="$1" pid
  [ -f "$file" ] || return 0
  pid="$(cat "$file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$file"
}

wait_for() { # url, seconds, pid file of the process that must stay alive meanwhile
  for _ in $(seq 1 "$2"); do
    curl -fsS -o /dev/null "$1" 2>/dev/null && return 0
    pid_alive "$3" || return 1
    sleep 1
  done
  return 1
}

cmd_up() {
  need docker; need java; need pnpm; need curl; need openssl
  docker info >/dev/null 2>&1 || die "docker is not running"
  load_env
  if pid_alive "$STATE/backend.pid" || pid_alive "$STATE/web.pid"; then die "already running (tools/ops/local/local.sh status)"; fi
  say "[1/5] database"
  "${COMPOSE[@]}" up -d --wait db >/dev/null
  say "[2/5] build the backend"
  ( cd "$REPO/backend" && ./mvnw -q -DskipTests -Dmaven.test.skip=true package ) || die "backend build failed"
  local jar; jar="$(ls "$REPO"/backend/target/forcitis-one-*.jar | grep -v -- '-plain' | head -1)"
  [ -n "$jar" ] || die "no backend jar under backend/target"
  say "[3/5] migrations (profile migrate,local)"
  SPRING_PROFILES_ACTIVE=migrate,local java -Duser.timezone=UTC -jar "$jar" > "$STATE/migrate.log" 2>&1 || die "migration failed (see $STATE/migrate.log)"
  say "[4/5] backend (profile local) on :8080"
  SPRING_PROFILES_ACTIVE=local FORCITIS_JOBS_ENABLED=false nohup java -Duser.timezone=UTC -jar "$jar" > "$STATE/backend.log" 2>&1 &
  echo $! > "$STATE/backend.pid"
  wait_for "$BACKEND_URL" 180 "$STATE/backend.pid" || die "the backend did not come up (see $STATE/backend.log)"
  say "[5/5] web client on :5173"
  ( cd "$REPO/frontend" && pnpm install --frozen-lockfile >/dev/null )
  # node itself, started directly, so the recorded pid is the one that listens (a pnpm or subshell pid would leave vite running after down)
  cd "$REPO/frontend/apps/web"
  nohup node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort > "$STATE/web.log" 2>&1 &
  echo $! > "$STATE/web.pid"
  cd "$REPO"
  wait_for "$WEB_URL" 60 "$STATE/web.pid" || die "the web client did not come up (see $STATE/web.log)"
  say "up: open $WEB_URL (logs in $STATE)"
}

cmd_down() {
  stop_pid "$STATE/web.pid"
  stop_pid "$STATE/backend.pid"
  if [ -f "$ENV_FILE" ] && command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    load_env; "${COMPOSE[@]}" stop db >/dev/null 2>&1 || true
  fi
  say "down: stopped (the data stays; wipe deletes it)"
}

cmd_wipe() {
  cmd_down
  if [ -f "$ENV_FILE" ] && docker info >/dev/null 2>&1; then
    load_env; "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  rm -rf "$STATE"
  say "wipe: database volume and local settings deleted"
}

cmd_status() {
  pid_alive "$STATE/backend.pid" && say "backend: running (pid $(cat "$STATE/backend.pid"))" || say "backend: stopped"
  pid_alive "$STATE/web.pid" && say "web: running (pid $(cat "$STATE/web.pid"))" || say "web: stopped"
  say "database: $(docker inspect -f '{{.State.Status}}' forcitis-local-db 2>/dev/null || echo none)"
}

cmd_e2e() {
  pid_alive "$STATE/backend.pid" && pid_alive "$STATE/web.pid" || die "the stack is not running: tools/ops/local/local.sh up first"
  ( cd "$REPO/frontend/apps/web" && pnpm exec playwright install chromium-headless-shell >/dev/null && LOCAL_WEB_URL="$WEB_URL" pnpm exec playwright test )
}

case "${1:-}" in
  up) cmd_up ;;
  e2e) cmd_e2e ;;
  down) cmd_down ;;
  wipe) cmd_wipe ;;
  status) cmd_status ;;
  *) die "usage: tools/ops/local/local.sh up | down | wipe | status | e2e" ;;
esac
