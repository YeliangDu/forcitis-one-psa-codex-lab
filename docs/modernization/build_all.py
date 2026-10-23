#!/usr/bin/env python3
"""统一生成入口：依次生成 INTENT / SESSION_LOG / STATUS、正式术语 glossary/terms.yaml、会话一览 SESSIONS、用语问题清单 glossary/ISSUES、ADR 与 README、brief、
已定决策速查表 DECISION_DIGEST.md（由 intent/ 片段汇总，不入仓）、提议术语清单（backend/glossary/proposed/README.md）、老系统读取登记（phase1a/infra/LEGACY_READ_REGISTER.md，由 legacy-read/ 片段生成，不入仓）。

用法：
  python3 docs/modernization/build_all.py          生成全部产物
  python3 docs/modernization/build_all.py --check --changed 文件  推送前的快速检查（scripts/precheck.sh --pre-push 用，GOV-1023-QUA-04）：文件列出本次改动的仓库相对路径；
                                                   各工具自测只跑改到的那一个（改了 build_all.py 则全跑），改动全在 docs/modernization 里 architecture/、glossary/、rules/ 之外时，再跳过耗时的四个生成器（产物已在时）；
                                                   完整检查留给 CI 与合入队列（不带 --changed 即完整）
  python3 docs/modernization/build_all.py --check  要求 docs/modernization 与提议术语清单重新生成前后无差异（CI 使用）：
                                                   terms.yaml 过期或缺失时失败；允许已生成一致的未提交改动；
                                                   ADR 拷贝与索引、brief、提议术语清单 README、frameworks 两个索引、panorama/ 下 FREEZE_*、panorama.yaml、GAPS.md、ssot/11-commands/{commands.yaml,GAPS.md} 不入仓（.gitignore），只要求能生成；
                                                   INTENT / SESSION_LOG / STATUS / DECISION_DIGEST / SESSIONS / glossary/ISSUES / LEGACY_READ_REGISTER / tasks/TASKS 不入仓（.gitignore），只要求能生成、片段合规
                                                   （会话画像必备字段、会话名 one-* 且唯一；用语问题片段格式）；TASKS 另要求连续两次生成内容一致；
                                                   另校验 RULE_REVIEWS.json 的原结论未被改写（与 origin/main 的合并基点比对；取不到时由 review_revisions.py
                                                   按环境处理：CI 失败，本地告警），
                                                   并运行提议术语清单、会话一览（含钩子 session_context.py）、用语问题清单生成器的自测
"""
import hashlib, importlib.util, os, subprocess, sys, tempfile
from pathlib import Path

BASE = os.path.dirname(os.path.abspath(__file__))
GLOSSARY_INDEX = os.path.join("..", "..", "backend", "glossary", "proposed", "README.md")
STEPS = ["build_records.py", "build_decision_digest.py", "build_sessions.py", "build_work_board.py", "glossary/build_issues.py", "adr/build_adrs.py", "rules/build_brief.py",
         "phase1a/infra/build_legacy_read_register.py", "../../backend/glossary/build_proposed_index.py"]
SELF_TESTED = ["build_decision_digest.py", "build_sessions.py", "build_work_board.py", "adr/build_adrs.py", "adr/new_adr.py", "../../scripts/queue/merge_train.py", "../../scripts/merge/mq.py", "../../scripts/hooks/session_context.py", "glossary/build_issues.py", "glossary/term_rules.py",
               "phase1a/infra/build_legacy_read_register.py", STEPS[-1]]
