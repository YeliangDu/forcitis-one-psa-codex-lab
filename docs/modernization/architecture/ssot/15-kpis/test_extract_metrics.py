"""ARC-1022-ARC-01：原文保留、拒绝无效输入与可复现性自测。"""
import contextlib
import importlib.util
import io
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location('extract_metrics', Path(__file__).with_name('extract_metrics.py'))
metrics = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(metrics)


class ExtractMetricsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for path in (metrics.BASELINE, metrics.GLOSSARY, metrics.VISION):
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(metrics.ROOT / path, target)

    def replace(self, path, before, after):
        target = self.root / path
        text = target.read_text(encoding='utf-8')
        self.assertIn(before, text)
        target.write_text(text.replace(before, after, 1), encoding='utf-8')

    def edit_yaml(self, path, change):
        target = self.root / path
        data = yaml.safe_load(target.read_text(encoding='utf-8'))
        change(data)
        target.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False), encoding='utf-8')

    def test_baseline_retains_every_cell_and_exact_line(self):
        lines = (self.root / metrics.BASELINE).read_text(encoding='utf-8').splitlines()
        extracted = metrics.by_metric_baseline(self.root)
        originals = [(i, line) for i, line in enumerate(lines, 1) if line.startswith('| M-')]
        self.assertEqual(48, len(extracted))
        self.assertEqual(len(originals), len(extracted))
        for row, (number, line) in zip(extracted, originals):
            self.assertEqual(line, '| ' + ' | '.join(row['cells'].values()) + ' |')
            self.assertEqual(f'{metrics.BASELINE}:{number}', row['source'])
            self.assertTrue(row['section'].startswith('### 3.'))
        self.assertIn('赢单金额、赢单率', [r['cells']['建议名称'] for r in extracted])

    def test_glossary_retains_all_keys_and_source_location(self):
        original = yaml.safe_load((self.root / metrics.GLOSSARY).read_text(encoding='utf-8'))
        expected = [row for row in original['terms'] if row['kind'] == 'metric']
        actual = metrics.by_glossary_metric(self.root)
        self.assertEqual(79, len(actual))
        self.assertEqual(expected, [{k: v for k, v in row.items() if k != 'source'} for row in actual])
        lines = (self.root / metrics.GLOSSARY).read_text(encoding='utf-8').splitlines()
        for row in actual:
            self.assertEqual(f'- id: {row["id"]}', lines[int(row['source'].rsplit(':', 1)[1]) - 1])
        self.assertEqual(4, sum('formula' in row for row in actual))

    def test_vision_retains_all_keys_and_goal_context(self):
        original = yaml.safe_load((self.root / metrics.VISION).read_text(encoding='utf-8'))
        expected = [{**kpi, 'goal_id': goal['id'], 'goal_name': goal['name']}
                    for goal in original['goals'] for kpi in goal['kpis']]
        self.assertEqual(16, len(expected))
        self.assertEqual(expected, metrics.by_vision_kpi(self.root))

    def test_baseline_invalid_row_rejected(self):
        self.replace(metrics.BASELINE, '| M-01 |', '| INVALID |')
        with self.assertRaisesRegex(ValueError, '编号不符'):
            metrics.by_metric_baseline(self.root)

    def test_each_baseline_table_header_rejected(self):
        for section in range(2, 7):
            with self.subTest(section=section):
                target = self.root / metrics.BASELINE
                original = (metrics.ROOT / metrics.BASELINE).read_text(encoding='utf-8')
                start = original.index(f'### 3.{section} ')
                target.write_text(original[:start] + original[start:].replace('| OAT |', '| changed |', 1), encoding='utf-8')
                with self.assertRaisesRegex(ValueError, '表头'):
                    metrics.by_metric_baseline(self.root)

    def test_baseline_count_mismatch_rejected(self):
        target = self.root / metrics.BASELINE
        lines = target.read_text(encoding='utf-8').splitlines(keepends=True)
        target.write_text(''.join(line for line in lines if not line.startswith('| M-01 |')), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '条数应为 48，实际 47'):
            metrics.by_metric_baseline(self.root)

    def test_baseline_column_count_and_separator_rejected(self):
        for before, after in [('| PIPE | I |', '| PIPE | extra | I |'), ('|---|---|---|', '|---|bad|---|')]:
            with self.subTest(after=after):
                shutil.copyfile(metrics.ROOT / metrics.BASELINE, self.root / metrics.BASELINE)
                if 'bad' in after:
                    text = (self.root / metrics.BASELINE).read_text(encoding='utf-8')
                    start = text.index('### 3.2 ')
                    (self.root / metrics.BASELINE).write_text(text[:start] + text[start:].replace(before, after, 1), encoding='utf-8')
                else:
                    self.replace(metrics.BASELINE, before, after)
                with self.assertRaisesRegex(ValueError, '表头、列数或分隔行'):
                    metrics.by_metric_baseline(self.root)

    def test_glossary_missing_definition_rejected(self):
        def change(data):
            next(row for row in data['terms'] if row['kind'] == 'metric').pop('definition')
        self.edit_yaml(metrics.GLOSSARY, change)
        with self.assertRaisesRegex(ValueError, 'terms.definition'):
            metrics.by_glossary_metric(self.root)

    def test_glossary_count_mismatch_rejected(self):
        def change(data):
            data['terms'].remove(next(row for row in data['terms'] if row['kind'] == 'metric'))
        self.edit_yaml(metrics.GLOSSARY, change)
        with self.assertRaisesRegex(ValueError, '条数应为 79，实际 78'):
            metrics.by_glossary_metric(self.root)

    def test_vision_missing_definition_rejected(self):
        self.edit_yaml(metrics.VISION, lambda data: data['goals'][0]['kpis'][0].pop('definition'))
        with self.assertRaisesRegex(ValueError, 'kpis.definition'):
            metrics.by_vision_kpi(self.root)

    def test_vision_count_mismatch_rejected(self):
        self.edit_yaml(metrics.VISION, lambda data: data['goals'][0]['kpis'].pop())
        with self.assertRaisesRegex(ValueError, '条数应为 16，实际 15'):
            metrics.by_vision_kpi(self.root)

    def test_missing_inputs_rejected(self):
        for path in (metrics.BASELINE, metrics.GLOSSARY, metrics.VISION):
            with self.subTest(path=path):
                target = self.root / path
                target.unlink()
                with self.assertRaises(OSError):
                    metrics.documents(self.root)
                shutil.copyfile(metrics.ROOT / path, target)

    def test_yaml_duplicate_keys_rejected(self):
        self.replace(metrics.VISION, 'name: OA 迁入模板停用率', 'name: OA 迁入模板停用率\n        name: 重复')
        with self.assertRaisesRegex(ValueError, '不重复的字符串'):
            metrics.by_vision_kpi(self.root)

    def test_yaml_empty_and_invalid_structures_rejected(self):
        for path, contents, extract in [
                (metrics.GLOSSARY, '', metrics.by_glossary_metric),
                (metrics.GLOSSARY, 'terms: []', metrics.by_glossary_metric),
                (metrics.GLOSSARY, 'terms: [null]', metrics.by_glossary_metric),
                (metrics.VISION, 'goals: {}', metrics.by_vision_kpi),
                (metrics.VISION, 'goals: [{id: GOAL-01, name: 目标, kpis: []}]', metrics.by_vision_kpi)]:
            with self.subTest(contents=contents):
                (self.root / path).write_text(contents, encoding='utf-8')
                with self.assertRaises(ValueError):
                    extract(self.root)

    def test_vision_missing_source_rejected(self):
        self.edit_yaml(metrics.VISION, lambda data: data['goals'][0]['kpis'][0].pop('source'))
        with self.assertRaisesRegex(ValueError, 'kpis.source'):
            metrics.by_vision_kpi(self.root)

    def test_duplicate_ids_rejected_without_changing_counts(self):
        def change(data):
            rows = [row for row in data['terms'] if row['kind'] == 'metric']
            rows[1]['id'] = rows[0]['id']
        self.edit_yaml(metrics.GLOSSARY, change)
        with self.assertRaisesRegex(ValueError, '词条重复'):
            metrics.by_glossary_metric(self.root)
        self.edit_yaml(metrics.VISION, lambda data: data['goals'][0]['kpis'][1].update(id='KPI-01'))
        with self.assertRaisesRegex(ValueError, 'KPI 重复'):
            metrics.by_vision_kpi(self.root)

    def test_literal_comparison_no_splitting_or_synonym_inference(self):
        candidates = {
            'by_metric_baseline': [{'cells': {'编号': 'M-01', '建议名称': '甲、乙'}, 'source': 'baseline:1'}],
            'by_glossary_metric': [{'id': 'a', 'zh': '甲', 'definition': '相同', 'source': 'terms:1'},
                                   {'id': 'b', 'zh': '共同', 'definition': '原文', 'source': 'terms:2'}],
            'by_vision_kpi': [{'id': 'KPI-01', 'name': '共同', 'definition': '原文 ', 'source': '依据'}]}
        counts, gaps = metrics.literal_comparison(candidates)
        self.assertEqual([0, 0, 1], list(counts['pairwise_name_intersections'].values()))
        self.assertEqual(0, counts['all_three_name_intersection'])
        self.assertEqual([1, 1, 0], list(counts['names_only_in_one_source'].values()))
        self.assertEqual(['共同'], list(gaps))
        candidates['by_vision_kpi'][0]['definition'] = '原文'
        self.assertEqual({}, metrics.literal_comparison(candidates)[1])

    def test_regeneration_bytes_and_check_without_writes(self):
        with contextlib.redirect_stdout(io.StringIO()):
            metrics.generate(self.root)
            paths = [self.root / metrics.OUTPUT / name for name in ('metrics.raw.yaml', 'GAPS.md')]
            first = [path.read_bytes() for path in paths]
            metrics.generate(self.root)
            self.assertEqual(first, [path.read_bytes() for path in paths])
            with patch.object(Path, 'write_bytes', side_effect=AssertionError('check 不应写入')):
                metrics.generate(self.root, check=True)
                metrics.generate(self.root, check=True)
            paths[0].write_bytes(b'outdated')
            with self.assertRaisesRegex(ValueError, '生成物不一致'):
                metrics.generate(self.root, check=True)
            self.assertEqual(b'outdated', paths[0].read_bytes())

    def test_invalid_input_does_not_overwrite_valid_artifacts(self):
        with contextlib.redirect_stdout(io.StringIO()):
            metrics.generate(self.root)
        paths = [self.root / metrics.OUTPUT / name for name in ('metrics.raw.yaml', 'GAPS.md')]
        original = [path.read_bytes() for path in paths]
        self.edit_yaml(metrics.VISION, lambda data: data['goals'][0]['kpis'].pop())
        with self.assertRaisesRegex(ValueError, '实际 15'):
            metrics.generate(self.root)
        self.assertEqual(original, [path.read_bytes() for path in paths])

    def test_main_reports_errors_and_nonzero_exit(self):
        with patch.object(metrics, 'generate', side_effect=ValueError('条数不符')), contextlib.redirect_stderr(io.StringIO()) as errors:
            self.assertEqual(1, metrics.main(['--check']))
        self.assertIn('指标候选抽取失败：条数不符', errors.getvalue())


if __name__ == '__main__':
    unittest.main()
