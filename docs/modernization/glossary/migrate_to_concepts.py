#!/usr/bin/env python3
"""ADR-0053 / N6：只读抽取概念草稿，完整往返与语义校验通过后才输出。"""
import argparse
import copy
from pathlib import Path
import re
import subprocess
import sys

import yaml

CONCEPTS, DESIGNATIONS, RELATIONS, COMPOSITION = "concepts", "designations", "relations", "composition"
LOCALE, NAME, ROLE = "locale", "name", "role"
USAGE, APPROVAL, SOURCE_TYPE = "usage_status", "approval_status", "source_type"
REL, TARGET, OBJECT, VERB = "rel", "target", "object", "verb"
MIGRATION, SOURCE, FIELD, INDEX, ATTRIBUTES = "migration", "source", "field", "index", "attributes"
SOURCES, EXCEPTIONS, REASON = "sources", "exceptions", "reason"
ZH_LOCALE, EN_LOCALE, UNKNOWN_LOCALE = "zh-CN", "en", "und"
ZH_ROLE, CODE_ROLE = "canonical", "code_name"
PREFERRED, PERMITTED, FORBIDDEN = "preferred", "permitted", "forbidden"
PENDING = "pending_confirmation"
APPROVAL_BY_STATUS = {"approved": "approved", "proposed": "proposed", "deprecated": "proposed", "retired": "proposed"}
PROJECTION_FIELDS = ("zh", "en", "snake", "aliases", "forbidden")
TEXT_FIELDS = ("id", "zh", "en", "snake", "kind", "bc", "scope", "definition", "status", "confidence")
LIST_FIELDS = ("standard_ref", "aliases", "forbidden")
MOVED_FIELDS = (*PROJECTION_FIELDS, "homonyms", "upstream_term")
RESERVED_FIELDS = (DESIGNATIONS, RELATIONS, COMPOSITION, MIGRATION)
ALIAS_TYPES = ("dialect", "undetermined", "old-code", "legacy-code", "legacy-table", "kpi-symbol",
               "abbr", "industry", "standard-zh", "standard-en", "synonym", "forbidden")
INTRINSIC_FORBIDDEN = ("dialect", "forbidden", "old-code", "legacy-code", "legacy-table")
VERB_HEADER = ("索引", "中文/语义", "英文原形", "过去分词", "snake", "标准动作映射", "并入谁/保留理由", "例子 1；例子 2")
NOUN_KINDS = ("aggregate", "entity", "value_object", "read_model", "business_rule", "metric", "role")
COMPOSED_KINDS = ("command", "domain_event", "state")
ENUMS = {'kind': (*NOUN_KINDS, *COMPOSED_KINDS, 'error_code'), 'scope': ('domain', 'kernel', 'acl'),
         'confidence': ('high', 'medium', 'low'), 'status': tuple(APPROVAL_BY_STATUS)}
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
DEFAULT_TERMS = HERE / "terms.yaml"
DEFAULT_PROPOSED = ROOT / "backend/glossary/proposed"
DEFAULT_VERBS = ROOT / "docs/modernization/glossary/arch-naming/N4_VERBS_STATES.md"


def unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=True)
        if key in result:
            raise ValueError(f"YAML 重复字段 {key!r}（第 {key_node.start_mark.line + 1} 行）")
        result[key] = loader.construct_object(value_node, deep=True)
    return result


def load_yaml(text):
    class Loader(yaml.SafeLoader):
        pass
    Loader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)
    return yaml.load(text, Loader=Loader)


def require_text(value, where):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{where}：缺失或无效的非空字符串")


def to_snake(name):
    require_text(name, "en")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", name):
        raise ValueError(f"en={name!r}：代码名须为英文 ASCII 字母数字")
    name = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name)
    name = re.sub(r"([A-Z])([A-Z][a-z])", r"\1_\2", name)
    return '_'.join(w.lower() for w in re.split(r"[^A-Za-z0-9]+|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])", name) if w and not w.isdigit())