STEPS.append("rules/build_rule_index.py")   # 规则索引（每个治理工具各占一行、位置分开，几个 PR 同时合入不冲突）
SELF_TESTED.append("rules/build_rule_index.py")
SELF_TESTED.append("../../scripts/hooks/start_sheet.py")
SELF_TESTED.append("../../scripts/ci/ci_recurrence.py")   # 每晚复发检测：失败 CI 按错题本报错特征归类
SELF_TESTED.append("../../scripts/ops/session_daily.py")   # 会话日报取数（调度每天早上用）
# 正式术语 glossary/terms.yaml 由 build_terms.py 生成，--check 要求可复现；PyYAML 依赖登记于 SUPPLY_CHAIN.md S1
STEPS.append("quality/defect-bank/defect_bank_check.py")   # 错题本检查（每个治理工具各占一行、位置分开，几个 PR 同时合入不冲突）
SELF_TESTED.append("quality/defect-bank/defect_bank_check.py")
if importlib.util.find_spec("yaml"):
    STEPS.insert(1, "glossary/build_terms.py")
    STEPS.insert(-1, "architecture/build_architecture.py")   # 架构施工图（architecture/DESIGN.md §3）：mmd 可复现、SVG 未过期
    SELF_TESTED.append("architecture/build_architecture.py")   # 上下文映射边校验与 A1
elif "--check" in sys.argv:
    sys.exit(f"失败：{sys.executable} 缺少 PyYAML，无法运行 glossary/build_terms.py 校验 terms.yaml 可复现（依赖见 SUPPLY_CHAIN.md S1）")
else:
    print(f"警告：{sys.executable} 缺少 PyYAML，本次未重新生成 glossary/terms.yaml；--check 会因此失败（依赖见 SUPPLY_CHAIN.md S1）", file=sys.stderr)
STEPS.append("architecture/check_proposals.py")   # 提议稿状态行检查（每个治理工具各占一行、位置分开，几个 PR 同时合入不冲突）
SELF_TESTED.append("architecture/check_proposals.py")
STEPS.append("architecture/panorama/scan_commands.py")   # 命令目录不入仓，先生成再由全景读取；编号登记与转换仍入仓
SELF_TESTED.append("architecture/panorama/scan_commands.py")
STEPS.append("architecture/panorama/panorama_build.py")   # 全景追溯模型与 TD/BU 缺口（每个治理工具各占一行、位置分开，几个 PR 同时合入不冲突；排在 notes_build 之前：笔记检查读它生成的 panorama.yaml）
SELF_TESTED.append("architecture/panorama/panorama_build.py")
STEPS.append("architecture/frameworks/notes_build.py")   # 资料笔记格式、锚点断链检查，生成概念索引与总目录（每个治理工具各占一行、位置分开，几个 PR 同时合入不冲突）
SELF_TESTED.append("architecture/frameworks/notes_build.py")
STEPS.append("architecture/panorama/object_freeze_check.py")   # 业务对象冻结：逐对象事实与初判（ADR-0059 判据；每个治理工具各占一行、位置分开，几个 PR 同时合入不冲突）
SELF_TESTED.append("architecture/panorama/object_freeze_check.py")

STEPS.append("check_tasks.py")   # GOV-1017-COO-01：任务片段检查与 tasks/TASKS.md 总表
SELF_TESTED.append("check_tasks.py")
SELF_TESTED.append("task_backfill.py")   # 按任务号在提交、分支、PR 里的引用给出状态补登建议（只写建议，不改片段）
SELF_TESTED.append("build_all.py")


def snapshot(base=BASE):
    files = subprocess.run(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", ".", GLOSSARY_INDEX],
                           cwd=base, capture_output=True, text=True, check=True).stdout.split("\0")
    return {name: hashlib.sha256(Path(base, name).read_bytes()).hexdigest() if Path(base, name).is_file() else None
            for name in files if name and name != "tasks/TASKS.md"}


# 不入仓的生成产物（相对 docs/modernization）：--check 要求它们没有被跟踪（.gitignore 对已跟踪文件无效，合并冲突时「保留对方版本」会让它们悄悄回到库里），且生成之后确实存在
UNTRACKED_PRODUCTS = ("adr/INDEX.md", "adr/README.md", "MODERNIZATION_BRIEF.md", "../../backend/glossary/proposed/README.md", "architecture/frameworks/CONCEPT_INDEX.md",
                      "architecture/frameworks/NOTES_INDEX.md", "architecture/panorama/FREEZE_FACTS.md", "architecture/panorama/FREEZE_FACTS.yaml", "architecture/panorama/FREEZE_IMPACT.md", "architecture/panorama/panorama.yaml", "architecture/panorama/GAPS.md",
                      "architecture/ssot/11-commands/commands.yaml", "architecture/ssot/11-commands/GAPS.md")


