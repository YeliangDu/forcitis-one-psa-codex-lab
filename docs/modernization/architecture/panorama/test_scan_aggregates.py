"""ARC-1021-ARC-08：聚合扫描、转换和破坏验证。"""
import contextlib
import datetime
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

import scan_aggregates as s


class AggregateScanTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.registry = {'cost_timesheet.timesheets': {'role': 'AGGREGATE_ROOT', 'aggregate': 'Timesheet'},
                         'cost_timesheet.lines': {'role': 'AGGREGATE_CHILD', 'aggregate': 'Timesheet'},
                         'cost_timesheet.not_a_root': {'role': 'QUEUE', 'aggregate': 'QueueEntry'},
                         'other_module.wrong': {'role': 'AGGREGATE_CHILD', 'aggregate': 'Timesheet'}}
        self.write_registry()
        self.java('domain', 'Timesheet', 'import com.forcitis.one.kernel.AggregateRoot;\n'
                  'import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;\n'
                  'public class Timesheet extends AggregateRoot {\n'
                  'void submit() { record(() -> new TimesheetSubmitted()); } }')
        self.java('application', 'SubmitTimesheet',
                  'import com.forcitis.one.cost.timesheet.domain.Timesheet;\n'
                  'public class SubmitTimesheet { Timesheet sheet; }')
        self.java('events', 'TimesheetSubmitted', 'import com.forcitis.one.kernel.event.DomainEvent;\n'
                  'public record TimesheetSubmitted() implements DomainEvent {}')
        self.java('events', 'TimesheetRejected', 'import com.forcitis.one.kernel.event.DomainEvent;\n'
                  'public record TimesheetRejected() implements DomainEvent {}')

    def write_registry(self):
        self.write(s.REGISTRY, yaml.safe_dump({'tables': self.registry}))

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding='utf-8')
        return target

    def java(self, layer, name, body, module='cost.timesheet'):
        return self.write(s.JAVA / Path(module.replace('.', '/')) / layer / (name + '.java'),
                          f'package com.forcitis.one.{module}.{layer};\n{body}\n')

    def test_arc08_comments_strings_and_non_kernel_parent_are_not_roots(self):
        self.java('domain', 'Noise', 'public class Noise {\n'
                  '// class Line extends AggregateRoot {}\n'
                  '/* class Block extends AggregateRoot {} */\n'
                  'String s = "class StringRoot extends AggregateRoot {}";\n'
                  'String block = """\nclass TextRoot extends AggregateRoot {}\n"""; }')
        self.java('domain', 'Unrelated', 'import third.party.AggregateRoot;\n'
                  'class Unrelated extends AggregateRoot {}')
        self.java('application', 'NoiseCommand', 'class NoiseCommand {\n'
                  'String x = "Timesheet"; /* Timesheet sheet; */ }')
        self.assertEqual(['cost.timesheet/Timesheet'], [r['id'] for r in s.collect(self.root)])
        self.assertEqual(['SubmitTimesheet'], s.collect(self.root)[0]['commands'])

    def test_arc08_union_schema_tables_and_events_with_fallback(self):
        self.java('domain', 'QueueEntry', 'class QueueEntry {}')
        self.java('domain', 'RegistryRoot', 'class RegistryRoot {}')
        self.registry['cost_timesheet.registry_roots'] = {'role': 'AGGREGATE_ROOT', 'aggregate': 'RegistryRoot'}
        self.write_registry()
        result = {r['id']: r for r in s.collect(self.root)}
        sheet = result['cost.timesheet/Timesheet']
        self.assertEqual(['code', 'registry'], sheet['source'])
        self.assertEqual(['registry'], result['cost.timesheet/RegistryRoot']['source'])
        self.assertNotIn('cost.timesheet/QueueEntry', result)
        self.assertEqual(['cost_timesheet.lines', 'cost_timesheet.timesheets'], sheet['tables'])
        self.assertEqual(['cost.timesheet.TimesheetSubmitted'], sheet['events'])
        self.assertEqual(['cost.timesheet.TimesheetRejected', 'cost.timesheet.TimesheetSubmitted'], sheet['events_in_module'])

    def test_arc08_command_publish_is_evidence_but_plain_constructor_is_not(self):
        self.java('application', 'SubmitTimesheet', 'import com.forcitis.one.cost.timesheet.domain.Timesheet;\n'
                  'import com.forcitis.one.cost.timesheet.events.TimesheetRejected;\n'
                  'class SubmitTimesheet { Timesheet sheet; void execute() { events.publish(new TimesheetRejected()); } }')
        sheet = s.collect(self.root)[0]
        self.assertEqual(['cost.timesheet.TimesheetRejected', 'cost.timesheet.TimesheetSubmitted'], sheet['events'])
        self.assertNotIn('events_in_module', sheet)
        self.java('application', 'SubmitTimesheet', 'import com.forcitis.one.cost.timesheet.domain.Timesheet;\n'
                  'import com.forcitis.one.cost.timesheet.events.TimesheetRejected;\n'
                  'class SubmitTimesheet { Timesheet sheet; Object unused = new TimesheetRejected(); }')
        self.assertEqual(['cost.timesheet.TimesheetSubmitted'], s.collect(self.root)[0]['events'])

    def test_arc08_application_record_referencing_root_is_a_class(self):
        self.java('application', 'SheetView', 'import com.forcitis.one.cost.timesheet.domain.Timesheet;\n'
                  'record SheetView(Timesheet sheet) {}')
        self.assertEqual(['SheetView', 'SubmitTimesheet'], s.collect(self.root)[0]['commands'])

    def test_arc08_mutation_removes_code_only_root(self):
        path = self.java('domain', 'TransientRoot', 'import com.forcitis.one.kernel.AggregateRoot;\n'
                         'class TransientRoot extends AggregateRoot {}')
        before = s.collect(self.root)
        self.assertIn('cost.timesheet/TransientRoot', [r['id'] for r in before])
        original = path.read_text(encoding='utf-8')
        path.write_text(original.replace('extends AggregateRoot', ''), encoding='utf-8')
        self.assertEqual(['cost.timesheet/Timesheet'], [r['id'] for r in s.collect(self.root)])
        path.write_text(original, encoding='utf-8')
        self.assertEqual(before, s.collect(self.root))

    def test_arc08_registry_root_missing_class_and_missing_input_fail(self):
        self.registry['cost_timesheet.absent'] = {'role': 'AGGREGATE_ROOT', 'aggregate': 'Absent'}
        self.write_registry()
        with self.assertRaisesRegex(ValueError, '未唯一对应'):
            s.collect(self.root)
        (self.root / s.REGISTRY).unlink()
        with self.assertRaises(FileNotFoundError):
            s.collect(self.root)
        with tempfile.TemporaryDirectory() as empty:
            with self.assertRaisesRegex(ValueError, '缺失或空'):
                s.collect(Path(empty))

    def write_unbuilt(self, rows):
        (self.root / s.UNBUILT).parent.mkdir(parents=True, exist_ok=True)
        (self.root / s.UNBUILT).write_text(yaml.safe_dump({'roots': rows}, allow_unicode=True), encoding='utf-8')

    def test_unbuilt_listed_root_is_reported_not_built(self):
        self.registry['cost_timesheet.absent'] = {'role': 'AGGREGATE_ROOT', 'aggregate': 'Absent'}
        self.write_registry()
        self.write_unbuilt([{'table': 'cost_timesheet.absent', 'owner': 'one-x', 'due': '2026-11-15', 'reason': '建表先于代码'}])
        unbuilt = []
        built = s.collect(self.root, today=datetime.date(2026, 10, 22), unbuilt=unbuilt)
        self.assertEqual(['cost_timesheet.absent'], [row['table'] for row in unbuilt])
        self.assertNotIn('Absent', [row['id'].split('/')[-1] for row in built])

    def test_unbuilt_entry_past_due_stale_or_incomplete_fails(self):
        self.registry['cost_timesheet.absent'] = {'role': 'AGGREGATE_ROOT', 'aggregate': 'Absent'}
        self.write_registry()
        today = datetime.date(2026, 10, 22)
        self.write_unbuilt([{'table': 'cost_timesheet.absent', 'owner': 'one-x', 'due': '2026-10-21', 'reason': 'r'}])
        with self.assertRaisesRegex(ValueError, '已过期'):
            s.collect(self.root, today=today)
        self.write_unbuilt([{'table': 'cost_timesheet.absent', 'owner': 'one-x', 'due': '2026-11-15'}])
        with self.assertRaisesRegex(ValueError, '每项须有'):
            s.collect(self.root, today=today)
        self.write_unbuilt([{'table': 'cost_timesheet.absent', 'owner': 'one-x', 'due': '2026-11-15', 'reason': 'r'},
                            {'table': 'cost_timesheet.nowhere', 'owner': 'one-x', 'due': '2026-11-15', 'reason': 'r'}])
        with self.assertRaisesRegex(ValueError, '不是注册表中的 AGGREGATE_ROOT'):
            s.collect(self.root, today=today)
        self.write_unbuilt([{'table': 'cost_timesheet.absent', 'owner': 'one-x', 'due': '2026-12-22', 'reason': 'r'}])
        with self.assertRaisesRegex(ValueError, '超过 60 天上限'):
            s.collect(self.root, today=today)

    def test_arc08_duplicate_java_identity_fails(self):
        self.java('domain', 'Timesheet', 'class Timesheet {}', module='other.module')
        path = self.root / s.JAVA / 'other/module/domain/Timesheet.java'
        path.write_text('package com.forcitis.one.cost.timesheet.domain; class Timesheet {}', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '编号重复'):
            s.collect(self.root)

    def test_arc08_generation_is_byte_identical_and_check_does_not_write(self):
        self.write(s.DRAFT, planned_text())
        with contextlib.redirect_stdout(io.StringIO()):
            s.generate(self.root)
            first = {p.name: p.read_bytes() for p in (self.root / s.OUTPUT).iterdir()}
            s.generate(self.root)
            self.assertEqual(first, {p.name: p.read_bytes() for p in (self.root / s.OUTPUT).iterdir()})
            s.generate(self.root, check=True)
            s.generate(self.root, check=True)
            built = self.root / s.OUTPUT / 'built.yaml'
            built.write_bytes(b'stale\n')
            with self.assertRaisesRegex(ValueError, '产物不一致'):
                s.generate(self.root, check=True)
            self.assertEqual(b'stale\n', built.read_bytes())


