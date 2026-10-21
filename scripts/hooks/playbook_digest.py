#!/usr/bin/env python3
import contextlib
import io
from pathlib import Path
import re
import subprocess
import sys
import tempfile


GENERATED_HEADER = "<!-- 由 scripts/hooks/playbook_digest.py 从 SESSION_PLAYBOOK.md 生成，docs/modernization/build_all.py 写出；请勿手改。 -->"
HEADER = "【SESSION_PLAYBOOK §2.1、§5、§11 摘要（会话管理、Codex 派工、任务命名与登记、调度职责、消息约定、战队协作；原文生成，一条一行；全文见文件）】"
WARNING = "【SESSION_PLAYBOOK §2.1、§5、§11 摘要】告警：无法读取或解析条目；请手动读全文。"
SECTION21_WARNING = "【SESSION_PLAYBOOK §2.1 摘要】告警：无法读取或解析会话管理编号条目；请手动读全文。"
SECTION11_WARNING = "【SESSION_PLAYBOOK §11 摘要】告警：无法读取或解析编号条目；请手动读全文。"


def section5_summaries(text):
    section = re.search(r"(?ms)^## 5[.\s].*?\n(.*?)(?=^## |\Z)", text)
    lines, fenced = [], False
    for line in (section.group(1) if section else "").splitlines():
        if re.match(r"^\s*(```|~~~)", line):
            fenced = not fenced
            continue
        if not fenced:
            lines.append(line)
    raw_blocks = {
        match.group(1): match.group(2)
        for match in re.finditer(
            r"(?ms)^[-+*]\s+\*\*([^*\n]+)\*\*[^\n]*\n(.*?)(?=^[-+*]\s|\Z)", "\n".join(lines)
        )
    }
    blocks = {title: " ".join(body.split()) for title, body in raw_blocks.items()}
    rules = [
        ("Codex 派工", "默认先交 Codex", [r"(默认先交 Codex[：:].+?)(?=——|[。；;]|$)"], "{0}"),
        ("Codex 派工", "run_in_background", [
            r"(一律用[^。；;]*?)的\s*(`run_in_background`[^。；;]*?启动)[，,]\s*(在[^。；;]*?后台任务[^。；;]*?可见)",
            r"(不得用[^。；;]*?脱离会话)",
        ], "{0} {1}；{2}；{3}"),
        ("Codex 派工", "< /dev/null", [
            r"`(codex exec)\b[^`]*?(<\s*/dev/null)\s*`[（(]([^，,。；;）)]+)",
        ], "{0} … {1}；{2}"),
        ("任务命名与登记", "任务号、任务名、后台任务显示、登记片段", [
            r"(每张卡).*?(一个)\*\*(任务号)\*\*\s*`([^`]+)`",
            r"\*\*(任务名)\*\*[：:]\s*`([^`]+)`",
            r"\*\*(后台任务显示)\*\*[：:].*?description 写\s*`([^`]+)`",
            r"\*\*(登记)\*\*[：:].*?在\s*`([^`]*/tasks/[^`]+)`\s*(写一个片段)",
        ], "{0}{1}{2} {3}；{4}：{5}\n{6}：{7}；{8}：{9} {10}"),
    ]
    entries = []
    for scope, key, patterns, template in rules:
        matches = [re.search(pattern, blocks.get(scope, "")) for pattern in patterns]
        if not all(matches):
            entries.append(f"【SESSION_PLAYBOOK §5 摘要】告警：无法解析「{scope}·{key}」；请手动读全文。")
            continue
        parts = [part.strip() for match in matches for part in match.groups()]
        for line in template.format(*parts).splitlines():
            append_section5(entries, scope, key, line)
    items = [item.strip().removesuffix("。").strip() for item in re.split(r"[；;]", excluded_text(raw_blocks.get("Codex 派工", "")))]
    items = [] if items == [""] else items
    if not items or not all(items):
        entries.append("【SESSION_PLAYBOOK §5 摘要】告警：无法解析「Codex 派工·不交 Codex 清单」；请手动读全文。")
    else:
        for item in items:
            append_section5(entries, "Codex 派工", "不交 Codex 清单", f"不交 Codex：{item}")
    return entries


