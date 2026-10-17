#!/usr/bin/env python3
import contextlib
import io
from pathlib import Path
import re
import sys
import tempfile


HEADER = "【SESSION_PLAYBOOK §5、§11 摘要（Codex 派工、任务命名与登记、调度职责、消息约定、战队协作；原文生成，一条一行；全文见文件）】"
WARNING = "【SESSION_PLAYBOOK §5、§11 摘要】告警：无法读取或解析条目；请手动读全文。"
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
    blocks = {
        match.group(1): " ".join(match.group(2).split())
        for match in re.finditer(
            r"(?ms)^[-+*]\s+\*\*([^*\n]+)\*\*[^\n]*\n(.*?)(?=^[-+*]\s|\Z)", "\n".join(lines)
        )
    }
    rules = [
        ("Codex 派工", "默认先交 Codex", [r"(默认先交 Codex[：:].+?)(?=——|[。；;]|$)"], "{0}"),
        ("Codex 派工", "run_in_background", [
            r"(一律用[^。；;]*?`run_in_background`[^。；;]*?启动)[，,]\s*(在[^。；;]*?后台任务[^。；;]*?可见)",
            r"(不得用[^。；;]*?脱离会话)",
        ], "{0}；{1}；{2}"),
        ("Codex 派工", "< /dev/null", [
            r"`(codex exec)\b[^`]*?(<\s*/dev/null)\s*`[（(]([^，,。；;）)]+)",
        ], "{0} … {1}；{2}"),
        ("任务命名与登记", "任务号、任务名、后台任务显示、登记片段", [
            r"(每张卡).*?(一个)\*\*(任务号)\*\*",
            r"\*\*(任务名)\*\*[：:]\s*`([^`]+)`",
            r"\*\*(后台任务显示)\*\*[：:].*?description 写\s*`([^`]+)`",
            r"\*\*(登记)\*\*[：:].*?(写一个片段)",
        ], "{0}{1}{2}；{3}：{4}；{5}：{6}；{7}：{8}"),
    ]
    entries = []
    for scope, key, patterns, template in rules:
        matches = [re.search(pattern, blocks.get(scope, "")) for pattern in patterns]
        if not all(matches):
            entries.append(f"【SESSION_PLAYBOOK §5 摘要】告警：无法解析「{scope}·{key}」；请手动读全文。")
            continue
        parts = [part for match in matches for part in match.groups()]
        summary = f"{scope}：{template.format(*parts)}".replace("`", "").replace("**", "")
        entries.append(summary if len(summary) <= 80 else summary[:79] + "…")
    return entries


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
        text = Path(path).read_text(encoding="utf-8")
        print(HEADER)
        print("\n".join(section5_summaries(text)))
        print("\n".join(summaries(text) or [SECTION11_WARNING]))
    except Exception:
        print(WARNING)


def assert_section5(text):
    actual = section5_summaries(text)
    required = [
        ("默认先交 Codex", "凡结果能由 Claude 审出对错", "先交 Codex"),
        ("一律用会话 Bash", "run_in_background", "启动", "后台任务里可见", "不得用", "nohup", "&", "setsid", "脱离会话"),
        ("codex exec", "< /dev/null", "必须关闭标准输入"),
        ("每张卡一个任务号", "任务名", "[主线·bc]", "后台任务显示", "<执行者>", "登记", "写一个片段"),
    ]
    assert len(actual) == 4, actual
    for line, words in zip(actual, required):
        assert "告警" not in line and all(word in line for word in words), line
        assert len(line) <= 80 and "\n" not in line, line
    return actual


def section5_mutation_test(text):
    actual = assert_section5(text)
    mutations = [
        ("**Codex 派工**", "**Codex 调用**", {0, 1, 2}),
        ("默认先交 Codex：", "可选 Codex：", {0}),
        ("一律用会话 Bash 的 `run_in_background` 启动", "", {1}),
        ("不得用 nohup、`&`、setsid 等脱离会话", "", {1}),
        ("< /dev/null", "", {2}),
        ("**任务命名与登记**", "**任务记录**", {3}),
        ("**任务号**", "**卡号**", {3}),
        ("**任务名**", "**卡名**", {3}),
        ("**后台任务显示**", "**显示说明**", {3}),
        ("**登记**", "**记录**", {3}),
        ("写一个片段", "", {3}),
    ]
    for before, after, missing in mutations:
        assert before in text, before
        broken = text.replace(before, after)
        lines = section5_summaries(broken)
        assert {i for i, line in enumerate(lines) if "告警" in line} == missing, lines
        assert all(lines[i] == actual[i] for i in range(4) if i not in missing), lines
        try:
            assert_section5(broken)
        except AssertionError:
            pass
        else:
            raise AssertionError(f"破坏原文后必须失败：{before}")
    return len(mutations)