def validate_entry(entry, source):
    if not isinstance(entry, dict):
        raise ValueError(f"{source}：词条须为映射")
    where = f"{source}#{entry.get('id', '<缺 id>')}"
    for field in TEXT_FIELDS:
        require_text(entry.get(field), f"{where}.{field}")
    for field in LIST_FIELDS:
        if not isinstance(entry.get(field), list):
            raise ValueError(f"{where}.{field}：缺失或无效的列表，空列表须显式写 []")
    for field, values in ENUMS.items():
        if entry.get(field) not in values:
            raise ValueError(f"{where}.{field}：未知值 {entry.get(field)!r}，须取 {values}")
    for field in RESERVED_FIELDS:
        if field in entry:
            raise ValueError(f"{where}：输入已有目标字段 {field}")
    for field in ("forbidden", "homonyms"):
        if field in entry:
            if not isinstance(entry[field], list):
                raise ValueError(f"{where}.{field}：须为列表")
            for value in entry[field]:
                require_text(value, f"{where}.{field}")
    if "upstream_term" in entry:
        require_text(entry['upstream_term'], f"{where}.upstream_term")
    for alias in entry['aliases']:
        if not isinstance(alias, dict):
            raise ValueError(f"{where}.aliases：每项须为映射")
        require_text(alias.get('name'), f"{where}.aliases.name")
        if alias.get('type') not in ALIAS_TYPES or type(alias.get('deprecated')) is not bool:
            raise ValueError(f"{where}.aliases：type 缺失或未知，或 deprecated 缺失或不是布尔值")
    if 'attributes' in entry:
        if not isinstance(entry['attributes'], list):
            raise ValueError(f"{where}.attributes：须为列表")
        for index, attr in enumerate(entry['attributes']):
            if not isinstance(attr, dict) or any(k in attr for k in RESERVED_FIELDS):
                raise ValueError(f"{where}.attributes[{index}]：须为无目标字段的映射")
            for field in ('zh', 'en', 'snake'):
                require_text(attr.get(field), f"{where}.attributes[{index}].{field}")


def read_sources(terms, proposed):
    if not proposed.is_dir():
        raise ValueError(f"提议目录不存在：{proposed}")
    sources = []
    for path in (terms, *sorted(proposed.glob('*.yaml'))):
        document = load_yaml(path.read_text(encoding='utf-8'))
        if not isinstance(document, dict) or not isinstance(document.get('terms'), list):
            raise ValueError(f"{path}：缺失或无效的 terms 列表")
        label = str(path.resolve().relative_to(ROOT)) if path.resolve().is_relative_to(ROOT) else str(path)
        sources.append((label, document))
    return sources


def read_verbs(path):
    text = path.read_text(encoding='utf-8')
    section = re.search(r"^### 2\.2 .*?\n(.*?)(?=^### |^## |\Z)", text, re.M | re.S)
    if not section:
        raise ValueError(f"{path}：缺少 N4 第 2 节的 2.2 动词表")
    rows = [tuple(cell.strip() for cell in line.strip().strip('|').split('|'))
            for line in section[1].splitlines() if line.lstrip().startswith('|')]
    if (len(rows) < 3 or rows[0] != VERB_HEADER or len(rows[1]) != len(VERB_HEADER)
            or any(not re.fullmatch(r":?-+:?", c) for c in rows[1])):
        raise ValueError(f"{path}：N4 动词表表头或分隔行无效")
    verbs = []
    for row in rows[2:]:
        if len(row) != len(VERB_HEADER) or any(not c for c in row):
            raise ValueError(f"{path}：N4 动词表缺字段：{row!r}")
        identity, semantic, en, past, snake, _, _, _ = row
        en, past, snake = (c.strip('`') for c in (en, past, snake))
        labels = [label.strip() for label in re.sub(r"（[^（）]*）", '', semantic).split('；')]
        if not re.fullmatch(r"V[0-9]{3}", identity) or any(not re.fullmatch(r"[\u3400-\u9fff]+", zh) for zh in labels):
            raise ValueError(f"{path}：N4 索引或中文/语义格式无效：{row!r}")
        if to_snake(en) != snake or not re.fullmatch(r"[A-Z][A-Za-z0-9]*", past):
            raise ValueError(f"{path}：N4 动词行无效：{row!r}")
        if any(v['review_index'] == identity for v in verbs):
            raise ValueError(f"{path}：N4 索引重复：{row!r}")
        verbs.append(dict(zh=labels[0], labels=labels, en=en, past=past, snake=snake, review_index=identity))
    return verbs


def alias_locale(name, source_type):
    if source_type in ('standard-zh', 'standard-en'):
        return ZH_LOCALE if source_type == 'standard-zh' else EN_LOCALE
    han = bool(re.search(r"[\u3400-\u9fff]", name))
    latin = bool(re.search(r"[A-Za-z]", name))
    return UNKNOWN_LOCALE if han == latin else ZH_LOCALE if han else EN_LOCALE


