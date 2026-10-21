"""ARC-1021-ARC-04：登记网络连线与覆盖检查的正反例。"""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

import panorama_build as p


class ObjectSourceTest(unittest.TestCase):
    def test_arc07_yaml_is_the_only_object_table_source(self):
        with tempfile.TemporaryDirectory(dir=p.ROOT) as tmp:
            root = Path(tmp)
            objects, details = root / "objects.yaml", root / "details.md"
            fields = ("id", "name", "definition", "level", "type", "key_states", "relations", "rules", "facts", "source", "term", "scope", "parent_text")
            row = {**dict.fromkeys(fields, ""), "id": "OBJ-01", "name": "往来单位", "level": "一级",
                   "key_states": "有效", "facts": "已登记", "parent": ["OBJ-02", "OBJ-03"], "parent_text": "OBJ-02、OBJ-03"}
            removed = {**row, "id": "OBJ-04", "definition": "【行号保留，已非对象】"}
            objects.write_text(yaml.safe_dump({"objects": [row, removed]}, allow_unicode=True), encoding="utf-8")
            details.write_text("## 1. 总表\n故意无可解析总表\n## 2. 说明\n### 2.1 往来单位与角色\n- 身份：物业\n### 2.2 其他\n- 监理\n## 3. 非对象", encoding="utf-8")
            model, edges = {"obj": {}}, []
            with patch.dict(p.SRC, obj=objects, obj_details=details):
                p.load_objects(model, lambda *edge: edges.append(edge))
                self.assertEqual(["OBJ-01"], list(model["obj"]))
                self.assertEqual("有效", model["obj"]["OBJ-01"]["states"])
                self.assertEqual(["OBJ-02", "OBJ-03"], model["obj"]["OBJ-01"]["parents"])
                self.assertTrue(model["obj"]["OBJ-01"]["primary"])
                self.assertIn("物业", model["obj_text"]["OBJ-01"])
                self.assertNotIn("监理", model["obj_text"]["OBJ-01"])
                self.assertEqual(2, len(edges))
                self.assertTrue(all("objects.yaml#objects[0]/OBJ-01" in edge[3] for edge in edges))
                row["key_states"] = "已停用"
                objects.write_text(yaml.safe_dump({"objects": [row]}, allow_unicode=True), encoding="utf-8")
                changed = {"obj": {}}
                p.load_objects(changed, lambda *edge: None)
                self.assertEqual("已停用", changed["obj"]["OBJ-01"]["states"])
                objects.write_text(yaml.safe_dump({"objects": [row, row]}, allow_unicode=True), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "编号重复"):
                    p.load_objects({"obj": {}}, lambda *edge: None)
                objects.unlink()
                with self.assertRaises(FileNotFoundError):
                    p.load_objects({"obj": {}}, lambda *edge: None)


class AggregateSourceTest(unittest.TestCase):
    def test_arc08_planned_yaml_replaces_section_2_2(self):
        with tempfile.TemporaryDirectory(dir=p.ROOT) as tmp:
            root = Path(tmp)
            planned, draft = root / "planned.yaml", root / "draft.md"
            text = p.SRC["agg"].read_text(encoding="utf-8")
            start, end = text.index("### 2.2"), text.index("### 2.3")
            draft.write_text(text[:start] + text[end:], encoding="utf-8")
            row = {"id": "AGG-01", "聚合（根）": "往来单位（OBJ-01）", "内含": "OBJ-02", "对应": "一对一", "不变量": "INV-01"}
            planned.write_text(yaml.safe_dump({"aggregates": [row]}, allow_unicode=True), encoding="utf-8")
            with patch.dict(p.SRC, planned=planned, agg=draft):
                model, links, _ = p.load()
                self.assertEqual(["AGG-01"], [a for a in model["agg"] if a.startswith("AGG-")])
                self.assertEqual(["OBJ-01", "OBJ-02"], model["agg"]["AGG-01"]["objs"])
                self.assertEqual("B-01", model["agg"]["AGG-01"]["bc"])
                self.assertTrue(all("planned.yaml#aggregates" in edge["src"] for edge in links if edge["kind"] == "obj-agg"))
                planned.write_text(yaml.safe_dump({"aggregates": [row, row]}, allow_unicode=True), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "编号重复"):
                    p.load_planned_aggregates({"agg": {}}, lambda *args: None)
                planned.unlink()
                with self.assertRaises(FileNotFoundError):
                    p.load_planned_aggregates({"agg": {}}, lambda *args: None)

    def test_arc08_default_sources_are_migrated(self):
        directory = p.ARCH / "ssot/i03-aggregates"
        self.assertEqual(directory / "built.yaml", p.SRC["built"])
        self.assertEqual(directory / "planned.yaml", p.SRC["planned"])


class CommandNetworkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=p.ROOT)
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.sources = {key: self.root / f'{key}.yaml' for key in ('cmd', 'otr')}
        self.command = {'id': 'CMD-12345678', 'name': '登记对象', 'aggregate': 'AGG-01',
                        'owner_bc': 'B-01', 'objects': ['OBJ-01']}
        self.transition = {'id': 'OTR-001', 'aggregate': 'AGG-01', 'command': '登记对象', 'by_command': 'CMD-12345678'}
        self.write()

    def write(self, commands=None, pending=None):
        self.sources['cmd'].write_text(yaml.safe_dump({'commands': commands if commands is not None else [self.command]}, allow_unicode=True))
        self.sources['otr'].write_text(yaml.safe_dump({'transitions': [self.transition], 'pending': pending or []}, allow_unicode=True))

    def load(self):
        model = {'cmd': {}, 'otr': {}, 'agg': {'AGG-01': {}}, 'bc': {'B-01': {}}}
        edges = []
        with patch.dict(p.SRC, self.sources):
            p.load_commands(model, lambda kind, a, b, src: edges.append({'kind': kind, 'from': a, 'to': b, 'src': src}))
        return model, edges

    def test_arc09_links_and_report_checks_include_pending(self):
        pending = {'id': 'OTR-002', 'aggregate': 'AGG-99', 'command': None, 'by_command': None}
        self.write(pending=[pending])
        model, edges = self.load()
        self.assertEqual({'cmd-agg', 'cmd-bc', 'cmd-obj', 'cmd-otr'}, {e['kind'] for e in edges})
        self.assertTrue(all('#' in e['src'] for e in edges))
        self.assertEqual(('CMD-12345678', 'OTR-001'), next((e['from'], e['to']) for e in edges if e['kind'] == 'cmd-otr'))
        gaps = p.defaultdict(list)
        p.check_commands(model, gaps)
        self.assertEqual([], gaps['N10-2'])
        self.assertEqual(['OTR-002（pending，AGG-99，命令 None）'], gaps['N10-3'])
        self.command['owner_bc'] = None
        self.transition['by_command'] = None
        self.write()
        model, edges = self.load()
        p.check_commands(model, gaps := p.defaultdict(list))
        self.assertEqual(1, len(gaps['N10-2']))
        self.assertEqual(1, len(gaps['N10-3']))
        self.assertEqual({'cmd-agg', 'cmd-obj'}, {e['kind'] for e in edges})
        self.assertNotIn('N11-2', p.CHECKS)
        self.assertIn('N11-1', p.CHECKS)

    def test_arc09_missing_duplicate_or_invalid_reference_fails(self):
        self.write(commands=[self.command, self.command])
        with self.assertRaisesRegex(ValueError, '编号重复'):
            self.load()
        self.write()
        self.transition['by_command'] = 'CMD-87654321'
        self.write()
        with self.assertRaisesRegex(ValueError, 'by_command'):
            self.load()
        self.transition['by_command'] = self.command['id']
        self.transition['command'] = ' 登记对象'
        self.write()
        with self.assertRaisesRegex(ValueError, '原文'):
            self.load()
        self.sources['cmd'].unlink()
        with self.assertRaises(FileNotFoundError):
            self.load()

    def test_arc09_repeated_read_has_identical_bytes(self):
        self.assertEqual(yaml.safe_dump(self.load()).encode(), yaml.safe_dump(self.load()).encode())


class ConstraintNetworkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=p.ROOT)
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.src = dict(p.SRC)
        for key in ("inv", "rule_cards", "rule", "ep", "tc", "manual"):
            self.src[key] = self.root / f"{key}.yaml"
        self.write("inv", {"rules": [{"id": "INV-03a", "owner_aggregates": ["AGG-01"]},
                                    {"id": "INV-04", "owner_aggregates": ["AGG-01"]}]})
        self.write("rule_cards", {"entries": [{"inv": "INV-03a", "rule_cards": ["RULE-1"], "evidence": "唯一对照"}],
                                  "pending": [{"inv": "INV-04", "candidate_rule_cards": ["RULE-002"]}]})
        self.src["rule"].write_text("| RULE-001 | 域 | 规则一 |\n| RULE-002 | 域 | 规则二 |\n", encoding="utf-8")
        self.write("ep", {"execution_points": [{"id": "EP-direct", "enforces": ["INV-03a"]},
                                              {"id": "EP-rule", "enforces": ["RULE-001"]},
                                              {"id": "EP-manual", "enforces": []}]})
        self.tests = {"tests": [{"key": "Test#only", "covers": ["RULE-001"], "class_covers": []},
                                {"key": "Test#direct", "covers": ["INV-04"], "class_covers": ["INV-03a"]}]}
        self.write("tc", self.tests)
        patcher = patch.dict(p.SRC, self.src)
        patcher.start()
        self.addCleanup(patcher.stop)

    def write(self, key, data):
        self.src[key].write_text(yaml.safe_dump(data, allow_unicode=True), encoding="utf-8")

    def load(self):
        model = {key: {} for key in ("inv", "rule", "ep", "tc")}
        links = []
        p.load_constraints(model, lambda kind, a, b, src, **extra: links.append(
            {"kind": kind, "from": a, "to": b, "src": src, **extra}))
        return model, links

    def manual(self, **changes):
        self.write("manual", {"entries": [{"inv": "INV-03a", "execution_points": ["EP-manual"],
                                           "evidence": "人工核对", **changes}]})

    def test_three_execution_vias_and_pending_is_not_linked(self):
        self.manual()
        model, links = self.load()
        edges = [e for e in links if e["kind"] == "inv-ep"]
        self.assertEqual({"enforces", "rule", "manual"}, {e["via"] for e in edges})
        self.assertTrue(all(e["from"] == "INV-03a" and "#" in e["src"] for e in edges))
        self.assertEqual([], model["inv"]["INV-04"]["rules"])
        self.assertEqual(["RULE-001"], model["inv"]["INV-03a"]["rules"])
        self.assertEqual({("EP-direct", "Test#direct", "INV-03a"), ("EP-rule", "Test#only", "RULE-001")},
                         {(e["from"], e["to"], e["reference"]) for e in links if e["kind"] == "ep-tc"})
        self.assertEqual({"rule", "class_covers", "covers"}, {e["via"] for e in links if e["kind"] == "inv-tc"})

    def test_manual_absent_empty_and_empty_entries(self):
        baseline = self.load()
        for content in ("", "entries: []\n"):
            with self.subTest(content=content):
                self.src["manual"].write_text(content, encoding="utf-8")
                self.assertEqual(baseline, self.load())

    def test_manual_unknown_inv_and_ep_are_fatal(self):
        for changes, message in (({"inv": "INV-999"}, "不存在的 INV"),
                                 ({"execution_points": ["EP-missing"]}, "不存在的 EP")):
            with self.subTest(changes=changes):
                self.manual(**changes)
                with self.assertRaisesRegex(ValueError, message):
                    self.load()

    def test_manual_invalid_shape_and_missing_evidence_are_fatal(self):
        self.src["manual"].write_text("[]", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "entries"):
            self.load()
        self.manual(evidence="")
        with self.assertRaisesRegex(ValueError, "evidence"):
            self.load()

    def test_removing_unique_rule_test_reports_n08_4(self):
        before, _ = self.load()
        before.update(table={}, mod={}, api={})
        gaps = p.defaultdict(list)
        p.check_coverage(before, gaps)
        self.assertEqual([], gaps["N08-4"])
        self.assertEqual(["INV-04"], gaps["N08-2"])
        self.assertEqual([], gaps["N08-3"])
        changed = copy.deepcopy(self.tests)
        changed["tests"] = [t for t in changed["tests"] if t["key"] != "Test#only"]
        self.write("tc", changed)
        after, _ = self.load()
        after.update(table={}, mod={}, api={})
        gaps = p.defaultdict(list)
        p.check_coverage(after, gaps)
        self.assertEqual(["RULE-001"], gaps["N08-4"])
        self.assertEqual([], after["rule"]["RULE-001"]["tcs"])

    def test_repeated_load_is_byte_identical(self):
        self.manual()
        self.assertEqual(yaml.safe_dump(self.load(), allow_unicode=True).encode(),
                         yaml.safe_dump(self.load(), allow_unicode=True).encode())


