#!/usr/bin/env python3
"""按来源原文生成命令目录（不入仓）；--check 只逐字节比较，不写文件。

先运行 python3 docs/modernization/build_all.py，再运行 --check。
command-ids.yaml 是持久登记，transitions.yaml 是转换正本，二者仍入仓。
"""
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


def former_names(row, allow_empty=False):
    names = row.get('former_names', [])
    if not isinstance(names, list) or ('former_names' in row and not names and not allow_empty):
        raise ValueError('former_names 应为非空文本列表')
    for name in names:
        string(name, 'former_names')
    return names


def registered_ids(root, seed=False):
    path, source = OUTPUT / 'command-ids.yaml', OUTPUT / 'commands.yaml'
    if (root / path).exists():
        return rows(read(root, path), 'ids')
    if seed and not (root / source).is_file():
        raise ValueError(f'{source} 缺失，无法 --seed；请从 generated-docs 分支或已核实的备份恢复快照；'
                         f'优先从 git 恢复 {path}，不要重建空登记')
    if not seed:
        raise ValueError(f'{path} 缺失；请从 git 恢复，退役记录与旧名会丢失。确需从 {source} 重新起底，用 --seed')
    return [dict(id=row['id'], aggregate=row['aggregate'], name=row['name'], former_names=[], retired=False)
            for row in rows(read(root, source), 'commands')]


def registry_index(registry):
    index, ids = {}, set()
    for row in registry:
        key = command_key(row, 'aggregate', 'name')
        ident = row.get('id')
        if not isinstance(ident, str) or not re.fullmatch(r'CMD-[0-9a-f]{8}', ident):
            raise ValueError('无效的命令 id')
        if ident in ids:
            raise ValueError(f'id 冲突：{ident}')
        if not isinstance(row.get('retired'), bool) or 'former_names' not in row:
            raise ValueError(f'{ident}: 缺失或无效的 retired / former_names')
        ids.add(ident)
        for name in [key[1], *former_names(row, allow_empty=True)]:
            alias = key[0], name
            if alias in index:
                raise ValueError(f'登记名称重复：{alias}')
            index[alias] = row
    return index, ids


def resolve_ids(registry, declarations):
    index, ids = registry_index(registry)
    claims, selected = {}, {}
    for key, names in declarations.items():
        for name in names:
            alias = key[0], name
            if name in claims and claims[name] != key:
                raise ValueError(f'旧名重复声明：{alias}')
            claims[name] = key
            if alias not in index and any(old == name for _, old in index):
                raise ValueError(f'旧名跨聚合：{key} / {name}')
    for key, names in declarations.items():
        for name in names:
            if (key[0], name) not in index:
                raise ValueError(f'旧名未登记：{key} 声明的 {name} 不在编号登记中')
    used = {}
    for key in sorted(declarations):
        candidates = {index[alias]['id']: index[alias] for alias in
                      [key, *((key[0], name) for name in declarations[key])] if alias in index}
        if len(candidates) > 1:
            raise ValueError(f'旧名指向多个编号：{key}')
        row = next(iter(candidates.values()), None)
        if row is None:
            ident = command_id(*key)
            if ident in ids:
                raise ValueError(f'id 冲突：{ident} / {key}')
            row = dict(id=ident, aggregate=key[0], name=key[1], former_names=[], retired=False)
            registry.append(row)
            ids.add(ident)
        ident = row['id']
        if ident in used and used[ident] != key:
            raise ValueError(f'同一编号对应多条命令：{ident}')
        used[ident] = key
        row['former_names'] = sorted(set([row['name'], *row['former_names'], *declarations[key]]) - {key[1]})
        row['name'] = key[1]
        selected[key] = ident
    for row in registry:
        row['retired'] = row['id'] not in used
    registry.sort(key=lambda row: row['id'])
    registry_index(registry)
    return selected


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


def merge_commands(root, aggregates, registry=None):
    merged, counts, sources, declarations = {}, {}, {}, {}
    for group in GROUPS:
        sources[group] = rows(read(root, CLOSEOUT / group / 'commands.yaml'), 'commands')
        for row in sources[group]:
            key = command_key(row, 'aggregate', 'name')
            declarations.setdefault(key, set()).update(former_names(row))
    entries = rows(read(root, CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml'), 'entries')
    for row in entries:
        key = command_key(row, 'object', 'command')
        declarations.setdefault(key, set()).update(former_names(row))
    ids = resolve_ids(registered_ids(root) if registry is None else registry, declarations)

    def add(key, kind, path, index, group):
        if key not in merged:
            ident = ids[key]
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
        commands = sources[group]
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


def fill_transitions(text, commands, renamed=None):
    renamed = renamed or {}
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
            if key not in index and key in renamed:
                raise ValueError(f'{ident}: 转换的命令原文仍用旧名「{key[1]}」，请同步改为「{renamed[key]}」')
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


def generate(root=ROOT, check=False, seed=False):
    registry = registered_ids(root, seed)
    commands, counts = merge_commands(root, planned_aggregates(root), registry)
    built = built_commands(root)
    renamed = {(row['aggregate'], old): row['name'] for row in registry if not row['retired'] for old in row['former_names']}
    transitions, stats = fill_transitions((root / TRANSITIONS).read_bytes().decode('utf-8'), commands, renamed)
    model, gaps = render(commands, built, counts, stats)
    outputs = {OUTPUT / 'commands.yaml': model, OUTPUT / 'GAPS.md': gaps, TRANSITIONS: transitions,
               OUTPUT / 'command-ids.yaml': yaml.safe_dump({'ids': registry}, allow_unicode=True, sort_keys=False, width=200)}
    different = [path for path, content in outputs.items()
                 if not (root / path).is_file() or (root / path).read_bytes() != content.encode('utf-8')]
    if check and different:
        raise ValueError('生成物不一致：' + '、'.join(map(str, different)) +
                         '；请先运行 python3 docs/modernization/build_all.py 生成，再运行 --check')
    if not check:
        for path in different:
            (root / path).parent.mkdir(parents=True, exist_ok=True)
            (root / path).write_bytes(outputs[path].encode('utf-8'))
    print(f'命令 {len(commands)}；已建 {len(built)}；转换回填 ' +
          '、'.join(f'{section} {part["filled"]}/{part["total"]}' for section, part in stats.items()))


def self_test():
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for seed in (False, True):
            try:
                registered_ids(root, seed=seed)
            except ValueError as error:
                assert ('generated-docs' if seed else '从 git 恢复') in str(error)
            else:
                raise AssertionError('登记或起底快照缺失应失败')
        path = root / OUTPUT / 'command-ids.yaml'
        path.parent.mkdir(parents=True)
        path.write_text('ids: []\n', encoding='utf-8')
        assert registered_ids(root) == [], '有显式登记时不依赖生成快照'
    print('scan_commands --self-test：通过（登记缺失、起底快照缺失失败；有登记无需生成快照）')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--seed', action='store_true', help='编号登记缺失时从已恢复的 commands.yaml 快照重新起底（不恢复退役记录与旧名）')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            self_test()
            return 0
        generate(check=args.check, seed=args.seed)
        return 0
    except (OSError, UnicodeError, ValueError, yaml.YAMLError) as error:
        print(f'命令登记失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
