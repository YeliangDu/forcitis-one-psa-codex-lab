#!/usr/bin/env python3
"""ARC-1021-ARC-08：扫描已建聚合，逐行转换规划清单；--check 只比对产物。"""
import argparse
import datetime
import re
import sys
from collections import Counter
from pathlib import Path

import yaml

from scan_acceptance_tests import LEXER, pairs

ROOT = Path(__file__).resolve().parents[2]
ARCH = Path('docs/modernization/architecture')
OUTPUT = ARCH / 'ssot/i03-aggregates'
JAVA = Path('backend/src/main/java/com/forcitis/one')
REGISTRY = Path('backend/architecture/data-access-registry.yaml')
DRAFT = ARCH / 'proposals/ENTERPRISE_AGGREGATES_DRAFT.md'
UNBUILT = ARCH / 'panorama/unbuilt_roots.yaml'
MAX_DUE_DAYS = 60   # 名单只容过渡期，到期日最远为当天起 60 天
PREFIX = 'com.forcitis.one.'
HEADERS = ['#', '聚合', '内含', '对应', '不变量', '命令（→ 事件；前置 INV）', '对外事件（订阅方）', '引用与依赖方式']


def read_yaml(path):
    value = yaml.safe_load(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise ValueError(f'{path}: 应为映射')
    return value


def java_unit(path, root):
    source = path.read_text(encoding='utf-8')
    matches = list(LEXER.finditer(source))
    if any(m.lastgroup == 'punct' and (m.group() in {'"', "'"}
           or source[m.start():m.start() + 2] == '/*') for m in matches):
        raise ValueError(f'{path}: 未闭合的 Java 字符串或注释')
    code = ' '.join(m.group() for m in matches if m.lastgroup not in {'comment', 'string'})
    code = re.sub(r'\s*\.\s*', '.', code)
    package = re.search(r'\bpackage\s+([\w.]+)\s*;', code)
    if not package:
        raise ValueError(f'{path}: 缺少 package')
    package_name = package[1]
    imports = re.findall(r'\bimport\s+([\w.*]+)\s*;', code)
    body = re.sub(r'\b(?:package|import)\s+[^;]+;', '', code)
    types = list(re.finditer(r'\b(class|record|interface|enum)\s+(\w+)\b([^{};]*)\{', body))
    own = [t for t in types if t[2] == path.stem]
    if not own:
        raise ValueError(f'{path}: 未找到与文件名一致的类型声明')
    if len(own) != 1:
        raise ValueError(f'{path}: 类型编号重复：{path.stem}')
    tokens = [(m.group(), m.start()) for m in matches if m.lastgroup not in {'comment', 'string'}]
    return {'name': path.stem, 'package': package_name, 'fq': package_name + '.' + path.stem,
            'imports': imports, 'body': body, 'header': own[0][3], 'kind': own[0][1],
            'tokens': tokens, 'pairs': pairs(tokens), 'path': path.relative_to(root).as_posix()}


def resolves(unit, name, fq):
    if name == fq:
        return True
    if name != fq.rsplit('.', 1)[-1]:
        return False
    explicit = [i for i in unit['imports'] if i.rsplit('.', 1)[-1] == name]
    if explicit:
        return explicit == [fq]
    return fq.rsplit('.', 1)[0] == unit['package'] or fq.rsplit('.', 1)[0] + '.*' in unit['imports']


def implements(unit, keyword, fq):
    match = re.search(rf'\b{keyword}\s+(.+)', unit['header'])
    names = re.findall(r'[\w.]+', match[1]) if match else []
    if keyword == 'extends':
        names = names[:1]
    return any(resolves(unit, name, fq) for name in names)


def references(unit, fq):
    name = fq.rsplit('.', 1)[-1]
    return bool(re.search(rf'(?<![\w.]){re.escape(fq)}\b', unit['body']) or
                resolves(unit, name, fq) and re.search(rf'(?<![\w.]){name}\b', unit['body']))


def published(unit, events):
    found = set()
    tokens, links = unit['tokens'], unit['pairs']
    for i, (value, _) in enumerate(tokens[:-1]):
        if value not in {'record', 'emit', 'publish'} or tokens[i + 1][0] != '(':
            continue
        args = ' '.join(t[0] for t in tokens[i + 2:links[i + 1]])
        args = re.sub(r'\s*\.\s*', '.', args)
        for name in re.findall(r'\bnew\s+([\w.]+)\s*\(', args):
            found.update(event['fq'] for event in events if resolves(unit, name, event['fq']))
    return found


def read_unbuilt(root, today):
    """Registry roots whose domain class is not written yet; each carries an owner and a due date, and fails once the date has passed."""
    if not (root / UNBUILT).is_file():
        return {}
    rows = read_yaml(root / UNBUILT).get('roots')
    if not isinstance(rows, list):
        raise ValueError(f'{UNBUILT}: roots 应为列表')
    listed = {}
    for row in rows:
        if not isinstance(row, dict) or not all(row.get(k) for k in ('table', 'owner', 'due', 'reason')):
            raise ValueError(f'{UNBUILT}: 每项须有 table、owner、due、reason：{row}')
        if row['table'] in listed:
            raise ValueError(f'{UNBUILT}: 重复：{row["table"]}')
        due = row['due'] if isinstance(row['due'], datetime.date) else datetime.date.fromisoformat(str(row['due']))
        if due > today + datetime.timedelta(days=MAX_DUE_DAYS):
            raise ValueError(f'{UNBUILT}: {row["table"]} 到期日 {due} 超过 {MAX_DUE_DAYS} 天上限，须拆小或改期')
        if due < today:
            raise ValueError(f'{UNBUILT}: {row["table"]} 已过期（{due}，负责 {row["owner"]}）')
        listed[row['table']] = {**row, 'due': due.isoformat()}
    return listed


def collect(root, today=None, unbuilt=None):
    files = sorted((root / JAVA).glob('*/*/**/*.java'))
    if not files:
        raise ValueError(f'缺失或空的来源：{JAVA}')
    units, indexed = [], {}
    for path in files:
        parts = path.relative_to(root / JAVA).parts
        if parts[2] not in {'domain', 'application', 'events'} or path.name == 'package-info.java':
            continue
        unit = java_unit(path, root)
        unit.update(module='.'.join(parts[:2]), layer=parts[2])
        if unit['fq'] in indexed:
            raise ValueError(f'类型编号重复：{unit["fq"]}')
        indexed[unit['fq']] = unit
        units.append(unit)
    registry = read_yaml(root / REGISTRY).get('tables')
    if not isinstance(registry, dict) or not registry:
        raise ValueError('数据访问注册表 tables 缺失或为空')
    pending = read_unbuilt(root, today or datetime.date.today())
    roots = {u['fq']: {'code'} for u in units if u['layer'] == 'domain' and u['kind'] == 'class'
             and implements(u, 'extends', PREFIX + 'kernel.AggregateRoot')}
    for table, row in registry.items():
        if not isinstance(row, dict) or 'role' not in row or not re.fullmatch(r'[a-z_]+\.[a-z_*]+', table):
            raise ValueError(f'无效的注册表条目：{table}')
        if row['role'] != 'AGGREGATE_ROOT':
            continue
        candidates = [u for u in units if u['layer'] == 'domain' and u['kind'] in {'class', 'record', 'enum'}
                      and u['name'] == row.get('aggregate') and u['module'].replace('.', '_') == table.split('.')[0]]
        if table in pending:
            if candidates:
                raise ValueError(f'{table}: 已有 domain 类 {row.get("aggregate")}，从 {UNBUILT} 删去')
            if unbuilt is not None:
                unbuilt.append(pending[table])
            continue
        if len(candidates) != 1:
            raise ValueError(f'{table}: AGGREGATE_ROOT 未唯一对应 domain 类：{row.get("aggregate")}')
        roots.setdefault(candidates[0]['fq'], set()).add('registry')
    events = [u for u in units if u['layer'] in {'domain', 'events'}
              and implements(u, 'implements', PREFIX + 'kernel.event.DomainEvent')]
    result = []
    for fq, sources in sorted(roots.items()):
        unit = indexed[fq]
        commands = [u for u in units if u['module'] == unit['module'] and u['layer'] == 'application'
                    and u['kind'] in {'class', 'record', 'enum'} and references(u, fq)]
        module_events = [e for e in events if e['module'] == unit['module']]
        emitted = set().union(*(published(u, module_events) for u in [unit, *commands]))
        item = {'id': unit['module'] + '/' + unit['name'], 'bc': unit['module'], 'root_class': unit['path'],
                'source': sorted(sources),
                'tables': sorted(t for t, r in registry.items() if r.get('aggregate') == unit['name']
                                 and t.split('.')[0] == unit['module'].replace('.', '_')),
                'commands': sorted(c['name'] for c in commands),
                'events': sorted(e['module'] + '.' + e['name'] for e in module_events if e['fq'] in emitted)}
        if set(e['fq'] for e in module_events) - emitted:
            item['events_in_module'] = sorted(e['module'] + '.' + e['name'] for e in module_events)
        result.append(item)
    stale = sorted(set(pending) - {t for t, r in registry.items() if isinstance(r, dict) and r.get('role') == 'AGGREGATE_ROOT'})
    if stale:
        raise ValueError(f'{UNBUILT}: 不是注册表中的 AGGREGATE_ROOT：' + '、'.join(stale))
    if not result:
        raise ValueError('未扫描到已建聚合')
    return sorted(result, key=lambda row: row['id'])


def parse_planned(text):
    lines = text.splitlines()
    start = next((i for i, line in enumerate(lines) if line.startswith('### 2.2 ')), None)
    end = next((i for i, line in enumerate(lines) if line.startswith('### 2.3 ')), None)
    if start is None or end is None or start >= end:
        raise ValueError('缺少 §2.2 / §2.3 聚合清单边界')
    aggregates, references, headers, seen, group, header = [], [], [], set(), None, None
    for no in range(start + 1, end):
        line = lines[no]
        if re.match(r'^\*\*B-\d+', line):
            group, header = line, None
        if not line.startswith('|'):
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if cells[0] == '#':
            normalized = [c.replace('聚合（根）', '聚合') for c in cells]
            if normalized != HEADERS or group is None:
                raise ValueError(f'§2.2:{no + 1}: 表头不符或缺 BC')
            header = cells
            if cells not in headers:
                headers.append(cells)
            continue
        if all(re.fullmatch(r':?-+:?', c) for c in cells):
            if header is None or len(cells) != len(header):
                raise ValueError(f'§2.2:{no + 1}: 分隔行列数不符')
            continue
        if header is None or len(cells) != len(header):
            raise ValueError(f'§2.2:{no + 1}: 表头列数不符')
        if not re.fullmatch(r'(AGG|REF)-\d{2,3}', cells[0]):
            raise ValueError(f'§2.2:{no + 1}: 非法编号：{cells[0]}')
        if cells[0] in seen:
            raise ValueError(f'§2.2:{no + 1}: 编号重复：{cells[0]}')
        seen.add(cells[0])
        row = {'id': cells[0], **dict(zip(header, cells)), 'BC': re.match(r'^\*\*(B-\d+)', group)[1],
               'bc_heading': group, 'source_line': no + 1}
        (aggregates if cells[0].startswith('AGG-') else references).append(row)
    if not aggregates:
        raise ValueError('§2.2 未读到聚合')
    return {'meta': {'source': DRAFT.as_posix(), 'section': '§2.2', 'count': len(aggregates),
                     'reference_count': len(references), 'row_count': len(aggregates) + len(references), 'headers': headers},
            'aggregates': aggregates, 'references': references}


def gap_report(built, planned):
    counts = Counter(tuple(a['source']) for a in built)
    lines = ['# 聚合登记差异', '', '由 panorama/scan_aggregates.py 生成，勿手改。', '',
             '## 统计与判据', '', f'- 已建：{len(built)}；仅 code {counts[("code",)]}；仅 registry {counts[("registry",)]}；两者 {counts[("code", "registry")]}。',
             '- registry 来源只取 role=AGGREGATE_ROOT；tables 取同模块全部同名 aggregate 表。',
             '- commands 取同模块 application 中直接引用根类型的类（含 record、查询视图和辅助类），不追间接调用。',
             '- events 仅取同模块 DomainEvent 类型在根或上述类的 record/emit/publish 参数内直接构造的证据；不做跨方法数据流推断。',
             '- 有未证实发布关系的同模块事件时，events_in_module 列出该模块全部事件；这不是发布归属结论。跨模块事件不计入 events。',
             '- 旧手写登记 ddd/aggregates.yaml 已退役（GOV-1023-ARC-01，intent 2026-10-23-154833）；其 invariants 已迁入 mapping.yaml，其余见 git 历史。', '']
    lines += ['## 发布关系待核（同模块事件回退）', '']
    lines += [f'- {a["id"]}：' + '、'.join(a['events_in_module']) for a in built if 'events_in_module' in a]
    meta = planned['meta']
    lines += ['', '## 规划清单条数核对', '',
              f'- `{DRAFT}` §2.2：AGG {meta["count"]} 行 → planned.yaml aggregates {len(planned["aggregates"])} 条。',
              f'- REF {meta["reference_count"]} 行 → references {len(planned["references"])} 条（原文明确无聚合，保留原文，不计 AGG）。',
              f'- 合计 {meta["row_count"]} 行；保留逐行表头、全部单元格原文、BC 标题与源行号；未合并、未改写文字。', '']
    return '\n'.join(lines)


def documents(root):
    unbuilt = []
    built = collect(root, unbuilt=unbuilt)
    planned = parse_planned((root / DRAFT).read_text(encoding='utf-8'))
    counts = Counter(tuple(a['source']) for a in built)
    built_doc = {'meta': {'count': len(built), 'code_only': counts[('code',)], 'registry_only': counts[('registry',)],
                          'both': counts[('code', 'registry')], 'sources': [JAVA.as_posix(), REGISTRY.as_posix()],
                          'registered_unbuilt': sorted(unbuilt, key=lambda row: row['table'])},
                 'aggregates': built}
    dump = lambda data: '# 由 panorama/scan_aggregates.py 生成，勿手改\n' + yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=200)
    for row in unbuilt:
        print(f'告警：{row["table"]} 已登记为聚合根、domain 类未建（负责 {row["owner"]}，到期 {row["due"]}）', file=sys.stderr)
    return {'built.yaml': dump(built_doc), 'planned.yaml': dump(planned), 'GAPS.md': gap_report(built, planned)}


def generate(root=ROOT, check=False):
    contents = {name: text.encode('utf-8') for name, text in documents(root).items()}
    if check:
        different = [name for name, data in contents.items() if not (root / OUTPUT / name).is_file()
                     or (root / OUTPUT / name).read_bytes() != data]
        if different:
            raise ValueError('产物不一致，重跑 scan_aggregates.py：' + '、'.join(different))
    else:
        (root / OUTPUT).mkdir(parents=True, exist_ok=True)
        for name, data in contents.items():
            (root / OUTPUT / name).write_bytes(data)
    print('聚合登记：' + '、'.join(contents) + ('；check 通过' if check else '；已生成'))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    try:
        generate(check=args.check)
        return 0
    except (OSError, ValueError, yaml.YAMLError) as error:
        print(f'聚合登记失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
