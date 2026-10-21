"""NAM-1017-GLO-09：term_rules 的新结构适配层草案；不接入正式读取入口。"""
import copy
import unittest

import yaml

import migrate_to_concepts as migration
import term_rules


def preferred(concept, locale, role):
    names = [d for d in concept['designations']
             if (d['locale'], d['role'], d['usage_status']) == (locale, role, 'preferred')]
    if len(names) != 1 or names[0]['approval_status'] not in ('approved', 'proposed'):
        raise ValueError(f"{concept['id']}：{locale}/{role} 缺失、不唯一或不可用")
    if any(d['name'] == names[0]['name'] and
           (d['usage_status'] == 'forbidden' or d['approval_status'] == 'pending_confirmation')
           for d in concept['designations']):
        raise ValueError(f"{concept['id']}：首选指称同时禁用或待确认")
    return names[0]['name']


def entries_on_concepts(document):
    entries = []
    seen = set()
    for concept in document['concepts']:
        if concept['id'] in seen:
            raise ValueError('重复概念 id')
        seen.add(concept['id'])
        row = {k: copy.deepcopy(v) for k, v in concept.items()
               if k not in ('designations', 'relations', 'composition', 'migration', 'attributes')}
        row['zh'] = preferred(concept, 'zh-CN', 'canonical')
        row['en'] = preferred(concept, 'en', 'code_name')
        row['snake'] = migration.to_snake(row['en'])
        for relation in concept['relations']:
            if relation['rel'] == 'homonym':
                row.setdefault('homonyms', []).append(relation['target'])
            elif relation['rel'] == 'upstream_term' and 'upstream_term' not in row:
                row['upstream_term'] = relation['target']
            else:
                raise ValueError('未知或重复关系')
        entries.append((concept['migration']['source'], row))
    return entries


def check_concepts(document, bcs):
    return term_rules.check(entries_on_concepts(document), bcs)


def term(identity, **extra):
    entry = dict(id=identity, zh=identity, en='Sample' + identity.title().replace('_', ''),
                 kind='entity', bc='cost.timesheet', scope='domain', definition='检查夹具',
                 standard_ref=['ADR-0053'], aliases=[], forbidden=[], status='approved', confidence='high')
    entry.update(extra)
    entry['snake'] = migration.to_snake(entry['en'])
    return entry


