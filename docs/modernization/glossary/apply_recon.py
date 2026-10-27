"""NAM-1017-GLO-06/07 裁定与设计定稿写回；按来源及 A/B 分类逐项核验。"""
import copy
import hashlib
import json
import re
from collections import Counter
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
        if not isinstance(term[field], str) or not term[field].strip(): fail(f"{term['id']} 的 {field} 必须为非空文本")
    for field in ("aliases", "forbidden", "standard_ref"):
        if not isinstance(term[field], list): fail(f"{term['id']} 的 {field} 必须为列表")
    if not term["standard_ref"] or term["snake"] != snake(term["en"]): fail(f"{term['id']} 缺依据或 snake 不由 en 派生")
def source_ref(source):
    return source["file"] + ("#" + source.get("question", source.get("clause", ""))
                             if source.get("question") or source.get("clause") else "")
def clause_text(path, clause):
    body = path.read_text(encoding="utf-8")
    if path.name.startswith("ADR-"):
        if "## 决定\n" not in body or not re.fullmatch(r"[1-9]\d*(?:\.[1-9]\d*)*", clause):
            return ""
        section = body.split("## 决定\n", 1)[-1].split("\n## ", 1)[0]
        stack, selected = [], []
        for line in section.splitlines():
            match = re.match(r"^( *)(\d+)\. ", line)
            if match:
                indent, number = len(match[1]), match[2]
                while stack and stack[-1][0] >= indent:
                    stack.pop()
                stack.append((indent, number))
            current = ".".join(n for _, n in stack)
            if current == clause or current.startswith(clause + "."):
                selected.append(line)
        return "\n".join(selected).strip()
    pattern = (r"^.*(?:\*\*|\| )" + re.escape(clause) + r"(?: |\*\*).*"
               if path.name == "CODING_STANDARD.md" else
               r"^#{2,4} " + re.escape(clause.removeprefix("§")) + r"(?: |\.).*")
    if path.name not in ("CODING_STANDARD.md", "NAMING_STANDARD.md"):
        return ""
    match = re.search(pattern, body, re.M)
    if not match:
        return ""
    if path.name == "CODING_STANDARD.md" and match[0].startswith("|"):
        return match[0]
    rest = body[match.end():]
    end = re.search(r"^#{2,4} |^\*\*CS-", rest, re.M)
    return (match[0] + (rest[:end.start()] if end else rest)).strip()
def source_content(source, path):
    body, question = path.read_text(encoding="utf-8"), source.get("question", "")
    if path.suffix == ".json":
        return json.loads(body).get(question)
    if path.name == "decisions-nm.yaml":
        data = yaml.safe_load(body)
        return {"answer": next((a for a in data["answers"] if a["key"] == question), None),
                "terms": [o for o in data["terms"] if question in o["answers"]]}
    if path.name == "rulings.yaml":
        return next((r for r in yaml.safe_load(body)["rulings"] if r["id"] == question), None)
    if path.name == "CAPINFO_NEWTERMS.md":
        row = next((l for l in body.splitlines() if l.startswith(f"| {question} |")), "")
        evidence = next((l for l in body.splitlines() if l.startswith("依据：")), "")
        date = re.search(r"状态：[^\n]*｜(\d{4}-\d{2}-\d{2})", body)
        submission = re.search(r"CAPINFO-\d{8}-\d{6}-[a-z0-9]+", evidence)
        return dict(row=row, questions=evidence.split(" 第 ")[-1].split(" 题。")[0],
                    submission=submission[0] if submission else "",
                    at=date[1] if date else "")
    if path.name.startswith("ADR-") or source.get("type") == "design":
        return {"clause": source.get("selector", source.get("clause", "")),
                "text": clause_text(path, source.get("selector", source.get("clause", "")))}
    match = re.search(r"^## 原话\n(.*?)(?=^## |\Z)", body, re.M | re.S)
    entry = {"text": match[1].strip() if match else ""}
    if source.get("ruling_clauses"):
        clauses = body.split("## 落点\n", 1)[-1].split("；")
        entry["rulings"] = [next((c.strip() for c in clauses if c.strip().startswith(p)), "") for p in source["ruling_clauses"]]
    return entry
