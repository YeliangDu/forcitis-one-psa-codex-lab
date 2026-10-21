"""ARC-1021-ARC-04：登记网络连线与覆盖检查的正反例。"""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

import panorama_build as p


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
            sources = {key: root / f"{key}.yaml" for key in ("built", "tables", "access")}
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
