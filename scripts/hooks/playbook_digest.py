#!/usr/bin/env python3
import contextlib
import io
from pathlib import Path
import re
import sys
import tempfile


HEADER = "【SESSION_PLAYBOOK §11 摘要（调度职责、消息约定、战队协作；原文生成，一条一行；全文见文件）】"
WARNING = "【SESSION_PLAYBOOK §11 摘要】告警：无法读取或解析编号条目；请手动读全文。"


def summaries(text):
    section = re.search(r"(?ms)^## 11[.\s].*?\n(.*?)(?=^## |\Z)", text)
    if not section:
        return []
    entries, group, parents, fenced = [], "", [], False
    for line in section.group(1).splitlines():
        if re.match(r"^\s*(```|~~~)", line):
            fenced = not fenced
        if fenced:
            continue
        item = re.match(r"^(\s*)(\d+)\.\s+(.+)", line)
        if not item:
            heading = re.match(r"^(\s*)([-+*]\s+)?\*\*([^*]+)\*\*", line)
            bullet = re.match(r"^(\s*)[-+*]\s+", line)
            if bullet:
                indent = len(bullet.group(1))
                parents = [(level, name) for level, name in parents if level < indent]
            if heading:
                indent, marker, title = heading.groups()
                name = re.split(r"[（(]", title)[0].strip()
                if not indent and not marker:
                    group, parents = name, []
                else:
                    parents = [(level, name) for level, name in parents if level < len(indent)]
                    parents.append((len(indent), name))
            continue
        indent, number, body = item.groups()
        parents = [(level, name) for level, name in parents if level < len(indent)]
        if indent and not parents and group != "消息约定":
            continue
        title = re.match(r"\*\*(.+?)\*\*", body)
        first = title.group(1) if title else re.split(r"[。！？]|(?<=[.!?])\s+", body, maxsplit=1)[0]
        first = first.replace("`", "").replace("**", "").strip()
        scope = "·".join([name for name in [group, *(name for _, name in parents)] if name])
        summary = f"{scope}第{number}条：{first}"
        entries.append(summary if len(summary) <= 80 else summary[:79] + "…")
    return entries


def run(path):
    try:
        lines = summaries(Path(path).read_text(encoding="utf-8"))
        if not lines:
            raise ValueError()
        print(HEADER)
        print("\n".join(lines))
    except Exception:
        print(WARNING)


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "SESSION_PLAYBOOK.md"
        cases = [
            ("1. **标题**：正文。", ["第1条：标题"]),
            ("2. 读取 `SESSION_LOG.md`。第二句。", ["第2条：读取 SESSION_LOG.md"]),
            ("3. **主条目**：正文\n  1. 子列表\n| 项 | 值 |\n| 4. 表格 | 值 |\n```\n5. 代码\n```", ["第3条：主条目"]),
            ("4. " + "长" * 100 + "。下一句。", ["第4条：" + "长" * 75 + "…"]),
            ("**职责**\n1. 开场：读文件\n\n**战队协作**（用户采纳）\n1. **待决定收件箱**：正文\n- 第一行：「派工」\n2. 直连", ["职责第1条：开场：读文件", "战队协作第1条：待决定收件箱", "战队协作第2条：直连"]),
            ("没有编号条目", []),
            ("**消息约定**（说明）\n  1. 缩进条目。下一句。\n- **少叫醒**（说明）\n  1. 只在需要时发消息。\n  2. 不回纯确认。",
             ["消息约定第1条：缩进条目", "消息约定·少叫醒第1条：只在需要时发消息", "消息约定·少叫醒第2条：不回纯确认"]),
            ("**消息约定**\n- **少叫醒（说明）**\n  - **嵌套**（说明）\n    1. **深层标题**：正文\n  2. 回到上层。\n- 普通条目\n  3. 不沿用前一个粗体组。\n**战队协作**\n1. 另一组。",
             ["消息约定·少叫醒·嵌套第1条：深层标题", "消息约定·少叫醒第2条：回到上层", "消息约定第3条：不沿用前一个粗体组", "战队协作第1条：另一组"]),
        ]
        for body, expected in cases:
            path.write_text("## 10. 前节\n9. 不收录\n## 11. 调度\n" + body + "\n## 12. 后节\n8. 不收录\n", encoding="utf-8")
            assert summaries(path.read_text(encoding="utf-8")) == expected
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                run(path)
            assert out.getvalue().splitlines() == ([HEADER, *expected] if expected else [WARNING])
            assert all(len(line) <= 80 for line in expected)
        path.unlink()
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            run(path)
        assert out.getvalue().splitlines() == [WARNING]
    assert HEADER == "【SESSION_PLAYBOOK §11 摘要（调度职责、消息约定、战队协作；原文生成，一条一行；全文见文件）】"
    real = Path(__file__).resolve().parents[2] / "docs/modernization/SESSION_PLAYBOOK.md"
    actual = summaries(real.read_text(encoding="utf-8"))
    assert actual and any(line.startswith("战队协作第") for line in actual), "真实 §11 必须能解析且含战队协作组"
    print(f"self-test: ok ({len(cases) + 2} cases, including real SESSION_PLAYBOOK §11)")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        run(sys.argv[1] if len(sys.argv) == 2 else Path(__file__).resolve().parents[2] / "docs/modernization/SESSION_PLAYBOOK.md")
