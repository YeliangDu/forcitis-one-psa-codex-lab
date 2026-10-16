#!/usr/bin/env python3
"""从 legacy-read/ 下的片段生成 LEGACY_READ_REGISTER.md（老系统与 A8 生产数据读取登记，ADR-0037 第 7 项）。

每次读取一个片段 legacy-read/<日期>-<HHMMSS>-<会话名>.md（用 new_legacy_read.py 新增）：
  - **日期**：YYYY-MM-DD
  - **执行账号**：…
  - **库与表**：…
  - **用途**：…
  - **输出形式与去向**：…
  - **落盘目录与删除时点**：…
  - **依据**：…
七个字段各占一行，缺一个、多一个、重复、为空或含「|」都失败。总表不入仓（.gitignore）：多个会话同时追加同一张表会冲突，
片段互不相同名；总表按 日期、文件名 排序，开头说明取自 legacy-read/_intro.md。
用法：build_legacy_read_register.py  |  build_legacy_read_register.py --self-test
"""
import os, re, sys, tempfile

BASE = os.path.dirname(os.path.abspath(__file__))
FIELDS = ("日期", "执行账号", "库与表", "用途", "输出形式与去向", "落盘目录与删除时点", "依据")
LINE = re.compile(r"- \*\*([^*]+)\*\*：(.*)")


def parse(path):
    seen = {}
    for number, line in enumerate(open(path, encoding="utf-8").read().splitlines(), 1):
        if not line.strip():
            continue
        m = LINE.fullmatch(line)
        if not m:
            raise ValueError(f"{path}:{number}：每行须为「- **字段**：值」")
        name, value = m.group(1), m.group(2).strip()
        if name not in FIELDS:
            raise ValueError(f"{path}:{number}：未知字段「{name}」，只能是 {'、'.join(FIELDS)}")
        if name in seen:
            raise ValueError(f"{path}:{number}：字段「{name}」重复")
        if not value:
            raise ValueError(f"{path}:{number}：字段「{name}」为空")
        if "|" in value:
            raise ValueError(f"{path}:{number}：字段「{name}」含「|」，会破坏总表；改用顿号或分号")
        seen[name] = value
    missing = [f for f in FIELDS if f not in seen]
    if missing:
        raise ValueError(f"{path}：缺少字段 {'、'.join(missing)}")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", seen["日期"]):
        raise ValueError(f"{path}：日期须为 YYYY-MM-DD：{seen['日期']}")
    if not os.path.basename(path).startswith(seen["日期"] + "-"):
        raise ValueError(f"{path}：文件名须以日期 {seen['日期']}- 开头")
    return seen


def build(folder):
    names = sorted(n for n in os.listdir(folder) if n.endswith(".md") and not n.startswith("_"))
    rows = [parse(os.path.join(folder, n)) for n in names]
    intro = open(os.path.join(folder, "_intro.md"), encoding="utf-8").read().rstrip("\n")
    table = ["| " + " | ".join(FIELDS) + " |", "|" + "---|" * len(FIELDS)]
    table += ["| " + " | ".join(r[f] for f in FIELDS) + " |" for r in rows]
    return f"{intro}\n\n" + "\n".join(table) + "\n", len(rows)


def self_test():
    with tempfile.TemporaryDirectory() as folder:
        good = "\n".join(f"- **{f}**：{'2026-10-16' if f == '日期' else f + '的值'}" for f in FIELDS) + "\n"
        open(os.path.join(folder, "_intro.md"), "w", encoding="utf-8").write("# 标题\n\n说明\n")
        open(os.path.join(folder, "2026-10-16-100000-a.md"), "w", encoding="utf-8").write(good)
        open(os.path.join(folder, "2026-10-16-090000-b.md"), "w", encoding="utf-8").write(good.replace("用途的值", "早的"))
        text, count = build(folder)
        lines = text.splitlines()
        assert count == 2 and lines[-1].count("|") == 8 and "早的" in lines[-2] and "早的" not in lines[-1], text
        for broken, why in ((good.replace("- **依据**：依据的值\n", ""), "缺字段"), (good + "- **用途**：再来一个\n", "重复字段"),
                            (good.replace("用途的值", "含|竖线"), "含竖线"), (good.replace("依据的值", " "), "空值"),
                            (good.replace("2026-10-16", "10-16", 1), "日期格式"), (good + "- **多余**：x\n", "未知字段"),
                            (good + "随手写的一行\n", "行格式")):
            open(os.path.join(folder, "2026-10-16-110000-bad.md"), "w", encoding="utf-8").write(broken)
            try:
                build(folder)
            except ValueError:
                continue
            raise AssertionError(f"应当失败却通过：{why}")
        os.remove(os.path.join(folder, "2026-10-16-110000-bad.md"))
        open(os.path.join(folder, "2026-10-17-100000-wrongdate.md"), "w", encoding="utf-8").write(good)
        try:
            build(folder)
        except ValueError:
            pass
        else:
            raise AssertionError("文件名日期与字段日期不一致应当失败")
    print("legacy read register self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    try:
        text, count = build(os.path.join(BASE, "legacy-read"))
    except ValueError as error:
        sys.exit(f"失败：{error}")
    open(os.path.join(BASE, "LEGACY_READ_REGISTER.md"), "w", encoding="utf-8").write(text)
    print(f"LEGACY_READ_REGISTER.md: {count} 条")
