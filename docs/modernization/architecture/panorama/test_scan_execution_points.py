#!/usr/bin/env python3
import contextlib
import hashlib
import io
import sys
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest.mock import patch

sys.dont_write_bytecode = True
import scan_execution_points as scan


JAVA = 'backend/src/test/java/com/forcitis/one/GuardTest.java'
MIGRATION = 'backend/src/main/resources/db/migration/example/V2__example_create.sql'
EXCEPTIONS = 'backend/architecture/example-exceptions.txt'


def write(root, path, content):
    destination = root / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(content, encoding='utf-8')


def fixture(root):
    write(root, JAVA, 'package com.forcitis.one; class GuardTest { @Test void guard() {} }')
    write(root, MIGRATION, '''CREATE TABLE example.records (
  value integer,
  -- INV-2a
  CONSTRAINT ck_records_value CHECK (value >= 0),
  CONSTRAINT uk_records_value UNIQUE (value)
);''')
    write(root, EXCEPTIONS, '# 已批准例外\n  example.Record # RULE-3 已批准  \n')


class JavaScanTest(unittest.TestCase):
    def test_test_annotations_fields_nested_names_and_locations(self):
        source = '''package com.forcitis.one;
class GuardTest {
  Class<?> type = Object.class;
  // @Test void fake() {}
  String fake = "@ArchTest static ArchRule fake;";
  // RULE-10b INV-2a RULE-10b
  @DisplayName("RULE-4 " + "INV-3")
  @org.junit.jupiter.api.Test void first() { String s = "}"; }
  @ParameterizedTest(name="{0}") void parameterized(int n) {}
  @TestFactory Object factory() { return null; }
  @Test void record() {}
  @ArchTest static ArchRule fields = rule().check("RULE-999"), second = other();
  @ArchTest static ArchRule bare, another;
  @ArchTest static ArchRule array[] = {};
  class Nested { @ArchTest void architecture(JavaClasses classes) {} }
  @RepeatedTest(2) void excluded() {}
}'''
        points = scan.java_points(source, JAVA)
        self.assertEqual([p['name'] for p in points], [
            'first', 'parameterized', 'factory', 'record', 'fields', 'second', 'bare', 'another', 'array', 'architecture'])
        self.assertEqual(points[0]['enforces'], ['INV-2a', 'INV-3', 'RULE-10b', 'RULE-4'])
        self.assertEqual(points[0]['location'], JAVA + ':8')
        self.assertEqual(points[3]['enforces'], [])
        expected = hashlib.sha256(b'com.forcitis.one.GuardTest$Nested#architecture').hexdigest()[:10]
        self.assertEqual(points[-1]['id'], 'EP-' + expected)

    def test_scope_uses_package_guard_path_or_real_archunit_import(self):
        ordinary = 'package example; class T { @Test void test() {} }'
        self.assertEqual(scan.java_points(ordinary, 'test/java/T.java'), [])
        self.assertEqual(len(scan.java_points(ordinary, 'test/java/guard/T.java')), 1)
        imported = ordinary.replace('class T', 'import com.tngtech.archunit.lang.ArchRule; class T')
        self.assertEqual(len(scan.java_points(imported, 'test/java/T.java')), 1)
        decoy = ordinary.replace('class T', '// import com.tngtech.archunit.lang.ArchRule;\nclass T')
        self.assertEqual(scan.java_points(decoy, 'test/java/T.java'), [])
        decoy = ordinary.replace('@Test', 'String s = "import com.tngtech.archunit.lang.ArchRule;"; @Test')
        self.assertEqual(scan.java_points(decoy, 'test/java/T.java'), [])

    def test_adjacent_comments_do_not_cross_blank_lines_or_method_bodies(self):
        source = '''package com.forcitis.one; class T {
  // INV-1

  @Test void first() { /* RULE-999 */ }
  // RULE-2
  @Test void second() {}
  /* INV-3 */ @Test void third() {}
}'''
        points = scan.java_points(source, JAVA)
        self.assertEqual([p['enforces'] for p in points], [[], ['RULE-2'], ['INV-3']])

    def test_java_invalid_inputs_fail(self):
        for source in ['class T {}', 'package com.forcitis.one; class T {',
                       'package com.forcitis.one; /* unfinished',
                       'package com.forcitis.one; class T { String s = "unfinished; }',
                       'package com.forcitis.one; class T { @Test int field = 0; }',
                       'package com.forcitis.one; class T { @Test }']:
            with self.subTest(source=source), self.assertRaises((ValueError, IndexError)):
                scan.java_points(source, JAVA)


