#!/usr/bin/env python3
"""新增一条用户决定片段 intent/<日期>-<序号>-<短名>.md，然后重新生成 INTENT.md。

用法：python3 docs/modernization/new_intent.py "<用户原话>" "<落点>" [--date YYYY-MM-DD]
原话与落点各为一行（会成为 INTENT.md 表格的一行），不能含未转义的「|」。
"""
import datetime, os, re, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))


def short_name(text, n=16):
    t = re.sub(r"[`*「」『』（）()\[\]【】<>:：，,。；;、！!？?\"'/\\|·\s]+", "-", text)
    return re.sub(r"-+", "-", t).strip("-")[:n].strip("-") or "x"


def next_seq(folder):
    nums = [int(m.group(1)) for f in os.listdir(folder) if (m := re.match(r"\d{4}-\d{2}-\d{2}-(\d{3})-", f))]
    return max(nums, default=0) + 1


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--date")]
    date = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--date=")), datetime.date.today().isoformat())
    if len(args) != 2:
        sys.exit(__doc__)
    quote, landing = args
    folder = os.path.join(BASE, "intent")
    path = os.path.join(folder, f"{date}-{next_seq(folder):03d}-{short_name(quote)}.md")
    open(path, "x", encoding="utf-8").write(f"## 原话\n\n{quote}\n\n## 落点\n\n{landing}\n")
    print(os.path.relpath(path, BASE))
    subprocess.run([sys.executable, os.path.join(BASE, "build_records.py")], check=True)
