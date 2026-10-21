#!/usr/bin/env python3
import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.dont_write_bytecode = True
from scan_acceptance_tests import LEXER, annotation, declaration_comments, display_name, pairs


ROOT = Path(__file__).resolve().parents[4]
OUTPUT = Path('docs/modernization/architecture/ssot/i08-execution-points')
RULE = re.compile(r'(?:INV|RULE)-\d+[a-z]?')
TESTS = {'Test', 'ParameterizedTest', 'TestFactory', 'ArchTest'}
KINDS = ('守护', '不变量', '数据约束', '授权', '豁免')
IDENTIFIER = r'(?:"(?:""|[^"])+"|[A-Za-z_][\w$]*)'
SQL = re.compile(r'(?P<space>\s+)|(?P<comment>--[^\n]*|/\*)|'
                 r"(?P<string>[eE]'(?:''|\\.|[^'\\])*'|'(?:''|[^'])*')|"
                 r'(?P<dollar>\$(?:[A-Za-z_]\w*)?\$)|'
                 rf'(?P<identifier>{IDENTIFIER})|(?P<punct>.)', re.S)


def point(key, kind, name, path, source, position, evidence=(), exempts=None):
    item = {'id': 'EP-' + hashlib.sha256(key.encode('utf-8')).hexdigest()[:10],
            'kind': kind, 'location': f'{path}:{source.count(chr(10), 0, position) + 1}',
            'name': name, 'enforces': sorted(set(RULE.findall(' '.join([name, *evidence]))))}
    if exempts is not None:
        item['exempts'] = exempts
    return item


def java_points(source, path):
    matches = list(LEXER.finditer(source))
    if any(m.lastgroup == 'punct' and (m.group() in {'"', "'"}
           or source[m.start():m.start() + 2] == '/*') for m in matches):
        raise ValueError('未闭合的 Java 字符串或注释')
    tokens = [(m.group(), m.start()) for m in matches if m.lastgroup != 'comment']
    links = pairs(tokens)
    code = ' '.join(m.group() if m.lastgroup not in {'string', 'comment'} else '' for m in matches)
    package = re.search(r'\bpackage\s+([\w. ]+)\s*;', code)
    if not package:
        raise ValueError('Java 源文件缺少 package 声明')
    package = package[1].replace(' ', '')
    if not (package == 'com.forcitis.one' or 'guard' in Path(path).parts[:-1]
            or re.search(r'\bimport\s+(?:static\s+)?com \. tngtech \. archunit\b', code)):
        return []
    result = []

    def members(start, end, classes):
        pending, i, declaration = {}, start, start
        while i < end:
            value = tokens[i][0]
            if value == '@':
                name, arguments, i = annotation(tokens, links, i)
                pending[name] = arguments
                continue
            if (value in {'class', 'interface', 'enum', 'record'} and (not i or tokens[i - 1][0] != '.')
                    and i + 1 < end and re.fullmatch(r'[^\W\d]\w*', tokens[i + 1][0])):
                body = i + 2
                while body < end and tokens[body][0] != '{':
                    body = links[body] + 1 if tokens[body][0] == '(' else body + 1
                if body == end:
                    raise ValueError('Java 类型缺少正文')
                members(body + 1, links[body], classes + [tokens[i + 1][0]])
                pending, i, declaration = {}, links[body] + 1, links[body] + 1
                continue
            if (value in {'(', '=', ';'} or value == ',' and 'ArchTest' in pending) and TESTS.intersection(pending):
                if not classes or (value != '(' and TESTS.intersection(pending) != {'ArchTest'}):
                    raise ValueError('测试注解未绑定方法或 ArchTest 字段')
                member = i - 1
                while tokens[member][0] == ']':
                    member -= 2
                name, position = tokens[member]
                title = display_name(pending['DisplayName']) if 'DisplayName' in pending else ''
                comments = declaration_comments(source, matches, tokens[declaration][1], position)
                result.append(point(package + '.' + '$'.join(classes) + '#' + name,
                                    '守护', name, path, source, position, [title, *comments]))
                if value != '(':
                    while i < end and tokens[i][0] != ';':
                        if tokens[i][0] in {'(', '[', '{'}:
                            i = links[i] + 1
                            continue
                        if tokens[i][0] == ',':
                            name, position = tokens[i + 1]
                            result.append(point(package + '.' + '$'.join(classes) + '#' + name,
                                                '守护', name, path, source, position, [title, *comments]))
                        i += 1
                    if i == end:
                        raise ValueError('ArchTest 字段缺少分号')
                    value = ';'
                pending = {}
            if value in {'(', '[', '{'}:
                if value == '{' and TESTS.intersection(pending):
                    raise ValueError('无法解析测试声明')
                i = links[i] + 1
                if value == '{':
                    pending, declaration = {}, i
                continue
            if value == ';':
                pending, declaration = {}, i + 1
            i += 1
        if TESTS.intersection(pending):
            raise ValueError('测试注解未绑定声明')

    members(0, len(tokens), [])
    return result


