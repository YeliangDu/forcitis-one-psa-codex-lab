"""ARC-1021-ARC-09：命令合并、原文匹配、字节边界与失败路径。"""
import hashlib
from unittest.mock import patch

import pytest
import yaml

import scan_commands as s


def command(aggregate='AGG-01', name='登记对象', **extra):
    return {'aggregate': aggregate, 'name': name, 'params': ['对象引用'],
            'preconditions': ['INV-01（原文）'], 'emits': '对象已登记', **extra}


def matrix(aggregate='AGG-01', name='登记对象'):
    return {'object': aggregate, 'command': name, 'raci': {'A': {'role': 'SH-E6', 'function': '维护'}},
            'a_source': 'group:finance'}


def write(root, path, value):
    (root / path).parent.mkdir(parents=True, exist_ok=True)
    (root / path).write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding='utf-8')


@pytest.fixture
def root(tmp_path):
    for group in s.GROUPS:
        write(tmp_path, s.CLOSEOUT / group / 'commands.yaml', {'commands': []})
    write(tmp_path, s.CLOSEOUT / 'finance/commands.yaml', {'commands': [command(), command(name='仅命令表')]})
    write(tmp_path, s.CLOSEOUT / 'revenue/commands.yaml', {'commands': [command('AGG-02')]})
    write(tmp_path, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': [matrix(), matrix(name='仅权责表')]})
    write(tmp_path, s.ARCH / 'ssot/i03-aggregates/planned.yaml', {'aggregates': [
        {'id': 'AGG-01', 'BC': 'B-01 原文', '聚合（根）': '对象（OBJ-01）', '内含': 'OBJ-02、OBJ-01', '对应': 'OBJ-03'}]})
    write(tmp_path, s.ARCH / 'ssot/i03-aggregates/built.yaml', {'aggregates': [
        {'id': 'sample.module/Root', 'bc': 'sample.module', 'commands': ['RegisterObject'], 'commands_via': ['Excluded']} ]})
    write(tmp_path, s.TRANSITIONS, {'transitions': [
        {'id': 'OTR-001', 'aggregate': 'AGG-01', 'command': '登记对象', 'by_command': None}], 'pending': []})
    return tmp_path


def collect(root):
    return s.merge_commands(root, s.planned_aggregates(root))


def test_arc09_merge_single_source_actor_objects_and_cross_aggregate_names(root):
    items, counts = collect(root)
    indexed = {(item['aggregate'], item['name']): item for item in items}
    first, second = indexed['AGG-01', '登记对象'], indexed['AGG-02', '登记对象']
    assert len(items) == 4
    assert counts == {'finance': 2, 'people': 0, 'revenue': 1, 'matrix': 2}
    assert first['in'] == ['commands', 'matrix']
    assert first['actor'] == {'role': 'SH-E6', 'function': '维护'}
    assert first['owner_bc'] == 'B-01 原文'
    assert first['objects'] == ['OBJ-01', 'OBJ-02', 'OBJ-03']
    assert first['params'] == ['对象引用']
    assert first['preconditions'] == ['INV-01（原文）']
    assert first['emits'] == '对象已登记'
    assert first['group'] == ['finance']
    assert first['same_name_in'] == [second['id']]
    assert second['same_name_in'] == [first['id']]
    assert second['owner_bc'] is None and second['objects'] == []
    assert indexed['AGG-01', '仅命令表']['in'] == ['commands']
    assert indexed['AGG-01', '仅命令表']['actor'] == {'role': None, 'function': None}
    assert indexed['AGG-01', '仅权责表']['in'] == ['matrix']
    assert indexed['AGG-01', '仅权责表']['params'] is None
    assert first['source'] == [
        {'file': (s.CLOSEOUT / 'finance/commands.yaml').as_posix(), 'section': 'commands', 'index': 0},
        {'file': (s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml').as_posix(), 'section': 'entries', 'index': 0}]
    assert s.built_commands(root) == [{'class': 'RegisterObject', 'module': 'sample.module', 'aggregate': 'sample.module/Root'}]


def test_arc09_id_is_stable_when_source_order_changes(root):
    before, _ = collect(root)
    expected = 'CMD-' + hashlib.sha256('AGG-01::登记对象'.encode()).hexdigest()[:8]
    assert next(item for item in before if item['aggregate'] == 'AGG-01' and item['name'] == '登记对象')['id'] == expected
    write(root, s.CLOSEOUT / 'finance/commands.yaml', {'commands': [command(name='仅命令表'), command()]})
    after, _ = collect(root)
    assert {(item['aggregate'], item['name']): item['id'] for item in before} == {
        (item['aggregate'], item['name']): item['id'] for item in after}


def test_arc09_exact_fill_preserves_all_other_bytes_and_is_idempotent(root):
    commands, _ = collect(root)
    text = '# 文件头：保持原文\r\nmeta: {note: "by_command: null"}\r\ntransitions:\r\n'
    cases = [('AGG-01', '登记对象'), ('AGG-01', ' 登记对象'), ('AGG-01', '登记对象 '),
             ('AGG-01', '〔缺〕登记对象'), ('AGG-03', '登记对象'), ('AGG-01', None)]
    for i, (agg, name) in enumerate(cases):
        text += (f'- id: OTR-{i}\r\n  aggregate: {agg}\r\n'
                 f'  command: {yaml.safe_dump(name, allow_unicode=True, default_style=chr(34)).strip()}\r\n'
                 '  by_command: null # 行尾注释\r\n  note: 不得改变\r\n')
    text += 'pending:\r\n- {id: OTR-pending, aggregate: AGG-02, command: 登记对象, by_command: null}\r\n'
    text += 'conflicts:\r\n- {by_command: null, note: 其他段不回填}\r\n'
    changed, stats = s.fill_transitions(text, commands)
    first = s.command_id('AGG-01', '登记对象')
    second = s.command_id('AGG-02', '登记对象')
    expected = text.replace('  by_command: null #', f'  by_command: {first} #', 1)
    expected = expected.replace('command: 登记对象, by_command: null}', f'command: 登记对象, by_command: {second}}}')
    assert changed.encode() == expected.encode()
    assert stats['transitions']['filled'] == 1
    assert len(stats['transitions']['missing']) == 5
    assert stats['pending']['filled'] == 1
    assert s.fill_transitions(changed, commands) == (changed, stats)


def test_arc09_generate_check_and_repeated_generation_are_byte_identical(root):
    with pytest.raises(ValueError, match='生成物不一致'):
        s.generate(root, check=True)
    assert not (root / s.OUTPUT).exists()
    s.generate(root)
    paths = [s.OUTPUT / 'commands.yaml', s.OUTPUT / 'GAPS.md', s.TRANSITIONS]
    first = {path: (root / path).read_bytes() for path in paths}
    s.generate(root, check=True)
    s.generate(root, check=True)
    s.generate(root)
    assert first == {path: (root / path).read_bytes() for path in paths}
    gaps = first[s.OUTPUT / 'GAPS.md'].decode()
    assert '合并后：4；两处都有：1；仅 commands：2；仅 matrix：1' in gaps
    assert '无 owner_bc（1）' in gaps and '合计：1/1（100.00%）' in gaps
    (root / paths[0]).write_bytes(b'broken')
    with pytest.raises(ValueError, match='生成物不一致'):
        s.generate(root, check=True)
    assert (root / paths[0]).read_bytes() == b'broken'


@pytest.mark.parametrize('path', [*(s.CLOSEOUT / group / 'commands.yaml' for group in s.GROUPS),
                                s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', s.TRANSITIONS,
                                s.ARCH / 'ssot/i03-aggregates/planned.yaml', s.ARCH / 'ssot/i03-aggregates/built.yaml'])
def test_arc09_missing_input_fails_before_writing(root, path):
    (root / path).unlink()
    with pytest.raises(OSError):
        s.generate(root)
    assert not (root / s.OUTPUT).exists()


@pytest.mark.parametrize('value', ['', '[]', 'commands: null', 'commands: {}', 'commands: [null]',
                                   'commands: []\ncommands: []', 'commands: [{name: x}]',
                                   'commands: [{name: x, aggregate: AGG-01, params: {}, emits: []}]'])
def test_arc09_bad_command_shape_fails(root, value):
    (root / s.CLOSEOUT / 'people/commands.yaml').write_text(value)
    with pytest.raises((ValueError, yaml.YAMLError)):
        s.generate(root)


def test_arc09_hash_collision_and_conflicting_sources_fail(root):
    with patch.object(s, 'command_id', return_value='CMD-12345678'):
        with pytest.raises(ValueError, match='id 冲突'):
            collect(root)
    write(root, s.CLOSEOUT / 'people/commands.yaml', {'commands': [command(params=['冲突'])]})
    with pytest.raises(ValueError, match='冲突'):
        collect(root)


@pytest.mark.parametrize('value', [
    {}, {'entries': None}, {'entries': [{'object': 'AGG-01', 'command': '登记对象', 'raci': {'A': []}}]}])
def test_arc09_bad_matrix_shape_fails(root, value):
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', value)
    with pytest.raises(ValueError):
        collect(root)


def test_arc09_instance_actor_is_not_inferred(root):
    entry = matrix()
    entry['raci']['A'] = {'resolve': 'instance', 'field': '费用归属对象.责任人'}
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': [entry]})
    items, _ = collect(root)
    assert all(item['actor'] == {'role': None, 'function': None} for item in items)


@pytest.mark.parametrize('change', [{'by_command': []}, {'command': []}, {'aggregate': None}, {'id': None}])
def test_arc09_bad_transition_shape_fails(root, change):
    row = {'id': 'OTR-001', 'aggregate': 'AGG-01', 'command': '登记对象', 'by_command': None, **change}
    text = yaml.safe_dump({'transitions': [row], 'pending': []}, allow_unicode=True)
    with pytest.raises(ValueError):
        s.fill_transitions(text, collect(root)[0])


def test_arc09_cli_failure_is_nonzero(root, capsys):
    with patch.object(s, 'ROOT', root), patch.object(s, 'generate', side_effect=ValueError('结构不符')):
        assert s.main(['--check']) == 1
    assert '命令登记失败：结构不符' in capsys.readouterr().err


@pytest.mark.parametrize('suffix', ['', ' # 注释'])
def test_arc09_implicit_null_remains_valid_yaml(root, suffix):
    text = ('transitions:\n- id: OTR-1\n  aggregate: AGG-01\n  command: 登记对象\n'
            f'  by_command:{suffix}\npending: []\n')
    changed, _ = s.fill_transitions(text, collect(root)[0])
    assert yaml.safe_load(changed)['transitions'][0]['by_command'] == s.command_id('AGG-01', '登记对象')
    assert changed.endswith(f'{suffix}\npending: []\n')


def test_arc09_transition_alias_cannot_rewrite_another_field(root):
    text = ('meta: &empty null\ntransitions:\n- id: OTR-1\n  aggregate: AGG-01\n'
            '  command: 登记对象\n  by_command: *empty\npending: []\n')
    with pytest.raises(ValueError, match='别名'):
        s.fill_transitions(text, collect(root)[0])


@pytest.mark.parametrize('path, value', [
    (s.ARCH / 'ssot/i03-aggregates/planned.yaml', {'aggregates': [{}]}),
    (s.ARCH / 'ssot/i03-aggregates/built.yaml', {'aggregates': [{'id': 'Root', 'bc': 'module', 'commands': None}]}),
    (s.TRANSITIONS, {'transitions': [], 'pending': None})])
def test_arc09_other_bad_shapes_fail_before_writing(root, path, value):
    write(root, path, value)
    with pytest.raises(ValueError):
        s.generate(root)
    assert not (root / s.OUTPUT).exists()


def test_arc09_consistent_duplicate_sources_merge_with_full_provenance(root):
    write(root, s.CLOSEOUT / 'people/commands.yaml', {'commands': [command()]})
    commands, counts = collect(root)
    item = next(row for row in commands if row['aggregate'] == 'AGG-01' and row['name'] == '登记对象')
    assert len(commands) == 4
    assert counts['people'] == 1
    assert item['group'] == ['finance', 'people']
    assert len(item['source']) == 3


def rename_transition(root, name):
    write(root, s.TRANSITIONS, {'transitions': [
        {'id': 'OTR-001', 'aggregate': 'AGG-01', 'command': name, 'by_command': None}], 'pending': []})


def registry(root):
    return s.read(root, s.OUTPUT / 'command-ids.yaml')['ids']


def test_arc04_seed_preserves_every_existing_id_and_checks_registry_bytes(root):
    s.generate(root)
    seed = s.read(root, s.OUTPUT / 'commands.yaml')['commands']
    custom = 'CMD-00000001'
    seed[0]['id'] = custom
    write(root, s.OUTPUT / 'commands.yaml', {'commands': seed})
    (root / s.OUTPUT / 'command-ids.yaml').unlink()
    with pytest.raises(ValueError, match='缺失.*--seed'):
        s.generate(root)
    s.generate(root, seed=True)
    expected = {(row['aggregate'], row['name']): row['id'] for row in seed}
    assert {(row['aggregate'], row['name']): row['id'] for row in registry(root)} == expected
    assert all(not row['retired'] and row['former_names'] == [] for row in registry(root))
    paths = [s.OUTPUT / name for name in ('commands.yaml', 'command-ids.yaml', 'GAPS.md')]
    before = {path: (root / path).read_bytes() for path in paths}
    s.generate(root, check=True)
    s.generate(root)
    assert before == {path: (root / path).read_bytes() for path in paths}
    path = s.OUTPUT / 'command-ids.yaml'
    (root / path).write_bytes(before[path] + b'\n')
    with pytest.raises(ValueError, match='生成物不一致.*command-ids.yaml'):
        s.generate(root, check=True)
    assert (root / path).read_bytes() == before[path] + b'\n'


@pytest.mark.parametrize('group', s.GROUPS)
def test_arc04_declared_rename_keeps_id_for_commands_and_matrix(root, group):
    s.generate(root)
    ident = s.command_id('AGG-01', '登记对象')
    for source in s.GROUPS:
        write(root, s.CLOSEOUT / source / 'commands.yaml', {'commands': []})
    write(root, s.CLOSEOUT / group / 'commands.yaml', {'commands': [
        command(name='注册对象', former_names=['登记对象'])]})
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': [matrix(name='注册对象')]})
    with pytest.raises(ValueError, match='OTR-001.*仍用旧名「登记对象」.*注册对象'):
        s.generate(root)
    rename_transition(root, '注册对象')
    s.generate(root)
    commands = s.read(root, s.OUTPUT / 'commands.yaml')['commands']
    assert len(commands) == 1 and commands[0]['id'] == ident
    assert s.read(root, s.TRANSITIONS)['transitions'][0]['by_command'] == ident
    assert commands[0]['in'] == ['commands', 'matrix']
    row = next(row for row in registry(root) if row['id'] == ident)
    assert row == dict(id=ident, aggregate='AGG-01', name='注册对象', former_names=['登记对象'], retired=False)
    s.generate(root, check=True)
    s.generate(root)
    assert row == next(row for row in registry(root) if row['id'] == ident)


def test_arc04_matrix_only_rename_and_retired_alias_reactivation(root):
    s.generate(root)
    old = s.command_id('AGG-01', '仅权责表')
    entry = {**matrix(name='权责操作'), 'former_names': ['仅权责表']}
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': [entry]})
    s.generate(root)
    assert next(row for row in registry(root) if row['id'] == old)['name'] == '权责操作'
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': []})
    s.generate(root)
    assert next(row for row in registry(root) if row['id'] == old)['retired']
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': [matrix(name='仅权责表')]})
    s.generate(root)
    row = next(row for row in registry(root) if row['id'] == old)
    assert not row['retired'] and row['name'] == '仅权责表'
    assert row['former_names'] == ['权责操作']


def test_arc04_undeclared_rename_allocates_new_id_and_retires_old(root):
    s.generate(root)
    old = s.command_id('AGG-01', '登记对象')
    write(root, s.CLOSEOUT / 'finance/commands.yaml', {'commands': [command(name='注册对象')]})
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': []})
    s.generate(root)
    assert next(row for row in registry(root) if row['id'] == old)['retired']
    new = next(row for row in registry(root) if row['name'] == '注册对象')
    assert new['id'] == s.command_id('AGG-01', '注册对象') and new['id'] != old
    write(root, s.CLOSEOUT / 'finance/commands.yaml', {'commands': [command()]})
    s.generate(root)
    assert not next(row for row in registry(root) if row['id'] == old)['retired']


@pytest.mark.parametrize('retired', [False, True])
def test_arc04_hash_collision_including_retired_ids_fails_without_writes(root, retired):
    s.generate(root)
    rows = registry(root)
    rows[0]['retired'] = retired
    write(root, s.OUTPUT / 'command-ids.yaml', {'ids': rows})
    before = (root / s.OUTPUT / 'command-ids.yaml').read_bytes()
    write(root, s.CLOSEOUT / 'people/commands.yaml', {'commands': [command(name='新命令')]})
    with patch.object(s, 'command_id', return_value=rows[0]['id']):
        with pytest.raises(ValueError, match='id 冲突'):
            s.generate(root)
    assert (root / s.OUTPUT / 'command-ids.yaml').read_bytes() == before


@pytest.mark.parametrize('names', [None, '', {}, [], [None], [42], [''], ['  ']])
def test_arc04_invalid_former_names_fail(root, names):
    write(root, s.CLOSEOUT / 'people/commands.yaml', {'commands': [command(former_names=names)]})
    with pytest.raises(ValueError, match='former_names'):
        s.generate(root)


def test_arc04_duplicate_old_name_claim_and_cross_aggregate_fail(root):
    s.generate(root)
    write(root, s.CLOSEOUT / 'people/commands.yaml', {'commands': [
        command(name='注册对象', former_names=['登记对象']),
        command(name='创建对象', former_names=['登记对象'])]})
    with pytest.raises(ValueError, match='旧名重复声明'):
        s.generate(root)
    write(root, s.CLOSEOUT / 'people/commands.yaml', {'commands': [
        command('AGG-03', '注册对象', former_names=['登记对象'])]})
    with pytest.raises(ValueError, match='旧名跨聚合'):
        s.generate(root)


def test_arc04_alias_claim_in_later_group_applies_to_earlier_group(root):
    s.generate(root)
    renamed = command(name='注册对象')
    write(root, s.CLOSEOUT / 'finance/commands.yaml', {'commands': [renamed]})
    write(root, s.CLOSEOUT / 'revenue/commands.yaml', {'commands': [
        {**renamed, 'former_names': ['登记对象']}]})
    write(root, s.CLOSEOUT / 'RESPONSIBILITY_MATRIX.yaml', {'entries': [matrix(name='注册对象')]})
    rename_transition(root, '注册对象')
    s.generate(root)
    commands = s.read(root, s.OUTPUT / 'commands.yaml')['commands']
    assert len(commands) == 1
    assert commands[0]['id'] == s.command_id('AGG-01', '登记对象')
    assert commands[0]['group'] == ['finance', 'revenue']
    s.generate(root, check=True)


@pytest.mark.parametrize('changes, message', [
    ({'id': 'bad'}, '无效的命令 id'), ({'retired': 'false'}, 'retired'),
    ({'former_names': None}, 'former_names'), ({'former_names': ['登记对象']}, '登记名称重复')])
def test_arc04_invalid_registry_fails_before_writing(root, changes, message):
    s.generate(root)
    values = registry(root)
    row = next(row for row in values if row['aggregate'] == 'AGG-01' and row['name'] == '登记对象')
    row.update(changes)
    write(root, s.OUTPUT / 'command-ids.yaml', {'ids': values})
    before = (root / s.OUTPUT / 'commands.yaml').read_bytes()
    with pytest.raises(ValueError, match=message):
        s.generate(root)
    assert (root / s.OUTPUT / 'commands.yaml').read_bytes() == before


def test_arc04_duplicate_claim_across_aggregates_is_rejected(root):
    write(root, s.CLOSEOUT / 'finance/commands.yaml', {'commands': [
        command(name='注册对象', former_names=['旧操作'])]})
    write(root, s.CLOSEOUT / 'people/commands.yaml', {'commands': [
        command('AGG-02', '创建对象', former_names=['旧操作'])]})
    with pytest.raises(ValueError, match='旧名重复声明'):
        s.generate(root)


def test_arc04_unregistered_former_name_is_rejected(root):
    s.generate(root)
    write(root, s.CLOSEOUT / 'finance/commands.yaml', {'commands': [
        command(name='注册对象', former_names=['登记对像'])]})
    with pytest.raises(ValueError, match='旧名未登记'):
        s.generate(root)
