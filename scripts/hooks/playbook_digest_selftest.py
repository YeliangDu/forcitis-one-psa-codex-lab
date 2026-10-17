#!/usr/bin/env python3
import contextlib
import importlib.util
import io
from pathlib import Path
import re
import sys
import tempfile
import unittest


HOOKS = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("playbook_digest", HOOKS / "playbook_digest.py")
digest = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(digest)
SOURCE = HOOKS.parents[1] / "docs/modernization/SESSION_PLAYBOOK.md"


class CommonDigestTest(unittest.TestCase):
    def setUp(self):
        self.text = SOURCE.read_text(encoding="utf-8")

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
            for title, lines in [("§2.1 会话管理", entries), ("§5 上下文管理", digest.assert_section5(self.text)),
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

    def test_source_edits_make_generated_file_stale(self):
        edits = [("**命名与唯一性**", "**会话命名与唯一性**"),
                 ("产出都先交 Codex 写", "产出必须先交 Codex 完成"),
                 ("**待决定收件箱**", "**待决定收件箱受理**")]
        for before, after in edits:
            with self.subTest(edit=before), tempfile.TemporaryDirectory() as tmp:
                source, output = Path(tmp) / "source.md", Path(tmp) / "common.md"
                source.write_text(self.text, encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "过期"):
                    digest.write_common(source, output, check=True)
                digest.write_common(source, output)
                source.write_text(self.text.replace(before, after, 1), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "过期"):
                    digest.write_common(source, output, check=True)
                digest.write_common(source, output)
                self.assertIn(after.replace("**", ""), output.read_text(encoding="utf-8"))
                digest.write_common(source, output, check=True)
                output.write_text("过期摘要\n", encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "过期"):
                    digest.write_common(source, output, check=True)


if __name__ == "__main__":
    unittest.main(argv=[__file__, *([] if sys.argv[1:] == ["--self-test"] else sys.argv[1:])])