def verify_source(key, source, card, verify_hashes=False):
    path = (REPO / source["file"]).resolve()
    if not path.is_relative_to(REPO) or not path.is_file(): fail(f"{key} 裁定来源不存在或越界")
    if verify_hashes and hashlib.sha256(path.read_bytes()).hexdigest() != source["sha256"]: fail(f"{key} 裁定来源已变化，请重新核对原话")
    if source.get("type", "user") not in ("user", "design"): fail(f"{key} 未知确认来源")
    actual = source_content(source, path)
    if not actual or not source.get("evidence") or actual != source["evidence"]: fail(f"{key} evidence 与裁定原文不一致或缺失")
    if "text" in actual and not actual["text"]: fail(f"{key} 缺真实条款依据或原话")
    if "text" in actual and hashlib.sha256(actual["text"].encode()).hexdigest() != source.get("content_sha256"): fail(f"{key} 裁定段落哈希不一致")
    if source.get("type") == "design":
        adr = REPO / "docs/modernization/adr/src/ADR-0053.md"
        if (source.get("task") != card or not clause_text(adr, source.get("decision", ""))
                or not actual.get("clause")):
            fail(f"{key} 缺真实条款依据或任务号不一致")
        return
    if path.suffix == ".json":
        stamp = actual.get("submitted", actual.get("submitted_at", actual.get("at", "")))
        question = source.get("question", "")
        batch = question.split("~", 1)[0]
        if "~" in question and actual.get("no") != question.split("~")[1]: fail(f"{key} 题号不一致")
        if (actual.get("unanswered") or actual.get("choice") is None
                or source.get("submission") != actual.get("submission")
                or source.get("batch") != batch or source.get("date") != stamp[:10]):
            fail(f"{key} 缺提交、未作答或日期/批次不一致")
        if actual.get("note") not in (None, "", "整组同意建议") and source.get("reviewed_note") != actual["note"]: fail(f"{key} 带说明的答复须逐字复核说明")
    elif path.parent.name == "intent":
        if source.get("date") != path.name[:10] or source.get("batch") != path.stem: fail(f"{key} 原话日期/批次不一致")
    elif path.name == "decisions-nm.yaml":
        if (not actual["answer"] or not actual["terms"] or source.get("submission") != actual["answer"]["submission"]
                or source.get("date") not in actual["terms"][0]["term"]["confirmed"]):
            fail(f"{key} 并入裁定缺提交或日期不一致")
    elif path.name == "CAPINFO_NEWTERMS.md":
        if not actual["row"] or source.get("submission") != actual["submission"] or source.get("date") != actual["at"]: fail(f"{key} 交接条目缺来源或日期不一致")
    elif path.name.startswith("ADR-"):
        if not re.search(r"^- \*\*日期\*\*：" + re.escape(source.get("date", "")) + r"$", path.read_text(encoding="utf-8"), re.M): fail(f"{key} ADR 日期不一致")
    elif source.get("origin") == "session_instruction":
        if not all(source.get(k) and source[k] in actual["text"] for k in ("date", "batch")): fail(f"{key} 会话指令缺日期或任务号")
    elif source.get("date", "") not in actual.get("source", ""):
        fail(f"{key} 裁定日期不一致")
def confirmation(keys, sources):
    return "；".join(dict.fromkeys(
        (f"设计方定稿，ADR-0053 决定 {sources[k]['decision']} 条，{sources[k]['task']}"
         if Path(sources[k]['file']).name == "ADR-0053.md" else
         f"设计方定稿，{Path(sources[k]['file']).name} {sources[k]['clause']}，{sources[k]['task']}")
        if sources[k].get("type") == "design" else
        f"{sources[k]['date']}（用户裁定，{sources[k]['batch']}，"
        f"{('提交 ' + sources[k]['submission']) if sources[k].get('submission') else source_ref(sources[k])}）"
        for k in keys))