def sql_tokens(source):
    tokens, comments, i = [], [], 0
    while i < len(source):
        match = SQL.match(source, i)
        value, kind, end = match.group(), match.lastgroup, match.end()
        if value == '/*':
            depth = 1
            while depth:
                nested = re.search(r'/\*|\*/', source[end:])
                if nested is None:
                    raise ValueError('SQL 块注释未闭合')
                depth += 1 if nested.group() == '/*' else -1
                end += nested.end()
            value, kind = source[i:end], 'comment'
        if kind == 'dollar':
            closing = source.find(value, end)
            if closing < 0:
                raise ValueError(f'美元引号未闭合：{value}')
            end, value = closing + len(value), '$body$'
        if kind == 'punct' and value in {"'", '"', '$'}:
            raise ValueError('SQL 字符串、标识符或美元引号无效')
        if kind == 'comment':
            comments.append((i, end, value))
        elif kind != 'space':
            tokens.append((value, i))
        i = end
    pairs(tokens)
    return tokens, comments


def identifier(value):
    if not re.fullmatch(IDENTIFIER, value):
        raise ValueError(f'预期 SQL 标识符，实际为 {value}')
    return value[1:-1].replace('""', '"') if value.startswith('"') else value.lower()


def relation(tokens, i, schema):
    name = identifier(tokens[i][0])
    if i + 1 < len(tokens) and tokens[i + 1][0] == '.':
        return (name, identifier(tokens[i + 2][0])), i + 3
    return (schema, name), i + 1


def adjacent(source, comments, position):
    result = []
    for start, end, text in reversed([c for c in comments if c[1] <= position]):
        gap = source[end:position]
        if gap.strip() or gap.count('\n') > 1:
            break
        result.insert(0, text)
        position = start
    return result


def segments(tokens, separator=','):
    links, start, i = pairs(tokens), 0, 0
    while i < len(tokens):
        if tokens[i][0] in {'(', '['}:
            i = links[i] + 1
            continue
        if tokens[i][0] == separator:
            yield tokens[start:i]
            start = i + 1
        i += 1
    if start < len(tokens):
        yield tokens[start:]


def sql_constraints(part, table, add):
    unnamed = 0
    links, name, i = pairs(part), None, 0
    while i < len(part):
        value, position = part[i]
        upper = value.upper()
        if upper == 'CONSTRAINT':
            name, position = identifier(part[i + 1][0]), part[i][1]
            i += 2
            upper = part[i][0].upper()
            if upper not in {'CHECK', 'UNIQUE', 'EXCLUDE', 'PRIMARY', 'FOREIGN'}:
                raise ValueError(f'无法解析约束 {name}')
        if upper in {'CHECK', 'UNIQUE', 'EXCLUDE'}:
            if upper == 'CHECK' and (i + 1 == len(part) or part[i + 1][0] != '('):
                raise ValueError('约束缺少定义')
            if upper != 'CHECK' and part[0][0].upper() in {'CONSTRAINT', 'UNIQUE', 'EXCLUDE'} and not any(
                    t[0] == '(' for t in part[i + 1:]):
                raise ValueError('表级约束缺少定义')
            if name is None:
                unnamed += 1
            else:
                add('constraint', table, name, position, part[0][1],
                    '数据约束' if upper == 'UNIQUE' else '不变量')
            name = None
        if upper in {'PRIMARY', 'FOREIGN'}:
            name = None
        i = links[i] + 1 if part[i][0] in {'(', '['} else i + 1
    return unnamed


def sql_table(statement, schema, active, add):
    values = [t[0].upper() for t in statement]
    head, unnamed = ' '.join(values), 0
    if re.match(r'CREATE (?:UNLOGGED |TEMPORARY |TEMP )?TABLE ', head):
        i = values.index('TABLE') + 1
        i += 3 if values[i:i + 3] == ['IF', 'NOT', 'EXISTS'] else 0
        table, i = relation(statement, i, schema)
        if statement[i][0] != '(':
            raise ValueError('CREATE TABLE 缺少列定义')
        for part in segments(statement[i + 1:pairs(statement)[i]]):
            unnamed += sql_constraints(part, table, add)
    elif head.startswith('ALTER TABLE '):
        i = 2 + (2 if values[2:4] == ['IF', 'EXISTS'] else 0)
        i += int(values[i] == 'ONLY')
        table, i = relation(statement, i, schema)
        for part in segments(statement[i:]):
            words = [t[0].upper() for t in part]
            if words[:2] == ['DROP', 'CONSTRAINT']:
                j = 4 if words[2:4] == ['IF', 'EXISTS'] else 2
                active.pop(('constraint', table, identifier(part[j][0])), None)
            elif words[0] == 'ADD':
                unnamed += sql_constraints(part[1:], table, add)
            elif 'CONSTRAINT' in words or 'CHECK' in words or 'UNIQUE' in words:
                raise ValueError('不支持的 ALTER TABLE 约束操作')
    return unnamed