class MappingTest(unittest.TestCase):
    def test_table_mappings_and_other_roles(self):
        with tempfile.TemporaryDirectory(dir=p.ROOT) as tmp:
            root = Path(tmp)
            sources = {key: root / f"{key}.yaml" for key in ("built", "tables", "access", "agg_map")}
            sources["agg_map"].write_text(yaml.safe_dump({"entries": [{"built": "cost.timesheet/Timesheet",
                "planned": ["AGG-01"], "relation": "same", "invariants": ["INV-01"]}]}))
            sources["built"].write_text(yaml.safe_dump({"aggregates": [{"id": "cost.timesheet/Timesheet",
                "bc": "cost.timesheet", "tables": ["cost_timesheet.timesheets"]}]}))
            sources["tables"].write_text(yaml.safe_dump([{"table": "planned.rows", "obj": "OBJ-01"}]))
            sources["access"].write_text(yaml.safe_dump({"tables": {
                "cost_timesheet.timesheets": {"role": "BUSINESS_DOCUMENT", "aggregate": "Timesheet"},
                "unmapped.rows": {"role": "AGGREGATE_CHILD"}, "technical.rows": {"role": "LOG_APPEND_ONLY"}}}))
            model = {"agg": {"AGG-01": {"objs": ["OBJ-01"], "tables": []}}, "table": {}, "inv": {}}
            edges = []
            with patch.dict(p.SRC, sources):
                p.load_tables(model, lambda kind, a, b, src, **extra: edges.append(
                    {"kind": kind, "from": a, "to": b, "src": src, **extra}), {"cost.timesheet": "B-06"})
            self.assertEqual(["AGG-01"], model["table"]["planned.rows"]["aggregates"])
            self.assertEqual(["INV-01"], model["agg"]["cost.timesheet/Timesheet"]["invs"])
            self.assertEqual(["cost.timesheet/Timesheet"], model["table"]["cost_timesheet.timesheets"]["aggregates"])
            gaps = p.defaultdict(list)
            p.check_coverage(model, gaps)
            self.assertEqual(["unmapped.rows（AGGREGATE_CHILD，聚合登记 无）"], gaps["N09-M5"])
            self.assertEqual({"covered": 1, "total": 2, "via": {"built": 1, "panorama": 0}}, p.coverage_counts(model, edges)["表→聚合"])
            sources["tables"].unlink()
            with patch.dict(p.SRC, sources):
                empty = {"agg": {}, "table": {}}
                p.load_tables(empty, lambda *args, **kwargs: None, {})
            self.assertIn("cost_timesheet.timesheets", empty["table"])

    def test_controller_match_uses_method_module_and_subdomain(self):
        with tempfile.TemporaryDirectory(dir=p.ROOT) as tmp:
            root = Path(tmp)
            java = root / "java"
            controller = java / "cost/timesheet/web/ExampleController.java"
            controller.parent.mkdir(parents=True)
            controller.write_text('''@RestController
@RequestMapping("/api/v1/cost")
class ExampleController {
  @GetMapping(path = "/timesheets/{id}") void get() {}
  @PostMapping({"/timesheets", "/timesheets:submit"}) void submit() {}
}
''')
            dependency = root / "module-dependencies.txt"
            dependency.write_text("cost.timesheet ->\n")
            api = root / "openapi.json"
            api.write_text(json.dumps({"paths": {
                "/api/v1/cost/timesheets/{id}": {"get": {}, "put": {}},
                "/api/v1/cost/timesheets": {"post": {}},
                "/api/v1/platform/timesheets": {"get": {"tags": ["example-controller"]}}}}))
            model = {"sd": {"cost": {}, "platform": {}}, "bc": {"B-06": {"src": "registration#B-06"}}, "api": {}, "mod": {}}
            edges = []
            with patch.object(p, "JAVA", java), patch.dict(p.SRC, modules=dependency, api=api):
                p.load_interfaces(model, lambda kind, a, b, src, **extra: edges.append(
                    {"kind": kind, "from": a, "to": b, "src": src, **extra}), {"cost.timesheet": "B-06"})
            self.assertEqual("B-06", model["api"]["GET /api/v1/cost/timesheets/{id}"]["bc"])
            self.assertIsNone(model["api"]["PUT /api/v1/cost/timesheets/{id}"]["bc"])
            self.assertIsNone(model["api"]["GET /api/v1/platform/timesheets"]["bc"])
            self.assertTrue(all(e["from"] is not None and e["to"] is not None for e in edges))
            model.update(inv={}, table={})
            gaps = p.defaultdict(list)
            p.check_coverage(model, gaps)
            self.assertEqual(2, len(gaps["N10-M3"]))
            model["mod"]["cost.timesheet"]["registered"] = False
            p.check_coverage(model, gaps)
            self.assertEqual(["cost.timesheet"], gaps["N10-M2"])


if __name__ == "__main__":
    unittest.main()
