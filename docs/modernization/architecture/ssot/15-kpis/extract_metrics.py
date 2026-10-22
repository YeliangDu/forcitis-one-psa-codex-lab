#!/usr/bin/env python3
"""三处指标候选原样并列；--check 只比对生成物。"""
import argparse
import itertools
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[5]
OUTPUT = Path('docs/modernization/architecture/ssot/15-kpis')
BASELINE = Path('docs/modernization/r0/METRIC_BASELINE_DRAFT.md')
GLOSSARY = Path('docs/modernization/glossary/terms.yaml')
VISION = Path('docs/modernization/architecture/togaf/vision.yaml')
HEADER = ['编号', '建议名称', '替代的叫法', 'OAT', '期间', '加总', '事实 BC',
          '基础', '含税', '层', '处置', '待决']
EXPECTED = {'by_metric_baseline': 48, 'by_glossary_metric': 79, 'by_vision_kpi': 16}


class UniqueLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        keys = [self.construct_object(key, deep=True) for key, _ in node.value]
        if any(not isinstance(key, str) for key in keys) or len(set(keys)) != len(keys):
            raise ValueError(f'YAML 映射键须为不重复的字符串：行 {node.start_mark.line + 1}')
        return super().construct_mapping(node, deep=deep)


def read_yaml(root, path):
    loader = UniqueLoader((root / path).read_text(encoding='utf-8'))
    try:
        node = loader.get_single_node()
        data = loader.construct_document(node) if node is not None else None
    finally:
        loader.dispose()
    if not isinstance(data, dict):
        raise ValueError(f'{path}: 应为映射')
    return data, node


def child(node, key):
    return next(value for name, value in node.value if name.value == key)


def rows(data, node, key):
    values = data.get(key)
    if not isinstance(values, list) or not values:
        raise ValueError(f'{key}: 应为非空列表')
    for value, origin in zip(values, child(node, key).value):
        if not isinstance(value, dict):
            raise ValueError(f'{key}: 条目应为映射')
        yield value, origin


def string(value, field):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f'{field}: 应为非空字符串')
    return value


def count(key, values):
    if len(values) != EXPECTED[key]:
        raise ValueError(f'{key}: 条数应为 {EXPECTED[key]}，实际 {len(values)}；与预期不符，未更新生成物')


def parse_table(table, section):
    if (len(table) < 3 or table[0][1] != HEADER
            or any(len(cells) != len(HEADER) for _, cells in table)
            or not all(re.fullmatch(r':?-{3,}:?', cell) for cell in table[1][1])):
        raise ValueError(f'指标基线表头、列数或分隔行不符：{section}')
    result = []
    for line, cells in table[2:]:
        if any(not cell for cell in cells) or not re.fullmatch(r'M-\d{2}', cells[0]):
            raise ValueError(f'{BASELINE}:{line}: 空单元格或编号不符')
        result.append({'cells': dict(zip(HEADER, cells)), 'section': section,
                       'source': f'{BASELINE}:{line}'})
    return result


def by_metric_baseline(root):
    lines = (root / BASELINE).read_text(encoding='utf-8').splitlines()
    starts = [i for i, line in enumerate(lines) if line == '## 3. 指标基线（48 个，草稿）']
    if len(starts) != 1:
        raise ValueError('指标基线缺唯一 §3 标题')
    result, sections, section, table, closed = [], [], None, [], False
    for index in range(starts[0] + 1, len(lines) + 1):
        line = lines[index] if index < len(lines) else '## 结束'
        if line.startswith('#'):
            if table:
                result.extend(parse_table(table, section))
                table, closed = [], True
            if section is not None and not closed:
                raise ValueError(f'指标基线小节缺表：{section}')
            if line.startswith('## '):
                break
            match = re.fullmatch(r'### 3\.([1-6]) .+', line)
            if not match:
                raise ValueError(f'指标基线小节结构不符：{line}')
            if match[1] == '1' and section is None:
                continue
            section, closed = line, False
            sections.append(int(match[1]))
        elif line.startswith('|') and section is not None:
            if closed or not line.endswith('|'):
                raise ValueError('指标基线表格结构不符')
            table.append((index + 1, [cell.strip() for cell in line[1:-1].split('|')]))
        elif table:
            result.extend(parse_table(table, section))
            table, closed = [], True
    if sections != [2, 3, 4, 5, 6]:
        raise ValueError('指标基线须有按序唯一的 §3.2～§3.6')
    count('by_metric_baseline', result)
    if [row['cells']['编号'] for row in result] != [f'M-{i:02}' for i in range(1, 49)]:
        raise ValueError('指标基线须有按序唯一的 M-01 至 M-48')
    return result


def by_glossary_metric(root):
    data, node = read_yaml(root, GLOSSARY)
    result, seen = [], set()
    for row, origin in rows(data, node, 'terms'):
        string(row.get('kind'), 'terms.kind')
        if row['kind'] != 'metric':
            continue
        for field in ('id', 'zh', 'en', 'definition'):
            string(row.get(field), f'terms.{field}')
        if row['id'] in seen or 'source' in row:
            raise ValueError(f'词条重复或 source 与定位字段冲突：{row["id"]}')
        seen.add(row['id'])
        result.append({**row, 'source': f'{GLOSSARY}:{origin.start_mark.line + 1}'})
    count('by_glossary_metric', result)
    return result


