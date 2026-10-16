#!/usr/bin/env python3
"""新增或改动的测试类在引入它的 PR 上重复跑（错题本机制，用户 2026-10-17 批准）：一个偶发失败的测试通常头一次进 CI 就露馅，重复跑几遍让它在 PR 上挂，而不是合入后挂在 main 上。

用法：repeat_new_tests.py <基线引用> [--times 3] [--max 40] [--dry-run]  |  repeat_new_tests.py --self-test
基线引用如 origin/main；比较 `<基线>...HEAD` 里新增或改动的 backend/src/test/java 下以 Test 或 IT 结尾的类（文件仍存在的）。*Test 用 surefire，*IT 用 failsafe，各自在已编译的目录上
重复 --times 遍，每遍一个新的 Maven 进程（新 JVM、新容器）；任何一遍失败、或某个类没产生报告，整体失败（挂一次即不过）。类多于 --max 时只重复前 --max 个（按路径排序）并明说，不失败。
CI 里接在 `surefire and failsafe, side by side` 之后，只在 pull_request、重步骤门控放行（带 queued 标签）时跑。
不重复的：抽象类（改它则改重复它的子类，沿 `extends` 找，最多三层）、`*MssqlIT`（pom 的 testExcludes 不编译它，由 legacy-it 跑）、`OpenApiExportTest`（pom 的 excludes：它要单独先跑，且改写 openapi.json）。
名额（--max）先给集成测试再给单元测试：偶发失败主要出在 IT，一个改了几十个 IT 的 PR 不能把 IT 整批挤掉。
Maven 参数沿用 CI（backend_verify.sh）：每个插件 2 个 fork、fork 总超时 900 秒，挂起的测试不会等到步骤超时。
"""
import glob
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
BACKEND = os.path.normpath(os.path.join(HERE, "..", "..", "backend"))
TEST_ROOT = "backend/src/test/java/"
CLASS = re.compile(r"^(?P<name>[A-Z][A-Za-z0-9]*(?:Test|IT))\.java$")


SKIP = ("OpenApiExportTest",)


def test_sources(root=None):
    """{类名: 源码}：backend/src/test/java 下所有 .java（找抽象类的子类用）。"""
    root = root or os.path.join(BACKEND, "src", "test", "java")
    out = {}
    for folder, _, files in os.walk(root):
        for f in files:
            if f.endswith(".java"):
                out[f[:-5]] = open(os.path.join(folder, f), encoding="utf-8").read()
    return out


def concrete_subclasses(name, sources, depth=3):
    """抽象类 name 的具体子类（沿 extends，最多 depth 层）。"""
    found, frontier = [], [name]
    for _ in range(depth):
        nxt = []
        for parent in frontier:
            for cls, text in sources.items():
                if re.search(rf"\bclass\s+{re.escape(cls)}(?:<[^>]*>)?\s+extends\s+{re.escape(parent)}\b", text) and cls not in found:
                    found.append(cls)
                    nxt.append(cls)
        frontier = nxt
    return sorted(c for c in found if CLASS.match(c + ".java") and not re.search(rf"\babstract\s+class\s+{re.escape(c)}\b", sources[c]))


def changed_test_classes(base, git=None, sources=None, note=print):
    """([surefire 类], [failsafe 类])：新增或改动且仍存在的测试类，按路径排序；抽象类换成它的具体子类，*MssqlIT 与 SKIP 里的不算。"""
    git = git or (lambda *a: subprocess.run(["git", "-c", "core.quotepath=off", *a], capture_output=True, text=True, check=True).stdout)
    out = git("diff", "--no-renames", "--name-only", "--diff-filter=AM", f"{base}...HEAD")
    names = []
    for path in sorted(out.splitlines()):
        if path.startswith(TEST_ROOT) and path.endswith(".java") and (m := CLASS.match(os.path.basename(path))):
            names.append(m.group("name"))
    sources = test_sources() if sources is None else sources
    chosen = []
    for name in names:
        if re.search(rf"\babstract\s+class\s+{re.escape(name)}\b", sources.get(name, "")):
            subs = concrete_subclasses(name, sources)
            note(f"abstract {name}: repeating its {len(subs)} concrete subclass(es)")
            chosen += subs
        else:
            chosen.append(name)
    unit, integration = [], []
    for name in dict.fromkeys(chosen):
        if name.endswith("MssqlIT") or name in SKIP:
            note(f"not repeated: {name} (excluded from the default build, run elsewhere)")
        else:
            (integration if name.endswith("IT") else unit).append(name)
    return sorted(unit), sorted(integration)


FORKS = ["-Dtest.forks=2", "-Dtest.fork.timeout=900"]   # as CI (backend_verify.sh): the runners share one Docker Desktop, and a hung test must not wait for the step timeout


