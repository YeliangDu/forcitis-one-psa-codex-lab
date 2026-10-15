#!/usr/bin/env python3
"""把第三轮在线确认页的答复（页面数据库 decisions/<编号>）导出为 glossary/decisions-round3-2026-10-15.json，键为 R3-<编号>。
先用 ArtifactData list（out_dir=<dir>）把 decisions 存成 <dir>/decisions/*.json，再运行：
python3 glossary/round3/export_decisions.py <dir>/decisions
答复的编号必须与 round3-items.json 一一对应，缺一条或多一条都失败。"""
import glob, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "decisions-round3-2026-10-15.json")
CHOICES = {"approve", "keep", "rename"}

codes = {i["code"] for g in json.load(open(os.path.join(HERE, "round3-items.json"), encoding="utf-8"))["groups"] for i in g["items"]}
out = {}
for f in sorted(glob.glob(os.path.join(sys.argv[1], "*.json"))):
    d = json.load(open(f, encoding="utf-8"))
    d = d.get("data", d)
    code = os.path.basename(f)[:-5]
    if d.get("choice") not in CHOICES:
        sys.exit(f"{code}: 未知选择 {d.get('choice')}")
    out[f"R3-{code}"] = {k: d.get(k, "") for k in ("item", "zh", "en", "choice", "renameTo", "note", "at")}
got = {k[3:] for k in out}
if got != codes:
    sys.exit(f"答复与决策单不一致：缺 {sorted(codes - got)[:10]}，多 {sorted(got - codes)[:10]}")
json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), {c: sum(1 for v in out.values() if v["choice"] == c) for c in sorted(CHOICES)})
