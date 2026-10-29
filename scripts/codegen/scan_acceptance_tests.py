#!/usr/bin/env python3
import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = Path('docs/architecture/ssot/acceptance')
SOURCES = ['backend/src/test/java/**/*.java (exclude **/architecture/**; '
           'outside acceptance require RULE-/INV-)',
           'frontend/**/__acceptance__/**']
RULE = re.compile(r'(?:RULE|INV)-\d+[a-z]?')
COVER = re.compile(r'(?:RULE|INV)-\d+[a-z]?|CMD-\S+')
TESTS = {'Test', 'ParameterizedTest', 'RepeatedTest', 'TestFactory', 'TestTemplate'}
LEXER = re.compile(
    r'(?P<comment>//[^\n]*|/\*.*?\*/)|'
    r'(?P<string>""".*?"""|"(?:\\.|[^"\\])*"|'
    r"'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)|"
    r'(?P<word>[^\W\d]\w*|\$[\w$]*)|(?P<punct>[^\s])', re.S)


def lex(source):
    return [(m.group(), m.start()) for m in LEXER.finditer(source)
            if m.lastgroup != 'comment']


def pairs(tokens):
    stack, result = [], {}
    closing = {')': '(', '}': '{', ']': '['}
    for i, (value, _) in enumerate(tokens):
        if value in closing.values():
            stack.append((value, i))
        elif value in closing:
            if not stack or stack[-1][0] != closing[value]:
                raise ValueError(f'括号不匹配：token {i}')
            _, start = stack.pop()
            result[start] = i
    if stack:
        raise ValueError('括号未闭合')
    return result


def literal(value):
    if value.startswith('"""'):
        value = '"' + value[3:-3].replace('"', '\\"').replace('\n', '\\n') + '"'
    if value.startswith('"'):
        value = re.sub(r'\\u+([0-9a-fA-F]{4})',
                       lambda m: chr(int(m[1], 16)), value)
        value = re.sub(r'\\([0-7]{1,3})', lambda m: chr(int(m[1], 8)), value)
        return json.loads(value.replace(r'\s', ' '))
    if value.startswith(('\'', '`')):
        if '${' in value:
            raise ValueError('动态测试名称无法静态抽取')
        return re.sub(r'\\([\\\'"`])', r'\1', value[1:-1])
    raise ValueError(f'名称必须是字符串字面量：{value}')


def display_name(arguments):
    values = [t[0] for t in arguments]
    if values[:2] == ['value', '=']:
        values = values[2:]
    if not values or any(v != '+' and not v.startswith('"') for v in values):
        raise ValueError('@DisplayName 必须是字符串字面量或其拼接')
    return ''.join(literal(v) for v in values if v != '+')


def annotation(tokens, links, i):
    i += 1
    name = tokens[i][0]
    i += 1
    while i < len(tokens) and tokens[i][0] == '.':
        name = tokens[i + 1][0]
        i += 2
    arguments = []
    if i < len(tokens) and tokens[i][0] == '(':
        end = links[i]
        arguments = tokens[i + 1:end]
        i = end + 1
    return name, arguments, i


def entry(key, display, names, path, source, position, kind='acceptance', class_covers=()):
    return {'key': key, 'display_name': display,
            'kind': kind, 'class_covers': sorted(set(class_covers)),
            'covers': sorted(set(COVER.findall(' '.join(names))) | set(class_covers)),
            'location': f'{path}:{source.count(chr(10), 0, position) + 1}'}


def declaration_comments(source, matches, start, end):
    comments = [m.group() for m in matches
                if m.lastgroup == 'comment' and start <= m.start() < end]
    for match in reversed([m for m in matches if m.end() <= start]):
        gap = source[match.end():start]
        if match.lastgroup != 'comment' or gap.strip() or gap.count('\n') > 1:
            break
        line_start = source.rfind('\n', 0, match.start()) + 1
        if source[line_start:match.start()].strip():
            break
        comments.insert(0, match.group())
        start = match.start()
    return comments


