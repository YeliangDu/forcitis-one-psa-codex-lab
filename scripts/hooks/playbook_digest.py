#!/usr/bin/env python3
import contextlib
import io
from pathlib import Path
import re
import sys
import tempfile


HEADER = "【硬规则摘要（SESSION_PLAYBOOK §11 原文生成，一条一行；全文见文件）】"
WARNING = "【硬规则摘要】告警：无法读取或解析 SESSION_PLAYBOOK §11 编号条目；请手动读全文。"


def summaries(text):
    section = re.search(r"(?ms)^## 11[.\s].*?\n(.*?)(?=^## |\Z)", text)
    if not section:
        return []
    entries, group, fenced = [], "", False
    for line in section.group(1).splitlines():
        if re.match(r"^\s*(```|~~~)", line):
            fenced = not fenced
        if fenced:
            continue
        item = re.match(r"^(\d+)\.\s+(.+)", line)
        if not item:
            heading = re.match(r"^\*\*([^*]+)\*\*", line)
            if heading and not line.startswith("-"):
                group = re.split(r"[（(]", heading.group(1))[0].strip()
            continue
        number, body = item.groups()
        title = re.match(r"\*\*(.+?)\*\*", body)
        first = title.group(1) if title else re.split(r"[。！？]|(?<=[.!?])\s+", body, maxsplit=1)[0]
        first = first.replace("`", "").replace("**", "").strip()
        summary = f"{group}第{number}条：{first}"
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
    print("self-test: ok (7 cases)")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        run(sys.argv[1] if len(sys.argv) == 2 else Path(__file__).resolve().parents[2] / "docs/modernization/SESSION_PLAYBOOK.md")
