"""NAM-1017-GLO-06 已裁定用语写回；提议源在同批次精确删除。"""
import copy
import hashlib
from pathlib import Path

import yaml

import apply_nm
import term_rules

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
UPDATE_FIELDS = {"zh", "en", "snake", "aliases", "forbidden", "standard_ref", "note"}


def fail(message):
    raise SystemExit("RECON 写回：" + message)


def snake(en):
    return "_".join(term_rules.words(en))


def validate(term):
    apply_nm.validate_term(term)
    for field in ("kind", "scope", "bc", "confidence", "confirmed"):
        if not isinstance(term[field], str) or not term[field].strip():
            fail(f"{term['id']} 的 {field} 必须为非空文本")
    for field in ("aliases", "forbidden", "standard_ref"):
        if not isinstance(term[field], list):
            fail(f"{term['id']} 的 {field} 必须为列表")
    if not term["standard_ref"] or term["snake"] != snake(term["en"]):
        fail(f"{term['id']} 缺依据或 snake 不由 en 派生")


def source_ref(source):
    return source["file"] + ("#" + source["question"] if source.get("question") else "")


def confirmation(keys, sources):
    return "；".join(dict.fromkeys(
        f"{sources[k]['date']}（用户裁定，{sources[k]['batch']}，"
        f"{('提交 ' + sources[k]['submission']) if sources[k].get('submission') else source_ref(sources[k])}）"
        for k in keys))


def read_decisions(doc=None):
    if doc is None:
        doc = yaml.safe_load((HERE / "decisions-recon.yaml").read_text(encoding="utf-8"))
    sources = doc["sources"]
    for key, source in sources.items():
        path = (REPO / source["file"]).resolve()
        if not path.is_relative_to(REPO) or not path.is_file():
            fail(f"{key} 裁定来源不存在或越界")
        if hashlib.sha256(path.read_bytes()).hexdigest() != source["sha256"]:
            fail(f"{key} 裁定来源已变化，请重新核对原话")
    for operation in doc["terms"]:
        keys = operation["sources"]
        if not keys or set(keys) - sources.keys():
            fail(f"{operation['term']['id']} 缺已核实裁定来源")
        term = operation["term"]
        if term["confirmed"] != confirmation(keys, sources):
            fail(f"{term['id']} 确认来源不一致")
        if not all(source_ref(sources[k]) in term["standard_ref"] for k in keys):
            fail(f"{term['id']} standard_ref 未列裁定出处")
    missing = doc["missing"]
    if len(missing) != 60 or {r["no"] for r in missing} != set(range(1, 61)):
        fail("漏登对照须逐项覆盖 60 项")
    if len(doc["promotions"]) != 66:
        fail("提议对照须逐项覆盖 A 类 66 项")
    return doc


def update(target, operation):
    entry = operation["term"]
    fields = operation.get("fields", [])
    if (not fields or len(fields) != len(set(fields)) or set(fields) - UPDATE_FIELDS
            or set(entry) != set(fields) | {"id", "confirmed"}):
        fail(f"{entry['id']} 须明确且仅列答复涉及的 fields")
    for field in fields:
        value = entry[field]
        if field in ("aliases", "forbidden", "standard_ref"):
            original = target.get(field, [])
            target[field] = original + [v for v in value if v not in original]
        elif field == "note":
            target[field] = apply_nm.append_text(target.get(field, ""), value)
        else:
            target[field] = value
    target["confirmed"] = apply_nm.append_text(target.get("confirmed", ""), entry["confirmed"])


