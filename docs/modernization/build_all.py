#!/usr/bin/env python3
"""统一生成入口：依次生成 INTENT / SESSION_LOG / STATUS、ADR 与 README、brief。

用法：
  python3 docs/modernization/build_all.py          生成全部产物
  python3 docs/modernization/build_all.py --check  生成后要求 docs/modernization 下工作区无差异（CI 使用）：
                                                   ADR、README 与 brief 与已提交的产物不一致、或有未提交的新产物时失败；
                                                   INTENT / SESSION_LOG / STATUS 不入仓（.gitignore），只要求能生成、片段合规；
                                                   另校验 RULE_REVIEWS.json 的原结论未被改写（与 HEAD 及 origin/main 合并基点比对，git 不可用时只告警）
"""
import os, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))
STEPS = ["build_records.py", "adr/build_adrs.py", "rules/build_brief.py"]

for step in STEPS:
    r = subprocess.run([sys.executable, os.path.join(BASE, step)], cwd=BASE, capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"{step} 失败：\n{r.stdout}{r.stderr}")
    print(f"{step}: {r.stdout.strip().splitlines()[0] if r.stdout.strip() else 'ok'}")
    if r.stderr.strip():
        print(r.stderr.strip(), file=sys.stderr)

if "--check" in sys.argv:
    r = subprocess.run([sys.executable, os.path.join(BASE, "rules", "review_revisions.py")], cwd=BASE, capture_output=True, text=True)
    if r.stderr.strip():
        print(r.stderr.strip(), file=sys.stderr)
    if r.returncode:
        sys.exit(f"RULE_REVIEWS.json 修订记录校验失败：\n{r.stdout}")
    print(f"rules/review_revisions.py: {r.stdout.strip()}")
    dirty = subprocess.run(["git", "status", "--porcelain", "--", "."], cwd=BASE, capture_output=True, text=True, check=True).stdout
    if dirty:
        sys.exit("生成产物与片段不一致（请运行 build_all.py 并提交结果，不要直接编辑生成产物）：\n" + dirty)
    print("check: 生成后工作区无差异")
