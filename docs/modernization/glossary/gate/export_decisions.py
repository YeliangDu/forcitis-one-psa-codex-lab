#!/usr/bin/env python3
"""把门禁小决策页的答复导出为 glossary/decisions-gate-<卡号>.json（门禁卡，build_terms.py 自动写回）或 glossary/answers/<单子>.json（ACL 归类、第三轮重做，由 one-glossary 按单子另行写回），键为 GATE-<单子>-<编号>。
只认用户点过「确认提交」的答复：先用 ArtifactData list 把集合 answers 与 answers-submit 都存到同一个 out_dir，再运行
python3 docs/modernization/glossary/gate/export_decisions.py <卡号> <out_dir>
没有提交记录、或提交之后又改过选择的，导出失败（请用户重新提交）；没选的条目算未定，不导出、不按建议处理。"""
import glob, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
card, base = sys.argv[1], sys.argv[2]
coll = "gate-" + re.sub(r"[^A-Za-z0-9-]", "-", card)
src = next(p for p in (os.path.join(d, f"{card}.json") for d in (HERE, os.path.join(HERE, "..", "acl"), os.path.join(HERE, "..", "round3", "sheets"))) if os.path.exists(p))
codes = {i["code"] for i in json.load(open(src, encoding="utf-8"))["items"]}
submit_path = os.path.join(base, "answers-submit", f"{coll}.json")
if not os.path.exists(submit_path):
    sys.exit(f"{card}：用户还没有提交（answers-submit/{coll} 不存在）")
submit = json.load(open(submit_path, encoding="utf-8"))
submit = submit.get("data", submit)
out = {}
for f in sorted(glob.glob(os.path.join(base, "answers", f"{coll}~*.json"))):
    d = json.load(open(f, encoding="utf-8"))
    d = d.get("data", d)
    code = os.path.basename(f)[:-5].split("~", 1)[1]
    if code not in codes:
        sys.exit(f"{code}：不是本卡条目")
    if d.get("at", "") > submit["at"]:
        sys.exit(f"{code}：提交之后又改过选择，请用户重新提交")
    if d.get("choice") not in ("approve", "keep", "rename"):
        sys.exit(f"{code}: 未知选择 {d.get('choice')}")
    out[f"GATE-{card}-{code}"] = {k: d.get(k, "") for k in ("item", "zh", "en", "choice", "renameTo", "note", "at")}
target = "decisions-gate" if src.startswith(HERE + os.sep) else "answers"
os.makedirs(os.path.join(HERE, "..", "answers"), exist_ok=True)
json.dump(out, open(os.path.join(HERE, "..", f"{target}-{card}.json") if target == "decisions-gate" else os.path.join(HERE, "..", "answers", f"{card}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
print(card, f"提交于 {submit['at']}", len(out), "条已定，", len(codes) - len(out), "条未定；",
      {c: sum(1 for v in out.values() if v["choice"] == c) for c in ("approve", "keep", "rename")})
