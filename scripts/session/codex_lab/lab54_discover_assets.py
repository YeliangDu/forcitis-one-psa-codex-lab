#!/usr/bin/env python3
"""Hosted asset discovery only; never certifies installation or migration."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys

REPO = "YeliangDu/forcitis-one-psa-codex-lab"
JDK_ASSET = "OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"
JDK_SHA = "dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e"
JDK_URL = ("https://github.com/adoptium/temurin25-binaries/releases/download/"
           "jdk-25.0.4.1%2B1/" + JDK_ASSET)
PROBE = r"""
set -eu
cat /etc/os-release
printf '\n=== packages ===\n'
if command -v dpkg-query >/dev/null 2>&1; then
  dpkg-query -W
  dpkg --print-architecture
else
  printf 'MISSING dpkg-query\n'
fi
printf '\n=== tool inventory ===\n'
for name in postgres psql initdb pg_ctl nginx curl openssl ssh-keygen bash \
    sha256sum flock timeout tar unzip setfacl getfacl python3 runuser \
    useradd chown java ip; do
  if command -v "$name" >/dev/null 2>&1; then
    printf 'FOUND %s %s\n' "$name" "$(command -v "$name")"
  else
    printf 'MISSING %s\n' "$name"
  fi
done
for name in postgres psql initdb pg_ctl bash sha256sum flock timeout tar; do
  if command -v "$name" >/dev/null 2>&1; then "$name" --version; fi
done
if command -v openssl >/dev/null 2>&1; then openssl version; fi
if command -v nginx >/dev/null 2>&1; then nginx -v 2>&1; fi
if command -v curl >/dev/null 2>&1; then curl --version; fi
if command -v python3 >/dev/null 2>&1; then python3 --version; fi
printf '\n=== existing users ===\n'
for name in postgres forcitis forcitis-migrate nginx; do
  id "$name" 2>/dev/null || printf 'MISSING_USER %s\n' "$name"
done
printf '\n=== network observation ===\n'
cat /proc/net/dev
cat /proc/net/route
cat /proc/net/ipv6_route
"""


def need(ok, reason):
    if not ok:
        raise ValueError(reason)


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def run(args, timeout):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, timeout=timeout).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    need(os.environ.get("GITHUB_ACTIONS") == "true"
         and os.environ.get("GITHUB_REPOSITORY") == REPO
         and os.environ.get("GITHUB_REPOSITORY_ID") == "1449415666"
         and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
         and os.environ.get("RUNNER_OS") == "Linux"
         and platform.machine() == "x86_64", "hosted Linux x86_64 Lab required")
    need(re.fullmatch(r"[0-9a-f]{40}", os.environ.get("GITHUB_SHA", "")),
         "exact workflow SHA required")
    parent = Path(os.environ["RUNNER_TEMP"]).resolve(strict=True)
    out = Path(args.out)
    need(out.is_absolute() and out.parent == parent and out.name not in ("", ".", "..")
         and not out.exists() and not out.is_symlink(), "new direct RUNNER_TEMP child required")
    out.mkdir(mode=0o700)
    (out / "pull.log").write_bytes(
        run(["docker", "pull", "--platform", "linux/amd64", "postgres:18"], 600))
    raw = run(["docker", "image", "inspect", "postgres:18"], 30)
    (out / "image-inspect.json").write_bytes(raw)
    rows = json.loads(raw)
    need(isinstance(rows, list) and len(rows) == 1, "ambiguous image inspection")
    image = rows[0]
    identity, digests = image.get("Id", ""), image.get("RepoDigests")
    need(re.fullmatch(r"sha256:[0-9a-f]{64}", identity)
         and image.get("Os") == "linux" and image.get("Architecture") == "amd64",
         "wrong image identity/platform")
    need(isinstance(digests, list) and digests and all(
        isinstance(x, str) and re.fullmatch(r".+@sha256:[0-9a-f]{64}", x)
        for x in digests), "actual RepoDigest required")
    argv = ["docker", "run", "--rm", "--network", "none", "--read-only",
            "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
            "--entrypoint", "/bin/sh", identity, "-ceu", PROBE]
    (out / "probe-argv.json").write_text(json.dumps(argv, indent=2) + "\n")
    (out / "inventory.log").write_bytes(run(argv, 120))
    run(["docker", "image", "save", "--output", str(out / "base-image.tar"), identity], 600)
    archive = out / JDK_ASSET
    run(["curl", "--fail", "--location", "--silent", "--show-error",
         "--proto", "=https", "--proto-redir", "=https", "--tlsv1.2",
         "--output", str(archive), JDK_URL], 600)
    need(sha(archive) == JDK_SHA, "existing JDK archive pin mismatch")
    receipt = {
        "kind": "lab54-asset-discovery/1", "status": "DISCOVERED_NOT_PREPARED",
        "repository": REPO, "repository_id": 1449415666,
        "source": os.environ["GITHUB_SHA"],
        "run": os.environ["GITHUB_RUN_ID"], "attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "candidate_tag": "postgres:18", "platform": "linux/amd64",
        "image_id": identity, "repo_digests": digests,
        "rootfs_diff_ids": image.get("RootFS", {}).get("Layers", []),
        "jdk": {"release": "jdk-25.0.4.1+1", "asset": JDK_ASSET, "sha256": JDK_SHA},
        "files_sha256": {p.name: sha(p) for p in sorted(out.iterdir()) if p.is_file()},
        "scope": "base inventory and pinned JDK download; no PG server, Java or installer execution",
        "capability_ready": False, "installation_authority": False,
        "migration_pass": False, "install_pass": False
    }
    (out / "discovery.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print("DISCOVERED_NOT_PREPARED; archive receipt/assets; S3b remains HOLD")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        raise SystemExit(86)
