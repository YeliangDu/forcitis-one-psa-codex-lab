#!/usr/bin/env python3
"""Check the exact acceptance-report set for default, legacy-it, or full (nightly profile).
The SQL Server list lives in backend/pom.xml, property legacy.it.acceptance. Nested reports count under their outer class.
Usage: acceptance_reports.py [--tier default|legacy-it|full] [--report-dir <path>] | --legacy-it-tests | --self-test
"""
import argparse
import os
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from ci_git import CiError, toplevel

SOURCES = "backend/src/test/java/"
PACKAGE = "com/forcitis/one/acceptance/"
REPORT_DIRS = ("backend/target/surefire-reports", "backend/target/failsafe-reports")
TEST_CLASS = re.compile(r"(Test.*|.*Tests?|.*TestCase|IT.*|.*IT|.*ITCase)\.java")
REPORT = re.compile(r"TEST-(com\.forcitis\.one\.acceptance\..+)\.xml")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def test_classes(root):
    found = set()
    for directory, _, names in os.walk(os.path.join(root, SOURCES, PACKAGE)):
        for name in names:
            if TEST_CLASS.fullmatch(name):
                relative = os.path.relpath(os.path.join(directory, name), os.path.join(root, SOURCES))
                found.add(relative[:-len(".java")].replace(os.sep, "."))
    return found


def legacy_acceptance_paths(root):
    try:
        pom = ET.parse(Path(root, "backend/pom.xml"))
    except (OSError, ET.ParseError) as error:
        raise CiError(f"cannot read backend/pom.xml: {error}") from error
    value = pom.findtext("m:properties/m:legacy.it.acceptance", namespaces=NS)
    if not value or not value.strip():
        raise CiError("backend/pom.xml must declare legacy.it.acceptance")
    paths = [part.strip() for part in value.split(",")]
    if len(paths) != len(set(paths)):
        raise CiError("legacy.it.acceptance has duplicate entries")
    for path in paths:
        # Exact IT paths only: a directory glob would silently move newly added acceptance classes.
        if not re.fullmatch(PACKAGE + r"(?:\w+/)*\w+IT\.java", path):
            raise CiError(f"legacy.it.acceptance must list exact acceptance *IT.java paths: {path}")
        if not Path(root, SOURCES + path).is_file():
            raise CiError(f"legacy.it.acceptance lists a nonexistent class: {path}")
    return paths


def legacy_classes(root, classes):
    listed = {path[:-5].replace("/", ".") for path in legacy_acceptance_paths(root)}
    return listed | {cls for cls in classes if cls.endswith("MssqlIT")}


def legacy_it_tests(root):
    return ",".join(["**/*MssqlIT.java", *legacy_acceptance_paths(root)])


def reported_classes(root, report_dirs=REPORT_DIRS):
    found = set()
    for report_dir in report_dirs:
        directory = os.path.join(root, report_dir)
        for name in os.listdir(directory) if os.path.isdir(directory) else []:
            match = REPORT.fullmatch(name)
            if match:
                found.add(match.group(1).split("$")[0])
    return found


def problems(root, tier="default", report_dirs=REPORT_DIRS):
    classes, reports = test_classes(root), reported_classes(root, report_dirs)
    legacy = legacy_classes(root, classes)
    expected = {"default": classes - legacy, "legacy-it": legacy, "full": classes}[tier]
    return ([f"{c} has no surefire/failsafe report in tier {tier}: it did not run" for c in sorted(expected - reports)]
            + [f"unexpected report for {c} in tier {tier}: no class in this tier" for c in sorted(reports - expected)]), len(expected), len(reports)


def touch(root, path):
    full = Path(root, path)
    full.parent.mkdir(parents=True, exist_ok=True)
    full.touch()


def fixture_pom(root, paths):
    target = Path(root, "backend/pom.xml")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><properties><legacy.it.acceptance>'
                      + ",".join(paths) + '</legacy.it.acceptance></properties></project>', encoding="utf-8")


def configuration_self_test(root):
    pom = ET.parse(Path(root, "backend/pom.xml"))
    plugins = pom.findall("m:build/m:plugins/m:plugin", NS)
    configs = {p.findtext("m:artifactId", namespaces=NS): p.find("m:configuration", NS) for p in plugins}
    failsafe = configs["maven-failsafe-plugin"]
    assert [e.text for e in failsafe.findall("m:excludes/m:exclude", NS)] == ["${legacy.it.acceptance}", "**/*MssqlIT.java"], "default failsafe must exclude the single SQL Server list and *MssqlIT"
    assert failsafe.findtext("m:reportsDirectory", namespaces=NS) == "${integration.reports}", "PR SQL Server reports must be isolated"
    assert failsafe.findtext("m:summaryFile", namespaces=NS) == "${integration.reports}/failsafe-summary.xml"
    profile = pom.find("m:profiles/m:profile[m:id='legacy-it']", NS)
    override = profile.find("m:build/m:plugins/m:plugin[m:artifactId='maven-failsafe-plugin']/m:configuration/m:excludes", NS)
    assert override is not None and override.attrib.get("combine.self") == "override" and not list(override), "full legacy-it must restore all ITs, including the moved classes"
    assert profile.find("m:build/m:plugins/m:plugin[m:artifactId='maven-failsafe-plugin']/m:configuration/m:includes", NS) is None, "nightly must keep the default ITs too"
    # Discover actual SQL Server users through test helpers and inheritance; the image-name guard itself starts no container.
    sources = {p: p.read_text(encoding="utf-8") for p in Path(root, SOURCES).rglob("*.java")}
    users = {p for p, text in sources.items() if re.search(r"\bTestMssql\.(?:shared|database)\(", text)}
    while True:
        names = {p.stem for p in users}
        added = {p for p, text in sources.items() if re.search(r"\bextends\s+(?:" + "|".join(sorted(names)) + r")\b", text)}
        if added <= users:
            break
        users |= added
    actual = {p.relative_to(Path(root, SOURCES)).as_posix() for p in users if p.as_posix().startswith(Path(root, SOURCES, PACKAGE).as_posix() + "/") and TEST_CLASS.fullmatch(p.name)}
    assert actual == set(legacy_acceptance_paths(root)), f"SQL Server acceptance users differ from legacy.it.acceptance: missing={actual - set(legacy_acceptance_paths(root))}, stale={set(legacy_acceptance_paths(root)) - actual}"
    script = Path(root, "scripts/ci/legacy_it.sh").read_text()
    assert "--legacy-it-tests" in script and '-Dit.test="$tests"' in script and "-Plegacy-it" in script, "PR must select the POM list, not just *MssqlIT"
    assert "--tier legacy-it" in script and "--report-dir" in script
    nightly = Path(root, ".github/workflows/nightly-legacy-it.yml").read_text()
    assert "scripts/ci/backend_verify.sh -Plegacy-it" in nightly and "acceptance_reports.py --tier full" in nightly, "nightly must run and check both tiers"


