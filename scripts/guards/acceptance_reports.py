#!/usr/bin/env python3
"""Check the exact acceptance-report set for default, legacy-it, or full (nightly profile).
The SQL Server list lives in backend/pom.xml, property legacy.it.acceptance. Nested reports count under their outer class.
Usage: acceptance_reports.py [--tier default|legacy-it|full] [--report-dir <path>] | --legacy-it-tests | --self-test
"""
import argparse
import json
import os
import re
import subprocess
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
JAVA_NON_CODE = re.compile(r'//[^\r\n]*|/\*.*?\*/|"""(?:\\.|(?!""").)*"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.DOTALL)


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
    paths = set(legacy_acceptance_paths(root))
    missing = sql_server_acceptance_users(root) - paths
    if missing:
        raise CiError("SQL Server acceptance users missing from legacy.it.acceptance: " + ", ".join(sorted(missing)))
    listed = {path[:-5].replace("/", ".") for path in paths}
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


def sql_server_acceptance_users(root):
    sources = {p: JAVA_NON_CODE.sub(" ", p.read_text(encoding="utf-8")) for p in Path(root, SOURCES).rglob("*.java")}
    users = {p for p, text in sources.items() if re.search(r"\bTestMssql\b", text)}
    while True:
        added = {p for p, text in sources.items()
                 if any(p.parent == helper.parent and re.search(rf"\b{re.escape(helper.stem)}\b", text)
                        for helper in users)}
        if added <= users:
            break
        users |= added
    return {p.relative_to(Path(root, SOURCES)).as_posix() for p in users
            if p.is_relative_to(Path(root, SOURCES, PACKAGE)) and TEST_CLASS.fullmatch(p.name)}


def discovery_self_test():
    with tempfile.TemporaryDirectory() as root:
        base = SOURCES + PACKAGE + "platform/integration/"
        files = {
            "StaticImportIT": "import static com.forcitis.one.TestMssql.shared; class StaticImportIT {}",
            "OrgSourceWorldSupport": "abstract class OrgSourceWorldSupport { Object sql = TestMssql.shared(); }",
            "InheritedIT": "class InheritedIT extends OrgSourceWorldSupport {}",
            "CompositeIT": "class CompositeIT { Object sql = OrgSourceWorldSupport.World.MSSQL; }",
            "IndirectSupport": "class IndirectSupport { Object sql = OrgSourceWorldSupport.World.MSSQL; }",
            "TransitiveIT": "class TransitiveIT { Object sql = IndirectSupport.sql; }",
            "LineCommentIT": "class LineCommentIT {} // see TestMssql OrgSourceWorldSupport StaticImportIT",
            "BlockCommentIT": '/* " TestMssql\n OrgSourceWorldSupport StaticImportIT */ class BlockCommentIT {}',
            "StringLiteralIT": r'class StringLiteralIT { String ref = "// TestMssql /* OrgSourceWorldSupport */ \\\" StaticImportIT"; }',
            "TextBlockIT": 'class TextBlockIT { String ref = """\n"TestMssql" // OrgSourceWorldSupport\n/* StaticImportIT */\n"""; }',
            "CommentReferenceIT": "class CommentReferenceIT {} // see StaticImportIT",
            "StringReferenceIT": 'class StringReferenceIT { String ref = "IndirectSupport"; }',
            "ReferenceAfterNoiseIT": 'class ReferenceAfterNoiseIT { String ref = "/*"; /* "ignored" */ Object sql = IndirectSupport.sql; }',
            "NormalIT": "class NormalIT {}",
        }
        for name, body in files.items():
            touch(root, base + name + ".java")
            Path(root, base + name + ".java").write_text(body)
        expected = {PACKAGE + "platform/integration/" + n + ".java"
                    for n in ("StaticImportIT", "InheritedIT", "CompositeIT", "TransitiveIT", "ReferenceAfterNoiseIT")}
        actual = sql_server_acceptance_users(root)
        assert actual == expected, f"QUAL-CARD-H-R3-B: ignore comments, strings and text blocks; keep static imports, inheritance and real transitive references: {actual ^ expected}"
        fixture_pom(root, sorted(expected))
        touch(root, base + "UnregisteredIT.java")
        Path(root, base + "UnregisteredIT.java").write_text("class UnregisteredIT extends OrgSourceWorldSupport {}")
        try:
            legacy_classes(root, test_classes(root))
        except CiError as error:
            assert "UnregisteredIT.java" in str(error), str(error)
        else:
            raise AssertionError("QUAL-CARD-H-R2-4: unregistered SQL Server acceptance class must fail and name the class")


def legacy_script_self_test(root):
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        scripts = base / "scripts/ci"
        scripts.mkdir(parents=True)
        script = scripts / "legacy_it.sh"
        script.write_text(Path(root, "scripts/ci/legacy_it.sh").read_text())
        (base / "scripts/codegen").mkdir()
        (base / "scripts/codegen/generate_api.sh").write_text(Path(root, "scripts/codegen/generate_api.sh").read_text())
        (base / "scripts/guards").mkdir()
        (base / "scripts/guards/acceptance_reports.py").write_text(
            'import sys\nprint("SelectedIT" if "--legacy-it-tests" in sys.argv else "reports checked")\n')
        backend = base / "backend"
        backend.mkdir()
        mvnw = backend / "mvnw"
        mvnw.write_text(f"#!{sys.executable}\n" + '''import json, os, signal, sys
from pathlib import Path
args = sys.argv[1:]
with open("calls.jsonl", "a") as out:
    out.write(json.dumps(args) + "\\n")
if "package" in args:
    assert "-DskipTests" in args and "-Djacoco.skip=true" in args
    if os.environ.get("HANG_PACKAGE"):
        signal.pause()
    Path("target/classes").mkdir(parents=True, exist_ok=True)
    Path("target/classes/Current.class").write_text("current")
    Path("target/forcitis-one-current.jar").write_text("current")
    sys.exit(int(os.environ.get("PACKAGE_STATUS", "0")))
assert Path("target/forcitis-one-current.jar").stat().st_mtime_ns >= Path("target/classes/Current.class").stat().st_mtime_ns
assert "-Dit.test=SelectedIT" in args
if os.environ.get("HANG_FAILSAFE"):
    signal.pause()
sys.exit(int(os.environ.get("FAILSAFE_STATUS", "0")))
''')
        mvnw.chmod(0o755)
        env = {**os.environ, "BACKEND_WATCHDOG_SECONDS": "1500"}
        args = ["bash", str(script), "-Dtest.forks=2", "-Dtest.fork.timeout=1800"]
        missing = subprocess.run(args, env=env, capture_output=True, text=True, timeout=10)
        assert missing.returncode == 1 and "bash scripts/codegen/generate_api.sh" in missing.stderr, missing.stdout + missing.stderr
        assert not (backend / "calls.jsonl").exists(), "缺生成物必须先失败，不能启动 Maven 或容器"
        for name in ("openapi.json", "src/generated/schema.ts", "src/generated/validators.ts", "src/generated/operations.ts"):
            product = base / "frontend/packages/api" / name
            product.parent.mkdir(parents=True, exist_ok=True)
            product.write_text("generated test fixture\n")
        passed = subprocess.run(args, env=env, capture_output=True, text=True, timeout=10)
        assert passed.returncode == 0 and "reports checked" in passed.stdout, passed.stdout + passed.stderr
        calls = [json.loads(line) for line in (backend / "calls.jsonl").read_text().splitlines()]
        assert len(calls) == 2 and calls[0][1] == "package" and calls[1][1:3] == ["failsafe:integration-test", "failsafe:verify"], calls
        assert all("-Plegacy-it" in c and all(p in c for p in args[2:]) for c in calls), calls
        for setting, status in (("PACKAGE_STATUS", 17), ("FAILSAFE_STATUS", 19)):
            failed = subprocess.run(args, env={**env, setting: str(status)}, capture_output=True, text=True, timeout=10)
            assert failed.returncode == status and "reports checked" not in failed.stdout, (setting, failed)
        bin_dir = base / "bin"
        bin_dir.mkdir()
        stubs = {"pgrep": "echo 424242", "jcmd": "echo thread-dump", "pkill": "exit 0",
                 "sleep": 'if [ "$1" = 20 ]; then /bin/sleep 0.01; else /bin/sleep "$1"; fi'}
        for name, body in stubs.items():
            command = bin_dir / name
            command.write_text("#!/bin/sh\n" + body + "\n")
            command.chmod(0o755)
        dump = backend / "target/thread-dumps/424242.txt"
        for phase in ("PACKAGE", "FAILSAFE"):
            (backend / "calls.jsonl").unlink()
            dump.unlink(missing_ok=True)
            hung = subprocess.run(args, env={**env, "PATH": str(bin_dir) + os.pathsep + env["PATH"],
                                            "BACKEND_WATCHDOG_SECONDS": "0.3", "HANG_" + phase: "1"},
                                  capture_output=True, text=True, timeout=10)
            assert hung.returncode == 124 and "exceeded 0.3 s" in hung.stdout, (phase, hung.stdout + hung.stderr)
            assert dump.read_text().strip() == "thread-dump", phase
            calls = [json.loads(line) for line in (backend / "calls.jsonl").read_text().splitlines()]
            assert len(calls) == (1 if phase == "PACKAGE" else 2), f"QUAL-CARD-H-R3-D: timed-out packaging must not start failsafe: {calls}"


def configuration_self_test(root):
    pom = ET.parse(Path(root, "backend/pom.xml"))
    plugins = pom.findall("m:build/m:plugins/m:plugin", NS)
    configs = {p.findtext("m:artifactId", namespaces=NS): p.find("m:configuration", NS) for p in plugins}
    failsafe = configs["maven-failsafe-plugin"]
    assert [e.text for e in failsafe.findall("m:excludes/m:exclude", NS)] == ["${legacy.it.acceptance}", "**/*MssqlIT.java", "**/*$*"], "QUAL-CARD-H-R2-6: default failsafe must exclude the SQL Server list, *MssqlIT and nested classes"
    assert failsafe.findtext("m:reportsDirectory", namespaces=NS) == "${integration.reports}", "PR SQL Server reports must be isolated"
    assert failsafe.findtext("m:summaryFile", namespaces=NS) == "${integration.reports}/failsafe-summary.xml"
    profile = pom.find("m:profiles/m:profile[m:id='legacy-it']", NS)
    override = profile.find("m:build/m:plugins/m:plugin[m:artifactId='maven-failsafe-plugin']/m:configuration/m:excludes", NS)
    assert override is not None and override.attrib.get("combine.self") == "override" and not list(override), "full legacy-it must restore all ITs, including the moved classes"
    assert profile.find("m:build/m:plugins/m:plugin[m:artifactId='maven-failsafe-plugin']/m:configuration/m:includes", NS) is None, "nightly must keep the default ITs too"
    actual = sql_server_acceptance_users(root)
    assert actual == set(legacy_acceptance_paths(root)), f"SQL Server acceptance users differ from legacy.it.acceptance: missing={actual - set(legacy_acceptance_paths(root))}, stale={set(legacy_acceptance_paths(root)) - actual}"
    script = Path(root, "scripts/ci/legacy_it.sh").read_text()
    assert "--legacy-it-tests" in script and '-Dit.test="$tests"' in script and "-Plegacy-it" in script, "PR must select the POM list, not just *MssqlIT"
    assert "--tier legacy-it" in script and "--report-dir" in script
    package = './mvnw -B package -DskipTests -Djacoco.skip=true -Plegacy-it "$@"'
    assert package in script and script.index(package) < script.index("./mvnw -B failsafe:integration-test"), "QUAL-CARD-H-R2-1: package the current classes before SQL Server tests need the jar"
    assert script.index("watchdog_pid=$!") < script.index(package), "QUAL-CARD-H-R3-D: start the watchdog before packaging"
    assert script.count('sleep "${BACKEND_WATCHDOG_SECONDS:-1500}"') == 1, "QUAL-CARD-H-R3-D: packaging and failsafe must share one watchdog budget"
    assert "test-compile" not in script, "package already compiles tests; failsafe goals must not repeat the lifecycle"
    assert 'sleep "${BACKEND_WATCHDOG_SECONDS:-1500}"' in script and 'Thread.print -l' in script and 'status=124' in script, "QUAL-CARD-H-R2-2: SQL Server must dump hung forks and fail before the fork timeout"
    ci = Path(root, ".github/workflows/ci.yml").read_text()
    report_step = ci.index("- name: every acceptance test class produced")
    rerun_step = ci.index("- name: SQL Server integration tests (profile legacy-it)")   # #742: the repeat step is gone; the SQL Server step is the run that adds reports after the default one
    assert report_step < rerun_step and "acceptance_reports.py --tier default" in ci[report_step:rerun_step], "QUAL-CARD-H-R2-7: default reports must be checked before explicit reruns"
    sql_step = ci.split("- name: SQL Server integration tests (profile legacy-it)", 1)[1].split("\n      - ", 1)[0]   # the step that waits for the run and fails the job (#1034: the run itself starts beside surefire and failsafe)
    start_step = ci.split("- name: start the SQL Server tests beside surefire and failsafe (profile legacy-it)", 1)[1].split("\n      - ", 1)[0]
    assert "scripts/ci/legacy_it.sh -Dtest.forks=2 -Dtest.fork.timeout=1800" in start_step, "QUAL-CARD-H-R2-2: SQL Server must use two bounded forks"
    assert "BACKEND_WATCHDOG_SECONDS: '1500'" in start_step and "timeout-minutes: 30" in sql_step, "QUAL-CARD-H-R2-2: watchdog must run before the step deadline"
    assert 'git worktree add --detach -q "$legacy" HEAD' in start_step and 'test "$(cat "$LEGACY_IT_RC")" = 0' in sql_step, "#1034: the SQL Server run has its own checkout, and its status fails the backend job"
    assert ci.index("- name: start the SQL Server tests beside") < ci.index("- name: surefire and failsafe, side by side"), "#1034: the SQL Server run starts before surefire and failsafe so that they overlap"
    nightly = Path(root, ".github/workflows/nightly-legacy-it.yml").read_text()
    assert "scripts/ci/backend_verify.sh -Plegacy-it" in nightly and "acceptance_reports.py --tier full" in nightly, "nightly must run and check both tiers"


def self_test():
    discovery_self_test()
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
    legacy_script_self_test(Path(__file__).resolve().parents[2])
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
