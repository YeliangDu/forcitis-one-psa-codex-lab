#!/usr/bin/env python3
"""Generate the clickable 用语确认 page (glossary/confirm-page/index.html) from glossary/terms.yaml.
Decisions are stored in the artifact's db collection `decisions` (doc id = item key), read back by Claude."""
import yaml, json, collections, html
d = yaml.safe_load(open("glossary/terms.yaml", encoding="utf-8"))
T = d["terms"]
INTERNAL = "FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目）"
FIN = {"G-8", "G-9", "G-10", "G-11", "G-12", "G-13", "G-16", "G-17", "G-20", "G-21", "G-27"}
items = []
# A: undetermined company terms on non-proposed entries
n = 0
for t in T:
    if t["status"] == "proposed":
        continue
    for k, al in enumerate(t.get("aliases") or []):
        if al.get("type") == "undetermined" and al["name"] != t["zh"]:
            n += 1
            items.append(dict(key=f"A-{t['id']}-{k}", no=f"A{n}", grp="A", say=al["name"], zh=t["zh"], en=t["en"], bc=t["bc"],
                              refs=(t.get("standard_ref") or [])[:2], defn=t.get("definition") or "", sug="dialect"))
n = 0
for t in T:
    if t["status"] != "proposed":
        continue
    n += 1
    old = [al["name"] for al in (t.get("aliases") or []) if al.get("type") in ("dialect", "undetermined", "old-code")]
    g = t.get("change_ref") or ""
    items.append(dict(key=f"B-{t['id']}", no=f"B{n}", grp="B", say=" / ".join(old) or "—", zh=t["zh"], en=t["en"], bc=t["bc"],
                      refs=(t.get("standard_ref") or [])[:2], defn=t.get("definition") or "", g=g, fin=g in FIN, sug="approve"))
n = 0
for t in sorted((t for t in T if t["status"] == "approved" and set(t.get("standard_ref") or []) <= {INTERNAL, "ADR-0016 权责发生制业财一体主干"}), key=lambda t: t["bc"]):
    n += 1
    items.append(dict(key=f"C-{t['id']}", no=f"C{n}", grp="C", say="", zh=t["zh"], en=t["en"], bc=t["bc"], refs=[], defn=t.get("definition") or "", sug="formal"))
data = json.dumps(items, ensure_ascii=False, separators=(",", ":"))
tpl = open("glossary/confirm-page/template.html", encoding="utf-8").read()
open("glossary/confirm-page/index.html", "w", encoding="utf-8").write(tpl.replace("/*__ITEMS__*/[]", data))
print(collections.Counter(i["grp"] for i in items), len(data))
