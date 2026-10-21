#!/usr/bin/env python3
"""从静态来源生成组织类别、权限码和三处角色原文；--check 不写文件。"""
import argparse
import re
import sys
from collections import Counter
from pathlib import Path

import yaml
import scan_commands as sc

ROOT = Path(__file__).resolve().parents[4]
ARCH = Path('docs/modernization/architecture')
OUTPUT = ARCH / 'ssot/07-org-roles'
PERMISSION = Path('backend/src/main/java/com/forcitis/one/platform/identity/api/Permission.java')
ADMINISTRATOR_MODE = Path('backend/src/main/java/com/forcitis/one/platform/identity/domain/AdministratorMode.java')
MIGRATIONS = Path('backend/src/main/resources/db/migration')
STAKEHOLDERS = ARCH / 'ssot/01-stakeholders/SSOT.yaml'
TERMS = Path('docs/modernization/glossary/terms.yaml')
MATRIX = ARCH / 'proposals/closeout/RESPONSIBILITY_MATRIX.yaml'
LITERAL = r"'(?:''|[^'])*'"
CODE = r'[a-z_]+\.[a-z_]+:[a-z0-9-]+:[a-z]+'


def source(path, text, offset):
    return f'{path.as_posix()}:{text.count(chr(10), 0, offset) + 1}'


def uncomment(text):
    pattern = rf'{LITERAL}|"(?:\\.|[^"\\])*"|--[^\n]*|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(pattern, lambda m: re.sub(r'[^\n]', ' ', m[0])
                  if m[0].startswith(('--', '//', '/*')) else m[0], text)


def permissions(text, path=PERMISSION):
    clean = uncomment(text)
    enum = re.search(r'\benum\s+Permission\s*\{([^;]+);', clean)
    constants = re.search(r'\binterface\s+Codes\s*\{([^{}]*)\}', clean)
    if not enum or not constants:
        raise ValueError('Permission 枚举或 Codes 结构不符')
    pattern = re.compile(r'String\s+(\w+)\s*=\s*"([^"\\]+)"\s*;')
    codes = {}
    for match in pattern.finditer(constants[1]):
        if match[1] in codes:
            raise ValueError('Codes 常量重复')
        codes[match[1]] = match[2]
    if pattern.sub('', constants[1]).strip():
        raise ValueError('Codes 必须是字符串字面量常量')
    result, seen = [], set()
    offset = enum.start(1)
    for part in enum[1].split(','):
        match = re.fullmatch(r'\s*(\w+)\s*\(\s*Codes\.(\w+)\s*\)\s*', part)
        if not match or match[1] != match[2] or match[2] not in codes:
            raise ValueError('枚举与 Codes 常量未一一对应')
        name, code = match[1], codes[match[2]]
        if name in seen or any(row['code'] == code for row in result):
            raise ValueError('权限名或权限码重复')
        if not re.fullmatch(CODE, code):
            raise ValueError('权限码格式不符')
        module, resource, action = code.split(':')
        result.append(dict(name=name, code=code, module=module, resource=resource, action=action,
                           source=source(path, text, offset + match.start(1))))
        seen.add(name)
        offset += len(part) + 1
    if not result or seen != set(codes):
        raise ValueError('枚举与 Codes 常量未一一对应')
    return result


def administrator_mode(text, perms, path=ADMINISTRATOR_MODE):
    clean = uncomment(text)
    codes = {row['name']: row['code'] for row in perms}
    sets, origins = {}, {}
    for name in ('PERMISSIONS', 'PLATFORM_ONLY'):
        declarations = list(re.finditer(r'\b' + name + r'\s*=', clean))
        pattern = (r'public\s+static\s+final\s+Set\s*<\s*String\s*>\s+' + name
                   + r'\s*=\s*Set\.of\s*\(([^;]*)\)\s*;')
        matches = list(re.finditer(pattern, clean))
        if len(declarations) != 1 or len(matches) != 1:
            raise ValueError(f'AdministratorMode.{name} 集合解析失败')
        match = matches[0]
        values, sources = [], {}
        offset = match.start(1)
        for part in match[1].split(','):
            item = re.fullmatch(r'\s*Permission\.Codes\.(\w+)\s*', part)
            if not item:
                raise ValueError(f'AdministratorMode.{name} 仅支持 Permission.Codes 常量')
            if item[1] not in codes:
                raise ValueError(f'AdministratorMode.{name} 常量缺失：Permission.Codes.{item[1]}')
            code = codes[item[1]]
            if code in sources:
                raise ValueError(f'AdministratorMode.{name} 常量重复：{item[1]}')
            values.append(code)
            sources[code] = [source(path, text, offset + item.start(1))]
            offset += len(part) + 1
        sets[name] = sorted(values)
        origins[name] = sources
    if not set(sets['PLATFORM_ONLY']) <= set(sets['PERMISSIONS']):
        raise ValueError('AdministratorMode.PLATFORM_ONLY 不属于 PERMISSIONS')
    return {'code': 'administrator-mode', 'name': '管理员模式', 'source': 'code',
            'source_location': source(path, text, re.search(r'\bPERMISSIONS\s*=', clean).start()),
            'permissions': sets['PERMISSIONS'], 'platform_only': sets['PLATFORM_ONLY'],
            'permission_codes': list(sets['PERMISSIONS']), 'permission_sources': origins['PERMISSIONS']}


