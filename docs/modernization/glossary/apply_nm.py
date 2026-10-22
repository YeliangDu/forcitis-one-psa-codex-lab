"""NM 已提交答复及对象交接写回；由 build_terms.py 在补充字段合入后调用。"""
import copy
import json
import re
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
REQUIRED = {"id", "zh", "en", "snake", "kind", "scope", "bc", "definition",
            "standard_ref", "confidence", "aliases", "forbidden", "status", "confirmed"}
RESPONSE_FIELDS = {"zh", "definition", "aliases", "forbidden", "standard_ref", "note"}


def validate_term(term):
    if REQUIRED - set(term) or term["status"] != "approved":
        raise SystemExit(f"NM 写回：{term.get('id')} 缺词条必填字段或未批准")
    for field in ("id", "zh", "en", "snake", "definition"):
        if not isinstance(term[field], str) or not term[field].strip():
            raise SystemExit(f"NM 写回：{term.get('id')} 的 {field} 必须为非空文本")


def load_proposed():
    files = sorted((ROOT.parent.parent / "backend/glossary/proposed").glob("*.yaml"))
    if not files:
        raise SystemExit("NM 写回：找不到 backend/glossary/proposed/*.yaml")
    return [t for path in files
            for t in (yaml.safe_load(path.read_text(encoding="utf-8")) or {}).get("terms") or []]


def banned_names(term):
    return set(term.get("forbidden", [])) | {
        a["name"] for a in term.get("aliases", []) if a["type"] == "dialect"}


def check_banned_names(terms, candidates, contexts):
    protected = []
    for context in contexts:
        for field in ("zh", "description"):
            if context.get(field):
                protected.append((None, f"bcs/{context['id']}.{field}", context[field]))
        protected.extend((None, f"bcs/{context['id']}.words", w) for w in context.get("words", []))
    for term in terms:
        for field in ("zh", "definition"):
            if term.get(field):
                protected.append((term["id"], f"terms/{term['id']}.{field}", term[field]))
    for tid, names in candidates.items():
        for name in sorted(names):
            if not re.search(r"[\u4e00-\u9fff]", name):
                continue
            for owner, source, value in protected:
                if owner != tid and (name in value or value in name):
                    raise SystemExit(f"NM 写回：{tid} 禁用短语 {name!r} 与 {source} 冲突；"
                                     "改为 standard-zh / deprecated: true，并移出 forbidden")


def append_text(original, addition):
    if not addition or addition in original:
        return original
    return original + ("；" if original else "") + addition


def update_term(target, entry, fields):
    if not fields or len(fields) != len(set(fields)) or set(fields) - RESPONSE_FIELDS:
        raise SystemExit(f"NM 写回：{entry['id']} 须明确列出答复涉及的 fields")
    if set(fields) - set(entry):
        raise SystemExit(f"NM 写回：{entry['id']} 缺 fields 对应值")
    for field, value in entry.items():
        if field not in set(fields) | {"confirmed"} and (
                field not in target or target[field] != value):
            raise SystemExit(f"NM 写回：{entry['id']} 非答复字段 {field} 与上游不一致，不得覆盖")
    for field in fields:
        if field in ("aliases", "forbidden", "standard_ref"):
            original = target.get(field, [])
            target[field] = original + [x for x in entry[field] if x not in original]
        elif field == "note":
            target[field] = append_text(target.get(field, ""), entry[field])
        else:
            target[field] = entry[field]
    target["confirmed"] = append_text(target.get("confirmed", ""), entry["confirmed"])


