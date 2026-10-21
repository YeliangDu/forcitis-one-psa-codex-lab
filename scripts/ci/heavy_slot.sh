#!/usr/bin/env bash
# Runs a heavy command (backend compile or tests) in one of a few machine-wide slots, at background priority.
# Usage: heavy_slot.sh [--no-slot] -- command [args...]
#   default     waits for a free slot (at most FORCITIS_HEAVY_SLOTS run at once, default 2, across every checkout and session of this user), then runs the command
#   --no-slot   only lowers the priority (frontend verify: heavy, but not what saturates the machine's containers)
# Environment: FORCITIS_HEAVY_SLOTS (default 2), FORCITIS_HEAVY_WAIT_SECONDS (default 3600; exit 75 when no slot frees up), FORCITIS_HEAVY_DIR (slot directory, default ~/.cache/forcitis-one/heavy-slots)
# A slot is a directory holding the owner's pid and start time; one whose owner is gone is reclaimed. Bash 3.2 compatible (macOS).
set -uo pipefail

use_slot=true
if [ "${1:-}" = "--no-slot" ]; then use_slot=false; shift; fi
[ "${1:-}" = "--" ] && shift
[ $# -gt 0 ] || { echo "usage: heavy_slot.sh [--no-slot] -- command [args...]" >&2; exit 2; }

slots=${FORCITIS_HEAVY_SLOTS:-2}
wait_limit=${FORCITIS_HEAVY_WAIT_SECONDS:-3600}
dir=${FORCITIS_HEAVY_DIR:-$HOME/.cache/forcitis-one/heavy-slots}
case "$slots$wait_limit" in *[!0-9]*|"") echo "heavy_slot.sh: FORCITIS_HEAVY_SLOTS and FORCITIS_HEAVY_WAIT_SECONDS must be whole numbers" >&2; exit 2 ;; esac

stamp() { ps -o lstart= -p "$1" 2>/dev/null | tr -s ' '; }

owner_alive() {
  local slot=$1 pid started
  pid=$(sed -n 1p "$slot/owner" 2>/dev/null) || return 1
  started=$(sed -n 2p "$slot/owner" 2>/dev/null)
  [ -n "$pid" ] && [ -n "$started" ] && [ "$(stamp "$pid")" = "$started" ]
}

# A directory without an owner file is a claim being written; only one older than a minute is a crashed claim
reclaimable() {
  local slot=$1
  if [ -f "$slot/owner" ]; then owner_alive "$slot" && return 1; return 0; fi
  [ -n "$(find "$slot" -maxdepth 0 -mmin +1 2>/dev/null)" ]
}

held=""
release() { [ -n "$held" ] && rm -rf "$held"; held=""; }

claim() {
  local i slot
  i=1
  while [ "$i" -le "$slots" ]; do
    slot="$dir/slot$i"
    if mkdir "$slot" 2>/dev/null; then
      printf '%s\n%s\n' "$$" "$(stamp $$)" >"$slot/owner"
      held=$slot
      return 0
    fi
    if reclaimable "$slot" && mv "$slot" "$slot.stale.$$" 2>/dev/null; then
      rm -rf "$slot.stale.$$"
      continue
    fi
    i=$((i + 1))
  done
  return 1
}

if $use_slot; then
  mkdir -p "$dir" || { echo "heavy_slot.sh: cannot create $dir" >&2; exit 1; }
  trap release EXIT
  waited=0
  until claim; do
    if [ "$waited" -ge "$wait_limit" ]; then
      echo "heavy_slot.sh: no free slot after ${waited}s (all $slots held); not starting: $*" >&2
      exit 75
    fi
    [ $((waited % 60)) -eq 0 ] && echo "heavy_slot.sh: waiting for a heavy slot ($slots in use machine-wide), ${waited}s so far" >&2
    sleep 2
    waited=$((waited + 2))
  done
fi

if command -v taskpolicy >/dev/null 2>&1; then
  set -- taskpolicy -b "$@"
else
  set -- nice -n 10 "$@"
fi

# The command runs as a child so the slot is released on every exit path; a signal for this script goes to the command too
"$@" &
child=$!
trap 'kill -TERM "$child" 2>/dev/null' TERM INT
wait "$child"
status=$?
if kill -0 "$child" 2>/dev/null; then wait "$child"; status=$?; fi
exit "$status"
