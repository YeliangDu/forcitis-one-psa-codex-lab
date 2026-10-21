import contextlib
import copy
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import yaml

SPEC = importlib.util.spec_from_file_location('migrate_to_concepts', Path(__file__).with_name('migrate_to_concepts.py'))
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)

VERB_TABLE = """## 2. 受控动词初稿
### 2.2 动词表
| 索引 | 中文/语义 | 英文原形 | 过去分词 | snake | 标准动作映射 | 并入谁/保留理由 | 例子 1；例子 2 |
|---|---|---|---|---|---|---|---|
| V001 | 发布 | `Publish` | `Published` | `publish` | execute〔拟〕 | 保留 | 发布项目；发布计划 |
| V002 | 核销（坏账） | `WriteOff` | `WrittenOff` | `write_off` | execute〔拟〕 | 保留 | 核销应收；核销坏账 |
| V003 | 生效 | `TakeEffect` | `Effective` | `take_effect` | execute〔拟〕 | 测试不规则分词 | 生效项目；生效规则 |
### 2.3 多义组
"""

def term(identity='project', zh='项目', en='Project', kind='aggregate', **extra):
    return dict(id=identity, zh=zh, en=en, snake=migration.to_snake(en), kind=kind,
                bc='delivery.execution', scope='domain', definition='迁移测试词条',
                standard_ref=['ADR-0053', {'rel': 'closeMatch', 'ref': '原标准依据'}],
                aliases=[], forbidden=[], status='approved', confidence='high', **extra)

class MigrationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.terms = self.root / 'terms.yaml'
        self.proposed = self.root / 'proposed'
        self.proposed.mkdir()
        self.verbs_path = self.root / 'N4_VERBS_STATES.md'
        self.verbs_path.write_text(VERB_TABLE, encoding='utf-8')
        self.verbs = migration.read_verbs(self.verbs_path)

    def sources(self, *entries):
        return [('terms.yaml', {'meta': {'version': 'test'}, 'terms': list(entries), 'common_words': ['publish']})]

    def run_cli(self, *arguments):
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = migration.main(['--terms', str(self.terms), '--proposed-dir', str(self.proposed),
                                   '--verbs', str(self.verbs_path), *map(str, arguments)])
        return code, stdout.getvalue(), stderr.getvalue()

    def write_terms(self, entries):
        self.terms.write_text(yaml.safe_dump({'terms': entries}, allow_unicode=True), encoding='utf-8')

    def test_adr0053_roundtrip_after_yaml_serialization_keeps_all_five_fields(self):
        entries = [term(), term('publish_project', '发布项目', 'PublishProject', 'command')]
        sources = self.sources(*entries)
        before = copy.deepcopy(sources)
        document = migration.migrate(sources, self.verbs)
        loaded = migration.load_yaml(yaml.safe_dump(document, allow_unicode=True))
        self.assertEqual([], migration.roundtrip_differences(sources, loaded))
        for entry, concept in zip(entries, loaded[migration.CONCEPTS]):
            self.assertEqual({k: entry[k] for k in migration.PROJECTION_FIELDS}, migration.project_legacy(concept))
            self.assertEqual(entry['id'], concept['id'])
            self.assertEqual(entry['standard_ref'], concept['standard_ref'])
            self.assertNotIn('snake', concept)
            self.assertNotIn('code.snake', [d[migration.LOCALE] for d in concept[migration.DESIGNATIONS]])
        self.assertEqual(before, sources)
        self.assertEqual({'meta': {'version': 'test'}, 'common_words': ['publish']},
                         loaded[migration.SOURCES][0][migration.ATTRIBUTES])

    def test_adr0053_aliases_and_forbidden_preserve_order_duplicates_and_evidence(self):
        entry = term()
        entry['aliases'] = [
            {'name': '旧项目', 'type': 'dialect', 'deprecated': True, 'source': '原证据', 'decision': '原裁定'},
            {'name': '待定名', 'type': 'undetermined', 'deprecated': False},
            {'name': '项目别称', 'type': 'synonym', 'deprecated': False},
            {'name': 'OldProject', 'type': 'old-code', 'deprecated': True},
            {'name': 'OH 项目', 'type': 'abbr', 'deprecated': False},
            {'name': '禁用名', 'type': 'forbidden', 'deprecated': False},
            {'name': '旧项目', 'type': 'dialect', 'deprecated': True},
        ]
        entry['forbidden'] = ['旧项目', 'OldProject', '旧项目']
        document = migration.migrate(self.sources(entry), self.verbs)
        concept = document[migration.CONCEPTS][0]
        designations = concept[migration.DESIGNATIONS]
        self.assertEqual([migration.PREFERRED] * 2 + [migration.FORBIDDEN, migration.PERMITTED,
                         migration.PERMITTED, migration.FORBIDDEN, migration.PERMITTED,
                         migration.FORBIDDEN, migration.FORBIDDEN] + [migration.FORBIDDEN] * 3,
                         [d[migration.USAGE] for d in designations])
        self.assertEqual(migration.PENDING, designations[3][migration.APPROVAL])
        self.assertEqual('undetermined', designations[3][migration.SOURCE_TYPE])
        self.assertEqual('und', designations[6][migration.LOCALE])
        for designation in designations:
            self.assertTrue({migration.LOCALE, migration.ROLE, migration.USAGE,
                             migration.APPROVAL, migration.SOURCE_TYPE}.issubset(designation))
        self.assertEqual(entry['aliases'], migration.project_legacy(concept)['aliases'])
        self.assertEqual(entry['forbidden'], migration.project_legacy(concept)['forbidden'])

    def test_adr0053_concept_status_does_not_approve_unknown_designations(self):
        for status, expected in [('approved', 'approved'), ('proposed', 'proposed'),
                                 ('deprecated', 'proposed'), ('retired', 'proposed')]:
            with self.subTest(status=status):
                entry = term()
                entry['status'] = status
                concept = migration.migrate(self.sources(entry), self.verbs)[migration.CONCEPTS][0]
                self.assertEqual(status, concept['status'])
                self.assertEqual([expected, expected], [d[migration.APPROVAL] for d in concept[migration.DESIGNATIONS]])

    def test_adr0053_homonyms_and_upstream_become_relations_without_losing_other_fields(self):
        entry = term(homonyms=['other_project', 'third_project'], upstream_term='upstream_project',
                     note='原备注', replaced_by='replacement', until='原期限', owner='one-glossary')
        concept = migration.migrate(self.sources(entry), self.verbs)[migration.CONCEPTS][0]
        self.assertEqual([{migration.REL: 'homonym', migration.TARGET: 'other_project'},
                          {migration.REL: 'homonym', migration.TARGET: 'third_project'},
                          {migration.REL: 'upstream_term', migration.TARGET: 'upstream_project'}], concept[migration.RELATIONS])
        self.assertNotIn('homonyms', concept)
        self.assertNotIn('upstream_term', concept)
        for field in ('standard_ref', 'note', 'replaced_by', 'until', 'owner'):
            self.assertEqual(entry[field], concept[field])

    def test_n4_decomposes_commands_events_and_object_states_to_stable_ids(self):
        for kind, zh, en in [('command', '发布项目', 'PublishProject'),
                             ('domain_event', '项目已发布', 'ProjectPublished'),
                             ('state', '项目已发布', 'ProjectPublished'),
                             ('command', '核销项目', 'WriteOffProject'),
                             ('domain_event', '项目已核销', 'ProjectWrittenOff'),
                             ('domain_event', '项目已生效', 'ProjectEffective')]:
            with self.subTest(kind=kind, en=en):
                entry = term('action', zh, en, kind)
                document = migration.migrate(self.sources(term(), entry), self.verbs)
                verb = 'write_off' if '核销' in zh else 'take_effect' if '生效' in zh else 'publish'
                self.assertEqual({migration.OBJECT: 'project', migration.VERB: verb},
                                 document[migration.CONCEPTS][1][migration.COMPOSITION])
                self.assertEqual([], document[migration.MIGRATION][migration.EXCEPTIONS])

    def test_n4_unparsed_names_are_exceptions_and_are_not_renamed(self):
        for kind, zh, en in [('command', '裁定项目', 'AdjudicateProject'),
                             ('domain_event', '项目发布', 'ProjectPublished'),
                             ('state', '已发布', 'Published'), ('state', '项目状态', 'ProjectStatus'),
                             ('state', '待确认', 'PendingConfirmation')]:
            with self.subTest(kind=kind, en=en):
                entry = term('action', zh, en, kind)
                document = migration.migrate(self.sources(term(), entry), self.verbs)
                self.assertNotIn(migration.COMPOSITION, document[migration.CONCEPTS][1])
                exceptions = document[migration.MIGRATION][migration.EXCEPTIONS]
                self.assertEqual(['action'], [e['id'] for e in exceptions])
                self.assertTrue(exceptions[0][migration.REASON])
                self.assertEqual([], migration.roundtrip_differences(self.sources(term(), entry), document))

    def test_n4_missing_cross_bc_or_ambiguous_objects_are_exceptions(self):
        action = term('action', '发布项目', 'PublishProject', 'command')
        other = term()
        other['bc'] = 'delivery.acceptance'
        for nouns in ([], [other], [term(), term('other_project')]):
            with self.subTest(nouns=nouns):
                document = migration.migrate(self.sources(*nouns, action), self.verbs)
                self.assertNotIn(migration.COMPOSITION, document[migration.CONCEPTS][-1])
                self.assertEqual(1, len(document[migration.MIGRATION][migration.EXCEPTIONS]))
        kernel = term()
        kernel.update(bc='kernel', scope='kernel')
        document = migration.migrate(self.sources(kernel, action), self.verbs)
        self.assertEqual('project', document[migration.CONCEPTS][-1][migration.COMPOSITION][migration.OBJECT])

    def test_cs17_each_required_field_missing_null_or_wrong_type_fails(self):
        for field in (*migration.TEXT_FIELDS, *migration.LIST_FIELDS):
            for mode in ('missing', 'null', 'wrong_type'):
                with self.subTest(field=field, mode=mode):
                    entry = term()
                    if mode == 'missing':
                        del entry[field]
                    else:
                        entry[field] = None if mode == 'null' else ({} if field in migration.LIST_FIELDS else [])
                    with self.assertRaisesRegex(ValueError, field):
                        migration.migrate(self.sources(entry), self.verbs)

    def test_cs17_invalid_aliases_and_relations_fail(self):
        for alias in ({'type': 'dialect', 'deprecated': True}, {'name': '旧名', 'deprecated': True},
                      {'name': '旧名', 'type': 'dialect'}, {'name': '旧名', 'type': 'unknown', 'deprecated': True},
                      {'name': '旧名', 'type': 'dialect', 'deprecated': 'true'}, '旧名'):
            with self.subTest(alias=alias):
                entry = term()
                entry['aliases'] = [alias]
                with self.assertRaisesRegex(ValueError, 'aliases'):
                    migration.migrate(self.sources(entry), self.verbs)
        for key, value in [('homonyms', None), ('homonyms', [None]), ('upstream_term', '')]:
            with self.subTest(key=key):
                with self.assertRaisesRegex(ValueError, key):
                    migration.migrate(self.sources(term(**{key: value})), self.verbs)

    def test_cs17_absent_document_terms_and_duplicate_ids_fail(self):
        for data in ({}, {'terms': None}, {'terms': {}}, []):
            with self.subTest(data=data):
                with self.assertRaisesRegex(ValueError, 'terms'):
                    migration.migrate([('broken.yaml', data)], self.verbs)
        with self.assertRaisesRegex(ValueError, '重复概念 id'):
            migration.migrate(self.sources(term(), term()), self.verbs)
        with self.assertRaisesRegex(ValueError, '重复字段'):
            migration.load_yaml('terms: []\nterms: []\n')

    def test_cs17_missing_input_paths_and_terms_fail(self):
        self.write_terms([])
        with self.assertRaisesRegex(ValueError, '目录不存在'):
            migration.read_sources(self.terms, self.root / 'missing')
        self.terms.write_text('common_words: []\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'terms'):
            migration.read_sources(self.terms, self.proposed)
        self.terms.unlink()
        with self.assertRaises(FileNotFoundError):
            migration.read_sources(self.terms, self.proposed)

    def test_n4_reads_fixture_and_fails_for_missing_or_broken_verb_rows(self):
        actual = [{k: v[k] for k in ('zh', 'en', 'past', 'snake')} for v in migration.read_verbs(self.verbs_path)]
        self.assertIn(dict(zh='核销', en='WriteOff', past='WrittenOff', snake='write_off'), actual)
        for text in ('', VERB_TABLE.replace('| V001 | 发布 | `Publish` | `Published` | `publish` | execute〔拟〕 | 保留 | 发布项目；发布计划 |',
                                           '| V001 | 发布 | `Publish` | `Published` | `publish` |'),
                     VERB_TABLE.replace('Published', ''), VERB_TABLE.replace('`publish`', '`changed`')):
            with self.subTest(text=text):
                self.verbs_path.write_text(text, encoding='utf-8')
                with self.assertRaises(ValueError):
                    migration.read_verbs(self.verbs_path)

    def test_adr0053_roundtrip_reports_every_changed_field_and_missing_concept(self):
        sources = self.sources(term(), term('other', '另一个项目', 'OtherProject'))
        document = migration.migrate(sources, self.verbs)
        concept = document[migration.CONCEPTS][0]
        concept[migration.DESIGNATIONS][0][migration.NAME] = '改过的名'
        concept[migration.DESIGNATIONS][1][migration.NAME] = 'ChangedProject'
        document[migration.CONCEPTS].pop()
        differences = migration.roundtrip_differences(sources, document)
        self.assertEqual(4, len(differences))
        for field in ('project.zh', 'project.en', 'project.snake', 'other'):
            self.assertTrue(any(field in difference for difference in differences))

    def test_adr0053_roundtrip_detects_alias_and_forbidden_content_changes(self):
        entry = term()
        entry['aliases'] = [{'name': '旧名', 'type': 'dialect', 'deprecated': True, 'source': '原证据'}]
        entry['forbidden'] = ['禁用名']
        sources = self.sources(entry)
        document = migration.migrate(sources, self.verbs)
        names = document[migration.CONCEPTS][0][migration.DESIGNATIONS]
        names[2][migration.NAME] = '被改名'
        names[2][migration.MIGRATION][migration.ATTRIBUTES]['source'] = '被改证据'
        names[3][migration.NAME] = '另一个禁用名'
        differences = migration.roundtrip_differences(sources, document)
        self.assertEqual(2, len(differences))
        self.assertTrue(any('project.aliases' in difference for difference in differences))
        self.assertTrue(any('project.forbidden' in difference for difference in differences))

    def test_n6_cli_stdout_and_explicit_out_preserve_all_sources(self):
        self.write_terms([term()])
        proposed_path = self.proposed / 'entry.yaml'
        proposed_path.write_text(yaml.safe_dump({'terms': [term('action', '发布项目', 'PublishProject', 'command')],
                                               'common_words': ['publish']}), encoding='utf-8')
        snapshots = {p: p.read_bytes() for p in (self.terms, proposed_path, self.verbs_path)}
        code, stdout, stderr = self.run_cli()
        self.assertEqual(0, code, stderr)
        self.assertIn('0 差异', stderr)
        self.assertEqual(2, len(migration.load_yaml(stdout)[migration.CONCEPTS]))
        destination = self.root / 'concepts.yaml'
        code, stdout, stderr = self.run_cli('--out', destination)
        self.assertEqual(0, code, stderr)
        self.assertEqual('', stdout)
        self.assertEqual(2, len(migration.load_yaml(destination.read_text())[migration.CONCEPTS]))
        self.assertEqual(snapshots, {p: p.read_bytes() for p in snapshots})

    def test_n6_cli_difference_or_missing_field_returns_nonzero_and_never_writes(self):
        entry = term()
        entry['snake'] = 'incorrect'
        self.write_terms([entry])
        destination = self.root / 'concepts.yaml'
        code, stdout, stderr = self.run_cli('--out', destination)
        self.assertEqual(1, code)
        self.assertEqual('', stdout)
        self.assertIn('project.snake', stderr)
        self.assertFalse(destination.exists())
        del entry['zh']
        self.write_terms([entry])
        code, stdout, stderr = self.run_cli('--out', destination)
        self.assertEqual(1, code)
        self.assertIn('project.zh', stderr)
        self.assertFalse(destination.exists())

    def test_n6_cli_refuses_repository_inputs_symlinks_hardlinks_and_existing_output(self):
        self.write_terms([term()])
        symlink, hardlink = self.root / 'symlink.yaml', self.root / 'hardlink.yaml'
        symlink.symlink_to(self.terms)
        hardlink.hardlink_to(self.terms)
        existing = self.root / 'existing.yaml'
        existing.write_text('原文件', encoding='utf-8')
        snapshot = self.terms.read_bytes()
        for destination in (migration.HERE / 'not-created.yaml', self.terms, self.verbs_path, symlink, hardlink, existing):
            with self.subTest(destination=destination):
                code, stdout, stderr = self.run_cli('--out', destination)
                self.assertEqual(1, code)
                self.assertEqual('', stdout)
                self.assertIn('迁移失败', stderr)
                self.assertEqual(snapshot, self.terms.read_bytes())
        self.assertEqual('原文件', existing.read_text(encoding='utf-8'))
        self.assertFalse((migration.HERE / 'not-created.yaml').exists())

    def test_n6r_1_semantic_mutations_and_deprecated_projection_report_differences(self):
        entry = {**term(), 'aliases': [{'name': '别名', 'type': 'synonym', 'deprecated': False},
                                     {'name': '待定名', 'type': 'undetermined', 'deprecated': False}]}
        sources = self.sources(entry)
        original = migration.migrate(sources, self.verbs)
        for index, key, value in [(0, migration.USAGE, migration.PERMITTED), (1, migration.APPROVAL, migration.PENDING),
                                  (2, migration.USAGE, migration.FORBIDDEN), (3, migration.APPROVAL, 'approved'),
                                  (2, migration.LOCALE, 'en'), (2, migration.ROLE, 'code_name')]:
            with self.subTest(index=index, key=key):
                document = copy.deepcopy(original)
                document[migration.CONCEPTS][0][migration.DESIGNATIONS][index][key] = value
                self.assertTrue(migration.roundtrip_differences(sources, document))
        document = copy.deepcopy(original)
        alias = document[migration.CONCEPTS][0][migration.DESIGNATIONS][2]
        alias[migration.MIGRATION][migration.ATTRIBUTES]['deprecated'] = True
        alias[migration.USAGE] = migration.FORBIDDEN
        self.assertTrue(any('project.aliases' in d for d in migration.roundtrip_differences(sources, document)))

    def test_n6r_1_relations_retained_fields_and_source_metadata_are_compared(self):
        sources = self.sources(term(homonyms=['other', 'other'], upstream_term='upstream', note='原备注'))
        original = migration.migrate(sources, self.verbs)
        for key in ('standard_ref', 'note', 'confidence', migration.RELATIONS, migration.MIGRATION):
            document = copy.deepcopy(original)
            concept = document[migration.CONCEPTS][0]
            concept[key] = [] if key == migration.RELATIONS else {} if key == migration.MIGRATION else '被改'
            self.assertTrue(migration.roundtrip_differences(sources, document), key)
        document = copy.deepcopy(original)
        document[migration.SOURCES][0][migration.ATTRIBUTES]['meta'] = {}
        self.assertTrue(migration.roundtrip_differences(sources, document))
        self.assertEqual([], migration.roundtrip_differences(self.sources(term(homonyms=[])),
                         migration.migrate(self.sources(term(homonyms=[])), self.verbs)))

    def test_n6r_2_attributes_have_designations_roundtrip_and_fail_on_invalid_input(self):
        entry = term(attributes=[dict(id='amount', zh='数额', en='Amount', snake='amount', note='属性证据')])
        sources = self.sources(entry)
        document = migration.load_yaml(yaml.safe_dump(migration.migrate(sources, self.verbs), allow_unicode=True))
        attr = document[migration.CONCEPTS][0]['attributes'][0]
        self.assertEqual(2, len(attr[migration.DESIGNATIONS]))
        self.assertEqual(entry, migration.restore_entry(document[migration.CONCEPTS][0]))
        self.assertEqual([], migration.roundtrip_differences(sources, document))
        attr[migration.DESIGNATIONS][1][migration.NAME] = 'ChangedAmount'
        self.assertTrue(any('project.attributes' in d for d in migration.roundtrip_differences(sources, document)))
        for value in (None, {}, [None], [dict(zh='数额', en='Amount')]):
            with self.assertRaisesRegex(ValueError, 'attributes'):
                migration.migrate(self.sources(term(attributes=value)), self.verbs)

    def test_n6r_3_old_names_are_forbidden_and_contradictory_names_block_output(self):
        for kind in ('old-code', 'legacy-code', 'legacy-table'):
            entry = term()
            entry['aliases'] = [dict(name='OldProject', type=kind, deprecated=False)]
            entry['forbidden'] = ['OldProject']
            concept = migration.migrate(self.sources(entry), self.verbs)[migration.CONCEPTS][0]
            self.assertEqual(migration.FORBIDDEN, concept[migration.DESIGNATIONS][2][migration.USAGE])
            self.assertEqual(entry['aliases'], migration.project_legacy(concept)['aliases'])
        for name in ('Project', '项目'):
            entry = term()
            entry['forbidden'] = [name]
            self.write_terms([entry])
            code, stdout, stderr = self.run_cli('--out', self.root / 'blocked.yaml')
            self.assertEqual(1, code)
            self.assertEqual('', stdout)
            self.assertIn('同名指称使用状态矛盾', stderr)
            self.assertFalse((self.root / 'blocked.yaml').exists())

    def test_n6r_4_9_n4_semantic_labels_homographs_and_exception_categories(self):
        self.verbs_path.write_text(VERB_TABLE.replace('发布 |', '发布（范围；说明）；公告（另义） |'), encoding='utf-8')
        verbs = migration.read_verbs(self.verbs_path)
        self.assertEqual(['发布', '公告'], verbs[0]['labels'])
        rows = VERB_TABLE.replace('### 2.3', '| V004 | 发布（另义） | `Publish` | `Published` | `publish` | execute | 不合并 | 例一；例二 |\n### 2.3')
        self.verbs_path.write_text(rows, encoding='utf-8')
        verbs = migration.read_verbs(self.verbs_path)
        cases = [('state', '已发布', 'Published', '裸状态'), ('state', '项目状态', 'ProjectStatus', '状态集合名'),
                 ('command', '提交项目供审批', 'SubmitProjectForApproval', '带补语'),
                 ('domain_event', '项目发布', 'ProjectPublished', '中文不合模板'),
                 ('command', '发布未知对象', 'PublishUnknown', '缺名词'), ('command', '发布项目', 'PublishProject', '匹配歧义')]
        for kind, zh, en, category in cases:
            _, reason = migration.decompose(term('action', zh, en, kind), [term()], verbs)
            self.assertTrue(reason.startswith(category), reason)

    def test_n6r_5_6_7_digits_output_main_checkout_and_enumerations(self):
        for en, expected in [('Http2Server', 'http_server'), ('ISO27001', 'iso'), ('Sha256Digest', 'sha_digest'), ('B2B', 'b_b')]:
            self.assertEqual(expected, migration.to_snake(en))
        for field in migration.ENUMS:
            entry = {**term(), field: 'typo'}
            with self.assertRaisesRegex(ValueError, field):
                migration.migrate(self.sources(entry), self.verbs)
        self.write_terms([term()])
        for common in (str(self.root / 'main/.git'), '.git'):
            target = (self.root / 'main' if common != '.git' else migration.ROOT) / 'denied.yaml'
            with patch.object(migration.subprocess, 'run', return_value=migration.subprocess.CompletedProcess([], 0, stdout=common)):
                code, stdout, stderr = self.run_cli('--out', target)
            self.assertEqual(1, code)
            self.assertEqual(('', True), (stdout, '主检出目录' in stderr))
            self.assertFalse(target.exists())

if __name__ == '__main__':
    unittest.main()
