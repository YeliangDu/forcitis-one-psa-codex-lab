"""ARC-1021-ARC-07：原文、失败输入、状态机多值与可复现性。"""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

import convert_objects as c


def table(*rows):
    return "\n".join(["## 1. 总表", "| " + " | ".join(c.HEADER) + " |",
                      "|" + "---|" * 13, "| **分组** |" + " |" * 12, *rows, "### 1.1 说明"])


def row(ident="OBJ-01", **changes):
    values = dict(zip(c.FIELDS, [ident, "**名称**", "`定义`", "**一级**", "类型", "草稿 → 已生效", "关系", "规则",
                               "事实", "出处", "词表", "范围", "OBJ-02、OBJ-03（多父）"]))
    values.update(changes)
    return "| " + " | ".join(values.values()) + " |"


class ConvertObjectsTest(unittest.TestCase):
    def test_group_markdown_and_multiple_parents(self):
        objects, groups = c.parse_objects(table(row()))
        self.assertEqual((1, 1), (len(objects), groups))
        obj = objects[0]
        self.assertEqual(("OBJ-01", "名称", "定义", "一级", "分组", 5),
                         tuple(obj[k] for k in ("id", "name", "definition", "level", "group", "source_line")))
        self.assertEqual(["OBJ-02", "OBJ-03"], obj["parent"])
        self.assertEqual("OBJ-02、OBJ-03（多父）", obj["parent_text"])
        self.assertEqual("草稿 → 已生效", obj["key_states"])

    def test_duplicates_empty_and_malformed_tables_fail(self):
        cases = [(table(row(), row()), "重复"), ("", "总表"), (table(), "未读到"),
                 (table(row()).replace("| 编号 |", "| 编号 | 多列 |"), "列数"),
                 (table(row()).replace("一级 / 次级", "错误表头"), "表头"),
                 (table(row()).replace("| OBJ-01 |", "| OBJ-01 | 多列 |"), "列数"),
                 (table(row("INVALID")), "非法对象")]
        for text, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(ValueError, message):
                c.parse_objects(text)

    def test_real_rows_and_freeze_are_lossless(self):
        docs = c.documents()
        objects = docs["objects.yaml"]["objects"]
        self.assertEqual({"count": 126, "group_count": 14, "valid_count": 104, "non_object_count": 22},
                         {k: v for k, v in docs["objects.yaml"]["meta"].items() if k != "source"})
        lines = c.SRC.read_text(encoding="utf-8").splitlines()
        freeze = yaml.safe_load(c.FINAL.read_text(encoding="utf-8"))
        for obj in objects:
            original = [x.strip() for x in lines[obj["source_line"] - 1].strip("|").split("|")]
            self.assertEqual([c.plain(x) for x in original], [obj[k] for k in c.FIELDS])
            self.assertEqual(obj["id"] in freeze, "freeze" in obj)
            if obj["id"] in freeze:
                self.assertEqual(freeze[obj["id"]], obj["freeze"])

    def test_both_machine_sets_and_repeated_aggregates_are_preserved(self):
        docs = c.documents()
        states = docs["state-machines.raw.yaml"]
        self.assertEqual({"by_object_count": 126, "by_aggregate_count": 131,
                          "group_counts": {"finance": 44, "people": 34, "revenue": 53}}, states["meta"])
        expected = [{"object": o["id"], "key_states_text": o["key_states"],
                     "source": f"{c.SRC.relative_to(c.ROOT)}:{o['source_line']}"}
                    for o in docs["objects.yaml"]["objects"] if o["key_states"]]
        self.assertEqual(expected, states["by_object"])
        for group in c.GROUPS:
            path = c.ARCH / f"proposals/closeout/{group}/state-machines.yaml"
            original = yaml.safe_load(path.read_text(encoding="utf-8"))["machines"]
            actual = [m for m in states["by_aggregate"] if m["group"] == group]
            self.assertEqual(original, [{k: v for k, v in m.items() if k not in ("group", "source")} for m in actual])
            self.assertEqual([f"{path.relative_to(c.ROOT)}#machines[{i}]" for i in range(len(original))], [m["source"] for m in actual])
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "machines.yaml"
            repeated = [{"aggregate": "AGG-01", "states": ["甲"]}, {"aggregate": "AGG-01", "states": ["乙"]}]
            path.write_text(yaml.safe_dump({"machines": repeated}), encoding="utf-8")
            with patch.object(c, "ROOT", Path(tmp)):
                actual, counts = c.aggregate_machines({"finance": path})
            self.assertEqual(2, counts["finance"])
            self.assertEqual(repeated, [{k: v for k, v in m.items() if k not in ("group", "source")} for m in actual])

    def test_missing_inputs_fail_and_check_never_writes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for key in ("SRC", "FINAL", "ARCH"):
                with self.subTest(key=key), patch.object(c, key, root / "absent"), self.assertRaises(FileNotFoundError):
                    c.generate()
            with patch.object(c, "HERE", root):
                with self.assertRaisesRegex(ValueError, "不一致"):
                    c.generate(True)
                self.assertEqual([], list(root.iterdir()))
                c.generate()
                first = {p.name: p.read_bytes() for p in root.iterdir()}
                c.generate(True)
                c.generate()
                self.assertEqual(first, {p.name: p.read_bytes() for p in root.iterdir()})
                changed = root / "objects.yaml"
                changed.write_bytes(changed.read_bytes() + b"# drift\n")
                before = {p.name: p.read_bytes() for p in root.iterdir()}
                with self.assertRaisesRegex(ValueError, "objects.yaml"):
                    c.generate(True)
                self.assertEqual(before, {p.name: p.read_bytes() for p in root.iterdir()})


if __name__ == "__main__":
    unittest.main()