def migration_files(root, module):
    paths = sorted((root / MIGRATIONS / module).glob('V*.sql'))
    if not paths:
        raise ValueError(f'{module} 缺迁移输入')
    return [(path.relative_to(root), uncomment(path.read_text(encoding='utf-8'))) for path in paths]


def sql_statements(text):
    pattern = rf"{LITERAL}|\$\$[\s\S]*?\$\$|;"
    start = 0
    for match in re.finditer(pattern, text):
        if match[0] == ';':
            part = text[start:match.start()]
            if part.strip():
                yield start + len(part) - len(part.lstrip()), part.strip()
            start = match.end()
    if text[start:].strip():
        raise ValueError('SQL 语句缺分号')


def literal(raw):
    if re.fullmatch(LITERAL, raw):
        return raw[1:-1].replace("''", "'")
    if raw.lower() in ('true', 'false'):
        return raw.lower() == 'true'
    raise ValueError('种子字段必须是字面量')


def value_rows(body):
    pattern = re.compile(rf'\s*\(\s*({LITERAL}|true|false)(?:\s*,\s*(?:{LITERAL}|true|false))*\s*\)\s*', re.I)
    offset = 0
    while offset < len(body):
        match = pattern.match(body, offset)
        if not match:
            raise ValueError('VALUES 行结构不符，仅支持字符串与布尔字面量')
        values = [literal(m[0]) for m in re.finditer(rf'{LITERAL}|true|false', match[0], re.I)]
        yield offset + match[0].index('('), values
        offset = match.end()
        if offset < len(body):
            if body[offset] != ',':
                raise ValueError('VALUES 行之间缺逗号')
            offset += 1
            if not body[offset:].strip():
                raise ValueError('VALUES 尾部逗号')


def permission_grants(body, columns, ids):
    if re.match(r'VALUES\b', body, re.I):
        for _, values in value_rows(re.sub(r'^VALUES\s*', '', body, flags=re.I)):
            if len(values) != len(columns):
                raise ValueError('role_permissions 列数不符')
            row = dict(zip(columns, values))
            if row.get('role_id') not in ids:
                raise ValueError('role_permissions 引用了未登记角色')
            yield ids[row['role_id']], row.get('permission_code')
        return
    prefix = r'SELECT\s+uuidv7\(\),\s*roles\.role_id,\s*grants\.permission_code\s+FROM\s+platform_identity\.roles\s+roles\s+'
    patterns = (
        prefix + r'CROSS\s+JOIN\s*\(VALUES\s+(.*?)\)\s+AS\s+grants\s*\(permission_code\)\s+WHERE\s+roles\.code\s*=\s*(' + LITERAL + ')',
        prefix + r'JOIN\s*\(VALUES\s+(.*?)\)\s+AS\s+grants\s*\(role_code,\s*permission_code\)\s+ON\s+grants\.role_code\s*=\s*roles\.code',
        r'SELECT\s+uuidv7\(\),\s*role_id,\s*(' + LITERAL + r')\s+FROM\s+platform_identity\.roles\s+WHERE\s+code\s*=\s*(' + LITERAL + ')',
    )
    for index, pattern in enumerate(patterns):
        match = re.fullmatch(pattern, body, re.I | re.S)
        if not match:
            continue
        if index == 2:
            yield literal(match[2]), literal(match[1])
        else:
            for _, values in value_rows(match[1]):
                if len(values) != index + 1:
                    raise ValueError('grants VALUES 列数不符')
                yield (literal(match[2]), values[0]) if index == 0 else tuple(values)
        return
    raise ValueError('不支持的 role_permissions 种子结构')