def verify_sheet(sheet, bykey, questions, answers, original=None):
    expected = set()
    for q in questions["items"]:
        key = f"{sheet}~{q['no']}"
        expected.add(key)
        answer, row = answers.get(key), bykey.get(key)
        if (not answer or not row or answer.get("unanswered") or answer.get("note")
                or not isinstance(answer.get("submission"), str) or not answer["submission"].strip()):
            raise SystemExit(f"NM 写回：{key} 缺已提交答复，或说明需要重新核对")
        for field in ("label", "choice", "submission"):
            if answer.get(field) != row[field]:
                raise SystemExit(f"NM 写回：{key} 的 {field} 与定稿不一致")
        choice = row["choice"]
        if type(choice) is not int or not 0 <= choice < len(q["opts"]) or q["opts"][choice]["label"] != row["label"]:
            raise SystemExit(f"NM 写回：{key} 的所选名称与题面选项序号不一致")
        if q.get("noun_refs", []) != row["noun_refs"]:
            raise SystemExit(f"NM 写回：{key} 名词引用已变化")
        if original is not None and (row["submission"] not in original or row["label"] not in original):
            raise SystemExit(f"NM 写回：{key} 在 intent 中缺提交或原话证据")
    if set(answers) != expected:
        raise SystemExit(f"NM 写回：{sheet} 题面和答复键不一致")
    return expected


def verify_handoff(source, bykey, original, submission):
    objects = re.findall(r"^\| (OBJ-\d+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|", original, re.M)
    if not objects or submission not in original:
        raise SystemExit(f"NM 写回：{source} 缺提交或对象表证据")
    expected = set()
    for ref, zh, en, bc in objects:
        key = f"{source}~{ref}"
        if key in expected:
            raise SystemExit(f"NM 写回：{key} 交接对象重复")
        expected.add(key)
        row = bykey.get(key, {})
        for field, value in dict(label=zh.strip(), en=en.strip(), bc=bc.strip(), submission=submission).items():
            if row.get(field) != value:
                raise SystemExit(f"NM 写回：{key} 的 {field} 与交接不一致")
    return expected


def confirmation(keys, bykey):
    return "；".join(dict.fromkeys(
        f"2026-10-17（用户，命名第 {k[2]} 批确认页，提交 {bykey[k]['submission']}）"
        if k.startswith("NM") else
        f"2026-10-17（用户授权登记；one-architecture 对象移交，提交 {bykey[k]['submission']}）" for k in keys))


def read_decisions(doc=None):
    if doc is None:
        doc = yaml.safe_load((HERE / "decisions-nm.yaml").read_text(encoding="utf-8"))
    rows = doc["answers"]
    bykey = {r["key"]: r for r in rows}
    if len(bykey) != len(rows):
        raise SystemExit("NM 写回：答复键重复")
    expected = set()
    intents = doc["meta"]["intents"]
    submitted = doc["meta"].get("submitted_sheets", [])
    if len(submitted) != len(set(submitted)) or set(submitted) & set(intents):
        raise SystemExit("NM 写回：题面来源重复")
    for sheet in [*intents, *submitted]:
        questions = json.loads((ROOT / f"rules/decide/sheets/{sheet}.json").read_text(encoding="utf-8"))
        answers = json.loads((ROOT / f"rules/decide/answers/{sheet}.json").read_text(encoding="utf-8"))
        original = (ROOT / intents[sheet]).read_text(encoding="utf-8") if sheet in intents else None
        expected.update(verify_sheet(sheet, bykey, questions, answers, original))
    for source, evidence in doc["meta"].get("handoffs", {}).items():
        if source in intents or source in submitted:
            raise SystemExit("NM 写回：交接与题面来源重复")
        original = (ROOT / evidence["path"]).read_text(encoding="utf-8")
        expected.update(verify_handoff(source, bykey, original, evidence["submission"]))
    if expected != set(bykey):
        raise SystemExit("NM 写回：定稿未逐项覆盖当前答复与对象交接")
    for operation in doc["terms"]:
        t = operation["term"]
        validate_term(t)
        keys = operation["answers"]
        if not keys or set(keys) - expected:
            raise SystemExit(f"NM 写回：{t['id']} 缺已核实的答复引用")
        confirmed = confirmation(keys, bykey)
        if t["confirmed"] != confirmed:
            raise SystemExit(f"NM 写回：{t['id']} 的确认来源不一致")
        for key in keys:
            if not key.startswith("NM") and any(t[f] != bykey[key][r] for f, r in (("zh", "label"), ("en", "en"), ("bc", "bc"))):
                raise SystemExit(f"NM 写回：{t['id']} 与 {key} 的对象名或 BC 不一致")
    for row in rows:
        targets = [o["term"]["id"] for o in doc["terms"] if row["key"] in o["answers"]]
        verbs = [v["source"] for v in doc["verbs"] if row["key"] in v["answers"]]
        if row["terms"] != targets or row["verbs"] != verbs:
            raise SystemExit(f"NM 写回：{row['key']} 对照表与实际写回不一致")
    return doc