def check_untracked_products(base=BASE, products=UNTRACKED_PRODUCTS, adr_glob="adr/ADR-*.md"):
    problems = []
    tracked = subprocess.run(["git", "ls-files", "-ci", "--exclude-standard", "-z"], cwd=base, capture_output=True, text=True, check=True).stdout.split("\0")
    problems += [f"{name} 是生成产物（.gitignore），却被跟踪：git rm --cached 它，保留源文件的改动" for name in tracked if name]
    problems += [f"{name} 生成之后不存在：生成步骤没有写出它" for name in products if not Path(base, name).is_file()]
    if not list(Path(base).glob(adr_glob)):
        problems.append(f"{adr_glob} 生成之后一个都没有：build_adrs.py 没有写出 ADR 拷贝")
    if problems:
        sys.exit("不入仓的生成产物不对：\n" + "\n".join(problems))
    print("check: 不入仓的生成产物没有被跟踪，且都已生成")


def run_steps(steps, base=BASE):
    for step in steps:
        r = subprocess.run([sys.executable, os.path.join(base, step)], cwd=base, capture_output=True, text=True)
        if r.returncode:
            sys.exit(f"{step} 失败：\n{r.stdout}{r.stderr}")
        print(f"{step}: {r.stdout.strip().splitlines()[0] if r.stdout.strip() else 'ok'}")
        if r.stderr.strip():
            print(r.stderr.strip(), file=sys.stderr)


def generate(check, base=BASE, steps=STEPS):
    before = snapshot(base) if check else None
    run_steps(steps, base)
    if not check:
        return
    task_table = Path(base, "tasks/TASKS.md")
    first = task_table.read_bytes()
    run_steps(["check_tasks.py"], base)
    if task_table.read_bytes() != first:
        sys.exit("tasks/TASKS.md 连续两次生成内容不一致")
    after = snapshot(base)
    dirty = "\n".join(sorted(name for name in before.keys() | after.keys() if before.get(name) != after.get(name)))
    if dirty:
        sys.exit("生成产物与片段不一致（请运行 build_all.py 后重新检查，不要直接编辑生成产物）：\n" + dirty)
    print("check: 入仓产物重新生成前后无差异；TASKS.md 连续两次生成一致")


