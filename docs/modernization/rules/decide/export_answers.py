#!/usr/bin/env python3
"""把业务决策在线页的答复导出为 rules/decide/answers/<单子>.json，供 one-rules 写回规则卡与 intent。
只认用户点过「确认提交」的答复：先用 ArtifactData list 把集合 answers 与 answers-submit 存到同一个 out_dir，再运行
python3 docs/modernization/rules/decide/export_answers.py <单子> <out_dir>
提交记录有两种：新版整页「检查答案」一次提交（文档 id 即提交编号，picks 含全部题）与旧版按组提交（文档 id 为组 id）。
同一题有多次提交时取最晚的一次；该次提交之后答复又改过的题算草稿，导出失败并列出，须请用户重新提交。"""
import glob, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))


def load(path):
    d = json.load(open(path, encoding="utf-8"))
    return d.get("data", d)


def export(sheet, base):
    answers = {}
    for f in glob.glob(os.path.join(base, "answers", f"{sheet}[~@]*.json")):
        answers[os.path.basename(f)[:-5].replace("@", "~", 1)] = load(f)   # ArtifactData 落盘时把「~」写成「@」
    subs = []
    for f in glob.glob(os.path.join(base, "answers-submit", "*.json")):
        d = load(f)
        d["_id"] = os.path.basename(f)[:-5]
        subs.append(d)
    subs.sort(key=lambda d: d.get("at", ""))
    out, stale = {}, []
    for d in subs:
        for k, v in (d.get("picks") or {}).items():
            if k.startswith(sheet + "~"):
                out[k] = dict(v, submission=d.get("number") or d["_id"], submitted=d["at"])
    for k, v in out.items():
        if answers.get(k, {}).get("at", "") > v["submitted"]:
            stale.append(k)
    return out, sorted(stale)


if __name__ == "__main__":
    sheet, base = sys.argv[1], sys.argv[2]
    out, stale = export(sheet, base)
    if stale:
        sys.exit(f"这些题提交后又改过，请用户重新提交：{'、'.join(stale)}")
    os.makedirs(os.path.join(HERE, "answers"), exist_ok=True)
    json.dump(out, open(os.path.join(HERE, "answers", f"{sheet}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
    subs = sorted({v["submission"] for v in out.values()})
    print(sheet, len(out), "题已提交（提交", "、".join(subs), "）；按推荐", sum(v["recommended"] for v in out.values()),
          "（其中没动过", sum(v["defaulted"] for v in out.values()), "）、改选", sum(not v["recommended"] and v["choice"] != "defer" for v in out.values()),
          "、暂不定", sum(v["choice"] == "defer" for v in out.values()))