def java_tests(source, path, kind='acceptance'):
    matches = list(LEXER.finditer(source))
    tokens = [(m.group(), m.start()) for m in matches if m.lastgroup != 'comment']
    links = pairs(tokens)
    package = re.search(r'\bpackage\s+([\w. ]+)\s*;',
                        ' '.join(t[0] for t in tokens))
    if package is None:
        raise ValueError('Java 源文件缺少 package 声明')
    result = []

    def members(start, end, classes, class_covers):
        pending = {}
        i = declaration = start
        while i < end:
            value = tokens[i][0]
            if value == '@':
                name, arguments, i = annotation(tokens, links, i)
                pending[name] = arguments
                continue
            if value in {'class', 'interface', 'enum', 'record'}:
                name = tokens[i + 1][0]
                body = i + 2
                while body < end and tokens[body][0] != '{':
                    body += 1
                if body == end:
                    raise ValueError(f'类型 {name} 缺少正文')
                title = display_name(pending['DisplayName']) if 'DisplayName' in pending else ''
                comments = declaration_comments(source, matches, tokens[declaration][1], tokens[i][1])
                covers = set(class_covers) | set(COVER.findall(title))
                covers.update(cover for comment in comments if comment.startswith('/**')
                              for cover in COVER.findall(comment))
                members(body + 1, links[body], classes + [name], covers)
                pending = {}
                i = declaration = links[body] + 1
                continue
            if value == '(':
                if TESTS.intersection(pending):
                    if not classes:
                        raise ValueError('测试方法未处于类型内')
                    name, position = tokens[i - 1]
                    title = display_name(pending['DisplayName']) if 'DisplayName' in pending else ''
                    key = package[1].replace(' ', '') + '.' + '$'.join(classes) + '#' + name
                    comments = declaration_comments(source, matches, tokens[declaration][1], position)
                    allowed = TESTS if kind == 'acceptance' else {'Test', 'ParameterizedTest'}
                    if allowed.intersection(pending):
                        result.append(entry(key, title, [name, title] + comments,
                                            path, source, position, kind, class_covers))
                    pending = {}
                i = links[i] + 1
                continue
            if value == '{':
                if TESTS.intersection(pending):
                    raise ValueError('未识别的测试方法声明')
                pending = {}
                i = declaration = links[i] + 1
                continue
            if value == ';':
                pending = {}
                declaration = i + 1
            i += 1
        if TESTS.intersection(pending):
            raise ValueError('测试注解未绑定方法')

    members(0, len(tokens), [], [])
    return result


def frontend_tests(source, path):
    tokens = lex(source)
    links = pairs(tokens)
    result = []

    def calls(start, end, titles):
        i = start
        while i < end:
            kind = tokens[i][0]
            if kind not in {'describe', 'test', 'it'} or (i and tokens[i - 1][0] == '.'):
                i += 1
                continue
            opening = i + 1
            while opening + 1 < end and tokens[opening][0] == '.':
                modifier = tokens[opening + 1][0]
                opening += 2
                if modifier == 'each':
                    if tokens[opening][0] not in {'(', '['}:
                        raise ValueError('暂不支持带模板表格的 each；请使用字面量数组')
                    opening = links[opening] + 1
            if opening >= end or tokens[opening][0] != '(':
                i += 1
                continue
            closing = links[opening]
            title = literal(tokens[opening + 1][0])
            if tokens[opening + 2][0] != ',':
                raise ValueError('测试名称必须是单个字符串字面量')
            if kind == 'describe':
                calls(opening + 3, closing, titles + [title])
            else:
                key = path + ('::' + '::'.join(titles) if titles else '') + '#' + title
                result.append(entry(key, title, titles + [title], path, source, tokens[i][1]))
            i = closing + 1

    calls(0, len(tokens), [])
    return result


def collect(root):
    backend = root / 'backend/src/test/java'
    if not backend.is_dir():
        raise ValueError('缺少 backend/src/test/java')
    java = sorted(p for p in backend.rglob('*.java')
                  if 'architecture' not in p.relative_to(backend).parts[:-1]
                  and ('acceptance' in p.relative_to(backend).parts[:-1]
                       or RULE.search(p.read_text(encoding='utf-8'))))
    frontend = sorted(p for p in (root / 'frontend').rglob('*')
                      if p.is_file() and '__acceptance__' in p.parts
                      and 'node_modules' not in p.parts
                      and p.suffix in {'.js', '.jsx', '.ts', '.tsx', '.mjs', '.cjs'})
    tests = []
    for path in java + frontend:
        relative = path.relative_to(root).as_posix()
        try:
            source = path.read_text(encoding='utf-8')
            if path.suffix == '.java':
                kind = 'acceptance' if 'acceptance' in path.relative_to(backend).parts[:-1] else 'integration'
                tests.extend(java_tests(source, relative, kind))
            else:
                tests.extend(frontend_tests(source, relative))
        except (ValueError, IndexError) as error:
            raise ValueError(f'{relative}: {error}') from error
    tests.sort(key=lambda test: test['key'])
    keys = [test['key'] for test in tests]
    if len(set(keys)) != len(keys):
        raise ValueError('测试 key 重复，无法唯一定位测试方法')
    return tests, len(java), len(frontend)


