"""Hosted pilot: partition and reconcile the default PostgreSQL Failsafe lane."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


TEST_ROOT = Path("backend/src/test/java")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
CLASS_NAME = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")


def plans(root, shard_count):
    pom = ET.parse(root / "backend/pom.xml")
    legacy = pom.findtext("m:properties/m:legacy.it.acceptance", namespaces=NS)
    if not legacy:
        raise ValueError("missing checked-in legacy excludes")
    ignored = {x.strip() for x in legacy.split(",") if x.strip()}
    if len(ignored) != len([x for x in legacy.split(",") if x.strip()]):
        raise ValueError("duplicate legacy excludes")
    plugin = [p for p in pom.findall("m:build/m:plugins/m:plugin", NS)
              if p.findtext("m:artifactId", namespaces=NS) == "maven-failsafe-plugin"]
    if len(plugin) != 1 or plugin[0].find("m:configuration/m:includes", NS) is not None:
        raise ValueError("Failsafe no longer uses the audited default includes")
    excludes = [x.text for x in plugin[0].findall("m:configuration/m:excludes/m:exclude", NS)]
    if excludes != ["${legacy.it.acceptance}", "**/*MssqlIT.java", "**/*$*"]:
        raise ValueError("Failsafe excludes have changed")
    files = subprocess.check_output(["git", "-C", str(root), "ls-files", "-z", str(TEST_ROOT)]).decode().split("\0")
    eligible, abstract, excluded = [], [], []
    for entry in sorted(filter(None, files)):
        path = Path(entry).relative_to(TEST_ROOT)
        if path.suffix != ".java" or not (path.stem.startswith("IT") or path.stem.endswith(("IT", "ITCase"))):
            continue
        if path.as_posix() in ignored or path.stem.endswith("MssqlIT") or "$" in path.stem:
            excluded.append(path.as_posix())
            continue
        name = path.stem
        if not CLASS_NAME.fullmatch(name) or not all(CLASS_NAME.fullmatch(part) for part in path.parts[:-1]):
            raise ValueError(f"unexpected test path: {path}")
        text = (root / entry).read_text()
        package = ".".join(path.parts[:-1])
        if not re.search(r"(?m)^package " + re.escape(package) + r"\s*;", text):
            raise ValueError(f"unexpected declared package: {path}")
        matches = re.findall(r"(?m)^[ \t]*((?:(?:public|final|abstract|sealed|non-sealed)\s+)*)class\s+" +
                             re.escape(name) + r"\b", text)
        if len(matches) != 1:
            raise ValueError(f"unclassified Failsafe candidate: {path}")
        fqcn = package + "." + name
        if "abstract" in matches[0].split():
            abstract.append(fqcn)
        else:
            eligible.append(fqcn)
    if not ignored.issubset(excluded) or len(eligible) < shard_count or len(set(eligible)) != len(eligible):
        raise ValueError("incomplete integration inventory")
    shards = [eligible[i::shard_count] for i in range(shard_count)]
    return dict(shards=shards, abstract=abstract, configured_excludes=excluded,
                integration_classes=len(eligible))


def reports(directory, expected):
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    actual = set()
    for path in sorted(directory.glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite":
            raise ValueError(f"unexpected report format: {path.name}")
        actual.add(suite.attrib["name"].split("$", 1)[0])
        for field in counts:
            counts[field] += int(suite.attrib[field])
    missing, extra = set(expected or ()) - actual, actual - set(expected or ()) if expected else set()
    print(json.dumps(dict(**counts, executed_classes=len(actual), missing=sorted(missing), extra=sorted(extra)),
                     sort_keys=True))
    return int(bool(not actual or not counts["tests"] or counts["failures"] or counts["errors"] or missing or extra))


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    plan = sub.add_parser("plan")
    plan.add_argument("root", type=Path)
    plan.add_argument("count", type=int)
    verify = sub.add_parser("verify")
    verify.add_argument("reports", type=Path)
    verify.add_argument("--selector")
    args = p.parse_args()
    if args.cmd == "plan":
        if not 2 <= args.count <= 8:
            p.error("invalid shard count")
        print(json.dumps(plans(args.root, args.count), sort_keys=True))
        return 0
    return reports(args.reports, args.selector.split(",") if args.selector else None)


if __name__ == "__main__":
    raise SystemExit(main())
