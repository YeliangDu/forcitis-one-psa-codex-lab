"""ARC-1021-ARC-11：静态抽取、输入拒绝、原文保留与字节复现。"""
import copy
import io
import tempfile
import unittest
import uuid
from collections import defaultdict
from pathlib import Path
from unittest.mock import patch

import yaml

import panorama_build as p
import scan_org_roles as s

JAVA = '''public enum Permission {
    FIRST(Codes.FIRST),
    SECOND(Codes.SECOND);
    public interface Codes {
        String FIRST = "platform.identity:role:read";
        String SECOND = "platform.identity:role:update";
    }
}'''
ADMINISTRATOR = '''public final class AdministratorMode {
    public static final Set<String> PERMISSIONS = Set.of(
        Permission.Codes.FIRST,
        Permission.Codes.SECOND);
    public static final Set<String> PLATFORM_ONLY = Set.of(Permission.Codes.FIRST);
}'''
ROLE_COLUMNS = '(role_id, code, name, kind, status, access_scope, is_administrative)'
GRANT_COLUMNS = '(role_permission_id, role_id, permission_code)'


def seeds():
    first, second = str(uuid.uuid4()), str(uuid.uuid4())
    sql = f'''-- 不输出内部标识
INSERT INTO platform_identity.roles {ROLE_COLUMNS} VALUES
('{first}', 'employee', '员;工', 'SYSTEM', 'ENABLED', 'SELF', false),
('{second}', 'admin', '管理员', 'SYSTEM', 'ENABLED', 'ALL', true);
INSERT INTO platform_identity.role_permissions {GRANT_COLUMNS}
SELECT uuidv7(), roles.role_id, grants.permission_code
FROM platform_identity.roles roles
CROSS JOIN (VALUES ('platform.identity:role:read')) AS grants (permission_code)
WHERE roles.code = 'employee';
INSERT INTO platform_identity.role_permissions {GRANT_COLUMNS}
SELECT uuidv7(), roles.role_id, grants.permission_code
FROM platform_identity.roles roles
JOIN (VALUES ('admin', 'platform.identity:role:read'),
('admin', 'platform.identity:missing:read')) AS grants (role_code, permission_code)
ON grants.role_code = roles.code;
INSERT INTO platform_identity.role_permissions {GRANT_COLUMNS}
SELECT uuidv7(), role_id, 'platform.identity:role:read'
FROM platform_identity.roles WHERE code = 'admin';
'''
    return s.uncomment(sql), first, second


class PermissionTest(unittest.TestCase):
    def test_enum_codes_and_locations(self):
        rows = s.permissions(JAVA)
        self.assertEqual(['FIRST', 'SECOND'], [row['name'] for row in rows])
        self.assertEqual('platform.identity', rows[0]['module'])
        self.assertEqual('role', rows[0]['resource'])
        self.assertEqual('read', rows[0]['action'])
        self.assertTrue(rows[0]['source'].endswith(':2'))
        self.assertTrue(rows[1]['source'].endswith(':3'))

    def test_invalid_or_unmatched_constants_fail(self):
        for text in (JAVA.replace('Codes.FIRST', 'Codes.ABSENT'),
                     JAVA.replace('SECOND(Codes.SECOND)', 'SECOND(Codes.FIRST)'),
                     JAVA.replace('String SECOND', 'String FIRST'),
                     JAVA.replace('platform.identity:role:update', 'bad'),
                     JAVA.replace('"platform.identity:role:read"', 'dynamic()'),
                     JAVA.replace('SECOND(Codes.SECOND);', ';')):
            with self.subTest(text=text), self.assertRaises(ValueError):
                s.permissions(text)


