#!/usr/bin/env python3
"""新增一条老系统 / A8 生产数据读取登记片段 legacy-read/<日期>-<HHMMSS>-<会话名>.md，然后重新生成 LEGACY_READ_REGISTER.md。

用法：python3 docs/modernization/phase1a/infra/new_legacy_read.py --session=<会话名> "<执行账号>" "<库与表>" "<用途>" "<输出形式与去向>" "<落盘目录与删除时点>" "<依据>" [--date=YYYY-MM-DD]
参数里有生产实例号时，生产写门只认登记的写法：/usr/bin/python3 -I -S docs/modernization/phase1a/infra/new_legacy_read.py …（scripts/gates/prod_targets.json 的 trusted_entries）。
六个值各为一行，不能含「|」。文件名由日期、当前时刻与会话名组成，并行会话互不撞名。
"""
import datetime, importlib.util, os, re, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))
FIELDS = ("执行账号", "库与表", "用途", "输出形式与去向", "落盘目录与删除时点", "依据")


def main(argv):
    opts = dict(a[2:].split("=", 1) for a in argv if a.startswith("--") and "=" in a)
    values = [a for a in argv if not a.startswith("--")]
    if len(values) != len(FIELDS) or not opts.get("session"):
        sys.exit(__doc__)
    date = opts.get("date", datetime.date.today().isoformat())
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date) or not re.fullmatch(r"[A-Za-z0-9._-]+", opts["session"]):
        sys.exit("日期须为 YYYY-MM-DD，会话名只能含字母、数字、点、下划线、连字符")
    text = f"- **日期**：{date}\n" + "".join(f"- **{f}**：{v}\n" for f, v in zip(FIELDS, values))
    now = datetime.datetime.now()
    while True:
        path = os.path.join(BASE, "legacy-read", f"{date}-{now:%H%M%S}-{opts['session']}.md")
        try:
            open(path, "x", encoding="utf-8").write(text)
            break
        except FileExistsError:
            now += datetime.timedelta(seconds=1)
    spec = importlib.util.spec_from_file_location("register", os.path.join(BASE, "build_legacy_read_register.py"))
    register = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(register)
    try:
        register.parse(path)
    except ValueError as error:
        os.remove(path)
        sys.exit(f"失败（未留下片段）：{error}")
    print(os.path.relpath(path, BASE))
    subprocess.run([sys.executable, *(["-I", "-S"] if sys.flags.isolated else []), os.path.join(BASE, "build_legacy_read_register.py")], check=True)  # the child as isolated as this run


if __name__ == "__main__":
    main(sys.argv[1:])
