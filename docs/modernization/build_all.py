#!/usr/bin/env python3
"""统一生成入口：依次生成 INTENT / SESSION_LOG / STATUS、正式术语 glossary/terms.yaml、会话一览 SESSIONS、用语问题清单 glossary/ISSUES、ADR 与 README、brief、
已定决策速查表 DECISION_DIGEST.md（由 intent/ 片段汇总，不入仓）、提议术语清单（backend/glossary/proposed/README.md）、老系统读取登记（phase1a/infra/LEGACY_READ_REGISTER.md，由 legacy-read/ 片段生成，不入仓）。

用法：
  python3 docs/modernization/build_all.py          生成全部产物
  python3 docs/modernization/build_all.py --check  生成后要求 docs/modernization 与提议术语清单工作区无差异（CI 使用）：
                                                   terms.yaml、ADR、README、brief 与提议术语清单与已提交的产物不一致、或有未提交的新产物时失败；
                                                   INTENT / SESSION_LOG / STATUS / DECISION_DIGEST / SESSIONS / glossary/ISSUES / LEGACY_READ_REGISTER 不入仓（.gitignore），只要求能生成、片段合规
                                                   （会话画像必备字段、会话名 one-* 且唯一；用语问题片段格式）；
                                                   另校验 RULE_REVIEWS.json 的原结论未被改写（与 origin/main 的合并基点比对；取不到时由 review_revisions.py
                                                   按环境处理：CI 失败，本地告警），
                                                   并运行提议术语清单、会话一览（含钩子 session_context.py）、用语问题清单生成器的自测
"""
import importlib.util, os, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))
GLOSSARY_INDEX = os.path.join("..", "..", "backend", "glossary", "proposed", "README.md")
STEPS = ["build_records.py", "build_decision_digest.py", "build_sessions.py", "build_work_board.py", "glossary/build_issues.py", "adr/build_adrs.py", "rules/build_brief.py",
         "phase1a/infra/build_legacy_read_register.py", "../../backend/glossary/build_proposed_index.py"]
SELF_TESTED = ["build_decision_digest.py", "build_sessions.py", "build_work_board.py", "../../scripts/queue/merge_train.py", "../../scripts/merge/mq.py", "../../scripts/hooks/session_context.py", "glossary/build_issues.py", "glossary/term_rules.py",
               "phase1a/infra/build_legacy_read_register.py", STEPS[-1]]
SELF_TESTED.append("../../scripts/ci/ci_recurrence.py")   # 每晚复发检测：失败 CI 按错题本报错特征归类
# 正式术语 glossary/terms.yaml 由 build_terms.py 生成，--check 要求可复现；PyYAML 依赖登记于 SUPPLY_CHAIN.md S1
if importlib.util.find_spec("yaml"):
    STEPS.insert(1, "glossary/build_terms.py")
    STEPS.insert(-1, "architecture/build_architecture.py")   # 架构施工图（architecture/DESIGN.md §3）：mmd 可复现、SVG 未过期
    SELF_TESTED.append("architecture/build_architecture.py")   # 上下文映射边校验与 A1
elif "--check" in sys.argv:
    sys.exit(f"失败：{sys.executable} 缺少 PyYAML，无法运行 glossary/build_terms.py 校验 terms.yaml 可复现（依赖见 SUPPLY_CHAIN.md S1）")
else:
    print(f"警告：{sys.executable} 缺少 PyYAML，本次未重新生成 glossary/terms.yaml；--check 会因此失败（依赖见 SUPPLY_CHAIN.md S1）", file=sys.stderr)

for step in STEPS:
    r = subprocess.run([sys.executable, os.path.join(BASE, step)], cwd=BASE, capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"{step} 失败：\n{r.stdout}{r.stderr}")
    print(f"{step}: {r.stdout.strip().splitlines()[0] if r.stdout.strip() else 'ok'}")
    if r.stderr.strip():
        print(r.stderr.strip(), file=sys.stderr)

if "--check" in sys.argv:
    for step in SELF_TESTED:
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
    dirty = subprocess.run(["git", "status", "--porcelain", "--", ".", GLOSSARY_INDEX], cwd=BASE, capture_output=True, text=True, check=True).stdout
    if dirty:
        sys.exit("生成产物与片段不一致（请运行 build_all.py 并提交结果，不要直接编辑生成产物）：\n" + dirty)
    print("check: 生成后工作区无差异")
