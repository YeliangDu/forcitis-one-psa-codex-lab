"""RECON 原有自测与复审回归自测。"""
import copy

from apply_recon import *


def sample_term(tid="a", **kw):
    term = dict(id=tid, zh="甲", en="First", snake="first", kind="aggregate", scope="domain",
                bc="cost.costing", definition="测试定义", standard_ref=["测试裁定"], confidence="medium",
                aliases=[], forbidden=[], status="approved", confirmed="本批确认")
    term.update(kw)
    return term


def test_sources(reject):
    doc = read_decisions()
    changed = copy.deepcopy(doc); next(iter(changed["sources"].values()))["sha256"] = "0" * 64
    reject(lambda: read_decisions(changed, verify_hashes=True), "裁定来源已变化")
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


def test_review_sources(user, reject):
    for field, value in (("label", "伪造裁定"), ("choice", 99), ("submission", "伪造提交"), ("unanswered", True)):
        doc = copy.deepcopy(user)
        doc["sources"]["S001"]["evidence"][field] = value
        reject(lambda: read_decisions(doc), "evidence 与裁定原文不一致")
    for field, value in (("date", "2000-01-01"), ("batch", "错误批次"), ("submission", "未提交")):
        doc = copy.deepcopy(user); doc["sources"]["S001"][field] = value
        reject(lambda: read_decisions(doc), "日期/批次不一致")
    for key in ("S009", "S033", "S047", "S049", "S074", "S097"):
        doc = copy.deepcopy(user); doc["sources"][key]["evidence"] = {}
        reject(lambda: read_decisions(doc), "evidence 与裁定原文不一致")
    doc = copy.deepcopy(user); del doc["sources"]["S006"]["reviewed_note"]
    reject(lambda: read_decisions(doc), "带说明的答复")
    doc = copy.deepcopy(user); doc["sources"]["S009"]["content_sha256"] = "0" * 64
    reject(lambda: read_decisions(doc), "裁定段落哈希不一致")
    for section in ("terms", "promotions", "missing"):
        doc = copy.deepcopy(user); doc[section][0]["sources"] = ["S999"]
        reject(lambda: read_decisions(doc), "缺已核实裁定来源")
    doc = copy.deepcopy(user); doc["missing"] = []
    reject(lambda: read_decisions(doc), "逐项覆盖 60 项")
    for field, value in (("target", "absent"), ("sources", ["S032"])):
        doc = copy.deepcopy(user); doc["missing"][42][field] = value
        reject(lambda: read_decisions(doc), "与写回不一致")
    doc = copy.deepcopy(user); doc["missing"][0]["zh"] = "错误中文"
    reject(lambda: read_decisions(doc), "逐项覆盖 60 项")
    doc = copy.deepcopy(user); doc["meta"]["missing_count"] -= 1
    reject(lambda: read_decisions(doc), "逐项覆盖 60 项")
    doc = copy.deepcopy(user); doc["promotions"][0]["sources"] = ["S001"]
    reject(lambda: read_decisions(doc), "提议来源与写回裁定不一致")

def test_review_design(design, reject):
    for field, value, message in (("confirmed", "伪造来源", "确认来源不一致"),
                                  ("standard_ref", [], "standard_ref 未列裁定出处"),
                                  ("standard_ref", design["promotions"][0]["standard_ref"][0], "standard_ref 未列裁定出处")):
        doc = copy.deepcopy(design); doc["promotions"][0][field] = value
        reject(lambda: read_decisions(doc), message)
    for category, count in (("A", 65), ("B", 104), ("C", 3)):
        doc = copy.deepcopy(design); doc["meta"]["promotion_counts"] = {category: count}
        reject(lambda: read_decisions(doc), "C 类不得下结论" if category == "C" else "逐项覆盖")
    for key in ("reason", "promote"):
        doc = copy.deepcopy(design); del doc["promotions"][0][key]
        reject(lambda: read_decisions(doc), "唯一结论及原因")
    for action in ("merge_into", "replaced_by", "keep_proposed"):
        doc = copy.deepcopy(design); row = doc["promotions"][0]
        del row["promote"], row["reason"]
        row[action] = "sync_run" if action != "keep_proposed" else "待改名"
        reject(lambda: read_decisions(doc), "唯一结论及原因")
    doc = copy.deepcopy(design); doc["promotions"][0]["category"] = "C"
    reject(lambda: read_decisions(doc), "C 类不得下结论")
    doc = copy.deepcopy(design); doc["promotions"].append(doc["promotions"][0])
    reject(lambda: read_decisions(doc), "逐项覆盖")
    doc = copy.deepcopy(design); doc["terms"][0]["term"]["forbidden"] = ["data"]
    reject(lambda: read_decisions(doc), "通用词不得")
    for field in ("clause", "decision"):
        doc = copy.deepcopy(design); doc["sources"]["D3"][field] = "999"
        reject(lambda: read_decisions(doc), "evidence 与裁定原文不一致" if field == "clause" else "缺真实条款")
    doc = copy.deepcopy(design); doc["terms"][0]["term"]["id"] = "tender_project"
    reject(lambda: read_decisions(doc), "C 类不得下结论")


