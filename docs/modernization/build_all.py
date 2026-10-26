#!/usr/bin/env python3
"""统一生成入口：按 GENERATORS 清单依次运行生成器与检查（GOV-1023-FV2 O14，定稿 1.11、2.5）。INTENT / SESSION_LOG / STATUS / SESSIONS / WORK_BOARD 已停止生成。
  build_all.py                  生成全部产物
  build_all.py --check          CI（scripts/ci/light.sh）：重新生成前后无差异；不入仓产物（清单第四列与 adr/ADR-*.md）没被跟踪且都已生成；
                                tasks/TASKS.md 两次生成一致；RULE_REVIEWS.json 原结论未被改写（rules/review_revisions.py，比对 origin/main 的合并基点）
  build_all.py --self-test-list 带自测的生成器；light.sh 逐个跑 --self-test，本入口不再跑工具自测
"""
import hashlib, importlib.util, os, subprocess, sys, tempfile
from pathlib import Path

BASE = os.path.dirname(os.path.abspath(__file__))
GLOSSARY_INDEX = os.path.join("..", "..", "backend", "glossary", "proposed", "README.md")
# 生成器清单，按运行顺序（scan_commands → panorama_build → notes_build 读前者产物）：(脚本（相对本目录）, 负责角色, 带 --self-test, 不入仓产物（.gitignore）, 需要 PyYAML)
GENERATORS = [
    ("glossary/build_terms.py", "language", False, (), True),   # terms.yaml 入仓，--check 要求可复现；PyYAML 见 SUPPLY_CHAIN.md S1
    ("build_decision_digest.py", "language", True, (), False),   # 也校验 intent/ 片段的文件名与格式
    ("glossary/build_issues.py", "language", True, (), False),
    ("adr/build_adrs.py", "design", True, ("adr/INDEX.md", "adr/README.md"), False),
    ("rules/build_brief.py", "language", False, ("MODERNIZATION_BRIEF.md",), False),
    ("phase1a/infra/build_legacy_read_register.py", "platform", True, (), False),
    ("../../backend/glossary/build_proposed_index.py", "language", True, (GLOSSARY_INDEX,), False),
    ("rules/build_rule_index.py", "language", True, (), False),
    ("architecture/build_architecture.py", "design", True, (), True),
    ("quality/defect-bank/defect_bank_check.py", "platform", True, (), False),
    ("architecture/check_proposals.py", "design", True, (), False),
    ("architecture/proposal_reads.py", "design", True, (), False),
    ("architecture/ssot/09-processes/build_catalog.py", "design", True, (), False),
    ("architecture/panorama/scan_commands.py", "design", True, ("architecture/ssot/11-commands/commands.yaml", "architecture/ssot/11-commands/GAPS.md"), False),
    ("architecture/panorama/panorama_build.py", "design", True, ("architecture/panorama/panorama.yaml", "architecture/panorama/GAPS.md"), False),
    ("architecture/frameworks/notes_build.py", "design", True, ("architecture/frameworks/CONCEPT_INDEX.md", "architecture/frameworks/NOTES_INDEX.md"), False),
    ("architecture/panorama/object_freeze_check.py", "design", True, ("architecture/panorama/FREEZE_FACTS.md", "architecture/panorama/FREEZE_FACTS.yaml", "architecture/panorama/FREEZE_IMPACT.md"), False),
    ("check_tasks.py", "lead", True, (), False),   # 任务片段检查与 tasks/TASKS.md（二乙随 Issues 删）
]
UNTRACKED_PRODUCTS = tuple(p for g in GENERATORS for p in g[3])


def snapshot(base=BASE):
    files = subprocess.run(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", ".", GLOSSARY_INDEX],
                           cwd=base, capture_output=True, text=True, check=True).stdout.split("\0")
    return {name: hashlib.sha256(Path(base, name).read_bytes()).hexdigest() if Path(base, name).is_file() else None
            for name in files if name and name != "tasks/TASKS.md"}


# 不入仓产物：--check 要求它们没被跟踪（.gitignore 对已跟踪文件无效，冲突时「保留对方版本」会让它们悄悄回到库里），且生成之后确实存在
def check_untracked_products(base=BASE, products=UNTRACKED_PRODUCTS, adr_glob="adr/ADR-*.md"):
    tracked = subprocess.run(["git", "ls-files", "-ci", "--exclude-standard", "-z"], cwd=base, capture_output=True, text=True, check=True).stdout.split("\0")
    problems = [f"{name} 是生成产物（.gitignore），却被跟踪：git rm --cached 它，保留源文件的改动" for name in tracked if name]
    problems += [f"{name} 生成之后不存在：生成步骤没有写出它" for name in products if not Path(base, name).is_file()]
    if not list(Path(base).glob(adr_glob)):
        problems.append(f"{adr_glob} 生成之后一个都没有：build_adrs.py 没有写出 ADR 拷贝")
    if problems:
        sys.exit("不入仓的生成产物不对：\n" + "\n".join(problems))
    print("check: 不入仓的生成产物没有被跟踪，且都已生成")


def run_steps(names, base=BASE):
    for step in names:
        r = subprocess.run([sys.executable, os.path.join(base, step)], cwd=base, capture_output=True, text=True)
        if r.returncode:
            sys.exit(f"{step} 失败：\n{r.stdout}{r.stderr}")
        print(f"{step}: {r.stdout.strip().splitlines()[0] if r.stdout.strip() else 'ok'}")
        if r.stderr.strip():
            print(r.stderr.strip(), file=sys.stderr)


