#!/usr/bin/env python3
"""Fail when an acceptance test class has no surefire or failsafe report, or a report has no class (a test that silently did not run
counts as a pass otherwise). Test classes are the files under backend/src/test/java/com/forcitis/one/acceptance/ whose names match
the surefire (Test*, *Test, *Tests, *TestCase) or failsafe (IT*, *IT, *ITCase) defaults; reports are TEST-com.forcitis.one.acceptance.*.xml
in backend/target/{surefire,failsafe}-reports, nested classes counted under their outer class.
Usage: acceptance_reports.py  |  acceptance_reports.py --self-test"""
import os
import re
import sys
import tempfile

from ci_git import CiError, toplevel

SOURCES = "backend/src/test/java/"
PACKAGE = "com/forcitis/one/acceptance/"
REPORT_DIRS = ("backend/target/surefire-reports", "backend/target/failsafe-reports")
TEST_CLASS = re.compile(r"(Test.*|.*Tests?|.*TestCase|IT.*|.*IT|.*ITCase)\.java")
REPORT = re.compile(r"TEST-(com\.forcitis\.one\.acceptance\..+)\.xml")


def test_classes(root):
    found = set()
    for directory, _, names in os.walk(os.path.join(root, SOURCES, PACKAGE)):
        for name in names:
            if TEST_CLASS.fullmatch(name):
                relative = os.path.relpath(os.path.join(directory, name), os.path.join(root, SOURCES))
                found.add(relative[:-len(".java")].replace(os.sep, "."))
    return found


def reported_classes(root):
    found = set()
    for report_dir in REPORT_DIRS:
        directory = os.path.join(root, report_dir)
        for name in os.listdir(directory) if os.path.isdir(directory) else []:
            match = REPORT.fullmatch(name)
            if match:
                found.add(match.group(1).split("$")[0])
    return found


def problems(root):
    classes, reports = test_classes(root), reported_classes(root)
    return ([f"{c} has no surefire/failsafe report: it did not run (is its name a test class name?)" for c in sorted(classes - reports)]
            + [f"report for {c} has no test class under {SOURCES}{PACKAGE}" for c in sorted(reports - classes)]), len(classes), len(reports)


def touch(root, path):
    full = os.path.join(root, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    open(full, "w").close()


def self_test():
    with tempfile.TemporaryDirectory() as root:
        assert problems(root) == ([], 0, 0), "no acceptance tests, no reports"
        source = SOURCES + PACKAGE + "platform/identity/"
        touch(root, source + "LoginAcceptanceTest.java")
        touch(root, source + "SessionAcceptanceIT.java")
        touch(root, source + "Fixtures.java")
        assert len(problems(root)[0]) == 2, "classes without reports fail; the helper is not a test class"
        touch(root, "backend/target/surefire-reports/TEST-com.forcitis.one.acceptance.platform.identity.LoginAcceptanceTest.xml")
        touch(root, "backend/target/surefire-reports/TEST-com.forcitis.one.acceptance.platform.identity.LoginAcceptanceTest$Nested.xml")
        assert "SessionAcceptanceIT" in problems(root)[0][0] and len(problems(root)[0]) == 1
        touch(root, "backend/target/failsafe-reports/TEST-com.forcitis.one.acceptance.platform.identity.SessionAcceptanceIT.xml")
        assert problems(root) == ([], 2, 2)
        touch(root, "backend/target/surefire-reports/TEST-com.forcitis.one.platform.identity.UnitTest.xml")
        assert problems(root) == ([], 2, 2), "reports of other tests are not counted"
        touch(root, "backend/target/surefire-reports/TEST-com.forcitis.one.acceptance.platform.identity.GhostAcceptanceTest.xml")
        assert "Ghost" in problems(root)[0][0]
    print("acceptance_reports self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    try:
        found, classes, reports = problems(toplevel())
    except CiError as e:
        print(f"::error title=Acceptance reports::{e}")
        return 1
    for message in found:
        print(f"::error title=Acceptance reports::{message}")
    print(f"acceptance_reports: {classes} test class(es), {reports} report(s), {len(found)} problem(s)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
