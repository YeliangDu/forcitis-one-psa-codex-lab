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
apt-get -o Debug::Acquire::gpgv=true -o Debug::pkgAcquire::Auth=true -o Acquire::AllowInsecureRepositories=false \
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
# Parse copied enabled APT declarations; do not infer signer consumption from filenames.
import re,shlex,stat
rings=root/"payload/keyrings"
known={
 "debian-archive-bookworm-automatic.gpg":"debian-archive-bookworm-automatic.pgp",
 "debian-archive-bookworm-security-automatic.gpg":"debian-archive-bookworm-security-automatic.pgp",
 "debian-archive-bookworm-stable.gpg":"debian-archive-bookworm-stable.pgp",
 "debian-archive-bullseye-automatic.gpg":"debian-archive-bullseye-automatic.pgp",
 "debian-archive-bullseye-security-automatic.gpg":"debian-archive-bullseye-security-automatic.pgp",
 "debian-archive-bullseye-stable.gpg":"debian-archive-bullseye-stable.pgp",
 "debian-archive-keyring.gpg":"debian-archive-keyring.pgp",
 "debian-archive-removed-keys.gpg":"debian-archive-removed-keys.pgp",
 "debian-archive-trixie-automatic.gpg":"debian-archive-trixie-automatic.pgp",
 "debian-archive-trixie-security-automatic.gpg":"debian-archive-trixie-security-automatic.pgp",
 "debian-archive-trixie-stable.gpg":"debian-archive-trixie-stable.pgp",
}
def regular_bytes(path,limit=16*1024*1024):
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    try:
        before=os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_nlink!=1 or before.st_size>limit:
            raise SystemExit("BLOCKED: unsafe regular asset "+str(path.relative_to(root)))
        with os.fdopen(os.dup(fd),"rb") as stream:
            raw=stream.read(limit+1)
        after=os.fstat(fd)
        identity=lambda s:(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_ctime_ns)
        if len(raw)!=before.st_size or identity(after)!=identity(before):
            raise SystemExit("BLOCKED: moving/oversize asset")
        return raw
    finally:
        os.close(fd)
aliases=[]
for p in sorted(root.rglob("*")):
    if p.is_symlink():
        target=os.readlink(p)
        if p.parent!=rings or known.get(p.name)!=target:
            print(json.dumps(dict(path=str(p.relative_to(root)),target=target)),file=sys.stderr)
            raise SystemExit("BLOCKED: unknown/out-of-root alias")
        aliases.append((p,target))
config=root/"payload/apt-config"
source_files=([config/"sources.list"] if (config/"sources.list").exists() else [])
source_files+=sorted((config/"sources.list.d").glob("*.sources"))
source_files+=sorted((config/"sources.list.d").glob("*.list"))
consumed=set()
sources=[]
for source in source_files:
    raw=regular_bytes(source,1024*1024)
    text=raw.decode("utf-8")
    sources.append(dict(path=str(source.relative_to(root)),sha256=hashlib.sha256(raw).hexdigest()))
    if source.suffix==".sources":
        for paragraph in re.split(r"\n\s*\n",text):
            fields={};last=None
            for line in paragraph.splitlines():
                if not line.strip() or line.lstrip().startswith("#"):
                    continue
                if line[0].isspace():
                    if last is None: raise SystemExit("BLOCKED: malformed deb822 continuation")
                    fields[last]+=" "+line.strip()
                else:
                    key,sep,value=line.partition(":")
                    key=key.lower()
                    if not sep or key in fields: raise SystemExit("BLOCKED: malformed/duplicate deb822 field")
                    last=key;fields[key]=value.strip()
            if not fields or fields.get("enabled","yes").lower()=="no":
                continue
            if any(fields.get(k,"no").lower()!="no" for k in ("trusted","allow-insecure","allow-weak","allow-downgrade-to-insecure")):
                raise SystemExit("BLOCKED: source bypasses APT authentication")
            if fields.get("enabled","yes").lower()!="yes" or not fields.get("signed-by"):
                raise SystemExit("BLOCKED: unsupported implicit APT signer")
            consumed.update(shlex.split(fields["signed-by"]))
    else:
        for line in text.splitlines():
            line=line.split("#",1)[0].strip()
            if not line: continue
            match=re.fullmatch(r"deb(?:-src)?\s+\[([^\]]+)\]\s+\S+\s+\S+(?:\s+.*)?",line)
            if not match: raise SystemExit("BLOCKED: unsupported legacy APT source")
            tokens=shlex.split(match[1])
            if any(v.split("=",1)[0] in ("trusted","allow-insecure","allow-weak","allow-downgrade-to-insecure")
                   and not v.endswith("=no") for v in tokens):
                raise SystemExit("BLOCKED: source bypasses APT authentication")
            options=[v.split("=",1)[1] for v in tokens if v.startswith("signed-by=")]
            if len(options)!=1: raise SystemExit("BLOCKED: missing/ambiguous Signed-By")
            consumed.update(options[0].split(","))
print(json.dumps(dict(kind="lab54-apt-signer-input/1",sources=sources,
                      signed_by=sorted(consumed))),file=sys.stderr,flush=True)
if not consumed:
    raise SystemExit("BLOCKED: no explicit enabled APT signer")
names=set()
for value in consumed:
    name=Path(value).name
    if value!="/usr/share/keyrings/"+name:
        raise SystemExit("BLOCKED: signer outside reviewed keyring root: "+value)
    names.add(name)
records=[]
for p,target in aliases:
    raw=regular_bytes(rings/target)  # O_NOFOLLOW rejects a second link or special target.
    used=p.name in names
    records.append(dict(path=str(p.relative_to(root)),target=target,
                        target_sha256=hashlib.sha256(raw).hexdigest(),
                        disposition="materialized" if used else "excluded-unused-known-alias"))
    if os.readlink(p)!=target: raise SystemExit("BLOCKED: alias changed")
    if used:
        temporary=rings/(p.name+".lab54-materializing")
        fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,"wb") as stream:
            stream.write(raw);stream.flush();os.fsync(stream.fileno())
        p.unlink()
        os.link(temporary,p,follow_symlinks=False)  # Never overwrite a newly appeared name.
        temporary.unlink()
    else:
        p.unlink()
for p in sorted(rings.iterdir()):
    if p.name not in names:
        if not stat.S_ISREG(p.lstat().st_mode):
            raise SystemExit("BLOCKED: unexpected keyring object")
        records.append(dict(path=str(p.relative_to(root)),disposition="excluded-unused-regular"))
        p.unlink()
selected=[]
for name in sorted(names):
    p=rings/name
    raw=regular_bytes(p)
    selected.append(dict(source="/usr/share/keyrings/"+name,
                         output=str(p.relative_to(root)),sha256=hashlib.sha256(raw).hexdigest()))
(root/"keyring-provenance.json").write_text(json.dumps(
    dict(sources=sources,selected=selected,aliases=records,installation_authority=False),indent=2)+"\n")
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