def require_sources(keys, sources, label):
    if not isinstance(keys, list) or not keys or len(keys) != len(set(keys)) or set(keys) - sources.keys(): fail(f"{label} 缺已核实裁定来源")
def read_decisions(doc=None, filename="decisions-recon.yaml", *, verify_hashes=False):
    if doc is None:
        path = (HERE / filename).resolve()
        if not path.is_relative_to(HERE) or not path.is_file(): fail(f"决策数据缺失或越界：{filename}")
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    sources = doc["sources"]
    for key, source in sources.items():
        verify_source(key, source, doc["meta"]["card"], verify_hashes)
    for operation in doc["terms"] + [dict(sources=r["sources"], term=r) for r in doc["promotions"] if r.get("category") == "B"]:
        keys, term = operation["sources"], operation["term"]
        require_sources(keys, sources, term["id"])
        if term.get("confirmed") != confirmation(keys, sources): fail(f"{term['id']} 确认来源不一致")
        if not isinstance(term.get("standard_ref"), list) or not all(source_ref(sources[k]) in term["standard_ref"] for k in keys):
            fail(f"{term['id']} standard_ref 未列裁定出处")
    for row in doc["promotions"] + doc.get("missing", []):
        require_sources(row.get("sources"), sources, row.get("id", row.get("zh")))
    common = set(yaml.safe_load((HERE / "terms.yaml").read_text(encoding="utf-8"))["common_words"])
    common.update(yaml.safe_load((REPO / "backend/glossary/terms.yaml").read_text(encoding="utf-8"))["common_words"])
    common.update(term_rules.GENERIC_FIRST + term_rules.GENERIC_LAST)
    for op in doc["terms"]:
        if any(sources[k].get("type") == "design" for k in op["sources"]):
            if {n.lower() for n in apply_nm.banned_names(op["term"])} & common: fail(f"{op['term']['id']} 通用词不得写入 forbidden 或禁用别名")
    verify_coverage(doc)
    verify_source_keys(doc)
    return doc
def verify_source_keys(doc):
    """来源键须连续 S001…Snnn，且每个来源至少被一项写回、漏登或转正引用；孤立来源与跳号即失败。"""
    keys = list(doc["sources"])
    if not all(re.fullmatch(r"S\d{3}", k) for k in keys): return  # 设计方决定文件用 D*/U_* 键，另有校验
    expected = [f"S{n:03d}" for n in range(1, len(keys) + 1)]
    if sorted(keys) != expected: fail("来源键须连续编号 S001…S%03d，不得跳号" % len(keys))
    used = set()
    for op in doc.get("terms", []): used.update(op["sources"])
    for row in doc.get("promotions", []) + doc.get("missing", []): used.update(row.get("sources") or [])
    orphans = sorted(set(keys) - used)
    if orphans: fail("来源未被任何写回、漏登或转正引用：" + "、".join(orphans[:5]))
def verify_missing(doc, audit):
    if "missing" in doc:
        rows, expected = doc["missing"], audit["missing_terms"]
        if (doc["meta"].get("missing_count") != len(expected) or len(rows) != len(expected)
                or {r["no"] for r in rows} != set(range(1, len(expected) + 1))
                or any(r["zh"] != expected[r["no"] - 1]["term"]["zh"] for r in rows)):
            fail(f"漏登对照须逐项覆盖 {len(expected)} 项")
        ops = {o["term"]["id"]: o for o in doc["terms"]}
        for row in rows:
            target = ops.get(row.get("target"))
            if row["result"] == "registered":
                if (not target or row["zh"] != target["term"].get("zh")
                        or set(row["sources"]) != set(target["sources"])):
                    fail(f"漏登 {row['no']} target、zh、sources 与写回不一致")
                if target["term"].get("status") != "approved": fail(f"漏登 {row['no']} 登记状态不一致")
            elif row["result"] != "not_registered" or not row.get("reason"):
                fail(f"漏登 {row['no']} 未登记须有原因")