def maven_args(kind, classes):
    if kind == "unit":
        return ["./mvnw", "-B", "-q", "surefire:test", f"-Dtest={','.join(classes)}", *FORKS]
    return ["./mvnw", "-B", "-q", "failsafe:integration-test", "failsafe:verify", f"-Dit.test={','.join(classes)}", *FORKS]


def maven(kind, classes):
    """一遍：新的 Maven 进程。surefire:test / failsafe:integration-test 用已编译的目录（backend_verify.sh 刚编译过）。"""
    return subprocess.run(maven_args(kind, classes), cwd=BACKEND, capture_output=True, text=True)


def reported(kind, name):
    return any(f.endswith(".xml") for f in report_files(kind, name))


def report_files(kind, name):
    """这个类自己的报告文件：精确匹配类名（改 GlossaryTest 不能删 ErrorCodeGlossaryTest 的）。"""
    folder = os.path.join(BACKEND, "target", "surefire-reports" if kind == "unit" else "failsafe-reports")
    return [f for pattern in (f"TEST-*.{name}.xml", f"*.{name}.txt", f"*.{name}-output.txt") for f in glob.glob(os.path.join(folder, pattern)) if re.search(rf"(^|\.){re.escape(name)}(-output)?\.(xml|txt)$", os.path.basename(f))]


def clear_reports(kind, classes):
    for name in classes:
        for f in report_files(kind, name):
            os.remove(f)


def repeat(unit, integration, times, limit, run=maven, has_report=reported, clear=clear_reports, out=print):
    """0 当每个类每一遍都过；1 当任一遍失败或类没跑。"""
    chosen = [("integration", c) for c in integration] + [("unit", c) for c in unit]   # the limit takes the integration tests first
    skipped = chosen[limit:]
    chosen = chosen[:limit]
    if skipped:
        out(f"not repeated (more than --max {limit} classes): {', '.join(c for _, c in skipped)}")
    if not chosen:
        out("no new or changed test class: nothing to repeat")
        return 0
    for kind in ("unit", "integration"):
        classes = [c for k, c in chosen if k == kind]
        if not classes:
            continue
        for n in range(1, times + 1):
            clear(kind, classes)
            result = run(kind, classes)
            missing = [c for c in classes if not has_report(kind, c)]
            if result.returncode or missing:
                tail = "\n".join((result.stdout + result.stderr).strip().splitlines()[-40:])
                out(f"FAIL {kind} run {n} of {times}: {', '.join(classes)}" + (f"; produced no report: {', '.join(missing)}" if missing else "") + f"\n{tail}")
                return 1
            out(f"ok {kind} run {n} of {times}: {', '.join(classes)}")
    return 0


