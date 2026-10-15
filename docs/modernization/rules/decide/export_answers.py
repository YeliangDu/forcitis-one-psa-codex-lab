#!/usr/bin/env python3
"""把业务决策在线页的答复导出为 rules/decide/answers/<单子>.json，供 one-rules 写回规则卡与 intent。
只认用户点过「确认提交」的组：先用 ArtifactData list 把集合 answers 与 answers-submit 存到同一个 out_dir，再运行
python3 docs/modernization/rules/decide/export_answers.py <单子> <out_dir>
提交记录里有本组每题的选择（没动过的题按推荐预选，标 defaulted）；提交后又改过的组仍算草稿，导出失败并列出，须请用户重新提交。"""
import glob, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sheet, base = sys.argv[1], sys.argv[2]
last = {}   # 组 id -> 本组答复的最后修改时刻
answers = {}
for f in glob.glob(os.path.join(base, "answers", f"{sheet}~*.json")):
    d = json.load(open(f, encoding="utf-8"))
    answers[os.path.basename(f)[:-5]] = d.get("data", d)
out, stale = {}, []
for f in sorted(glob.glob(os.path.join(base, "answers-submit", "*.json"))):
    d = json.load(open(f, encoding="utf-8"))
    d = d.get("data", d)
    picks = {k: v for k, v in (d.get("picks") or {}).items() if k.startswith(sheet + "~")}
    if not picks:
        continue
    gid = os.path.basename(f)[:-5]
    if any(answers.get(k, {}).get("at", "") > d["at"] for k in picks):
        stale.append(gid)
        continue
    for k, v in picks.items():
        out[k] = dict(v, group=gid, submitted=d["at"])
if stale:
    sys.exit(f"这些组提交后又改过，请用户重新提交：{'、'.join(stale)}")
os.makedirs(os.path.join(HERE, "answers"), exist_ok=True)
json.dump(out, open(os.path.join(HERE, "answers", f"{sheet}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
print(sheet, len(out), "题已提交；按推荐", sum(v["recommended"] for v in out.values()), "（其中没动过", sum(v["defaulted"] for v in out.values()),
      "）、改选", sum(not v["recommended"] and v["choice"] != "defer" for v in out.values()), "、暂不定", sum(v["choice"] == "defer" for v in out.values()))