def verify_coverage(doc):
    audit = json.loads((HERE / "arch-naming/reconcile-decided-slim.json").read_text(encoding="utf-8"))
    verify_missing(doc, audit)
    counts = doc["meta"].get("promotion_counts", {})
    if not counts or set(counts) - {"A", "B"} or any(type(n) is not int or n < 1 for n in counts.values()): fail("分类声明只能为 A 或 B 的正整数，C 类不得下结论")
    classified = audit["proposed"]
    expected = {(r["file"], r["id"]): r for r in classified if r["category"] in counts}
    label = "、".join(f"{c} 类 {n} 项" for c, n in counts.items())
    rows = doc["promotions"]
    if (Counter(r["category"] for r in expected.values()) != Counter(counts)
            or len(rows) != sum(counts.values())
            or {(r["file"], r["id"]) for r in rows} != set(expected)):
        fail(f"提议对照须逐项覆盖 {label}，与分类数及总数一致")
    for row in rows:
        original = expected[(row["file"], row["id"])]
        if (row.get("category", "A") != original["category"] or row["original"] != original["original"]
                or row["original"]["id"] != row["id"]):
            fail(f"{row['id']} 分类或原提议快照不一致，C 类不得下结论")
        if row.get("category") == "B":
            choices = {"promote", "merge_into", "replaced_by", "keep_proposed"} & row.keys()
            if len(choices) != 1 or not isinstance(row.get("reason"), str) or not row["reason"].strip(): fail(f"{row['id']} 须有唯一结论及原因")
            choice = next(iter(choices))
            if not isinstance(row[choice], str) or not row[choice].strip(): fail(f"{row['id']} 结论缺目标或原因")
    operations = {o["term"]["id"]: o for o in doc["terms"]}
    c_ids = {r["id"] for r in classified if r["category"] == "C"}
    if c_ids & operations.keys(): fail("C 类不得下结论或写回")
    for row in rows:
        target_id = row.get("target", row.get("promote", row.get("merge_into", row.get("replaced_by"))))
        operation = operations.get(target_id)
        if row.get("category", "A") == "A":
            if any(doc["sources"][k].get("type") == "design" for k in row["sources"]): fail(f"{row['id']} A 类必须走用户裁定通道")
            if operation and not set(row["sources"]) <= set(operation["sources"]): fail(f"{row['id']} 提议来源与写回裁定不一致")
        elif not all(doc["sources"].get(k, {}).get("type") == "design" for k in row["sources"]):
            fail(f"{row['id']} B 类必须走设计方通道")
        elif operation and set(row["sources"]) != set(operation["sources"]):
            fail(f"{row['id']} 提议来源与设计方写回不一致")
        if "promote" in row and (row["promote"] != row["id"] or not operation or operation["op"] != "add"): fail(f"{row['id']} 转正必须保留 id 并有新增词条操作")
        if row.get("escalate_to") and (row["escalate_to"] != "C" or "keep_proposed" not in row): fail(f"{row['id']} 业务含义只能转交 C 并维持提议")
        if row.get("category") == "B":
            if row.get("semantic_class") not in ("technical", "business"): fail(f"{row['id']} 缺逐条语义分类")
            if row["semantic_class"] == "business" and (row.get("escalate_to") != "C" or "keep_proposed" not in row): fail(f"{row['id']} 业务含义只能转交 C 并维持提议")
            if "keep_proposed" not in row:
                if row.get("technical") is not True or not row.get("technical_reason", "").strip(): fail(f"{row['id']} 设计方转正须声明 technical:true 及理由")
                if not row["original"].get("standard_ref"): fail(f"{row['id']} 缺实质出处，须维持提议")
            if "promote" in row:
                verify_design_fields(operation["term"], row, doc["sources"])
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
def apply_operations(terms, operations, *, proposed, contexts, allow_homonyms=False):
    result = copy.deepcopy(terms)
    byid = {t["id"]: t for t in result}
    if len(byid) != len(result): fail("正式词表 id 重复")
    touched, candidates = set(), {}
    counts = {"added": 0, "updated": 0}
    for operation in operations:
        entry = copy.deepcopy(operation["term"])
        if operation["op"] == "add":
            validate(entry)
        elif not isinstance(entry.get("id"), str) or not entry["id"].strip():
            fail("操作 id 必须为非空文本")
        tid = entry["id"]
        if tid in touched: fail(f"重复操作 {tid}")
        touched.add(tid)
        action = operation["op"]
        if action == "add":
            if tid in byid: fail(f"新增 {tid} 已存在，不覆盖概念")
            for other in [*result, *proposed]:
                for field in ("id", "snake"):
                    if entry[field] == other.get(field):
                        if (allow_homonyms and field == "snake" and entry["bc"] != other.get("bc")
                                and other["id"] in entry.get("homonyms", [])
                                and tid in other.get("homonyms", [])):
                            continue
                        fail(f"新增 {tid} 的 {field} 与存量 {other['id']} 重复")
            result.append(entry)
            byid[tid] = entry
            candidates[tid] = apply_nm.banned_names(entry)
            counts["added"] += 1
        elif action == "update":
            if tid not in byid: fail(f"更新目标 {tid} 不存在")
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
    if new: fail("新增用语问题：" + "；".join(f"{k} {after[k]}" for k in sorted(new)))
    terms[:] = result
    return counts

