#!/usr/bin/env bash
# Hosted authenticated package collection only; no application/install execution.
set -euo pipefail
umask 077
trap 'echo "BLOCKED: asset collection failed" >&2; exit 86' ERR
test "$GITHUB_ACTIONS" = true
test "$GITHUB_REPOSITORY" = YeliangDu/forcitis-one-psa-codex-lab
test "$GITHUB_REPOSITORY_ID" = 1449415666
test "$RUNNER_ENVIRONMENT" = github-hosted
test "$RUNNER_OS" = Linux
test "$(uname -m)" = x86_64
out="$1"
test "$(dirname "$out")" = "$RUNNER_TEMP"
test ! -e "$out" && test ! -L "$out"
here="$(cd "$(dirname "$0")" && pwd -P)"
image=postgres@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42
jdk=OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz
jdk_sha=dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e
mkdir -m 700 "$out"
cp "$here/Dockerfile" "$here/entrypoint.sh" "$out/"
mkdir "$out/payload"
docker pull --platform linux/amd64 "$image" > "$out/payload/base-pull.log"
docker image inspect "$image" > "$out/payload/base-inspect.json"
cid=""
cleanup() { if [ -n "$cid" ]; then docker rm -fv "$cid" >/dev/null; fi; }
trap cleanup EXIT
cid="$(docker create --platform linux/amd64 --network bridge --entrypoint /bin/bash "$image" -ceu '
export DEBIAN_FRONTEND=noninteractive
mkdir -p /lab54/debs /lab54/lists /lab54/keyrings
apt-get -o Acquire::AllowInsecureRepositories=false \
  -o Acquire::AllowDowngradeToInsecureRepositories=false \
  -o APT::Get::AllowUnauthenticated=false update
set --
for name in nginx curl openssh-client acl python3 iproute2 unzip ca-certificates util-linux passwd; do
  version="$(apt-cache policy "$name" | awk "/Candidate:/ {print \$2}")"
  test -n "$version" && test "$version" != "(none)"
  set -- "$@" "$name=$version"
done
printf "%s\n" "$@" > /lab54/targets.txt
apt-cache policy nginx curl openssh-client acl python3 iproute2 unzip ca-certificates util-linux passwd > /lab54/apt-policy.txt
apt-get -o APT::Get::AllowUnauthenticated=false -y --no-install-recommends --download-only install "$@"
cp /var/cache/apt/archives/*.deb /lab54/debs/
cp -a /var/lib/apt/lists/. /lab54/lists/
cp -a /etc/apt /lab54/apt-config
cp -a /usr/share/keyrings/. /lab54/keyrings/
dpkg-query -W > /lab54/base-packages.tsv
: > /lab54/debs.tsv
for file in /lab54/debs/*.deb; do
  printf "%s\t" "$(basename "$file")" >> /lab54/debs.tsv
  dpkg-deb -f "$file" Package Version Architecture | paste -sd "\t" >> /lab54/debs.tsv
done
')"
docker start -a "$cid" > "$out/payload/collection.log" 2>&1
test "$(docker inspect -f '{{.State.ExitCode}}' "$cid")" = 0
docker cp "$cid:/lab54/." "$out/payload/"
docker rm -v "$cid" >/dev/null
cid=""
curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' \
  --output "$out/payload/$jdk" \
  "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/$jdk"
printf '%s  %s\n' "$jdk_sha" "$out/payload/$jdk" | sha256sum --check -
python3 - "$out" "$image" "$jdk" "$jdk_sha" <<'PY'
from pathlib import Path
import hashlib,json,os,sys
root=Path(sys.argv[1])
# Record every copied link without resolving or reading its target.
# source_root identifies the declared copy origin, not an authorized target.
origins={"payload/lists":"/var/lib/apt/lists",
         "payload/apt-config":"/etc/apt",
         "payload/keyrings":"/usr/share/keyrings"}
links=[]
for p in sorted(root.rglob("*")):
    if not p.is_symlink():
        continue
    relative=str(p.relative_to(root))
    origin=next((source for prefix,source in origins.items()
                 if relative.startswith(prefix+"/")), None)
    links.append(dict(path=relative,target=os.readlink(p),source_root=origin,
                      disposition="BLOCKED_PENDING_SOURCE_REVIEW"))
if links:
    diagnostic=dict(kind="lab54-collection-links/1",links=links,
                    materialized=False,input_lock_created=False,
                    installation_authority=False)
    (root/"collection-diagnostics.json").write_text(json.dumps(diagnostic,indent=2)+"\n")
    print(json.dumps(diagnostic),file=sys.stderr,flush=True)
    raise SystemExit("BLOCKED: source links require independent review; no input-lock produced")
files={}
for p in sorted(root.rglob("*")):
    if p.is_symlink():
        raise SystemExit("BLOCKED: symlink appeared after diagnostic inventory")
    if p.is_file():
        with p.open("rb") as stream:
            files[str(p.relative_to(root))]=hashlib.file_digest(stream,"sha256").hexdigest()
image=json.loads((root/"payload/base-inspect.json").read_text())[0]
if image["Os"]!="linux" or image["Architecture"]!="amd64":
    raise SystemExit("BLOCKED: wrong base platform")
lock=dict(format="lab54-offline-input/1",base=sys.argv[2],platform="linux/amd64",
          jdk_asset=sys.argv[3],jdk_sha256=sys.argv[4],files_sha256=files,
          workflow_sha=os.environ["GITHUB_SHA"],run=os.environ["GITHUB_RUN_ID"],
          attempt=os.environ["GITHUB_RUN_ATTEMPT"],scope="APT authenticated download only",
          installation_authority=False,migration_pass=False,install_pass=False)
(root/"input-lock.json").write_text(json.dumps(lock,indent=2)+"\n")
files["input-lock.json"]=hashlib.sha256((root/"input-lock.json").read_bytes()).hexdigest()
(root/"SHA256SUMS").write_text("".join(v+"  "+k+"\n" for k,v in files.items()))
PY
echo "COLLECTED_NOT_BUILT: independently pin input-lock.json and SHA256SUMS"
