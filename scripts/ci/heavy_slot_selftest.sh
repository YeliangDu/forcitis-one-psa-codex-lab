#!/usr/bin/env bash
# Self-test for heavy_slot.sh: exit code, slot limit, stale reclaim, release, priority wrapper. No timing assumptions beyond bounded polling.
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
tool="$here/heavy_slot.sh"
work=$(mktemp -d "${TMPDIR:-/tmp}/heavy_slot_selftest.XXXXXX")
holders=""
cleanup() { for p in $holders; do kill "$p" 2>/dev/null; done; rm -rf "$work"; }
trap cleanup EXIT

export FORCITIS_HEAVY_DIR="$work/slots" FORCITIS_HEAVY_SLOTS=2 FORCITIS_HEAVY_WAIT_SECONDS=2
failed=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1: expected '$3', got '$2'"; failed=$((failed + 1)); fi; }

# poll until a file exists, at most ~20 s
await() { local n=0; while [ ! -e "$1" ] && [ "$n" -lt 200 ]; do sleep 0.1; n=$((n + 1)); done; [ -e "$1" ]; }

"$tool" -- sh -c 'exit 7'; check "command exit code is passed through" "$?" 7

"$tool" -- true; check "slot is released after the command" "$(ls "$work/slots")" ""

hold() {
  "$tool" -- sh -c "touch '$work/ready$1'; while [ ! -e '$work/release' ]; do sleep 0.1; done" &
  holders="$holders $!"
  await "$work/ready$1"
}
hold 1; hold 2
"$tool" -- true 2>/dev/null; check "third caller gets no slot while two are held (exit 75)" "$?" 75
touch "$work/release"; wait
holders=""
check "slots are free after the holders end" "$(ls "$work/slots")" ""

rm -f "$work/release"
mkdir -p "$work/slots/slot1" "$work/slots/slot2"
printf '999999\nMon Jan  1 00:00:00 2001\n' >"$work/slots/slot1/owner"
printf '999998\nMon Jan  1 00:00:00 2001\n' >"$work/slots/slot2/owner"
"$tool" -- true; check "slots of dead owners are reclaimed" "$?" 0

mkdir -p "$work/slots/slot1" "$work/slots/slot2"
touch -t 200101010000 "$work/slots/slot1" "$work/slots/slot2"
"$tool" -- true; check "claims that never got an owner file are reclaimed once old" "$?" 0

rm -rf "$work/slots"; mkdir -p "$work/slots"
"$tool" --no-slot -- true; check "--no-slot runs without a slot" "$?" 0
check "--no-slot leaves no slot behind" "$(ls "$work/slots")" ""

mkdir -p "$work/bin"
printf '#!/bin/sh\necho "$@" >"%s/policy"\nexec "$@"\n' "$work" >"$work/bin/taskpolicy"
sed -i.bak 's/^exec "\$@"$/shift; exec "$@"/' "$work/bin/taskpolicy"; rm -f "$work/bin/taskpolicy.bak"
chmod +x "$work/bin/taskpolicy"
PATH="$work/bin:$PATH" "$tool" -- sh -c 'exit 0'
check "command runs under taskpolicy -b" "$(cut -d' ' -f1 "$work/policy" 2>/dev/null)" "-b"

if [ "$failed" -ne 0 ]; then echo "heavy_slot self-test: $failed FAIL"; exit 1; fi
echo "heavy_slot self-test: all PASS"
