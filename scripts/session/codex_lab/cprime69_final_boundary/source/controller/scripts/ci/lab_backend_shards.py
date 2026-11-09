"""Partition and reconcile the hosted default PostgreSQL Failsafe lane."""
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
    print(json.dumps(dict(**counts, executed_classes=len(actual), classes=sorted(actual),
                          missing=sorted(missing), extra=sorted(extra)),
                     sort_keys=True))
    return int(bool(not actual or not counts["tests"] or counts["failures"] or counts["errors"] or missing or extra))


def aggregate(inventory, directory, head, run, attempt, legacy):
    plan = json.loads(inventory.read_text())
    shards = plan["shards"]
    expected = [c for shard in shards for c in shard]
    if (len(shards) != 4 or not all(shards) or len(expected) != len(set(expected))
            or len(expected) != plan["integration_classes"]):
        raise ValueError("incomplete or overlapping backend plan")
    slots = {str(i): set(c) for i, c in enumerate(shards, 1)}
    slots["unit"] = None
    if legacy:
        slots["legacy"] = None
    prefix = f"lab-backend-result-{head}-{attempt}-"
    actual_slots = {p.parent.name.removeprefix(prefix) for p in directory.glob(f"{prefix}*/result.json")}
    if actual_slots != set(slots):
        raise ValueError("missing or extra backend artifacts")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for slot, required in slots.items():
        result = json.loads((directory / (prefix + slot) / "result.json").read_text())
        if not (result.get("head") == head and result.get("run") == run
                and result.get("attempt") == attempt and result.get("slot") == slot
                and result.get("missing") == [] and result.get("extra") == []):
            raise ValueError(f"unrelated or incomplete backend evidence for {slot}")
        classes = result.get("classes")
        if (not isinstance(classes, list) or not classes or len(set(classes)) != len(classes)
                or result.get("executed_classes") != len(classes)
                or (required is not None and set(classes) != required)):
            raise ValueError(f"incorrect backend class coverage for {slot}")
        for key in totals:
            count = result.get(key)
            if type(count) is not int or count < 0 or (key == "tests" and count == 0):
                raise ValueError(f"invalid backend counts for {slot}")
            totals[key] += count
    if totals["failures"] or totals["errors"]:
        raise ValueError("backend lanes failed")
    print(json.dumps(dict(head=head, run=run, attempt=attempt, **totals,
                          integration_classes=len(expected), slots=sorted(slots))))


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    plan = sub.add_parser("plan")
    plan.add_argument("root", type=Path)
    plan.add_argument("count", type=int)
    verify = sub.add_parser("verify")
    verify.add_argument("reports", type=Path)
    verify.add_argument("--selector")
    complete = sub.add_parser("aggregate")
    complete.add_argument("inventory", type=Path)
    complete.add_argument("directory", type=Path)
    complete.add_argument("--head", required=True)
    complete.add_argument("--run", required=True)
    complete.add_argument("--attempt", required=True)
    complete.add_argument("--legacy", action="store_true")
    args = p.parse_args()
    if args.cmd == "plan":
        if not 2 <= args.count <= 8:
            p.error("invalid shard count")
        print(json.dumps(plans(args.root, args.count), sort_keys=True))
        return 0
    if args.cmd == "aggregate":
        aggregate(args.inventory, args.directory, args.head, args.run, args.attempt, args.legacy)
        return 0
    return reports(args.reports, args.selector.split(",") if args.selector else None)


if __name__ == "__main__":
    raise SystemExit(main())
