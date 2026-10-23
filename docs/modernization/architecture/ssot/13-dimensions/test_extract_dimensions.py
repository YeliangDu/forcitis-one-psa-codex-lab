#!/usr/bin/env python3
"""ARC-1021-ARC-13：原文、拒绝无效输入与字节一致性自测。"""
import contextlib
import io
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

import extract_dimensions as ed


class ExtractDimensionsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        paths = [ed.facts.BI, ed.METRICS, ed.REGISTRY]
        paths.extend(path.relative_to(ed.ROOT) for path in (ed.ROOT / ed.MIGRATIONS).rglob('*.sql'))
        for path in paths:
            (self.root / path).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ed.ROOT / path, self.root / path)

    def change(self, path, old, new):
        target = self.root / path
        text = target.read_text(encoding='utf-8')
        self.assertIn(old, text)
        target.write_text(text.replace(old, new, 1), encoding='utf-8')

    def raw(self):
        return yaml.safe_load(ed.documents(self.root)['dimensions.raw.yaml'])

    def test_counts_and_all_bus_cells_preserved_without_symbol_interpretation(self):
        raw = self.raw()
        self.assertEqual({'by_bi_bus_matrix': 14, 'by_metric_baseline': 48,
                          'by_versioned_master_tables': 8,
                          'literal_differences': {'only_bi_bus_matrix': 14, 'only_metric_baseline_oat': 39}},
                         raw['meta']['counts'])
        lines = (self.root / ed.facts.BI).read_text().splitlines()
        columns = raw['by_bi_bus_matrix']
        self.assertEqual(ed.facts.HEADER, [column['name'] for column in columns])
        for index, column in enumerate(columns):
            self.assertEqual(16, len(column['rows']))
            for row in column['rows']:
                source = lines[int(row['source'].rsplit(':', 1)[1]) - 1]
                cells = [cell.strip() for cell in source[1:-1].split('|')]
                self.assertEqual(cells[0], row['fact'])
                self.assertEqual(cells[index], row['cell'])

    def test_all_metric_fields_and_subsection_titles_preserved(self):
        lines = (self.root / ed.METRICS).read_text().splitlines()
        rows = ed.by_metric_baseline(self.root)
        self.assertEqual([f'M-{i:02}' for i in range(1, 49)], [row['metric'] for row in rows])
        for row in rows:
            index = int(row['source'].rsplit(':', 1)[1]) - 1
            cells = [cell.strip() for cell in lines[index][1:-1].split('|')]
            self.assertEqual(cells[3:7], [row[key] for key in ('OAT', '期间', '加总', '事实 BC')])
            self.assertEqual(next(line for line in reversed(lines[:index]) if line.startswith('### ')), row['section'])
        self.assertEqual('WON_PROPOSAL_VALUE、WR', rows[1]['OAT'])
        self.assertEqual('—（待定扩展原语）', rows[2]['OAT'])

    def test_registry_values_absence_empty_lists_and_ddl_source_lines(self):
        original = yaml.safe_load((self.root / ed.REGISTRY).read_text())['tables']
        rows = ed.by_versioned_master_tables(self.root)
        self.assertEqual(8, len(rows))
        for row in rows:
            expected = original[row['table']]
            for field in ('role', 'mutability', 'retention', 'lineage', 'writers', 'readers'):
                self.assertEqual(expected.get(field), row[field])
            self.assertEqual(['readers'], row['missing_fields'])
            self.assertEqual(2, len(row['effective_columns']))
            for column in row['effective_columns']:
                path, line = column['source'].rsplit(':', 1)
                source = (self.root / path).read_text().splitlines()[int(line) - 1]
                self.assertEqual(column['name'], source.split()[0])
                self.assertEqual(column['type'], source.split()[1].rstrip(','))
        self.assertEqual([], rows[-1]['writers'])
        self.assertIsNone(rows[-1]['readers'])

    def test_gaps_are_complete_literal_cell_sets(self):
        documents = ed.documents(self.root)
        raw = yaml.safe_load(documents['dimensions.raw.yaml'])
        bus = {row['name'] for row in raw['by_bi_bus_matrix']}
        oat = {row['OAT'] for row in raw['by_metric_baseline']}
        listed = {line.split('`')[1] for line in documents['GAPS.md'].splitlines() if line.startswith('- `')}
        self.assertEqual(bus ^ oat, listed)
        self.assertIn('—', listed)
        self.assertIn('CM、CMR', listed)
        self.assertNotIn('CMR', listed)

    def test_bus_structure_and_markers(self):
        path = self.root / ed.facts.BI
        original = path.read_text()
        matrix = ed.facts.parse_bus_matrix(original)
        header_index = int(matrix['source'].rsplit(':', 1)[1]) - 1
        for offset, old, new in [(0, '业务过程事实', '未知列'), (1, '---', 'bad'), (0, '粒度', '')]:
            with self.subTest(offset=offset, old=old):
                lines = original.splitlines()
                lines[header_index + offset] = lines[header_index + offset].replace(old, new, 1)
                path.write_text('\n'.join(lines))
                with self.assertRaises(ValueError):
                    ed.by_bi_bus_matrix(self.root)
        path.write_text(original)
        cell = matrix['rows'][0]['cells']['期间']
        source_line = matrix['rows'][0]['source'].rsplit(':', 1)[1]
        lines = original.splitlines()
        cells = lines[int(source_line) - 1].split('|')
        cells[5] = ' 任意新标记（不解释） '
        lines[int(source_line) - 1] = '|'.join(cells)
        path.write_text('\n'.join(lines))
        self.assertNotEqual(cell, '任意新标记（不解释）')
        self.assertEqual('任意新标记（不解释）', ed.by_bi_bus_matrix(self.root)[4]['rows'][0]['cell'])

    def test_metric_structure_rejected(self):
        path = self.root / ed.METRICS
        original = path.read_text()
        mutations = [('| OAT |', '| oat |'), ('| M-01 |', '| M-02 |'),
                     ('| PIPE | I |', '| PIPE |'), ('| PIPE |', '| |'),
                     ('|---|---|---|---|---|---|---|---|---|---|---|---|', '|bad|'),
                     ('### 3.2 商机与订单', '### 3.7 商机与订单')]
        for old, new in mutations:
            with self.subTest(old=old):
                path.write_text(original)
                self.change(ed.METRICS, old, new)
                with self.assertRaises(ValueError):
                    ed.by_metric_baseline(self.root)

    def test_registry_structure_count_and_duplicate_keys_rejected(self):
        path = self.root / ed.REGISTRY
        original = path.read_text()
        for old, new in [('tables:', 'other:'), ('role: DIMENSION_VERSIONED', 'role: SNAPSHOT'),
                         ('mutability: APPEND_ONLY\n    retention: {policy: PERMANENT}\n    lineage: {source: ORG_SOURCE}',
                          'mutability: APPEND_ONLY\n    retention: []\n    lineage: {source: ORG_SOURCE}'),
                         ('writers: []', 'writers: null')]:
            with self.subTest(old=old):
                path.write_text(original)
                if old == 'writers: []':
                    # first empty writers list of a versioned master table: an earlier table may legitimately have none
                    start = original.index('role: DIMENSION_VERSIONED')
                    at = original.index(old, start)
                    path.write_text(original[:at] + new + original[at + len(old):])
                else:
                    self.change(ed.REGISTRY, old, new)
                with self.assertRaises(ValueError):
                    ed.by_versioned_master_tables(self.root)
        path.write_text(original + '\ntables: {}\n')
        with self.assertRaises(ValueError):
            ed.by_versioned_master_tables(self.root)

    def test_ddl_missing_ambiguous_or_wrong_interval_rejected(self):
        table = 'platform_identity.role_versions'
        path = next(path for path in (self.root / ed.MIGRATIONS).rglob('*.sql')
                    if f'CREATE TABLE {table} (' in path.read_text())
        original = path.read_text()
        for text in [original.replace(f'CREATE TABLE {table} (', f'CREATE TABLE {table}_missing ('),
                     original.replace('effective_to_at             timestamptz,', 'wrong_to_at                 timestamptz,'),
                     original + original, original + f'\nALTER TABLE {table} DROP COLUMN effective_to_at;\n']:
            with self.subTest(text_length=len(text)):
                path.write_text(text)
                with self.assertRaises(ValueError):
                    ed.effective_columns(self.root, table)

    def test_rerun_is_byte_identical_and_check_never_repairs(self):
        with contextlib.redirect_stdout(io.StringIO()):
            ed.generate(self.root)
            first = {name: (self.root / ed.OUTPUT / name).read_bytes() for name in ed.documents(self.root)}
            ed.generate(self.root)
            ed.generate(self.root, check=True)
            ed.generate(self.root, check=True)
            self.assertEqual(first, {name: (self.root / ed.OUTPUT / name).read_bytes() for name in first})
            target = self.root / ed.OUTPUT / 'GAPS.md'
            target.write_bytes(b'broken')
            with self.assertRaisesRegex(ValueError, 'GAPS.md'):
                ed.generate(self.root, check=True)
            self.assertEqual(b'broken', target.read_bytes())
            target.unlink()
            with self.assertRaises(ValueError):
                ed.generate(self.root, check=True)
            self.assertFalse(target.exists())

    def test_missing_inputs_and_cli_error_exit(self):
        for relative in (ed.facts.BI, ed.METRICS, ed.REGISTRY):
            path = self.root / relative
            original = path.read_bytes()
            path.unlink()
            with self.subTest(path=relative), self.assertRaises(OSError):
                ed.documents(self.root)
            path.write_bytes(original)
        with patch.object(ed, 'generate', side_effect=ValueError('结构不符')):
            with contextlib.redirect_stderr(io.StringIO()) as output:
                self.assertEqual(1, ed.main(['--check']))
            self.assertIn('结构不符', output.getvalue())


if __name__ == '__main__':
    unittest.main()
