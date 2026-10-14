#!/usr/bin/env python3
"""把用语确认页 db（collection decisions，键 R2-*）导出为 glossary/decisions-round2-2026-10-14.json。
先用 ArtifactData query（out_dir=<dir>）把 decisions 存成 <dir>/decisions/*.json，再运行：
python3 glossary/export_decisions_round2.py <dir>/decisions"""
import json, glob, os, sys
out = {}
for f in sorted(glob.glob(os.path.join(sys.argv[1], "R2-*.json"))):
    d = json.load(open(f, encoding="utf-8"))
    d = d.get("data", d)
    out[os.path.basename(f)[:-5]] = {k: d.get(k, "") for k in ("item", "zh", "en", "choice", "renameTo", "note", "at")}
json.dump(out, open("glossary/decisions-round2-2026-10-14.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), {g: sum(1 for k in out if k.startswith(f"R2-{g}-")) for g in "PAN"})
