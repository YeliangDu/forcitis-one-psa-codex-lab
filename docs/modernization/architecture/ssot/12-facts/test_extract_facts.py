#!/usr/bin/env python3
"""ARC-1021-ARC-12：原文、结构拒绝与生成一致性自测。"""
import contextlib
import io
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

import extract_facts as ef


class ExtractFactsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for path in (ef.OBJECTS, *ef.EVENTS, ef.BI):
            (self.root / path).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ef.ROOT / path, self.root / path)

    def rewrite_yaml(self, path, data):
        (self.root / path).write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False), encoding='utf-8')

    def test_three_classes_and_verbatim_note(self):
        for raw, expected in [('已经发生 → 金额', ('declared', None)), ('—', ('none', None)),
                              ('— （括注（内层）；保留原文）', ('none_with_note', '（括注（内层）；保留原文）')),
                              ('—(note)', ('none_with_note', '(note)'))]:
            with self.subTest(raw=raw):
                self.assertEqual(expected, ef.classify(raw))
        for raw in ('', None, [], '— 未带括注', '— '):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                ef.classify(raw)

    def test_every_object_field_and_fact_line_matches_source(self):
        original = yaml.safe_load((self.root / ef.OBJECTS).read_text())['objects']
        rows = ef.by_object(self.root)
        self.assertEqual(126, len(rows))
        self.assertEqual({'declared': 81, 'none': 25, 'none_with_note': 20},
                         dict(ef.Counter(row['classification'] for row in rows)))
        self.assertEqual(22, sum(not row['in_system'] for row in rows))
        lines = (self.root / ef.OBJECTS).read_text().splitlines()
        for source, row in zip(original, rows):
            self.assertEqual([source[key] for key in ('id', 'name', 'level', 'facts')],
                             [row[key] for key in ('object', 'object_name', 'level', 'facts')])
            self.assertEqual('已非对象' not in source['definition'], row['in_system'])
            self.assertTrue(lines[int(row['source'].rsplit(':', 1)[1]) - 1].startswith('  facts:'))

    def test_all_events_preserve_extension_and_missing_description(self):
        rows = ef.by_event(self.root)
        expected = []
        for path in ef.EVENTS:
            messages = yaml.safe_load((self.root / path).read_text())['components']['messages']
            expected.extend(messages.values())
        self.assertEqual(67, len(rows))
        self.assertEqual(42, sum('description' in row['missing_fields'] for row in rows))
        for original, row in zip(expected, rows):
            self.assertEqual(original['title'], row['title'])
            self.assertEqual(original.get('description'), row['description'])
            self.assertEqual(original['x-event'], row['x-event'])
            self.assertEqual(original['x-event']['eventType'], row['event_type'])
            self.assertEqual(original['x-event']['publisher'], row['publisher'])
            self.assertEqual('description' not in original, 'description' in row['missing_fields'])
            path, line = row['source'].rsplit(':', 1)
            self.assertEqual('name: ' + original['name'], (self.root / path).read_text().splitlines()[int(line) - 1].strip())

    def test_new_fact_extensions_are_copied_without_filtering(self):
        path = ef.EVENTS[0]
        data = yaml.safe_load((self.root / path).read_text())
        extension = next(iter(data['components']['messages'].values()))['x-event']
        extension.update(posting={'when': ['原值', False]}, fact={'kind': '待定'}, audit=None)
        self.rewrite_yaml(path, data)
        self.assertEqual(extension, ef.by_event(self.root)[0]['x-event'])

    def test_bus_matrix_all_cells_and_source_lines_are_verbatim(self):
        text = (self.root / ef.BI).read_text()
        matrix = ef.parse_bus_matrix(text)
        self.assertEqual(ef.HEADER, matrix['header'])
        self.assertEqual(16, len(matrix['rows']))
        for row in matrix['rows']:
            line = int(row['source'].rsplit(':', 1)[1])
            cells = [cell.strip() for cell in text.splitlines()[line - 1][1:-1].split('|')]
            self.assertEqual(cells[0], row['fact'])
            self.assertEqual(cells, list(row['cells'].values()))
        self.assertTrue(matrix['rows'][-1]['fact'].startswith('**期间快照**'))

    def test_bus_matrix_rejects_changed_header_width_count_and_separator(self):
        text = (self.root / ef.BI).read_text()
        prefix, section = text.split('### 3.2 总线矩阵（草案）', 1)
        mutations = [text.replace('业务过程事实 | 粒度', '业务过程事实 | 改名', 1),
                     text.replace('业务过程事实 | 粒度', '业务过程事实 | 多列 | 粒度', 1),
                     prefix + '### 3.2 总线矩阵（草案）' + section.replace('|---|---|', '|bad|---|', 1),
                     text.replace('### 3.2 总线矩阵（草案）', '### 3.2 未知'),
                     '\n'.join(line for line in text.splitlines() if not line.startswith('| 工时条目 |')),
                     text.replace('| 工时条目 |', '|  |', 1)]
        for index, mutated in enumerate(mutations):
            with self.subTest(mutation=index), self.assertRaises(ValueError):
                ef.parse_bus_matrix(mutated)

    def test_inputs_missing_malformed_or_empty_fail_without_output(self):
        path = self.root / ef.EVENTS[-1]
        original = path.read_bytes()
        for content in (None, b'', b'components: []\n', b'components: [\n'):
            if content is None:
                path.unlink()
            else:
                path.write_bytes(content)
            with self.subTest(content=content), self.assertRaises((OSError, ValueError, yaml.YAMLError)):
                ef.generate(self.root)
            self.assertFalse((self.root / ef.OUTPUT).exists())
            path.write_bytes(original)

    def test_duplicates_invalid_objects_and_event_count_fail(self):
        data = yaml.safe_load((self.root / ef.OBJECTS).read_text())
        data['objects'].append(data['objects'][0])
        self.rewrite_yaml(ef.OBJECTS, data)
        with self.assertRaisesRegex(ValueError, '对象重复'):
            ef.by_object(self.root)
        data['objects'] = [{'id': 'OBJ-01'}]
        self.rewrite_yaml(ef.OBJECTS, data)
        with self.assertRaises(ValueError):
            ef.by_object(self.root)
        (self.root / ef.OBJECTS).write_text('objects: []\nobjects: []\n')
        with self.assertRaisesRegex(ValueError, '不重复'):
            ef.by_object(self.root)
        path = ef.EVENTS[0]
        data = yaml.safe_load((self.root / path).read_text())
        data['components']['messages'].pop(next(iter(data['components']['messages'])))
        self.rewrite_yaml(path, data)
        with self.assertRaisesRegex(ValueError, '条数应为 67'):
            ef.by_event(self.root)

    def test_repeat_bytes_check_is_read_only_and_detects_tampering(self):
        with contextlib.redirect_stdout(io.StringIO()):
            ef.generate(self.root)
            output = self.root / ef.OUTPUT
            first = {path.name: path.read_bytes() for path in output.iterdir()}
            ef.generate(self.root)
            self.assertEqual(first, {path.name: path.read_bytes() for path in output.iterdir()})
            for _ in range(2):
                ef.generate(self.root, check=True)
            self.assertEqual(first, {path.name: path.read_bytes() for path in output.iterdir()})
            for name, original in first.items():
                path = output / name
                path.write_bytes(b'changed\n')
                with self.assertRaisesRegex(ValueError, name):
                    ef.generate(self.root, check=True)
                self.assertEqual(b'changed\n', path.read_bytes())
                path.unlink()
                with self.assertRaisesRegex(ValueError, name):
                    ef.generate(self.root, check=True)
                self.assertFalse(path.exists())
                path.write_bytes(original)
            (self.root / ef.BI).write_text((self.root / ef.BI).read_text().replace('一版报价', '两版报价', 1))
            with self.assertRaisesRegex(ValueError, 'facts.raw.yaml'):
                ef.generate(self.root, check=True)

    def test_cli_failure_is_nonzero_and_reports_error(self):
        with patch.object(ef, 'generate', side_effect=ValueError('输入结构不符')):
            error = io.StringIO()
            with contextlib.redirect_stderr(error):
                self.assertEqual(1, ef.main(['--check']))
            self.assertIn('事实候选抽取失败：输入结构不符', error.getvalue())


if __name__ == '__main__':
    unittest.main()