def planned_text():
    header = '| ' + ' | '.join(s.HEADERS) + ' |\n'
    row = '| AGG-01 | **工时**（OBJ-01） | `行` | 一对一 | INV-01 | 提交（→ 已提交） | 已提交 | — |\n'
    return '### 2.2 聚合清单\n**B-01 工时**（已建）\n' + header + '|---' * 8 + '|\n' + row + '### 2.3 协作\n'


class PlannedConversionTest(unittest.TestCase):
    def test_arc08_cells_are_preserved_and_references_counted_separately(self):
        text = planned_text().replace('### 2.3',
                    '| REF-01 | 只读登记 | — | 无聚合 | — | — | — | — |\n### 2.3')
        data = s.parse_planned(text)
        row = data['aggregates'][0]
        self.assertEqual('**工时**（OBJ-01）', row['聚合'])
        self.assertEqual('`行`', row['内含'])
        self.assertEqual('**B-01 工时**（已建）', row['bc_heading'])
        self.assertEqual((1, 1, 2), tuple(data['meta'][k] for k in ('count', 'reference_count', 'row_count')))

    def test_arc08_duplicate_bad_header_column_count_and_missing_section_fail(self):
        text = planned_text()
        row = next(line for line in text.splitlines() if line.startswith('| AGG-'))
        for bad, message in ((text.replace(row, row + '\n' + row), '编号重复'),
                             (text.replace('内含', '改表头'), '表头'),
                             (text.replace(' | — |', ' | — | 多一列 |'), '列数'),
                             (text.replace('### 2.3', '### 9.3'), '边界')):
            with self.subTest(message=message), self.assertRaisesRegex(ValueError, message):
                s.parse_planned(bad)

    def test_arc08_cli_failure_returns_nonzero(self):
        with patch.object(s, 'generate', side_effect=ValueError('编号重复')):
            with contextlib.redirect_stderr(io.StringIO()) as err:
                self.assertEqual(1, s.main(['--check']))
            self.assertIn('编号重复', err.getvalue())


if __name__ == '__main__':
    unittest.main()