class SqlScanTest(unittest.TestCase):
    def test_standard_strings_and_escape_strings_have_distinct_backslash_rules(self):
        source = r'''SELECT '\', E'escaped \' quote; CONSTRAINT fake CHECK (true)', 'it''s a quote';
CREATE TABLE example.records (x int, CONSTRAINT real CHECK (x > 0));'''
        active = {}
        self.assertEqual(scan.scan_sql(source, MIGRATION, active), 0)
        self.assertEqual([p['name'] for _, p in active.values()], ['real'])

    def test_named_unnamed_unique_index_exclusion_and_trigger_kinds(self):
        source = '''CREATE TABLE example.records (
  value integer CHECK (value > 0) UNIQUE,
  other integer CONSTRAINT "RULE-4" CHECK (other > 0),
  -- INV-7a
  CONSTRAINT ck_value CHECK (value < 100),
  CONSTRAINT uk_value UNIQUE NULLS NOT DISTINCT (value),
  CONSTRAINT ex_range EXCLUDE USING gist (value WITH =)
);
CREATE UNIQUE INDEX CONCURRENTLY uk_other ON example.records (other) WHERE other > 0;
CREATE FUNCTION example.guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$;
-- RULE-9
CREATE TRIGGER tg_immutable BEFORE UPDATE ON example.records EXECUTE FUNCTION example.guard();
CREATE CONSTRAINT TRIGGER tg_check AFTER INSERT ON example.records
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION example.check_permission();
CREATE TRIGGER tg_role BEFORE DELETE ON example.records EXECUTE PROCEDURE example.guard();
CREATE TRIGGER tg_authoriz BEFORE UPDATE ON example.records EXECUTE FUNCTION example.guard();'''
        active = {}
        self.assertEqual(scan.scan_sql(source, MIGRATION, active), 2)
        points = {p['name']: p for _, p in active.values()}
        self.assertEqual({name: p['kind'] for name, p in points.items()}, {
            'RULE-4': '不变量', 'ck_value': '不变量', 'uk_value': '数据约束', 'ex_range': '不变量',
            'uk_other': '数据约束', 'tg_immutable': '不变量', 'tg_check': '授权',
            'tg_role': '授权', 'tg_authoriz': '授权'})
        self.assertEqual(points['ck_value']['enforces'], ['INV-7a'])
        self.assertEqual(points['tg_immutable']['enforces'], ['RULE-9'])
        self.assertEqual(points['ck_value']['location'], MIGRATION + ':5')
        key = f'{MIGRATION}::example.records::ck_value'
        self.assertEqual(points['ck_value']['id'], 'EP-' + hashlib.sha256(key.encode()).hexdigest()[:10])

    def test_pseudo_keywords_comments_strings_dollar_bodies_and_quoted_identifiers(self):
        source = '''-- CREATE TRIGGER fake BEFORE INSERT ON example.records EXECUTE FUNCTION f();
/* outer /* CONSTRAINT fake CHECK (true) */ CREATE UNIQUE INDEX fake ON t(x); */
CREATE FUNCTION f() RETURNS trigger AS $$
  CREATE TRIGGER fake BEFORE INSERT ON example.records EXECUTE FUNCTION f();
  ALTER TABLE example.records DROP CONSTRAINT real;
$$ LANGUAGE plpgsql;
DO $body$ BEGIN RAISE NOTICE 'CONSTRAINT fake CHECK (true)'; END $body$;
SELECT 'CONSTRAINT fake CHECK (true);', E'escaped \\' CREATE UNIQUE INDEX fake';
CREATE TABLE "Example"."Records" (
  "CHECK" text DEFAULT 'CHECK (true); CREATE TRIGGER fake',
  value integer,
  CONSTRAINT real CHECK (value > 0)
);
COMMENT ON TABLE "Example"."Records" IS 'DROP CONSTRAINT real;';'''
        active = {}
        self.assertEqual(scan.scan_sql(source, MIGRATION, active), 0)
        self.assertEqual([p['name'] for _, p in active.values()], ['real'])
        self.assertEqual(next(iter(active.values()))[0], ('Example', 'Records'))

    def test_drop_is_scoped_and_recreation_uses_new_source(self):
        active = {}
        source = '''CREATE TABLE example.a (x int, CONSTRAINT ck CHECK (x > 0));
CREATE TABLE example.b (x int, CONSTRAINT ck CHECK (x > 0));
CREATE UNIQUE INDEX uk_a ON example.a(x);
CREATE TRIGGER tg BEFORE UPDATE ON example.a EXECUTE FUNCTION f();
CREATE TRIGGER tg BEFORE UPDATE ON example.b EXECUTE FUNCTION f();'''
        scan.scan_sql(source, MIGRATION, active)
        later = '''ALTER TABLE example.a DROP CONSTRAINT IF EXISTS ck;
DROP TRIGGER IF EXISTS tg ON example.a;
DROP INDEX CONCURRENTLY IF EXISTS example.uk_a;
ALTER TABLE example.a ADD CONSTRAINT ck CHECK (x > 1);'''
        scan.scan_sql(later, 'V10.sql', active)
        self.assertEqual(len(active), 3)
        self.assertEqual(active['constraint', ('example', 'a'), 'ck'][1]['location'], 'V10.sql:4')
        self.assertEqual(active['constraint', ('example', 'b'), 'ck'][1]['location'], MIGRATION + ':2')

    def test_column_constraints_alter_add_and_search_path(self):
        active = {}
        source = '''SET search_path TO example, public;
CREATE TABLE records (x integer CONSTRAINT ck_x CHECK (x > 0), y int);
ALTER TABLE records ADD CONSTRAINT uk_y UNIQUE (y), ADD COLUMN z integer CHECK (z > 0);
CREATE UNIQUE INDEX uk_x ON records(x);
DROP INDEX uk_x;'''
        self.assertEqual(scan.scan_sql(source, MIGRATION, active), 1)
        self.assertEqual(set(active), {('constraint', ('example', 'records'), 'ck_x'),
                                       ('constraint', ('example', 'records'), 'uk_y')})

    def test_comment_immediately_before_named_column_constraint(self):
        active = {}
        source = '''CREATE TABLE example.records (x integer
  -- INV-6a
  CONSTRAINT ck_x CHECK (x > 0));'''
        scan.scan_sql(source, MIGRATION, active)
        self.assertEqual(next(iter(active.values()))[1]['enforces'], ['INV-6a'])

    def test_invalid_sql_fails_instead_of_omitting_source(self):
        for source in ['', '-- empty', "SELECT 'unfinished;", 'DO $tag$ BEGIN END;',
                       '/* unfinished', 'CREATE TABLE example.a (x int;',
                       'CREATE TABLE example.a (x int, CONSTRAINT ck CHECK);',
                       'ALTER TABLE example.a ADD CONSTRAINT uk UNIQUE;',
                       'ALTER TABLE example.a RENAME CONSTRAINT old TO new;', 'invalid SQL;']:
            with self.subTest(source=source), self.assertRaises((ValueError, IndexError)):
                scan.scan_sql(source, MIGRATION, {})


