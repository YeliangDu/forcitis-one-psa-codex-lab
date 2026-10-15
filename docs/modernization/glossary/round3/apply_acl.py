"""ACL 归类的写回规划（用户 2026-10-15 在固定确认页逐组提交，glossary/decisions-round3-2026-10-15.json，键 ACL-<单子>-<编号>）。
glossary/build_terms.py 调用 apply(terms) 给正式词条写使用范围；round3/writeback.py 调用 plan() 改提议文件与 acl/acl-classification.json。
单子：acl/ACL-DEFFIX（领域用语，按建议改写定义）、ACL-TRANSITION（ACL 过渡期）、ACL-LONGTERM（ACL 长期）、ACL-UNDECIDED（待定，按推荐选项的归类）。
「同意建议」取推荐选项的归类；「保持原样」即保留为领域用语，但组织同步 7 条以 ADR-0039 为准归集成层（用户 2026-10-15，
intent 2026-10-15-171549-one-coordinator），「待放行离职」留在 workforce 作领域用语。
暂缓：ACL-TRANSITION G58、G59（门禁 R-1b-0 的 G06、G07）待 one-coordinator 定顺序，不进决定记录，归类不改。"""
import glob, json, os

HERE = os.path.dirname(os.path.abspath(__file__))
ACL_DIR = os.path.join(HERE, "..", "acl")
DECISIONS = os.path.join(HERE, "..", "decisions-round3-2026-10-15.json")
CLASSIFICATION = os.path.join(ACL_DIR, "acl-classification.json")
HELD = {"ACL-TRANSITION-G58", "ACL-TRANSITION-G59"}
ORG_SYNC = {
    "frontend-ui.yaml#held", "frontend-ui.yaml#release", "frontend-ui.yaml#org_sync",
    "organization_workforce.yaml#workforce__sync_diff", "organization_workforce.yaml#workforce__login_name",
    "organization_workforce.yaml#workforce__relationship", "organization_workforce.yaml#separation_observed_at",
}
ORG_SYNC_BC = "platform.integration"
SEPARATION_HOLD = "architecture-acl.yaml#separation_hold"
CARD_CLASS = {"ACL-DEFFIX": "领域", "ACL-TRANSITION": "ACL过渡期", "ACL-LONGTERM": "ACL长期"}


def recommended_class(card, item):
    if card in CARD_CLASS:
        return CARD_CLASS[card]
    label = next(o for o in item["options"] if o.get("recommended"))["label"]
    for word, cls in (("长期", "ACL长期"), ("过渡期", "ACL过渡期"), ("领域", "领域")):
        if word in label:
            return cls
    raise SystemExit(f"{card} {item['code']}：推荐选项「{label}」看不出归类")


def scope_of(cls):
    if cls.startswith("ACL"):
        return {"scope": "acl", "acl_class": cls[3:]}
    if cls.startswith("领域"):
        return {"scope": "domain"}
    raise SystemExit(f"归类 {cls} 不是领域或 ACL")


def plan():
    """返回 key（terms#id 或 <提议文件>#id）→ {class, decision, bc?, definition?}，只含用户已答复的条目。"""
    decisions = json.load(open(DECISIONS, encoding="utf-8"))
    out, seen = {}, set()
    for path in sorted(glob.glob(os.path.join(ACL_DIR, "ACL-*.json"))):
        doc = json.load(open(path, encoding="utf-8"))
        for item in doc["items"]:
            dk = f"{doc['card']}-{item['code']}"
            seen.add(dk)
            if dk in HELD:
                if dk in decisions:
                    raise SystemExit(f"{dk} 暂缓写回，不应出现在决定记录中")
                continue
            d = decisions.get(dk)
            if not d:
                raise SystemExit(f"{dk}（{item['zh']}）没有答复")
            key = item["key"]
            if d["choice"] == "approve":
                cls = recommended_class(doc["card"], item)
            elif d["choice"] == "keep":
                cls = "ACL过渡期" if key in ORG_SYNC else "领域"
            else:
                raise SystemExit(f"{dk}：ACL 归类不支持选择 {d['choice']}")
            row = {"class": cls, "decision": dk}
            if key in ORG_SYNC:
                if d["choice"] != "keep":
                    raise SystemExit(f"{dk}：组织同步条目的答复不是「保持原样」，ADR-0039 的处理需重新确认")
                row.update(bc=ORG_SYNC_BC, ruling="intent 2026-10-15-171549-one-coordinator（以 ADR-0039 为准）")
            if doc["card"] == "ACL-DEFFIX" and d["choice"] == "approve":
                row["definition"] = item["definition"]
            out[key] = row
    extra = sorted(k for k in decisions if k.startswith("ACL-") and k not in seen)
    if extra:
        raise SystemExit("决定记录中有不在 ACL 单子里的键：" + "、".join(extra))
    missing = sorted(ORG_SYNC - set(out))
    if missing:
        raise SystemExit("组织同步条目不在 ACL 单子里：" + "、".join(missing))
    out[SEPARATION_HOLD] = {"class": "领域", "decision": "intent 2026-10-15-171549-one-coordinator（待放行离职留 workforce）"}
    return out


def classification():
    """acl/acl-classification.json：key → 归类（含预判与已答复的结果）。"""
    return {r["key"]: r for r in json.load(open(CLASSIFICATION, encoding="utf-8"))}


def apply(terms, id_map):
    """build_terms.py 调用：按归类给正式词条写 scope（及 acl_class），ACL 答复改写的定义与 bc 一并写入。
    id_map：第三轮转正式时提议 id → 正式 id（apply_round3.plan()["id_map"]）。返回 (有归类条数, 没有归类的 id 列表)。"""
    rows = classification()
    decided = plan()
    byid = {t["id"]: t for t in terms}
    hit = set()
    for key, row in rows.items():
        file, term_id = key.split("#", 1)
        if file == "question":
            continue
        tid = term_id if file == "terms" else id_map.get(term_id)
        if not tid or tid not in byid:
            continue
        t = byid[tid]
        d = decided.get(key, {})
        t.update(scope_of(d.get("class") or row["class"]))
        if d.get("bc"):
            t["bc"] = d["bc"]
        if d.get("definition"):
            t["definition"] = d["definition"]
        hit.add(tid)
    return len(hit), sorted(set(byid) - hit)
