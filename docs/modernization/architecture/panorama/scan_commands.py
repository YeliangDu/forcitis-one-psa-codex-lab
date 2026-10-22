#!/usr/bin/env python3
"""按来源原文生成命令编号；--check 只逐字节比较，不写文件。"""
import argparse
import hashlib
import re
import sys
from collections import defaultdict
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[4]
ARCH = Path('docs/modernization/architecture')
CLOSEOUT = ARCH / 'proposals/closeout'
OUTPUT = ARCH / 'ssot/11-commands'
TRANSITIONS = ARCH / 'ssot/10-objects/transitions.yaml'
GROUPS = ('finance', 'people', 'revenue')


def mapping(node):
    if not isinstance(node, yaml.MappingNode):
        raise ValueError('应为 YAML 映射')
    result = {}
    for key, value in node.value:
        if not isinstance(key, yaml.ScalarNode) or key.value in result:
            raise ValueError('无效或重复的 YAML 键')
        result[key.value] = value
    return result


def document(text):
    node = yaml.compose(text)
    mapping(node)

    def validate(current, active=()):
        if id(current) in active:
            raise ValueError('不支持递归 YAML 别名')
        active = (*active, id(current))
        if isinstance(current, yaml.MappingNode):
            for child in mapping(current).values():
                validate(child, active)
        elif isinstance(current, yaml.SequenceNode):
            for child in current.value:
                validate(child, active)

    validate(node)
    return yaml.safe_load(text), node


def read(root, path):
    try:
        return document((root / path).read_bytes().decode('utf-8'))[0]
    except (ValueError, yaml.YAMLError) as error:
        raise ValueError(f'{path}: {error}') from error


def rows(data, field):
    value = data.get(field)
    if not isinstance(value, list) or any(not isinstance(row, dict) for row in value):
        raise ValueError(f'{field} 应为条目列表')
    return value


def string(value, field, nullable=False):
    if nullable and value is None:
        return
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f'{field} 应为非空文本')


def command_key(row, aggregate, name):
    string(row.get(aggregate), aggregate)
    string(row.get(name), name)
    return row[aggregate], row[name]


def command_id(aggregate, name):
    return 'CMD-' + hashlib.sha256(f'{aggregate}::{name}'.encode('utf-8')).hexdigest()[:8]


def planned_aggregates(root):
    result = {}
    for row in rows(read(root, ARCH / 'ssot/i03-aggregates/planned.yaml'), 'aggregates'):
        ident = row.get('id')
        string(ident, '聚合 id')
        if ident in result:
            raise ValueError(f'规划聚合编号重复：{ident}')
        name = row.get('聚合（根）') if '聚合（根）' in row else row.get('聚合')
        fields = [name, row.get('内含'), row.get('对应')]
        for value in fields:
            if not isinstance(value, str):
                raise ValueError(f'{ident}: 聚合、内含、对应应为文本')
        string(row.get('BC'), 'BC', nullable=True)
        objects = list(dict.fromkeys(re.findall(r'OBJ-\d{2,3}', ' '.join(fields))))
        result[ident] = {'owner_bc': row.get('BC'), 'objects': objects}
    return result