def test_review_outcomes(user, reject):
    original = sample_term(status="proposed")
    target = sample_term("b", zh="乙", en="Second", snake="second",
                         aliases=[dict(name="甲", type="standard-zh"), dict(name="First", type="standard-en")])
    row = dict(id="a", category="B", original=original, reason="同一概念归并", merge_into="b")
    verify_design_result({"b": target}, row, [])
    reject(lambda: verify_design_result({}, row, []), "没有已批准正式目标")
    bad = copy.deepcopy(target); bad["aliases"] = []
    reject(lambda: verify_design_result({"b": bad}, row, []), "合并旧名须作可检索别名")
    dep = {k: v for k, v in row.items() if k != "merge_into"}
    dep.update(replaced_by="b", owner="one-test", until="首次部署前")
    retained = dict(original, status="deprecated", replaced_by="b", owner="one-test", until="首次部署前")
    verify_design_result({"b": target}, dep, [retained])
    for field in ("owner", "until"):
        bad = copy.deepcopy(dep); del bad[field]
        reject(lambda: verify_design_result({"b": target}, bad, [retained]), "弃用须保留原字段")
    keep = dict(id="a", original=original, keep_proposed="待改名", reason="代码仍在使用")
    verify_design_result({}, keep, [original])
    bad = dict(keep, keep_proposed="")
    reject(lambda: verify_design_result({}, bad, [original]), "维持提议缺原因")
    reject(lambda: verify_design_result({}, keep, []), "维持提议缺原因")
    promote = dict(id="a", original=original, promote="a", reason="技术结构")
    verify_design_result({"a": sample_term()}, promote, [])
    reject(lambda: verify_design_result({"a": sample_term()}, promote, [original]), "源未删除")
    a = dict(file="f", id="a", original=original, target="b", disposition="remove", sources=["S001"])
    reject(lambda: verify_promotions([dict(target, aliases=[])], [a], {"f": []}), "zh/en 不一致")
    other_bc = dict(target, bc="platform.identity")
    reject(lambda: verify_promotions([other_bc], [a], {"f": []}), "bc 迁移缺裁定")
    reject(lambda: verify_promotions([target], [a], {"f": []}, user), "target 缺裁定支撑")