def verify_promotions(terms, promotions, proposed_by_file, doc=None):
    byid = {t["id"]: t for t in terms}
    seen = set()
    for row in promotions:
        key = (row["file"], row["id"])
        if key in seen: fail(f"重复提议来源 {key}")
        seen.add(key)
        if row.get("category") == "B":
            verify_design_result(byid, row, proposed_by_file[row["file"]], doc["sources"] if doc else None)
            continue
        target = byid.get(row["target"])
        if not target or target.get("status") != "approved": fail(f"{key} 没有已批准正式目标")
        original = row["original"]
        if original["id"] != row["id"]: fail(f"{key} 原条目 id 不一致")
        rows = [t for t in proposed_by_file[row["file"]] if t["id"] == row["id"]]
        if row["disposition"] == "remove":
            if any(original[k] != target[k] and original[k] not in {a["name"] for a in target["aliases"]}
                   for k in ("zh", "en")):
                fail(f"{key} original 与 target 的 zh/en 不一致且未保留别名")
            if original["bc"] != target["bc"]:
                change = row.get("bc_change", {})
                if (not doc or change.get("from") != original["bc"] or change.get("to") != target["bc"]
                        or change.get("source") not in row["sources"]
                        or target["bc"] not in str(doc["sources"][change["source"]]["evidence"])):
                    fail(f"{key} original 与 target 的 bc 迁移缺裁定支撑")
            if doc and not any(
                    source_ref(doc["sources"][k]) in target["standard_ref"]
                    or any(all(o["term"].get(f) == target.get(f) for f in ("id", "zh", "en", "bc"))
                           for o in doc["sources"][k]["evidence"].get("terms", []))
                    for k in row["sources"]):
                fail(f"{key} target 缺裁定支撑")
            if rows: fail(f"{key} 已转正但提议源未删除")
        elif row["disposition"] == "retain_deprecated":
            if original.get("status") != "deprecated" or rows != [original]: fail(f"{key} 旧名兼容条目已漂移")
            if original.get("replaced_by") != row["target"]: fail(f"{key} 弃用条目指向错误目标")
        else:
            fail(f"{key} 未知清理方式")

def verify_design_fields(target, row, sources=None):
    original = row["original"]
    metadata = set() if "keep_proposed" in row else {"status", "confirmed", "standard_ref", "note"}
    changed = {k for k in set(original) | set(target) if k not in metadata and original.get(k) != target.get(k)}
    declarations = row.get("field_changes", {})
    if set(declarations) != changed: fail(f"{row['id']} 转正字段与原提议不一致，须逐字段列原因与来源")
    for field, change in declarations.items():
        if (field not in {"zh", "definition", "en", "snake", "kind", "bc", "scope", "used_by"}
                or change.get("from") != original.get(field) or change.get("to") != target.get(field)
                or not isinstance(change.get("reason"), str) or not change["reason"].strip()): fail(f"{row['id']} {field} 字段变更缺原因或值不一致")
        require_sources(change.get("sources"), sources or {}, row["id"] + ":" + field)
        if field in {"zh", "definition"} and any(sources[k].get("type", "user") != "user" for k in change["sources"]): fail(f"{row['id']} {field} 不得由设计方修改，须用户指令")
        for source in (sources[k] for k in change["sources"]):
            if source.get("origin") == "session_instruction" and (field not in source.get("field_authorizations", {}).get(row["id"], []) or row["id"] not in source["evidence"]["text"]): fail(f"{row['id']} {field} 会话指令未授权该字段")

