#!/usr/bin/env bash
# Only hosted prepare-experiment; the two expected digests are independent inputs.
set -euo pipefail
umask 077
test "$1" = prepare-experiment || { echo "BLOCKED: installation/production forbidden" >&2; exit 86; }
trap 'echo "BLOCKED: offline preparation/probe failed" >&2; exit 86' ERR
test "$GITHUB_ACTIONS" = true
test "$GITHUB_REPOSITORY" = YeliangDu/forcitis-one-psa-codex-lab
test "$GITHUB_REPOSITORY_ID" = 1449415666
test "$RUNNER_ENVIRONMENT" = github-hosted
context="$2"; lock_sha="$3"; sums_sha="$4"; out="$5"
test "$(dirname "$out")" = "$RUNNER_TEMP"
test ! -e "$out" && test ! -L "$out"
printf '%s  %s\n' "$lock_sha" "$context/input-lock.json" | sha256sum --check -
printf '%s  %s\n' "$sums_sha" "$context/SHA256SUMS" | sha256sum --check -
(cd "$context" && sha256sum --check SHA256SUMS)
base=postgres@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42
# No pull fallback: a new job must first load the independently verified base archive.
docker image inspect "$base" >/dev/null
mkdir -m 700 "$out"
DOCKER_BUILDKIT=1 docker build --pull=false --network none --platform linux/amd64 \
  --iidfile "$out/image-id.txt" "$context" > "$out/build.log" 2>&1
image="$(cat "$out/image-id.txt")"
docker image inspect "$image" > "$out/image-inspect.json"
cid="$(docker create --network none --read-only --security-opt no-new-privileges \
  --cap-drop ALL --cap-add CHOWN --cap-add FOWNER --cap-add DAC_OVERRIDE \
  --cap-add SETUID --cap-add SETGID \
  --tmpfs /run:rw,mode=0755 --tmpfs /tmp:rw,mode=1777 \
  --tmpfs /data/forcitis-one:rw,mode=0755 \
  --tmpfs /etc/forcitis-one:rw,mode=0755 \
  --tmpfs /var/lib/postgresql:rw,mode=0755 "$image")"
cleanup() { docker rm -fv "$cid" >/dev/null; }
trap cleanup EXIT
docker inspect "$cid" > "$out/container-before.json"
python3 - "$out/container-before.json" <<'PY'
import json,sys
c=json.load(open(sys.argv[1]))[0]
h=c["HostConfig"]
expected={"/run","/tmp","/data/forcitis-one","/etc/forcitis-one","/var/lib/postgresql"}
if h["NetworkMode"]!="none" or h["Privileged"] or h.get("Binds") or h.get("PortBindings"):
    raise SystemExit("BLOCKED: unsafe container boundary")
if set(h.get("Tmpfs",{}))!=expected:
    raise SystemExit("BLOCKED: unexpected tmpfs inventory")
if any(m["Type"]!="tmpfs" or m["Destination"] not in expected for m in c.get("Mounts",[])):
    raise SystemExit("BLOCKED: unapproved mount")
PY
docker start -a "$cid" > "$out/probe.log" 2>&1
docker inspect "$cid" > "$out/container-after.json"
test "$(docker inspect -f '{{.State.ExitCode}}' "$cid")" = 0
docker image save --output "$out/experiment-image.tar" "$image"
python3 - "$out" "$lock_sha" "$sums_sha" <<'PY'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1])
rows=[json.loads(x) for x in (root/"probe.log").read_text().splitlines() if x.startswith('{"kind":')]
if len(rows)!=1 or rows[0].get("kind")!="lab54-tools-only/1" or not rows[0].get("tool_probe_completed"):
    raise SystemExit("BLOCKED: actual tool receipt missing")
files={}
for p in root.iterdir():
    if p.is_file():
        with p.open("rb") as s: files[p.name]=hashlib.file_digest(s,"sha256").hexdigest()
receipt=dict(kind="lab54-prepared-tool-image/1",input_lock_sha256=sys.argv[2],
             input_sums_sha256=sys.argv[3],files_sha256=files,probe=rows[0],
             installation_authority=False,application_started=False,
             flyway_executed=False,migration_pass=False,install_pass=False)
(root/"prepared-tools.json").write_text(json.dumps(receipt,indent=2)+"\n")
PY
echo "TOOLS_ONLY: independently pin prepared-tools.json and image archive before S4"