class SeedTest(unittest.TestCase):
    def test_multiline_insert_select_forms_and_both_gaps(self):
        text, first, second = seeds()
        path = Path('migration.sql')
        roles = s.system_roles([(path, text)])
        self.assertEqual(['employee', 'admin'], [row['code'] for row in roles])
        self.assertEqual('员;工', roles[0]['name'])
        self.assertFalse(roles[0]['is_administrative'])
        self.assertTrue(roles[1]['is_administrative'])
        self.assertEqual(['platform.identity:role:read'], roles[0]['permission_codes'])
        self.assertEqual(2, len(roles[1]['permission_sources']['platform.identity:role:read']))
        self.assertEqual('migration', roles[0]['source'])
        self.assertEqual('migration', roles[1]['source'])
        self.assertEqual('migration.sql:3', roles[0]['source_location'])
        self.assertEqual('migration.sql:4', roles[1]['source_location'])
        self.assertNotIn(first, s.dump(roles))
        self.assertNotIn(second, s.dump(roles))
        self.assertEqual((['platform.identity:missing:read'], ['platform.identity:role:update']),
                         s.discrepancies(s.permissions(JAVA), roles))

    def test_values_grants_with_reordered_columns(self):
        text, first, _ = seeds()
        text += f'''INSERT INTO platform_identity.role_permissions
        (permission_code, role_id, role_permission_id) VALUES
        ('platform.identity:role:update', '{first}', '{uuid.uuid4()}');'''
        roles = s.system_roles([(Path('migration.sql'), text)])
        self.assertIn('platform.identity:role:update', roles[0]['permission_codes'])

    def test_unsupported_seed_and_missing_roles_fail(self):
        text, _, _ = seeds()
        for bad in (text.replace("'employee';", "'absent';"),
                    text.replace('SELECT uuidv7()', 'SELECT other_function()'),
                    text.replace("'SYSTEM'", 'dynamic()'),
                    text.replace('is_administrative)', 'is_administrative, extra)'),
                    text.replace('false)', "'false')"), ''):
            with self.subTest(), self.assertRaises(ValueError):
                s.system_roles([(Path('migration.sql'), bad)])


class AdministratorModeTest(unittest.TestCase):
    def test_constants_locations_and_gap_recalculation(self):
        perms = s.permissions(JAVA)
        role = s.administrator_mode(ADMINISTRATOR, perms, Path('AdministratorMode.java'))
        self.assertEqual('administrator-mode', role['code'])
        self.assertEqual('管理员模式', role['name'])
        self.assertEqual('code', role['source'])
        self.assertEqual('AdministratorMode.java:2', role['source_location'])
        self.assertEqual(['platform.identity:role:read', 'platform.identity:role:update'], role['permissions'])
        self.assertEqual(role['permissions'], role['permission_codes'])
        self.assertEqual(['platform.identity:role:read'], role['platform_only'])
        self.assertEqual({'platform.identity:role:read': ['AdministratorMode.java:3'],
                          'platform.identity:role:update': ['AdministratorMode.java:4']}, role['permission_sources'])
        roles = s.system_roles([(Path('migration.sql'), seeds()[0])])
        self.assertEqual((['platform.identity:missing:read'], []), s.discrepancies(perms, roles + [role]))

    def test_missing_constants_and_unparseable_sets_fail(self):
        for text in (ADMINISTRATOR.replace('Codes.SECOND', 'Codes.ABSENT'),
                     ADMINISTRATOR.replace('PLATFORM_ONLY = Set.of(Permission.Codes.FIRST)',
                                           'PLATFORM_ONLY = Set.of(Permission.Codes.ABSENT)'),
                     ADMINISTRATOR.replace('Set.of(', 'Set.copyOf('),
                     ADMINISTRATOR.replace('Permission.Codes.SECOND', 'dynamic()'),
                     ADMINISTRATOR.replace('PERMISSIONS', 'OTHER'),
                     ADMINISTRATOR.replace('Permission.Codes.SECOND', 'Permission.Codes.FIRST'),
                     ADMINISTRATOR.replace('Permission.Codes.SECOND', '/* Permission.Codes.SECOND */'),
                     ADMINISTRATOR.replace('Permission.Codes.FIRST,', '')):
            with self.subTest(text=text), self.assertRaises(ValueError):
                s.administrator_mode(text, s.permissions(JAVA))


class OrgRolesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.stakeholders = {'model': {'roles': [{'id': 'ROLE-01', 'host': '客户', 'nested': ['原文']}],
                                       'project_roles': [{'id': 'PRJ-01', 'label': '监理'}]}}
        self.terms = {'terms': [{'id': 'employee', 'zh': '员工', 'en': 'Employee', 'bc': 'platform.identity',
                                 'kind': 'role', 'definition': '原文'}, {'kind': 'entity'}]}
        self.matrix = {'roles': {'SH-E1': {'positions': ['待定'], 'org_needs': [{'responsibility': '原文'}]}},
                       'entries': [{'raci': {'A': {'role': 'SH-E1'}, 'R': ['SH-E1', 'SH-E1', 'SH-E2']}},
                                   {'raci': {'A': {'resolve': 'instance', 'field': '负责人'}, 'R': ['SH-E2']}}]}
        self.write(s.STAKEHOLDERS, self.stakeholders)
        self.write(s.TERMS, self.terms)
        self.write(s.MATRIX, self.matrix)
        self.write(s.PERMISSION, JAVA)
        self.write(s.ADMINISTRATOR_MODE, ADMINISTRATOR)
        self.write(s.MIGRATIONS / 'platform_identity/V1.sql', seeds()[0])
        self.sql = '''CREATE TABLE organization_workforce.org_units (
        status text CHECK (status IN ('ACTIVE', 'DISABLED')),
        region_kind text CONSTRAINT ck_org_units_region_kind
            CHECK (region_kind IN ('HEADQUARTERS', 'REGIONAL')));
        CREATE TABLE organization_workforce.employees (
        gender text CHECK (gender IN ('MALE', 'FEMALE')));
        INSERT INTO organization_workforce.org_units (name) VALUES ('不抽数据行');'''
        self.write(s.MIGRATIONS / 'organization_workforce/V1.sql', self.sql)

    def write(self, path, value):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(value if isinstance(value, str) else yaml.safe_dump(value, allow_unicode=True), encoding='utf-8')

    def test_raw_roles_preserve_structure_and_count_commands_once(self):
        raw = s.raw_roles(self.root)
        self.assertEqual(self.stakeholders['model'], raw['stakeholder_ssot'])
        self.assertEqual(self.matrix['roles'], raw['responsibility_matrix']['roles'])
        self.assertEqual({'A': 1, 'R': 1, 'any': 1}, raw['responsibility_matrix']['command_references']['SH-E1'])
        self.assertEqual({'A': 0, 'R': 2, 'any': 2}, raw['responsibility_matrix']['command_references']['SH-E2'])
        self.assertEqual({'stakeholder_ssot': {'roles': 1, 'project_roles': 1}, 'glossary': 1,
                          'responsibility_matrix': 1}, raw['meta']['counts'])

    def test_org_categories_names_sources_and_no_data_rows(self):
        rows = s.org_units([(Path('migration.sql'), self.sql)])
        self.assertEqual(2, len(rows))
        self.assertIsNone(rows[0]['constraint'])
        self.assertEqual('ck_org_units_region_kind', rows[1]['constraint'])
        self.assertEqual(['HEADQUARTERS', 'REGIONAL'], rows[1]['values'])
        self.assertEqual('migration.sql:3', rows[1]['source'])
        self.assertNotIn('不抽数据行', s.dump(rows))
        with self.assertRaises(ValueError):
            s.org_units([(Path('bad.sql'), self.sql.replace("'ACTIVE'", 'dynamic()'))])

    def test_rerun_bytes_check_never_writes_and_missing_input_fails(self):
        s.generate(self.root)
        before = {path.name: path.read_bytes() for path in (self.root / s.OUTPUT).iterdir()}
        s.generate(self.root)
        s.generate(self.root, check=True)
        s.generate(self.root, check=True)
        self.assertEqual(before, {path.name: path.read_bytes() for path in (self.root / s.OUTPUT).iterdir()})
        file = self.root / s.OUTPUT / 'permissions.yaml'
        file.write_text('stale', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '生成物不一致'):
            s.generate(self.root, check=True)
        self.assertEqual('stale', file.read_text())
        (self.root / s.TERMS).unlink()
        with self.assertRaises(FileNotFoundError):
            s.generate(self.root)

    def test_malformed_yaml_and_structure_fail(self):
        for value in ({'roles': []}, {'model': {'roles': {}, 'project_roles': []}}):
            self.write(s.STAKEHOLDERS, value)
            with self.assertRaises(ValueError):
                s.raw_roles(self.root)
        self.write(s.STAKEHOLDERS, self.stakeholders)
        for value in ('terms: []\nterms: []\n', {'terms': [{'kind': 'role'}]}):
            self.write(s.TERMS, value)
            with self.assertRaises(ValueError):
                s.raw_roles(self.root)
        with self.assertRaises(ValueError):
            s.migration_files(self.root, 'missing')

    def test_administrator_parse_failure_exits_without_writing(self):
        generate = s.generate
        for text in (ADMINISTRATOR.replace('Codes.SECOND', 'Codes.ABSENT'),
                     ADMINISTRATOR.replace('Set.of(', 'Set.copyOf(')):
            self.write(s.ADMINISTRATOR_MODE, text)
            with self.subTest(text=text), patch.object(s, 'generate', wraps=lambda **kw: generate(self.root, **kw)), \
                    patch('sys.stderr', new_callable=io.StringIO) as error:
                self.assertEqual(1, s.main([]))
                self.assertIn('组织角色抽取失败：AdministratorMode.', error.getvalue())
                self.assertFalse((self.root / s.OUTPUT).exists())

    def test_roles_link_system_roles_and_report_uncovered_stakeholders(self):
        s.generate(self.root)
        local = tempfile.TemporaryDirectory(dir=p.ROOT)
        self.addCleanup(local.cleanup)
        roles, raw = Path(local.name) / 'roles.yaml', Path(local.name) / 'roles.raw.yaml'
        roles.write_text(yaml.safe_dump({'roles': [
            {'id': 'ROLE-01', 'name': '甲', 'aliases': {'responsibility_matrix': ['SH-E1']}, 'system_roles': ['employee'], 'host_category': '员工'},
            {'id': 'ROLE-02', 'name': '乙', 'aliases': {}, 'system_roles': [], 'host_category': ''}]}, allow_unicode=True), encoding='utf-8')
        raw.write_text(yaml.safe_dump({'responsibility_matrix': {'command_references': {'SH-E1': {}, 'SH-E2': {}, 'system（定时）': {}, 'platform.identity': {}}}}), encoding='utf-8')
        model, links = {'mod': {'platform.identity': {}}}, []
        def link(kind, a, b, src, **evidence):
            links.append(dict(kind=kind, **{'from': a, 'to': b}, src=src, **evidence))
        with patch.dict(p.SRC, permissions=self.root / s.OUTPUT / 'permissions.yaml', roles=roles, roles_raw=raw):
            p.load_org_roles(model, link)
            self.assertEqual([('ROLE-01', 'employee')], [(e['from'], e['to']) for e in links if e['kind'] == 'role-srole'])
            gaps = defaultdict(list)
            p.check_org_roles(model, gaps)
            self.assertEqual(['SH-E2（权责表引用，未归任何 ROLE）'], gaps['N01-R1'])
            self.assertEqual(['ROLE-02'], gaps['N01-R2'])
            roles.write_text(yaml.safe_dump({'roles': [{'id': 'ROLE-01', 'system_roles': ['absent']}]}), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, '不存在的系统角色'):
                p.load_org_roles({'mod': {'platform.identity': {}}}, link)

    def test_network_links_check_only_and_existing_check_numbers_unchanged(self):
        s.generate(self.root)
        model, links = {'mod': {'platform.identity': {}}}, []
        def link(kind, a, b, src, **evidence):
            links.append(dict(kind=kind, **{'from': a, 'to': b}, src=src, **evidence))
        with patch.dict(p.SRC, permissions=self.root / s.OUTPUT / 'permissions.yaml', roles=self.root / 'absent.yaml'):
            p.load_org_roles(model, link)
            self.assertEqual(2, len(model['perm']))
            self.assertEqual(3, len(model['srole']))
            self.assertEqual(2, sum(edge['kind'] == 'perm-mod' for edge in links))
            self.assertEqual(5, sum(edge['kind'] == 'srole-perm' for edge in links))
            self.assertTrue(all(edge['src'] for edge in links))
            self.assertTrue(all(edge['to'] in model['perm'] for edge in links if edge['kind'] == 'srole-perm'))
            gaps = defaultdict(list)
            p.check_org_roles(model, gaps)
            self.assertFalse(gaps['N01-R3'])
            del model['srole']['administrator-mode']
            gaps = defaultdict(list)
            p.check_org_roles(model, gaps)
            self.assertEqual(['platform.identity:role:update'], gaps['N01-R3'])
            self.assertIn('流程', p.CHECKS['N07-1'])
            self.assertIn('流程', p.CHECKS['N07-2'])
            self.assertNotIn('N07-3', p.CHECKS)
            model['srole']['employee']['permission_codes'].append('platform.identity:role:update')
            gaps = defaultdict(list)
            p.check_org_roles(model, gaps)
            self.assertFalse(gaps['N01-R3'])
            self.assertEqual({}, model['role'])
            (self.root / s.OUTPUT / 'permissions.yaml').unlink()
            with self.assertRaises(FileNotFoundError):
                p.load_org_roles(copy.deepcopy(model), link)


if __name__ == '__main__':
    unittest.main()