def apply_operations(terms, operations, *, proposed, contexts):
    result = copy.deepcopy(terms)
    byid = {t["id"]: t for t in result}
    if len(byid) != len(result):
        fail("正式词表 id 重复")
    touched, candidates = set(), {}
    counts = {"added": 0, "updated": 0}
    for operation in operations:
        entry = copy.deepcopy(operation["term"])
        if operation["op"] == "add":
            validate(entry)
        elif not isinstance(entry.get("id"), str) or not entry["id"].strip():
            fail("操作 id 必须为非空文本")
        tid = entry["id"]
        if tid in touched:
            fail(f"重复操作 {tid}")
        touched.add(tid)
        action = operation["op"]
        if action == "add":
            if tid in byid:
                fail(f"新增 {tid} 已存在，不覆盖概念")
            for other in [*result, *proposed]:
                for field in ("id", "snake"):
                    if entry[field] == other.get(field):
                        fail(f"新增 {tid} 的 {field} 与存量 {other['id']} 重复")
            result.append(entry)
            byid[tid] = entry
            candidates[tid] = apply_nm.banned_names(entry)
            counts["added"] += 1
        elif action == "update":
            if tid not in byid:
                fail(f"更新目标 {tid} 不存在")
            target = byid[tid]
            original_bans = apply_nm.banned_names(target)
            update(target, operation)
            validate(target)
            if "snake" in operation["fields"]:
                if any(t.get("snake") == target["snake"] and t["id"] != tid
                       for t in [*result, *proposed]):
                    fail(f"更新 {tid} 的 snake 与存量重复")
            candidates[tid] = apply_nm.banned_names(target) - original_bans
            counts["updated"] += 1
        else:
            fail(f"未知操作 {action}")
    apply_nm.check_banned_names(result + proposed, candidates, contexts)
    bcs = {b["id"]: b["kind"] for b in contexts}
    before = term_rules.check([("formal", t) for t in terms] + [("proposed", t) for t in proposed], bcs)
    after = term_rules.check([("formal", t) for t in result] + [("proposed", t) for t in proposed], bcs)
    new = set(after) - set(before)
    if new:
        fail("新增用语问题：" + "；".join(f"{k} {after[k]}" for k in sorted(new)))
    terms[:] = result
    return counts


def verify_promotions(terms, promotions, proposed_by_file):
    byid = {t["id"]: t for t in terms}
    seen = set()
    for row in promotions:
        key = (row["file"], row["id"])
        if key in seen:
            fail(f"重复提议来源 {key}")
        seen.add(key)
        target = byid.get(row["target"])
        if not target or target.get("status") != "approved":
            fail(f"{key} 没有已批准正式目标")
        original = row["original"]
        if original["id"] != row["id"]:
            fail(f"{key} 原条目 id 不一致")
        rows = [t for t in proposed_by_file[row["file"]] if t["id"] == row["id"]]
        if row["disposition"] == "remove":
            if rows:
                fail(f"{key} 已转正但提议源未删除")
        elif row["disposition"] == "retain_deprecated":
            if original.get("status") != "deprecated" or rows != [original]:
                fail(f"{key} 旧名兼容条目已漂移")
            if original.get("replaced_by") != row["target"]:
                fail(f"{key} 弃用条目指向错误目标")
        else:
            fail(f"{key} 未知清理方式")


def apply(terms):
    doc = read_decisions()
    contexts = yaml.safe_load((HERE / "bcs.yaml").read_text(encoding="utf-8"))["bcs"]
    proposed_by_file = {str(p.relative_to(REPO)): (yaml.safe_load(p.read_text(encoding="utf-8")) or {}).get("terms") or []
                        for p in sorted((REPO / "backend/glossary/proposed").glob("*.yaml"))}
    counts = apply_operations(terms, doc["terms"], contexts=contexts,
                              proposed=[t for rows in proposed_by_file.values() for t in rows])
    verify_promotions(terms, doc["promotions"], proposed_by_file)
    return counts


def sample_term(tid="a", **kw):
    term = dict(id=tid, zh="甲", en="First", snake="first", kind="aggregate", scope="domain",
                bc="cost.costing", definition="测试定义", standard_ref=["测试裁定"], confidence="medium",
                aliases=[], forbidden=[], status="approved", confirmed="本批确认")
    term.update(kw)
    return term


def test_sources(reject):
    doc = read_decisions()
    changed = copy.deepcopy(doc); next(iter(changed["sources"].values()))["sha256"] = "0" * 64
    reject(lambda: read_decisions(changed), "裁定来源已变化")
    changed = copy.deepcopy(doc); changed["terms"][0]["term"]["confirmed"] = "错误来源"
    reject(lambda: read_decisions(changed), "确认来源不一致")
    changed = copy.deepcopy(doc); changed["missing"].pop()
    reject(lambda: read_decisions(changed), "逐项覆盖 60 项")
    changed = copy.deepcopy(doc); changed["promotions"].pop()
    reject(lambda: read_decisions(changed), "逐项覆盖 A 类 66 项")


