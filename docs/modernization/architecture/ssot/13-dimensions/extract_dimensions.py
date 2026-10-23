#!/usr/bin/env python3
"""三处分析维度候选原样并列；--check 只比对生成物。"""
import argparse
import importlib.util
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[5]
OUTPUT = Path('docs/modernization/architecture/ssot/13-dimensions')
METRICS = Path('docs/modernization/r0/METRIC_BASELINE_DRAFT.md')
REGISTRY = Path('backend/architecture/data-access-registry.yaml')
MIGRATIONS = Path('backend/src/main/resources/db/migration')
METRIC_HEADER = ['编号', '建议名称', '替代的叫法', 'OAT', '期间', '加总', '事实 BC',
                 '基础', '含税', '层', '处置', '待决']

# 12-facts 由另一工作维护，只导入解析函数，不在该目录写入缓存。
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('dimension_fact_parser', ROOT / OUTPUT.parent / '12-facts/extract_facts.py')
facts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(facts)


def by_bi_bus_matrix(root):
    matrix = facts.parse_bus_matrix((root / facts.BI).read_text(encoding='utf-8'))
    return [{'name': name, 'source': matrix['source'], 'rows': [
        {'fact': row['fact'], 'cell': row['cells'][name], 'source': row['source']}
        for row in matrix['rows']]} for name in matrix['header']]


def by_metric_baseline(root):
    lines = (root / METRICS).read_text(encoding='utf-8').splitlines()
    starts = [i for i, line in enumerate(lines) if line == '## 3. 指标基线（48 个，草稿）']
    if len(starts) != 1:
        raise ValueError('指标基线缺唯一 §3 标题')
    result, section, table, sections, closed = [], None, [], set(), False
    for index in range(starts[0] + 1, len(lines) + 1):
        line = lines[index] if index < len(lines) else '## 结束'
        if line.startswith('#'):
            if table:
                result.extend(parse_metric_table(table, section))
                table = []
                closed = True
            if section is not None and not closed:
                raise ValueError(f'指标基线小节缺表：{section}')
            if line.startswith('## '):
                break
            if not re.fullmatch(r'### 3\.[1-6] .+', line) or line in sections:
                raise ValueError(f'指标基线小节结构不符：{line}')
            sections.add(line)
            section = line
            closed = False
        elif line.startswith('|'):
            if section is None or closed or not line.endswith('|'):
                raise ValueError('指标基线表格结构不符')
            table.append((index + 1, [cell.strip() for cell in line[1:-1].split('|')]))
        elif table:
            result.extend(parse_metric_table(table, section))
            table = []
            closed = True
    if len(sections) != 6 or [row['metric'] for row in result] != [f'M-{i:02}' for i in range(1, 49)]:
        raise ValueError('指标基线须有 6 个小节及按序唯一的 M-01 至 M-48')
    return result


def parse_metric_table(table, section):
    if section.startswith('### 3.1 '):
        if (len(table) != 7 or table[0][1] != ['列', '含义']
                or any(len(cells) != 2 or any(not cell for cell in cells) for _, cells in table)
                or not all(re.fullmatch(r':?-{3,}:?', cell) for cell in table[1][1])):
            raise ValueError('指标基线列说明表结构不符')
        return []
    if (len(table) < 3 or table[0][1] != METRIC_HEADER
            or any(len(cells) != len(METRIC_HEADER) for _, cells in table)
            or not all(re.fullmatch(r':?-{3,}:?', cell) for cell in table[1][1])):
        raise ValueError(f'指标基线表头、列数或分隔行不符：{section}')
    rows = []
    for line, cells in table[2:]:
        if any(not cell for cell in cells) or not re.fullmatch(r'M-\d{2}', cells[0]):
            raise ValueError(f'指标基线:{line}: 空单元格或编号不符')
        row = dict(zip(METRIC_HEADER, cells))
        rows.append({'metric': cells[0], 'section': section,
                     **{key: row[key] for key in ('OAT', '期间', '加总', '事实 BC')},
                     'source': f'{METRICS.as_posix()}:{line}'})
    return rows


def effective_columns(root, table):
    files = sorted((root / MIGRATIONS).rglob('*.sql'))
    if not files:
        raise ValueError(f'{MIGRATIONS}: 缺迁移 DDL')
    matches = []
    for path in files:
        text = path.read_text(encoding='utf-8')
        # 只接受当前基线的显式 CREATE TABLE；区间列的后续修改须另行审查。
        if re.search(r'\bALTER\s+TABLE\s+' + re.escape(table) + r'\b[^;]*\beffective_(?:from|to)_', text, re.I):
            raise ValueError(f'{table}: 生效区间存在 ALTER TABLE，须审查 DDL 解析范围')
        pattern = r'^CREATE TABLE ' + re.escape(table) + r' \(\n([\s\S]*?)^\);'
        for match in re.finditer(pattern, text, re.M):
            columns = []
            for column in re.finditer(r'^[ \t]+(effective_(?:from|to)_(?:date|at))\s+(date|timestamptz)\b[^\n]*', match[1], re.M):
                offset = match.start(1) + column.start()
                columns.append({'name': column[1], 'type': column[2],
                                'source': f'{path.relative_to(root).as_posix()}:{text[:offset].count(chr(10)) + 1}'})
            matches.append(columns)
    if len(matches) != 1:
        raise ValueError(f'{table}: 须有唯一可解析的 CREATE TABLE，实际 {len(matches)}')
    columns = matches[0]
    names = [column['name'] for column in columns]
    if names not in (['effective_from_date', 'effective_to_date'], ['effective_from_at', 'effective_to_at']):
        raise ValueError(f'{table}: 生效区间列结构不符：{names}')
    expected_type = 'date' if names[0].endswith('_date') else 'timestamptz'
    if any(column['type'] != expected_type for column in columns):
        raise ValueError(f'{table}: 生效区间类型不符')
    return columns


