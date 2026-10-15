#!/usr/bin/env python3
"""写一条用语问题的处理片段 glossary/issues/<问题文件名去 .md>.resolution.md（one-glossary 用），然后重新生成 glossary/ISSUES.md。

用法：python3 docs/modernization/glossary/resolve_term_issue.py <问题文件名> <已预判|待用户|已关闭> "<处理：预判结论与依据，或去向>"
问题片段只增不改；处理片段一条问题一份，记当前状态，状态变化时覆盖它。处理为一行，不能含未转义的「|」。
"""
import os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from glossary.build_issues import ISSUES, RESOLUTION, STATES  # noqa: E402

if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    issue, state, note = os.path.basename(sys.argv[1]), sys.argv[2], sys.argv[3].strip()
    if not issue.endswith(".md") or issue.endswith(".resolution.md") or not os.path.isfile(os.path.join(ISSUES, issue)):
        sys.exit(f"找不到问题片段 glossary/issues/{issue}\n\n{__doc__}")
    if state not in STATES or not note or "\n" in note or "|" in note:
        sys.exit(__doc__)
    path = os.path.join(ISSUES, issue[:-3] + ".resolution.md")
    open(path, "w", encoding="utf-8").write(RESOLUTION.format(state, note))
    print(os.path.relpath(path, os.path.dirname(HERE)))
    subprocess.run([sys.executable, os.path.join(HERE, "build_issues.py")], check=True)