def generate(check, names, base=BASE):
    before = snapshot(base) if check else None
    run_steps(names, base)
    if not check:
        return
    first = Path(base, "tasks/TASKS.md").read_bytes()
    run_steps(["check_tasks.py"], base)
    if Path(base, "tasks/TASKS.md").read_bytes() != first:
        sys.exit("tasks/TASKS.md 连续两次生成内容不一致")
    after = snapshot(base)
    dirty = "\n".join(sorted(name for name in before.keys() | after.keys() if before.get(name) != after.get(name)))
    if dirty:
        sys.exit("生成产物与片段不一致（请运行 build_all.py 后重新检查，不要直接编辑生成产物）：\n" + dirty)
    print("check: 入仓产物重新生成前后无差异；TASKS.md 连续两次生成一致")


def self_test():
    import contextlib, io
    with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
        root, base = Path(tmp), Path(tmp, "docs/modernization")
        for folder in ("inputs", "tasks"):
            (base / folder).mkdir(parents=True)
        (root / ".gitignore").write_text("docs/modernization/tasks/TASKS.md\ndocs/modernization/gen.md\ndocs/modernization/A-*.md\n", encoding="utf-8")
        (base / "build_index.py").write_text("from pathlib import Path\np = Path('.')\n(p / 'README.md').write_text(''.join(f.read_text() for f in sorted((p / 'inputs').glob('*.txt'))))\n", encoding="utf-8")
        (base / "check_tasks.py").write_text("from pathlib import Path\nPath('tasks/TASKS.md').write_text('t')\n", encoding="utf-8")
        (base / "inputs/a.txt").write_text("one", encoding="utf-8")
        git = lambda *args: subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", "-C", str(root), *args], check=True, capture_output=True)
        git("init", "-q")
        names = ["build_index.py", "check_tasks.py"]
        generate(False, names, str(base))
        git("add", ".")
        def fails(message, run=lambda: generate(True, names, str(base))):
            try:
                run()
            except SystemExit as error:
                assert message in str(error), str(error)
            else:
                raise AssertionError(f"应失败：{message}")
        for change in (lambda: (base / "README.md").write_text("manual edit"), lambda: (base / "README.md").unlink(), lambda: (base / "inputs/b.txt").write_text("two")):
            change()
            fails("生成产物与片段不一致")
        generate(True, names, str(base))   # 已生成一致的未提交改动：通过
        (base / "tasks/TASKS.md").unlink()
        generate(True, names, str(base))   # TASKS 缺失可重建
        (base / "check_tasks.py").write_text("import time\nfrom pathlib import Path\nPath('tasks/TASKS.md').write_text(str(time.time_ns()))\n", encoding="utf-8")
        fails("连续两次生成内容不一致")
        (base / "check_tasks.py").write_text("raise SystemExit('generation failed')\n", encoding="utf-8")
        fails("generation failed")
        (base / "gen.md").write_text("g"), (base / "A-1.md").write_text("a")
        check_untracked_products(str(base), ("gen.md",), "A-*.md")   # 已生成且未被跟踪：通过
        fails("生成之后不存在", lambda: check_untracked_products(str(base), ("gen.md", "missing.md"), "A-*.md"))
        fails("一个都没有", lambda: check_untracked_products(str(base), ("gen.md",), "B-*.md"))
        git("add", "-f", "docs/modernization/gen.md")
        fails("却被跟踪", lambda: check_untracked_products(str(base), ("gen.md",), "A-*.md"))
    assert all(Path(BASE, g[0]).is_file() and g[1] in {"design", "dev", "language", "lead", "platform"} for g in GENERATORS) and len({g[0] for g in GENERATORS}) == len(GENERATORS), "清单脚本都在、不重复，角色取自 roles/"
    print("build_all --self-test：通过（手改、删除、漏生成、生成失败、TASKS 两次不一致、不入仓产物被跟踪或没生成即失败；一致的未提交产物与重建通过；清单脚本齐全）")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if sys.argv[1:] == ["--self-test-list"]:
        return print("\n".join(os.path.normpath(os.path.join("docs/modernization", g[0])) for g in GENERATORS if g[2]))
    has_yaml = bool(importlib.util.find_spec("yaml"))
    message = f"{sys.executable} 缺少 PyYAML，无法运行 glossary/build_terms.py 校验 terms.yaml 可复现（依赖见 SUPPLY_CHAIN.md S1）"
    if not has_yaml:
        sys.exit("失败：" + message) if "--check" in sys.argv else print("警告：" + message + "；本次跳过需要它的生成器", file=sys.stderr)
    generate("--check" in sys.argv, [g[0] for g in GENERATORS if has_yaml or not g[4]])
    if "--check" not in sys.argv:
        return
    check_untracked_products()
    merge_base = subprocess.run(["git", "merge-base", "HEAD", "origin/main"], cwd=BASE, capture_output=True, text=True)
    base_args = ["--base", merge_base.stdout.strip()] if merge_base.returncode == 0 and merge_base.stdout.strip() else []
    if not base_args:
        print("警告：取不到 origin/main 的合并基点，原结论比对交给 review_revisions.py：有 origin/main 时以它本身作基线（可能误报），否则 CI 失败、本地告警", file=sys.stderr)
    r = subprocess.run([sys.executable, "-B", os.path.join(BASE, "rules", "review_revisions.py"), *base_args], cwd=BASE, capture_output=True, text=True)
    if r.stderr.strip():
        print(r.stderr.strip(), file=sys.stderr)
    if r.returncode:
        sys.exit(f"RULE_REVIEWS.json 修订记录校验失败：\n{r.stdout}")
    print(f"rules/review_revisions.py: {r.stdout.strip()}")


if __name__ == "__main__":
    main()
