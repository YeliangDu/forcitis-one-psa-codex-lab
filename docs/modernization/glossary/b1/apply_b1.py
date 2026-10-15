"""B1 决策页答复的写回（用户 2026-10-15 在固定确认页逐组提交，glossary/decisions-b1-2026-10-15.json，键 B1-<单子>-<编号>）。
glossary/build_terms.py 调用 apply(terms)：
- B1-SCOPE：正式词条按答复写使用范围（提议条目的 scope 已直接写进提议文件）；
- B1-NAME：按答复改中文名（改名条目的定义随卡改写），旧中文名记为别名；旧名仍是另一条活词条的名字或是泛称，不列入禁用；
- B1-KIND：只记录，类别（kind）由 B2 按 DDD 战术分类落地。
「同意建议」取卡上的推荐选项；「保持原样」不改；「改为…」按 renameTo（中文名 / 英文名）。"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
DECISIONS = os.path.join(HERE, "..", "decisions-b1-2026-10-15.json")
CONFIRMED = "2026-10-15（用户，B1 用语登记规则决策页）"


def load(card):
    with open(os.path.join(HERE, f"{card}.json"), encoding="utf-8") as f:
        return json.load(f)["items"]


def decided(card):
    decisions = json.load(open(DECISIONS, encoding="utf-8"))
    for item in load(card):
        d = decisions.get(f"{card}-{item['code']}")
        if not d:
            raise SystemExit(f"{card}-{item['code']}（{item['zh']}）没有答复")
        yield item, d


def confirm(t):
    old = t.get("confirmed") or ""
    if CONFIRMED not in old:
        t["confirmed"] = f"{old}；{CONFIRMED}" if old else CONFIRMED


def apply(terms):
    """返回（写使用范围条数, 改名条数）。"""
    byid = {t["id"]: t for t in terms}
    scoped = renamed = 0
    for item, d in decided("B1-SCOPE"):
        src, term_id = item["key"].split("#", 1)
        if src != "terms.yaml":
            continue
        if d["choice"] != "approve":
            raise SystemExit(f"B1-SCOPE {item['code']}：答复「{d['choice']}」{d.get('renameTo')} 需要 one-glossary 另行处理")
        t = byid[term_id]
        if t.get("scope"):
            raise SystemExit(f"B1-SCOPE {item['code']}：{term_id} 已有使用范围 {t['scope']}")
        t["scope"] = item["scope"]
        if item.get("acl_class"):
            t["acl_class"] = item["acl_class"]
        confirm(t)
        scoped += 1
    for item, d in decided("B1-NAME"):
        option = next(o for o in item["options"] if o.get("recommended"))
        if d["choice"] == "keep" or (d["choice"] == "approve" and option["action"] != "rename"):
            continue
        zh = option["zh"] if d["choice"] == "approve" else d["renameTo"].split("/")[0].strip()
        term_id = next(t["id"] for t in terms if t["zh"] == item["zh"] and t["en"] == item["en"])
        t = byid[term_id]
        old = t["zh"]
        t["zh"] = zh
        t["definition"] = item["definition"]
        if all(a["name"] != old for a in t.get("aliases") or []):
            t.setdefault("aliases", []).append({"name": old, "type": "dialect", "deprecated": True,
                                                "source": f"B1 决策页 {item['code']} 改名；旧名是另一条词条的名字或泛称，不列入禁用"})
        confirm(t)
        renamed += 1
    return scoped, renamed