def merge_commands(root, aggregates):
    merged, ids, counts = {}, {}, {}

    def add(key, kind, path, index, group):
        if key not in merged:
            ident = command_id(*key)
            if ident in ids:
                raise ValueError(f'id 冲突：{ident}，{ids[ident]} 与 {key}')
            ids[ident] = key
            merged[key] = {'id': ident, 'name': key[1], 'aggregate': key[0],
                           **aggregates.get(key[0], {'owner_bc': None, 'objects': []}),
                           'actor': {'role': None, 'function': None}, 'params': None,
                           'preconditions': None, 'emits': None, 'group': [], 'in': [], 'source': []}
        item = merged[key]
        if kind not in item['in']:
            item['in'].append(kind)
        if group and group not in item['group']:
            item['group'].append(group)
        item['source'].append({'file': path.as_posix(), 'section': 'commands' if kind == 'commands' else 'entries', 'index': index})
        return item

    for group in GROUPS:
        path = CLOSEOUT / group / 'commands.yaml'
        commands = rows(read(root, path), 'commands')
        counts[group] = len(commands)
        for index, row in enumerate(commands):
            key = command_key(row, 'aggregate', 'name')
            if not isinstance(row.get('params'), list) or not isinstance(row.get('emits'), (list, str)):
                raise ValueError(f'{path} commands[{index}]: params 或 emits 结构不符')
            if row.get('preconditions') is not None and not isinstance(row['preconditions'], (list, str)):
                raise ValueError(f'{path} commands[{index}]: preconditions 结构不符')
            values = {field: row.get(field) for field in ('params', 'preconditions', 'emits')}
            previous = merged.get(key)
            if previous and 'commands' in previous['in'] and any(previous[k] != v for k, v in values.items()):
                raise ValueError(f'{path} commands[{index}]: 同一命令的参数、前提或事件冲突：{key}')
            add(key, 'commands', path, index, group).update(values)
    path = CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml'
    entries = rows(read(root, path), 'entries')
    counts['matrix'] = len(entries)
    for index, row in enumerate(entries):
        key = command_key(row, 'object', 'command')
        raci = row.get('raci')
        if not isinstance(raci, dict) or not isinstance(raci.get('A'), dict):
            raise ValueError(f'{path} entries[{index}]: 缺 raci.A 映射')
        actor = {field: raci['A'].get(field) for field in ('role', 'function')}
        for field, value in actor.items():
            string(value, f'raci.A.{field}', nullable=True)
        group = row.get('a_source')
        string(group, 'a_source', nullable=True)
        group = group.removeprefix('group:') if group and group.startswith('group:') else None
        previous = merged.get(key)
        if previous and 'matrix' in previous['in'] and previous['actor'] != actor:
            raise ValueError(f'{path} entries[{index}]: 同一命令的 actor 冲突：{key}')
        add(key, 'matrix', path, index, group)['actor'] = actor
    commands = [merged[key] for key in sorted(merged)]
    same_names(commands)
    return commands, counts


def same_names(commands):
    names = defaultdict(list)
    for item in commands:
        names[item['name']].append(item['id'])
    for item in commands:
        item['group'].sort()
        if len(names[item['name']]) > 1:
            item['same_name_in'] = sorted(ident for ident in names[item['name']] if ident != item['id'])


def built_commands(root):
    result, seen = [], set()
    for row in rows(read(root, ARCH / 'ssot/i03-aggregates/built.yaml'), 'aggregates'):
        for field in ('id', 'bc'):
            string(row.get(field), field)
        if row['id'] in seen:
            raise ValueError(f'已建聚合编号重复：{row["id"]}')
        seen.add(row['id'])
        commands = row.get('commands')
        if not isinstance(commands, list):
            raise ValueError(f'{row["id"]}: commands 应为类名列表')
        for name in commands:
            string(name, '命令类名')
            result.append({'class': name, 'module': row['bc'], 'aggregate': row['id']})
    return sorted(result, key=lambda item: (item['aggregate'], item['class']))


def fill_transitions(text, commands):
    data, tree = document(text)
    index = {(item['aggregate'], item['name']): item['id'] for item in commands}
    replacements, statistics, seen = [], {}, set()
    sections = mapping(tree)
    for section in ('transitions', 'pending'):
        entries = rows(data, section)
        missing, filled = [], 0
        for row, node in zip(entries, sections[section].value):
            ident = row.get('id')
            string(ident, '转换 id')
            if ident in seen:
                raise ValueError(f'转换编号重复：{ident}')
            seen.add(ident)
            string(row.get('aggregate'), 'aggregate')
            if 'command' not in row:
                raise ValueError(f'{ident}: 缺 command 字段')
            string(row['command'], 'command', nullable=True)
            key = row['aggregate'], row['command']
            value = mapping(node).get('by_command')
            if not isinstance(value, yaml.ScalarNode) or not (
                    value.tag == 'tag:yaml.org,2002:null' or
                    value.tag == 'tag:yaml.org,2002:str' and re.fullmatch(r'CMD-[0-9a-f]{8}', value.value)):
                raise ValueError(f'{ident}: 缺失或无效的 by_command')
            field = next(key_node for key_node, _ in node.value if key_node.value == 'by_command')
            raw = text[value.start_mark.index:value.end_mark.index]
            if value.start_mark.index < field.end_mark.index or '&' in raw:
                raise ValueError(f'{ident}: by_command 不支持 YAML 锚点或别名')
            target = index.get(key)
            if row['by_command'] != target:
                replacement = target or 'null'
                if not raw and text[value.start_mark.index - 1] == ':':
                    replacement = ' ' + replacement
                replacements.append((value.start_mark.index, value.end_mark.index, replacement))
            if target:
                filled += 1
            else:
                missing.append({'id': ident, 'aggregate': key[0], 'command': key[1]})
        statistics[section] = {'total': len(entries), 'filled': filled, 'missing': missing}
    for start, end, value in sorted(replacements, reverse=True):
        text = text[:start] + value + text[end:]
    return text, statistics