def self_test():
    global BACKEND
    git_out = "\n".join(["backend/src/test/java/com/x/FooTest.java", "backend/src/test/java/com/x/BarIT.java", "backend/src/test/java/com/x/Helper.java", "backend/src/test/java/com/x/FooTest.txt",
                         "backend/src/main/java/com/x/MainTest.java", "docs/x/NotATest.java", "backend/src/test/java/com/x/AbstractBaseTest.java", "backend/src/test/java/com/x/SeeyonMssqlIT.java",
                         "backend/src/test/java/com/x/OpenApiExportTest.java"])
    sources = {"FooTest": "class FooTest {}", "BarIT": "class BarIT {}", "AbstractBaseTest": "abstract class AbstractBaseTest {}", "ChildBTest": "class ChildBTest extends AbstractBaseTest {}",
               "ChildAIT": "class ChildAIT extends AbstractBaseTest {}", "GrandChildTest": "class GrandChildTest extends ChildBTest {}", "MidAbstractTest": "abstract class MidAbstractTest extends AbstractBaseTest {}",
               "LeafOfMidTest": "class LeafOfMidTest extends MidAbstractTest {}", "Helper": "class Helper {}", "SeeyonMssqlIT": "class SeeyonMssqlIT {}", "OpenApiExportTest": "class OpenApiExportTest {}",
               "Unrelated2Test": "class Unrelated2Test extends SomethingElse {}"}
    calls, notes = [], []
    unit, integration = changed_test_classes("origin/main", git=lambda *a: (calls.append(a), git_out)[1], sources=sources, note=notes.append)
    assert unit == ["ChildBTest", "FooTest", "GrandChildTest", "LeafOfMidTest"] and integration == ["BarIT", "ChildAIT"], (unit, integration)
    assert calls == [("diff", "--no-renames", "--name-only", "--diff-filter=AM", "origin/main...HEAD")], "added or modified only (a deleted test has nothing to run), renames not folded"
    assert any("abstract AbstractBaseTest: repeating its 4" in n for n in notes) and any("SeeyonMssqlIT" in n for n in notes) and any("OpenApiExportTest" in n for n in notes), notes
    assert "AbstractBaseTest" not in unit and "MidAbstractTest" not in unit and "SeeyonMssqlIT" not in integration and "OpenApiExportTest" not in unit, "abstract, Mssql and the excluded export test are never run"
    assert maven_args("unit", ["A", "B"]) == ["./mvnw", "-B", "-q", "surefire:test", "-Dtest=A,B", "-Dtest.forks=2", "-Dtest.fork.timeout=900"]
    assert maven_args("integration", ["C"]) == ["./mvnw", "-B", "-q", "failsafe:integration-test", "failsafe:verify", "-Dit.test=C", "-Dtest.forks=2", "-Dtest.fork.timeout=900"], "failsafe:verify is what fails the run"
    with tempfile.TemporaryDirectory() as tmp:
        old, BACKEND = BACKEND, tmp
        try:
            for kind, folder in (("unit", "surefire-reports"), ("integration", "failsafe-reports")):
                os.makedirs(os.path.join(tmp, "target", folder))
                for f in ("TEST-com.x.GlossaryTest.xml", "com.x.GlossaryTest.txt", "com.x.GlossaryTest-output.txt", "TEST-com.x.ErrorCodeGlossaryTest.xml", "com.x.ErrorCodeGlossaryTest.txt", "failsafe-summary.xml"):
                    open(os.path.join(tmp, "target", folder, f), "w").write("x")
                assert reported(kind, "GlossaryTest") and reported(kind, "ErrorCodeGlossaryTest") and not reported(kind, "Glossary")
                clear_reports(kind, ["GlossaryTest"])
                left = sorted(os.listdir(os.path.join(tmp, "target", folder)))
                assert left == ["TEST-com.x.ErrorCodeGlossaryTest.xml", "com.x.ErrorCodeGlossaryTest.txt", "failsafe-summary.xml"], (kind, left)
                assert not reported(kind, "GlossaryTest") and reported(kind, "ErrorCodeGlossaryTest"), "exact class names: GlossaryTest does not touch ErrorCodeGlossaryTest"
        finally:
            BACKEND = old
    class R:
        def __init__(self, code, text=""):
            self.returncode, self.stdout, self.stderr = code, text, ""
    log, seen = [], []

    def runner(codes):
        it = iter(codes)

        def run(kind, classes):
            seen.append((kind, tuple(classes)))
            return R(next(it, 0), "boom line")
        return run
    out = log.append
    assert repeat([], [], 3, 40, out=out) == 0 and "nothing to repeat" in log[-1]
    seen.clear(); log.clear()
    assert repeat(["A"], ["B"], 3, 40, run=runner([0] * 6), has_report=lambda k, c: True, clear=lambda *a: None, out=out) == 0
    assert seen == [("unit", ("A",))] * 3 + [("integration", ("B",))] * 3, "every class three times, each time a new Maven process"
    seen.clear(); log.clear()
    assert repeat(["A"], ["B"], 3, 40, run=runner([0, 1]), has_report=lambda k, c: True, clear=lambda *a: None, out=out) == 1 and len(seen) == 2 and "FAIL unit run 2 of 3" in log[-1] and "boom line" in log[-1], "one failure is enough, and it stops there"
    seen.clear(); log.clear()
    assert repeat(["A"], [], 3, 40, run=runner([0] * 3), has_report=lambda k, c: False, clear=lambda *a: None, out=out) == 1 and "produced no report: A" in log[-1], "a class that did not run is a failure, not a pass"
    seen.clear(); log.clear()
    assert repeat(["A", "B", "C"], ["D", "E"], 1, 3, run=runner([0] * 5), has_report=lambda k, c: True, clear=lambda *a: None, out=out) == 0 and seen == [("unit", ("A",)), ("integration", ("D", "E"))] \
        and "not repeated (more than --max 3 classes): B, C" in log[0], (seen, log)   # the integration tests come first
    cleared = []
    repeat(["A"], [], 2, 40, run=runner([0, 0]), has_report=lambda k, c: True, clear=lambda k, cl: cleared.append((k, tuple(cl))), out=out)
    assert cleared == [("unit", ("A",))] * 2, "the reports of the previous pass are removed before each pass (an old report must not vouch for a new run)"
    print("repeat_new_tests self-test: ok")


def main():
    args = sys.argv[1:]
    if args == ["--self-test"]:
        self_test()
        return
    if not args or args[0].startswith("--"):
        sys.exit(__doc__)
    base, times, limit, dry = args.pop(0), 3, 40, False
    while args:
        a = args.pop(0)
        if a == "--times" and args and args[0].isdigit() and int(args[0]) >= 1:
            times = int(args.pop(0))
        elif a == "--max" and args and args[0].isdigit():
            limit = int(args.pop(0))
        elif a == "--dry-run":
            dry = True
        else:
            sys.exit(__doc__)
    unit, integration = changed_test_classes(base)
    if dry:
        print(f"unit: {unit}\nintegration: {integration}\ntimes: {times}, max: {limit}")
        return
    sys.exit(repeat(unit, integration, times, limit))


main()
