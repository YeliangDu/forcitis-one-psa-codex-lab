#!/usr/bin/env python3
import contextlib
import importlib.util
import io
from pathlib import Path
import re
import runpy
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


HOOKS = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("playbook_digest", HOOKS / "playbook_digest.py")
digest = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(digest)
SOURCE = HOOKS.parents[1] / "docs/modernization/SESSION_PLAYBOOK.md"


# 固定样例使用虚构登记路径，避免被扫描器当成真实构建输入。
SECTION5_FIXTURE = """## 5. 上下文管理
- **Codex 派工**：
  - 默认先交 Codex：凡结果能由 Claude 审出对错的产出都先交 Codex 写——编码与文档。
  - 不交 Codex：生产取证与任何连生产的操作；身份、授权、凭据、单人模式防线等安全改动；opus 复审；验收测试；与用户和其他会话的沟通。
  - 调用：在独立 worktree 中 `codex exec -m fixed-model -o <结果文件> < /dev/null`（必须关闭标准输入，否则空等）。一律用会话 Bash 的 `run_in_background` 启动，在本会话后台任务里可见、完成自动唤醒；不得用 nohup、`&`、setsid 等脱离会话。
- **任务命名与登记**：
  - 每张卡（含设计、文档）一个**任务号** `<主线码>-<MMDD>-<会话码>-<两位序号>`。
  - **任务名**：`[主线·bc] 动作 + 对象`。
  - **后台任务显示**：`run_in_background` 的 description 写 `<任务号> <任务名> (<执行者>)`。
  - **登记**：派卡时在 `fixed/tasks/<任务号>.md` 写一个片段。
"""


def assert_section5(text):
    actual = digest.section5_summaries(text)
    assert actual and not any("告警：" in line for line in actual), actual
    assert all(len(line) <= 80 and "\n" not in line for line in actual), actual
    excluded = [line for line in actual if line.startswith("Codex 派工：不交 Codex：")]
    primary = [line for line in actual if line not in excluded]
    assert len(primary) == 5, primary
    assert primary[0].startswith("Codex 派工：默认先交 Codex："), primary
    assert "run_in_background" in primary[1], primary
    assert "codex exec" in primary[2] and "< /dev/null" in primary[2], primary
    assert all(line.startswith("任务命名与登记：") for line in primary[3:]), primary
    source_list = re.search(r"(?m)^\s*[-+*]\s+不交 Codex[：:]\s*(.+)", text)
    assert source_list, "真实 §5 必须有不交 Codex 清单"
    items = [item.strip().removesuffix("。").strip() for item in re.split(r"[；;]", source_list.group(1))]
    assert excluded == ["Codex 派工：不交 Codex：" + item for item in items], excluded
    return actual


def section5_mutation_test(text):
    actual = assert_section5(text)
    tasks, exclusions = {3, 4}, set(range(5, len(actual)))
    mutations = [
        ("**Codex 派工**", "**Codex 调用**", {0, 1, 2} | exclusions, 4),
        ("默认先交 Codex：", "可选 Codex：", {0}, 1),
        ("一律用会话 Bash 的 `run_in_background` 启动", "", {1}, 1),
        ("不得用 nohup、`&`、setsid 等脱离会话", "", {1}, 1),
        ("< /dev/null", "", {2}, 1),
        ("**任务命名与登记**", "**任务记录**", tasks, 1),
        ("**任务号**", "**卡号**", tasks, 1),
        ("**任务名**", "**卡名**", tasks, 1),
        ("**后台任务显示**", "**显示说明**", tasks, 1),
        ("**登记**", "**记录**", tasks, 1),
        ("写一个片段", "", tasks, 1),
        ("不交 Codex：", "交给 Codex：", exclusions, 1),
        ("fixed/tasks/<任务号>.md", "", tasks, 1),
        ("`<主线码>-<MMDD>-<会话码>-<两位序号>`", "", tasks, 1),
    ]
    for before, after, missing, warnings in mutations:
        assert before in text, before
        broken = text.replace(before, after)
        lines = digest.section5_summaries(broken)
        assert sum("告警：" in line for line in lines) == warnings, lines
        assert all(line in lines for i, line in enumerate(actual) if i not in missing), lines
        assert len(lines) == len(actual) - len(missing) + warnings, lines
        try:
            assert_section5(broken)
        except AssertionError:
            pass
        else:
            raise AssertionError(f"破坏固定样例后必须失败：{before}")
    for empty in ("", "；", "生产操作；；安全改动"):
        broken = re.sub(r"(?m)(^  - 不交 Codex：).+", lambda m: m[1] + empty, text)
        assert any("告警：无法解析" in line and "不交 Codex" in line for line in digest.section5_summaries(broken))
    return len(mutations) + 3