def section5_generation_test(text, actual):
    changes = [
        ("产出都先交 Codex 写", "产出必须先交 Codex 完成"),
        ("在本会话后台任务里可见", "在本会话后台任务列表里可见"),
        ("必须关闭标准输入", "必须关闭标准输入以免空等"),
        ("[主线·bc] 动作 + 对象", "[主线·bc] 核实 + 对象"),
    ]
    for i, (before, after) in enumerate(changes):
        assert before in text, before
        changed = section5_summaries(text.replace(before, after))
        assert after in changed[i] and changed[i] != actual[i], changed[i]
        assert all(changed[j] == actual[j] for j in range(4) if j != i), changed

    for i, (before, _) in enumerate(changes):
        long = section5_summaries(text.replace(before, before[:1] + "长" * 100 + before[1:]))[i]
        assert len(long) == 80 and long.endswith("…"), long
    wrapped = text.replace("启动，在", "启动，\n    在")
    assert section5_summaries(wrapped) == actual
    section = re.search(r"(?ms)^## 5[.\s].*?\n(.*?)(?=^## |\Z)", text).group(1)
    for fence in ("```", "~~~"):
        assert section5_summaries(f"## 5. 上下文管理\n{fence}\n{section}\n{fence}\n") == section5_summaries("")
    assert section5_summaries("## 4. 前节\n" + section + "\n## 6. 后节\n") == section5_summaries("")
    assert all("告警" in line and len(line) <= 80 for line in section5_summaries(""))


def section5_self_test(text):
    actual = assert_section5(text)
    section5_generation_test(text, actual)
    section = re.search(r"(?ms)^## 5[.\s].*?\n(.*?)(?=^## |\Z)", text).group(1)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "SESSION_PLAYBOOK.md"
        for source, expected in [(text, summaries(text)), ("## 5. 上下文管理\n" + section, [SECTION11_WARNING])]:
            path.write_text(source, encoding="utf-8")
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                run(path)
            assert out.getvalue().splitlines() == [HEADER, *actual, *expected]

    # coordinator 089cd7ffca5026162dd8326173521f8f1ee7bd99 的原文固定样例，自测不依赖本地分支引用。
    coordinator = '## 5. 上下文管理\n- **Codex 派工**：\n  - 调用：在独立 worktree 中 `codex exec -m gpt-6.1-sol -c model_reasoning_effort=xhigh -s <read-only|workspace-write> --ephemeral -o <结果文件> < /dev/null`（必须关闭标准输入，否则会停在「Reading additional input from stdin」干等，10-17 N9 因此空等 77 分钟）（本机 Codex 目录中最强模型；机械活可用 high），不联网；提示词用 `templates/WORK_CARD.md`，要求先读 `CLAUDE.md`（Codex 读仓库根 `AGENTS.md`，指向同一套规则）、本文件 §5、§6.1 与 `CODING_STANDARD.md`、`NAMING_STANDARD.md`，并以中文写结果。一律用会话 Bash 的 `run_in_background` 启动，在本会话后台任务里可见、完成自动唤醒；不得用 nohup、`&`、setsid 等脱离会话（用户 2026-10-17「所有会话调动 codex 任务都要在自己的后台任务显示」）；不轮询。\n'
    assert section5_summaries(coordinator)[2] == "Codex 派工：codex exec … < /dev/null；必须关闭标准输入"

    return section5_mutation_test(text)


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
            assert out.getvalue().splitlines() == [HEADER, *section5_summaries(""), *(expected or [SECTION11_WARNING])]
            assert all(len(line) <= 80 for line in expected)
        path.unlink()
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            run(path)
        assert out.getvalue().splitlines() == [WARNING]
    assert HEADER == "【SESSION_PLAYBOOK §5、§11 摘要（Codex 派工、任务命名与登记、调度职责、消息约定、战队协作；原文生成，一条一行；全文见文件）】"
    real = Path(__file__).resolve().parents[2] / "docs/modernization/SESSION_PLAYBOOK.md"
    actual = summaries(real.read_text(encoding="utf-8"))
    assert actual and any(line.startswith("战队协作第") for line in actual), "真实 §11 必须能解析且含战队协作组"
    mutation_count = section5_self_test(real.read_text(encoding="utf-8"))
    print(f"self-test: ok (原有 {len(cases) + 2} 项；真实 SESSION_PLAYBOOK §5 四条、§11；coordinator 固定样例；{mutation_count} 项破坏验证)")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        run(sys.argv[1] if len(sys.argv) == 2 else Path(__file__).resolve().parents[2] / "docs/modernization/SESSION_PLAYBOOK.md")