def apply_operations(terms, operations, *, contexts=None, proposed=None):
    if contexts is None:
        contexts = yaml.safe_load((HERE / "bcs.yaml").read_text(encoding="utf-8"))["bcs"]
    if proposed is None:
        proposed = load_proposed()
    byid = {t["id"]: t for t in terms}
    touched = set()
    candidates = {}
    counts = {"added": 0, "updated": 0, "homonyms": 0}
    for operation in operations:
        entry = copy.deepcopy(operation["term"])
        validate_term(entry)
        tid = entry["id"]
        if tid in touched:
            raise SystemExit(f"NM 写回：重复操作 {tid}")
        touched.add(tid)
        if operation["op"] == "add":
            if tid in byid:
                raise SystemExit(f"NM 写回：新增 {tid} 已存在，不覆盖概念")
            for other in proposed:
                for field in ("id", "snake"):
                    if entry[field] == other.get(field):
                        raise SystemExit(f"NM 写回：新增 {tid} 的 {field} 与 proposed/{other['id']} 重复")
            terms.append(entry)
            byid[tid] = entry
            candidates[tid] = banned_names(entry)
            counts["added"] += 1
        elif operation["op"] == "update":
            if tid not in byid:
                raise SystemExit(f"NM 写回：更新目标 {tid} 不存在")
            target = byid[tid]
            original_bans = banned_names(target)
            update_term(target, entry, operation.get("fields", []))
            candidates[tid] = banned_names(target) - original_bans
            counts["updated"] += 1
        else:
            raise SystemExit(f"NM 写回：未知操作 {operation['op']}")
    check_banned_names(terms + proposed, candidates, contexts)
    live = [t for t in terms if t["status"] != "deprecated"]
    for field in ("zh", "en"):
        groups = {}
        for t in live:
            groups.setdefault(t[field], []).append(t)
        for name, group in groups.items():
            if len(group) < 2 or not any(t["id"] in touched for t in group):
                continue
            if len({t["bc"] for t in group}) != len(group):
                raise SystemExit(f"NM 写回：同 BC 的 {field} 重名 {name}")
            for t in group:
                for other in group:
                    if other is not t and other["id"] not in t.setdefault("homonyms", []):
                        t["homonyms"].append(other["id"])
                        counts["homonyms"] += 1
    return counts


def apply(terms):
    doc = read_decisions()
    return apply_operations(terms, doc["terms"])