def test_review_files(user, design, reject):
    import json
    import tempfile
    from unittest.mock import patch
    import apply_recon as recon
    for file, clause in (("CODING_STANDARD.md", "CS-2.24"), ("NAMING_STANDARD.md", "§2.4")):
        source = copy.deepcopy(design["sources"]["D4"])
        source.update(file="docs/modernization/" + file, clause=clause)
        source["evidence"] = source_content(source, recon.REPO / source["file"])
        source["content_sha256"] = hashlib.sha256(source["evidence"]["text"].encode()).hexdigest()
        verify_source("规范", source, design["meta"]["card"])
        source["clause"] = "不存在的条款"
        reject(lambda: verify_source("规范", source, design["meta"]["card"]), "evidence 与裁定原文不一致")

    with tempfile.TemporaryDirectory(prefix="recon-source-") as tmp:
        root = Path(tmp).resolve()
        for key in ("S001", "S009", "S033", "S047", "S074", "S097"):
            source = copy.deepcopy(user["sources"][key]); p = root / source["file"]
            p.parent.mkdir(parents=True, exist_ok=True)
            body = (recon.REPO / source["file"]).read_text()
            if p.suffix == ".json":
                data = json.loads(body); data["未引用题"] = {"label": "无关修改"}; p.write_text(json.dumps(data, ensure_ascii=False))
            elif key == "S009":
                p.write_text(body.replace("## 落点", "## 落点\n\n落点补记，不属于用户原话"))
            elif key == "S033":
                p.write_text(body.replace("**状态**：", "**状态**：状态记录补记；"))
            elif key in ("S074", "S097"):
                data = yaml.safe_load(body)
                if key == "S074":
                    next(r for r in data["rulings"] if r["id"] != source["question"])["note"] = "未引用裁定的补记"
                else:
                    next(o for o in data["terms"] if source["question"] not in o["answers"])["note"] = "未引用操作的补记"
                p.write_text(yaml.safe_dump(data, allow_unicode=True))
            else:
                p.write_text(body + "\n# 不属于引用裁定的变动\n")
            with patch.object(recon, "REPO", root):
                verify_source(key, source, user["meta"]["card"])
                reject(lambda: verify_source(key, source, user["meta"]["card"], True), "裁定来源已变化")
            # 在源文件本身注入错误，同时更新整文件哈希，仍必须由内容检查拦截。
            if key == "S001":
                for field, value in (("label", "源裁定被改"), ("choice", 9), ("submission", "伪造"), ("unanswered", True)):
                    data = json.loads(body); data[source["question"]][field] = value
                    p.write_text(json.dumps(data)); source["sha256"] = hashlib.sha256(p.read_bytes()).hexdigest()
                    with patch.object(recon, "REPO", root):
                        reject(lambda: verify_source(key, source, user["meta"]["card"]), "evidence 与裁定原文不一致")


def review_self_test():
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
    reject(lambda: read_decisions(filename="missing-recon.yaml"), "决策数据缺失或越界")
    user = read_decisions()
    design = read_decisions(filename="decisions-recon-b.yaml")
    assert {f"S{n:03d}" for n in range(1, 98)} <= set(user["sources"])
    test_review_sources(user, reject)
    test_review_design(design, reject)
    test_review_outcomes(user, reject)
    test_review_files(user, design, reject)
    test_intent_clauses(user, reject)
    test_round2_fields(design, reject)
    compatibility_self_test()
    print(f"RECON 复审/机制自测：通过（{failures} 个新增负向用例；原 97 来源仍在；四种结论；无关变动可通过）")


def compatibility_self_test():
    import contextlib
    import io
    import os
    import runpy
    from unittest.mock import patch
    import apply_recon as recon
    upstream = []
    class Captured(Exception): pass
    def capture(terms):
        upstream.extend(copy.deepcopy(terms))
        raise Captured
    previous = Path.cwd()
    try:
        os.chdir(HERE.parent)
        with patch.object(recon, "apply", capture), contextlib.redirect_stdout(io.StringIO()):
            try: runpy.run_path(str(HERE / "build_terms.py"))
            except Captured: pass
    finally:
        os.chdir(previous)
    assert upstream, "须在 RECON 写回前截取真实生成输入"
    doc = read_decisions()
    expected = {t["id"]: copy.deepcopy(t) for t in upstream}
    for operation in doc["terms"]:
        entry = operation["term"]
        if operation["op"] == "add":
            assert entry["id"] not in expected
            expected[entry["id"]] = copy.deepcopy(entry)
        else:
            target = expected[entry["id"]]
            for field in operation["fields"]:
                if field in ("aliases", "forbidden", "standard_ref"):
                    target[field] += [v for v in entry[field] if v not in target[field]]
                elif field == "note": target[field] = apply_nm.append_text(target.get(field, ""), entry[field])
                else: target[field] = entry[field]
            target["confirmed"] = apply_nm.append_text(target.get("confirmed", ""), entry["confirmed"])
    contexts = yaml.safe_load((HERE / "bcs.yaml").read_text(encoding="utf-8"))["bcs"]
    apply_operations(upstream, doc["terms"], proposed=[], contexts=contexts)
    ids = {r["target"] for r in doc["promotions"]} | {r["target"] for r in doc["missing"] if r["target"]}
    actual = {t["id"]: t for t in upstream}
    class Dumper(yaml.SafeDumper):
        def ignore_aliases(self, data): return True
    Dumper.add_representer(__import__("collections").OrderedDict, lambda d, v: d.represent_dict(v.items()))
    emit = lambda values: yaml.dump(values, Dumper=Dumper, allow_unicode=True, sort_keys=False, width=200).encode()
    assert emit([actual[i] for i in sorted(ids)]) == emit([expected[i] for i in sorted(ids)])
    print("RECON 生成时点对照：通过（A66、漏登60，期望由当前决定与写回前输入计算，不锁最终词表）")


