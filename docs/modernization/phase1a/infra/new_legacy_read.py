#!/usr/bin/env python3
"""新增一条老系统 / A8 生产数据读取登记片段 legacy-read/<日期>-<HHMMSS>-<会话名>.md，然后重新生成 LEGACY_READ_REGISTER.md。

用法：python3 docs/modernization/phase1a/infra/new_legacy_read.py --session=<会话名> "<执行账号>" "<库与表>" "<用途>" "<输出形式与去向>" "<落盘目录与删除时点>" "<依据>" [--date=YYYY-MM-DD]
六个值各为一行，不能含「|」。文件名由日期、当前时刻与会话名组成，并行会话互不撞名。
"""
import datetime, os, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))
FIELDS = ("执行账号", "库与表", "用途", "输出形式与去向", "落盘目录与删除时点", "依据")


def main(argv):
    opts = dict(a[2:].split("=", 1) for a in argv if a.startswith("--") and "=" in a)
    values = [a for a in argv if not a.startswith("--")]
    if len(values) != len(FIELDS) or not opts.get("session"):
        sys.exit(__doc__)
    date = opts.get("date", datetime.date.today().isoformat())
    text = f"- **日期**：{date}\n" + "".join(f"- **{f}**：{v}\n" for f, v in zip(FIELDS, values))
    now = datetime.datetime.now()
    while True:
        path = os.path.join(BASE, "legacy-read", f"{date}-{now:%H%M%S}-{opts['session']}.md")
        try:
            open(path, "x", encoding="utf-8").write(text)
            break
        except FileExistsError:
            now += datetime.timedelta(seconds=1)
    print(os.path.relpath(path, BASE))
    subprocess.run([sys.executable, os.path.join(BASE, "build_legacy_read_register.py")], check=True)


if __name__ == "__main__":
    main(sys.argv[1:])
