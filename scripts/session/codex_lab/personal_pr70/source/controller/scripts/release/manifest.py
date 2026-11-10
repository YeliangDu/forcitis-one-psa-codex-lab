#!/usr/bin/env python3
"""Writes and verifies manifest.json and SHA256SUMS of a release bundle directory.

  manifest.py write  <bundle-dir> --version ... --git-sha ... (see --help)
  manifest.py verify <bundle-dir>   # manifest, SHA256SUMS and the files on disk agree; exit 1 otherwise
  manifest.py --self-test
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import sys
import tempfile

MANIFEST = "manifest.json"
SUMS = "SHA256SUMS"
SCHEMA = 1


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def bundle_files(bundle):
    found = []
    for root, dirs, files in os.walk(bundle):
        dirs.sort()
        for name in sorted(files):
            relative = os.path.relpath(os.path.join(root, name), bundle).replace(os.sep, "/")
            if relative not in (MANIFEST, SUMS):
                found.append(relative)
    return sorted(found)


def write(args):
    bundle = args.bundle
    files = [{"path": p, "sha256": sha256(os.path.join(bundle, p)), "size": os.path.getsize(os.path.join(bundle, p))}
             for p in bundle_files(bundle)]
    with open(os.path.join(bundle, "migrations.txt"), encoding="utf-8") as handle:
        migrations = [line.split("  ", 1)[1].strip() for line in handle if line.strip()]
    manifest = {
        "schema": SCHEMA,
        "name": "forcitis-one",
        "version": args.version,
        "gitSha": args.git_sha,
        "gitCommitTime": datetime.datetime.fromtimestamp(int(args.commit_epoch), datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "builtAt": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "toolchain": {"jdk": args.jdk, "node": args.node, "pnpm": args.pnpm},
        "environments": args.environments.split(","),
        "frontendBuildVariables": args.frontend_variables.split(),
        "migrationCount": len(migrations),
        "latestMigration": max(migrations, key=os.path.basename, default=None),
        "files": files,
    }
    with open(os.path.join(bundle, MANIFEST), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    with open(os.path.join(bundle, SUMS), "w", encoding="utf-8") as handle:
        for path in sorted([MANIFEST] + [f["path"] for f in files]):
            handle.write(f"{sha256(os.path.join(bundle, path))}  {path}\n")


def verify(bundle):
    errors = []
    try:
        with open(os.path.join(bundle, MANIFEST), encoding="utf-8") as handle:
            manifest = json.load(handle)
    except (OSError, ValueError) as error:
        return [f"{MANIFEST} unreadable: {error}"]
    sums = {}
    try:
        with open(os.path.join(bundle, SUMS), encoding="utf-8") as handle:
            for line in handle:
                match = re.fullmatch(r"([0-9a-f]{64})  (.+)\n?", line)
                if not match:
                    errors.append(f"{SUMS}: malformed line {line!r}")
                    continue
                sums[match.group(2)] = match.group(1)
    except OSError as error:
        return [f"{SUMS} unreadable: {error}"]
    on_disk = set(bundle_files(bundle))
    listed = {f["path"]: f["sha256"] for f in manifest.get("files", [])}
    if set(listed) != on_disk:
        errors.append(f"manifest files differ from bundle: missing {sorted(on_disk - set(listed))}, extra {sorted(set(listed) - on_disk)}")
    if set(sums) != on_disk | {MANIFEST}:
        errors.append(f"{SUMS} entries differ from bundle: missing {sorted((on_disk | {MANIFEST}) - set(sums))}, extra {sorted(set(sums) - on_disk - {MANIFEST})}")
    for path in sorted(on_disk & set(listed)):
        actual = sha256(os.path.join(bundle, path))
        if actual != listed[path]:
            errors.append(f"{path}: manifest sha256 {listed[path]} != file {actual}")
        if sums.get(path) not in (None, actual):
            errors.append(f"{path}: {SUMS} sha256 {sums[path]} != file {actual}")
    if MANIFEST in sums and sums[MANIFEST] != sha256(os.path.join(bundle, MANIFEST)):
        errors.append(f"{MANIFEST}: {SUMS} sha256 does not match")
    for key in ("version", "gitSha", "builtAt"):
        if not manifest.get(key):
            errors.append(f"manifest has no {key}")
    return errors


def self_test():
    with tempfile.TemporaryDirectory() as bundle:
        os.makedirs(os.path.join(bundle, "web-dev"))
        for path, text in (("forcitis-one.jar", "jar"), ("web-dev/index.html", "<html>"),
                           ("migrations.txt", "0" * 64 + "  db/migration/a/V1__a.sql\n")):
            with open(os.path.join(bundle, path), "w", encoding="utf-8") as handle:
                handle.write(text)
        args = argparse.Namespace(bundle=bundle, version="0.1.0", git_sha="a" * 40, commit_epoch="0", jdk="25",
                                  node="v24", pnpm="12", environments="dev", frontend_variables="VITE_X")
        write(args)
        assert verify(bundle) == [], verify(bundle)
        with open(os.path.join(bundle, "web-dev/index.html"), "a", encoding="utf-8") as handle:
            handle.write("tampered")
        assert any("web-dev/index.html" in e for e in verify(bundle)), "tampered file not detected"
        os.remove(os.path.join(bundle, "web-dev/index.html"))
        assert any("missing" in e or "extra" in e for e in verify(bundle)), "removed file not detected"
        with open(os.path.join(bundle, "extra.txt"), "w", encoding="utf-8") as handle:
            handle.write("x")
        assert any("extra.txt" in e for e in verify(bundle)), "added file not detected"
    print("manifest.py self-test passed")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return 0
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    w = sub.add_parser("write")
    w.add_argument("bundle")
    for name in ("--version", "--git-sha", "--commit-epoch", "--jdk", "--node", "--pnpm", "--environments", "--frontend-variables"):
        w.add_argument(name, required=True)
    v = sub.add_parser("verify")
    v.add_argument("bundle")
    args = parser.parse_args()
    if args.command == "write":
        write(args)
        return 0
    errors = verify(args.bundle)
    for error in errors:
        print(f"manifest.py: {error}", file=sys.stderr)
    if not errors:
        print(f"manifest.py: {args.bundle} verified")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