def self_test():
    term = sample_term
    contexts = [dict(id="cost.costing", kind="bc", zh="项目成本", words=["成本"])]
    def run(ts, ops, proposed=None):
        return apply_operations(ts, ops, proposed=proposed or [], contexts=contexts)
    failures = 0
    def reject(fn, message):
        nonlocal failures
        try:
            fn()
        except SystemExit as error:
            assert message in str(error), str(error)
            failures += 1
        else:
            raise AssertionError("应拦截：" + message)

    add = lambda t: dict(op="add", term=t)
    for field in apply_nm.REQUIRED:
        t = term(); del t[field]
        reject(lambda: run([], [add(t)]), "缺词条必填字段")
    for field in ("id", "zh", "en", "snake", "definition", "confirmed", "bc", "scope", "kind", "confidence"):
        for value in ("", " \t", None):
            reject(lambda: run([], [add(term(**{field: value}))]), "必须为非空文本")
    for field in ("aliases", "forbidden", "standard_ref"):
        reject(lambda: run([], [add(term(**{field: "错误类型"}))]), "必须为列表")
    reject(lambda: run([], [add(term(standard_ref=[]))]), "缺依据")
    reject(lambda: run([], [add(term(snake="wrong"))]), "snake 不由 en")
    reject(lambda: run([term()], [add(term())]), "已存在")
    for old in ([term("b")], []):
        reject(lambda: run(old, [add(term())], [] if old else [term("b")]), "snake 与存量")
    reject(lambda: run([], [add(term())], [term(en="Second", snake="second")]), "id 与存量")
    reject(lambda: run([], [add(term()), add(term())]), "重复操作")
    reject(lambda: run([], [dict(op="bad", term=term())]), "未知操作")
    reject(lambda: run([], [dict(op="update", fields=["zh"], term=dict(id="a", zh="乙", confirmed="本批"))]), "不存在")
    for fields, patch in (([], {}), (["bc"], {"bc": "other"}), (["zh", "zh"], {"zh": "乙"}),
                          (["zh"], {"zh": "乙", "definition": "越界"}), (["zh"], {})):
        reject(lambda: run([term()], [dict(op="update", fields=fields,
                                         term=dict(id="a", confirmed="本批", **patch))]), "fields")
    for location in ("forbidden", "aliases"):
        value = ["成本"] if location == "forbidden" else [dict(name="成本", type="dialect", deprecated=True)]
        reject(lambda: run([], [add(term(**{location: value}))]), "禁用短语")
    reject(lambda: run([], [add(term(kind="metric"))]), "新增用语问题")
    reject(lambda: run([], [add(term(bc="missing"))]), "新增用语问题")
    original = term(confirmed="原确认", note="原说明", upstream_only="保留")
    patch = dict(op="update", fields=["en", "snake", "note", "standard_ref"],
                 term=dict(id="a", en="Second", snake="second", note="本次说明",
                           standard_ref=["新裁定"], confirmed="新增确认"))
    ts = [original]
    run(ts, [patch]); before = copy.deepcopy(ts); run(ts, [patch])
    assert ts == before and ts[0]["upstream_only"] == "保留"
    assert ts[0]["confirmed"] == "原确认；新增确认" and ts[0]["note"] == "原说明；本次说明"
    assert ts[0]["standard_ref"] == ["测试裁定", "新裁定"]
    run([], [add(term(aliases=[dict(name="成本", type="standard-zh", deprecated=True)]))])
    row = dict(file="f", id="a", target="a", disposition="remove", original=term(status="proposed"))
    verify_promotions([term()], [row], {"f": []})
    reject(lambda: verify_promotions([term()], [row], {"f": [term()]}), "源未删除")
    reject(lambda: verify_promotions([], [row], {"f": []}), "没有已批准")
    reject(lambda: verify_promotions([term()], [row, row], {"f": []}), "重复提议来源")
    test_sources(reject)
    print(f"RECON 写回自测：通过（{failures} 个负向用例；定向更新、确认追加、幂等、精确转正清理）")


if __name__ == "__main__":
    import sys
    if sys.argv[1:] != ["--self-test"]:
        fail("用法：python3 glossary/apply_recon.py --self-test；写回由 build_terms.py 调用")
    self_test()
