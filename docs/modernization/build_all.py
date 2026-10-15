#!/usr/bin/env python3
"""统一生成入口：依次生成 INTENT / SESSION_LOG / STATUS、ADR 与 README、brief、提议术语清单（backend/glossary/proposed/README.md）。

用法：
  python3 docs/modernization/build_all.py          生成全部产物
  python3 docs/modernization/build_all.py --check  生成后要求 docs/modernization 与提议术语清单工作区无差异（CI 使用）：
                                                   ADR、README、brief 与提议术语清单与已提交的产物不一致、或有未提交的新产物时失败；
                                                   INTENT / SESSION_LOG / STATUS 不入仓（.gitignore），只要求能生成、片段合规；
                                                   另校验 RULE_REVIEWS.json 的原结论未被改写（与 origin/main 的合并基点比对；取不到时由 review_revisions.py
                                                   按环境处理：CI 失败，本地告警），
                                                   并运行提议术语清单生成器的自测
"""
import os, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))
GLOSSARY_INDEX = os.path.join("..", "..", "backend", "glossary", "proposed", "README.md")
STEPS = ["build_records.py", "adr/build_adrs.py", "rules/build_brief.py", "../../backend/glossary/build_proposed_index.py"]

for step in STEPS:
    r = subprocess.run([sys.executable, os.path.join(BASE, step)], cwd=BASE, capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"{step} 失败：\n{r.stdout}{r.stderr}")
    print(f"{step}: {r.stdout.strip().splitlines()[0] if r.stdout.strip() else 'ok'}")
    if r.stderr.strip():
        print(r.stderr.strip(), file=sys.stderr)

if "--check" in sys.argv:
    r = subprocess.run([sys.executable, os.path.join(BASE, STEPS[-1]), "--self-test"], cwd=BASE, capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"提议术语清单生成器自测失败：\n{r.stdout}{r.stderr}")
    print(f"build_proposed_index.py --self-test: {r.stdout.strip()}")
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
