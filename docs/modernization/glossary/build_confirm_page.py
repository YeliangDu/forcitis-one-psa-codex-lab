#!/usr/bin/env python3
"""Generate the clickable 用语确认 page (glossary/confirm-page/index.html).
第二轮（2026-10-14）：P 组 = backend/glossary/proposed/*.yaml 的提议术语，A 组 = terms.yaml 中待确认的公司叫法（OA 盘点），
N 组 = 新 BC 命名；每项建议来自 glossary/round2-proposals.yaml。第一轮 197 项已确认（decisions-2026-10-14.json），不再出现。
Decisions are stored in the artifact's db collection `decisions` (doc id = item key, round-2 keys start with R2-), read back by Claude.
Run: cd docs/modernization && python3 glossary/build_confirm_page.py"""
import yaml, json, collections, glob, os

T = yaml.safe_load(open("glossary/terms.yaml", encoding="utf-8"))["terms"]
BYID = {t["id"]: t for t in T}
PROP = yaml.safe_load(open("glossary/round2-proposals.yaml", encoding="utf-8"))
PROPOSED_DIR = "../../backend/glossary/proposed"
items = []

# P: module proposed terms
n = 0
for f in sorted(glob.glob("glossary/round2-source/*.yaml"), reverse=True):   # template-carryover first, then platform-contracts
    for t in yaml.safe_load(open(f, encoding="utf-8"))["terms"]:
        p = PROP["P"][t["id"]]
        n += 1
        to = ""
        if p["sug"] == "change":
            to = f"{p.get('zh', t['zh'])} / {p.get('en', t['en'])}" + ("（只改定义）" if "definition" in p and "zh" not in p and "en" not in p else "")
        elif p["sug"] == "merge":
            tgt = BYID[p["into"]]
            to = f"并入正式用语「{tgt['zh']} / {tgt['en']}」"
        opts = None if p["sug"] != "approve" else [["approve", "同意转正式"], ["rename", "改为…"]]
        items.append(dict(key=f"R2-P-{t['id']}", no=f"P{n}", grp="P", src=os.path.basename(f), say=f"{t['zh']} / {t['en']}",
                          zh=t["zh"], en=t["en"], bc=t["bc"], to=to, defn=p.get("definition") or t.get("definition") or "",
                          why=p.get("why", ""), dialect=p.get("dialect", []), code="" if p.get("code", "").startswith("无") else p.get("code", ""),
                          refs=[], sug=p["sug"], opts=opts))

# A: undetermined company terms (OA inventory) on approved entries
n = 0
for t in T:
    for k, al in enumerate(t.get("aliases") or []):
        if al.get("type") == "undetermined" and al["name"] != t["zh"]:
            n += 1
            p = PROP["A"].get(f"{t['id']}/{al['name']}", {"sug": "dialect"})
            # 存储的文档 id 只接受 ASCII、不含「/」：用别名序号（第一轮同样做法）
            items.append(dict(key=f"R2-A-{t['id']}-{k}", no=f"A{n}", grp="A",
                              say=al["name"] + (f"（{al['source']}）" if al.get("source") else ""), zh=t["zh"], en=t["en"], bc=t["bc"],
                              refs=(t.get("standard_ref") or [])[:2], defn=t.get("definition") or "", why=p.get("note", ""), sug=p["sug"]))

# N: new BC names
for k, (key, p) in enumerate(PROP["N"].items(), 1):
    items.append(dict(key=f"R2-N-{key}", no=f"N{k}", grp="N", say=p["old"], zh=p["zh"], en=p["en"], bc="新 BC",
                      to=f"{p['zh']} / {p['en']}", why=p["why"], dialect=p.get("dialect", []), refs=[], defn="", sug="change"))

data = json.dumps(items, ensure_ascii=False, separators=(",", ":"))
tpl = open("glossary/confirm-page/template.html", encoding="utf-8").read()
open("glossary/confirm-page/index.html", "w", encoding="utf-8").write(tpl.replace("/*__ITEMS__*/[]", data))
print(collections.Counter(i["grp"] for i in items), collections.Counter(i["sug"] for i in items), len(data))