def system_roles(files):
    roles, ids, pending = {}, {}, []
    fields = ('code', 'name', 'kind', 'status', 'access_scope', 'is_administrative')
    for path, text in files:
        for offset, statement in sql_statements(text):
            if not re.match(r'INSERT\s+INTO\s+platform_identity\.(?:roles|role_permissions)\b', statement, re.I):
                continue
            match = re.fullmatch(r'INSERT\s+INTO\s+platform_identity\.(roles|role_permissions)\s*\(([^)]+)\)\s*(.*)', statement, re.I | re.S)
            if not match:
                raise ValueError(f'{source(path, text, offset)} 种子结构不符')
            columns = [col.strip().lower() for col in match[2].split(',')]
            if len(columns) != len(set(columns)):
                raise ValueError('INSERT 列重复')
            if match[1].lower() == 'role_permissions':
                if set(columns) != {'role_permission_id', 'role_id', 'permission_code'}:
                    raise ValueError('role_permissions 列结构不符')
                pending.append((match[3], columns, source(path, text, offset)))
                continue
            if set(columns) != {'role_id', *fields} or not re.match(r'VALUES\b', match[3], re.I):
                raise ValueError('roles 必须是显式列的 VALUES 字面值')
            body = re.sub(r'^VALUES\s*', '', match[3], flags=re.I)
            body_offset = offset + match.start(3) + len(match[3]) - len(body)
            for row_offset, values in value_rows(body):
                if len(values) != len(columns):
                    raise ValueError('roles 列数不符')
                row = dict(zip(columns, values))
                for field in ('role_id', *fields[:-1]):
                    sc.string(row[field], field)
                if type(row['is_administrative']) is not bool:
                    raise ValueError('is_administrative 应为布尔值')
                if row['code'] in roles or row['role_id'] in ids:
                    raise ValueError('系统角色重复')
                ids[row['role_id']] = row['code']
                roles[row['code']] = {**{field: row[field] for field in fields}, 'permission_codes': [],
                                      'source': 'migration',
                                      'source_location': source(path, text, body_offset + row_offset), 'permission_sources': {}}
    if not roles or not pending:
        raise ValueError('缺 roles 或 role_permissions 种子')
    for body, columns, src in pending:
        for role, code in permission_grants(body, columns, ids):
            if role not in roles or not isinstance(code, str) or not re.fullmatch(CODE, code):
                raise ValueError(f'{src} 角色未登记或权限码结构不符')
            node = roles[role]
            node['permission_sources'].setdefault(code, []).append(src)
            node['permission_codes'] = sorted(node['permission_sources'])
    return list(roles.values())


def raw_roles(root):
    stakeholder = sc.read(root, STAKEHOLDERS)
    model = stakeholder.get('model')
    if not isinstance(model, dict):
        raise ValueError('干系人 SSOT 缺 model')
    parts = {key: sc.rows(model, key) for key in ('roles', 'project_roles')}
    for rows in parts.values():
        for row in rows:
            sc.string(row.get('id'), '干系人角色 id')
    glossary = []
    for row in sc.rows(sc.read(root, TERMS), 'terms'):
        if row.get('kind') == 'role':
            for field in ('id', 'zh', 'en', 'bc', 'definition'):
                sc.string(row.get(field), field)
            glossary.append({field: row[field] for field in ('id', 'zh', 'en', 'bc', 'definition')})
    matrix = sc.read(root, MATRIX)
    roles = matrix.get('roles')
    if not isinstance(roles, dict) or any(not isinstance(v, dict) for v in roles.values()):
        raise ValueError('权责表 roles 应为映射')
    counts = {key: Counter() for key in ('A', 'R', 'any')}
    for row in sc.rows(matrix, 'entries'):
        raci = row.get('raci')
        if not isinstance(raci, dict) or not isinstance(raci.get('A'), dict) or not isinstance(raci.get('R'), list):
            raise ValueError('raci.A / R 结构不符')
        accountable = raci['A'].get('role')
        if 'role' not in raci['A'] and raci['A'].get('resolve') != 'instance':
            raise ValueError('非 instance 的 raci.A 缺 role')
        sc.string(accountable, 'raci.A.role', nullable=True)
        responsible = raci['R']
        for ident in responsible:
            sc.string(ident, 'raci.R')
        a = {accountable} if accountable else set()
        r = set(responsible)
        for key, values in (('A', a), ('R', r), ('any', a | r)):
            counts[key].update(values)
    references = {ident: {key: counts[key][ident] for key in counts} for ident in sorted(counts['any'])}
    meta = {'sources': [str(STAKEHOLDERS), str(TERMS), str(MATRIX)],
            'counts': {'stakeholder_ssot': {key: len(value) for key, value in parts.items()},
                       'glossary': len(glossary), 'responsibility_matrix': len(roles)}}
    return {'meta': meta, 'stakeholder_ssot': parts, 'glossary': glossary,
            'responsibility_matrix': {'roles': roles, 'command_references': references}}