def render_yaml(tests):
    quote = lambda value: json.dumps(value, ensure_ascii=False)
    lines = ['# 由 architecture/panorama/scan_acceptance_tests.py 生成，勿手改',
             'meta:', f'  count: {len(tests)}',
             f'  covers_nonempty: {sum(bool(t["covers"]) for t in tests)}',
             '  generated_from:']
    lines.extend(f'    - {quote(source)}' for source in SOURCES)
    lines.append('tests:' if tests else 'tests: []')
    for test in tests:
        lines.extend([f'  - key: {quote(test["key"])}',
                      f'    display_name: {quote(test["display_name"])}',
                      f'    kind: {quote(test["kind"])}',
                      f'    class_covers: {quote(test["class_covers"])}',
                      f'    covers: {quote(test["covers"])}',
                      f'    location: {quote(test["location"])}'])
    return '\n'.join(lines) + '\n'


def render_gaps(root, tests):
    rules_file = root / 'docs/architecture/ssot/constraint-rules/rules.yaml'
    rule_dir = root / 'docs/business/rules'
    if not rules_file.is_file() or not rule_dir.is_dir():
        raise ValueError('缺少规则来源 rules.yaml 或 rules/ 目录')
    rules = set()
    for path in [rules_file] + sorted(rule_dir.glob('*.md')):
        rules.update(RULE.findall(path.read_text(encoding='utf-8')))
    covered = {cover for test in tests for cover in test['covers']}
    missing = sorted(rules - covered)
    unnamed = [test for test in tests if not test['covers']]
    groups = defaultdict(list)
    for test in unnamed:
        groups[test['location'].rsplit(':', 1)[0]].append(test)
    lines = ['# 验收测试编号缺口', '',
             '由 architecture/panorama/scan_acceptance_tests.py 生成，勿手改。', '',
             '仅核对源码中的编号引用；不代表测试已运行或业务规则已验证。', '',
             f'## 有测试无编号（{len(unnamed)} 条）', '']
    for path, items in sorted(groups.items()):
        lines.extend([f'### {path}', ''])
        for test in items:
            lines.append(f'- `{test["key"]}` — `{test["location"]}`')
        lines.append('')
    if not unnamed:
        lines.extend(['无。', ''])
    lines.extend([f'## 有规则无测试（{len(missing)} 条）', '',
                  '来源：`docs/architecture/ssot/constraint-rules/rules.yaml`'
                  ' 与 `docs/business/rules/*.md` 中全部 RULE-/INV- 编号（含正文引用）。', ''])
    categories = [('新规则卡（RULE-316 及以上）',
                   [r for r in missing if r.startswith('RULE-') and int(re.search(r'\d+', r)[0]) >= 316]),
                  ('老行为卡（RULE-315 及以下）',
                   [r for r in missing if r.startswith('RULE-') and int(re.search(r'\d+', r)[0]) < 316]),
                  ('INV', [r for r in missing if r.startswith('INV-')])]
    for title, items in categories:
        items.sort(key=lambda r: (int(re.search(r'\d+', r)[0]), r))
        lines.extend([f'### {title}（{len(items)} 条）', '', '前 30 个编号（按数字排序）：', ''])
        lines.extend(f'- `{rule}`' for rule in items[:30])
        lines.extend([''] if items else ['无。', ''])
    return '\n'.join(lines) + '\n', len(unnamed), len(missing)