def scan_sql(source, path, active):
    tokens, comments = sql_tokens(source)
    if not tokens:
        raise ValueError('SQL 来源无语句')
    schema, unnamed = 'public', 0

    def add(category, table, name, position, start, kind):
        key = (category, table if category != 'index' else table[0], name)
        if key in active:
            raise ValueError(f'重复的活动对象：{table}.{name}')
        evidence = adjacent(source, comments, start) + adjacent(source, comments, position)
        active[key] = (table, point(f'{path}::{".".join(table)}::{name}', kind, name,
                                    path, source, position, evidence))

    for statement in segments(tokens, ';'):
        values = [t[0].upper() for t in statement]
        head = ' '.join(values)
        if not values or values[0] not in {'CREATE', 'ALTER', 'DROP', 'COMMENT', 'INSERT',
                                          'UPDATE', 'DELETE', 'SELECT', 'SET', 'DO', 'GRANT',
                                          'REVOKE', 'BEGIN', 'COMMIT', 'TRUNCATE'}:
            raise ValueError(f'无法识别 SQL 语句：{head[:100]}')
        if head.startswith('SET SEARCH_PATH'):
            i = 3 if values[2] in {'=', 'TO'} else 2
            schema = identifier(statement[i][0])
        elif re.match(r'CREATE (?:UNLOGGED |TEMPORARY |TEMP )?TABLE |ALTER TABLE ', head):
            unnamed += sql_table(statement, schema, active, add)
        elif re.match(r'CREATE UNIQUE INDEX ', head):
            i = 3 + int(values[3] == 'CONCURRENTLY')
            i += 3 if values[i:i + 3] == ['IF', 'NOT', 'EXISTS'] else 0
            if values[i] == 'ON':
                unnamed += 1
                continue
            index, end = relation(statement, i, schema)
            if values[end] != 'ON':
                raise ValueError('唯一索引缺少 ON')
            table, _ = relation(statement, end + 1 + int(values[end + 1] == 'ONLY'), schema)
            add('index', table, index[1], statement[i][1], statement[0][1], '数据约束')
        elif re.match(r'CREATE (?:OR REPLACE )?(?:CONSTRAINT )?TRIGGER ', head):
            i = values.index('TRIGGER') + 1
            name = identifier(statement[i][0])
            table, _ = relation(statement, values.index('ON', i) + 1, schema)
            function, _ = relation(statement, values.index('EXECUTE', i) + 2, schema)
            if values[1:3] == ['OR', 'REPLACE']:
                active.pop(('trigger', table, name), None)
            kind = '授权' if re.search('authoriz|permission|role', name + '.'.join(function), re.I) else '不变量'
            add('trigger', table, name, statement[i][1], statement[0][1], kind)
        elif head.startswith('DROP TRIGGER '):
            i = 4 if values[2:4] == ['IF', 'EXISTS'] else 2
            table, _ = relation(statement, values.index('ON', i) + 1, schema)
            active.pop(('trigger', table, identifier(statement[i][0])), None)
        elif head.startswith('DROP INDEX '):
            i = 2 + int(values[2] == 'CONCURRENTLY')
            i += 2 if values[i:i + 2] == ['IF', 'EXISTS'] else 0
            for part in segments(statement[i:]):
                index, _ = relation(part, 0, schema)
                active.pop(('index', index[0], index[1]), None)
        elif (values[0] in {'DROP', 'ALTER'} and values[1] in {'TRIGGER', 'CONSTRAINT', 'INDEX'}
              or head.startswith('CREATE ') and values[1] in {'TRIGGER', 'CONSTRAINT', 'UNIQUE'}):
            raise ValueError(f'不支持的执行点 DDL：{head[:100]}')
    return unnamed