def org_units(files):
    result = []
    for path, text in files:
        for offset, statement in sql_statements(text):
            table = re.match(r'(?:CREATE|ALTER)\s+TABLE\s+organization_workforce\.(\w+)\b', statement, re.I)
            if not table or not table[1].startswith(('org_unit', 'legal_entit')):
                continue
            pattern = rf'(?:(?:CONSTRAINT\s+(\w+)\s+))?CHECK\s*\(\s*(\w+)\s+IN\s*\(([^()]*)\)\s*\)'
            found = list(re.finditer(pattern, statement, re.I))
            if len(re.findall(r'\bIN\s*\(', statement, re.I)) != len(found):
                raise ValueError('组织类别 CHECK IN 结构不符')
            for match in found:
                values = [row for _, row in value_rows('(' + match[3] + ')')][0]
                if any(not isinstance(value, str) for value in values):
                    raise ValueError('组织类别应为字符串列表')
                result.append({'table': 'organization_workforce.' + table[1], 'column': match[2],
                               'constraint': match[1], 'values': values,
                               'source': source(path, text, offset + match.start())})
    if not result:
        raise ValueError('缺组织单元或法人主体类别 CHECK')
    return result


def discrepancies(perms, roles):
    declared = {row['code'] for row in perms}
    seeded = {code for row in roles for code in row['permission_codes']}
    return sorted(seeded - declared), sorted(declared - seeded)


def dump(data):
    return '# 由 panorama/scan_org_roles.py 生成，勿手改\n' + yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=200)


def generate(root=ROOT, check=False):
    perms = permissions((root / PERMISSION).read_text(encoding='utf-8'))
    roles = system_roles(migration_files(root, 'platform_identity'))
    roles.append(administrator_mode((root / ADMINISTRATOR_MODE).read_text(encoding='utf-8'), perms))
    raw = raw_roles(root)
    units = org_units(migration_files(root, 'organization_workforce'))
    unknown, unheld = discrepancies(perms, roles)
    gaps = ['# 组织与角色抽取缺口', '', '由 panorama/scan_org_roles.py 生成，勿手改。', '',
            f'## 种子有、枚举无（{len(unknown)}）', '', *[f'- {code}' for code in unknown], '',
            f'## 枚举有、无系统角色持有（{len(unheld)}）', '', *[f'- {code}' for code in unheld], '',
            '按迁移种子与 AdministratorMode 代码定义统计，运行时由管理员配置的自定义角色不在此列。',
            '三处角色原样并列，不合并、不裁定；命令引用按每条 entries 去重，A / R 分列，any 为两者并集。',
            '组织类别含相关表的状态枚举；未具名 CHECK 的 constraint 为 null，不推断数据库自动命名。', '']
    outputs = {'permissions.yaml': dump({'meta': {'permissions': len(perms), 'system_roles': len(roles)},
                                       'permissions': perms, 'system_roles': roles}),
               'roles.raw.yaml': dump(raw), 'org-units.yaml': dump({'meta': {'count': len(units)}, 'categories': units}),
               'GAPS.md': '\n'.join(gaps)}
    different = [name for name, content in outputs.items() if not (root / OUTPUT / name).is_file()
                 or (root / OUTPUT / name).read_bytes() != content.encode('utf-8')]
    if check and different:
        raise ValueError('组织角色生成物不一致：' + '、'.join(different))
    if not check:
        (root / OUTPUT).mkdir(parents=True, exist_ok=True)
        for name in different:
            (root / OUTPUT / name).write_bytes(outputs[name].encode('utf-8'))
    print(f'权限码 {len(perms)}；系统角色 {len(roles)}；三处角色 {raw["meta"]["counts"]}；组织类别 {len(units)}；种子多出 {len(unknown)}；无角色持有 {len(unheld)}')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    try:
        generate(check=args.check)
        return 0
    except (OSError, UnicodeError, ValueError, yaml.YAMLError) as error:
        print(f'组织角色抽取失败：{error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