def render(commands, built, counts, transitions):
    both = sum(set(item['in']) == {'commands', 'matrix'} for item in commands)
    only_commands = sum(item['in'] == ['commands'] for item in commands)
    only_matrix = sum(item['in'] == ['matrix'] for item in commands)
    missing_bc = [item for item in commands if not item['owner_bc']]
    same = defaultdict(list)
    for item in commands:
        if item.get('same_name_in'):
            same[item['name']].append(item)
    meta = {'sources': counts, 'count': len(commands), 'both': both, 'only_commands': only_commands,
            'only_matrix': only_matrix, 'built_count': len(built), 'missing_owner_bc': len(missing_bc),
            'same_name_groups': len(same), 'same_name_commands': sum(map(len, same.values()))}
    model = '# 由 panorama/scan_commands.py 生成，勿手改\n' + yaml.safe_dump(
        {'meta': meta, 'commands': commands, 'built': built}, allow_unicode=True, sort_keys=False, width=200)
    lines = ['# 命令编号缺口', '', '由 panorama/scan_commands.py 生成，勿手改。', '', '## 合并核对', '',
             *[f'- commands {group}：{counts[group]}' for group in GROUPS],
             f'- commands 合计：{sum(counts[group] for group in GROUPS)}', f'- matrix：{counts["matrix"]}',
             f'- 合并后：{len(commands)}；两处都有：{both}；仅 commands：{only_commands}；仅 matrix：{only_matrix}',
             f'- 已建命令类与聚合关系：{len(built)}（未做类名与 CMD 对照）', '', f'## 无 owner_bc（{len(missing_bc)}）', '',
             *[f'- {item["id"]} — {item["aggregate"]} — {item["name"]}' for item in missing_bc], '',
             'owner_bc 仅取 planned.yaml 的 aggregates[].BC 原文；未列入该段的引用不推断 BC。',
             'actor 只取 raci.A 的 role、function 原文；未声明的字段保留 null，不从实例字段推断角色。',
             'source.index 为各来源列表的从 0 开始序号；group 汇集 commands 文件分组与 matrix 的 a_source group 值。', '',
             f'## 同名跨聚合（{len(same)} 组，{sum(map(len, same.values()))} 条）', '']
    for name, items in sorted(same.items()):
        lines.append(f'- {name}：' + '；'.join(f'{item["aggregate"]} / {item["id"]}' for item in items))
    total = sum(part['total'] for part in transitions.values())
    filled = sum(part['filled'] for part in transitions.values())
    lines += ['', '## 转换回填率', '', f'- 合计：{filled}/{total}（{filled / total if total else 0:.2%}）']
    for section, part in transitions.items():
        lines += [f'- {section}：{part["filled"]}/{part["total"]}；未回填 {len(part["missing"])}']
    lines += ['', '只匹配 (aggregate, command 原文)，不去空格、不去「〔缺〕」、不做近似或同义匹配。', '', '### 未回填清单', '']
    for section, part in transitions.items():
        lines += [f'- {section} / {item["id"]} — {item["aggregate"]} — {item["command"]}' for item in part['missing']]
    return model, '\n'.join(lines) + '\n'


def generate(root=ROOT, check=False):
    commands, counts = merge_commands(root, planned_aggregates(root))
    built = built_commands(root)
    transitions, stats = fill_transitions((root / TRANSITIONS).read_bytes().decode('utf-8'), commands)
    model, gaps = render(commands, built, counts, stats)
    outputs = {OUTPUT / 'commands.yaml': model, OUTPUT / 'GAPS.md': gaps, TRANSITIONS: transitions}
    different = [path for path, content in outputs.items()
                 if not (root / path).is_file() or (root / path).read_bytes() != content.encode('utf-8')]
    if check and different:
        raise ValueError('生成物不一致：' + '、'.join(map(str, different)))
    if not check:
        for path in different:
            (root / path).parent.mkdir(parents=True, exist_ok=True)
            (root / path).write_bytes(outputs[path].encode('utf-8'))
    print(f'命令 {len(commands)}；已建 {len(built)}；转换回填 ' +
          '、'.join(f'{section} {part["filled"]}/{part["total"]}' for section, part in stats.items()))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    try:
        generate(check=args.check)
        return 0
    except (OSError, UnicodeError, ValueError, yaml.YAMLError) as error:
        print(f'命令登记失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