def designation(name, locale, role, usage, approval, source_type, field, index, attributes=None):
    return {LOCALE: locale, NAME: name, ROLE: role, USAGE: usage, APPROVAL: approval,
            SOURCE_TYPE: source_type, MIGRATION: {FIELD: field, INDEX: index, ATTRIBUTES: attributes or {}}}


def convert_entry(source, entry):
    concept = {key: copy.deepcopy(value) for key, value in entry.items() if key not in MOVED_FIELDS}
    approval = APPROVAL_BY_STATUS[entry['status']]
    names = [designation(entry['zh'], ZH_LOCALE, ZH_ROLE, PREFERRED, approval, 'registered', 'zh', 0),
             designation(entry['en'], EN_LOCALE, CODE_ROLE, PREFERRED, approval, 'registered', 'en', 0)]
    for index, alias in enumerate(entry['aliases']):
        usage = FORBIDDEN if alias['deprecated'] or alias['type'] in INTRINSIC_FORBIDDEN else PERMITTED
        state = PENDING if alias['type'] == 'undetermined' else approval
        attributes = {k: copy.deepcopy(v) for k, v in alias.items() if k not in ('name', 'type')}
        locale = alias_locale(alias['name'], alias['type'])
        names.append(designation(alias['name'], locale, ZH_ROLE if locale != EN_LOCALE else CODE_ROLE,
                                 usage, state, alias['type'], 'aliases', index, attributes))
    for index, name in enumerate(entry['forbidden']):
        locale = alias_locale(name, 'forbidden')
        names.append(designation(name, locale, ZH_ROLE if locale != EN_LOCALE else CODE_ROLE,
                                 FORBIDDEN, approval, 'forbidden', 'forbidden', index))
    concept[DESIGNATIONS] = names
    concept[RELATIONS] = [{REL: 'homonym', TARGET: target} for target in entry.get('homonyms', [])]
    if 'upstream_term' in entry:
        concept[RELATIONS].append({REL: 'upstream_term', TARGET: entry['upstream_term']})
    concept[MIGRATION] = {SOURCE: source, 'relation_fields': [k for k in ('homonyms', 'upstream_term') if k in entry]}
    if 'attributes' in entry:
        concept['attributes'] = [{**{k: copy.deepcopy(v) for k, v in a.items() if k not in ('zh', 'en', 'snake')},
            DESIGNATIONS: [designation(a[f], loc, role, PREFERRED, approval, 'registered', f, 0)
                           for f, loc, role in (('zh', ZH_LOCALE, ZH_ROLE), ('en', EN_LOCALE, CODE_ROLE))]}
            for a in entry['attributes']]
    return concept


def decompose(entry, entries, verbs):
    entries = [n for n in entries if n['status'] not in ('deprecated', 'retired') and
               (n['bc'] == entry['bc'] or n['scope'] == 'kernel')]
    candidates = []
    for verb in verbs:
        for noun in entries:
            if entry['kind'] == 'command':
                en = verb['en'] + noun['en']
            else:
                en = noun['en'] + verb['past']
            labels = verb.get('labels', [verb['zh']])
            zh_names = [label + noun['zh'] if entry['kind'] == 'command' else noun['zh'] + '已' + label for label in labels]
            if entry['en'] == en and entry['zh'] in zh_names:
                candidates.append({OBJECT: noun['id'], VERB: verb['snake']})
    if len(candidates) == 1:
        return candidates[0], None
    return None, exception_reason(entry, entries, verbs, candidates)


def exception_reason(entry, nouns, verbs, candidates):
    en, zh = entry['en'], entry['zh']
    if candidates:
        return '匹配歧义：多个对象或 N4 语义行动词匹配，须人工确认'
    if entry['kind'] == 'state':
        return '状态集合名：缺少状态取值与所属集合槽位' if en.endswith(('Status', 'State', 'Outcome')) or zh.endswith(('状态', '结果')) else '裸状态：缺少所属对象或 aspect，不能由已字推定事件'
    if any(en == v['en'] or en == v['past'] for v in verbs) or not any(n['en'] in en for n in nouns):
        return '缺名词：没有可定位的同 BC / kernel 对象，或名称只有动作'
    if re.search(r"(?:For|By|With|To|As|Became|Become|Given|Under|Of)[A-Z]", en):
        return '带补语：包含目的、方向或结果短语，须人工登记完整槽位'
    return '中文不合模板：中英文未同时精确匹配受控动词与登记对象；不作近义替换'