def verify_design_result(byid, row, entries, sources=None):
    rows = [t for t in entries if t["id"] == row["id"]]
    if "keep_proposed" in row:
        if len(rows) != 1 or not row["keep_proposed"].strip(): fail(f"{row['id']} 维持提议缺原因或源条目已漂移")
        verify_design_fields(rows[0], row, sources)
        return
    choice = next(k for k in ("promote", "merge_into", "replaced_by") if k in row)
    target = byid.get(row[choice])
    if not target or target.get("status") != "approved": fail(f"{row['id']} 没有已批准正式目标")
    if choice == "promote": verify_design_fields(target, row, sources)
    if choice == "replaced_by":
        expected = dict(row["original"], status="deprecated", replaced_by=row[choice],
                        until=row.get("until"), owner=row.get("owner"))
        if not expected["until"] or not expected["owner"] or rows != [expected]: fail(f"{row['id']} 弃用须保留原字段并列 replaced_by、until、owner")
    elif rows:
        fail(f"{row['id']} 已转正或合并但提议源未删除")
    if choice == "merge_into":
        aliases = {a["name"] for a in target["aliases"] if a["type"] not in
                   ("dialect", "old-code", "legacy-code", "legacy-table", "forbidden")}
        for field in ("zh", "en"):
            if row["original"][field] != target[field] and row["original"][field] not in aliases: fail(f"{row['id']} 合并旧名须作可检索别名，不全局禁用")

def apply(terms):
    doc = read_decisions()
    contexts = yaml.safe_load((HERE / "bcs.yaml").read_text(encoding="utf-8"))["bcs"]
    proposed_by_file = {str(p.relative_to(REPO)): (yaml.safe_load(p.read_text(encoding="utf-8")) or {}).get("terms") or []
                        for p in sorted((REPO / "backend/glossary/proposed").glob("*.yaml"))}
    counts = apply_operations(terms, doc["terms"], contexts=contexts,
                              proposed=[t for rows in proposed_by_file.values() for t in rows])
    verify_promotions(terms, doc["promotions"], proposed_by_file, doc)
    if doc["meta"].get("design_decisions"):
        design = read_decisions(filename=doc["meta"]["design_decisions"])
        rows = design["promotions"]
        additions = {r["promote"] for r in rows if "promote" in r}
        targets = additions | {r[k] for r in rows for k in ("merge_into", "replaced_by") if k in r}
        if ({op["term"]["id"] for op in design["terms"] if op["op"] == "add"} != additions
                or any(op["term"]["id"] not in targets for op in design["terms"])):
            fail("设计方操作必须逐项对应 B 类结论，不得操作 C 类或无关词条")
        extra = apply_operations(terms, design["terms"], contexts=contexts, allow_homonyms=True,
                                 proposed=[t for entries in proposed_by_file.values() for t in entries])
        verify_promotions(terms, rows, proposed_by_file, design)
        counts = {k: counts[k] + extra[k] for k in counts}
    return counts

if __name__ == "__main__":
    import sys
    if sys.argv[1:] == ["--verify"]:
        for filename in ("decisions-recon.yaml", "decisions-recon-b.yaml"):
            read_decisions(filename=filename, verify_hashes=True)
        print("RECON 来源内容及整文件审计哈希：通过")
    elif sys.argv[1:] == ["--self-test"]:
        from apply_recon_selftest import self_test, review_self_test
        self_test()
        review_self_test()
    else:
        fail("用法：apply_recon.py --self-test | --verify；写回由 build_terms.py 调用")