def section5_generation_test(text, actual):
    changes = [
        (0, "产出都先交 Codex 写", "产出必须先交 Codex 完成"),
        (1, "在本会话后台任务里可见", "在本会话后台任务列表里可见"),
        (2, "必须关闭标准输入", "必须关闭标准输入以免空等"),
        (3, "[主线·bc] 动作 + 对象", "[主线·bc] 核实 + 对象"),
        (4, "fixed/tasks/<任务号>.md", "fixed/tasks/<任务号>-新.md"),
        (5, "生产取证与任何连生产的操作", "生产取证及连接生产的操作"),
    ]
    for i, before, after in changes:
        assert before in text, before
        changed = digest.section5_summaries(text.replace(before, after))
        assert after in changed[i] and changed[i] != actual[i], changed[i]
        assert all(changed[j] == actual[j] for j in range(len(actual)) if j != i), changed
        long = digest.section5_summaries(text.replace(before, before[:1] + "长" * 100 + before[1:]))
        assert len(long[i]) == 80 and long[i].endswith("…"), long
        assert "告警：" in long[i + 1] and "截断" in long[i + 1], long
        assert all(len(line) <= 80 for line in long), long
    wrapped = text.replace("启动，在", "启动，\n    在")
    assert digest.section5_summaries(wrapped) == actual
    section = re.search(r"(?ms)^## 5[.\s].*?\n(.*?)(?=^## |\Z)", text).group(1)
    for fence in ("```", "~~~"):
        assert digest.section5_summaries(f"## 5. 上下文管理\n{fence}\n{section}\n{fence}\n") == digest.section5_summaries("")
    assert digest.section5_summaries("## 4. 前节\n" + section + "\n## 6. 后节\n") == digest.section5_summaries("")
    assert all("告警" in line and len(line) <= 80 for line in digest.section5_summaries(""))


def section5_self_test(text):
    actual = assert_section5(text)
    required = [
        ("默认先交 Codex", "凡结果能由 Claude 审出对错", "先交 Codex"),
        ("一律用会话 Bash", "run_in_background", "启动", "后台任务里可见", "不得用", "nohup", "&", "setsid", "脱离会话"),
        ("codex exec", "< /dev/null", "必须关闭标准输入"),
        ("每张卡一个任务号", "<主线码>-<MMDD>-<会话码>-<两位序号>", "任务名", "[主线·bc]"),
        ("后台任务显示", "<执行者>", "登记", "fixed/tasks/<任务号>.md", "写一个片段"),
    ]
    for line, words in zip(actual, required):
        assert all(word in line for word in words), line
    section5_generation_test(text, actual)
    section = re.search(r"(?ms)^## 5[.\s].*?\n(.*?)(?=^## |\Z)", text).group(1)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "SESSION_PLAYBOOK.md"
        for source, expected in [(text, digest.summaries(text) or [digest.SECTION11_WARNING]), ("## 5. 上下文管理\n" + section, [digest.SECTION11_WARNING])]:
            path.write_text(source, encoding="utf-8")
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                digest.run(path)
            assert out.getvalue().splitlines() == [digest.GENERATED_HEADER, "", digest.HEADER, "", "## §2.1 会话管理", "",
                *digest.section21_summaries(source), "", "## §5 上下文管理", "", *actual, "", "## §11 总调度会话", "", *expected]
    # coordinator 089cd7ffca5026162dd8326173521f8f1ee7bd99 的原文固定样例，自测不依赖本地分支引用。
    coordinator = '## 5. 上下文管理\n- **Codex 派工**：\n  - 调用：在独立 worktree 中 `codex exec -m gpt-6.1-sol -c model_reasoning_effort=xhigh -s <read-only|workspace-write> --ephemeral -o <结果文件> < /dev/null`（必须关闭标准输入，否则会停在「Reading additional input from stdin」干等，10-17 N9 因此空等 77 分钟）（本机 Codex 目录中最强模型；机械活可用 high），不联网；提示词用 `templates/WORK_CARD.md`，要求先读 `CLAUDE.md`（Codex 读仓库根 `AGENTS.md`，指向同一套规则）、本文件 §5、§6.1 与 `CODING_STANDARD.md`、`NAMING_STANDARD.md`，并以中文写结果。一律用会话 Bash 的 `run_in_background` 启动，在本会话后台任务里可见、完成自动唤醒；不得用 nohup、`&`、setsid 等脱离会话（用户 2026-10-17「所有会话调动 codex 任务都要在自己的后台任务显示」）；不轮询。\n'
    assert digest.section5_summaries(coordinator)[2] == "Codex 派工：codex exec … < /dev/null；必须关闭标准输入"

    return section5_mutation_test(text)


