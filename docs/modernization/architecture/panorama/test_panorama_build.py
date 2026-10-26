"""ARC-1021-ARC-04：登记网络连线与覆盖检查的正反例。"""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import panorama_build as p


class DimensionMetricNetworkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=p.ROOT)
        self.addCleanup(self.tmp.cleanup)
        self.sources = {key: Path(self.tmp.name) / f"{key}.yaml"
                        for key in ("dims", "dims_raw", "kpis", "terms", "obj")}
        patcher = patch.dict(p.SRC, self.sources)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.dimension = {"id": "DIM-01", "name": "期间", "term": None, "scd": {"value": "未定"},
                          "backed_by": ["OBJ-01", "OBJ-02", "example.rows"],
                          "sources": {"bi_column": "期间"},
                          "slices": [{"row": "工时条目", "facts": ["FACT-01"]}]}
        self.metric = {"id": "KPI-01", "name": "工时", "kind": "业务指标", "term": "hours",
                       "baseline": "M-01", "vision_kpi": None, "facts": ["FACT-01"], "dimensions": ["DIM-01"]}
        self.write("obj", {"objects": [{"id": "OBJ-02", "definition": "已非对象"}]})
        self.write("terms", {"terms": [{"id": "hours", "stages": ["VS-01.1"], "processes": ["PRC-01"]}]})
        self.write("dims_raw", {"by_bi_bus_matrix": [
            {"name": "期间", "rows": [{"fact": "工时条目", "cell": "✔"}, {"fact": "费用", "cell": "—"}]},
            {"name": "阶段", "rows": [{"fact": "工时条目", "cell": "✔"}]}]})
        self.write_dimensions()
        self.write("kpis", {"kpis": [self.metric]})

    def write(self, key, data):
        self.sources[key].write_text(yaml.safe_dump(data, allow_unicode=True), encoding="utf-8")

    def write_dimensions(self, rows=None):
        self.write("dims", {"dimensions": rows if rows is not None else [self.dimension],
                            "not_dimensions": [{"sources": {"bi_column": ["阶段"]}}]})

    def load(self):
        model = {"dim": {}, "metric": {}, "fact": {"FACT-01": {}}, "obj": {"OBJ-01": {}}}
        edges = []
        link = lambda kind, a, b, src, **extra: edges.append(
            {"kind": kind, "from": a, "to": b, "src": src, **extra})
        p.load_dimensions(model, link)
        p.load_metrics(model, link)
        return model, edges

    def test_arc02_loaders_fields_links_and_repeatability(self):
        model, edges = self.load()
        self.assertEqual("期间", model["dim"]["DIM-01"]["name"])
        self.assertEqual({"value": "未定"}, model["dim"]["DIM-01"]["scd"])
        metric = model["metric"]["KPI-01"]
        self.assertEqual(("工时", "业务指标", "hours", "M-01", None, ["FACT-01"], ["DIM-01"]),
                         tuple(metric[k] for k in ("zh", "kind", "term", "baseline", "vision_kpi", "facts", "dimensions")))
        self.assertEqual((["VS-01.1"], ["PRC-01"]), (metric["stages"], metric["processes"]))
        self.assertEqual({("dim-fact", "DIM-01", "FACT-01"), ("dim-obj", "DIM-01", "OBJ-01"),
                          ("metric-fact", "KPI-01", "FACT-01"), ("metric-dim", "KPI-01", "DIM-01"),
                          ("metric-term", "KPI-01", "hours")}, {(e["kind"], e["from"], e["to"]) for e in edges})
        self.assertTrue(all("#" in e["src"] for e in edges))
        self.assertEqual("工时条目", next(e for e in edges if e["kind"] == "dim-fact")["row"])
        self.assertEqual(yaml.safe_dump(self.load()).encode(), yaml.safe_dump(self.load()).encode())

    def test_arc02_invalid_and_duplicate_ids_fail_for_both_loaders(self):
        for key, section, row in (("dims", "dimensions", self.dimension), ("kpis", "kpis", self.metric)):
            original = self.sources[key].read_text()
            for rows, reason in (([row, row], "编号重复"), ([{**row, "id": "invalid"}], "编号无效"),
                                 ([{**row, "id": None}], "编号无效")):
                with self.subTest(key=key, reason=reason):
                    self.write(key, {section: rows})
                    with self.assertRaisesRegex(ValueError, reason):
                        self.load()
            self.sources[key].write_text(original)

    def test_arc02_missing_references_fail(self):
        for changes in ({"slices": [{"row": "工时条目", "facts": ["FACT-99"]}]}, {"backed_by": ["OBJ-99"]}):
            with self.subTest(changes=changes):
                self.write_dimensions([{**self.dimension, **changes}])
                with self.assertRaisesRegex(ValueError, "不存在"):
                    self.load()
        self.write_dimensions()
        for changes in ({"facts": ["FACT-99"]}, {"dimensions": ["DIM-99"]}, {"term": "missing"}):
            with self.subTest(changes=changes):
                self.write("kpis", {"kpis": [{**self.metric, **changes}]})
                with self.assertRaisesRegex(ValueError, "不存在"):
                    self.load()

    def test_arc02_missing_empty_and_malformed_catalogs_fail(self):
        for key, section in (("dims", "dimensions"), ("kpis", "kpis")):
            original = self.sources[key].read_text()
            for content in ("", "[]", f"{section}: invalid\n"):
                with self.subTest(key=key, content=content):
                    self.sources[key].write_text(content)
                    with self.assertRaises(ValueError):
                        self.load()
            self.sources[key].unlink()
            with self.assertRaises(FileNotFoundError):
                self.load()
            self.sources[key].write_text(original)

    def test_arc02_n12_1_empty_slices_and_n12_2_missing_row_or_column(self):
        for slices, expected in ((self.dimension["slices"], (0, 0)), ([], (1, 1)),
                                 ([{"row": "工时条目", "facts": []}], (1, 1)),
                                 ([{"row": "其他", "facts": ["FACT-01"]}], (0, 1))):
            with self.subTest(slices=slices):
                self.write_dimensions([{**self.dimension, "slices": slices}])
                gaps = p.defaultdict(list)
                p.check_dimensions(self.load()[0], gaps)
                self.assertEqual(expected, (len(gaps["N12-1"]), len(gaps["N12-2"])))
                if gaps["N12-2"]:
                    self.assertIn("行「工时条目」× 列「期间」", gaps["N12-2"][0])
        self.write_dimensions([])
        gaps = p.defaultdict(list)
        p.check_dimensions({"dim": {}}, gaps)
        self.assertEqual(1, len(gaps["N12-2"]))

    def test_metric_kind_outside_the_three_values_fails(self):
        for kind in ("经营指标", None, ""):
            self.write("kpis", {"kpis": [{**self.metric, "kind": kind}]})
            with self.assertRaisesRegex(ValueError, "kind 须为"):
                self.load()

    def test_arc02_n13_new_source_and_technical_metrics_excluded(self):
        self.write("terms", {"terms": [{"id": "hours", "stages": ["VS-01.1"], "processes": ["PRC-01"]},
                                      {"id": "technical", "stages": ["VS-01.2"]}]})
        self.write("kpis", {"kpis": [self.metric, {**self.metric, "id": "KPI-02", "term": None, "facts": []},
                                    {**self.metric, "id": "KPI-100", "term": "technical", "kind": "技术度量"},
                                    {**self.metric, "id": "KPI-101", "term": None, "kind": "技术度量", "facts": []}]})
        model, _ = self.load()
        model.update({key: {} for key in ("sh", "con", "act", "vs", "obj", "prc", "agg", "inv", "bc", "sd", "table")})
        model["fact"] = {}
        model["stage"] = {sid: {"name": sid, "gates": [], "in_stage": []} for sid in ("VS-01.1", "VS-01.2")}
        gaps = p.check(model)
        self.assertEqual(["VS-01.2 VS-01.2"], gaps["N13-1"])
        self.assertEqual(["KPI-02 工时"], gaps["N13-2"])
        self.assertEqual(["KPI-01 工时", "KPI-02 工时"], gaps["N13-3"])
        self.assertEqual(([], []), (model["metric"]["KPI-02"]["stages"], model["metric"]["KPI-02"]["processes"]))


class BindingNetworkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=p.ROOT)
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "bindings.yaml"
        patcher = patch.dict(p.SRC, bindings=self.path)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.binding = {"id": "BIND-001", "kpi": "KPI-01", "facts": ["FACT-01", "FACT-02"],
                        "formula": "文字口径", "formula_version": "2026.1", "time_basis": "业务时间",
                        "grain": "项目 × 月", "source_table": "reporting.rows", "source": "出处原文位置"}

    def write(self, bindings=None, pending=None):
        self.path.write_text(yaml.safe_dump({"meta": {"status": "proposal"},
                                            "bindings": [self.binding] if bindings is None else bindings,
                                            "pending": [] if pending is None else pending}, allow_unicode=True), encoding="utf-8")

    def load(self):
        model = {"bind": {}, "metric": {f"KPI-0{i}": {"zh": f"指标{i}", "kind": "业务指标",
                                                   "facts": [], "stages": [], "processes": []} for i in range(1, 5)},
                 "fact": {"FACT-01": {}, "FACT-02": {}}}
        model["metric"]["KPI-04"]["kind"] = "技术度量"
        edges = []
        p.load_bindings(model, lambda kind, a, b, src: edges.append(
            {"kind": kind, "from": a, "to": b, "src": src}))
        return model, edges

    def test_arc03_fields_links_metric_attachment_and_repeatability(self):
        self.write([self.binding, {**self.binding, "id": "BIND-002", "time_basis": "记录时间"},
                    {**self.binding, "id": "BIND-003", "time_basis": "期间"}])
        model, edges = self.load()
        self.assertEqual(self.binding, {k: v for k, v in model["bind"]["BIND-001"].items() if k != "src"})
        self.assertEqual(["BIND-001", "BIND-002", "BIND-003"], model["metric"]["KPI-01"]["bind"])
        self.assertEqual([], model["metric"]["KPI-02"]["bind"])
        self.assertEqual({(kind, f"BIND-00{i}", target) for i in range(1, 4) for kind, target in
                          (("bind-metric", "KPI-01"), ("bind-fact", "FACT-01"), ("bind-fact", "FACT-02"))},
                         {(e["kind"], e["from"], e["to"]) for e in edges})
        self.assertEqual(9, len(edges))
        self.assertTrue(all("#bindings[" in e["src"] for e in edges))
        self.assertTrue(model["bind"]["BIND-001"]["src"].endswith("#bindings[0]/BIND-001"))
        self.assertEqual(yaml.safe_dump((model, edges)).encode(), yaml.safe_dump(self.load()).encode())

    def test_arc03_invalid_and_duplicate_binding_ids_fail(self):
        for rows, reason in (([self.binding, self.binding], "编号重复"),
                             ([{**self.binding, "id": "BIND-01"}], "编号无效"),
                             ([{**self.binding, "id": None}], "编号无效")):
            with self.subTest(reason=reason, rows=rows):
                self.write(rows)
                with self.assertRaisesRegex(ValueError, reason):
                    self.load()

    def test_arc03_unknown_kpi_or_fact_and_invalid_time_basis_fail(self):
        for changes, reason in (({"kpi": "KPI-99"}, "不存在的 KPI"),
                                ({"facts": ["FACT-99"]}, "不存在的 FACT"),
                                ({"time_basis": "其他时间"}, "time_basis"),
                                ({"facts": "FACT-01"}, "facts 应为列表")):
            with self.subTest(changes=changes):
                self.write([{**self.binding, **changes}])
                with self.assertRaisesRegex(ValueError, reason):
                    self.load()

    def test_arc03_unknown_pending_kpi_or_bound_pending_kpi_fail(self):
        for kpi, reason in (("KPI-99", "不存在的 KPI"), ("KPI-01", "既有绑定又在 pending")):
            with self.subTest(kpi=kpi):
                self.write(pending=[{"kpi": kpi, "reason": "待确认"}])
                with self.assertRaisesRegex(ValueError, reason):
                    self.load()

    def test_arc03_malformed_catalogs_fail_and_pending_is_optional(self):
        for content in ("", "[]", "bindings: invalid", "bindings: [invalid]",
                        "bindings: []\npending: invalid", "bindings: []\npending: [invalid]"):
            with self.subTest(content=content):
                self.path.write_text(content)
                with self.assertRaises(ValueError):
                    self.load()
        self.path.write_text("bindings: []\n")
        self.assertEqual({}, self.load()[0]["bind"])

    def test_arc03_n14_1_complete_binding_has_no_gap(self):
        self.write()
        gaps = p.defaultdict(list)
        p.check_bindings(self.load()[0], gaps)
        self.assertEqual([], gaps["N14-1"])

    def test_arc03_n14_1_empty_fields_are_reported_without_loader_error(self):
        for field in ("facts", "formula_version", "time_basis"):
            for value in ([[], None] if field == "facts" else ["", None, "   "]):
                with self.subTest(field=field, value=value):
                    self.write([{**self.binding, field: value}])
                    gaps = p.defaultdict(list)
                    p.check_bindings(self.load()[0], gaps)
                    self.assertEqual([f"BIND-001（缺 {field}）"], gaps["N14-1"])
                    self.assertFalse(any(g.startswith("KPI-01 ") for g in gaps["N13-3"]))
            self.write([{k: v for k, v in self.binding.items() if k != field}])
            gaps = p.defaultdict(list)
            p.check_bindings(self.load()[0], gaps)
            self.assertEqual([f"BIND-001（缺 {field}）"], gaps["N14-1"])

    def test_arc03_n13_3_bound_pending_and_absent_with_report_counts(self):
        reason = "待确认" * 20
        self.write(pending=[{"kpi": "KPI-02", "reason": reason}])
        model, edges = self.load()
        model.update({key: {} for key in ("sh", "con", "act", "vs", "stage", "obj", "prc", "agg", "inv", "bc", "sd", "table")})
        model["fact"] = {}
        gaps = p.check(model)
        self.assertEqual([f"KPI-02 指标2（pending：{reason[:40]}）", "KPI-03 指标3"], gaps["N13-3"])
        self.assertEqual(reason, model["metric"]["KPI-02"]["bind_pending"]["reason"])
        self.assertTrue(model["metric"]["KPI-02"]["bind_pending"]["src"].endswith("#pending[0]"))
        output, report = p.render(model, edges, gaps)
        self.assertIn("同一输入按改前判据计 3 个缺口，按改后判据计 2 个缺口", report)
        self.assertIn("| N14-1 | 每个绑定有事实、公式版本与时间基准 | 0 |", report)
        self.assertIn("| 14 计算绑定 | bind | 1 |", report)
        self.assertEqual(["FACT-01", "FACT-02"], yaml.safe_load(output)["nodes"]["bind"]["BIND-001"]["facts"])
        self.assertEqual((output, report), p.render(model, edges, p.check(model)))

    def test_arc03_absent_file_keeps_node_empty_and_all_business_metrics_as_gaps(self):
        model, edges = self.load()
        self.assertEqual({}, model["bind"])
        self.assertEqual([], edges)
        gaps = p.defaultdict(list)
        p.check_bindings(model, gaps)
        self.assertEqual([f"KPI-0{i} 指标{i}" for i in range(1, 4)], gaps["N13-3"])
        self.assertEqual([], gaps["N14-1"])


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
            facts = root / "facts.yaml"
            facts.write_text("facts: []\n", encoding="utf-8")
            dimensions, kpis = root / "dimensions.yaml", root / "kpis.yaml"
            dimensions.write_text("dimensions: []\n", encoding="utf-8")
            kpis.write_text("kpis: []\n", encoding="utf-8")
            bindings = root / "bindings.yaml"
            bindings.write_text("bindings: []\n", encoding="utf-8")
            text = p.SRC["agg"].read_text(encoding="utf-8")
            start, end = text.index("### 2.2"), text.index("### 2.3")
            draft.write_text(text[:start] + text[end:], encoding="utf-8")
            row = {"id": "AGG-01", "聚合（根）": "往来单位（OBJ-01）", "内含": "OBJ-02", "对应": "一对一", "不变量": "INV-01"}
            planned.write_text(yaml.safe_dump({"aggregates": [row]}, allow_unicode=True), encoding="utf-8")
            with patch.dict(p.SRC, planned=planned, agg=draft, facts=facts, dims=dimensions, kpis=kpis, bindings=bindings):
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
        self.assertIn('N11-2', p.CHECKS)
        self.assertIn('N11-3', p.CHECKS)
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
        with self.assertRaisesRegex(FileNotFoundError, 'build_all.py.*generated-docs'):
            self.load()

    def test_arc09_repeated_read_has_identical_bytes(self):
        self.assertEqual(yaml.safe_dump(self.load()).encode(), yaml.safe_dump(self.load()).encode())


class FactNetworkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=p.ROOT)
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "facts.yaml"
        patcher = patch.dict(p.SRC, facts=self.path)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.operating = {"id": "FACT-01", "name": "工时成本事实", "kind": "经营事实",
                          "owner_bc": "operations.facts", "wave": "W1",
                          "produced_by": ["CMD-12345678", "EVT:cost.timesheet.TimesheetApproved"],
                          "sources": {"object": ["OBJ-01"]}}
        self.analytic = {"id": "FACT-02", "name": "工时汇总", "kind": "分析事实",
                         "owner_bc": "operations.facts", "wave": "W1", "derives_from": ["FACT-01"],
                         "analytic_source": "原文" * 30}

    def write(self, rows=None):
        self.path.write_text(yaml.safe_dump({"facts": rows if rows is not None else [self.analytic, self.operating],
                                             "pending": [{"id": "P-01"}], "not_facts": [{"id": "NF-01"}]},
                                            allow_unicode=True), encoding="utf-8")

    def load(self):
        model = {"fact": {}, "bc": {"B-16": {}}, "cmd": {"CMD-12345678": {}},
                 "evt": {"cost.timesheet.TimesheetApproved": {}}, "obj": {
                     "OBJ-01": {"name": "工时单", "active": True, "primary": True, "facts": "工时成本事实"}}}
        edges = []
        p.load_facts(model, lambda kind, a, b, src: edges.append(
            {"kind": kind, "from": a, "to": b, "src": src}), {"operations.facts": "B-16"})
        return model, edges

    def gaps(self, model):
        gaps = p.defaultdict(list)
        p.check_facts(model, gaps)
        return gaps

    def test_arc16_loads_nodes_links_forward_references_and_repeats(self):
        self.write()
        model, edges = self.load()
        self.assertEqual({"FACT-01", "FACT-02"}, set(model["fact"]))
        self.assertEqual(("工时成本事实", "经营事实", "operations.facts", "W1"),
                         tuple(model["fact"]["FACT-01"][key] for key in ("name", "kind", "owner_bc", "wave")))
        self.assertEqual({("cmd-fact", "CMD-12345678", "FACT-01"),
                          ("evt-fact", "cost.timesheet.TimesheetApproved", "FACT-01"),
                          ("fact-fact", "FACT-01", "FACT-02"), ("obj-fact", "OBJ-01", "FACT-01"),
                          ("fact-bc", "FACT-01", "B-16"), ("fact-bc", "FACT-02", "B-16")},
                         {(e["kind"], e["from"], e["to"]) for e in edges})
        self.assertTrue(model["fact"]["FACT-01"]["src"].endswith("facts.yaml#facts[1]/FACT-01"))
        self.assertTrue(all("#facts[" in e["src"] for e in edges))
        self.assertEqual([], self.gaps(model)["N11-2"])
        self.assertEqual([], self.gaps(model)["N11-3"])
        self.assertEqual(yaml.safe_dump(self.load()).encode(), yaml.safe_dump(self.load()).encode())

    def test_arc16_retired_object_source_is_kept_without_link(self):
        self.operating["sources"] = {"object": ["OBJ-01", "OBJ-76"]}
        self.write()
        _, edges = self.load()
        self.assertEqual({"OBJ-01"}, {e["from"] for e in edges if e["kind"] == "obj-fact"})

    def test_arc16_missing_file_key_duplicate_invalid_id_and_kind_fail(self):
        with self.assertRaises(FileNotFoundError):
            self.load()
        self.path.write_text("meta: {}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "facts"):
            self.load()
        self.write([self.operating, self.operating])
        with self.assertRaisesRegex(ValueError, "编号重复"):
            self.load()
        for changes, message in (({"id": "FACT-1"}, "编号无效"), ({"kind": "候选"}, "kind")):
            with self.subTest(changes=changes):
                self.write([{**self.operating, **changes}])
                with self.assertRaisesRegex(ValueError, message):
                    self.load()

    def test_arc16_node_11_renders_fact_count_and_report_gaps(self):
        self.write([{**self.operating, "id": f"FACT-{index:02d}", "produced_by": []}
                    for index in range(1, 38)])
        model, edges = self.load()
        model.update(inv={}, table={}, api={}, mod={})
        first = p.render(model, edges, self.gaps(model))
        self.assertEqual(first, p.render(model, edges, self.gaps(model)))
        self.assertEqual(37, yaml.safe_load(first[0])["meta"]["counts"]["fact"])
        self.assertIn("| 11 业务事实 | fact | 37 |", first[1])
        self.assertIn("## N11-2 每条经营事实有产生它的命令或事件（37）", first[1])

    def test_arc16_unknown_command_event_fact_object_bc_and_bad_producer_fail(self):
        for changes, message in (({"produced_by": ["CMD-missing"]}, "不存在的 cmd"),
                                 ({"produced_by": ["EVT:missing"]}, "不存在的 evt"),
                                 ({"derives_from": ["FACT-99"]}, "不存在的 FACT"),
                                 ({"sources": {"object": ["OBJ-99"]}}, "不存在的 OBJ"),
                                 ({"owner_bc": "missing"}, "不存在的 BC"),
                                 ({"produced_by": ["other"]}, "写法无效")):
            with self.subTest(changes=changes):
                self.write([{**self.operating, **changes}])
                with self.assertRaisesRegex(ValueError, message):
                    self.load()

    def test_arc16_n11_1_blank_dash_and_notes_are_gaps(self):
        self.write()
        model, _ = self.load()
        for value in ("", " \t", "—", "-", " —（注） ", " -（注）"):
            with self.subTest(value=value):
                model["obj"]["OBJ-01"]["facts"] = value
                self.assertEqual(["OBJ-01 工时单"], self.gaps(model)["N11-1"])
        model["obj"]["OBJ-01"]["facts"] = " 工时成本事实 "
        self.assertEqual([], self.gaps(model)["N11-1"])
        model["obj"]["OBJ-01"].update(facts="—（注）", primary=False)
        self.assertEqual([], self.gaps(model)["N11-1"])

    def test_arc16_n11_2_missing_producer_is_report_only(self):
        self.operating["produced_by"] = []
        self.write()
        model, _ = self.load()
        self.assertEqual(["FACT-01 工时成本事实"], self.gaps(model)["N11-2"])
        self.assertEqual([], self.gaps(model)["N11-3"])

    def test_arc16_n11_3_empty_or_any_analytic_source_is_report_only(self):
        for refs in ([], ["FACT-01", "FACT-02"]):
            with self.subTest(refs=refs):
                self.analytic["derives_from"] = refs
                self.write()
                model, _ = self.load()
                self.assertEqual([f"FACT-02 工时汇总（analytic_source：{'原文' * 20}）"],
                                 self.gaps(model)["N11-3"])


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
                api.unlink()
                with self.assertRaisesRegex(ValueError, "bash scripts/generate-api.sh"):
                    p.load_interfaces(model, lambda *args, **kwargs: None, {"cost.timesheet": "B-06"})
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


class EventNetworkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=p.ROOT)
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.sources = {key: self.root / f"{key}.yaml" for key in ("built", "cmd", "cmd_map", "modules")}
        self.sources["events"] = self.root / "events"
        self.sources["events"].mkdir()
        for name in ("approval", "attendance", "identity", "integration", "numbering", "timesheet", "workforce"):
            self.event_file(name, {})
        self.event = {"name": "TimesheetSubmitted", "title": "工时单已提交", "x-event": {
            "eventType": "cost.timesheet.TimesheetSubmitted", "version": 1,
            "publisher": "cost.timesheet", "consumers": ["platform.approval", "cost.timesheet"]}}
        self.ident = self.event["x-event"]["eventType"]
        self.aggregate = {"id": "cost.timesheet/Timesheet", "bc": "cost.timesheet", "commands": ["SubmitTimesheet"],
                          "events": [self.ident], "events_in_module": [self.ident]}
        self.command = {"aggregate": self.aggregate["id"], "src": "commands.yaml#commands/CMD-12345678"}
        self.aggregate_mapping = {}
        self.write("built", {"aggregates": [self.aggregate]})
        self.write("cmd", {"built": [{"aggregate": self.aggregate["id"], "module": "cost.timesheet", "class": "SubmitTimesheet"}]})
        self.sources["modules"].write_text("# 订阅方向登记\nplatform.approval -> cost.timesheet # 已登记\ncost.timesheet ->\n", encoding="utf-8")
        self.mod_to_bc = {"cost.timesheet": "B-06", "platform.approval": "B-40"}
        patcher = patch.dict(p.SRC, self.sources)
        patcher.start()
        self.addCleanup(patcher.stop)

    def write(self, key, data):
        self.sources[key].write_text(yaml.safe_dump(data, allow_unicode=True), encoding="utf-8")

    def event_file(self, name, messages):
        (self.sources["events"] / f"{name}.yaml").write_text(
            yaml.safe_dump({"components": {"messages": messages}}, allow_unicode=True), encoding="utf-8")

    def load(self):
        self.event_file("timesheet", {"TimesheetSubmitted": self.event})
        model = {"evt": {}, "cmd": {"CMD-12345678": self.command}, "agg": self.aggregate_mapping}
        edges = []
        p.load_events(model, lambda kind, a, b, src, **extra: edges.append(
            {"kind": kind, "from": a, "to": b, "src": src, **extra}), self.mod_to_bc)
        return model, edges

    def gaps(self, model):
        gaps = p.defaultdict(list)
        p.check_events(model, gaps)
        return gaps

    def test_arc10_catalog_edges_coverage_and_mapping_absent(self):
        (self.sources["events"] / "asyncapi.yaml").write_text("不可读取的生成物", encoding="utf-8")
        sources = p.source_files()
        self.assertNotIn("events", sources)
        self.assertEqual(7, len([key for key in sources if key.startswith("evt_")]))
        self.assertTrue(all(path.is_file() for path in sources.values()))
        self.assertNotIn("asyncapi.yaml", [path.name for path in sources.values()])
        model, edges = self.load()
        node = model["evt"][self.ident]
        self.assertEqual(("TimesheetSubmitted", "工时单已提交", 1, "cost.timesheet", "B-06"),
                         tuple(node[k] for k in ("name", "title", "version", "publisher_module", "publisher_bc")))
        self.assertIsNone(node["fact"])
        self.assertEqual(["cost.timesheet", "platform.approval"], node["subscribers"])
        self.assertIn("timesheet.yaml#components.messages.TimesheetSubmitted", node["src"])
        self.assertEqual({"evt-bc": 1, "evt-agg": 2, "evt-mod": 2}, dict(p.Counter(e["kind"] for e in edges)))
        self.assertEqual({"events", "module"}, {e["via"] for e in edges if e["kind"] == "evt-agg"})
        self.assertEqual(([], []), (self.gaps(model)["N10-5"], self.gaps(model)["N10-6"]))
        model.update(inv={}, table={}, api={})
        coverage = p.coverage_counts(model, edges)
        self.assertEqual({"covered": 1, "total": 1, "via": {"events": 1, "module": 1}}, coverage["事件→聚合"])
        self.assertEqual(1, coverage["事件→发布 BC"]["covered"])
        self.assertEqual(yaml.safe_dump(self.load()).encode(), yaml.safe_dump(self.load()).encode())

    def test_arc10_missing_publisher_version_and_unregistered_dependency(self):
        self.event["x-event"].update(publisher=None, version=None)
        model, edges = self.load()
        self.assertIsNone(model["evt"][self.ident]["publisher_module"])
        self.assertFalse(any(e["kind"] == "evt-bc" for e in edges))
        gap = self.gaps(model)["N10-5"]
        self.assertEqual(1, len(gap))
        self.assertIn("发布者缺失", gap[0])
        self.assertIn("版本缺失", gap[0])
        for publisher, version, reason in ((None, 1, "发布者"), ("cost.timesheet", None, "版本")):
            with self.subTest(reason=reason):
                self.event["x-event"].update(publisher=publisher, version=version)
                gaps = self.gaps(self.load()[0])["N10-5"]
                self.assertEqual(1, len(gaps))
                self.assertIn(reason, gaps[0])
        self.event["x-event"].update(publisher="cost.timesheet", version=1)
        self.sources["modules"].write_text("cost.timesheet -> platform.approval\nplatform.approval ->\n", encoding="utf-8")
        model, _ = self.load()
        self.assertEqual(1, len(self.gaps(model)["N10-6"]))
        self.assertIn("platform.approval → cost.timesheet 未登记", self.gaps(model)["N10-6"][0])
        self.event["x-event"]["subscribers"] = ["platform.audit"]
        self.assertEqual([], self.gaps(self.load()[0])["N10-6"])

    def test_arc10_filename_fallback_multiple_publishers_and_subscribers(self):
        del self.event["x-event"]["publisher"]
        self.event["x-event"]["subscribers"] = ["platform.approval"]
        model, edges = self.load()
        self.assertEqual("cost.timesheet", model["evt"][self.ident]["publisher_module"])
        self.assertTrue(next(e["src"] for e in edges if e["kind"] == "evt-mod").endswith(".x-event.subscribers"))
        self.mod_to_bc = {}
        self.assertEqual(1, len(self.gaps(self.load()[0])["N10-5"]))
        self.event["x-event"]["publisher"] = ["cost.timesheet", "platform.approval"]
        self.assertEqual(1, len(self.gaps(self.load()[0])["N10-5"]))
        duplicate = copy.deepcopy(self.event)
        duplicate["x-event"].update(publisher="platform.approval", version=2)
        self.event_file("approval", {"Alias": duplicate})
        model, _ = self.load()
        self.assertEqual(1, len(model["evt"]))
        self.assertEqual(2, len(model["evt"][self.ident]["declarations"]))
        self.assertIn("版本缺失或不唯一", self.gaps(model)["N10-5"][0])

    def test_arc10_command_mapping_requires_same_aggregate_and_both_class_catalogs(self):
        self.write("cmd_map", {"entries": [{"class": "SubmitTimesheet", "module": "cost.timesheet",
                                          "built_aggregate": self.aggregate["id"], "commands": ["CMD-12345678"]}]})
        _, edges = self.load()
        command_edges = [e for e in edges if e["kind"] == "cmd-evt"]
        self.assertEqual(1, len(command_edges))
        self.assertEqual(("CMD-12345678", self.ident, "mapping"),
                         tuple(command_edges[0][k] for k in ("from", "to", "via")))
        self.assertIn("cmd_map.yaml#entries[0]", command_edges[0]["src"])
        self.command["aggregate"] = "AGG-01"
        self.assertFalse(any(e["kind"] == "cmd-evt" for e in self.load()[1]))
        self.aggregate_mapping[self.aggregate["id"]] = {"planned": ["AGG-01"], "relation": "same"}
        mapped = [e for e in self.load()[1] if e["kind"] == "cmd-evt"]
        self.assertEqual(1, len(mapped))
        self.assertIn("mapping.yaml#entries/", mapped[0]["aggregate_mapping_src"])
        self.aggregate_mapping[self.aggregate["id"]]["relation"] = "pending"
        self.assertFalse(any(e["kind"] == "cmd-evt" for e in self.load()[1]))
        self.write("cmd", {"built": []})
        with self.assertRaisesRegex(ValueError, "两份目录"):
            self.load()

    def test_n10_4_reports_command_classes_without_a_settled_mapping(self):
        entry = {"class": "SubmitTimesheet", "module": "cost.timesheet", "built_aggregate": self.aggregate["id"],
                 "commands": ["CMD-12345678"], "relation": "same"}
        self.write("cmd_map", {"entries": [entry]})
        self.assertEqual([], self.gaps(self.load()[0])["N10-4"])
        entry["relation"] = "pending"
        self.write("cmd_map", {"entries": [entry]})
        self.assertEqual(1, len(self.gaps(self.load()[0])["N10-4"]))
        self.write("cmd_map", {"entries": []})
        self.assertIn("未登记对照", self.gaps(self.load()[0])["N10-4"][0])

    def test_arc10_invalid_or_missing_catalog_fails(self):
        self.event["x-event"]["consumers"] = [42]
        with self.assertRaisesRegex(ValueError, "模块名"):
            self.load()
        self.event["x-event"]["consumers"] = []
        del self.event["x-event"]["eventType"]
        with self.assertRaisesRegex(ValueError, "eventType"):
            self.load()
        (self.sources["events"] / "approval.yaml").unlink()
        with self.assertRaises(FileNotFoundError):
            self.load()


if __name__ == "__main__":
    unittest.main()
