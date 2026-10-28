#!/usr/bin/env python3
"""三处事实候选原样并列；--check 只比对生成物。"""
import argparse
import re
import sys
from collections import Counter
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[5]
OUTPUT = Path('docs/modernization/architecture/ssot/12-facts')
OBJECTS = Path('docs/modernization/architecture/ssot/10-objects/objects.yaml')
EVENTS = tuple(Path(f'docs/modernization/events/{name}.yaml') for name in
               ('approval', 'attendance', 'identity', 'integration', 'numbering', 'timesheet', 'workforce'))
BI = Path('docs/modernization/BI_SCOPE.md')
HEADER = ['业务过程事实', '粒度', '事实 BC（来源）', '阶段', '期间', '法人', '组织',
          '项目 / WBS', '合同 / 条款', '客户', '人员', '服务产品', '成本类型', '供应商']


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


def location(path, node):
    return f'{path.as_posix()}:{node.start_mark.line + 1}'


def string(value, field):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f'{field}: 应为非空字符串')
    return value


def classify(value):
    string(value, 'facts')
    if value == '—':
        return 'none', None
    if value.startswith('—'):
        note = value[1:].strip()
        if not re.fullmatch(r'（[\s\S]+）|\([\s\S]+\)', note):
            raise ValueError(f'facts: 破折号后应为括注：{value}')
        return 'none_with_note', note
    return 'declared', None


def by_object(root):
    data, node = read_yaml(root, OBJECTS)
    rows = data.get('objects')
    if not isinstance(rows, list) or not rows:
        raise ValueError(f'{OBJECTS}: objects 应为非空列表')
    result, seen = [], set()
    for row, origin in zip(rows, child(node, 'objects').value):
        if not isinstance(row, dict):
            raise ValueError('objects 条目应为映射')
        for field in ('id', 'name', 'level', 'definition', 'facts'):
            string(row.get(field), f'objects.{field}')
        if row['id'] in seen:
            raise ValueError(f'对象重复：{row["id"]}')
        seen.add(row['id'])
        kind, note = classify(row['facts'])
        item = {'object': row['id'], 'object_name': row['name'], 'level': row['level'],
                'in_system': '已非对象' not in row['definition'], 'facts': row['facts'],
                'classification': kind, 'source': location(OBJECTS, child(origin, 'facts'))}
        if note is not None:
            item['note'] = note
        result.append(item)
    return result


def by_event(root):
    result, seen = [], set()
    for path in EVENTS:
        data, node = read_yaml(root, path)
        components = data.get('components')
        messages = components.get('messages') if isinstance(components, dict) else None
        if not isinstance(messages, dict) or not messages:
            raise ValueError(f'{path}: components.messages 应为非空映射')
        origins = dict((key.value, value) for key, value in child(child(node, 'components'), 'messages').value)
        for name, message in messages.items():
            extension = message.get('x-event') if isinstance(message, dict) else None
            if not isinstance(extension, dict):
                raise ValueError(f'{path}: {name} 缺 x-event 映射')
            event_type = string(extension.get('eventType'), f'{name}.eventType')
            publisher = string(extension.get('publisher'), f'{name}.publisher')
            if event_type in seen:
                raise ValueError(f'事件重复：{event_type}')
            seen.add(event_type)
            string(message.get('title'), f'{name}.title')
            if 'description' in message:
                string(message['description'], f'{name}.description')
            result.append({'event_type': event_type, 'title': message['title'],
                           'description': message.get('description'), 'publisher': publisher,
                           'x-event': extension, 'missing_fields': [key for key in ('description',) if key not in message],
                           'source': location(path, origins[name])})
    if len(result) != 67:
        raise ValueError(f'事件条数应为 67，实际 {len(result)}')
    return result


