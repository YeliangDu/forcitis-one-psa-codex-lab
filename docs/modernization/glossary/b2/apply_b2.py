"""B2：词条类别（kind）统一为 DDD 战术分类（one-coordinator 2026-10-15 认可的方案，用户 2026-10-15 在决策页 B1-KIND 逐条确认 63 条）。
glossary/build_terms.py 调用 apply(terms)；backend/glossary/proposed/*.yaml 由 b2/writeback.py 一次性改写（同一套规则）。
规则：
- 类别只取 DDD_KINDS 十种（error_code 另列）；混用的取值合并：value→value_object、enum→state、event→domain_event、action→command、rule / policy→business_rule；
- document、attribute、concept、subdomain、module、system 以及部分 entity 按 b2/kind-judgement.json 逐条判定：
  bcs 移出词条（归 bcs.yaml，其英文词由 Glossary.java 读 bcs.yaml 登记）、attribute_of 挂到 owner 词条的 attributes（英文词与中文名同样登记）、
  common_word 移出词条、bc_description 写进 bcs.yaml 对应 bc 的描述（提议条目，由 writeback.py 处理）。
- 判定表里的 old_kind 与词条现状不符即失败，不猜测。"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
DDD_KINDS = ("aggregate", "entity", "value_object", "domain_event", "command", "business_rule", "read_model", "metric", "role", "state")
KIND_MAP = {"value": "value_object", "enum": "state", "event": "domain_event", "action": "command", "rule": "business_rule", "policy": "business_rule"}
MOVED = ("bcs", "attribute_of", "common_word", "bc_description")
CONFIRMED = "2026-10-15（用户，B2 类别判定）"


def judgement():
    return json.load(open(os.path.join(HERE, "kind-judgement.json"), encoding="utf-8"))["items"]


def new_kind(entry, judged):
    """返回类别（DDD 取值或 MOVED 之一）；error_code 与已是 DDD 取值的不动。"""
    kind = entry["kind"]
    if judged is not None:
        if judged["old_kind"] != kind:
            raise SystemExit(f"{entry['id']}：判定表的原类别 {judged['old_kind']} 与词条现状 {kind} 不符")
        return judged["new_kind"]
    if kind in KIND_MAP:
        return KIND_MAP[kind]
    if kind in DDD_KINDS or kind == "error_code":
        return kind
    raise SystemExit(f"{entry['id']}：类别 {kind} 不在 DDD 分类里，也不在 b2/kind-judgement.json（补一条判定）")


def apply(terms):
    """就地修改词条列表：改类别、移出词条、属性挂到 owner。返回（改类别条数, 移出条数, 挂属性条数）。"""
    judged = {i["id"]: i for i in judgement() if i["source"] == "terms"}
    byid = {t["id"]: t for t in terms}
    unknown = sorted(set(judged) - set(byid))
    if unknown:
        raise SystemExit("判定表里有词表中不存在的词条：" + "、".join(unknown))
    changed = moved = attached = 0
    keep = []
    for t in terms:
        k = new_kind(t, judged.get(t["id"]))
        if k in MOVED:
            moved += 1
            if k == "attribute_of":
                owner = byid.get(judged[t["id"]]["owner"])
                if not owner:
                    raise SystemExit(f"{t['id']}：owner {judged[t['id']].get('owner')} 不在词表")
                owner.setdefault("attributes", []).append(
                    {x: t[x] for x in ("id", "zh", "en", "snake", "definition") if x in t} | {"bc": t["bc"]})
                attached += 1
            continue
        if k != t["kind"]:
            t["kind"] = k
            changed += 1
            if t["id"] in judged:
                t["confirmed"] = (t.get("confirmed") + "；" if t.get("confirmed") else "") + CONFIRMED
        keep.append(t)
    terms[:] = keep
    return changed, moved, attached