class CollectionTest(unittest.TestCase):
    def test_empty_or_missing_source_directory_fails_with_path(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, 'backend/src/test/java'):
                scan.collect(root)
            fixture(root)
            for path, expected in [(MIGRATION, 'db/migration'), (EXCEPTIONS, 'architecture')]:
                original = (root / path).read_text()
                (root / path).unlink()
                with self.assertRaisesRegex(ValueError, expected):
                    scan.collect(root)
                write(root, path, original)

    def test_source_errors_name_the_file_and_acceptance_is_excluded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            write(root, 'backend/src/test/java/com/forcitis/one/acceptance/Bad.java', 'invalid')
            self.assertEqual(len(scan.collect(root)[0]), 4)
            for path, source in [(JAVA, 'package com.forcitis.one; class T {'),
                                 (MIGRATION, "SELECT 'unfinished;")]:
                original = (root / path).read_text()
                write(root, path, source)
                with self.assertRaisesRegex(ValueError, path):
                    scan.collect(root)
                write(root, path, original)
            (root / EXCEPTIONS).write_bytes(b'\xff')
            with self.assertRaisesRegex(ValueError, EXCEPTIONS):
                scan.collect(root)

    def test_later_migration_drops_constraint_index_and_trigger_in_version_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            with (root / MIGRATION).open('a') as output:
                output.write('CREATE UNIQUE INDEX uk_index ON example.records(value);\n'
                             'CREATE TRIGGER tg BEFORE INSERT ON example.records EXECUTE FUNCTION f();')
            write(root, MIGRATION.replace('V2__example_create', 'V10__example_drop'), '''
ALTER TABLE example.records DROP CONSTRAINT ck_records_value;
DROP INDEX example.uk_index;
DROP TRIGGER tg ON example.records;''')
            points, _ = scan.collect(root)
            self.assertEqual({p['name'] for p in points if p['kind'] != '豁免'}, {'guard', 'uk_records_value'})

    def test_ids_ignore_unrelated_lines_exception_keeps_entire_original_line(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            before, _ = scan.collect(root)
            for path in (JAVA, MIGRATION, EXCEPTIONS):
                write(root, path, '\n\n' + (root / path).read_text())
            after, _ = scan.collect(root)
            self.assertEqual([p['id'] for p in before], [p['id'] for p in after])
            self.assertTrue(all(int(b['location'].rsplit(':', 1)[1]) + 2 == int(a['location'].rsplit(':', 1)[1])
                                for b, a in zip(before, after)))
            exemption = next(p for p in before if p['kind'] == '豁免')
            self.assertEqual(exemption['name'], 'example.Record # RULE-3 已批准')
            self.assertEqual(exemption['exempts'], 'example-exceptions.txt')
            self.assertEqual(exemption['enforces'], ['RULE-3'])

    def test_id_collisions_and_duplicate_sources_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            with patch.object(scan.hashlib, 'sha256') as digest:
                digest.return_value.hexdigest.return_value = 'a' * 64
                with self.assertRaisesRegex(ValueError, 'id 冲突'):
                    scan.collect(root)
            with (root / EXCEPTIONS).open('a') as output:
                output.write('example.Record # RULE-3 已批准\n')
            with self.assertRaisesRegex(ValueError, 'id 冲突'):
                scan.collect(root)

    def test_exception_adjacent_comment_reference_is_isolated_by_blank_lines(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            write(root, EXCEPTIONS, '# INV-1\nfirst\n# RULE-2\n\nsecond\nthird\n')
            points, _ = scan.collect(root)
            self.assertEqual({p['name']: p['enforces'] for p in points if p['kind'] == '豁免'},
                             {'first': ['INV-1'], 'second': [], 'third': []})

    def test_repeated_concurrent_reads_check_drift_and_mutation_deletes_exactly_one_ep(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            with patch.object(scan, 'ROOT', root), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(scan.main([]), 0)
                before = {name: (root / scan.OUTPUT / name).read_bytes() for name in ('execution-points.yaml', 'GAPS.md')}
                self.assertEqual(scan.main([]), 0)
                self.assertEqual(scan.main(['--check']), 0)
                self.assertEqual(scan.main(['--check']), 0)
                self.assertEqual(before, {name: (root / scan.OUTPUT / name).read_bytes() for name in before})
                with ThreadPoolExecutor(max_workers=4) as pool:
                    copies = list(pool.map(lambda _: scan.render(*scan.collect(root)), range(4)))
                self.assertTrue(all(copies[0] == copy for copy in copies[1:]))
                original, _ = scan.collect(root)
                source = (root / MIGRATION).read_text()
                write(root, MIGRATION, source.replace('  CONSTRAINT ck_records_value CHECK (value >= 0),\n', ''))
                changed, _ = scan.collect(root)
                removed = {p['id'] for p in original} - {p['id'] for p in changed}
                self.assertEqual(removed, {next(p['id'] for p in original if p['name'] == 'ck_records_value')})
                self.assertEqual({p['id'] for p in changed} - {p['id'] for p in original}, set())
                errors = io.StringIO()
                with contextlib.redirect_stderr(errors):
                    self.assertEqual(scan.main(['--check']), 1)
                self.assertIn('不一致', errors.getvalue())
                self.assertEqual(before, {name: (root / scan.OUTPUT / name).read_bytes() for name in before})
                write(root, MIGRATION, source)
                self.assertEqual(scan.main(['--check']), 0)

    def test_check_missing_outputs_does_not_write_and_gaps_are_complete(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            with patch.object(scan, 'ROOT', root), contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(scan.main(['--check']), 1)
            self.assertFalse((root / scan.OUTPUT).exists())
            points, unnamed = scan.collect(root)
            rendered = scan.render(points, unnamed)
            self.assertTrue(rendered['execution-points.yaml'].startswith('# 由 scan_execution_points.py 生成，勿手改\n'))
            self.assertIn('2/4（50.00%）', rendered['GAPS.md'])
            self.assertIn('无名约束数：0', rendered['GAPS.md'])
            self.assertIn('### ' + JAVA, rendered['GAPS.md'])
            self.assertEqual([p['id'] for p in points], sorted(p['id'] for p in points))


if __name__ == '__main__':
    unittest.main(verbosity=2)