def self_test():
    import contextlib, io, shutil
    with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
        root = Path(tmp)
        base = root / "docs/modernization"
        (base / "inputs").mkdir(parents=True)
        (base / "tasks").mkdir()
        (base / "glossary").mkdir()
        (base / "glossary/bcs.yaml").write_text("bcs: []\n", encoding="utf-8")
        (root / ".gitignore").write_text("docs/modernization/tasks/TASKS.md\n", encoding="utf-8")
        shutil.copyfile(Path(BASE, "check_tasks.py"), base / "check_tasks.py")
        (base / "build_index.py").write_text(
            "from pathlib import Path\np = Path('.')\n"
            "(p / 'README.md').write_text(''.join(f.read_text() for f in sorted((p / 'inputs').glob('*.txt'))))\n", encoding="utf-8")
        (base / "inputs/a.txt").write_text("one", encoding="utf-8")
        subprocess.run(["git", "init", "-q", str(root)], check=True, capture_output=True)
        steps = ["build_index.py", "check_tasks.py"]
        generate(False, str(base), steps)
        subprocess.run(["git", "-C", str(root), "add", "."], check=True, capture_output=True)
        def checked_failure(message):
            try:
                generate(True, str(base), steps)
            except SystemExit as error:
                assert message in str(error), str(error)
            else:
                raise AssertionError(f"应失败：{message}")
        (base / "README.md").write_text("manual edit", encoding="utf-8")
        checked_failure("生成产物与片段不一致")
        (base / "README.md").unlink()
        checked_failure("生成产物与片段不一致")
        (base / "inputs/b.txt").write_text("two", encoding="utf-8")
        checked_failure("生成产物与片段不一致")
        generate(True, str(base), steps)
        (base / "tasks/TASKS.md").unlink()
        generate(True, str(base), steps)
        (base / "tasks/TASKS.md").write_text("manual table", encoding="utf-8")
        generate(True, str(base), steps)
        with (base / "check_tasks.py").open("a", encoding="utf-8") as stream:
            stream.write("\nimport time\n(BASE / 'tasks/TASKS.md').write_text(str(time.time_ns()))\n")
        checked_failure("连续两次生成内容不一致")
        (base / "check_tasks.py").write_text("raise SystemExit('generation failed')\n", encoding="utf-8")
        checked_failure("generation failed")
    with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
        root = Path(tmp)
        git = lambda *args: subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=root, capture_output=True, text=True, check=True)
        git("init", "-q", "-b", "main")
        (root / ".gitignore").write_text("gen.md\nA-*.md\n", encoding="utf-8")
        (root / "gen.md").write_text("g", encoding="utf-8")
        (root / "A-1.md").write_text("a", encoding="utf-8")
        def outcome(**kw):
            try:
                check_untracked_products(root, **kw)
            except SystemExit as error:
                return str(error)
            return "ok"
        assert outcome(products=("gen.md",), adr_glob="A-*.md") == "ok", "已生成且未被跟踪：通过"
        assert "生成之后不存在" in outcome(products=("gen.md", "missing.md"), adr_glob="A-*.md"), "生成步骤没写出：失败"
        assert "一个都没有" in outcome(products=("gen.md",), adr_glob="B-*.md"), "ADR 拷贝一个都没有：失败"
        git("add", "-f", "gen.md")
        git("add", ".gitignore")
        assert "却被跟踪" in outcome(products=("gen.md",), adr_glob="A-*.md"), "被忽略的文件又被跟踪（冲突时保留对方版本）：失败"
    # 快速检查（--changed）：只跑改到的工具自测；与重生成器输入无关的文档改动跳过四个慢生成器；改 build_all.py 全跑；缺产物时不跳过
    with tempfile.TemporaryDirectory() as quick_tmp:
        for name in UNTRACKED_PRODUCTS:
            Path(quick_tmp, name).parent.mkdir(parents=True, exist_ok=True)
            Path(quick_tmp, name).write_text("x")
        all_steps = set(STEPS)
        steps, tests = quick_plan(["docs/modernization/tasks/X.md"], quick_tmp)
        assert not (set(HEAVY_STEPS) & set(steps)) and all_steps - set(HEAVY_STEPS) <= set(steps) and not tests, "只改 tasks/：不跑慢生成器、不跑自测"
        steps, tests = quick_plan(["docs/modernization/architecture/panorama/panorama_build.py"], quick_tmp)
        assert set(HEAVY_STEPS) & all_steps <= set(steps) and tests == ["architecture/panorama/panorama_build.py"], "改了全景构建：慢生成器照跑，只跑它自己的自测"
        steps, tests = quick_plan(["docs/modernization/build_all.py"], quick_tmp)
        assert tests == SELF_TESTED, "改了 build_all.py：自测全跑"
        steps, tests = quick_plan(["docs/modernization/tasks/X.md", "backend/pom.xml"], quick_tmp)
        assert set(HEAVY_STEPS) & all_steps <= set(steps), "混有 docs 之外的改动：不跳过"
        Path(quick_tmp, UNTRACKED_PRODUCTS[0]).unlink()
        steps, tests = quick_plan(["docs/modernization/tasks/X.md"], quick_tmp)
        assert set(HEAVY_STEPS) & all_steps <= set(steps), "产物缺失：不跳过"
    print("build_all --self-test：通过（不入仓产物被跟踪或没生成即失败；手改、删除、漏生成失败；一致未提交产物通过；TASKS 缺失或手改可重建、两次不一致或生成失败被拦截）")


