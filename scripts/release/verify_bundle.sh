#!/usr/bin/env bash
# Verifies a release archive the way the deployment does: archive sha256 against its .sha256 file, a single top directory named
# after the archive, SHA256SUMS and manifest.json against every file, and the jar is runnable.
#
#   scripts/release/verify_bundle.sh <dir>/forcitis-one-<version>.tar.gz     (expects <archive>.sha256 next to it)
set -euo pipefail

archive="${1:?usage: verify_bundle.sh <forcitis-one-version.tar.gz>}"
repo="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/release/lib.sh
. "$repo/scripts/release/lib.sh"

fail() { echo "verify_bundle.sh: $*" >&2; exit 1; }
[ -f "$archive" ] || fail "$archive not found"
[ -f "$archive.sha256" ] || fail "$archive.sha256 not found"
name="$(basename "$archive" .tar.gz)"

(cd "$(dirname "$archive")" && release_sha256_check "$(basename "$archive").sha256") || fail "archive sha256 mismatch"

work="$(mktemp -d "${TMPDIR:-/tmp}/forcitis-one-verify.XXXXXX")"
trap 'rm -rf "$work"' EXIT
# Same entry rules as the installer on the server (remote_install.sh check_tar_entries)
tar -tzf "$archive" | while IFS= read -r entry; do
  case "$entry" in "$name"|"$name/"|"$name"/*) ;; *) fail "entry outside $name/: $entry" ;; esac
  case "/$entry/" in */../*) fail "entry with ..: $entry" ;; esac
done
if tar -tvzf "$archive" | grep -Eq '^[lhbcp]'; then
  fail "bundle contains links or special files"
fi
tar -xzf "$archive" -C "$work"
bundle="$work/$name"

(cd "$bundle" && release_sha256_check SHA256SUMS) || fail "SHA256SUMS mismatch"
python3 -I "$repo/scripts/release/manifest.py" verify "$bundle"
release_check_jar_runnable "$bundle/forcitis-one.jar" "$work/jar-check" || fail "jar is not runnable"
for required in forcitis-one.jar manifest.json SHA256SUMS migrations.txt deploy/remote_install.sh deploy/forcitis-one.service \
                deploy/nginx-one.conf deploy/forcitis-one.logrotate deploy/one.env.template \
                deploy/forcitis-one-release-pull.service deploy/forcitis-one-release-pull.timer; do
  [ -s "$bundle/$required" ] || fail "$required missing or empty"
done
envs="$(python3 -I -c 'import json,sys; print(" ".join(json.load(open(sys.argv[1]))["environments"]))' "$bundle/manifest.json")"
for env in $envs; do
  [ -s "$bundle/web-$env/index.html" ] || fail "web-$env/index.html missing"
  [ -s "$bundle/web-$env/h5/index.html" ] || fail "web-$env/h5/index.html missing"
done
echo "verify_bundle.sh: $archive verified ($(wc -l < "$bundle/SHA256SUMS" | tr -d ' ') files, environments: $envs)"
