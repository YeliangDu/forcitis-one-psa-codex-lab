#!/usr/bin/env python3
"""把财务交回的《财务确认单》答复（FINANCE_CONFIRM.html 导出的 .txt）按来源分发，不改任何结论：
- 总账类（题或小问有总账回填位置）→ ../rules/GL_FINANCE_DECISIONS.json 对应事项的 finance_answers，并重新生成 GL_FINANCE_DECISIONS.md；
- 经营分析类（其余题与小问）和 ADR 整体确认 → 本目录 FINANCE_ANSWERS.json（每次交回追加一条 reply，保留历史）。
结论是否需要调整，由评审会话判断。
用法：python3 import_finance_answers.py <答复文件.txt>"""
import json, pathlib, subprocess, sys

HERE = pathlib.Path(__file__).parent
sys.path.insert(0, str(HERE))
from build_finance_confirm import HINTS, GL_JSON  # 回填位置只在一处定义

MARK = "---- 以下为机读数据，请勿修改 ----"
text = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
if MARK not in text:
    sys.exit("答复文件缺少机读数据段，请让财务用确认单的「导出答复」或「复制答复」重新生成")
p = json.loads(text.split(MARK, 1)[1].strip().splitlines()[0])
if p.get("form") != "FINANCE_CONFIRM" or p.get("version") not in (2, 3):
    sys.exit("不是第 2 版或第 3 版财务确认单的答复（第 1 版作废，请让财务改填第 3 版）")
meta = {"role": p.get("respondent_role", ""), "date": p.get("date", "")}
filled = lambda a: bool(a.get("choice") or a.get("note"))

gl_rows, bi_rows = [], {}
for a in p["answers"]:
    h = HINTS[a["fq"]]
    if "parts" in a:
        for i, part in enumerate(a["parts"]):
            if not filled(part):
                continue
            target = h["parts"][i].get("gl")
            row = {"choice": part["choice"], "note": part["note"]}
            (gl_rows.append((target, row)) if target else bi_rows.__setitem__(f"{a['fq']}.{i + 1}", dict(row, label=part["label"])))
    elif filled(a):
        row = {"choice": a["choice"], "note": a["note"]}
        (gl_rows.append((h["gl"], row)) if h.get("gl") else bi_rows.__setitem__(a["fq"], row))

d = json.loads(GL_JSON.read_text(encoding="utf-8"))
for target, row in gl_rows:
    item, idx = target.split("#")
    dec = d["decisions"][item]
    fa = dec.setdefault("finance_answers", [])
    fa += [None] * (len(dec["pending_finance"]) - len(fa))
    fa[int(idx)] = dict(row, **meta)
GL_JSON.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")

out = HERE / "FINANCE_ANSWERS.json"
store = json.loads(out.read_text(encoding="utf-8")) if out.exists() else {
    "form": "FINANCE_CONFIRM", "note": "财务确认单中经营分析类答复与 ADR 整体确认；总账类答复在 ../rules/GL_FINANCE_DECISIONS.json。只记录答复，不改结论。", "replies": []}
store["replies"].append(dict(meta, version=p["version"],
                             adrs={a["adr"]: {"choice": a["choice"], "note": a["note"]} for a in p.get("adrs", []) if filled(a)},
                             answers=bi_rows))
out.write_text(json.dumps(store, ensure_ascii=False, indent=2), encoding="utf-8")

print(f"总账类 {len(gl_rows)} 条 → {GL_JSON.name}；经营分析类 {len(bi_rows)} 条、ADR 确认 {len(store['replies'][-1]['adrs'])} 份 → {out.name}")
subprocess.run([sys.executable, str(GL_JSON.parent / "build_gl_decisions.py")], check=True)