def collect(root):
    patterns = ('backend/src/test/java/**/*.java',
                'backend/src/main/resources/db/migration/**/*.sql', 'backend/architecture/*-exceptions.txt')
    sources = [sorted(root.glob(pattern)) for pattern in patterns]
    for pattern, files in zip(patterns, sources):
        if not files:
            raise ValueError(f'缺失或空的来源：{pattern}')
    result, active, unnamed = [], {}, 0
    def migration_order(path):
        match = re.fullmatch(r'V(\d+(?:[._]\d+)*)__.+\.sql', path.name)
        if not match:
            raise ValueError(f'{path.relative_to(root)}: 无法解析迁移版本')
        return tuple(map(int, re.split('[._]', match[1]))), path.as_posix()
    sources[1].sort(key=migration_order)
    for group, files in enumerate(sources):
        for file in files:
            path = file.relative_to(root).as_posix()
            if group == 0 and 'acceptance' in file.relative_to(root / 'backend/src/test/java').parts[:-1]:
                continue
            try:
                source = file.read_text(encoding='utf-8')
                if group == 0:
                    result.extend(java_points(source, path))
                elif group == 1:
                    unnamed += scan_sql(source, path, active)
                else:
                    position, comments = 0, []
                    for line in source.splitlines(keepends=True):
                        name = line.strip()
                        if name.startswith('#'):
                            comments.append(name)
                        if name and not name.startswith('#'):
                            result.append(point(f'{file.name}::{name}', '豁免', name, path,
                                                source, position, comments, exempts=file.name))
                        if not name.startswith('#'):
                            comments = []
                        position += len(line)
            except (ValueError, IndexError, KeyError, OSError, UnicodeError) as error:
                raise ValueError(f'{path}: {error}') from error
    result.extend(item for _, item in active.values())
    result.sort(key=lambda item: item['id'])
    ids = Counter(item['id'] for item in result)
    if any(count > 1 for count in ids.values()):
        raise ValueError('id 冲突：' + ', '.join(key for key, count in ids.items() if count > 1))
    return result, unnamed


def render(points, unnamed):
    quote = lambda value: json.dumps(value, ensure_ascii=False)
    nonempty = sum(bool(item['enforces']) for item in points)
    rate = f'{nonempty}/{len(points)}（{nonempty / len(points) if points else 0:.2%}）'
    yaml = ['# 由 scan_execution_points.py 生成，勿手改', 'meta:', f'  count: {len(points)}',
            f'  enforces_nonempty: {nonempty}', f'  unnamed_constraints: {unnamed}',
            'execution_points:' if points else 'execution_points: []']
    gaps = ['# 执行点编号缺口', '', '由 scan_execution_points.py 生成，勿手改。', '',
            '仅登记源码中的执行点与编号引用，不代表守护测试已运行或业务规则已验证。', '',
            '## 统计', '', *[f'- {kind}：{sum(p["kind"] == kind for p in points)}' for kind in KINDS],
            f'- enforces 非空率：{rate}', f'- 无名约束数：{unnamed}（迁移声明累计，不入登记）', '',
            '## enforces 为空的条目（按来源文件分组）', '']
    groups = defaultdict(list)
    for item in points:
        yaml.extend(f'  - id: {quote(item["id"])}' if key == 'id' else f'    {key}: {quote(value)}'
                    for key, value in item.items())
        if not item['enforces']:
            groups[item['location'].rsplit(':', 1)[0]].append(item)
    for path, items in sorted(groups.items()):
        gaps.extend([f'### {path}（{len(items)} 条）', ''])
        gaps.extend(f'- `{p["id"]}` — {quote(p["name"])} — `{p["location"]}`' for p in items)
        gaps.append('')
    if not groups:
        gaps.extend(['无。', ''])
    return {'execution-points.yaml': '\n'.join(yaml) + '\n', 'GAPS.md': '\n'.join(gaps) + '\n'}


def main(argv=None):
    parser = argparse.ArgumentParser(description='从守护、迁移约束与例外清单生成执行点登记')
    parser.add_argument('--check', action='store_true', help='逐字节比较磁盘文件，不写入')
    args = parser.parse_args(argv)
    try:
        points, unnamed = collect(ROOT)
        different = []
        for name, content in render(points, unnamed).items():
            path, data = ROOT / OUTPUT / name, content.encode('utf-8')
            if args.check:
                if not path.is_file() or path.read_bytes() != data:
                    different.append(str(path.relative_to(ROOT)))
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
        print('；'.join(f'{kind} {sum(p["kind"] == kind for p in points)}' for kind in KINDS))
        print(f'执行点 {len(points)}；enforces 非空 {sum(bool(p["enforces"]) for p in points)}；无名约束 {unnamed}')
        for path in different:
            print(f'不一致：{path}', file=sys.stderr)
        return int(bool(different))
    except (ValueError, OSError, UnicodeError) as error:
        print(f'抽取失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