class CheckersOnConceptsTest(unittest.TestCase):
    bcs = {'kernel': 'kernel', 'cost': 'subdomain', 'cost.timesheet': 'bc',
           'delivery.execution': 'bc', 'platform.integration': 'bc'}

    def document(self, *entries):
        sources = [('terms.yaml', {'terms': list(entries), 'common_words': ['id']})]
        before = copy.deepcopy(sources)
        result = migration.load_yaml(yaml.safe_dump(migration.migrate(sources, []), allow_unicode=True))
        self.assertEqual(before, sources)
        return result

    def parity(self, entries, expected):
        old = term_rules.check([('terms.yaml', e) for e in entries], self.bcs)
        new = check_concepts(self.document(*entries), self.bcs)
        self.assertEqual(old, new)
        self.assertEqual(expected, {term_rules.rule_of(k) for k in new})
        return new

    def test_n8_valid_batch_and_proposed_have_identical_empty_problems(self):
        self.parity([term('a'), term('b', status='proposed')], set())

    def test_n8_event_and_command_counterexamples_keep_problem_keys_and_messages(self):
        for kind, en, rule in [('domain_event', 'TimesheetApproval', 'event_name'),
                               ('command', 'ApprovedTimesheet', 'command_name')]:
            with self.subTest(kind=kind):
                self.parity([term('a', kind=kind, en=en)], {rule})
        for en in ['ArWrittenOff', 'DocumentSubmittedForApproval', 'ApprovalTaskBecameOverdue']:
            with self.subTest(en=en):
                self.parity([term('a', kind='domain_event', en=en)], set())

    def test_n8_generic_names_and_role_exception_are_preserved(self):
        for en in ['CreateTimesheet', 'TimesheetManager']:
            with self.subTest(en=en):
                self.parity([term('a', en=en)], {'generic_word'})
        self.parity([term('a', kind='role', en='ProjectManager')], set())

    def test_n8_scope_bc_kernel_used_by_and_acl_problems_are_preserved(self):
        cases = [({'bc': 'cost'}, {'bc'}), ({'bc': 'missing'}, {'bc'}),
                 ({'bc': 'kernel'}, {'kernel'}),
                 ({'scope': 'kernel', 'bc': 'kernel'}, {'used_by'}),
                 ({'scope': 'kernel', 'bc': 'kernel', 'used_by': ['kernel']}, {'used_by'}),
                 ({'scope': 'acl'}, {'acl_class'}), ({'acl_class': '长期'}, {'acl_class'}),
                 ({'scope': 'acl', 'bc': 'platform.integration', 'acl_class': '长期'}, set())]
        for fields, expected in cases:
            with self.subTest(fields=fields):
                self.parity([term('a', **fields)], expected)

    def test_n8_metric_definition_is_preserved(self):
        self.parity([term('a', kind='metric', formula='x')], {'metric'})
        self.parity([term('a', kind='metric', formula='x', grain='法人', as_of='业务日期')], set())

    def test_n8_deprecation_and_replacement_references_are_preserved(self):
        self.parity([term('a', status='deprecated')], {'deprecated'})
        self.parity([term('a', status='deprecated', replaced_by='b', until='切换前', owner='one-glossary'),
                     term('b')], set())

    def test_n8_upstream_and_homonym_relationships_are_read_from_relations(self):
        self.parity([term('a', upstream_term='b')], {'upstream_term'})
        self.parity([term('a', upstream_term='b'), term('b')], {'upstream_term'})
        self.parity([term('a', upstream_term='b'), term('b', bc='delivery.execution')], set())
        self.parity([term('a', homonyms=['b']), term('b', bc='delivery.execution')], {'homonyms'})

    def test_n8_same_bc_chinese_and_english_uniqueness_is_preserved(self):
        self.parity([term('a', zh='项目'), term('b', zh='项目')], {'unique_zh'})
        self.parity([term('a', en='Project'), term('b', en='Project')], {'unique_en'})

    def test_n8_cross_bc_homonyms_require_both_directions(self):
        self.parity([term('a', zh='项目', en='Project'),
                     term('b', zh='项目', en='Project', bc='delivery.execution')], {'homonym_zh', 'homonym_en'})
        self.parity([term('a', zh='项目', en='Project', homonyms=['b']),
                     term('b', zh='项目', en='Project', bc='delivery.execution', homonyms=['a'])], set())

    def test_n8_error_code_chinese_name_is_unique_globally(self):
        self.parity([term('a', zh='未登录', kind='error_code', homonyms=['b']),
                     term('b', zh='未登录', kind='error_code', bc='delivery.execution', homonyms=['a'])],
                    {'unique_zh_error_code'})

    def test_n8_combined_batch_and_baseline_decisions_are_identical(self):
        entries = [term('a', en='TimesheetApproval', kind='domain_event'),
                   term('b', kind='metric'), term('c', upstream_term='absent')]
        problems = self.parity(entries, {'event_name', 'metric', 'upstream_term'})
        baseline = {'entries': dict(problems), 'rules': {r: {'count': 1} for r in
                    ('event_name', 'metric', 'upstream_term')}}
        self.assertEqual([], term_rules.judge(problems, baseline))
        self.assertEqual(3, len(term_rules.judge({}, baseline)))
        self.assertEqual(3, len(term_rules.judge(problems, {'entries': {}, 'rules': {}})))

    def test_n8_code_name_uses_language_and_role_never_id_or_ui_label(self):
        document = self.document(term('opaque_id', en='Http2Server'))
        concept = document['concepts'][0]
        concept['designations'].append(dict(locale='en', role='ui_label', name='WrongInterfaceName',
                                           usage_status='preferred', approval_status='approved'))
        row = entries_on_concepts(document)[0][1]
        self.assertEqual('Http2Server', row['en'])
        self.assertEqual('http_server', row['snake'])

    def test_n8_name_and_relation_mutations_change_the_checker_result(self):
        document = self.document(term('a', en='Project', upstream_term='b'),
                                 term('b', bc='delivery.execution'))
        self.assertEqual({}, check_concepts(document, self.bcs))
        concept = document['concepts'][0]
        next(d for d in concept['designations'] if d['role'] == 'code_name')['name'] = 'ProjectManager'
        concept['relations'][0]['target'] = 'missing'
        self.assertEqual({'generic_word', 'upstream_term'},
                         {term_rules.rule_of(k) for k in check_concepts(document, self.bcs)})

    def test_n8_missing_duplicate_pending_and_conflicting_preferred_fail_closed(self):
        for mode in ('missing', 'duplicate', 'pending', 'conflict'):
            with self.subTest(mode=mode):
                document = self.document(term('a'))
                concept = document['concepts'][0]
                chosen = next(d for d in concept['designations'] if d['role'] == 'code_name')
                if mode == 'missing':
                    concept['designations'].remove(chosen)
                elif mode == 'duplicate':
                    concept['designations'].append(copy.deepcopy(chosen))
                elif mode == 'pending':
                    chosen['approval_status'] = 'pending_confirmation'
                else:
                    concept['designations'].append(dict(chosen, usage_status='forbidden'))
                with self.assertRaises(ValueError):
                    check_concepts(document, self.bcs)


    def test_n8_l6_kind_mutation_is_seen_by_the_original_rule(self):
        document = self.document(term('a'))
        self.assertEqual({}, check_concepts(document, self.bcs))
        document['concepts'][0]['kind'] = 'value'
        self.assertEqual({'kind'}, {term_rules.rule_of(k) for k in check_concepts(document, self.bcs)})

    def test_n8_l6_bc_and_scope_mutations_change_the_checker_result(self):
        for field, value, expected in [('bc', 'missing', {'bc'}), ('scope', 'missing', {'scope'})]:
            with self.subTest(field=field):
                document = self.document(term('a'))
                self.assertEqual({}, check_concepts(document, self.bcs))
                document['concepts'][0][field] = value
                self.assertEqual(expected, {term_rules.rule_of(k) for k in check_concepts(document, self.bcs)})

    def test_n8_l6_retained_fields_are_live_inputs_not_cached_old_results(self):
        cases = [({'bc': 'kernel', 'scope': 'kernel', 'used_by': ['cost.timesheet']}, 'used_by', ['kernel'], 'used_by'),
                 ({'scope': 'acl', 'acl_class': '长期'}, 'acl_class', '未知', 'acl_class'),
                 ({'kind': 'metric', 'formula': 'x', 'grain': '法人', 'as_of': '业务日期'}, 'formula', '', 'metric'),
                 ({}, 'status', 'deprecated', 'deprecated')]
        for fields, field, value, rule in cases:
            with self.subTest(field=field):
                document = self.document(term('a', **fields))
                self.assertEqual({}, check_concepts(document, self.bcs))
                document['concepts'][0][field] = value
                self.assertEqual({rule}, {term_rules.rule_of(k) for k in check_concepts(document, self.bcs)})

    def test_n8_l6_invalid_code_name_uses_migration_projection_validation(self):
        document = self.document(term('a'))
        chosen = next(d for d in document['concepts'][0]['designations'] if d['role'] == 'code_name')
        chosen['name'] = 'Invalid_Name'
        with self.assertRaisesRegex(ValueError, '代码名须为英文 ASCII 字母数字'):
            entries_on_concepts(document)


if __name__ == '__main__':
    unittest.main()