def migrate(sources, verbs):
    entries, seen, metadata = [], set(), []
    for source, document in sources:
        if not isinstance(document, dict) or not isinstance(document.get('terms'), list):
            raise ValueError(f"{source}：缺失或无效的 terms 列表")
        metadata.append({SOURCE: source, ATTRIBUTES: copy.deepcopy({k: v for k, v in document.items() if k != 'terms'})})
        for entry in document['terms']:
            validate_entry(entry, source)
            if entry['id'] in seen:
                raise ValueError(f"{source}：重复概念 id {entry['id']}")
            seen.add(entry['id'])
            entries.append((source, entry))
    concepts, exceptions = [], []
    nouns = [e for _, e in entries if e['kind'] in NOUN_KINDS]
    for source, entry in entries:
        concept = convert_entry(source, entry)
        if entry['kind'] in COMPOSED_KINDS:
            composition, reason = decompose(entry, nouns, verbs)
            if composition is not None:
                concept[COMPOSITION] = composition
            else:
                exceptions.append({'id': entry['id'], 'kind': entry['kind'], SOURCE: source, REASON: reason})
        concepts.append(concept)
    return {CONCEPTS: concepts, SOURCES: metadata, MIGRATION: {EXCEPTIONS: exceptions}}


def validate_designations(concept):
    names, coordinates, usages = concept[DESIGNATIONS], set(), {}
    approval = APPROVAL_BY_STATUS[concept['status']]
    for d in names:
        require_text(d[NAME], f"{concept['id']}.designations.name")
        field, index, attrs = d[MIGRATION][FIELD], d[MIGRATION][INDEX], d[MIGRATION][ATTRIBUTES]
        if field not in ('zh', 'en', 'aliases', 'forbidden') or type(index) is not int or index < 0 or (field, index) in coordinates:
            raise ValueError('指称字段归属或序号无效/重复')
        coordinates.add((field, index))
        source_type = d[SOURCE_TYPE]
        if field == 'aliases':
            if source_type not in ALIAS_TYPES or type(attrs.get('deprecated')) is not bool:
                raise ValueError('aliases：来源类型或 deprecated 无效')
            usage = FORBIDDEN if attrs['deprecated'] or source_type in INTRINSIC_FORBIDDEN else PERMITTED
        else:
            expected_type = 'registered' if field in ('zh', 'en') else 'forbidden'
            if source_type != expected_type or attrs or (field in ('zh', 'en') and index != 0):
                raise ValueError('首选/禁用指称溯源无效')
            usage = FORBIDDEN if field == 'forbidden' else PREFERRED
        locale = ZH_LOCALE if field == 'zh' else EN_LOCALE if field == 'en' else alias_locale(d[NAME], source_type)
        expected = (usage, PENDING if source_type == 'undetermined' else approval, locale, CODE_ROLE if locale == EN_LOCALE else ZH_ROLE)
        if (d[USAGE], d[APPROVAL], d[LOCALE], d[ROLE]) != expected:
            raise ValueError(f"{field}[{index}]：usage_status/approval_status/locale/role 与来源语义不一致，预期 {expected}")
        forbidden = d[USAGE] == FORBIDDEN
        if d[NAME] in usages and usages[d[NAME]] != forbidden:
            raise ValueError(f"同名指称使用状态矛盾：{d[NAME]!r} 同时可用和禁用")
        usages[d[NAME]] = forbidden


def project_legacy(concept):
    validate_designations(concept)
    names = concept[DESIGNATIONS]
    result = {}
    for field, locale, role in (('zh', ZH_LOCALE, ZH_ROLE), ('en', EN_LOCALE, CODE_ROLE)):
        preferred = [d for d in names if (d[LOCALE], d[ROLE], d[USAGE]) == (locale, role, PREFERRED)]
        if len(preferred) != 1:
            raise ValueError(f"{concept['id']}.{field}：须恰有一个首选指称")
        result[field] = preferred[0][NAME]
    result['snake'] = to_snake(result['en'])
    for field in ('aliases', 'forbidden'):
        designations = sorted((d for d in names if d[MIGRATION][FIELD] == field), key=lambda d: d[MIGRATION][INDEX])
        if [d[MIGRATION][INDEX] for d in designations] != list(range(len(designations))):
            raise ValueError(f'{field}：指称序号不连续')
        result[field] = [({**copy.deepcopy(d[MIGRATION][ATTRIBUTES]), NAME: d[NAME], 'type': d[SOURCE_TYPE],
                          'deprecated': d[USAGE] == FORBIDDEN and (d[SOURCE_TYPE] not in INTRINSIC_FORBIDDEN or d[MIGRATION][ATTRIBUTES]['deprecated'])}
                          if field == 'aliases' else d[NAME]) for d in designations]
    return result