class CommonDigestTest(unittest.TestCase):
    def setUp(self):
        self.text = ("### 2.1 会话管理\n" + "\n".join(
            f"{number}. **{'命名与唯一性' if number == 2 else f'固定条目{number}'}**：正文。" for number in range(1, 17))
            + "\n" + SECTION5_FIXTURE + "\n## 11. 总调度会话\n**战队协作**\n1. **待决定收件箱**：正文。\n")

    def test_section5_fixed_fixture(self):
        self.assertEqual(section5_self_test(SECTION5_FIXTURE), 17)

    def test_real_source_parses_without_warnings(self):
        text = SOURCE.read_text(encoding="utf-8")
        self.assertNotIn("告警：", digest.render_common(text))
        self.assertTrue(digest.section21_summaries(text))
        assert_section5(text)
        self.assertTrue(digest.summaries(text))

    def assert_complete(self, text):
        entries = digest.section21_summaries(text)
        self.assertEqual(len(entries), 16)
        self.assertFalse(any("告警" in line for line in entries), entries)
        self.assertEqual([int(re.match(r"第(\d+)条：", line).group(1)) for line in entries], list(range(1, 17)))
        return entries

    def test_source_and_hook_share_renderer(self):
        entries = self.assert_complete(self.text)
        section = re.search(r"(?ms)^### 2\.1 .*?\n(.*?)(?=^## |\Z)", self.text).group(1)
        titles = re.findall(r"(?m)^\d+\. \*\*([^*]+)\*\*", section)
        self.assertEqual(len(titles), 16)
        for index, (entry, title) in enumerate(zip(entries, titles), 1):
            self.assertEqual(entry, f"第{index}条：{title}")
            self.assertLessEqual(len(entry), 80)
        with tempfile.TemporaryDirectory() as tmp:
            source, output = Path(tmp) / "source.md", Path(tmp) / "common.md"
            source.write_text(self.text, encoding="utf-8")
            digest.write_common(source, output)
            captured = io.StringIO()
            with contextlib.redirect_stdout(captured):
                digest.run(source)
            content = output.read_text(encoding="utf-8")
            self.assertEqual(content, captured.getvalue())
            self.assertEqual(content, digest.render_common(self.text))
            self.assertIn("请勿手改", content.splitlines()[0])
            for title, lines in [("§2.1 会话管理", entries), ("§5 上下文管理", assert_section5(self.text)),
                                 ("§11 总调度会话", digest.summaries(self.text))]:
                self.assertIn(f"## {title}\n\n" + "\n".join(lines), content)
            digest.write_common(source, output, check=True)

    def test_numbered_extraction_uses_section11_rules(self):
        section = re.search(r"(?ms)^### 2\.1 .*?\n(.*?)(?=^## |\Z)", self.text).group(1)
        body = section.replace("**命名与唯一性**", "命名须唯一。下一句。")
        body += '\n  1. 嵌套不注入\n| 2. 表格 | 不注入 |\n```\n3. 代码不注入\n```\n'
        text = "### 2.1 会话管理\n" + body + "\n### 2.2 后节\n17. 不注入\n"
        entries = self.assert_complete(text)
        self.assertEqual(entries[1], "第2条：命名须唯一")
        long = text.replace("命名须唯一。", "长" * 100 + "。")
        self.assertEqual(digest.section21_summaries(long)[1], "第2条：" + "长" * 75 + "…")
        added = text.replace("### 2.2 后节", "17. **新增条目**：正文。\n### 2.2 后节")
        self.assertEqual(digest.section21_summaries(added)[-1], "第17条：新增条目")

    def test_source_mutations_fail_before_regeneration(self):
        section = re.search(r"(?ms)^### 2\.1 .*?\n(.*?)(?=^## |\Z)", self.text).group(1)
        broken_sources = [self.text.replace("### 2.1 会话管理", "### 2.1 会话规则", 1)]
        for number in range(1, 17):
            broken = re.sub(rf"(?ms)^{number}\. .*?(?=^\d+\. |\Z)", "", section, count=1)
            broken_sources.append(self.text.replace(section, broken, 1))
        for fence in ("```", "~~~"):
            broken_sources.append(self.text.replace(section, f"{fence}\n{section}\n{fence}\n", 1))
        for index, broken in enumerate(broken_sources):
            with self.subTest(mutation=index), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaises(AssertionError):
                    self.assert_complete(broken)
                self.assertTrue(any("告警" in line for line in digest.section21_summaries(broken)))
                source, output = Path(tmp) / "source.md", Path(tmp) / "common.md"
                source.write_text(self.text, encoding="utf-8")
                digest.write_common(source, output)
                before = output.read_bytes()
                source.write_text(broken, encoding="utf-8")
                for check in (False, True):
                    with self.assertRaisesRegex(ValueError, "告警"):
                        digest.write_common(source, output, check=check)
                    self.assertEqual(output.read_bytes(), before)

    def test_source_edits_regenerate_without_tracked_output(self):
        edits = [("**命名与唯一性**", "**会话命名与唯一性**"),
                 ("产出都先交 Codex 写", "产出必须先交 Codex 完成"),
                 ("**待决定收件箱**", "**待决定收件箱受理**")]
        for before, after in edits:
            with self.subTest(edit=before), tempfile.TemporaryDirectory() as tmp:
                source, output = Path(tmp) / "source.md", Path(tmp) / "common.md"
                source.write_text(self.text, encoding="utf-8")
                digest.write_common(source, output, check=True)
                self.assertEqual(output.read_text(encoding="utf-8"), digest.render_common(self.text))
                source.write_text(self.text.replace(before, after, 1), encoding="utf-8")
                digest.write_common(source, output, check=True)
                self.assertIn(after.replace("**", ""), output.read_text(encoding="utf-8"))
                output.write_text("过期摘要\n", encoding="utf-8")
                digest.write_common(source, output, check=True)
                self.assertEqual(output.read_text(encoding="utf-8"), digest.render_common(source.read_text(encoding="utf-8")))

    def test_check_rejects_warnings_and_nondeterministic_generation(self):
        content = digest.render_common(self.text)
        with tempfile.TemporaryDirectory() as tmp:
            source, output = Path(tmp) / "source.md", Path(tmp) / "common.md"
            source.write_text(self.text, encoding="utf-8")
            output.write_text("保留原产物", encoding="utf-8")
            for second, error in [(content + "变化", "两次生成内容不一致"), (content + "告警：损坏", "告警")]:
                with self.subTest(error=error), patch.object(digest, "render_common", side_effect=[content, second]):
                    with self.assertRaisesRegex(ValueError, error):
                        digest.write_common(source, output, check=True)
                    self.assertEqual(output.read_text(encoding="utf-8"), "保留原产物")
            for before in ("产出都先交 Codex 写", "生产取证与任何连生产的操作"):
                source.write_text(self.text.replace(before, before + "长" * 100), encoding="utf-8")
                for check in (False, True):
                    with self.assertRaisesRegex(ValueError, "截断"):
                        digest.write_common(source, output, check=check)
                    self.assertEqual(output.read_text(encoding="utf-8"), "保留原产物")

    def test_build_check_collects_failures(self):
        build = SOURCE.with_name("build_all.py")
        calls = []

        def run(args, **kwargs):
            calls.append(args)
            name = Path(args[1]).name if args[0] == sys.executable else ""
            if name == "build_records.py" or (name == "build_sessions.py" and "--self-test" in args):
                return SimpleNamespace(returncode=1, stdout="固定失败", stderr="")
            if name == "review_revisions.py" or "-B" in args:
                return SimpleNamespace(returncode=1, stdout="固定修订失败", stderr="")
            if "status" in args:
                return SimpleNamespace(returncode=0, stdout=" M glossary/terms.yaml\n?? adr/ADR-9999.md\n", stderr="")
            stdout = "base" if "merge-base" in args else "HEAD" if "rev-parse" in args else ""
            return SimpleNamespace(returncode=0, stdout=stdout, stderr="")

        def fail_digest(*args, **kwargs):
            raise ValueError("固定摘要失败")

        module = SimpleNamespace(write_common=fail_digest)
        spec = SimpleNamespace(loader=SimpleNamespace(exec_module=lambda module: None))
        out, err = io.StringIO(), io.StringIO()
        with patch("importlib.util.spec_from_file_location", return_value=spec), \
                patch("importlib.util.module_from_spec", return_value=module), \
                patch("subprocess.run", side_effect=run), patch.object(sys, "argv", [str(build), "--check"]), \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            with self.assertRaises(SystemExit) as stopped:
                runpy.run_path(str(build), run_name="__main__")
        self.assertEqual(stopped.exception.code, 1)
        for error in ("固定摘要失败", "build_records.py 失败", "build_sessions.py 自测失败", "固定修订失败",
                      "生成产物与片段不一致", " M glossary/terms.yaml", "?? adr/ADR-9999.md"):
            self.assertIn(error, err.getvalue())
        self.assertTrue(any("status" in args for args in calls), "失败后仍检查生成产物")
        status = next(args for args in calls if "status" in args)
        for path in ("glossary/terms.yaml", "adr/ADR-*.md", "adr/README.md", "adr/INDEX.md",
                     "MODERNIZATION_BRIEF.md", "architecture/generated/", "../../backend/glossary/proposed/README.md"):
            self.assertIn(path, status)
        self.assertNotIn("sessions/_COMMON.md", status)
        self.assertIn("architecture/check_proposals.py", " ".join(arg for args in calls for arg in args))


if __name__ == "__main__":
    unittest.main(argv=[__file__, *([] if sys.argv[1:] == ["--self-test"] else sys.argv[1:])])
