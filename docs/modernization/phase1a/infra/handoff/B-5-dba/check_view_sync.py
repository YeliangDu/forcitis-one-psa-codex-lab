#!/usr/bin/env python3
"""B-5 交接脚本中的视图正文必须与 phase1a 下的正本逐字一致。

用法：python3 docs/modernization/phase1a/infra/handoff/B-5-dba/check_view_sync.py   不一致时列出并以 1 退出
"""
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
PHASE1A = HERE.parents[2]
VIEW = re.compile(r"^CREATE VIEW\s+(\S+).*?^GO$", re.S | re.M)

PAIRS = [
    (PHASE1A / "SEEYON_VIEWS.sql", HERE / "a8_10_apply.sql"),
    (PHASE1A / "LEGACY_INBOX_VIEW.sql", HERE / "legacy_10_apply.sql"),
    (PHASE1A / "LEGACY_ORG_NAMES_VIEW.sql", HERE / "legacy_10_apply.sql"),
]


def views(path):
    return {m.group(1): m.group(0) for m in VIEW.finditer(path.read_text(encoding="utf-8"))}


def main():
    problems = []
    for source, handoff in PAIRS:
        wanted, actual = views(source), views(handoff)
        if not wanted:
            problems.append(f"{source.name}：没有找到任何 CREATE VIEW")
        for name, body in wanted.items():
            if name not in actual:
                problems.append(f"{handoff.name} 缺少 {name}（正本 {source.name}）")
            elif actual[name] != body:
                problems.append(f"{handoff.name} 中 {name} 与正本 {source.name} 不一致")
    for p in problems:
        print(p)
    if problems:
        sys.exit(1)
    print("B-5 视图正文与正本一致")


if __name__ == "__main__":
    main()
