#!/usr/bin/env python3
"""Merge per-lens rule cards (rules/raw/*.md) into BUSINESS_RULES.md.

- verifies each card's Source: file exists under legacy/psa and the line range is inside the file
- drops cards whose citation fails (reported), de-duplicates cards that cite overlapping ranges in the
  same file with the same category (keeps the higher-priority / higher-confidence one, records the merge)
- renumbers RULE-001.. grouped by domain then category, writes summary table + SME section
Run from the workspace root: python3 analysis/psa/rules/merge_rules.py
"""
import os, re, glob, json
from collections import defaultdict, Counter

ROOT = os.path.realpath("legacy/psa")
RAW = "analysis/psa/rules/raw"
OUT = "analysis/psa/BUSINESS_RULES.md"
DOMAIN = {"timesheet": "工时与资源", "cost": "项目成本与核算", "presales": "售前与报价",
          "delivery": "交付、预算与收入", "approval": "审批（platform.approval）"}
CAT_ORDER = {"Calculation": 0, "Validation": 1, "Lifecycle": 2, "Policy": 3}
PRI = {"P0": 0, "P1": 1, "P2": 2}
CONF = {"High": 0, "Medium": 1, "Low": 2}

cards, rejected = [], []
for path in sorted(glob.glob(os.path.join(RAW, "*.md"))):
    lens = os.path.basename(path)[:-3]
    dom = DOMAIN[lens.split("-")[0]]
    text = open(path, encoding="utf-8").read()
    parts = re.split(r"(?m)^### RULE-[\w-]+:\s*", text)[1:]
    for p in parts:
        name, _, body = p.partition("\n")
        body = re.split(r"(?m)^---\s*$|^\*\*Coverage", body)[0].rstrip()
        g = lambda k: (re.search(rf"\*\*{k}:\*\*\s*(.+)", body) or [None, ""])[1].strip()
        src = g("Source")
        m = re.search(r"`([^`:]+):(\d+)(?:-(\d+))?`", src)
        c = {"name": name.strip(), "body": body, "lens": lens, "domain": dom,
             "category": g("Category").split()[0] if g("Category") else "Policy",
             "priority": (re.search(r"P[012]", g("Priority")) or ["P1"])[0],
             "confidence": (re.match(r"\W*(High|Medium|Low)", g("Confidence")) or [None, "Medium"])[1],
             "source": src}
        if not m:
            rejected.append((c, "no parsable Source")); continue
        f, a = m.group(1), int(m.group(2)); b = int(m.group(3) or a)
        f = re.sub(r"^F/", "psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/", f)
        f = re.sub(r"^FA/", "psa-backend/yudao-module-fst/yudao-module-fst-api/src/main/java/cn/iocoder/yudao/module/fst/", f)
        fp = os.path.join(ROOT, f)
        if not os.path.isfile(fp):
            rejected.append((c, f"file not found: {f}")); continue
        n = sum(1 for _ in open(fp, encoding="utf-8", errors="ignore"))
        if a < 1 or b > n or a > b:
            rejected.append((c, f"range {a}-{b} outside file ({n} lines)")); continue
        c.update(file=f, start=a, end=b)
        cards.append(c)

# de-duplicate: same file + same category + overlapping range (>=50% of the shorter range)
cards.sort(key=lambda c: (PRI.get(c["priority"], 1), CONF.get(c["confidence"], 1)))
kept, folded = [], defaultdict(list)
for c in cards:
    dup = None
    for k in kept:
        if k["file"] == c["file"] and k["category"] == c["category"]:
            ov = min(k["end"], c["end"]) - max(k["start"], c["start"]) + 1
            if ov > 0 and ov >= 0.5 * min(k["end"] - k["start"] + 1, c["end"] - c["start"] + 1):
                dup = k; break
    if dup: folded[id(dup)].append(c)
    else: kept.append(c)

kept.sort(key=lambda c: (list(DOMAIN.values()).index(c["domain"]), CAT_ORDER.get(c["category"], 9),
                         PRI.get(c["priority"], 1), c["file"], c["start"]))
for i, c in enumerate(kept, 1):
    c["id"] = f"RULE-{i:03d}"

def fix_body(c):
    b = re.sub(r"(?m)^\*\*Category:\*\*.*\n?", "", c["body"]).strip()
    also = folded.get(id(c))
    if also:
        b += "\n**Also stated by:** " + "; ".join(f"{x['lens']} — “{x['name']}” (`{x['file']}:{x['start']}-{x['end']}`)" for x in also)
    return f"**Category:** {c['category']}\n" + b