def by_versioned_master_tables(root):
    data, node = facts.read_yaml(root, REGISTRY)
    tables = data.get('tables')
    if not isinstance(tables, dict) or not tables:
        raise ValueError('数据访问注册表 tables 须为非空映射')
    origins = dict((key.value, value) for key, value in facts.child(node, 'tables').value)
    result = []
    for name, row in tables.items():
        if not isinstance(row, dict):
            raise ValueError(f'{name}: 表登记须为映射')
        facts.string(row.get('role'), f'{name}.role')
        if row['role'] != 'DIMENSION_VERSIONED':
            continue
        if not re.fullmatch(r'[a-z_]+\.[a-z_]+', name):
            raise ValueError(f'表键不符：{name}')
        facts.string(row.get('mutability'), f'{name}.mutability')
        for field in ('retention', 'lineage'):
            if not isinstance(row.get(field), dict) or not row[field]:
                raise ValueError(f'{name}.{field}: 须为非空映射')
        facts.string(row['retention'].get('policy'), f'{name}.retention.policy')
        facts.string(row['lineage'].get('source'), f'{name}.lineage.source')
        for field in ('writers', 'readers'):
            if field in row:
                if not isinstance(row[field], list):
                    raise ValueError(f'{name}.{field}: 须为列表')
                for value in row[field]:
                    facts.string(value, f'{name}.{field}')
        result.append({'table': name, 'role': row['role'], 'mutability': row['mutability'],
                       'retention': row['retention'], 'lineage': row['lineage'],
                       'writers': row.get('writers'), 'readers': row.get('readers'),
                       'missing_fields': [key for key in ('writers', 'readers') if key not in row],
                       'effective_columns': effective_columns(root, name),
                       'source': facts.location(REGISTRY, origins[name])})
    if len(result) != 8:
        raise ValueError(f'版本化主数据表应有 8 张，实际 {len(result)}')
    return result


def documents(root):
    matrix, metrics, tables = by_bi_bus_matrix(root), by_metric_baseline(root), by_versioned_master_tables(root)
    bus_names = {column['name'] for column in matrix}
    oat_names = {row['OAT'] for row in metrics}
    differences = {'only_bi_bus_matrix': sorted(bus_names - oat_names),
                   'only_metric_baseline_oat': sorted(oat_names - bus_names)}
    counts = {'by_bi_bus_matrix': len(matrix), 'by_metric_baseline': len(metrics),
              'by_versioned_master_tables': len(tables),
              'literal_differences': {key: len(values) for key, values in differences.items()}}
    raw = {'meta': {'sources': [str(facts.BI), str(METRICS), str(REGISTRY), str(MIGRATIONS)],
                    'counts': counts, 'versioned_master_tables': '版本化主数据表，非分析维度',
                    'bus_matrix_cells': '14 列均保留 16 行；单元格标记符号不解释。',
                    'literal_comparison': '按去除 Markdown 单元格外围空白后的完整字符串去重比对；保留括注、组合表达式与占位符，不拆分、不判同义。',
                    'missing_fields': 'writers/readers 缺字段时输出 null 并登记 missing_fields；原有空列表保留 []。'},
           'by_bi_bus_matrix': matrix, 'by_metric_baseline': metrics,
           'by_versioned_master_tables': tables}
    gaps = ['# 分析维度候选字面差异', '', '由 extract_dimensions.py 生成，勿手改。', '',
            '仅并列来源，不合并、不判同义。版本化主数据表，非分析维度。', '',
            raw['meta']['literal_comparison'], '',
            '总线矩阵包含 14 个原始表头（含业务过程事实、粒度、事实 BC（来源）、阶段）；每列保留全部 16 行，标记不解释。', '']
    for key, title in [('only_bi_bus_matrix', '仅总线矩阵列名出现'),
                       ('only_metric_baseline_oat', '仅指标基线 OAT 完整单元格出现')]:
        gaps.extend([f'## {title}（{len(differences[key])}）', ''])
        for value in differences[key]:
            sources = [column['source'] for column in matrix if column['name'] == value] if key == 'only_bi_bus_matrix' else [
                f'{row["metric"]} {row["source"]}' for row in metrics if row['OAT'] == value]
            gaps.append(f'- `{value}`：' + '；'.join(f'`{source}`' for source in sources))
        gaps.append('')
    return {'dimensions.raw.yaml': '# 由 extract_dimensions.py 生成，勿手改\n' + yaml.safe_dump(
        raw, allow_unicode=True, sort_keys=False, width=200), 'GAPS.md': '\n'.join(gaps)}


def generate(root=ROOT, check=False):
    outputs = documents(root)
    different = [name for name, content in outputs.items() if not (root / OUTPUT / name).is_file()
                 or (root / OUTPUT / name).read_bytes() != content.encode('utf-8')]
    if check and different:
        raise ValueError('分析维度候选生成物不一致：' + '、'.join(different))
    if not check:
        (root / OUTPUT).mkdir(parents=True, exist_ok=True)
        for name in different:
            (root / OUTPUT / name).write_bytes(outputs[name].encode('utf-8'))
    print(yaml.safe_load(outputs['dimensions.raw.yaml'])['meta']['counts'])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    try:
        generate(check=args.check)
        return 0
    except (OSError, UnicodeError, ValueError, yaml.YAMLError) as error:
        print(f'分析维度候选抽取失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
