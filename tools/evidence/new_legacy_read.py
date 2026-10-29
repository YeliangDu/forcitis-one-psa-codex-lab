#!/usr/bin/env python3
"""新增一条老系统 / A8 生产数据读取登记片段 legacy-read/<日期>-<HHMMSS>-<会话名>.md（ADR-0037 第 7 项）；片段即正本，#742 起不再生成总表。

用法：python3 tools/evidence/new_legacy_read.py --session=<会话名> "<执行账号>" "<库与表>" "<用途>" "<输出形式与去向>" "<落盘目录与删除时点>" "<依据>" [--date=YYYY-MM-DD]
参数里不要写生产特征（实例号、主机名、生产库名）：生产写门对整条命令做字面匹配，见特征即拒（用户 intent 2026-10-28-202019）。
用法二：new_legacy_read.py --check  校验 legacy-read/ 下全部片段（light.sh 跑）。
六个值各为一行，不能为空、不能含「|」。文件名由日期、当前时刻与会话名组成，并行会话互不撞名。
"""
import datetime, os, re, sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "modernization", "phase1a", "infra")
FIELDS = ("执行账号", "库与表", "用途", "输出形式与去向", "落盘目录与删除时点", "依据")
LINE = re.compile(r"- \*\*([^*]+)\*\*：(.*)")


def parse(path):
    """片段的格式校验：七个字段（日期加 FIELDS）各占一行，缺、多、重复、为空或含「|」都失败；日期须为 YYYY-MM-DD 且是文件名前缀。"""
    fields, seen = ("日期",) + FIELDS, {}
    for number, line in enumerate(open(path, encoding="utf-8").read().splitlines(), 1):
        if not line.strip():
            continue
        m = LINE.fullmatch(line)
        if not m:
            raise ValueError(f"{path}:{number}：每行须为「- **字段**：值」")
        name, value = m.group(1), m.group(2).strip()
        if name not in fields:
            raise ValueError(f"{path}:{number}：未知字段「{name}」，只能是 {'、'.join(fields)}")
        if name in seen:
            raise ValueError(f"{path}:{number}：字段「{name}」重复")
        if not value:
            raise ValueError(f"{path}:{number}：字段「{name}」为空")
        if "|" in value:
            raise ValueError(f"{path}:{number}：字段「{name}」含「|」；改用顿号或分号")
        seen[name] = value
    missing = [f for f in fields if f not in seen]
    if missing:
        raise ValueError(f"{path}：缺少字段 {'、'.join(missing)}")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", seen["日期"]):
        raise ValueError(f"{path}：日期须为 YYYY-MM-DD：{seen['日期']}")
    if not os.path.basename(path).startswith(seen["日期"] + "-"):
        raise ValueError(f"{path}：文件名须以日期 {seen['日期']}- 开头")
    return seen


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
    try:
        parse(path)
    except ValueError as error:
        os.remove(path)
        sys.exit(f"失败（未留下片段）：{error}")
    print(os.path.relpath(path, BASE))


def check_all():
    folder = os.path.join(BASE, "legacy-read")
    names = sorted(n for n in os.listdir(folder) if n.endswith(".md") and not n.startswith("_"))
    for name in names:
        try:
            parse(os.path.join(folder, name))
        except ValueError as error:
            sys.exit(f"老系统读取登记片段不合格：{error}")
    print(f"legacy-read: {len(names)} 条片段格式合格")


if __name__ == "__main__":
    if sys.argv[1:] == ["--check"]:
        check_all()
    else:
        main(sys.argv[1:])