def self_test():
    def term(tid, bc, zh="项目", en="Project"):
        return dict(id=tid, bc=bc, zh=zh, en=en, snake=tid, kind="aggregate", scope="domain",
                    definition="测试对象定义", standard_ref=[], confidence="medium", aliases=[],
                    forbidden=[], status="approved", confirmed="本批确认")

    def op(t, action="add", fields=None):
        return {"op": action, "term": t, "fields": ["definition"] if fields is None else fields}

    def run(source, operations, **kwargs):
        return apply_operations(source, operations, contexts=kwargs.get("contexts", []),
                                proposed=kwargs.get("proposed", []))

    failures = 0

    def rejected(source, operations, message, **kwargs):
        nonlocal failures
        try:
            run(source, operations, **kwargs)
        except SystemExit as error:
            assert message in str(error), (message, str(error))
            failures += 1
            return
        raise AssertionError(f"应拦截：{message}")

    terms = [term("a", "delivery.execution")]
    run(terms, [op(term("b", "cost.costing"))])
    assert terms[0]["homonyms"] == ["b"] and terms[1]["homonyms"] == ["a"]
    for source, operations, message in (
        ([term("a", "delivery.execution")], [op(term("a", "cost.costing"))], "已存在"),
        ([], [op(term("a", "cost.costing"), "update")], "不存在"),
        ([term("a", "delivery.execution")], [op(term("b", "delivery.execution"))], "同 BC"),
        ([term("a", "delivery.execution")], [op(term("a", "cost.costing"), "update")], "非答复字段 bc"),
        ([], [op(term("a", "cost.costing")), op(term("a", "cost.costing"))], "重复操作"),
        ([], [op(term("a", "cost.costing"), "unknown")], "未知操作"),
    ):
        rejected(source, operations, message)
    for field in ("id", "zh", "en", "snake", "definition"):
        for empty in ("", " \t", None):
            entry = term("a", "delivery.execution")
            entry[field] = empty
            rejected([], [op(entry)], f"{field} 必须为非空文本")
    missing = term("a", "delivery.execution")
    del missing["zh"]
    rejected([], [op(missing)], "缺词条必填字段")
    for field, value in (("en", "Changed"), ("snake", "changed"), ("kind", "entity"),
                         ("scope", "acl"), ("confidence", "high"), ("core", True),
                         ("used_by", ["cost.costing"])):
        original = term("a", "delivery.execution")
        entry = copy.deepcopy(original)
        entry[field] = value
        rejected([original], [op(entry, "update")], f"非答复字段 {field}")
    for fields in ([], ["kind"], ["definition", "definition"]):
        original = term("a", "delivery.execution")
        rejected([original], [op(copy.deepcopy(original), "update", fields)], "fields")
    entry = term("a", "delivery.execution", zh="改名")
    rejected([term("a", "delivery.execution")], [op(entry, "update")], "非答复字段 zh")
    for field in ("id", "snake"):
        other = term("b", "cost.costing", zh="另一对象", en="Other")
        other[field] = "a"
        rejected([], [op(term("a", "delivery.execution"))], f"{field} 与 proposed", proposed=[other])
    for field in ("zh", "words", "description"):
        for location in ("forbidden", "aliases"):
            entry = term("a", "organization.leave", zh="加班记录", en="OvertimeRecord")
            entry[location] = ["加班"] if location == "forbidden" else [
                {"name": "加班", "type": "dialect", "deprecated": True}]
            context = dict(id="organization.leave", **{field: ["加班"] if field == "words" else "加班"})
            rejected([], [op(entry)], "禁用短语", contexts=[context])
    for field in ("zh", "definition"):
        entry = term("a", "delivery.execution", zh="甲对象", en="First")
        entry["forbidden"] = ["通用短语"]
        other = term("b", "cost.costing", zh="乙对象", en="Second")
        other[field] = "已有通用短语说明"
        rejected([other], [op(entry)], f"terms/b.{field}")
        entry["forbidden"] = ["已有通用短语说明的旧称"]
        rejected([other], [op(entry)], f"terms/b.{field}")
    entry = term("a", "delivery.execution", zh="甲对象", en="First")
    entry["forbidden"] = ["通用词旧称"]
    rejected([], [op(entry)], "bcs/other.zh", contexts=[dict(id="other", zh="通用词")])
    other = term("b", "cost.costing", zh="乙对象", en="Second")
    other["definition"] = "通用词旧称已用于其他概念"
    rejected([], [op(entry)], "terms/b.definition", proposed=[other])
    entry["forbidden"] = []
    entry["aliases"] = [{"name": "通用词旧称", "type": "standard-zh", "deprecated": True}]
    run([], [op(entry)], proposed=[other])
    entry["aliases"] = [{"name": "纯旧申请记录", "type": "dialect", "deprecated": True}]
    run([], [op(entry)], proposed=[other])
    original = term("a", "delivery.execution")
    original.update(confirmed="原确认一；原确认二", note="上游说明", aliases=[
        {"name": "Original", "type": "old-code", "deprecated": True}],
        forbidden=["OriginalCode"], standard_ref=["原依据"], upstream_only="保留")
    entry = copy.deepcopy(original)
    entry.pop("upstream_only")
    entry.update(confirmed="新增确认", note="本次说明", definition="答复定义", aliases=[
        {"name": "New", "type": "old-code", "deprecated": True}],
        forbidden=["NewCode"], standard_ref=["新依据"])
    fields = ["definition", "note", "aliases", "forbidden", "standard_ref"]
    terms = [original]
    run(terms, [op(entry, "update", fields)])
    assert terms[0]["confirmed"] == "原确认一；原确认二；新增确认"
    assert terms[0]["note"] == "上游说明；本次说明" and terms[0]["upstream_only"] == "保留"
    assert [a["name"] for a in terms[0]["aliases"]] == ["Original", "New"]
    assert terms[0]["forbidden"] == ["OriginalCode", "NewCode"]
    assert terms[0]["standard_ref"] == ["原依据", "新依据"]
    before = copy.deepcopy(terms)
    run(terms, [op(entry, "update", fields)])
    assert terms == before
    doc = read_decisions()
    for key, field, value, message in (
        ("NM5~NM5-01", "label", "公司统一的财务职责", "label 与定稿"),
        ("NM5~NM5-01", "choice", 0, "choice 与定稿"),
        ("NM5~NM5-02", "submission", "另一提交", "submission 与定稿"),
        ("NM5~NM5-03", "noun_refs", ["未知对象"], "名词引用已变化"),
        ("CAPINFO~OBJ-120", "en", "OtherOrder", "en 与交接"),
        ("CAPINFO~OBJ-121", "bc", "cost.costing", "bc 与交接"),
        ("CAPINFO~OBJ-123", "submission", "另一提交", "submission 与交接"),
    ):
        changed = copy.deepcopy(doc)
        next(r for r in changed["answers"] if r["key"] == key)[field] = value
        try:
            read_decisions(changed)
        except SystemExit as error:
            assert message in str(error), str(error)
            failures += 1
        else:
            raise AssertionError(f"应拦截：{key}.{field}")
    questions = {"items": [{"no": "Q", "opts": [{"label": "甲"}, {"label": "乙"}]}]}
    for patch in (dict(unanswered=True), dict(note="需重新核对"), dict(submission=""), dict(submission=None)):
        entry = dict(label="甲", choice=0, submission="已提交", noun_refs=[])
        entry.update(patch)
        try:
            verify_sheet("T", {"T~Q": entry}, questions, {"T~Q": entry})
        except SystemExit as error:
            assert "缺已提交答复" in str(error)
            failures += 1
        else:
            raise AssertionError(f"应拦截：无有效提交或需核对的答复 {patch}")
    answer = dict(label="甲", choice=1, submission="已提交", noun_refs=[])
    try:
        verify_sheet("T", {"T~Q": answer}, questions, {"T~Q": answer})
    except SystemExit as error:
        assert "选项序号不一致" in str(error)
        failures += 1
    else:
        raise AssertionError("应拦截：答复与定稿同时使用错误选项序号")
    print(f"NM 写回自测：通过（{failures} 个负向用例、同名互列、确认与说明追加保留、"
          "更新幂等、51 道答复及 7 项对象交接来源核对）")


if __name__ == "__main__":
    import sys
    if sys.argv[1:] != ["--self-test"]:
        raise SystemExit("用法：python3 glossary/apply_nm.py --self-test；写回由 glossary/build_terms.py 调用")
    self_test()