def parse_bus_matrix(text, path=BI):
    lines = text.splitlines()
    starts = [i for i, line in enumerate(lines) if re.fullmatch(r'### 3\.2 总线矩阵（草案）\s*', line)]
    if len(starts) != 1:
        raise ValueError('BI 缺唯一 §3.2 总线矩阵标题')
    table, ended = [], False
    for index in range(starts[0] + 1, len(lines)):
        line = lines[index]
        if re.match(r'#{1,3} ', line):
            break
        if line.startswith('|'):
            if ended or not line.endswith('|'):
                raise ValueError('总线矩阵表格结构不符')
            cells = [cell.strip() for cell in line[1:-1].split('|')]
            if len(cells) != len(HEADER):
                raise ValueError(f'总线矩阵:{index + 1}: 列数不符')
            table.append((index + 1, cells))
        elif table:
            ended = True
    if len(table) != 18:
        raise ValueError(f'总线矩阵应有表头、分隔行与 16 行事实，实际 {len(table)} 行')
    if table[0][1] != HEADER:
        raise ValueError('总线矩阵表头不符')
    if not all(re.fullmatch(r':?-{3,}:?', cell) for cell in table[1][1]):
        raise ValueError('总线矩阵分隔行不符')
    rows, seen = [], set()
    for line, cells in table[2:]:
        if any(not cell for cell in cells) or cells[0] in seen:
            raise ValueError(f'总线矩阵:{line}: 空单元格或事实行名重复')
        seen.add(cells[0])
        rows.append({'fact': cells[0], 'cells': dict(zip(HEADER, cells)), 'source': f'{path.as_posix()}:{line}'})
    return {'header': table[0][1], 'source': f'{path.as_posix()}:{table[0][0]}', 'rows': rows}


def documents(root):
    objects, events = by_object(root), by_event(root)
    matrix = parse_bus_matrix((root / BI).read_text(encoding='utf-8'))
    counts = {'by_object': len(objects), 'object_classifications': {
        kind: sum(row['classification'] == kind for row in objects)
        for kind in ('declared', 'none', 'none_with_note')},
        'object_in_system': dict(Counter(str(row['in_system']).lower() for row in objects)),
        'by_event': len(events), 'event_publishers': dict(Counter(row['publisher'] for row in events)),
        'event_missing_description': sum('description' in row['missing_fields'] for row in events),
        'by_bi_bus_matrix': len(matrix['rows'])}
    raw = {'meta': {'sources': [str(OBJECTS), *map(str, EVENTS), str(BI)], 'counts': counts},
           'by_object': objects, 'by_event': events, 'by_bi_bus_matrix': matrix}
    gaps = ['# 业务事实候选抽取缺口', '', '由 extract_facts.py 生成，勿手改。', '',
            '三处候选仅原样并列，不合并、不裁定是否经营事实。', '',
            '## 输入路径差异', '',
            '派工卡写 `docs/modernization/r0/BI_SCOPE.md`；当前仓库实际输入为 `docs/modernization/BI_SCOPE.md` §3.2。脚本固定读取实际路径，不自动回退。', '',
            f'## none_with_note（{counts["object_classifications"]["none_with_note"]}）', '',
            'N11-1 仅以空串、「—」或「-」判缺失，带括注值不会命中该条件。以下列出全部 none_with_note；不等同于 N11-1 的一级业务对象检查范围，也不裁定事实含义。', '']
    for row in objects:
        if row['classification'] == 'none_with_note':
            gaps.append(f'- {row["object"]} {row["object_name"]}：{row["facts"]}；in_system={str(row["in_system"]).lower()}；`{row["source"]}`')
    gaps.extend(['', f'## 事件 description 原字段缺失（{counts["event_missing_description"]}）', '',
                 '原字段缺失时输出 description: null，并在 missing_fields 中标记；不从 title、payload 或 x-event 补写。x-event 全映射原样保留，避免推断哪些扩展字段与事实有关。', ''])
    for row in events:
        if row['missing_fields']:
            gaps.append(f'- {row["event_type"]}：`{row["source"]}`')
    return {'facts.raw.yaml': '# 由 extract_facts.py 生成，勿手改\n' + yaml.safe_dump(
        raw, allow_unicode=True, sort_keys=False, width=200), 'GAPS.md': '\n'.join([*gaps, ''])}


def generate(root=ROOT, check=False):
    outputs = documents(root)
    different = [name for name, content in outputs.items() if not (root / OUTPUT / name).is_file()
                 or (root / OUTPUT / name).read_bytes() != content.encode('utf-8')]
    if check and different:
        raise ValueError('事实候选生成物不一致：' + '、'.join(different))
    if not check:
        (root / OUTPUT).mkdir(parents=True, exist_ok=True)
        for name in different:
            (root / OUTPUT / name).write_bytes(outputs[name].encode('utf-8'))
    print(yaml.safe_load(outputs['facts.raw.yaml'])['meta']['counts'])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    try:
        generate(check=args.check)
        return 0
    except (OSError, UnicodeError, ValueError, yaml.YAMLError) as error:
        print(f'事实候选抽取失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