# 快速检查（--changed）：下面四个耗时生成器（实测各慢 5–11 秒）读 docs/modernization 的 architecture/、glossary/、rules/ 以及 backend、frontend 的文件；
# 改动全落在 docs/modernization 里这三个目录之外（tasks/、intent/、sessions/、phase1a/ 等）时，它们的输入没变，可以跳过
HEAVY_INPUT_PREFIXES = ("docs/modernization/architecture/", "docs/modernization/glossary/", "docs/modernization/rules/", "docs/modernization/build_all.py")
HEAVY_STEPS = ("architecture/panorama/panorama_build.py", "glossary/build_terms.py", "architecture/frameworks/notes_build.py", "architecture/panorama/object_freeze_check.py")


def repo_relative(step):
    return os.path.relpath(os.path.join(BASE, step), os.path.join(BASE, "..", "..")).replace(os.sep, "/")


def quick_plan(changed, base=BASE):
    """(要跑的生成步骤, 要跑的自测)：changed 为本次改动的仓库相对路径"""
    changed = set(changed)
    everything = repo_relative("build_all.py") in changed
    self_tests = [t for t in SELF_TESTED if everything or repo_relative(t) in changed]
    light_only = bool(changed) and all(name.startswith("docs/modernization/") and not name.startswith(HEAVY_INPUT_PREFIXES) for name in changed)
    products_present = all(Path(base, name).is_file() for name in UNTRACKED_PRODUCTS)
    steps = [t for t in STEPS if not (light_only and products_present and t in HEAVY_STEPS)]
    return steps, self_tests


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    steps, self_tests = STEPS, SELF_TESTED
    if "--changed" in sys.argv:
        steps, self_tests = quick_plan(Path(sys.argv[sys.argv.index("--changed") + 1]).read_text().split())
        print(f"quick: 生成步骤 {len(steps)}/{len(STEPS)}，自测 {len(self_tests)}/{len(SELF_TESTED)}（完整检查在 CI）")
    generate("--check" in sys.argv, steps=steps)
    if "--check" in sys.argv:
        check_untracked_products()
        for step in self_tests:
            r = subprocess.run([sys.executable, os.path.join(BASE, step), "--self-test"], cwd=BASE, capture_output=True, text=True)
            if r.returncode:
                sys.exit(f"{step} 自测失败：\n{r.stdout}{r.stderr}")
            print(f"{os.path.basename(step)} --self-test: {r.stdout.strip()}")
        merge_base = subprocess.run(["git", "merge-base", "HEAD", "origin/main"], cwd=BASE, capture_output=True, text=True)
        base_args = ["--base", merge_base.stdout.strip()] if merge_base.returncode == 0 and merge_base.stdout.strip() else []
        if not base_args:
            print("警告：取不到 origin/main 的合并基点（无 origin/main、或浅克隆无共同祖先），原结论比对交给 review_revisions.py："
                  "有 origin/main 时回退为它本身作基线（可能误报），否则 CI 失败、本地告警", file=sys.stderr)
        else:
            head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=BASE, capture_output=True, text=True).stdout.strip()
            if base_args[1] == head:
                print("说明：合并基点即 HEAD（在 main 上运行），本步原结论比对不会发现改写；push 的比对由 CI 的专门步骤以 github.event.before 为基线完成")
        r = subprocess.run([sys.executable, "-B", os.path.join(BASE, "rules", "review_revisions.py"), *base_args], cwd=BASE, capture_output=True, text=True)
        if r.stderr.strip():
            print(r.stderr.strip(), file=sys.stderr)
        if r.returncode:
            sys.exit(f"RULE_REVIEWS.json 修订记录校验失败：\n{r.stdout}")
        print(f"rules/review_revisions.py: {r.stdout.strip()}")


if __name__ == "__main__":
    main()