def restore_entry(concept):
    result = {k: copy.deepcopy(v) for k, v in concept.items() if k not in RESERVED_FIELDS}
    result.update(project_legacy(concept))
    for relation in concept[RELATIONS]:
        rel, target = relation[REL], relation[TARGET]
        require_text(target, 'relations.target')
        if rel == 'homonym':
            result.setdefault('homonyms', []).append(target)
        elif rel == 'upstream_term' and rel not in result:
            result[rel] = target
        else:
            raise ValueError(f"relations：未知或重复关系 {rel!r}")
    if 'homonyms' in concept[MIGRATION]['relation_fields']:
        result.setdefault('homonyms', [])
    if 'attributes' in concept:
        result['attributes'] = []
        for index, attr in enumerate(concept['attributes']):
            names = project_legacy({**attr, 'id': f"{concept['id']}.attributes[{index}]", 'status': concept['status']})
            result['attributes'].append({**{k: copy.deepcopy(v) for k, v in attr.items() if k != DESIGNATIONS},
                                         **{k: names[k] for k in ('zh', 'en', 'snake')}})
    return result


def roundtrip_differences(sources, document):
    originals = {e['id']: e for _, data in sources for e in data['terms']}
    differences, found = [], set()
    for concept in document[CONCEPTS]:
        identity = concept['id']
        if identity in found or identity not in originals:
            differences.append(f"{identity}：重复或多出的概念 id")
            continue
        found.add(identity)
        try:
            projection = restore_entry(concept)
        except (ValueError, KeyError, TypeError) as error:
            differences.append(f"{identity}：投影失败：{error}")
            continue
        for field in sorted(originals[identity].keys() | projection.keys()):
            if field not in originals[identity] or field not in projection or originals[identity][field] != projection[field]:
                differences.append(f"{identity}.{field}：原字段缺失、多出或投影不等")
        source = next(s for s, data in sources if any(e['id'] == identity for e in data['terms']))
        if concept[MIGRATION][SOURCE] != source:
            differences.append(f"{identity}.migration.source：来源不等")
    differences.extend(f"{identity}：缺少概念" for identity in originals if identity not in found)
    expected_sources = [{SOURCE: s, ATTRIBUTES: {k: v for k, v in data.items() if k != 'terms'}} for s, data in sources]
    if document[SOURCES] != expected_sources:
        differences.append('sources：文件元数据、顺序或来源不等')
    return differences


def validate_output(destination, inputs):
    common = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', '--git-common-dir'], check=True,
                            capture_output=True, text=True).stdout.strip()
    if not common:
        raise ValueError('git-common-dir 为空，无法验证主检出目录边界')
    main_root = (ROOT / common).resolve().parent
    if any(destination.is_relative_to(root) for root in (ROOT, main_root)) or any(destination == path or
            (destination.exists() and destination.samefile(path)) for path in inputs):
        raise ValueError('--out 不能写入 worktree、主检出目录或覆盖输入；请指定仓库外的新文件')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--terms', type=Path, default=DEFAULT_TERMS)
    parser.add_argument('--proposed-dir', type=Path, default=DEFAULT_PROPOSED)
    parser.add_argument('--verbs', type=Path, default=DEFAULT_VERBS)
    parser.add_argument('--out', type=Path, help='显式输出到仓库外文件；默认 stdout')
    args = parser.parse_args(argv)
    try:
        sources = read_sources(args.terms, args.proposed_dir)
        document = migrate(sources, read_verbs(args.verbs))
        output = yaml.safe_dump(document, allow_unicode=True, sort_keys=False)
        differences = roundtrip_differences(sources, load_yaml(output))
        if differences:
            raise ValueError(f'往返比对失败：{len(differences)} 差异；反解例外 {len(document[MIGRATION][EXCEPTIONS])} 条\n' + '\n'.join(differences))
        if args.out:
            destination = args.out.resolve()
            inputs = [args.terms.resolve(), args.verbs.resolve(), *[p.resolve() for p in args.proposed_dir.glob('*.yaml')]]
            validate_output(destination, inputs)
            with destination.open('x', encoding='utf-8') as stream:
                stream.write(output)
        else:
            sys.stdout.write(output)
        print(f"已比对 {len(document[CONCEPTS])} 条的完整字段、属性指称与语义，0 差异；反解例外 {len(document[MIGRATION][EXCEPTIONS])} 条（见 migration.exceptions）。", file=sys.stderr)
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError, yaml.YAMLError) as error:
        print(f"迁移失败：{error}", file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