def self_test():
    with tempfile.TemporaryDirectory() as root:
        source = PACKAGE + "platform/identity/"
        normal = "com.forcitis.one.acceptance.platform.identity.LoginAcceptanceTest"
        moved = "com.forcitis.one.acceptance.platform.identity.SessionAcceptanceIT"
        mssql = "com.forcitis.one.acceptance.platform.identity.FutureMssqlIT"
        fixture_pom(root, [source + "SessionAcceptanceIT.java"])
        touch(root, SOURCES + source + "SessionAcceptanceIT.java")
        touch(root, SOURCES + source + "LoginAcceptanceTest.java")
        touch(root, SOURCES + source + "FutureMssqlIT.java")
        touch(root, SOURCES + source + "Fixtures.java")
        assert len(problems(root)[0]) == 1, "default only requires its own class, not SQL Server classes or helpers"
        unit = "backend/target/surefire-reports/TEST-" + normal + ".xml"
        touch(root, unit)
        touch(root, unit.replace(".xml", "$Nested.xml"))
        assert problems(root) == ([], 1, 1), "moved classes need no report in default; nested classes count once"
        touch(root, "backend/target/surefire-reports/TEST-com.forcitis.one.platform.identity.UnitTest.xml")
        assert problems(root) == ([], 1, 1), "other packages are not acceptance tests"
        moved_report = "backend/target/failsafe-reports/TEST-" + moved + ".xml"
        touch(root, moved_report)
        assert len(problems(root)[0]) == 1 and "unexpected report" in problems(root)[0][0], "default must catch a broken exclusion"
        Path(root, moved_report).unlink()
        isolated = ("backend/target/legacy-it-reports",)
        assert len(problems(root, "legacy-it", isolated)[0]) == 2, "legacy-it missing reports fail even when default reports exist"
        for cls in (moved, mssql):
            touch(root, isolated[0] + "/TEST-" + cls + ".xml")
        assert problems(root, "legacy-it", isolated) == ([], 2, 2), "legacy-it includes acceptance *MssqlIT automatically"
        touch(root, isolated[0] + "/TEST-" + normal + ".xml")
        assert "unexpected report" in problems(root, "legacy-it", isolated)[0][0], "default class in legacy-it is an error"
        touch(root, moved_report)
        assert len(problems(root, "full")[0]) == 1, "full profile cannot omit an acceptance *MssqlIT"
        touch(root, "backend/target/failsafe-reports/TEST-" + mssql + ".xml")
        assert problems(root, "full") == ([], 3, 3), "nightly checks the union of both tiers"
        touch(root, SOURCES + source + "NewAcceptanceIT.java")
        assert "NewAcceptanceIT" in problems(root)[0][0], "new acceptance class forgotten by Maven still fails"
        ghost = source + "GhostAcceptanceIT.java"
        fixture_pom(root, [source + "SessionAcceptanceIT.java", ghost])
        for tier in ("default", "legacy-it", "full"):
            try:
                problems(root, tier)
            except CiError as error:
                assert "nonexistent class" in str(error)
            else:
                raise AssertionError("stale POM list must fail every tier")
        fixture_pom(root, [source + "SessionAcceptanceIT.java"])
        Path(root, SOURCES + source + "NewAcceptanceIT.java").unlink()
        touch(root, "backend/target/surefire-reports/TEST-com.forcitis.one.acceptance.GhostAcceptanceTest.xml")
        assert any("GhostAcceptanceTest" in p for p in problems(root, "full")[0]), "orphan report still fails"
        assert legacy_it_tests(root) == "**/*MssqlIT.java," + source + "SessionAcceptanceIT.java"
    configuration_self_test(Path(__file__).resolve().parents[2])
    print("acceptance_reports self-test: ok (tiers, stale entries, new/orphan classes, POM selection, SQL Server discovery, nightly)")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--legacy-it-tests", action="store_true")
    parser.add_argument("--tier", choices=("default", "legacy-it", "full"), default="default")
    parser.add_argument("--report-dir", action="append")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    try:
        root = toplevel()
        if args.legacy_it_tests:
            print(legacy_it_tests(root))
            return 0
        found, classes, reports = problems(root, args.tier, args.report_dir or REPORT_DIRS)
    except CiError as error:
        print(f"::error title=Acceptance reports::{error}")
        return 1
    for message in found:
        print(f"::error title=Acceptance reports::{message}")
    print(f"acceptance_reports ({args.tier}): {classes} test class(es), {reports} report(s), {len(found)} problem(s)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
