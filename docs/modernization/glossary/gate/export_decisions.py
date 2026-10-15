#!/usr/bin/env python3
"""把门禁小决策页的答复导出为 glossary/decisions-gate-<卡号>.json（门禁卡，build_terms.py 自动写回）或 glossary/answers/<单子>.json（ACL 归类、第三轮重做，由 one-glossary 按单子另行写回），键为 GATE-<单子>-<编号>。
只认用户点过「确认提交」的答复：先用 ArtifactData list 把集合 answers 与 answers-submit 都存到同一个 out_dir，再运行
python3 docs/modernization/glossary/gate/export_decisions.py <卡号> <out_dir>
所在组没提交、或提交后又改过的条目仍算草稿，不导出；没选的条目算未定，不按建议处理。"""
import glob, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
card, base = sys.argv[1], sys.argv[2]
coll = "gate-" + re.sub(r"[^A-Za-z0-9-]", "-", card)
src = next(p for p in (os.path.join(d, f"{card}.json") for d in (HERE, os.path.join(HERE, "..", "acl"), os.path.join(HERE, "..", "round3", "sheets"))) if os.path.exists(p))
target = "decisions-gate" if os.path.dirname(src) == HERE else "answers"
codes = {i["code"] for i in json.load(open(src, encoding="utf-8"))["items"]}
submitted = {}   # 答复文档 id -> 所在组的提交时刻（页面按组提交，提交记录里列出本组条目）
for f in glob.glob(os.path.join(base, "answers-submit", "*.json")):
    d = json.load(open(f, encoding="utf-8"))
    d = d.get("data", d)
    for key in d.get("keys") or []:
        submitted[key] = max(submitted.get(key, ""), d["at"])
out, drafts = {}, 0
for f in sorted(glob.glob(os.path.join(base, "answers", f"{coll}~*.json"))):
    d = json.load(open(f, encoding="utf-8"))
    d = d.get("data", d)
    key = os.path.basename(f)[:-5]
    code = key.split("~", 1)[1]
    if code not in codes:
        sys.exit(f"{code}：不是本单条目")
    if key not in submitted or d.get("at", "") > submitted[key]:
        drafts += 1   # 所在组没提交，或提交后又改过：仍是草稿，不算答复
        continue
    if d.get("choice") not in ("approve", "keep", "rename"):
        sys.exit(f"{code}: 未知选择 {d.get('choice')}")
    out[f"GATE-{card}-{code}"] = {k: d.get(k, "") for k in ("item", "zh", "en", "choice", "renameTo", "note", "at")}
json.dump(out, open(os.path.join(HERE, "..", f"{target}-{card}.json") if target == "decisions-gate" else os.path.join(HERE, "..", "answers", f"{card}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
print(card, len(out), "条已提交，", drafts, "条草稿未提交，", len(codes) - len(out) - drafts, "条未选；",
      {c: sum(1 for v in out.values() if v["choice"] == c) for c in ("approve", "keep", "rename")})
