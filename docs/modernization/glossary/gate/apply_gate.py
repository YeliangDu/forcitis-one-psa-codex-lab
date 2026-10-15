"""开工前术语门禁的写回：glossary/build_terms.py 调用 apply(terms)。
对每张已答复的卡（gate/<卡号>.json 加 glossary/decisions-gate-<卡号>.json），按用户选择登记正式词条：
没选的条目算未定、跳过；approve / rename 登记为正式词条（values 中的取值各登记一条 <父 id>__<取值>）；merge 把本卡写法挂为目标词条的别名；
common_word 只打印提示，由 one-glossary 补进 backend/glossary/terms.yaml 的 common_words。没有答复的卡跳过。"""
import glob, json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))


def snake(en):
    spaced = re.sub(r"([A-Z])([A-Z][a-z])", r"\1 \2", re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", en))
    return "_".join(w.lower() for w in re.split(r"[^A-Za-z0-9]+", spaced) if w and not w.isdigit())


def apply(terms):
    byid = {t["id"]: t for t in terms}
    added = 0
    for path in sorted(glob.glob(os.path.join(HERE, "*.json"))):
        doc = json.load(open(path, encoding="utf-8"))
        dec_path = os.path.join(HERE, "..", f"decisions-gate-{doc['card']}.json")
        if not os.path.exists(dec_path):
            continue
        decisions = json.load(open(dec_path, encoding="utf-8"))
        confirmed = f"{doc['date']}（用户，开工前术语门禁 {doc['card']}）"
        for i in doc["items"]:
            d = decisions.get(f"GATE-{doc['card']}-{i['code']}")
            if not d:
                continue   # 用户没选：未定，留到下一张决策单，不按建议处理
            if d["choice"] == "approve":
                o = next(x for x in i["options"] if x["recommended"])
            elif d["choice"] == "keep":
                o = {"action": "approve", "zh": i["zh"], "en": i["en"]}
            else:
                parts = [x.strip() for x in d["renameTo"].split("/")]
                o = {"action": "rename", "zh": parts[0], "en": parts[1] if len(parts) > 1 else i["en"]}
            if o["action"] == "common_word":
                print(f"gate {doc['card']} {i['code']}：{i['en']} 归为通用词，补进 backend/glossary/terms.yaml common_words")
                continue
            if o["action"] == "merge":
                t = byid[o["into"]]
                for name, kind in ((i["zh"], "standard-zh"), (i["en"], "old-code")):
                    if name not in (t["zh"], t["en"]) and all(a["name"] != name for a in t.setdefault("aliases", [])):
                        t["aliases"].append({"name": name, "type": kind, "deprecated": kind == "old-code"})
                continue
            given = i.get("id") or ""
            prefix = given.rsplit("__", 1)[0] + "__" if "__" in given else ""
            parent = given if given and o.get("recommended") else prefix + snake(o["en"])
            rows = [(parent, o["zh"], o["en"], i["kind"], i["definition"])]
            rows += [(f"{parent}__{snake(v['en'])}", v["zh"], v["en"], "value", v.get("definition") or f"{o['zh']}的取值")
                     for v in i.get("values") or []]
            for term_id, zh, en, kind, definition in rows:
                if term_id in byid:
                    raise SystemExit(f"门禁 {doc['card']}：{term_id} 与已有正式词条重名")
                e = dict(id=term_id, zh=zh, en=en, snake=snake(en), kind=kind, bc=i["bc"], definition=definition,
                         standard_ref=[i["refs"]], confidence="medium", aliases=[], forbidden=[], status="approved",
                         note=f"开工前术语门禁 {doc['card']}（GATE-{doc['card']}-{i['code']}）", confirmed=confirmed)
                if (zh, en) != (i["zh"], i["en"]) and term_id == parent:
                    e["aliases"].append({"name": i["en"], "type": "old-code", "deprecated": True})
                terms.append(e)
                byid[term_id] = e
                added += 1
    return added