stats = Counter(c["category"] for c in kept); pstats = Counter(c["priority"] for c in kept)
sme = [c for c in kept if c["confidence"] != "High" or "Suspected defect" in c["body"]]
p0_flag = [c for c in kept if c["priority"] == "P0" and (c["confidence"] != "High" or "Suspected defect" in c["body"])]

L = ["# BUSINESS_RULES — psa", "",
     "日期：2026-10-14 · 方式：轻量 Method B。5 个核心域（工时与资源、项目成本与核算、售前与报价、交付预算与收入、审批 platform.approval），"
     "每个域按计算 / 校验 / 状态三个视角各派一个 business-rules-extractor（售前把校验和状态合为一个视角，共 14 个）。"
     "以 `docs/domain/00_战略设计/09-业务规则清单-全域汇总.md` 及各域文档为种子，**每条规则都落到代码引用上**。"
     "合并脚本 `rules/merge_rules.py` 逐条校验引用的文件存在、行号在范围内，并把同一处代码的重复陈述合并。"
     "差旅报销域未纳入本轮（已有 `docs/domain/02_项目核算域_accounting/travel/04-差旅报销域-业务规则.md`），按 brief 在对应阶段开工前补抽。", "",
     f"**合计 {len(kept)} 条**（抽取 {len(cards)+len(rejected)} 张卡；引用校验未通过而丢弃 {len(rejected)} 张；重复陈述合并 {sum(len(v) for v in folded.values())} 张）。"
     f"按类别：" + "，".join(f"{k} {v}" for k, v in stats.most_common()) + "。按优先级：" + "，".join(f"{k} {pstats[k]}" for k in ("P0", "P1", "P2")) + "。"
     f"需要 SME 确认的有 {len(sme)} 条，其中 P0 {len(p0_flag)} 条（置信度不足 High，或带有疑似缺陷）。", "",
     "## 汇总表", "", "| ID | 域 | 名称 | 类别 | 优先级 | 来源 | 置信度 |", "|---|---|---|---|---|---|---|"]
for c in kept:
    L.append(f"| {c['id']} | {c['domain']} | {c['name'].replace('|','/')} | {c['category']} | {c['priority']} | `{os.path.basename(c['file'])}:{c['start']}-{c['end']}` | {c['confidence']} |")
cur = None
for c in kept:
    if c["domain"] != cur:
        cur = c["domain"]; L += ["", f"## {cur}", ""]
    L += [f"### {c['id']}: {c['name']}", fix_body(c).replace(f"`{c['file']}", f"`{c['file']}"), ""]
L += ["## 需要 SME 确认的规则", "", "置信度不足 High，或抽取时发现疑似缺陷（遗留行为看起来有误，或与文档不一致）。brief 的行为契约**不会**把这些当作已定行为，会列为开放问题。", ""]
for c in sme:
    conf = (re.search(r"\*\*Confidence:\*\*\s*(.+)", c["body"]) or [None, ""])[1]
    dfx = (re.search(r"\*\*Suspected defect:\*\*\s*(.+)", c["body"]) or [None, ""])[1]
    L.append(f"- [ ] **{c['id']}**（{c['priority']}，{c['domain']}）{c['name']} — " + (f"疑似缺陷：{dfx[:300]} " if dfx else "") + (f"｜{conf[:300]}" if c['confidence'] != 'High' else ""))
if rejected:
    L += ["", "## 引用校验未通过而丢弃的卡", ""] + [f"- {c['lens']} “{c['name']}”：{why}（{c['source'][:120]}）" for c, why in rejected]
open(OUT, "w", encoding="utf-8").write("\n".join(L) + "\n")
json.dump({"total": len(kept), "rejected": len(rejected), "folded": sum(len(v) for v in folded.values()),
           "by_category": stats, "by_priority": pstats, "sme": len(sme), "p0_flagged": len(p0_flag),
           "by_lens": Counter(c["lens"] for c in cards)}, open("analysis/psa/rules/merge_stats.json", "w"), ensure_ascii=False, indent=1)
print(json.load(open("analysis/psa/rules/merge_stats.json")))
for c, why in rejected: print("REJECTED", c["lens"], c["name"][:60], why)