def excluded_text(body):
    """The text of the `不交 Codex：` sub-item on its own lines, plus its indented continuation lines; ` - ` or ` + ` inside the text do not end it."""
    lines = body.splitlines()
    for index, line in enumerate(lines):
        found = re.match(r"^\s*[-+*]\s+不交 Codex[：:](.*)$", line)
        if found:
            text = [found.group(1).strip()]
            for follow in lines[index + 1:]:
                if not follow.strip() or re.match(r"^\s*[-+*]\s", follow) or not follow[0].isspace():
                    break
                text.append(follow.strip())
            return " ".join(text)
    return ""


def append_section5(entries, scope, key, text):
    summary = f"{scope}：{text}".replace("`", "").replace("**", "")
    entries.append(summary if len(summary) <= 80 else summary[:79] + "…")
    if len(summary) > 80:
        entries.append(f"【SESSION_PLAYBOOK §5 摘要】告警：「{key}」超过80字，已截断；请手动读全文。")


def summaries(text):
    section = re.search(r"(?ms)^## 11[.\s].*?\n(.*?)(?=^## |\Z)", text)
    if not section:
        return []
    return numbered_summaries(section.group(1))


def numbered_summaries(section):
    entries, group, parents, fenced = [], "", [], False
    for line in section.splitlines():
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


def section21_summaries(text):
    section = re.search(r"(?ms)^### 2\.1 会话管理[^\n]*\n(.*?)(?=^#{1,3} |\Z)", text)
    if not section:
        return [SECTION21_WARNING]
    entries = numbered_summaries(section.group(1))
    numbers = [int(match.group(1)) for line in entries if (match := re.match(r"第(\d+)条：", line))]
    for number in range(1, max([16, *numbers]) + 1):
        if numbers.count(number) != 1:
            entries.append(f"【SESSION_PLAYBOOK §2.1 摘要】告警：第{number}条缺失或重复；请手动读全文。")
    return entries


def render_common(text):
    sections = [("§2.1 会话管理", section21_summaries(text)),
                ("§5 上下文管理", section5_summaries(text)),
                ("§11 总调度会话", summaries(text) or [SECTION11_WARNING])]
    return GENERATED_HEADER + "\n\n" + HEADER + "\n\n" + "\n\n".join(
        f"## {title}\n\n" + "\n".join(entries) for title, entries in sections) + "\n"


def write_common(source, output, check=False):
    contents = [render_common(Path(source).read_text(encoding="utf-8")) for _ in range(2 if check else 1)]
    for content in contents:
        if "告警：" in content:
            raise ValueError("摘要抽取告警，拒绝生成：\n" + "\n".join(line for line in content.splitlines() if "告警：" in line))
    if check:
        if contents[0] != contents[1]:
            raise ValueError("sessions/_COMMON.md 两次生成内容不一致")
    Path(output).write_text(contents[0], encoding="utf-8")


def run(path):
    try:
        text = Path(path).read_text(encoding="utf-8")
        print(render_common(text), end="")
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
            assert out.getvalue().splitlines() == [GENERATED_HEADER, "", HEADER, "", "## §2.1 会话管理", "",
                SECTION21_WARNING, "", "## §5 上下文管理", "", *section5_summaries(""), "", "## §11 总调度会话", "",
                *(expected or [SECTION11_WARNING])]
            assert all(len(line) <= 80 for line in expected)
        path.unlink()
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            run(path)
        assert out.getvalue().splitlines() == [WARNING]
    assert HEADER == "【SESSION_PLAYBOOK §2.1、§5、§11 摘要（会话管理、Codex 派工、任务命名与登记、调度职责、消息约定、战队协作；原文生成，一条一行；全文见文件）】"
    real = Path(__file__).resolve().parents[2] / "docs/modernization/SESSION_PLAYBOOK.md"
    actual = summaries(real.read_text(encoding="utf-8"))
    assert actual and any(line.startswith("战队协作第") for line in actual), "真实 §11 必须能解析且含战队协作组"
    result = subprocess.run([sys.executable, "-B", "-I", str(Path(__file__).with_name("playbook_digest_selftest.py")), "--self-test"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    print(f"self-test: ok (原有 {len(cases) + 2} 项；真实 §5 四条及不交清单、§11；固定样例；§5 固定样例破坏验证；公共摘要、可复现性与失败汇总自测)")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        run(sys.argv[1] if len(sys.argv) == 2 else Path(__file__).resolve().parents[2] / "docs/modernization/SESSION_PLAYBOOK.md")