def self_test():
    source = '''package example.acceptance;
@DisplayName("RULE-1 group") class Outer {
  // @Test void fake() {}
  String decoy = "@Test void fake() {}";
  @Nested @DisplayName("INV-2a nested") class Inner {
    @org.junit.jupiter.params.ParameterizedTest(name="{0}")
    @ValueSource(strings={"{", "}"})
    @DisplayName("RULE-3 " + "CMD-run")
    void sample(String argument) { var ignored = "}"; }
    @Test void unnamed() {}
  }
  @Test void bare() {}
}
'''
    tests = java_tests(source, 'Example.java')
    assert len(tests) == 3
    assert tests[0] == {
        'key': 'example.acceptance.Outer$Inner#sample',
        'display_name': 'RULE-3 CMD-run',
        'kind': 'acceptance',
        'class_covers': ['INV-2a', 'RULE-1'],
        'covers': ['CMD-run', 'INV-2a', 'RULE-1', 'RULE-3'],
        'location': 'Example.java:9'}
    assert tests[1]['display_name'] == ''
    assert tests[1]['covers'] == ['INV-2a', 'RULE-1']
    assert tests[2]['key'] == 'example.acceptance.Outer#bare'
    assert tests[2]['covers'] == ['RULE-1']
    comments = java_tests('''package example;
/** INV-1 */ @DisplayName("RULE-316") class Example {
  // RULE-317
  /** INV-2 */
  @Test void first() { /* RULE-999 */ }
  // RULE-999

  @Test void second() {}
  @RepeatedTest(2) void excluded() {}
}''', 'Example.java', 'integration')
    assert len(comments) == 2 and comments[0]['kind'] == 'integration'
    assert comments[0]['class_covers'] == ['INV-1', 'RULE-316']
    assert comments[0]['covers'] == ['INV-1', 'INV-2', 'RULE-316', 'RULE-317']
    assert comments[1]['covers'] == ['INV-1', 'RULE-316']
    frontend = frontend_tests(
        'describe("RULE-1 suite", () => { it("INV-2 works", () => {}); '
        'test.each([1,2])("CMD-run", () => {}); });', 'example.test.ts')
    assert len(frontend) == 2
    assert frontend[0]['covers'] == ['INV-2', 'RULE-1']
    assert frontend[1]['covers'] == ['CMD-run', 'RULE-1']
    assert java_tests('package example; class Empty {}', 'Empty.java') == []
    assert frontend_tests('// it("fake", () => {});', 'empty.ts') == []
    assert 'tests: []' in render_yaml([])
    print('PASS：嵌套类、参数化、类/方法注释、空行隔离、kind、拼接、伪注解、定位、前端、空输入')
    return 0


def main():
    parser = argparse.ArgumentParser(description='从验收测试源码抽取编号引用清单')
    parser.add_argument('--check', action='store_true', help='逐字节核对已生成文件，不写入')
    parser.add_argument('--self-test', action='store_true', help='运行内置解析样例')
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    try:
        tests, java_count, frontend_count = collect(ROOT)
        gaps, unnamed, missing = render_gaps(ROOT, tests)
        outputs = {'tests.yaml': render_yaml(tests), 'gaps.md': gaps}
        different = []
        for name, content in outputs.items():
            path = ROOT / OUTPUT / name
            data = content.encode('utf-8')
            if args.check:
                if not path.is_file() or path.read_bytes() != data:
                    different.append(path.relative_to(ROOT).as_posix())
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
        nonempty = sum(bool(test['covers']) for test in tests)
        ratio = nonempty / len(tests) if tests else 0
        print(f'测试方法 {len(tests)}；covers 非空 {nonempty}（{ratio:.2%}）；'
              f'有测试无编号 {unnamed}；有规则无测试 {missing}；'
              f'Java 文件 {java_count}；前端源文件 {frontend_count}')
        for kind in ('acceptance', 'integration'):
            items = [t for t in tests if t['kind'] == kind]
            count = sum(bool(t['covers']) for t in items)
            print(f'{kind}: 方法 {len(items)}；covers 非空 {count}（{count / len(items) if items else 0:.2%}）')
        covered = {c for t in tests for c in t['covers']}
        print(f'被覆盖 RULE {sum(c.startswith("RULE-") for c in covered)}；'
              f'INV {sum(c.startswith("INV-") for c in covered)}')
        for path in different:
            print(f'不一致：{path}', file=sys.stderr)
        return int(bool(different))
    except (ValueError, OSError) as error:
        print(f'抽取失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