def test_intent_clauses(user, reject):
    import tempfile
    from unittest.mock import patch
    import apply_recon as recon
    source = user['sources']['S025']
    with tempfile.TemporaryDirectory(prefix='recon-intent-') as tmp:
        root = Path(tmp).resolve(); p = root / source['file']; p.parent.mkdir(parents=True)
        body = (recon.REPO / source['file']).read_text()
        p.write_text(body.replace('F-2 含审批状态', 'F-2 与本批词条无关的记录，含审批状态'))
        with patch.object(recon, 'REPO', root):
            verify_source('S025', source, user['meta']['card'])
        p.write_text(body.replace('证据本身只存内容', '证据本身不再只存内容'))
        with patch.object(recon, 'REPO', root):
            reject(lambda: verify_source('S025', source, user['meta']['card']), 'evidence 与裁定原文不一致')
    doc = copy.deepcopy(user); doc['sources']['S025']['evidence']['rulings'] = []
    reject(lambda: read_decisions(doc), 'evidence 与裁定原文不一致')


def test_round2_fields(design, reject):
    for field in ("zh", "definition", "bc", "scope", "en", "snake", "kind"):
        doc = copy.deepcopy(design); doc["terms"][0]["term"][field] = "越界修改"
        reject(lambda: read_decisions(doc), "逐字段列原因与来源" if field != "definition" else "字段变更缺原因或值不一致")
    for field in ("technical", "technical_reason"):
        doc = copy.deepcopy(design); del doc["promotions"][0][field]
        reject(lambda: read_decisions(doc), "technical:true 及理由")
    doc = copy.deepcopy(design); doc["promotions"][0]["semantic_class"] = "business"
    reject(lambda: read_decisions(doc), "业务含义只能转交 C")
    for field in ("reason", "sources"):
        doc = copy.deepcopy(design); del doc["promotions"][0]["field_changes"]["definition"][field]
        reject(lambda: read_decisions(doc), "字段变更缺原因" if field == "reason" else "缺已核实裁定来源")
    doc = copy.deepcopy(design); doc["promotions"][0]["field_changes"]["definition"]["sources"] = ["D4"]
    reject(lambda: read_decisions(doc), "不得由设计方修改")
    doc = copy.deepcopy(design); row = doc["promotions"][0]; entry = doc["terms"][0]["term"]
    row["field_changes"]["zh"] = dict(**{"from": row["original"]["zh"], "to": "自定中文"}, reason="设计自行更名", sources=["D4"])
    entry["zh"] = "自定中文"
    reject(lambda: read_decisions(doc), "不得由设计方修改")
    row["field_changes"]["zh"]["sources"] = ["U_R2"]
    reject(lambda: read_decisions(doc), "会话指令未授权该字段")
    doc = copy.deepcopy(design); row = next(r for r in doc["promotions"] if r["id"] == "supersede")
    row["field_changes"]["en"]["to"] = "自造英文"
    reject(lambda: read_decisions(doc), "字段变更缺原因或值不一致")
    for key in ("D3", "D4"):
        assert "ADR-0053" in confirmation([key], design["sources"])
    source = dict(design["sources"]["D4"], file="docs/modernization/NAMING_STANDARD.md", clause="§2.4")
    assert confirmation(["S"], {"S": source}) == "设计方定稿，NAMING_STANDARD.md §2.4，NAM-1017-GLO-07"


if __name__ == "__main__":
    self_test()
    review_self_test()