def by_vision_kpi(root):
    data, node = read_yaml(root, VISION)
    result, goal_ids, kpi_ids = [], set(), set()
    for goal, goal_node in rows(data, node, 'goals'):
        for field in ('id', 'name'):
            string(goal.get(field), f'goals.{field}')
        if goal['id'] in goal_ids:
            raise ValueError(f'目标重复：{goal["id"]}')
        goal_ids.add(goal['id'])
        for row, _ in rows(goal, goal_node, 'kpis'):
            for field in ('id', 'name', 'definition', 'source'):
                string(row.get(field), f'kpis.{field}')
            if row['id'] in kpi_ids or {'goal_id', 'goal_name'} & row.keys():
                raise ValueError(f'KPI 重复或所属目标字段冲突：{row["id"]}')
            kpi_ids.add(row['id'])
            result.append({**row, 'goal_id': goal['id'], 'goal_name': goal['name']})
    count('by_vision_kpi', result)
    return result


def literal_comparison(candidates):
    names, groups = {}, {}
    for key, entries in candidates.items():
        names[key] = set()
        for row in entries:
            baseline = key == 'by_metric_baseline'
            name = row['cells']['建议名称'] if baseline else row['zh' if key == 'by_glossary_metric' else 'name']
            definition = row['cells'] if baseline else row['definition']
            names[key].add(name)
            groups.setdefault(name, []).append({'origin': key,
                'id': row['cells']['编号'] if baseline else row['id'],
                'definition': definition, 'source': row['source']})
    pairs = {f'{a}__{b}': len(names[a] & names[b]) for a, b in itertools.combinations(names, 2)}
    unique = {key: len(values - set().union(*(v for k, v in names.items() if k != key)))
              for key, values in names.items()}
    gaps = {name: entries for name, entries in sorted(groups.items())
            if len({entry['origin'] for entry in entries}) > 1
            and any(entry['definition'] != entries[0]['definition'] for entry in entries[1:])}
    return {'pairwise_name_intersections': pairs,
            'all_three_name_intersection': len(set.intersection(*names.values())),
            'names_only_in_one_source': unique}, gaps


def gap_document(candidates, gaps):
    lines = ['# 指标候选字面差异', '', '由 extract_metrics.py 生成，勿手改。', '',
             '仅按完整名称字面相同比对，不拆分组合名称，不判同义。交 one-architecture 判定。', '',
             '词表复制全部原始键；愿景 KPI 的 source 保留原有依据文字，不覆盖为文件定位。', '',
             '基线没有独立 definition 列，以下以完整表行 cells 展示口径原文；与 definition 文本结构不同也列为候选，不能据此认定语义冲突。', '',
             '## 条数与预期', '']
    for key, entries in candidates.items():
        lines.append(f'- {key}：实际 {len(entries)}，预期 {EXPECTED[key]}，一致。')
    lines.extend(['', '输入缺失、结构或条数不符时脚本报错退出，不覆盖生成物；错误包含实际条数与预期。', '',
                  f'## 同名而定义原文不同的候选（{len(gaps)} 组）', ''])
    for name, entries in gaps.items():
        lines.extend([f'### {name}', ''])
        for entry in entries:
            lines.extend([f'- {entry["origin"]} / {entry["id"]}；出处：{entry["source"]}',
                          '', '```yaml', yaml.safe_dump({'definition': entry['definition']},
                          allow_unicode=True, sort_keys=False, width=200).rstrip(), '```', ''])
    return '\n'.join(lines)


def documents(root):
    candidates = {'by_metric_baseline': by_metric_baseline(root),
                  'by_glossary_metric': by_glossary_metric(root),
                  'by_vision_kpi': by_vision_kpi(root)}
    comparison, gaps = literal_comparison(candidates)
    counts = {key: len(entries) for key, entries in candidates.items()}
    raw = {'meta': {'sources': list(map(str, (BASELINE, GLOSSARY, VISION))),
                    'counts': counts, 'expected_counts': EXPECTED,
                    'literal_comparison': '基线建议名称、词表 zh、愿景 name 的完整字符串；仅去 Markdown 单元格外围空白，不拆分、不判同义。',
                    **comparison, 'different_definition_groups': len(gaps)}, **candidates}
    return {'metrics.raw.yaml': '# 由 extract_metrics.py 生成，勿手改\n' + yaml.safe_dump(
        raw, allow_unicode=True, sort_keys=False, width=200),
        'GAPS.md': gap_document(candidates, gaps)}


def generate(root=ROOT, check=False):
    outputs = documents(root)
    different = [name for name, content in outputs.items() if not (root / OUTPUT / name).is_file()
                 or (root / OUTPUT / name).read_bytes() != content.encode('utf-8')]
    if check and different:
        raise ValueError('指标候选生成物不一致：' + '、'.join(different))
    if not check:
        (root / OUTPUT).mkdir(parents=True, exist_ok=True)
        for name in different:
            (root / OUTPUT / name).write_bytes(outputs[name].encode('utf-8'))
    print(yaml.safe_load(outputs['metrics.raw.yaml'])['meta'])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    try:
        generate(check=args.check)
        return 0
    except (OSError, UnicodeError, ValueError, yaml.YAMLError) as error:
        print(f'指标候选抽取失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
